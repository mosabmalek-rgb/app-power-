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

    @Transaction
    suspend fun replaceSubscribersSnapshot(subscribers: List<SubscriberEntity>) {
        clearAllSubscribers()
        insertSubscribers(subscribers)
    }

    @Query("SELECT * FROM subscriber_delta_state WHERE id = 1")
    suspend fun readDeltaState(): SubscriberDeltaState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDeltaState(state: SubscriberDeltaState)

    @Query("DELETE FROM subscriber_delta_state")
    suspend fun clearDeltaState()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageDeltaRows(rows: List<SubscriberDeltaStage>)

    @Query("SELECT * FROM subscriber_delta_stage")
    suspend fun stagedDeltaRows(): List<SubscriberDeltaStage>

    @Query("DELETE FROM subscriber_delta_stage")
    suspend fun clearDeltaStage()

    @Query("DELETE FROM subscriber_delta_stage WHERE id IN (:ids)")
    suspend fun deleteDeltaStageIds(ids: List<String>)

    @Query("DELETE FROM subscribers WHERE id IN (:ids)")
    suspend fun deleteSubscriberIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM pending_readings")
    suspend fun anyReadingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_payments")
    suspend fun anyPaymentCount(): Int

    @Query("SELECT COUNT(*) FROM pending_sync_events")
    suspend fun anyEventCount(): Int

    @Query("SELECT COUNT(*) FROM subscribers")
    suspend fun liveSubscriberCount(): Int

    @Query("SELECT COUNT(*) FROM subscriber_delta_stage")
    suspend fun stagedSubscriberCount(): Int

    @Query("SELECT COUNT(*) FROM pending_readings WHERE status <> 'synced'")
    suspend fun outstandingReadingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_payments WHERE status <> 'synced'")
    suspend fun outstandingPaymentCount(): Int

    @Query("SELECT COUNT(*) FROM pending_sync_events")
    suspend fun outstandingEventCount(): Int

    @Transaction
    suspend fun hasUnsentWork(): Boolean =
        outstandingReadingCount() > 0 || outstandingPaymentCount() > 0 ||
            outstandingEventCount() > 0

    /** Change identity only after a successful login and after proving no unsent local work exists. */
    @Transaction
    suspend fun clearSubscriberCacheForIdentityChange() {
        check(!hasUnsentWork()) { "Unsent offline work prevents account switch" }
        check(anyReadingCount() == 0 && anyPaymentCount() == 0 && anyEventCount() == 0) {
            "Offline work/history must be explicitly reconciled before account switch"
        }
        clearAllSubscribers()
        clearDeltaStage()
        clearDeltaState()
    }

    /** Compatibility with servers that lack Delta: cursor and legacy data must change together. */
    @Transaction
    suspend fun publishLegacySnapshot(rows: List<SubscriberEntity>) {
        clearAllSubscribers()
        insertSubscribers(rows)
        clearDeltaStage()
        clearDeltaState()
    }

    /**
     * Verify the previously committed cursor before touching ANY subscriber rows.
     * Room rolls back stage, live snapshot and cursor as a single transaction on error.
     */
    @Transaction
    suspend fun commitDeltaPage(
        scopeKey: String,
        requestCursor: String?,
        mode: String,
        scopeVersion: String,
        cycleKey: String,
        rows: List<SubscriberEntity>,
        deletes: List<String>,
        nextCursor: String,
        hasMore: Boolean
    ) {
        require(scopeKey.isNotBlank() && scopeVersion.length == 64 && cycleKey.isNotBlank())
        require(nextCursor.isNotBlank() && nextCursor.length <= 2048)
        require(rows.all { it.id.isNotBlank() } && rows.distinctBy { it.id }.size == rows.size)
        require(deletes.distinct().size == deletes.size)
        require(rows.none { row -> row.id in deletes })
        val previous = readDeltaState()
        // Never allow a newly scoped bootstrap to reveal or overwrite a foreign snapshot.
        if (previous != null && previous.scopeKey != scopeKey) {
            error("Cannot apply page across tenant/user/branch scope")
        }
        if (mode == "bootstrap") {
            if (requestCursor == null) {
                // First page of a fresh bootstrap: the live snapshot is NOT cleared.
                clearDeltaStage()
                clearDeltaState()
            } else {
                check(previous?.phase == "bootstrap" &&
                    previous.scopeKey == scopeKey &&
                    previous.cursor == requestCursor &&
                    previous.scopeVersion == scopeVersion &&
                    previous.cycleKey == cycleKey) { "Bootstrap page cursor/scope drift" }
            }
            if (rows.isNotEmpty()) stageDeltaRows(rows.map(SubscriberDeltaStage::from))
            if (deletes.isNotEmpty()) deleteDeltaStageIds(deletes)
            if (!hasMore) {
                val complete = stagedDeltaRows().map(SubscriberDeltaStage::published)
                clearAllSubscribers()
                if (complete.isNotEmpty()) insertSubscribers(complete)
                clearDeltaStage()
            }
            saveDeltaState(SubscriberDeltaState(
                scopeKey = scopeKey, cursor = nextCursor,
                phase = if (hasMore) "bootstrap" else "delta",
                scopeVersion = scopeVersion, cycleKey = cycleKey
            ))
        } else {
            require(mode == "delta") { "Unknown server Delta mode" }
            check(previous != null && previous.phase == "delta" &&
                previous.scopeKey == scopeKey &&
                previous.cursor == requestCursor &&
                previous.scopeVersion == scopeVersion &&
                previous.cycleKey == cycleKey) { "Delta page cursor/scope drift" }
            if (deletes.isNotEmpty()) deleteSubscriberIds(deletes)
            if (rows.isNotEmpty()) insertSubscribers(rows)
            saveDeltaState(previous.copy(cursor = nextCursor))
        }
    }

    @Query("SELECT * FROM pending_readings ORDER BY createdAt DESC")
    fun getAllPendingReadingsFlow(): Flow<List<PendingReadingEntity>>

    @Query("SELECT * FROM pending_readings WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getPendingReadingsWithStatus(status: String): List<PendingReadingEntity>

    @Query("SELECT * FROM pending_readings WHERE subscriberId = :subscriberId")
    suspend fun getReadingsForSubscriber(subscriberId: String): List<PendingReadingEntity>

    @Query("""
        SELECT COUNT(*) FROM pending_readings
        WHERE subscriberId = :subscriberId
          AND (
            readingCycleKey = :cycleKey
            OR (
              readingCycleKey IS NULL
              AND readingDate >= :startDateInclusive
              AND readingDate < :endDateExclusive
            )
          )
    """)
    suspend fun countReadingsForSubscriberInCycle(
        subscriberId: String,
        cycleKey: String,
        startDateInclusive: String,
        endDateExclusive: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingReading(reading: PendingReadingEntity): Long

    @Transaction
    suspend fun insertPendingReadingIfNoReadingInCycle(
        reading: PendingReadingEntity,
        cycleKey: String,
        startDateInclusive: String,
        endDateExclusive: String
    ): Boolean {
        if (countReadingsForSubscriberInCycle(reading.subscriberId, cycleKey, startDateInclusive, endDateExclusive) > 0) {
            return false
        }
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
