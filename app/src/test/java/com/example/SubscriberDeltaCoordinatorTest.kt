package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.FieldReadingDatabase
import com.example.data.SubscriberDeltaCoordinator
import com.example.data.SubscriberEntity
import com.example.data.api.FieldReadingApi
import com.example.data.api.MobileReadingCycleBoundsDto
import com.example.data.api.MobileSubscriberDeltaResponse
import com.example.data.api.MobileSubscriberDto
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response

/** Pure code-side fake HTTP plus in-memory Room: no real network, device, or hosted data. */
@RunWith(RobolectricTestRunner::class)
class SubscriberDeltaCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.dao()
    private val scope = "1:2:7"
    private val version = "a".repeat(64)
    private val calls = mutableListOf<Triple<Long, String?, String?>>()

    @After fun close() = db.close()

    private fun subscriber(id: Long) =
        MobileSubscriberDto(id, "S$id", "Subscriber $id", "123", null, 7L, null, null)

    private fun row(id: String) = SubscriberEntity(
        id = id, refNumber = "S$id", name = "Existing $id", phone = "",
        address = "", branchId = "7", lastReading = 0.0,
        lastReadingDate = "", balance = 0.0
    )

    private fun page(mode: String, next: String, more: Boolean = false,
        upserts: List<MobileSubscriberDto> = emptyList(), cycle: String = "cycle-1",
        tenant: Long = 1L
    ) = MobileSubscriberDeltaResponse(
        tenantId = tenant, userId = 2L, branchId = 7L, mode = mode,
        scopeVersion = version,
        currentCycle = MobileReadingCycleBoundsDto(
            cycle, "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z", "November"
        ),
        upserts = upserts, deletes = emptyList(), nextCursor = next,
        hasMore = more, requiresBootstrap = false, serverTime = "2026-11-23T00:00:00Z",
        transferredCount = upserts.size
    )

    private fun fake(vararg replies: Any): FieldReadingApi {
        val responses = ArrayDeque(replies.toList())
        return Proxy.newProxyInstance(
            FieldReadingApi::class.java.classLoader,
            arrayOf(FieldReadingApi::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getSubscribersDelta" -> {
                    check(responses.isNotEmpty()) { "Unexpected additional request" }
                    requireNotNull(args)
                    calls += Triple(args[0] as Long, args[2] as String?, args[3] as String?)
                    when (val item = responses.removeFirst()) {
                        is Throwable -> throw item
                        else -> item
                    }
                }
                "toString" -> "Mock Delta API"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> throw AssertionError("Unexpected non-Delta API invocation")
            }
        } as FieldReadingApi
    }

    private suspend fun refresh(api: FieldReadingApi, guard: () -> Unit = {}) =
        SubscriberDeltaCoordinator(dao) { api }.refresh(1L, 2L, 7L, scope, guard)

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
    )

    @Test fun interruptedBootstrapResumesFromRoomCursorWithoutPublishingPartialData() = runBlocking {
        dao.insertSubscribers(listOf(row("99")))
        val interrupted = fake(
            page("bootstrap", "cursor-one", more = true, upserts = listOf(subscriber(1))),
            IOException("synthetic network interruption")
        )
        assertTrue(runCatching { refresh(interrupted) }.isFailure)
        assertNotNull(dao.getSubscriberById("99"))
        assertNull(dao.getSubscriberById("1"))
        assertEquals("cursor-one", dao.readDeltaState()?.cursor)
        assertEquals("bootstrap", dao.readDeltaState()?.phase)
        assertEquals(1, dao.stagedSubscriberCount())
        assertNull(calls[0].second)
        assertEquals("true", calls[0].third)
        assertEquals("cursor-one", calls[1].second)
        val resumed = fake(page("bootstrap", "cursor-two", upserts = listOf(subscriber(2))))
        refresh(resumed)
        assertEquals("cursor-one", calls[2].second)
        assertNull(dao.getSubscriberById("99"))
        assertNotNull(dao.getSubscriberById("1"))
        assertNotNull(dao.getSubscriberById("2"))
        assertEquals("delta", dao.readDeltaState()?.phase)
        assertEquals("cursor-two", dao.readDeltaState()?.cursor)
        assertEquals(0, dao.stagedSubscriberCount())
    }

    @Test fun expiredCursorAllowsExactlyOneSafeBootstrapRestart() = runBlocking {
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("9")), emptyList(), "cursor-old", false)
        val mock = fake(
            httpError(409, """{"requiresBootstrap":true,"error":{"code":"CURSOR_EXPIRED"}}"""),
            page("bootstrap", "cursor-fresh", upserts = listOf(subscriber(1)))
        )
        refresh(mock)
        assertEquals("cursor-old", calls[0].second)
        assertNull(calls[1].second)
        assertEquals("true", calls[1].third)
        assertNull(dao.getSubscriberById("9"))
        assertNotNull(dao.getSubscriberById("1"))
        assertEquals("cursor-fresh", dao.readDeltaState()?.cursor)
    }

    @Test fun forbiddenResponseDoesNotBootstrapOrModifyLiveRows() = runBlocking {
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("9")), emptyList(), "cursor-old", false)
        val mock = fake(httpError(401, """{"error":{"code":"UNAUTHORIZED"}}"""))
        assertTrue(runCatching { refresh(mock) }.isFailure)
        assertEquals(1, calls.size)
        assertEquals("cursor-old", dao.readDeltaState()?.cursor)
        assertNotNull(dao.getSubscriberById("9"))
    }

    @Test fun sessionRevocationBetweenResponseAndCommitLeavesRoomUnchanged() = runBlocking {
        var checks = 0
        val guard = {
            checks++
            if (checks == 2) throw IllegalStateException("synthetic logout")
        }
        assertTrue(runCatching {
            refresh(fake(page("bootstrap", "cursor-one", upserts = listOf(subscriber(1)))), guard)
        }.isFailure)
        assertEquals(2, checks)
        assertNull(dao.readDeltaState())
        assertNull(dao.getSubscriberById("1"))
        assertEquals(0, dao.stagedSubscriberCount())
    }

    @Test fun changedTenantAndCycleRejectWithoutAdvancingCursor() = runBlocking {
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("9")), emptyList(), "cursor-old", false)
        assertTrue(runCatching { refresh(fake(page("delta", "cursor-new", tenant = 99L))) }.isFailure)
        assertTrue(runCatching { refresh(fake(page("delta", "cursor-new", cycle = "cycle-2"))) }.isFailure)
        assertEquals("cursor-old", dao.readDeltaState()?.cursor)
        assertNotNull(dao.getSubscriberById("9"))
    }

    @Test fun zeroChangeDeltaPreservesSnapshotAndAdvancesCursor() = runBlocking {
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("9")), emptyList(), "cursor-old", false)
        refresh(fake(page("delta", "cursor-new")))
        assertNotNull(dao.getSubscriberById("9"))
        assertEquals("cursor-new", dao.readDeltaState()?.cursor)
    }
}
