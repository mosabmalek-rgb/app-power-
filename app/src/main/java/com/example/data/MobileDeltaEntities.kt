package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Only the current authenticated tenant/user/branch may be shown in the UI. */
@Entity(tableName = "mobile_active_scope")
data class MobileActiveScopeEntity(
    @PrimaryKey val id: Int = 1,
    val scopeKey: String,
    val scopeVersion: String,
    val status: String = "ready",
)

/** Cursor and received pages are committed in the same Room transaction. */
@Entity(tableName = "mobile_sync_states", primaryKeys = ["scopeKey", "scopeVersion"])
data class MobileSyncStateEntity(
    val scopeKey: String,
    val scopeVersion: String,
    val cursor: String,
    val mode: String,
    val bootstrapMarker: String? = null,
    val pageCount: Int = 0,
    val lastAppliedAt: Long = 0L,
    val completed: Boolean = false,
)
