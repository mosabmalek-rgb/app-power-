package com.example.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "subscribers", primaryKeys = ["scopeKey", "id"], indices = [Index(value = ["scopeKey", "branchId"])])
data class SubscriberEntity(
    val id: String,
    val refNumber: String,
    val name: String,
    val phone: String,
    val address: String,
    val branchId: String,
    val lastReading: Double,
    val lastReadingDate: String,
    val balance: Double,
    val hasReadingInCurrentCycle: Boolean = false,
    val cycleReadingValue: String? = null,
    val cycleReadingAt: String? = null,
    val cycleReadingStatus: String? = null,
    val currentCycleKey: String? = null,
    val scopeKey: String = "__legacy__",
    val scopeVersion: String = "__legacy__",
    val isVisible: Boolean = true,
    val bootstrapMarker: String? = null
)
