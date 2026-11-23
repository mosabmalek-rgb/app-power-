package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.FieldReadingDatabase
import com.example.data.PendingReadingEntity
import com.example.data.SubscriberEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriberDeltaRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = database.dao()
    private fun row(id: String, name: String) = SubscriberEntity(
        id=id, refNumber="S$id", name=name, phone="", address="",
        branchId="7", lastReading=0.0, lastReadingDate="", balance=0.0
    )
    private val scope = "1:2:7"
    private val version = "a".repeat(64)

    @After fun tearDown() { database.close() }

    @Test fun bootstrapPagesRemainInvisibleUntilFinalAtomicPublish() = runBlocking {
        dao.insertSubscribers(listOf(row("9","old")))
        dao.commitDeltaPage(scope,null,"bootstrap",version,"cycle-1",
            listOf(row("1","first")),emptyList(),"cursor-a",true)
        assertNotNull(dao.getSubscriberById("9"))
        assertNull(dao.getSubscriberById("1"))
        assertEquals("cursor-a",dao.readDeltaState()?.cursor)
        assertEquals(1,dao.stagedSubscriberCount())
        dao.commitDeltaPage(scope,"cursor-a","bootstrap",version,"cycle-1",
            listOf(row("2","second")),emptyList(),"cursor-b",false)
        assertNull(dao.getSubscriberById("9"))
        assertNotNull(dao.getSubscriberById("1"))
        assertNotNull(dao.getSubscriberById("2"))
        assertEquals(0,dao.stagedSubscriberCount())
        assertEquals("delta",dao.readDeltaState()?.phase)
    }

    @Test fun deltaPageAndCursorAreAtomicAndLeaveOutboxUntouched() = runBlocking {
        dao.insertPendingReading(PendingReadingEntity(
            subscriberId="1",currentReading=17.0,readingDate="2026-11-01 12:00",
            notes="",latitude=0.0,longitude=0.0,photoPath=null,status="pending"
        ))
        dao.commitDeltaPage(scope,null,"bootstrap",version,"cycle-1",
            listOf(row("1","first")),emptyList(),"cursor-first",false)
        dao.commitDeltaPage(scope,"cursor-first","delta",version,"cycle-1",
            listOf(row("2","second")),listOf("1"),"cursor-second",false)
        assertNull(dao.getSubscriberById("1"))
        assertNotNull(dao.getSubscriberById("2"))
        assertEquals("cursor-second",dao.readDeltaState()?.cursor)
        assertEquals(1,dao.outstandingReadingCount())
    }

    @Test fun invalidCursorDoesNotDeleteRowsOrAdvanceCursor() = runBlocking {
        dao.commitDeltaPage(scope,null,"bootstrap",version,"cycle-1",
            listOf(row("1","safe")),emptyList(),"cursor-a",false)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                dao.commitDeltaPage(scope,"wrong-cursor","delta",version,"cycle-1",
                    listOf(row("2","should-rollback")),listOf("1"),"cursor-b",false)
            }
        }
        assertNotNull(dao.getSubscriberById("1"))
        assertNull(dao.getSubscriberById("2"))
        assertEquals("cursor-a",dao.readDeltaState()?.cursor)
    }

    @Test fun switchingScopeIsBlockedWhenAnyOfflineHistoryExists() = runBlocking {
        dao.insertPendingReading(PendingReadingEntity(
            subscriberId="1",currentReading=17.0,readingDate="2026-11-01 12:00",
            notes="",latitude=0.0,longitude=0.0,photoPath=null,status="pending"
        ))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { dao.clearSubscriberCacheForIdentityChange() }
        }
        assertEquals(1,dao.outstandingReadingCount())
    }
    @Test fun interruptedBootstrapResumesWithoutExposingPartialRows() = runBlocking {
        dao.insertSubscribers(listOf(row("90", "old-visible")))
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("1", "staged")), emptyList(), "cursor-one", true)

        // A simulated process restart reads the same persisted stage and cursor.
        val stateAfterRestart = dao.readDeltaState()
        assertEquals("bootstrap", stateAfterRestart?.phase)
        assertEquals("cursor-one", stateAfterRestart?.cursor)
        assertNotNull(dao.getSubscriberById("90"))
        assertNull(dao.getSubscriberById("1"))

        dao.commitDeltaPage(scope, stateAfterRestart?.cursor, "bootstrap", version, "cycle-1",
            listOf(row("2", "last")), emptyList(), "cursor-two", false)

        assertNull(dao.getSubscriberById("90"))
        assertNotNull(dao.getSubscriberById("1"))
        assertNotNull(dao.getSubscriberById("2"))
        assertEquals("delta", dao.readDeltaState()?.phase)
    }

    @Test fun invalidContinuationKeepsStagingAndCursorForRetry() = runBlocking {
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("1", "keep-staged")), emptyList(), "cursor-one", true)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                dao.commitDeltaPage(scope, "wrong-cursor", "bootstrap", version, "cycle-1",
                    listOf(row("2", "invalid")), emptyList(), "cursor-two", false)
            }
        }
        assertEquals(1, dao.stagedSubscriberCount())
        assertEquals("cursor-one", dao.readDeltaState()?.cursor)
        assertNull(dao.getSubscriberById("2"))
    }

    @Test fun foreignScopeCannotReplacePublishedRowsOrOutbox() = runBlocking {
        dao.insertPendingReading(PendingReadingEntity(
            subscriberId="1", currentReading=18.0, readingDate="2026-11-01 12:00",
            notes="", latitude=0.0, longitude=0.0, photoPath=null, status="pending"
        ))
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("1", "original")), emptyList(), "cursor-1", false)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                dao.commitDeltaPage("2:3:7", null, "bootstrap", version, "cycle-1",
                    listOf(row("4", "foreign")), emptyList(), "cursor-2", false)
            }
        }
        assertNotNull(dao.getSubscriberById("1"))
        assertNull(dao.getSubscriberById("4"))
        assertEquals("cursor-1", dao.readDeltaState()?.cursor)
        assertEquals(1, dao.outstandingReadingCount())
    }

    @Test fun interruptedUploadStatusIsEligibleForIdempotentRetry() = runBlocking {
        val operation = PendingReadingEntity(
            subscriberId = "1", currentReading = 19.0, readingDate = "2026-11-01 12:00",
            notes = "", latitude = 0.0, longitude = 0.0, photoPath = null, status = "pending",
            clientReferenceId = "test-operation-keep-same-key"
        )
        val localId = dao.insertPendingReading(operation).toInt()
        dao.updateReadingSyncState(localId, "syncing", 0, null, System.currentTimeMillis())
        val queued = dao.getPendingReadingsWithStatus("syncing")
        assertEquals(1, queued.size)
        assertEquals("test-operation-keep-same-key", queued.single().clientReferenceId)
        dao.updateReadingSyncState(localId, "retryable", 1, "synthetic cancellation", System.currentTimeMillis())
        val retry = dao.getPendingReadingsWithStatus("retryable")
        assertEquals(1, retry.size)
        assertEquals(queued.single().clientReferenceId, retry.single().clientReferenceId)
    }

    @Test fun failedStageReplacementKeepsPreviouslyPublishedSnapshot() = runBlocking {
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-1",
            listOf(row("1", "published")), emptyList(), "cursor-before", false)
        dao.commitDeltaPage(scope, null, "bootstrap", version, "cycle-2",
            listOf(row("2", "staged")), emptyList(), "cursor-after-page-one", true)
        assertNotNull(dao.getSubscriberById("1"))
        assertNull(dao.getSubscriberById("2"))
        assertEquals(1, dao.stagedSubscriberCount())
        assertEquals("cursor-after-page-one", dao.readDeltaState()?.cursor)
    }

}
