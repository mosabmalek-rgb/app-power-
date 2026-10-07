package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Base64
import com.example.BuildConfig
import com.example.data.FieldReadingDao
import com.example.data.OfflineSyncPolicy
import com.example.data.PendingPaymentEntity
import com.example.data.PendingReadingEntity
import com.example.data.PendingSyncEventEntity
import com.example.data.ReadingCycleType
import com.example.data.SubscriberEntity
import com.example.data.api.FieldReadingApi
import com.example.data.api.MobileApiClient
import com.example.data.api.MobileChangePasswordRequest
import com.example.data.api.MobileLoginRequest
import com.example.data.api.MobileReadingRequest
import com.example.data.api.MobileSessionStore
import com.example.data.api.MobileSyncEventRequest
import com.example.data.api.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SyncSummary(
    val synced: Int,
    val retryable: Int,
    val needsReview: Int
) {
    fun message(): String = "تمت مزامنة $synced قراءة. بقيت $retryable قراءة ستُعاد محاولتها تلقائياً، و$needsReview قراءة تحتاج مراجعة الفني."
}

class FieldReadingRepository(
    private val context: Context,
    private val dao: FieldReadingDao,
    private val sessionStore: MobileSessionStore = MobileSessionStore(context)
) {
    private fun requireSubscriptionForOffline() { if (!sessionStore.subscriptionAllowsOfflineNow()) throw IllegalStateException("انتهت صلاحية اشتراك PowerHub. يلزم تجديد الاشتراك والاتصال بالإنترنت.") }

    private val remoteConfig = RemoteConfigRepository(context)
    private var api: FieldReadingApi = MobileApiClient.create(sessionStore, remoteConfig.activeApiUrl())
    private val prefs = context.getSharedPreferences("field_reading_prefs", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = sessionStore.isLoggedIn()
    fun mustChangePassword(): Boolean = prefs.getBoolean("must_change_password", false)
    fun isSubscriptionValidOffline(): Boolean = sessionStore.subscriptionAllowsOfflineNow()

    fun hasLocalPin(): Boolean = sessionStore.hasLocalPin()
    fun setLocalPin(pin: String) = sessionStore.setLocalPin(pin)
    fun verifyLocalPin(pin: String): Boolean = sessionStore.verifyLocalPin(pin)
    fun getUserName(): String = prefs.getString("user_name", "") ?: ""
    fun getUserRole(): String = prefs.getString("user_role", "") ?: ""
    fun getCompanyName(): String = prefs.getString("company_name", "PowerHub") ?: "PowerHub"
    fun getSavedUsername(): String = prefs.getString("saved_username", "") ?: ""
    private fun activeBranchId(): Long = prefs.getLong("active_branch_id", -1L)

    fun getReadingCycleType(): ReadingCycleType {
        val typeName = prefs.getString("reading_cycle_type", ReadingCycleType.MONTHLY.name) ?: ReadingCycleType.MONTHLY.name
        return try {
            ReadingCycleType.valueOf(typeName)
        } catch (e: Exception) {
            ReadingCycleType.MONTHLY
        }
    }

    fun setReadingCycleType(type: ReadingCycleType) {
        prefs.edit().putString("reading_cycle_type", type.name).apply()
    }

    fun getWeeklyCycleStartDay(): Int {
        return prefs.getInt("reading_cycle_weekly_day", java.util.Calendar.SATURDAY)
    }

    fun setWeeklyCycleStartDay(day: Int) {
        prefs.edit().putInt("reading_cycle_weekly_day", day).apply()
    }

    private suspend fun ensureActiveAccessToken() {
        refreshApiBaseUrl()
        if (!sessionStore.needsAccessRefresh()) return
        val refreshToken = sessionStore.refreshToken() ?: throw IllegalStateException("انتهت جلسة الموبايل. سجّل الدخول مجدداً.")
        sessionStore.save(api.refresh(com.example.data.api.MobileRefreshRequest(refreshToken)))
    }

    private suspend fun refreshApiBaseUrl() {
        if (remoteConfig.refreshIfNeeded().getOrDefault(false)) {
            api = MobileApiClient.create(sessionStore, remoteConfig.activeApiUrl())
        }
    }

    private fun extractErrorMessage(e: HttpException): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        if (!body.isNullOrBlank()) {
            val matched = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            if (!matched.isNullOrBlank()) return matched
        }
        return when (e.code()) {
            401 -> "بيانات الدخول غير صحيحة. يرجى التحقق من البريد وكلمة المرور."
            403 -> "غير مصرح بالدخول (الحساب ليس لديه صلاحية محصل ميداني، أو اشتراك المنصة منتهي، أو الحساب غير مرتبط بفرع)."
            404 -> "نقطة تسجيل الدخول غير متوفرة على الخادم."
            else -> "حدث خطأ أثناء الاتصال بالخادم (${e.code()})."
        }
    }

    suspend fun login(email: String, password: String, remember: Boolean): Result<Unit> = runCatching {
        refreshApiBaseUrl()
        val session = try {
            api.login(MobileLoginRequest(email = email.trim().lowercase(), password = password, deviceLabel = "Android Field Reader"))
        } catch (e: HttpException) {
            throw Exception(extractErrorMessage(e))
        } catch (e: IOException) {
            throw Exception("تعذر الاتصال بالخادم. يرجى التحقق من اتصال الإنترنت.")
        }
        sessionStore.save(session)
        prefs.edit().apply {
            putString("user_name", session.user.name ?: email.substringBefore("@"))
            putString("user_role", session.user.role)
            putString("company_name", "PowerHub")
            putLong("active_branch_id", session.user.branchIds.firstOrNull() ?: -1L)
            putBoolean("must_change_password", session.user.mustChangePassword)
            if (remember) putString("saved_username", email.trim().lowercase()) else remove("saved_username")
            apply()
        }
    }

    suspend fun changeTemporaryPassword(newPassword: String): Result<Unit> = runCatching {
        ensureActiveAccessToken()
        api.changePassword(MobileChangePasswordRequest(newPassword))
        prefs.edit().putBoolean("must_change_password", false).apply()
    }

    fun clearSession() {
        sessionStore.clear()
        prefs.edit().clear().apply()
    }

    val subscribersFlow: Flow<List<SubscriberEntity>> = dao.getAllSubscribersFlow()
    val pendingReadingsFlow: Flow<List<PendingReadingEntity>> = dao.getAllPendingReadingsFlow()
    val pendingPaymentsFlow: Flow<List<PendingPaymentEntity>> = dao.getAllPendingPaymentsFlow()
    fun getSubscriberFlow(id: String): Flow<SubscriberEntity?> = dao.getSubscriberByIdFlow(id)

    suspend fun saveReading(subscriberId: String, currentReading: Double, notes: String, latitude: Double, longitude: Double, photoPath: String?, isReadingReset: Boolean, fieldVerified: Boolean = true): Result<Unit> {
        requireSubscriptionForOffline()
        val subscriber = dao.getSubscriberById(subscriberId) ?: return Result.failure(Exception("لم يتم العثور على بيانات هذا المشترك على الجهاز."))
        if (!isReadingReset && currentReading < subscriber.lastReading) return Result.failure(Exception("القراءة الحالية يجب أن تكون أكبر من أو تساوي القراءة السابقة."))
        val existing = dao.getReadingsForSubscriber(subscriberId)
        if (existing.any { it.currentReading == currentReading }) return Result.failure(Exception("تم حفظ القراءة نفسها محلياً مسبقاً."))
        val today = currentDateTime().substringBefore(" ")
        if (existing.any { it.readingDate.startsWith(today) } && !isReadingReset) return Result.failure(Exception("توجد قراءة محلية لهذا المشترك اليوم."))
        dao.insertPendingReading(PendingReadingEntity(subscriberId = subscriberId, currentReading = currentReading, readingDate = currentDateTime(), notes = notes, latitude = latitude, longitude = longitude, photoPath = photoPath, status = "pending", isReadingReset = isReadingReset, fieldVerified = fieldVerified))
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
        val allItems = buildList { var cursor: String? = null; do { val response = api.getSubscribers(branchId, 100, cursor); addAll(response.items); cursor = response.nextCursor } while (!cursor.isNullOrBlank()) }
        dao.clearAllSubscribers()
        dao.insertSubscribers(allItems.map { item ->
            SubscriberEntity(id = item.id.toString(), refNumber = item.subscriberNo, name = item.fullName, phone = item.phone, address = item.address ?: "", branchId = item.branchId.toString(), lastReading = item.lastReading?.toDoubleOrNull() ?: 0.0, lastReadingDate = item.lastReadingAt ?: "", balance = 0.0)
        })
    }

    suspend fun syncAllData(onProgress: (Int, String) -> Unit): Result<SyncSummary> {
        requireSubscriptionForOffline()
        val outcome = runCatching {
            ensureActiveAccessToken()
            val branchId = activeBranchId()
            if (branchId <= 0) throw IllegalStateException("حدّث قائمة المشتركين أولاً لتحديد الفرع المسموح.")
            flushPendingSyncEvents()
            val readings = dao.getPendingReadingsWithStatus("pending") + dao.getPendingReadingsWithStatus("retryable")
            if (readings.isEmpty()) {
                val summary = SyncSummary(0, 0, 0)
                queueSyncEvent(branchId, "sync_cycle", "success", summary)
                return@runCatching summary
            }
            var synced = 0
            var retryable = 0
            var needsReview = 0
            readings.forEachIndexed { index, reading ->
                onProgress(((index * 100) / readings.size).coerceAtLeast(5), "جارٍ إرسال قراءة ${index + 1} من ${readings.size}…")
                try {
                    dao.updateReadingSyncState(reading.localId, "syncing", reading.retryCount, null, System.currentTimeMillis())
                    val image = reading.photoPath?.let { loadImagePayload(it) ?: throw PermanentSyncException("تعذر العثور على صورة القراءة أو تجاوزت 5 ميغابايت.") }
                    val response = api.createReading(MobileReadingRequest(reading.clientReferenceId, branchId, reading.subscriberId.toLong(), String.format(Locale.US, "%.3f", reading.currentReading), localDateToUtc(reading.readingDate), if (image == null) "manual" else "photo", reading.isReadingReset, reading.fieldVerified, reading.latitude.takeIf { it != 0.0 }, reading.longitude.takeIf { it != 0.0 }, image?.first, image?.second, reading.notes.ifBlank { null }))
                    if (response.id > 0) { dao.updateReadingSyncState(reading.localId, "synced", reading.retryCount, null, System.currentTimeMillis()); synced++ }
                    else {
                        val message = "رفض الخادم تأكيد القراءة؛ ستُعاد المحاولة تلقائياً."
                        dao.updateReadingSyncState(reading.localId, "retryable", reading.retryCount + 1, message, System.currentTimeMillis())
                        queueSyncEvent(branchId, "sync_error", "retryable", SyncSummary(0, 1, 0), "READING_SYNC_UNCONFIRMED", message)
                        retryable++
                    }
                } catch (error: Exception) {
                    val issue = classifySyncFailure(error)
                    dao.updateReadingSyncState(reading.localId, issue.status, reading.retryCount + 1, issue.message, System.currentTimeMillis())
                    queueSyncEvent(branchId, "sync_error", issue.status, SyncSummary(0, if (issue.status == "needs_review") 0 else 1, if (issue.status == "needs_review") 1 else 0), if (issue.status == "needs_review") "READING_SYNC_REVIEW" else "READING_SYNC_RETRY", issue.message)
                    if (issue.status == "needs_review") needsReview++ else retryable++
                }
            }
            onProgress(100, "اكتمل إرسال القراءات: $synced ناجحة، $retryable ستُعاد تلقائياً، $needsReview تحتاج مراجعة.")
            if (synced > 0) dao.clearSyncedReadings()
            val summary = SyncSummary(synced, retryable, needsReview)
            queueSyncEvent(branchId, "sync_cycle", when { needsReview > 0 -> "needs_review"; retryable > 0 -> "partial"; else -> "success" }, summary)
            summary
        }
        if (outcome.isFailure) {
            activeBranchId().takeIf { it > 0 }?.let { branchId -> queueSyncEvent(branchId, "sync_error", "retryable", SyncSummary(0, 0, 0), "SYNC_CYCLE_ERROR", outcome.exceptionOrNull()?.message) }
        }
        return outcome
    }

    suspend fun requeueReadingForReview(localId: Int) = dao.requeueReadingForReview(localId)

    private suspend fun queueSyncEvent(branchId: Long, eventType: String, status: String, summary: SyncSummary, errorCode: String? = null, errorMessage: String? = null) {
        dao.insertPendingSyncEvent(PendingSyncEventEntity(branchId = branchId, eventType = eventType, status = status, syncedCount = summary.synced, retryableCount = summary.retryable, needsReviewCount = summary.needsReview, errorCode = errorCode, errorMessage = errorMessage?.take(1500)))
        runCatching { flushPendingSyncEvents() }
    }

    private suspend fun flushPendingSyncEvents() {
        dao.getPendingSyncEvents().forEach { event ->
            api.createSyncEvent(MobileSyncEventRequest(event.eventKey, event.branchId, event.eventType, event.status, event.syncedCount, event.retryableCount, event.needsReviewCount, event.errorCode, event.errorMessage, BuildConfig.VERSION_NAME, deviceLabel(), epochToUtc(event.createdAt)))
            dao.deletePendingSyncEvent(event.localId)
        }
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

    private fun epochToUtc(value: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(value))

    private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(160).ifBlank { "Android Field Reader" }

    private class PermanentSyncException(message: String) : IllegalStateException(message)

    private suspend fun logoutRemote() { runCatching { if (sessionStore.isLoggedIn()) { ensureActiveAccessToken(); api.logout() } } }

    suspend fun logout() { logoutRemote(); clearSession() }

    private fun classifySyncFailure(error: Exception) = when (error) {
        is PermanentSyncException -> com.example.data.OfflineSyncIssue("needs_review", error.message ?: "تحتاج القراءة إلى مراجعة الفني.")
        is IOException -> OfflineSyncPolicy.forNetworkFailure()
        is HttpException -> when (error.code()) {
            else -> OfflineSyncPolicy.forHttpStatus(error.code())
        }
        else -> com.example.data.OfflineSyncIssue("retryable", error.message?.take(300) ?: "تعذر إرسال القراءة؛ ستُعاد المحاولة تلقائياً.")
    }
}
