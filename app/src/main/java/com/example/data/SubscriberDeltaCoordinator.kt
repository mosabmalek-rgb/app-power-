package com.example.data

import com.example.data.api.FieldReadingApi
import com.example.data.api.MobileSubscriberDeltaResponse
import com.example.data.api.MobileSubscriberDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Safe server-authoritative subscribers sync. The lock serializes WorkManager and UI refresh
 * in-process. Every page is persisted atomically with its cursor by Room.
 */
class SubscriberDeltaCoordinator(
    private val dao: FieldReadingDao,
    private val api: () -> FieldReadingApi
) {
    private companion object {
        val syncLock = Mutex()
        const val MAX_PAGES = 10_000
    }

    suspend fun refresh(
        tenantId: Long,
        userId: Long,
        branchId: Long,
        expectedScope: String,
        assertActiveScope: () -> Unit,
        onCacheInvalidated: () -> Unit = {}
    ) = syncLock.withLock {
        require(tenantId > 0 && userId > 0 && branchId > 0)
        val scope = "$tenantId:$userId:$branchId"
        require(scope == expectedScope) { "Active account scope changed" }
        val stored = dao.readDeltaState()
        check(stored == null || stored.scopeKey == scope) { "Foreign cached subscriber scope" }

        var cursor = stored?.cursor
        var bootstrap = stored == null
        var retryBootstrap = false
        var pages = 0
        var previousVersion: String? = stored?.scopeVersion
        var previousCycle: String? = stored?.cycleKey
        while (true) {
            assertActiveScope()
            check(++pages <= MAX_PAGES) { "Exceeded subscriber sync page limit" }
            val page = try {
                api().getSubscribersDelta(
                    branchId = branchId,
                    limit = 100,
                    cursor = if (bootstrap) null else cursor,
                    bootstrap = if (bootstrap) "true" else null
                )
            } catch (error: HttpException) {
                // Rejected identity/permission or stale server scope must immediately
                // conceal the previously published rows, even if re-bootstrap later fails.
                if (error.code() == 401 || error.code() == 403) onCacheInvalidated()
                val recoverable = parseBootstrapRequired(error)
                if (!recoverable || retryBootstrap) throw error
                onCacheInvalidated()
                // Reset the locally staged snapshot and cursor on first NEW bootstrap page,
                // not on a 409 response. Keep the published snapshot untouched until completion.
                bootstrap = true
                cursor = null
                previousVersion = null
                previousCycle = null
                retryBootstrap = true
                continue
            }
            assertActiveScope()
            validatePage(page,tenantId,userId,branchId,cursor,bootstrap,previousVersion,previousCycle)
            val newRows = page.upserts.map { row -> row.asEntity(page.currentCycle.key) }
            val ids = page.deletes.map(Long::toString)
            // Room applies the page AND opaque cursor in a single transaction.
            dao.commitDeltaPage(
                scopeKey = scope,
                requestCursor = if (bootstrap) null else cursor,
                mode = page.mode,
                scopeVersion = page.scopeVersion,
                cycleKey = page.currentCycle.key,
                rows = newRows,
                deletes = ids,
                nextCursor = page.nextCursor,
                hasMore = page.hasMore
            )
            assertActiveScope()
            cursor = page.nextCursor
            bootstrap = false
            previousVersion = page.scopeVersion
            previousCycle = page.currentCycle.key
            if (!page.hasMore) break
        }
    }

    private fun parseBootstrapRequired(e: HttpException): Boolean {
        if (e.code() != 409 && e.code() != 400) return false
        val body = e.response()?.errorBody()?.string() ?: return false
        val data = runCatching { JSONObject(body) }.getOrNull() ?: return false
        val code = data.optJSONObject("error")?.optString("code")
        return data.optBoolean("requiresBootstrap", false) &&
            code in setOf("CURSOR_EXPIRED", "CURSOR_SCOPE_CHANGED", "BOOTSTRAP_REQUIRED", "CURSOR_INVALID")
    }
}

internal fun validatePage(
    response: MobileSubscriberDeltaResponse,
    tenantId: Long,
    userId: Long,
    branchId: Long,
    cursor: String?,
    bootstrap: Boolean,
    previousScopeVersion: String?,
    previousCycleKey: String?
) {
    require(!response.requiresBootstrap)
    require(response.tenantId == tenantId && response.userId == userId &&
        response.branchId == branchId) { "Server returned foreign tenant/user/branch" }
    require(response.scopeVersion.matches(Regex("[0-9a-f]{64}")))
    require(response.currentCycle.key.isNotBlank())
    require(response.nextCursor.isNotBlank() && response.nextCursor.length <= 2048)
    require(response.transferredCount == response.upserts.size + response.deletes.size)
    require(response.upserts.size <= 100 && response.deletes.size <= 100)
    require(response.deletes.all { it > 0 } && response.deletes.distinct().size == response.deletes.size)
    require(response.upserts.all { it.id > 0 && it.branchId == branchId })
    require(response.upserts.map { it.id }.distinct().size == response.upserts.size)
    require(response.upserts.none { it.id in response.deletes })
    if (bootstrap) {
        require(cursor == null && response.mode == "bootstrap")
    } else {
        require(response.mode in setOf("bootstrap", "delta"))
        require(previousScopeVersion == response.scopeVersion &&
            previousCycleKey == response.currentCycle.key) { "Scope or cycle changed mid-stream" }
    }
    if (response.hasMore) {
        require(response.nextCursor != cursor) { "Delta cursor made no progress" }
    }
}

internal fun MobileSubscriberDto.asEntity(cycleKey: String): SubscriberEntity {
    require(lastReading == null || lastReading.toDoubleOrNull()?.isFinite() == true) {
        "Invalid subscriber reading number"
    }
    val reading = currentCycleReading
    return SubscriberEntity(
        id=id.toString(),refNumber=subscriberNo,name=fullName,phone=phone,
        address=address ?: "",branchId=branchId.toString(),
        lastReading=lastReading?.toDoubleOrNull() ?: 0.0,
        lastReadingDate=lastReadingAt ?: "",balance=0.0,
        hasReadingInCurrentCycle=hasReadingInCurrentCycle,
        cycleReadingValue=reading?.readingValue,cycleReadingAt=reading?.readingAt,
        cycleReadingStatus=reading?.status,
        currentCycleKey=if(hasReadingInCurrentCycle)cycleKey else null
    )
}
