package com.example.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import java.util.Base64
import java.util.concurrent.TimeUnit

data class RemoteApiConfig(
    @Json(name = "config_version") val configVersion: Int,
    @Json(name = "api_base_url") val apiBaseUrl: String,
    @Json(name = "signature") val signature: String
)

object RemoteConfigValidator {
    fun normalizeApiBaseUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        require(uri.scheme == "https")
        require(!uri.host.isNullOrBlank())
        require(uri.query == null && uri.fragment == null)
        require(uri.path.isNullOrBlank() || uri.path == "/")
        "https://${uri.host}${if (uri.port == -1) "" else ":${uri.port}"}/"
    }.getOrNull()
}

object RemoteConfigSignature {
    fun canonicalPayload(config: RemoteApiConfig): String? {
        val url = RemoteConfigValidator.normalizeApiBaseUrl(config.apiBaseUrl) ?: return null
        return "${config.configVersion}\n$url"
    }

    fun verify(publicKeyBase64: String, config: RemoteApiConfig): Boolean = runCatching {
        val payload = requireNotNull(canonicalPayload(config))
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val signatureBytes = Base64.getDecoder().decode(config.signature)
        require(keyBytes.size == 32)
        if (Security.getProvider("EdDSA") == null) Security.addProvider(EdDSASecurityProvider())
        val keySpec = EdDSAPublicKeySpec(keyBytes, EdDSANamedCurveTable.getByName("Ed25519"))
        val verifier = Signature.getInstance("NONEwithEdDSA")
        verifier.initVerify(KeyFactory.getInstance("EdDSA").generatePublic(keySpec))
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        verifier.verify(signatureBytes)
    }.getOrDefault(false)
}

class RemoteConfigStore(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "powerhub_remote_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun activeApiUrl(): String = prefs.getString("api_base_url", null)
        ?: RemoteConfigValidator.normalizeApiBaseUrl(BuildConfig.POWERHUB_API_BASE_URL)
        ?: error("عنوان PowerHub API غير مهيأ")

    fun lastCheckedAt(): Long = prefs.getLong("last_checked_at", 0L)

    fun save(config: RemoteApiConfig) {
        val apiUrl = requireNotNull(RemoteConfigValidator.normalizeApiBaseUrl(config.apiBaseUrl)) { "عنوان API البعيد غير صالح" }
        prefs.edit()
            .putString("api_base_url", apiUrl)
            .putInt("config_version", config.configVersion)
            .putLong("last_checked_at", System.currentTimeMillis())
            .apply()
    }
}

class RemoteConfigRepository(private val context: Context) {
    private val store = RemoteConfigStore(context)
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val adapter = Moshi.Builder().build().adapter(RemoteApiConfig::class.java)

    fun activeApiUrl(): String = store.activeApiUrl()

    suspend fun refreshIfNeeded(force: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val remoteUrl = BuildConfig.POWERHUB_REMOTE_CONFIG_URL.trim()
            val publicKey = BuildConfig.POWERHUB_REMOTE_CONFIG_PUBLIC_KEY.trim()
            if (remoteUrl.isBlank()) return@runCatching false
            require(remoteUrl.startsWith("https://")) { "يجب أن يكون رابط التهيئة البعيدة HTTPS." }
            require(publicKey.isNotBlank()) { "مفتاح تحقق التهيئة البعيدة غير مهيأ." }
            if (!force && System.currentTimeMillis() - store.lastCheckedAt() < 30 * 60 * 1000L) return@runCatching false
            val response = client.newCall(Request.Builder().url(remoteUrl).header("Accept", "application/json").build()).execute()
            response.use {
                require(it.isSuccessful) { "تعذر تنزيل التهيئة البعيدة (${it.code})." }
                val config = requireNotNull(adapter.fromJson(requireNotNull(it.body).string())) { "ملف التهيئة البعيدة غير صالح." }
                require(RemoteConfigSignature.verify(publicKey, config)) { "توقيع التهيئة البعيدة غير صالح." }
                val previousUrl = store.activeApiUrl()
                store.save(config)
                previousUrl != store.activeApiUrl()
            }
        }
    }
}
