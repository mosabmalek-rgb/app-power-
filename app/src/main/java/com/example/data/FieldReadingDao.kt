package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldReadingDao {
    @Query("SELECT * FROM subscribers ORDER BY name ASC")
    fun getAllSubscribersFlow(): Flow<List<SubscriberEntity>>

    @Query("SELECT * FROM subscribers WHERE id = :id LIMIT 1")
    suspend fun getSubscriberById(id: String): SubscriberEntity?

    @Query("SELECT * FROM subscribers WHERE id = :id LIMIT 1")
    fun getSubscriberByIdFlow(id: String): Flow<SubscriberEntity?>

    @Query("SELECT * FROM subscribers WHERE name LIKE :query OR refNumber LIKE :query OR phone LIKE :query")
    fun searchSubscribersFlow(query: String): Flow<List<SubscriberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscribers(subscribers: List<SubscriberEntity>)

    @Query("SELECT * FROM pending_readings ORDER BY createdAt DESC")
    fun getAllPendingReadingsFlow(): Flow<List<PendingReadingEntity>>

    @Query("SELECT * FROM pending_readings WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getPendingReadingsWithStatus(status: String): List<PendingReadingEntity>

    @Query("SELECT * FROM pending_readings WHERE subscriberId = :subscriberId")
    suspend fun getReadingsForSubscriber(subscriberId: String): List<PendingReadingEntity>

    @Query("SELECT COUNT(*) FROM pending_readings WHERE subscriberId = :subscriberId AND readingDate >= :startDateInclusive AND readingDate < :endDateExclusive")
    suspend fun countReadingsForSubscriberInRange(subscriberId: String, startDateInclusive: String, endDateExclusive: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingReading(reading: PendingReadingEntity): Long

    @Transaction
    suspend fun insertPendingReadingIfNoReadingInRange(reading: PendingReadingEntity, startDateInclusive: String, endDateExclusive: String): Boolean {
        if (countReadingsForSubscriberInRange(reading.subscriberId, startDateInclusive, endDateExclusive) > 0) return false
        insertPendingReading(reading)
        return true
    }

    @Query("UPDATE pending_readings SET status = :status WHERE localId = :localId")
    suspend fun updateReadingStatus(localId: Int, status: String)

    @Query("UPDATE pending_readings SET status = :status, retryCount = :retryCount, lastError = :lastError, lastAttemptAt = :lastAttemptAt WHERE localId = :localId")
    suspend fun updateReadingSyncState(localId: Int, status: String, retryCount: Int, lastError: String?, lastAttemptAt: Long)

    @Query("UPDATE pending_readings SET status = 'pending', lastError = NULL WHERE localId = :localId AND status = 'needs_review'")
    suspend fun requeueReadingForReview(localId: Int)

    @Query("SELECT * FROM pending_sync_events ORDER BY createdAt ASC")
    suspend fun getPendingSyncEvents(): List<PendingSyncEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSyncEvent(event: PendingSyncEventEntity): Long

    @Query("DELETE FROM pending_sync_events WHERE localId = :localId")
    suspend fun deletePendingSyncEvent(localId: Int)

    @Query("SELECT * FROM pending_payments ORDER BY createdAt DESC")
    fun getAllPendingPaymentsFlow(): Flow<List<PendingPaymentEntity>>

    @Query("SELECT * FROM pending_payments WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getPendingPaymentsWithStatus(status: String): List<PendingPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingPayment(payment: PendingPaymentEntity): Long

    @Query("UPDATE pending_payments SET status = :status WHERE localId = :localId")
    suspend fun updatePaymentStatus(localId: Int, status: String)

    @Query("DELETE FROM subscribers")
    suspend fun clearAllSubscribers()

    @Query("DELETE FROM pending_readings WHERE status = 'synced'")
    suspend fun clearSyncedReadings()

    @Query("DELETE FROM pending_payments WHERE status = 'synced'")
    suspend fun clearSyncedPayments()
}
