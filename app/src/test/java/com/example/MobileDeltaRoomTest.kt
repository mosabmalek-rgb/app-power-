package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MobileDeltaRoomTest {
    private fun subscriber(id: String, scope: String, version: String) =
        SubscriberEntity(
            id=id,refNumber=id,name="Subscriber $id",phone="",address="",
            branchId="101",lastReading=0.0,lastReadingDate="",balance=0.0,
            scopeKey=scope,scopeVersion=version
        )
    private fun unsent(id: String, scope: String, status: String = "pending") =
        PendingReadingEntity(
            subscriberId=id,currentReading=11.0,readingDate="2026-11-16 10:00",
            notes="",latitude=0.0,longitude=0.0,photoPath=null,status=status,scopeKey=scope,
            clientReferenceId="550e8400-e29b-41d4-a716-446655440000"
        )

    @Test fun `bootstrap stays hidden until all pages are committed and delta tombstones preserve pending work`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,FieldReadingDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao=db.dao()
        val scope="10:7:101"
        val version="a".repeat(64)
        try {
            dao.setActiveScope(MobileActiveScopeEntity(scopeKey=scope,scopeVersion="not-bootstrapped",status="bootstrapping"))
            dao.applyMobileDeltaPage(scope,version,"101","bootstrap",
                listOf(subscriber("1001",scope,version)),emptyList(),"signed-p1",true,1234L)
            assertTrue(dao.getAllSubscribersFlow().first().isEmpty())
            assertEquals("signed-p1",dao.getIncompleteBootstrapState(scope)?.cursor)
            dao.applyMobileDeltaPage(scope,version,"101","bootstrap",
                listOf(subscriber("1002",scope,version)),emptyList(),"signed-p2",false,1235L)
            assertEquals(setOf("1001","1002"),dao.getAllSubscribersFlow().first().map { it.id }.toSet())
            dao.insertPendingReading(unsent("1001",scope))
            dao.applyMobileDeltaPage(scope,version,"101","delta",
                emptyList(),listOf("1001"),"signed-p3",false,1236L)
            assertEquals(listOf("1002"),dao.getAllSubscribersFlow().first().map { it.id })
            assertEquals(1,dao.getScopedPendingReadingsWithStatus(scope,"pending").size)
            assertEquals("signed-p3",dao.getSyncState(scope,version)?.cursor)
            assertEquals(true,dao.getSyncState(scope,version)?.completed)
        } finally { db.close() }
    }

    @Test fun `failed atomic page does not advance cursor or corrupt outbox and account switch hides old rows`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,FieldReadingDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao=db.dao()
        val scope="10:7:101"
        val other="20:8:101"
        val version="b".repeat(64)
        try {
            dao.setActiveScope(MobileActiveScopeEntity(scopeKey=scope,scopeVersion="not-bootstrapped",status="bootstrapping"))
            dao.applyMobileDeltaPage(scope,version,"101","bootstrap",
                listOf(subscriber("1001",scope,version)),emptyList(),"signed-1",false,100L)
            dao.insertPendingReading(unsent("1001",scope,"syncing"))
            assertTrue(runCatching {
                dao.applyMobileDeltaPage(scope,version,"101","delta",
                    listOf(subscriber("1002",scope,version),subscriber("1002",scope,version)),
                    listOf("1001"),"signed-corrupt",false,200L)
            }.isFailure)
            assertEquals("signed-1",dao.getSyncState(scope,version)?.cursor)
            assertEquals(listOf("1001"),dao.getAllSubscribersFlow().first().map { it.id })
            dao.resetInterruptedReadings(scope)
            assertEquals(1,dao.getScopedPendingReadingsWithStatus(scope,"retryable").size)
            dao.setActiveScope(MobileActiveScopeEntity(scopeKey=other,scopeVersion=version,status="ready"))
            assertTrue(dao.getAllSubscribersFlow().first().isEmpty())
            assertTrue(dao.getScopedPendingReadingsWithStatus(other,"retryable").isEmpty())
            assertEquals(1,dao.getScopedPendingReadingsWithStatus(scope,"retryable").size)
        } finally { db.close() }
    }

    @Test fun `authorization scope renewal cannot replay an abandoned Delta checkpoint`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,FieldReadingDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao=db.dao()
        val scope="10:7:101"
        val oldVersion="c".repeat(64)
        val newVersion="d".repeat(64)
        try {
            dao.setActiveScope(MobileActiveScopeEntity(scopeKey=scope,
                scopeVersion="not-bootstrapped",status="bootstrapping"))
            dao.applyMobileDeltaPage(scope,oldVersion,"101","bootstrap",
                listOf(subscriber("1001",scope,oldVersion)),emptyList(),"initial",false,100L)
            dao.applyMobileDeltaPage(scope,oldVersion,"101","delta",
                listOf(subscriber("1002",scope,oldVersion)),emptyList(),"delta-in-progress",true,101L)
            assertEquals("delta-in-progress",
                dao.getIncompleteDeltaState(scope,oldVersion)?.cursor)
            dao.quarantineActiveScope(scope)
            dao.forgetIncompleteDelta(scope)
            dao.applyMobileDeltaPage(scope,newVersion,"101","bootstrap",
                listOf(subscriber("2001",scope,newVersion)),emptyList(),"new-cursor",false,200L)
            assertNull(dao.getIncompleteDeltaState(scope,newVersion))
            assertEquals(listOf("2001"),dao.getAllSubscribersFlow().first().map { it.id })
            assertEquals("new-cursor",dao.getLatestCompletedSyncState(scope)?.cursor)
        } finally { db.close() }
    }

    @Test fun `Room 8 to 9 upgrade preserves legacy subscribers readings payments and events without showing unknown owner`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="mobile-delta-v8-test.db"
        context.deleteDatabase(name)
        val path=context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val raw=SQLiteDatabase.openOrCreateDatabase(path,null)
        try {
            raw.execSQL("""CREATE TABLE subscribers(
               id TEXT NOT NULL PRIMARY KEY,refNumber TEXT NOT NULL,name TEXT NOT NULL,
               phone TEXT NOT NULL,address TEXT NOT NULL,branchId TEXT NOT NULL,
               lastReading REAL NOT NULL,lastReadingDate TEXT NOT NULL,balance REAL NOT NULL,
               hasReadingInCurrentCycle INTEGER NOT NULL DEFAULT 0,cycleReadingValue TEXT,
               cycleReadingAt TEXT,cycleReadingStatus TEXT,currentCycleKey TEXT)""")
            raw.execSQL("""CREATE TABLE pending_readings(
               localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
               subscriberId TEXT NOT NULL,currentReading REAL NOT NULL,readingDate TEXT NOT NULL,
               notes TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,
               photoPath TEXT,status TEXT NOT NULL,isReadingReset INTEGER NOT NULL,
               fieldVerified INTEGER NOT NULL,clientReferenceId TEXT NOT NULL,createdAt INTEGER NOT NULL,
               retryCount INTEGER NOT NULL,lastError TEXT,lastAttemptAt INTEGER,readingValueText TEXT,
               readingCycleKey TEXT)""")
            raw.execSQL("""CREATE TABLE pending_payments(
               localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
               subscriberId TEXT NOT NULL,amount REAL NOT NULL,paymentMethod TEXT NOT NULL,
               notes TEXT NOT NULL,status TEXT NOT NULL,clientReferenceId TEXT NOT NULL,
               createdAt INTEGER NOT NULL)""")
            raw.execSQL("""CREATE TABLE pending_sync_events(
               localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,eventKey TEXT NOT NULL,
               branchId INTEGER NOT NULL,eventType TEXT NOT NULL,status TEXT NOT NULL,
               syncedCount INTEGER NOT NULL,retryableCount INTEGER NOT NULL,needsReviewCount INTEGER NOT NULL,
               errorCode TEXT,errorMessage TEXT,createdAt INTEGER NOT NULL)""")
            raw.execSQL("""INSERT INTO subscribers(id,refNumber,name,phone,address,branchId,lastReading,lastReadingDate,balance,hasReadingInCurrentCycle)
               VALUES('1001','S-1','Old subscriber','','','101',10,'',0,0)""")
            raw.execSQL("""INSERT INTO pending_readings(subscriberId,currentReading,readingDate,notes,latitude,longitude,
               status,isReadingReset,fieldVerified,clientReferenceId,createdAt,retryCount)
               VALUES('1001',11,'2026-11-16 10:00','',0,0,'pending',0,1,'old-client-op',1,0)""")
            raw.execSQL("""INSERT INTO pending_payments(subscriberId,amount,paymentMethod,notes,status,clientReferenceId,createdAt)
               VALUES('1001',50,'CASH','','pending','payment-op',1)""")
            raw.execSQL("""INSERT INTO pending_sync_events(eventKey,branchId,eventType,status,syncedCount,retryableCount,needsReviewCount,createdAt)
               VALUES('event-1',101,'sync_error','retryable',0,1,0,1)""")
            raw.version=8
        } finally { raw.close() }
        val db=Room.databaseBuilder(context,FieldReadingDatabase::class.java,name)
            .addMigrations(FieldReadingDatabase.MIGRATION_8_9)
            .allowMainThreadQueries().build()
        try {
            val dao=db.dao()
            assertEquals(listOf("1001"),dao.getAllSubscriberIds())
            assertTrue(dao.getAllSubscribersFlow().first().isEmpty())
            assertEquals(1,dao.legacyUnsentReadingCount())
            assertEquals(1,dao.legacyUnsentPaymentCount())
            assertEquals(1,dao.legacySyncEventCount())
            dao.claimVerifiedLegacyScope("10:7:101")
            dao.setActiveScope(MobileActiveScopeEntity(
                scopeKey="10:7:101",scopeVersion="legacy-seeded",status="ready"
            ))
            assertEquals(listOf("1001"),dao.getAllSubscribersFlow().first().map { it.id })
            assertEquals(1,dao.getScopedPendingReadingsWithStatus("10:7:101","pending").size)
            assertEquals(1,dao.getScopedPendingPaymentsWithStatus("10:7:101","pending").size)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
