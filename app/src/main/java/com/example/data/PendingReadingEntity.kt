package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_readings")
data class PendingReadingEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val subscriberId: String,
    val currentReading: Double,
    val readingDate: String,
    val notes: String,
    val latitude: Double,
    val longitude: Double,
    val photoPath: String?,
    val status: String, // "pending", "syncing", "retryable", "needs_review", "synced"
    val isMeterReplacement: Boolean = false,
    val clientReferenceId: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null
)
