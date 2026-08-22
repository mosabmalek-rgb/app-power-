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
        PendingPaymentEntity::class
    ],
    version = 2,
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

        @Volatile
        private var INSTANCE: FieldReadingDatabase? = null

        fun getDatabase(context: Context): FieldReadingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FieldReadingDatabase::class.java,
                    "field_reading_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
