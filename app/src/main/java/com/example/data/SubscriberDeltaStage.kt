package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Incomplete bootstrap pages stay here until the final page is verified and published. */
@Entity(tableName = "subscriber_delta_stage")
data class SubscriberDeltaStage(
    @PrimaryKey val id: String,
    val refNumber: String,
    val name: String,
    val phone: String,
    val address: String,
    val branchId: String,
    val lastReading: Double,
    val lastReadingDate: String,
    val balance: Double,
    val hasReadingInCurrentCycle: Boolean,
    val cycleReadingValue: String?,
    val cycleReadingAt: String?,
    val cycleReadingStatus: String?,
    val currentCycleKey: String?
) {
    fun published() = SubscriberEntity(
        id, refNumber, name, phone, address, branchId, lastReading,
        lastReadingDate, balance, hasReadingInCurrentCycle,
        cycleReadingValue, cycleReadingAt, cycleReadingStatus, currentCycleKey
    )

    companion object {
        fun from(row: SubscriberEntity) = SubscriberDeltaStage(
            row.id, row.refNumber, row.name, row.phone, row.address, row.branchId,
            row.lastReading, row.lastReadingDate, row.balance,
            row.hasReadingInCurrentCycle, row.cycleReadingValue, row.cycleReadingAt,
            row.cycleReadingStatus, row.currentCycleKey
        )
    }
}
