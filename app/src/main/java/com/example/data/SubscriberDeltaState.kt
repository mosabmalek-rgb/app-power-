package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One active authenticated scope; the opaque cursor is committed with each Room page. */
@Entity(tableName = "subscriber_delta_state")
data class SubscriberDeltaState(
    @PrimaryKey val id: Int = 1,
    val scopeKey: String,
    val cursor: String,
    val phase: String, // bootstrap | delta
    val scopeVersion: String,
    val cycleKey: String
)
