package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.data.FieldReadingDao
import com.example.data.PendingPaymentEntity
import com.example.data.PendingReadingEntity
import com.example.data.SubscriberEntity
import com.example.data.api.FieldReadingApi
import com.example.data.api.MobileApiClient
import com.example.data.api.MobileLoginRequest
import com.example.data.api.MobileReadingRequest
import com.example.data.api.MobileSessionStore
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FieldReadingRepository(
    private val context: Context,
    private val dao: FieldReadingDao,
    private val sessionStore: MobileSessionStore = MobileSessionStore(context),
    private val api: FieldReadingApi = MobileApiClient.create(sessionStore)
) {
    private val prefs = context.getSharedPreferences("field_reading_prefs", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = sessionStore.isLoggedIn()
    fun getUserName(): String = prefs.getString("user_name", "") ?: ""
    fun getUserRole(): String = prefs.getString("user_role", "") ?: ""
    fun getCompanyName(): String = prefs.getString("company_name", "PowerHub") ?: "PowerHub"
    fun getSavedUsername(): String = prefs.getString("saved_username", "") ?: ""
    private fun activeBranchId(): Long = prefs.getLong("active_branch_id", -1L)

    private suspend fun ensureActiveAccessToken() {
        if (!sessionStore.needsAccessRefresh()) return
        val refreshToken = sessionStore.refreshToken() ?: throw IllegalStateException("انتهت جلسة الموبايل. سجّل الدخول مجدداً.")
        sessionStore.save(api.refresh(com.example.data.api.MobileRefreshRequest(refreshToken)))
    }

    suspend fun login(email: String, password: String, remember: Boolean): Result<Unit> = runCatching {
        val session = api.login(MobileLoginRequest(email = email.trim().lowercase(), password = password, deviceLabel = "Android Field Reader"))
        sessionStore.save(session)
        prefs.edit().apply {
            putString("user_name", session.user.name ?: email.substringBefore("@"))
            putString("user_role", session.user.role)
            putString("company_name", "PowerHub")
            putLong("active_branch_id", session.user.branchIds.firstOrNull() ?: -1L)
            if (remember) putString("saved_username", email.trim().lowercase()) else remove("saved_username")
            apply()
        }
    }

    fun clearSession() {
        sessionStore.clear()
        prefs.edit().clear().apply()
    }

    val subscribersFlow: Flow<List<SubscriberEntity>> = dao.getAllSubscribersFlow()
    val pendingReadingsFlow: Flow<List<PendingReadingEntity>> = dao.getAllPendingReadingsFlow()
    val pendingPaymentsFlow: Flow<List<PendingPaymentEntity>> = dao.getAllPendingPaymentsFlow()
    fun getSubscriberFlow(id: String): Flow<SubscriberEntity?> = dao.getSubscriberByIdFlow(id)

    suspend fun saveReading(subscriberId: String, currentReading: Double, notes: String, latitude: Double, longitude: Double, photoPath: String?, isMeterReplacement: Boolean): Result<Unit> {
        val subscriber = dao.getSubscriberById(subscriberId) ?: return Result.failure(Exception("لم يتم العثور على بيانات هذا المشترك على الجهاز."))
        if (!isMeterReplacement && currentReading < subscriber.lastReading) return Result.failure(Exception("القراءة الحالية يجب أن تكون أكبر من أو تساوي القراءة السابقة."))
        val existing = dao.getReadingsForSubscriber(subscriberId)
        if (existing.any { it.currentReading == currentReading }) return Result.failure(Exception("تم حفظ القراءة نفسها محلياً مسبقاً."))
        val today = currentDateTime().substringBefore(" ")
        if (existing.any { it.readingDate.startsWith(today) } && !isMeterReplacement) return Result.failure(Exception("توجد قراءة محلية لهذا العداد اليوم."))
        dao.insertPendingReading(PendingReadingEntity(subscriberId = subscriberId, currentReading = currentReading, readingDate = currentDateTime(), notes = notes, latitude = latitude, longitude = longitude, photoPath = photoPath, status = "pending", isMeterReplacement = isMeterReplacement))
        return Result.success(Unit)
    }

    suspend fun savePayment(subscriberId: String, amount: Double, paymentMethod: String, notes: String): Boolean {
        if (amount <= 0) return false
        dao.insertPendingPayment(PendingPaymentEntity(subscriberId = subscriberId, amount = amount, paymentMethod = paymentMethod, notes = notes, status = "pending"))
        return true
    }

    suspend fun refreshSubscribers(): Result<Unit> = runCatching {
        ensureActiveAccessToken()
        val profile = api.getProfile().user
        val branchId = activeBranchId().takeIf { it in profile.branchIds } ?: profile.branchIds.firstOrNull()
            ?: throw IllegalStateException("لا توجد فروع ميدانية مسموحة لهذا الحساب.")
        prefs.edit().putLong("active_branch_id", branchId).apply()
        val response = api.getSubscribers(branchId)
        dao.clearAllSubscribers()
        dao.insertSubscribers(response.items.map { item ->
            SubscriberEntity(id = item.id.toString(), meterId = item.meterId.toString(), refNumber = item.subscriberNo, name = item.fullName, phone = item.phone, address = item.address ?: "", branchId = item.branchId.toString(), lastReading = item.lastReading?.toDoubleOrNull() ?: 0.0, lastReadingDate = item.lastReadingAt ?: "", balance = 0.0)
        })
    }

    suspend fun syncAllData(onProgress: (Int, String) -> Unit): Result<String> = runCatching {
        ensureActiveAccessToken()
        val branchId = activeBranchId()
        if (branchId <= 0) throw IllegalStateException("حدّث قائمة المشتركين أولاً لتحديد الفرع المسموح.")
        val readings = dao.getPendingReadingsWithStatus("pending") + dao.getPendingReadingsWithStatus("failed")
        if (readings.isEmpty()) return@runCatching "لا توجد قراءات معلقة للمزامنة."
        var synced = 0
        var failed = 0
        readings.forEachIndexed { index, reading ->
            onProgress(((index * 100) / readings.size).coerceAtLeast(5), "جارٍ إرسال قراءة ${index + 1} من ${readings.size}…")
            try {
                dao.updateReadingStatus(reading.localId, "syncing")
                val image = reading.photoPath?.let { loadImagePayload(it) }
                val response = api.createReading(MobileReadingRequest(
                    clientOperationId = reading.clientReferenceId,
                    branchId = branchId,
                    subscriberId = reading.subscriberId.toLong(),
                    meterId = subscriberMeterId(reading.subscriberId),
                    readingValue = String.format(Locale.US, "%.3f", reading.currentReading),
                    readingAt = localDateToUtc(reading.readingDate),
                    captureMethod = if (image == null) "manual" else "photo",
                    isMeterReplacement = reading.isMeterReplacement,
                    fieldVerified = reading.latitude != 0.0 || reading.longitude != 0.0,
                    latitude = reading.latitude.takeIf { it != 0.0 },
                    longitude = reading.longitude.takeIf { it != 0.0 },
                    imageData = image?.first,
                    imageMime = image?.second,
                    notes = reading.notes.ifBlank { null }
                ))
                if (response.id > 0) { dao.updateReadingStatus(reading.localId, "synced"); synced++ } else { dao.updateReadingStatus(reading.localId, "failed"); failed++ }
            } catch (_: Exception) {
                dao.updateReadingStatus(reading.localId, "failed")
                failed++
            }
        }
        onProgress(100, "اكتمل إرسال القراءات: $synced ناجحة، $failed تحتاج إعادة محاولة.")
        if (synced > 0) dao.clearSyncedReadings()
        "تمت مزامنة $synced قراءة. بقيت $failed قراءة للمراجعة أو إعادة المحاولة. التحصيلات الميدانية ستُفعّل في مرحلة مالية لاحقة."
    }

    private suspend fun subscriberMeterId(subscriberId: String): Long {
        val meterId = dao.getSubscriberById(subscriberId)?.meterId?.toLongOrNull()
        return meterId ?: throw IllegalStateException("معرف العداد غير متاح محلياً. حدّث قائمة المشتركين ثم أعد المحاولة.")
    }

    private fun currentDateTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun loadImagePayload(path: String): Pair<String, String>? {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 0 || file.length() > 5 * 1024 * 1024) return null
        val mime = when (file.extension.lowercase(Locale.US)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) to mime
    }
    private fun localDateToUtc(value: String): String {
        val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(value) ?: Date()
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsed)
    }
}
