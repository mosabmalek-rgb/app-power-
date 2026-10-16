package com.example.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

class MobileSessionStore(context: Context) {
    private val prefs: android.content.SharedPreferences = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "powerhub_mobile_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Throwable) {
        context.getSharedPreferences("powerhub_mobile_session_fallback", Context.MODE_PRIVATE)
    }

    fun accessToken(): String? = prefs.getString("access_token", null)
    fun refreshToken(): String? = prefs.getString("refresh_token", null)
    fun isLoggedIn(): Boolean = !accessToken().isNullOrBlank() && !refreshToken().isNullOrBlank()
    fun needsAccessRefresh(): Boolean = System.currentTimeMillis() >= prefs.getLong("access_expires_at", 0L) - 60_000L
    fun subscriptionEndsAt(): Long? = prefs.getLong("subscription_ends_at", 0L).takeIf { it > 0L }
    fun subscriptionAllowsOfflineNow(): Boolean {
        val now = System.currentTimeMillis()
        val lastSeen = prefs.getLong("last_wall_clock_ms", 0L)
        if (lastSeen > 0L && now + 5 * 60_000L < lastSeen) return false
        val endsAt = subscriptionEndsAt() ?: return false
        prefs.edit().putLong("last_wall_clock_ms", maxOf(now, lastSeen)).apply()
        return now < endsAt
    }

    fun hasLocalPin(): Boolean = !prefs.getString("local_pin_hash", null).isNullOrBlank()

    fun setLocalPin(pin: String) {
        require(pin.matches(Regex("\\d{4}"))) { "PIN must contain exactly four digits" }
        prefs.edit().putString("local_pin_hash", hashPin(pin)).apply()
    }

    fun verifyLocalPin(pin: String): Boolean = hasLocalPin() &&
        MessageDigest.isEqual(hashPin(pin).toByteArray(), prefs.getString("local_pin_hash", "").orEmpty().toByteArray())

    private fun hashPin(pin: String): String = MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private val _sessionRevokedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionRevokedEvents: SharedFlow<Unit> = _sessionRevokedEvents.asSharedFlow()

    fun save(session: MobileSessionResponse) {
        prefs.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putLong("access_expires_at", System.currentTimeMillis() + (session.expiresIn * 1000L))
            .putLong("subscription_ends_at", java.time.Instant.parse(session.user.subscriptionEndsAt).toEpochMilli())
            .putLong("last_wall_clock_ms", System.currentTimeMillis())
            .apply()
    }

    /** Save a successful token rotation without touching offline work or local PIN settings. */
    fun saveRefreshedSession(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long,
        subscriptionEndsAt: String?
    ) {
        val editor = prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("access_expires_at", System.currentTimeMillis() + (expiresInSeconds * 1000L))
            .putLong("last_wall_clock_ms", System.currentTimeMillis())
        subscriptionEndsAt?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { java.time.Instant.parse(value).toEpochMilli() }
                .getOrNull()
                ?.let { editor.putLong("subscription_ends_at", it) }
        }
        editor.apply()
    }

    /**
     * The server rejected both the device access token and its refresh token.
     * Remove credentials only: Room readings, payment queue, local PIN and settings remain intact.
     */
    fun markSessionRevoked() {
        val wasLoggedIn = isLoggedIn()
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .putLong("access_expires_at", 0L)
            .apply()
        if (wasLoggedIn) _sessionRevokedEvents.tryEmit(Unit)
    }

    fun clear() = prefs.edit().clear().apply()
}

private class MobileSessionAuthenticator(
    private val sessionStore: MobileSessionStore,
    private val baseUrl: String
) : Authenticator {
    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        // Login and refresh manage their own 401 responses. Never recursively refresh them.
        if (path.endsWith("/api/mobile/v1/auth/login") || path.endsWith("/api/mobile/v1/auth/refresh")) {
            return null
        }
        if (response.code != 401 || responseCount(response) >= 2) return null

        val errorCode = errorCode(response.peekBody(64L * 1024L).string())
        if (errorCode != "UNAUTHORIZED") return null

        return synchronized(sessionStore) {
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotBlank() }
            val currentAccessToken = sessionStore.accessToken()

            // Another concurrent request may already have rotated the token.
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestToken) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            val refreshToken = sessionStore.refreshToken()?.takeIf { it.isNotBlank() }
                ?: return@synchronized null

            try {
                val jsonBody = JSONObject().put("refreshToken", refreshToken).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val refreshRequest = Request.Builder()
                    .url(baseUrl.trim().trimEnd('/') + "/api/mobile/v1/auth/refresh")
                    .post(jsonBody)
                    .header("Accept", "application/json")
                    .build()

                var refreshStatus = 0
                var refreshBody = ""
                refreshClient.newCall(refreshRequest).execute().use { refreshResponse ->
                    refreshStatus = refreshResponse.code
                    refreshBody = refreshResponse.body?.string().orEmpty()
                }

                if (refreshStatus !in 200..299) {
                    if (refreshStatus == 401 && errorCode(refreshBody) == "REFRESH_TOKEN_EXPIRED") {
                        sessionStore.markSessionRevoked()
                    }
                    return@synchronized null
                }

                val payload = JSONObject(refreshBody)
                val newAccessToken = payload.optString("accessToken").takeIf { it.isNotBlank() }
                    ?: return@synchronized null
                val newRefreshToken = payload.optString("refreshToken").takeIf { it.isNotBlank() }
                    ?: return@synchronized null
                val expiresIn = payload.optLong("expiresIn", 15 * 60L)
                val subscriptionEndsAt = payload.optJSONObject("user")?.optString("subscriptionEndsAt")
                    ?.takeIf { !it.isNullOrBlank() && it != "null" }

                sessionStore.saveRefreshedSession(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    expiresInSeconds = expiresIn,
                    subscriptionEndsAt = subscriptionEndsAt
                )
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            } catch (_: IOException) {
                // A network failure is not proof of revocation; keep credentials and pending work.
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }

    private fun errorCode(body: String): String? = runCatching {
        val root = JSONObject(body)
        val error = root.optJSONObject("error") ?: root
        error.optString("code").takeIf { it.isNotBlank() && it != "null" }
    }.getOrNull()

    private companion object {
        val refreshClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

object MobileApiClient {
    fun create(sessionStore: MobileSessionStore, configuredBaseUrl: String = BuildConfig.POWERHUB_API_BASE_URL): FieldReadingApi {
        val baseUrl = configuredBaseUrl.trim()
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) { "عنوان PowerHub API غير مهيأ" }
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                sessionStore.accessToken()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                header("Accept", "application/json")
            }.build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(MobileSessionAuthenticator(sessionStore, baseUrl))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FieldReadingApi::class.java)
    }
}
