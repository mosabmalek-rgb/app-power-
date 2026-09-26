package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubscriberEntity::class,
        PendingReadingEntity::class,
        PendingPaymentEntity::class,
        PendingSyncEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class FieldReadingDatabase : RoomDatabase() {
    abstract fun dao(): FieldReadingDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscribers ADD COLUMN meterId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_readings ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pending_readings ADD COLUMN lastError TEXT")
                db.execSQL("ALTER TABLE pending_readings ADD COLUMN lastAttemptAt INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_sync_events (localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, eventKey TEXT NOT NULL, branchId INTEGER NOT NULL, eventType TEXT NOT NULL, status TEXT NOT NULL, syncedCount INTEGER NOT NULL DEFAULT 0, retryableCount INTEGER NOT NULL DEFAULT 0, needsReviewCount INTEGER NOT NULL DEFAULT 0, errorCode TEXT, errorMessage TEXT, createdAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_readings ADD COLUMN fieldVerified INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE subscribers_new (id TEXT NOT NULL PRIMARY KEY, refNumber TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, branchId TEXT NOT NULL, lastReading REAL NOT NULL, lastReadingDate TEXT NOT NULL, balance REAL NOT NULL)")
                db.execSQL("INSERT INTO subscribers_new (id, refNumber, name, phone, address, branchId, lastReading, lastReadingDate, balance) SELECT id, refNumber, name, phone, address, branchId, lastReading, lastReadingDate, balance FROM subscribers")
                db.execSQL("DROP TABLE subscribers")
                db.execSQL("ALTER TABLE subscribers_new RENAME TO subscribers")
                db.execSQL("CREATE TABLE pending_readings_new (localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriberId TEXT NOT NULL, currentReading REAL NOT NULL, readingDate TEXT NOT NULL, notes TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, photoPath TEXT, status TEXT NOT NULL, isReadingReset INTEGER NOT NULL DEFAULT 0, clientReferenceId TEXT NOT NULL, createdAt INTEGER NOT NULL, retryCount INTEGER NOT NULL DEFAULT 0, lastError TEXT, lastAttemptAt INTEGER)")
                db.execSQL("INSERT INTO pending_readings_new (localId, subscriberId, currentReading, readingDate, notes, latitude, longitude, photoPath, status, isReadingReset, clientReferenceId, createdAt, retryCount, lastError, lastAttemptAt) SELECT localId, subscriberId, currentReading, readingDate, notes, latitude, longitude, photoPath, status, isMeterReplacement, clientReferenceId, createdAt, retryCount, lastError, lastAttemptAt FROM pending_readings")
                db.execSQL("DROP TABLE pending_readings")
                db.execSQL("ALTER TABLE pending_readings_new RENAME TO pending_readings")
            }
        }

        @Volatile
        private var INSTANCE: FieldReadingDatabase? = null

        fun getDatabase(context: Context): FieldReadingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FieldReadingDatabase::class.java,
                    "field_reading_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
