package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.FieldReadingDatabase
import com.example.data.MobileActiveScopeEntity
import com.example.data.PendingPaymentEntity
import com.example.data.PendingReadingEntity
import com.example.data.SubscriberEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** File-backed Room recovery; simulates elapsed time without a 26-hour sleep. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MobileDeltaOfflineRecoveryTest {
    private val scope = "10:7:101"
    private val other = "20:8:101"
    private val revision = "a".repeat(64)
    private fun sub(id: String) = SubscriberEntity(
        id=id,refNumber=id,name="Reader $id",phone="",address="",
        branchId="101",lastReading=10.0,lastReadingDate="",balance=0.0,
        scopeKey=scope,scopeVersion=revision
    )
    private fun reading(id: String) = PendingReadingEntity(
        subscriberId=id,currentReading=13.0,readingDate="2026-11-16 10:00",
        notes="",latitude=0.0,longitude=0.0,photoPath=null,
        status="pending",scopeKey=scope,clientReferenceId="operation-1001"
    )
    private fun open(context: Context,name: String) =
        Room.databaseBuilder(context,FieldReadingDatabase::class.java,name)
            .allowMainThreadQueries().build()

    @Test fun `room reopens 26 hours later from cache with the same delta cursor and unsent reading and payment`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="e51-reopen-26h-test.db"
        context.deleteDatabase(name)
        val yesterday=System.currentTimeMillis()-26L*60*60*1000
        try {
            open(context,name).use { db ->
                val dao=db.dao()
                dao.setActiveScope(MobileActiveScopeEntity(scopeKey=scope,
                    scopeVersion="not-bootstrapped",status="bootstrapping"))
                dao.applyMobileDeltaPage(scope,revision,"101","bootstrap",
                    listOf(sub("1001"),sub("1002")),emptyList(),"bootstrap-final",false,yesterday)
                dao.applyMobileDeltaPage(scope,revision,"101","delta",
                    listOf(sub("1002").copy(name="Updated")),emptyList(),"delta-42",false,yesterday)
                dao.insertPendingReading(reading("1001"))
                dao.insertPendingPayment(PendingPaymentEntity(
                    subscriberId="1001",amount=50.0,paymentMethod="CASH",
                    notes="",status="pending",clientReferenceId="payment-1001",scopeKey=scope))
            }
            // No network call is possible here; all assertions use restored Room.
            open(context,name).use { db ->
                val dao=db.dao()
                assertEquals("delta-42",dao.getLatestCompletedSyncState(scope)?.cursor)
                assertEquals("delta",dao.getLatestCompletedSyncState(scope)?.mode)
                assertEquals(yesterday,dao.getLatestCompletedSyncState(scope)?.lastAppliedAt)
                assertEquals(setOf("1001","1002"),dao.getAllSubscribersFlow().first().map { it.id }.toSet())
                assertEquals("Updated",dao.getSubscriberByIdFlow("1002").first()?.name)
                assertEquals("operation-1001",dao.getScopedPendingReadingsWithStatus(scope,"pending").single().clientReferenceId)
                assertEquals("payment-1001",dao.getScopedPendingPaymentsWithStatus(scope,"pending").single().clientReferenceId)
                assertNull(dao.getIncompleteBootstrapState(scope))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun `disconnect and process failure preserve last committed cursor and recover without duplicated work`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="e51-network-phase-recovery-test.db"
        context.deleteDatabase(name)
        try {
            open(context,name).use { db ->
                val dao=db.dao()
                dao.setActiveScope(MobileActiveScopeEntity(scopeKey=scope,
                    scopeVersion="not-bootstrapped",status="bootstrapping"))
                dao.applyMobileDeltaPage(scope,revision,"101","bootstrap",
                    listOf(sub("1001")),emptyList(),"page-1",false,1000L)
                dao.insertPendingReading(reading("1001"))
                // Failure before receipt: no Room write, cursor remains unchanged.
                assertEquals("page-1",dao.getSyncState(scope,revision)?.cursor)
                // Bad page after receipt is rejected without a cursor advance.
                assertTrue(runCatching {
                    dao.applyMobileDeltaPage(scope,revision,"101","delta",
                        listOf(sub("1002"),sub("1002")),emptyList(),"bad-page",true,2000L)
                }.isFailure)
                assertEquals("page-1",dao.getSyncState(scope,revision)?.cursor)
                // Simulate a crash AFTER insert, BEFORE committing the outer
                // Room transaction. Nested DAO transaction must roll back.
                assertTrue(runCatching {
                    db.withTransaction {
                        dao.applyMobileDeltaPage(scope,revision,"101","delta",
                            listOf(sub("1002")),emptyList(),"page-2",true,3000L)
                        error("injected interruption before commit")
                    }
                }.isFailure)
                assertNull(dao.getSubscriberByIdFlow("1002").first())
                assertEquals("page-1",dao.getSyncState(scope,revision)?.cursor)
            }
            open(context,name).use { db ->
                val dao=db.dao()
                assertEquals("page-1",dao.getSyncState(scope,revision)?.cursor)
                dao.applyMobileDeltaPage(scope,revision,"101","delta",
                    listOf(sub("1002")),emptyList(),"page-2",true,3000L)
                assertEquals("page-2",dao.getIncompleteDeltaState(scope,revision)?.cursor)
            }
            open(context,name).use { db ->
                val dao=db.dao()
                // Interruption after commit: stored cursor is page-2.
                assertEquals("page-2",dao.getIncompleteDeltaState(scope,revision)?.cursor)
                assertTrue(runCatching {
                    dao.applyMobileDeltaPage(scope,revision,"101","delta",
                        listOf(sub("1002")),emptyList(),"page-2",true,3001L)
                }.isFailure)
                dao.applyMobileDeltaPage(scope,revision,"101","delta",
                    emptyList(),emptyList(),"page-3",false,3002L)
                assertEquals("page-3",dao.getLatestCompletedSyncState(scope)?.cursor)
                assertEquals(setOf("1001","1002"),dao.getAllSubscribersFlow().first().map { it.id }.toSet())
                assertEquals(1,dao.getScopedPendingReadingsWithStatus(scope,"pending").size)
                dao.setActiveScope(MobileActiveScopeEntity(
                    scopeKey=other,scopeVersion=revision,status="ready"))
                assertTrue(dao.getAllSubscribersFlow().first().isEmpty())
                assertTrue(dao.getScopedPendingReadingsWithStatus(other,"pending").isEmpty())
                assertEquals(1,dao.getScopedPendingReadingsWithStatus(scope,"pending").size)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun `1000 cached subscribers apply and delta delete retains offline outbox`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context,FieldReadingDatabase::class.java)
            .allowMainThreadQueries().build().use { db ->
                val dao=db.dao()
                dao.setActiveScope(MobileActiveScopeEntity(scopeKey=scope,
                    scopeVersion="not-bootstrapped",status="bootstrapping"))
                // This is a local transaction stress check, not a network page.
                val rows=(1..1000).map { sub(it.toString()) }
                dao.applyMobileDeltaPage(scope,revision,"101","bootstrap",
                    rows,emptyList(),"initial",false,System.currentTimeMillis())
                assertEquals(1000,dao.getAllSubscribersFlow().first().size)
                dao.insertPendingReading(reading("1"))
                dao.applyMobileDeltaPage(scope,revision,"101","delta",
                    emptyList(),listOf("1"),"tombstone",false,System.currentTimeMillis())
                assertEquals(999,dao.getAllSubscribersFlow().first().size)
                assertEquals(1,dao.getScopedPendingReadingsWithStatus(scope,"pending").size)
                assertEquals("tombstone",dao.getLatestCompletedSyncState(scope)?.cursor)
            }
    }
}
