package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.FieldReadingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * File-backed v8 fixture reconstructed from the v8 entities on main at
 * 9ab3ad5c256920338f8f277c51036e7df8a347bb. No hosted environment.
 *
 * Unlike Room.inMemoryDatabaseBuilder, this tests the *actual* Room upgrade
 * path, v8 on-disk bytes, schema validation and a second open at v9.
 */
@RunWith(RobolectricTestRunner::class)
class FieldReadingRoomV8ToV9MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "field-reading-v8-to-v9-test.db"
    private var opened: FieldReadingDatabase? = null

    @After fun cleanUp() {
        opened?.close()
        opened = null
        context.deleteDatabase(databaseName)
    }

    private fun createV8File() {
        context.deleteDatabase(databaseName)
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            // Exactly four v8 entity tables. No Delta tables or cursor metadata.
            legacy.execSQL("""CREATE TABLE IF NOT EXISTS subscribers (
                id TEXT NOT NULL PRIMARY KEY, refNumber TEXT NOT NULL,
                name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL,
                branchId TEXT NOT NULL, lastReading REAL NOT NULL,
                lastReadingDate TEXT NOT NULL, balance REAL NOT NULL,
                hasReadingInCurrentCycle INTEGER NOT NULL, cycleReadingValue TEXT,
                cycleReadingAt TEXT, cycleReadingStatus TEXT, currentCycleKey TEXT)""")
            legacy.execSQL("""CREATE TABLE IF NOT EXISTS pending_readings (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                subscriberId TEXT NOT NULL, currentReading REAL NOT NULL,
                readingDate TEXT NOT NULL, notes TEXT NOT NULL, latitude REAL NOT NULL,
                longitude REAL NOT NULL, photoPath TEXT, status TEXT NOT NULL,
                isReadingReset INTEGER NOT NULL, fieldVerified INTEGER NOT NULL,
                clientReferenceId TEXT NOT NULL, createdAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL, lastError TEXT, lastAttemptAt INTEGER,
                readingValueText TEXT, readingCycleKey TEXT)""")
            legacy.execSQL("""CREATE TABLE IF NOT EXISTS pending_payments (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                subscriberId TEXT NOT NULL, amount REAL NOT NULL,
                paymentMethod TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL,
                clientReferenceId TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
            legacy.execSQL("""CREATE TABLE IF NOT EXISTS pending_sync_events (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventKey TEXT NOT NULL, branchId INTEGER NOT NULL,
                eventType TEXT NOT NULL, status TEXT NOT NULL,
                syncedCount INTEGER NOT NULL, retryableCount INTEGER NOT NULL,
                needsReviewCount INTEGER NOT NULL, errorCode TEXT, errorMessage TEXT,
                createdAt INTEGER NOT NULL)""")
            legacy.execSQL("""INSERT INTO subscribers VALUES (
                '15', 'S15', 'Legacy subscriber', '059', '', '7', 100.5,
                '2026-11-01', 12.0, 1, '100.50', '2026-11-01', 'posted', 'cycle-legacy')""")
            for (status in listOf("pending", "syncing", "retryable", "needs_review", "synced")) {
                val key = "original-key-$status"
                legacy.execSQL(
                    """INSERT INTO pending_readings
                    (subscriberId,currentReading,readingDate,notes,latitude,longitude,
                    status,isReadingReset,fieldVerified,clientReferenceId,createdAt,
                    retryCount,readingValueText,readingCycleKey)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    arrayOf("15", 101.25, "2026-11-02 12:00", status, 0.0, 0.0,
                        status, 0, 1, key, 1720000000000L, 2, "101.25", "cycle-legacy")
                )
            }
            legacy.execSQL("""INSERT INTO pending_payments
                (subscriberId,amount,paymentMethod,notes,status,clientReferenceId,createdAt)
                VALUES ('15',21.5,'CASH','unsettled','pending','payment-original-key',1720000000000)""")
            legacy.execSQL("""INSERT INTO pending_sync_events
                (eventKey,branchId,eventType,status,syncedCount,retryableCount,
                needsReviewCount,createdAt)
                VALUES ('event-original-key',7,'sync_cycle','retryable',0,1,0,1720000000000)""")
            legacy.version = 8
        } finally {
            legacy.close()
        }
    }

    private fun openV9() = Room.databaseBuilder(
        context, FieldReadingDatabase::class.java, databaseName
    ).addMigrations(FieldReadingDatabase.MIGRATION_8_9)
        .allowMainThreadQueries().build().also { opened = it }

    @Test fun v8OfflineRowsKeysAndCycleFieldsSurviveMigrationAndReopen() = runBlocking {
        createV8File()
        val db = openV9()
        val dao = db.dao() // Opens and validates the upgraded Room database.
        val subscriber = requireNotNull(dao.getSubscriberById("15"))
        assertEquals("Legacy subscriber", subscriber.name)
        assertEquals(100.5, subscriber.lastReading, 0.0)
        assertEquals("cycle-legacy", subscriber.currentCycleKey)
        assertEquals("100.50", subscriber.cycleReadingValue)
        assertTrue(subscriber.hasReadingInCurrentCycle)
        for (status in listOf("pending", "syncing", "retryable", "needs_review", "synced")) {
            val reading = dao.getPendingReadingsWithStatus(status).single()
            assertEquals("original-key-$status", reading.clientReferenceId)
            assertEquals("101.25", reading.readingValueText)
            assertEquals("cycle-legacy", reading.readingCycleKey)
            assertEquals(2, reading.retryCount)
        }
        assertEquals("payment-original-key",
            dao.getPendingPaymentsWithStatus("pending").single().clientReferenceId)
        assertEquals("event-original-key", dao.getPendingSyncEvents().single().eventKey)
        assertNull(dao.readDeltaState())
        assertEquals(0, dao.stagedSubscriberCount())

        db.close()
        opened = null
        val reopened = openV9()
        val roundTrip = reopened.dao()
        assertEquals(5, listOf("pending", "syncing", "retryable", "needs_review", "synced")
            .sumOf { roundTrip.getPendingReadingsWithStatus(it).size })
        assertEquals("payment-original-key",
            roundTrip.getPendingPaymentsWithStatus("pending").single().clientReferenceId)
        assertEquals("event-original-key", roundTrip.getPendingSyncEvents().single().eventKey)

        // A new subscriber Bootstrap page must not delete the inherited outbox.
        roundTrip.commitDeltaPage("1:2:7", null, "bootstrap", "a".repeat(64),
            "cycle-new", emptyList(), emptyList(), "cursor-1", true)
        assertEquals("bootstrap", roundTrip.readDeltaState()?.phase)
        assertEquals(5, listOf("pending", "syncing", "retryable", "needs_review", "synced")
            .sumOf { roundTrip.getPendingReadingsWithStatus(it).size })
        assertEquals(1, roundTrip.outstandingPaymentCount())
        assertEquals(1, roundTrip.outstandingEventCount())
    }

    @Test fun rejectedMigrationRollsBackAndLeavesOriginalV8OutboxForRetry() = runBlocking {
        createV8File()
        val failing = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE deliberately_partial (id INTEGER)")
                error("Synthetic migration failure; SQLite must roll back")
            }
        }
        val invalidDb = Room.databaseBuilder(
            context, FieldReadingDatabase::class.java, databaseName
        ).addMigrations(failing).allowMainThreadQueries().build()
        try {
            assertThrows(Exception::class.java) { invalidDb.openHelper.writableDatabase }
        } finally {
            invalidDb.close()
        }
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            assertEquals(8, legacy.version)
            val cursor = legacy.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE name='deliberately_partial'", null)
            cursor.use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            val readings = legacy.rawQuery(
                "SELECT COUNT(*) FROM pending_readings WHERE clientReferenceId LIKE 'original-key-%'", null)
            readings.use { assertTrue(it.moveToFirst()); assertEquals(5, it.getInt(0)) }
        } finally {
            legacy.close()
        }

        val recovered = openV9()
        assertEquals(5, listOf("pending", "syncing", "retryable", "needs_review", "synced")
            .sumOf { recovered.dao().getPendingReadingsWithStatus(it).size })
        assertEquals("payment-original-key",
            recovered.dao().getPendingPaymentsWithStatus("pending").single().clientReferenceId)
    }
}
