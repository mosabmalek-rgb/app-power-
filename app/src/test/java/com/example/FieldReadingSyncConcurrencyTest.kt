package com.example

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.FieldReadingDatabase
import com.example.data.SubscriberCacheTrust
import com.example.data.SubscriberEntity
import com.example.data.PendingPaymentEntity
import com.example.data.PendingReadingEntity
import com.example.data.api.FieldReadingApi
import com.example.data.api.MobileMeResponse
import com.example.data.api.MobileReadingCycleBoundsDto
import com.example.data.api.MobileReadingCycleConfigDto
import com.example.data.api.MobileReadingCycleResponse
import com.example.data.api.MobileReadingRequest
import com.example.data.api.MobileReadingResponse
import com.example.data.api.MobileSessionResponse
import com.example.data.api.MobileSessionStore
import com.example.data.api.MobileSubscriberDeltaResponse
import com.example.data.api.MobileSubscriberDto
import com.example.data.api.MobileSyncEventResponse
import com.example.data.api.MobileUser
import com.example.data.repository.FieldReadingRepository
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Local-only integration against the REAL repository + Room, with a synthetic
 * suspend Retrofit interface. Two repository instances simulate UI and Worker.
 * No emulator, hosted HTTP, SMS or payments are invoked.
 */
@RunWith(RobolectricTestRunner::class)
class FieldReadingSyncConcurrencyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
        .allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("field_reading_prefs", Context.MODE_PRIVATE)
    private val store = MobileSessionStore(context)
    private val user = MobileUser(
        2L, "Synthetic technician", "field", 1L, listOf(7L),
        "2099-01-01T00:00:00Z"
    )
    private val operationKey = "stable-reading-operation-key"
    private val calls = AtomicInteger(0)
    private val deltaCalls = AtomicInteger(0)
    private val seenUpload = CompletableDeferred<MobileReadingRequest>()
    private val heldAck = CompletableDeferred<MobileReadingResponse>()
    @Volatile private var replyImmediatelyAfterFirst = false
    @Volatile private var failDelta = false
    @Volatile private var revokeDelta = false
    @Volatile private var profileNoBranches = false
    @Volatile private var profileForeignIdentity = false
    @Volatile private var profileMovedBranch = false
    private val postedKeys = java.util.Collections.synchronizedList(mutableListOf<String>())

    private fun authenticate() {
        store.clear()
        prefs.edit().clear().commit()
        val json = """{"tenantId":1,"sessionId":99,"sub":"2"}"""
        val payload = Base64.encodeToString(
            json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        store.save(MobileSessionResponse("fake.$payload.signature", "fake-refresh", 3600L, user))
        prefs.edit().putLong("profile_user_id", 2L).putLong("profile_tenant_id", 1L)
            .putLong("active_branch_id", 7L)
            .putString("subscriber_cache_scope", "1:2:7").commit()
    }

    private fun fakeApi(): FieldReadingApi = Proxy.newProxyInstance(
        FieldReadingApi::class.java.classLoader,
        arrayOf(FieldReadingApi::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "createReading" -> {
                val request = args!![0] as MobileReadingRequest
                postedKeys += request.clientOperationId
                val attempt = calls.incrementAndGet()
                seenUpload.complete(request)
                if (attempt == 1) {
                    // Suspend without blocking an Android/Coroutine thread.
                    // This continuation belongs to the original Retrofit suspend call.
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args.last() as Continuation<MobileReadingResponse>
                    val waitForAck: suspend () -> MobileReadingResponse = { heldAck.await() }
                    waitForAck.startCoroutine(continuation)
                    COROUTINE_SUSPENDED
                } else {
                    check(replyImmediatelyAfterFirst) { "Duplicate concurrent reading upload" }
                    MobileReadingResponse(100L, "accepted", null, true)
                }
            }
            "getProfile" -> MobileMeResponse(
                when {
                    profileForeignIdentity -> user.copy(tenantId = 99L)
                    profileNoBranches -> user.copy(branchIds = emptyList())
                    profileMovedBranch -> user.copy(branchIds = listOf(8L))
                    else -> user
                }
            )
            "getReadingCycle" -> MobileReadingCycleResponse(
                MobileReadingCycleConfigDto("MONTHLY", 1, 1, "Asia/Gaza"),
                MobileReadingCycleBoundsDto(
                    "cycle-1", "2026-11-01T00:00:00Z",
                    "2026-12-01T00:00:00Z", "November"
                )
            )
            "getSubscribersDelta" -> {
                if (revokeDelta) {
                    throw HttpException(Response.error<Any>(
                        403, """{"error":{"code":"BRANCH_ACCESS_DENIED"}}""".toResponseBody()
                    ))
                }
                if (failDelta) throw java.io.IOException("synthetic Delta outage")
                val number = deltaCalls.incrementAndGet()
                val bootstrap = args!![3] == "true"
                MobileSubscriberDeltaResponse(
                    tenantId = 1L, userId = 2L, branchId = 7L,
                    mode = if (bootstrap) "bootstrap" else "delta",
                    scopeVersion = "a".repeat(64),
                    currentCycle = MobileReadingCycleBoundsDto(
                        "cycle-1", "2026-11-01T00:00:00Z",
                        "2026-12-01T00:00:00Z", "November"
                    ),
                    upserts = if (bootstrap) listOf(
                        MobileSubscriberDto(15L, "S15", "Synthetic subscriber", "", null,
                            7L, "5", null)
                    ) else emptyList(),
                    deletes = emptyList(), nextCursor = "cursor-$number",
                    hasMore = false, requiresBootstrap = false,
                    serverTime = "2026-11-23T00:00:00Z",
                    transferredCount = if (bootstrap) 1 else 0
                )
            }
            "createSyncEvent" -> MobileSyncEventResponse(501L, false)
            "logout" -> Unit
            "toString" -> "Synthetic mobile API"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> error("Unexpected API call: ${method.name}")
        }
    } as FieldReadingApi

    private fun repository(api: FieldReadingApi): FieldReadingRepository {
        val repo = FieldReadingRepository(context, db.dao(), store)
        val field = FieldReadingRepository::class.java.getDeclaredField("api")
        field.isAccessible = true
        field.set(repo, api)
        return repo
    }

    private suspend fun seedPendingReading() {
        db.dao().insertPendingReading(PendingReadingEntity(
            subscriberId = "15", currentReading = 5.0,
            readingDate = "2026-11-23 12:00", notes = "",
            latitude = 0.0, longitude = 0.0, photoPath = null,
            status = "pending", clientReferenceId = operationKey,
            readingValueText = "5.0", readingCycleKey = "cycle-1"
        ))
    }

    @After fun close() {
        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        store.clear()
        prefs.edit().clear().commit()
        db.close()
    }

    @Test fun uiAndWorkerUploadsAreSerializedAndOnlyOneReadingIsPosted() = runBlocking {
        authenticate()
        seedPendingReading()
        val api = fakeApi()
        val ui = repository(api)
        val worker = repository(api)

        val manual = async { ui.syncAllData { _, _ -> } }
        withTimeout(10000) { seenUpload.await() }
        assertEquals("syncing", db.dao().getPendingReadingsWithStatus("syncing").single().status)
        val periodic = async(start = CoroutineStart.UNDISPATCHED) {
            worker.syncAllData { _, _ -> }
        }
        assertFalse("worker must wait behind manual upload", periodic.isCompleted)
        assertEquals(1, calls.get())

        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        assertTrue(withTimeout(15000) { manual.await() }.isSuccess)
        val result = withTimeout(15000) { periodic.await() }
        assertTrue(result.isSuccess)
        assertEquals("no duplicate HTTP posts", 1, calls.get())
        assertEquals(listOf(operationKey), postedKeys.toList())
        assertEquals(0, db.dao().anyReadingCount())
        assertEquals("Worker still polls Delta with an empty outbox", 2, deltaCalls.get())
    }

    @Test fun cancelledInFlightReadingKeepsKeyAndCanBeSafelyReplayed() = runBlocking {
        authenticate()
        seedPendingReading()
        val api = fakeApi()
        val repo = repository(api)

        val interrupted = async { repo.syncAllData { _, _ -> } }
        withTimeout(10000) { seenUpload.await() }
        interrupted.cancelAndJoin()
        val retriable = db.dao().getPendingReadingsWithStatus("retryable").single()
        assertEquals(operationKey, retriable.clientReferenceId)
        assertEquals(1, calls.get())

        replyImmediatelyAfterFirst = true
        val recovered = withTimeout(15000) { repo.syncAllData { _, _ -> } }
        assertTrue(recovered.isSuccess)
        assertEquals(2, calls.get())
        assertEquals(listOf(operationKey, operationKey), postedKeys.toList())
        assertEquals(0, db.dao().anyReadingCount())
    }

    @Test fun logoutWaitsForActiveUploadAndPreservesUnsentOfflineOutbox() = runBlocking {
        authenticate()
        seedPendingReading()
        db.dao().insertPendingReading(PendingReadingEntity(
            subscriberId = "16", currentReading = 6.0, readingDate = "2026-11-23 12:01",
            notes = "review", latitude = 0.0, longitude = 0.0, photoPath = null,
            status = "needs_review", clientReferenceId = "needs-review-original"
        ))
        db.dao().insertPendingPayment(PendingPaymentEntity(
            subscriberId = "15", amount = 21.0, paymentMethod = "CASH",
            notes = "offline", status = "pending", clientReferenceId = "payment-original"
        ))
        val api = fakeApi()
        val repo = repository(api)
        val upload = async { repo.syncAllData { _, _ -> } }
        withTimeout(10000) { seenUpload.await() }
        val logout = async(start = CoroutineStart.UNDISPATCHED) { repo.logout() }
        assertFalse("logout must wait until upload settles", logout.isCompleted)
        assertTrue(store.isLoggedIn())

        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        assertTrue(withTimeout(15000) { upload.await() }.isSuccess)
        withTimeout(15000) { logout.await() }
        assertFalse(store.isLoggedIn())
        assertEquals("needs-review-original",
            db.dao().getPendingReadingsWithStatus("needs_review").single().clientReferenceId)
        assertEquals("payment-original",
            db.dao().getPendingPaymentsWithStatus("pending").single().clientReferenceId)
        assertEquals(1, calls.get())
    }
    @Test fun cancelledWorkerWaitingForUploadMutexDoesNotDuplicateReading() = runBlocking {
        authenticate()
        seedPendingReading()
        val api = fakeApi()
        val manualRepo = repository(api)
        val workerRepo = repository(api)
        val manual = async { manualRepo.syncAllData { _, _ -> } }
        withTimeout(10000) { seenUpload.await() }
        val waitingWorker = async(start = CoroutineStart.UNDISPATCHED) {
            workerRepo.syncAllData { _, _ -> }
        }
        assertFalse(waitingWorker.isCompleted)
        waitingWorker.cancelAndJoin()
        assertTrue(waitingWorker.isCancelled)
        assertEquals(1, calls.get())

        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        assertTrue(withTimeout(15000) { manual.await() }.isSuccess)
        assertEquals(0, db.dao().anyReadingCount())
        val nextWorker = withTimeout(15000) {
            workerRepo.syncAllData { _, _ -> }
        }
        assertTrue(nextWorker.isSuccess)
        assertEquals(1, calls.get())
        assertEquals(listOf(operationKey), postedKeys.toList())
    }

    @Test fun deltaFailureNeverReportsCompleteOrDeletesAcknowledgedReading() = runBlocking {
        authenticate()
        seedPendingReading()
        val repo = repository(fakeApi())
        failDelta = true
        val progress = mutableListOf<Int>()
        val upload = async { repo.syncAllData { percent, _ -> progress.add(percent) } }
        withTimeout(10000) { seenUpload.await() }
        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        val result = withTimeout(15000) { upload.await() }
        assertTrue(result.isFailure)
        assertFalse("no 100% before successful Delta", progress.contains(100))
        val confirmed = db.dao().getPendingReadingsWithStatus("synced").single()
        assertEquals(operationKey, confirmed.clientReferenceId)
        assertEquals(1, calls.get())
    }

    @Test fun manualRefreshWaitsForWorkerUploadAndCannotPublishMidFlight() = runBlocking {
        authenticate()
        seedPendingReading()
        val api = fakeApi()
        val worker = repository(api)
        val screen = repository(api)
        val runningUpload = async { worker.syncAllData { _, _ -> } }
        withTimeout(10000) { seenUpload.await() }
        val manualRefresh = async(start = CoroutineStart.UNDISPATCHED) {
            screen.refreshSubscribers()
        }
        assertFalse("manual Delta must wait for in-flight upload", manualRefresh.isCompleted)
        assertEquals(0, deltaCalls.get())

        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        assertTrue(withTimeout(15000) { runningUpload.await() }.isSuccess)
        assertTrue(withTimeout(15000) { manualRefresh.await() }.isSuccess)
        assertEquals(1, calls.get())
        assertEquals("upload and then manual refresh", 2, deltaCalls.get())
        assertEquals(0, db.dao().anyReadingCount())
    }

    @Test fun revokedBranchDuringManualDeltaQuarantinesCacheButPreservesOfflineWork() = runBlocking {
        authenticate()
        val subscriber = SubscriberEntity(
            id = "15", refNumber = "S15", name = "Local", phone = "",
            address = "", branchId = "7", lastReading = 5.0,
            lastReadingDate = "", balance = 0.0
        )
        db.dao().insertSubscribers(listOf(subscriber))
        seedPendingReading()
        db.dao().insertPendingPayment(PendingPaymentEntity(
            subscriberId = "15", amount = 12.0, paymentMethod = "CASH",
            notes = "", status = "pending", clientReferenceId = "payment-stable"
        ))
        revokeDelta = true
        val repo = repository(fakeApi())
        val result = withTimeout(15000) { repo.refreshSubscribers() }
        assertTrue(result.isFailure)
        val trust = SubscriberCacheTrust(prefs)
        assertTrue("quarantine must survive repository recreation", trust.isQuarantined())
        assertTrue("UI subscriber data must be invisible", repo.subscribersFlow.first().isEmpty())
        assertTrue(repo.pendingReadingsFlow.first().isEmpty())
        assertTrue(repo.pendingPaymentsFlow.first().isEmpty())
        assertEquals(operationKey,
            db.dao().getPendingReadingsWithStatus("pending").single().clientReferenceId)
        assertEquals("payment-stable",
            db.dao().getPendingPaymentsWithStatus("pending").single().clientReferenceId)
        assertEquals("Local", db.dao().getSubscriberById("15")?.name)
        val afterRestart = repository(fakeApi())
        assertTrue(afterRestart.subscribersFlow.first().isEmpty())
    }

    @Test fun cancelledManualRefreshWaitingOnUploadNeverRunsDelta() = runBlocking {
        authenticate()
        seedPendingReading()
        val api = fakeApi()
        val worker = repository(api)
        val manual = repository(api)
        val runningUpload = async { worker.syncAllData { _, _ -> } }
        withTimeout(10000) { seenUpload.await() }
        val pendingManual = async(start = CoroutineStart.UNDISPATCHED) {
            manual.refreshSubscribers()
        }
        assertFalse(pendingManual.isCompleted)
        pendingManual.cancelAndJoin()
        assertTrue(pendingManual.isCancelled)
        assertEquals(0, deltaCalls.get())

        heldAck.complete(MobileReadingResponse(100L, "accepted", null, false))
        assertTrue(withTimeout(15000) { runningUpload.await() }.isSuccess)
        assertEquals("only uploader Delta reached API", 1, deltaCalls.get())
        assertEquals(1, calls.get())
    }

    @Test fun successfulProfileWithNoBranchesQuarantinesWithoutDeletingOutbox() = runBlocking {
        authenticate()
        seedPendingReading()
        profileNoBranches = true
        val repo = repository(fakeApi())
        val outcome = withTimeout(15000) { repo.refreshSubscribers() }
        assertTrue(outcome.isFailure)
        assertTrue(SubscriberCacheTrust(prefs).isQuarantined())
        assertTrue(repo.pendingReadingsFlow.first().isEmpty())
        assertEquals(operationKey,
            db.dao().getPendingReadingsWithStatus("pending").single().clientReferenceId)
        assertEquals(0, deltaCalls.get())
        assertTrue(repository(fakeApi()).subscribersFlow.first().isEmpty())
    }

    @Test fun foreignProfileIdentityIsDeniedBeforeCacheMutationOrDelta() = runBlocking {
        authenticate()
        seedPendingReading()
        db.dao().insertSubscribers(listOf(SubscriberEntity(
            id = "15", refNumber = "S15", name = "Keep local", phone = "",
            address = "", branchId = "7", lastReading = 5.0, lastReadingDate = "",
            balance = 0.0
        )))
        profileForeignIdentity = true
        val repo = repository(fakeApi())
        val outcome = withTimeout(15000) { repo.refreshSubscribers() }
        assertTrue(outcome.isFailure)
        assertTrue(SubscriberCacheTrust(prefs).isQuarantined())
        assertEquals("1:2:7", prefs.getString("subscriber_cache_scope", null))
        assertEquals("Keep local", db.dao().getSubscriberById("15")?.name)
        assertEquals(operationKey,
            db.dao().getPendingReadingsWithStatus("pending").single().clientReferenceId)
        assertEquals(0, deltaCalls.get())
    }

    @Test fun branchRemovedAndReassignedFailsClosedWhileOfflineOutboxExists() = runBlocking {
        authenticate()
        seedPendingReading()
        profileMovedBranch = true
        val repo = repository(fakeApi())
        val outcome = withTimeout(15000) { repo.refreshSubscribers() }
        assertTrue(outcome.isFailure)
        assertTrue(SubscriberCacheTrust(prefs).isQuarantined())
        assertEquals("1:2:7", prefs.getString("subscriber_cache_scope", null))
        assertEquals(7L, prefs.getLong("active_branch_id", -1))
        assertEquals(operationKey,
            db.dao().getPendingReadingsWithStatus("pending").single().clientReferenceId)
        assertEquals(0, deltaCalls.get())
    }

}
