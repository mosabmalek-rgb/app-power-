package com.example.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

class MobileSessionStore(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "powerhub_mobile_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun accessToken(): String? = prefs.getString("access_token", null)
    fun refreshToken(): String? = prefs.getString("refresh_token", null)
    fun isLoggedIn(): Boolean = !accessToken().isNullOrBlank() && !refreshToken().isNullOrBlank()
    fun needsAccessRefresh(): Boolean = System.currentTimeMillis() >= prefs.getLong("access_expires_at", 0L) - 60_000L

    fun hasLocalPin(): Boolean = !prefs.getString("local_pin_hash", null).isNullOrBlank()

    fun setLocalPin(pin: String) {
        require(pin.matches(Regex("\\d{4}"))) { "PIN must contain exactly four digits" }
        prefs.edit().putString("local_pin_hash", hashPin(pin)).apply()
    }

    fun verifyLocalPin(pin: String): Boolean = hasLocalPin() &&
        MessageDigest.isEqual(hashPin(pin).toByteArray(), prefs.getString("local_pin_hash", "").orEmpty().toByteArray())

    private fun hashPin(pin: String): String = MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun save(session: MobileSessionResponse) {
        prefs.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putLong("access_expires_at", System.currentTimeMillis() + (session.expiresIn * 1000L))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
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
