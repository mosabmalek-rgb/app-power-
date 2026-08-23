package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "pending_sync_events")
data class PendingSyncEventEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val eventKey: String = UUID.randomUUID().toString(),
    val branchId: Long,
    val eventType: String, // sync_cycle | sync_error
    val status: String, // success | partial | retryable | needs_review | failed
    val syncedCount: Int = 0,
    val retryableCount: Int = 0,
    val needsReviewCount: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
