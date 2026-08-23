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
    version = 4,
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

        @Volatile
        private var INSTANCE: FieldReadingDatabase? = null

        fun getDatabase(context: Context): FieldReadingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FieldReadingDatabase::class.java,
                    "field_reading_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
