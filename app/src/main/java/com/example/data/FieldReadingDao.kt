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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingReading(reading: PendingReadingEntity): Long

    @Query("UPDATE pending_readings SET status = :status WHERE localId = :localId")
    suspend fun updateReadingStatus(localId: Int, status: String)

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
