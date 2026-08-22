package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriberEntity::class,
        PendingReadingEntity::class,
        PendingPaymentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FieldReadingDatabase : RoomDatabase() {
    abstract fun dao(): FieldReadingDao

    companion object {
        @Volatile
        private var INSTANCE: FieldReadingDatabase? = null

        fun getDatabase(context: Context): FieldReadingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FieldReadingDatabase::class.java,
                    "field_reading_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
