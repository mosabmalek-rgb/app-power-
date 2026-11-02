package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscribers")
data class SubscriberEntity(
    @PrimaryKey val id: String,
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
    val currentCycleKey: String? = null
)
