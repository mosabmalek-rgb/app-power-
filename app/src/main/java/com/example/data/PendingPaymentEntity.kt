package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_payments")
data class PendingPaymentEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val subscriberId: String,
    val amount: Double,
    val paymentMethod: String, // "CASH", "BANK" (or "نقد", "تحويل بنكي")
    val notes: String,
    val status: String, // "pending", "synced", "failed"
    val clientReferenceId: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
)
