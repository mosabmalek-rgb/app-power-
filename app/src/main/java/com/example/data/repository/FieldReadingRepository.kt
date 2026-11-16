package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Base64
import com.example.BuildConfig
import com.example.data.FieldReadingDao
import com.example.data.OfflineSyncPolicy
import com.example.data.ReadingValuePrecision
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
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import org.json.JSONObject
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
    private val profilePrefs = context.getSharedPreferences("field_profile_prefs", Context.MODE_PRIVATE)

    private fun currentProfileUserId(): Long? {
        prefs.getLong("profile_user_id", -1L).takeIf { it > 0L }?.let { return it }

        // Existing installs may still have a valid access token but predate the
        // local profile_user_id preference. The server's signed JWT carries the
        // authenticated numeric user id in "sub"; use it only to key local UI data.
        val encodedPayload = sessionStore.accessToken()?.split('.')?.getOrNull(1)
            ?.takeIf { it.isNotBlank() } ?: return null
        val payload = runCatching {
            val bytes = android.util.Base64.decode(
                encodedPayload,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            JSONObject(String(bytes, Charsets.UTF_8)).optString("sub").toLongOrNull()
        }.getOrNull()
        return payload?.takeIf { it > 0L }
    }

    fun getProfileImagePath(): String? {
        val userId = currentProfileUserId() ?: return null
        return profilePrefs.getString("profile_image_path_$userId", null)?.takeIf { java.io.File(it).isFile }
    }

    fun setProfileImagePath(path: String) {
        val userId = currentProfileUserId()
            ?: throw IllegalStateException("تعذر تحديد حساب المستخدم لحفظ صورة الملف. تحقق من تسجيل الدخول ثم أعد المحاولة.")
        profilePrefs.edit().putString("profile_image_path_$userId", path).apply()
    }

    val sessionRevokedEvents = sessionStore.sessionRevokedEvents

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

    fun getSavedCycleRange(): com.example.data.CycleDateRange? {
        val key = prefs.getString("server_cycle_key", null) ?: return null
        val start = prefs.getLong("server_cycle_start", -1L)
        val end = prefs.getLong("server_cycle_end", -1L)
        val label = prefs.getString("server_cycle_label", key) ?: key
        val zone = prefs.getString("server_cycle_timezone", "Asia/Gaza") ?: "Asia/Gaza"
        if (start < 0L || end <= start) return null
        return com.example.data.CycleDateRange(getReadingCycleType(), start, end, label, label, zone, key)
    }

    suspend fun refreshReadingCycleConfiguration(): Result<com.example.data.CycleDateRange> = runCatching {
        ensureActiveAccessToken()
        val branchId = activeBranchId()
        if (branchId <= 0L) throw IllegalStateException("لا يوجد فرع ميداني محدد.")
        val response = api.getReadingCycle(branchId)
        val type = runCatching { ReadingCycleType.valueOf(response.cycle.cycleType.uppercase(Locale.US)) }
            .getOrElse { throw IllegalStateException("نوع دورة القراءة الوارد من المنصة غير معروف.") }
        val bounds = response.currentCycle
        val range = com.example.data.ReadingCycleCalculator.fromServerBounds(type, bounds.key, bounds.startAt, bounds.endAt, bounds.label, response.cycle.timezone)
            ?: throw IllegalStateException("حدود دورة القراءة الواردة من المنصة غير صالحة.")
        prefs.edit().apply {
            putString("reading_cycle_type", type.name)
            putInt("reading_cycle_weekly_day", response.cycle.weeklyStartDay)
            putString("server_cycle_key", bounds.key)
            putLong("server_cycle_start", range.startMillis)
            putLong("server_cycle_end", range.endMillis)
            putString("server_cycle_label", bounds.label)
            putString("server_cycle_timezone", response.cycle.timezone)
            apply()
        }
        range
    }

    suspend fun getCycleSyncedReadingCount(fromMillis: Long, toMillisInclusive: Long): Result<Int> = runCatching {
        ensureActiveAccessToken()
        val branchId = activeBranchId()
        if (branchId <= 0L) throw IllegalStateException("لا يوجد فرع ميداني محدد.")
        fun iso(millis: Long): String = java.time.Instant.ofEpochMilli(millis).toString()
        // CycleDateRange.endMillis is inclusive; the API window is [from, to).
        val exclusiveEnd = Math.addExact(toMillisInclusive, 1L)
        api.getCycleSyncStats(branchId, iso(fromMillis), iso(exclusiveEnd)).syncedReadingCount
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

    private data class ServerApiError(val code: String?, val message: String?, val details: String?)

    private fun parseServerApiError(e: HttpException): ServerApiError? {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val root = JSONObject(body)
            val error = root.optJSONObject("error") ?: root
            val code = error.optString("code").takeIf { it.isNotBlank() && it != "null" }
            val message = error.optString("message").takeIf { it.isNotBlank() && it != "null" }
            val details = error.optJSONObject("details")?.let { data ->
                val issues = data.optJSONArray("issues")
                if (issues != null && issues.length() > 0) {
                    (0 until issues.length()).mapNotNull { index ->
                        val issue = issues.optJSONObject(index) ?: return@mapNotNull null
                        val path = issue.optString("path").takeIf { it.isNotBlank() }
                        val issueMessage = issue.optString("message").takeIf { it.isNotBlank() }
                        listOfNotNull(path, issueMessage).joinToString(": ").takeIf { it.isNotBlank() }
                    }.joinToString("; ").takeIf { it.isNotBlank() }
                } else null
            }
            ServerApiError(code, message, details)
        }.getOrNull()
    }

    private fun extractErrorMessage(e: HttpException): String {
        val serverError = parseServerApiError(e)
        if (!serverError?.message.isNullOrBlank()) return serverError?.message.orEmpty()
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
        // Never delete another account's Room data or pending outbox. The v8
        // cache is attributed only when the previous account+tenant+site
        // identity is independently proven; otherwise it stays quarantined.
        val ownerPrefs=context.getSharedPreferences("field_cache_owner",Context.MODE_PRIVATE)
        val newBranchId=session.user.branchIds.firstOrNull()?:-1L
        require(newBranchId>0L) { "لا يوجد فرع مصرح به للتطبيق الميداني." }
        val newOwner="${session.user.tenantId}:${session.user.id}:$newBranchId"
        val previousOwner=ownerPrefs.getString("owner",null)
        val previousUserId=prefs.getLong("profile_user_id",-1L)
        val previousTenantId=prefs.getLong("profile_tenant_id",-1L)
        val previousBranchId=prefs.getLong("active_branch_id",-1L)
        val knownSameOwner=previousOwner==newOwner || (
            previousOwner==null && previousUserId==session.user.id &&
            previousTenantId==session.user.tenantId && previousBranchId==newBranchId
        )
        val legacyUnsent=dao.legacyUnsentReadingCount()+dao.legacyUnsentPaymentCount()+
            dao.legacySyncEventCount()
        if(!knownSameOwner && legacyUnsent>0){
            throw IllegalStateException(
                "هناك عمليات قديمة بلا هوية موثقة محفوظة بأمان. سجل الدخول بالحساب السابق لاستردادها قبل تبديل الحساب."
            )
        }
        if(knownSameOwner)dao.claimVerifiedLegacyScope(newOwner)
        val checkpoint=dao.getLatestCompletedSyncState(newOwner)
        val hasLegacy=dao.visibleSubscriberCount(newOwner,"legacy-seeded")>0
        dao.setActiveScope(com.example.data.MobileActiveScopeEntity(
            scopeKey=newOwner,
            scopeVersion=checkpoint?.scopeVersion?:if(hasLegacy)"legacy-seeded" else "not-bootstrapped",
            status=if(checkpoint!=null || hasLegacy)"ready" else "bootstrapping",
        ))
        if(!knownSameOwner)prefs.edit().remove("last_subscriber_refresh_at").apply()
        ownerPrefs.edit().putString("owner", newOwner).apply()
        sessionStore.save(session)
        prefs.edit().apply {
            putString("user_name", session.user.name ?: email.substringBefore("@"))
            putLong("profile_user_id", session.user.id)
            putLong("profile_tenant_id", session.user.tenantId)
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

    suspend fun clearSession() {
        // Atomic identity deactivation in Room occurs before removing the
        // credentials: no cached subscriber from another account is displayed.
        dao.clearActiveScope()
        sessionStore.clear()
        prefs.edit().clear().apply()
    }

    val subscribersFlow: Flow<List<SubscriberEntity>> = dao.getAllSubscribersFlow()
    val pendingReadingsFlow: Flow<List<PendingReadingEntity>> = dao.getAllPendingReadingsFlow()
    val pendingPaymentsFlow: Flow<List<PendingPaymentEntity>> = dao.getAllPendingPaymentsFlow()
    fun getSubscriberFlow(id: String): Flow<SubscriberEntity?> = dao.getSubscriberByIdFlow(id)

    suspend fun saveReading(subscriberId: String, currentReading: Double, notes: String, latitude: Double, longitude: Double, photoPath: String?, isReadingReset: Boolean, cycleRange: com.example.data.CycleDateRange, fieldVerified: Boolean = true, readingValueText: String? = null): Result<Unit> {
        requireSubscriptionForOffline()
        requireLocalOwner()
        val active=dao.getActiveScope()
        check(active?.scopeKey==currentScopeKey() && active.status=="ready") { "نطاق البيانات المحلية غير جاهز." }
        val subscriber = dao.getSubscriberById(subscriberId) ?: return Result.failure(Exception("لم يتم العثور على بيانات هذا المشترك على الجهاز."))
        if (subscriber.branchId != activeBranchId().toString()) {
            return Result.failure(IllegalStateException("هذا المشترك لا ينتمي إلى الفرع المصرح به على الجهاز."))
        }
        val normalizedReadingText = ReadingValuePrecision.normalizeForSync(currentReading, readingValueText)
            ?: return Result.failure(Exception("القراءة يجب أن تكون رقماً صحيحاً أو تحتوي على منزلة أو منزلتين عشريتين كحد أقصى."))
        if (!isReadingReset && currentReading < subscriber.lastReading) return Result.failure(Exception("القراءة الحالية يجب أن تكون أكبر من أو تساوي القراءة السابقة."))
        if (subscriber.hasReadingInCurrentCycle && subscriber.currentCycleKey == cycleRange.cycleKey) {
            return Result.failure(Exception("تم تسجيل قراءة لهذا المشترك في دورة القراءة الحالية. حدّث القائمة ولا تسجل قراءة أخرى."))
        }
        if (System.currentTimeMillis() !in cycleRange.startMillis..cycleRange.endMillis) {
            return Result.failure(Exception("انتهت صلاحية نسخة دورة القراءة المحفوظة. اتصل بالإنترنت لتحديث الدورة قبل تسجيل القراءة."))
        }
        val cycleKey = cycleRange.cycleKey ?: return Result.failure(Exception("تعذر التحقق من هوية دورة القراءة الحالية. اتصل بالإنترنت وحدّث بيانات الدورة قبل تسجيل القراءة."))
        val zone = TimeZone.getTimeZone(cycleRange.timeZoneId)
        val startText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(Date(cycleRange.startMillis))
        val endExclusiveText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(Date(cycleRange.endMillis + 1L))
        val reading = PendingReadingEntity(subscriberId = subscriberId, currentReading = currentReading, readingDate = currentDateTime(), notes = notes, latitude = latitude, longitude = longitude, photoPath = photoPath, status = "pending", isReadingReset = isReadingReset, fieldVerified = fieldVerified, readingValueText = normalizedReadingText, readingCycleKey = cycleKey, scopeKey = currentScopeKey())
        if (!dao.insertPendingReadingIfNoReadingInCycle(reading, cycleKey, startText, endExclusiveText)) {
            return Result.failure(Exception("توجد قراءة محفوظة لهذا المشترك في دورة القراءة الحالية، محلية أو على الخادم."))
        }
        return Result.success(Unit)
    }

    suspend fun savePayment(subscriberId: String, amount: Double, paymentMethod: String, notes: String): Boolean {
        if (amount <= 0) return false
        requireLocalOwner()
        val active=dao.getActiveScope()
        if(active?.scopeKey!=currentScopeKey() || active.status!="ready")return false
        val subscriber = dao.getSubscriberById(subscriberId) ?: return false
        if (subscriber.branchId != activeBranchId().toString()) return false
        dao.insertPendingPayment(PendingPaymentEntity(subscriberId = subscriberId, amount = amount, paymentMethod = paymentMethod, notes = notes, status = "pending", scopeKey = currentScopeKey()))
        return true
    }

    // All Repository instances (foreground + WorkManager) share this lock.
    // Database transactions protect checkpoints across process crashes.
    private companion object {
        val subscriberRefreshMutex = kotlinx.coroutines.sync.Mutex()
        const val MAX_DELTA_PAGES_PER_RUN = 500
    }

    private fun currentScopeKey(): String {
        val tenantId=prefs.getLong("profile_tenant_id",-1L)
        val userId=currentProfileUserId()?:-1L
        val branchId=activeBranchId()
        check(tenantId>0L && userId>0L && branchId>0L)
        return "$tenantId:$userId:$branchId"
    }

    private fun requireLocalOwner() {
        val expected=currentScopeKey()
        val recorded=context.getSharedPreferences("field_cache_owner",Context.MODE_PRIVATE)
            .getString("owner",null)
        check(recorded==expected) {
            "بيانات هذا الجهاز تخص حساباً أو فرعاً آخر. أعد تسجيل الدخول قبل العمل الميداني."
        }
    }

    fun lastSubscriberRefreshAt(): Long=prefs.getLong("last_subscriber_refresh_at",0L)

    suspend fun refreshSubscribersIfStale(maxAgeMillis: Long=2*60*60*1000L): Result<Unit> {
        if(!isLoggedIn())return Result.success(Unit)
        requireLocalOwner()
        val last=lastSubscriberRefreshAt()
        if(last>0 && System.currentTimeMillis()-last in 0 until maxAgeMillis)
            return Result.success(Unit)
        return refreshSubscribers()
    }

    suspend fun refreshSubscribers(): Result<Unit> = subscriberRefreshMutex.withLock {
        runCatching {
            requireLocalOwner()
            ensureActiveAccessToken()
            val profile=api.getProfile().user
            val branchId=activeBranchId()
            val scopeKey=currentScopeKey()
            val tenantId=prefs.getLong("profile_tenant_id",-1L)
            val userId=currentProfileUserId()?:-1L
            require(profile.id==userId && profile.tenantId==tenantId &&
                branchId>0L && branchId in profile.branchIds) {
                "تغير نطاق الحساب أو الفرع؛ توقفت المزامنة لحماية البيانات المحلية."
            }
            val active=dao.getActiveScope()
            require(active?.scopeKey==scopeKey) {
                "نطاق قاعدة البيانات لا يطابق الحساب المسجل."
            }
            // Persisted pending bootstrap takes precedence over the last full
            // checkpoint, even after a process crash between Delta pages.
            val pending=dao.getIncompleteBootstrapState(scopeKey)
            val inProgressDelta=dao.getIncompleteDeltaState(scopeKey,active.scopeVersion)
            val complete=dao.getLatestCompletedSyncState(scopeKey)
            var cursor=pending?.cursor?:inProgressDelta?.cursor?:complete?.cursor
            var needsBootstrap=cursor==null
            var restarted=false
            val seenCursors=mutableSetOf<String>()
            var pages=0
            var totalTransferred=0
            while(true) {
                require(pages++<MAX_DELTA_PAGES_PER_RUN) {
                    "تجاوزت المزامنة الحد الآمن للصفحات؛ المؤشر المحفوظ لم يتقدم دون معاملة Room."
                }
                val response=try {
                    api.getSubscribersDelta(branchId,100,cursor,if(needsBootstrap)true else null)
                } catch(e:HttpException) {
                    val code=parseServerApiError(e)?.code
                    if(e.code() in listOf(400,409) &&
                        code in setOf("CURSOR_EXPIRED","CURSOR_SCOPE_CHANGED",
                            "CURSOR_INVALID","BOOTSTRAP_REQUIRED") && !restarted) {
                        // Server invalidated the previous scope/cursor. Keep
                        // pending work and quarantine the old subscriber view.
                        dao.quarantineActiveScope(scopeKey)
                        dao.forgetIncompleteBootstrap(scopeKey)
                        dao.forgetIncompleteDelta(scopeKey)
                        cursor=null
                        needsBootstrap=true
                        restarted=true
                        continue
                    }
                    throw e
                }
                if(response.requiresBootstrap) {
                    require(!restarted) { "كرر الخادم طلب إعادة التهيئة؛ أوقفت المزامنة دون فقدان البيانات." }
                    dao.quarantineActiveScope(scopeKey)
                    dao.forgetIncompleteBootstrap(scopeKey)
                    dao.forgetIncompleteDelta(scopeKey)
                    cursor=null
                    needsBootstrap=true
                    restarted=true
                    continue
                }
                require(response.tenantId==tenantId && response.userId==userId &&
                    response.branchId==branchId &&
                    response.scopeVersion.length==64 &&
                    response.scopeVersion.all { it in '0'..'9' || it in 'a'..'f' }) {
                    "استجابة Delta لا تطابق هوية المستأجر أو الفني أو الفرع."
                }
                require(response.mode in listOf("bootstrap","delta")) {
                    "طور المزامنة غير موثوق."
                }
                if(needsBootstrap)require(response.mode=="bootstrap") {
                    "رفض تطبيق Delta قبل تهيئة كاملة."
                }
                require(response.nextCursor.isNotBlank() && response.nextCursor.length<=2048)
                require(!response.hasMore || (response.nextCursor!=cursor &&
                    seenCursors.add(response.nextCursor))) {
                    "حُلقة أو تكرار في المؤشرات؛ لم يتم تجاوز آخر صفحة مثبتة."
                }
                require(response.upserts.all { it.branchId==branchId }) {
                    "احتوت صفحة Delta على بيانات فرع آخر؛ لن تطبق محلياً."
                }
                val cycle=response.currentCycle
                require(cycle.key.isNotBlank() && cycle.startAt.isNotBlank() &&
                    cycle.endAt.isNotBlank()) {
                    "بيانات دورة القراءة غير مكتملة."
                }
                val serverTime=java.time.Instant.parse(response.serverTime).toEpochMilli()
                val upserts=response.upserts.map { item ->
                    val reading=item.currentCycleReading
                    com.example.data.SubscriberEntity(
                        id=item.id.toString(),
                        refNumber=item.subscriberNo,
                        name=item.fullName,
                        phone=item.phone,
                        address=item.address?:"",
                        branchId=branchId.toString(),
                        lastReading=item.lastReading?.toDoubleOrNull()?:0.0,
                        lastReadingDate=item.lastReadingAt?:"",
                        balance=0.0,
                        hasReadingInCurrentCycle=item.hasReadingInCurrentCycle,
                        cycleReadingValue=reading?.readingValue,
                        cycleReadingAt=reading?.readingAt,
                        cycleReadingStatus=reading?.status,
                        currentCycleKey=if(item.hasReadingInCurrentCycle)cycle.key else null,
                        scopeKey=scopeKey,scopeVersion=response.scopeVersion,
                    )
                }
                val deletes=response.deletes.map { value ->
                    require(value>0L) { "معرف حذف Delta غير صالح." }
                    value.toString()
                }
                // Data changes and the only resumable cursor are in ONE Room
                // transaction. Failure before/within it cannot advance state.
                dao.applyMobileDeltaPage(scopeKey,response.scopeVersion,
                    branchId.toString(),response.mode,upserts,deletes,
                    response.nextCursor,response.hasMore,serverTime)
                totalTransferred+=upserts.size+deletes.size
                cursor=response.nextCursor
                needsBootstrap=false
                if(!response.hasMore) {
                    // A subscriber or reading may have changed while the
                    // bootstrap pages were being downloaded. The signed final
                    // bootstrap cursor starts Delta at the frozen high-water:
                    // immediately reconcile subsequent changes before marking
                    // this refresh complete.
                    if(response.mode=="bootstrap")continue
                    prefs.edit().putLong("last_subscriber_refresh_at",serverTime).apply()
                    break
                }
            }
            android.util.Log.d("PowerHubSync",
                "Delta complete; pages=$pages records=$totalTransferred")
            Unit
        }
    }

    suspend fun syncAllData(onProgress: (Int, String) -> Unit): Result<SyncSummary> {
        requireSubscriptionForOffline()
        requireLocalOwner()
        val outcome = runCatching {
            ensureActiveAccessToken()
            val branchId = activeBranchId()
            if (branchId <= 0) throw IllegalStateException("حدّث قائمة المشتركين أولاً لتحديد الفرع المسموح.")
            val scopeKey=currentScopeKey()
            flushPendingSyncEvents()
            dao.resetInterruptedReadings(scopeKey)
            val readings = dao.getScopedPendingReadingsWithStatus(scopeKey,"pending") +
                dao.getScopedPendingReadingsWithStatus(scopeKey,"retryable")
            if (readings.isEmpty()) {
                val summary = SyncSummary(0, 0, 0)
                queueSyncEvent(branchId, "sync_cycle", "success", summary)
                return@runCatching summary
            }
            var synced = 0
            var retryable = 0
            var needsReview = 0
            readings.forEachIndexed { index, reading ->
                check(currentScopeKey()==scopeKey && dao.getActiveScope()?.scopeKey==scopeKey) {
                    "تغير الحساب أثناء إرسال القراءات؛ توقفت المزامنة دون إرسال سجل نطاق آخر."
                }
                val localSubscriber = dao.getSubscriberById(reading.subscriberId)
                if (localSubscriber == null || localSubscriber.branchId != branchId.toString()) {
                    dao.updateScopedReadingSyncState(
                        reading.localId,scopeKey, "needs_review", reading.retryCount,
                        "القراءة تخص مشتركاً غير موجود في الفرع الحالي؛ أُوقف إرسالها لحماية عزل البيانات.",
                        System.currentTimeMillis()
                    )
                    needsReview++
                    return@forEachIndexed
                }
                onProgress(((index * 100) / readings.size).coerceAtLeast(5), "جارٍ إرسال قراءة ${index + 1} من ${readings.size}…")
                try {
                    dao.updateScopedReadingSyncState(reading.localId,scopeKey, "syncing", reading.retryCount, null, System.currentTimeMillis())
                    val image = reading.photoPath?.let { loadImagePayload(it) ?: throw PermanentSyncException("تعذر العثور على صورة القراءة أو تجاوزت 5 ميغابايت.") }
                    check(currentScopeKey()==scopeKey && dao.getActiveScope()?.scopeKey==scopeKey) {
                        "تغير نطاق الحساب قبل الإرسال."
                    }
                    val response = api.createReading(MobileReadingRequest(reading.clientReferenceId, branchId, reading.subscriberId.toLong(), ReadingValuePrecision.normalizeForSync(reading.currentReading, reading.readingValueText)
                        ?: throw PermanentSyncException("القراءة يجب أن تكون رقماً صحيحاً أو تحتوي على منزلة أو منزلتين عشريتين كحد أقصى."), localDateToUtc(reading.readingDate), if (image == null) "manual" else "photo", reading.isReadingReset, reading.fieldVerified, reading.latitude.takeIf { it != 0.0 }, reading.longitude.takeIf { it != 0.0 }, image?.first, image?.second, reading.notes.ifBlank { null }))
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
                    val diagnosticCode = issue.errorCode
                        ?: if (issue.status == "needs_review") "READING_SYNC_REVIEW" else "READING_SYNC_RETRY"
                    queueSyncEvent(branchId, "sync_error", issue.status, SyncSummary(0, if (issue.status == "needs_review") 0 else 1, if (issue.status == "needs_review") 1 else 0), diagnosticCode, issue.message)
                    if (issue.status == "needs_review") needsReview++ else retryable++
                }
            }
            onProgress(100, "اكتمل إرسال القراءات: $synced ناجحة، $retryable ستُعاد تلقائياً، $needsReview تحتاج مراجعة.")
            if (synced > 0) {
                dao.clearSyncedReadingsForScope(scopeKey)
                // Refresh the server-authoritative meter baseline after successful uploads.
                // This keeps platform-entered readings and field-entered readings in sync
                // without discarding any unsynced local work.
                runCatching { refreshSubscribers() }
            }
            val summary = SyncSummary(synced, retryable, needsReview)
            queueSyncEvent(branchId, "sync_cycle", when { needsReview > 0 -> "needs_review"; retryable > 0 -> "partial"; else -> "success" }, summary)
            summary
        }
        if (outcome.isFailure) {
            activeBranchId().takeIf { it > 0 }?.let { branchId -> queueSyncEvent(branchId, "sync_error", "retryable", SyncSummary(0, 0, 0), "SYNC_CYCLE_ERROR", outcome.exceptionOrNull()?.message) }
        }
        return outcome
    }

    suspend fun requeueReadingForReview(localId: Int) {
        requireLocalOwner()
        dao.requeueScopedReadingForReview(localId,currentScopeKey())
    }

    private suspend fun queueSyncEvent(branchId: Long, eventType: String, status: String, summary: SyncSummary, errorCode: String? = null, errorMessage: String? = null) {
        dao.insertPendingSyncEvent(PendingSyncEventEntity(branchId = branchId, eventType = eventType, status = status, syncedCount = summary.synced, retryableCount = summary.retryable, needsReviewCount = summary.needsReview, errorCode = errorCode, errorMessage = errorMessage?.take(1500), scopeKey = currentScopeKey()))
        runCatching { flushPendingSyncEvents() }
    }

    private suspend fun flushPendingSyncEvents() {
        val scopeKey=currentScopeKey()
        dao.getScopedPendingSyncEvents(scopeKey).forEach { event ->
            check(currentScopeKey()==scopeKey && dao.getActiveScope()?.scopeKey==scopeKey) {
                "تغير الحساب أثناء رفع حدث المزامنة؛ أوقف الإرسال."
            }
            check(event.branchId==activeBranchId()) { "أوقفت إرسال حدث من نطاق فرع آخر." }
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
        is HttpException -> {
            val serverError = parseServerApiError(error)
            val fallback = OfflineSyncPolicy.forHttpStatus(error.code(), serverError?.code)
            val code = serverError?.code ?: "HTTP_${error.code()}"
            val message = serverError?.message?.takeIf { it.isNotBlank() } ?: fallback.message
            val details = serverError?.details?.takeIf { it.isNotBlank() }
            val diagnosticMessage = buildString {
                append(message)
                if (!details.isNullOrBlank()) append(" — ").append(details)
                append(" [").append(code).append("]")
            }.take(700)
            com.example.data.OfflineSyncIssue(fallback.status, diagnosticMessage, code)
        }
        else -> com.example.data.OfflineSyncIssue("retryable", error.message?.take(300) ?: "تعذر إرسال القراءة؛ ستُعاد المحاولة تلقائياً.")
    }
}
