package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldReadingDao {
    @Query("""SELECT s.* FROM subscribers s JOIN mobile_active_scope a ON a.id=1
        AND a.scopeKey=s.scopeKey AND a.scopeVersion=s.scopeVersion
        WHERE a.status='ready' AND s.isVisible=1 ORDER BY s.name ASC""")
    fun getAllSubscribersFlow(): Flow<List<SubscriberEntity>>

    @Query("""SELECT s.* FROM subscribers s JOIN mobile_active_scope a ON a.id=1
        AND a.scopeKey=s.scopeKey AND a.scopeVersion=s.scopeVersion
        WHERE a.status='ready' AND s.isVisible=1 AND s.id=:id LIMIT 1""")
    suspend fun getSubscriberById(id: String): SubscriberEntity?

    @Query("""SELECT s.* FROM subscribers s JOIN mobile_active_scope a
        ON a.id=1 AND a.scopeKey=s.scopeKey AND a.scopeVersion=s.scopeVersion
        WHERE a.status='ready' AND s.isVisible=1 AND s.id=:id LIMIT 1""")
    fun getSubscriberByIdFlow(id: String): Flow<SubscriberEntity?>

    @Query("""SELECT s.* FROM subscribers s JOIN mobile_active_scope a ON a.id=1
        AND a.scopeKey=s.scopeKey AND a.scopeVersion=s.scopeVersion
        WHERE a.status='ready' AND s.isVisible=1
          AND (s.name LIKE :query OR s.refNumber LIKE :query OR s.phone LIKE :query)""")
    fun searchSubscribersFlow(query: String): Flow<List<SubscriberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscribers(subscribers: List<SubscriberEntity>)

    // Apply a fully downloaded snapshot atomically; keep records referenced by local work.
    @Transaction
    suspend fun replaceSubscribersSnapshot(subscribers: List<SubscriberEntity>) {
        if (subscribers.map { it.id }.toSet().size != subscribers.size) {
            throw IllegalArgumentException("Duplicate subscriber IDs in server snapshot")
        }
        if (subscribers.isEmpty()) {
            throw IllegalArgumentException("Refusing to replace subscriber cache with an empty snapshot")
        }
        insertSubscribers(subscribers)
        val receivedIds = subscribers.map { it.id }.toSet()
        getLegacySubscriberIds().filterNot { it in receivedIds }.forEach { id ->
            deleteSubscriberWithoutLocalWork(id)
        }
    }

    @Query("SELECT id FROM subscribers WHERE scopeKey='__legacy__'")
    suspend fun getLegacySubscriberIds(): List<String>

    @Query("SELECT id FROM subscribers")
    suspend fun getAllSubscriberIds(): List<String>

    @Query("""
        DELETE FROM subscribers WHERE id = :id AND scopeKey='__legacy__'
        AND NOT EXISTS (SELECT 1 FROM pending_readings WHERE subscriberId = :id AND scopeKey='__legacy__')
        AND NOT EXISTS (SELECT 1 FROM pending_payments WHERE subscriberId = :id AND scopeKey='__legacy__')
    """)
    suspend fun deleteSubscriberWithoutLocalWork(id: String)

    @Query("""SELECT r.* FROM pending_readings r JOIN mobile_active_scope a
        ON a.id=1 AND r.scopeKey=a.scopeKey ORDER BY r.createdAt DESC""")
    fun getAllPendingReadingsFlow(): Flow<List<PendingReadingEntity>>

    @Query("SELECT * FROM pending_readings WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getPendingReadingsWithStatus(status: String): List<PendingReadingEntity>

    @Query("SELECT * FROM pending_readings WHERE subscriberId = :subscriberId")
    suspend fun getReadingsForSubscriber(subscriberId: String): List<PendingReadingEntity>

    @Query("""
        SELECT COUNT(*) FROM pending_readings
        WHERE subscriberId = :subscriberId AND scopeKey = :scopeKey
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
        endDateExclusive: String,
        scopeKey: String = "__legacy__"
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
        if (countReadingsForSubscriberInCycle(reading.subscriberId, cycleKey, startDateInclusive, endDateExclusive, reading.scopeKey) > 0) {
            return false
        }
        insertPendingReading(reading)
        return true
    }

    @Query("UPDATE pending_readings SET status = :status WHERE localId = :localId")
    suspend fun updateReadingStatus(localId: Int, status: String)

    @Query("UPDATE pending_readings SET status = :status, retryCount = :retryCount, lastError = :lastError, lastAttemptAt = :lastAttemptAt WHERE localId = :localId")
    suspend fun updateReadingSyncState(localId: Int, status: String, retryCount: Int, lastError: String?, lastAttemptAt: Long)

    @Query("""UPDATE pending_readings SET status=:status, retryCount=:retryCount,
        lastError=:lastError, lastAttemptAt=:lastAttemptAt
        WHERE localId=:localId AND scopeKey=:scopeKey""")
    suspend fun updateScopedReadingSyncState(
        localId: Int, scopeKey: String, status: String,
        retryCount: Int, lastError: String?, lastAttemptAt: Long
    ): Int

    @Query("""UPDATE pending_readings SET status='pending', lastError=NULL
        WHERE localId=:localId AND scopeKey=:scopeKey AND status='needs_review'""")
    suspend fun requeueScopedReadingForReview(localId: Int,scopeKey: String): Int

    @Query("UPDATE pending_readings SET status = 'pending', lastError = NULL WHERE localId = :localId AND status = 'needs_review'")
    suspend fun requeueReadingForReview(localId: Int)

    @Query("SELECT * FROM pending_sync_events ORDER BY createdAt ASC")
    suspend fun getPendingSyncEvents(): List<PendingSyncEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSyncEvent(event: PendingSyncEventEntity): Long

    @Query("DELETE FROM pending_sync_events WHERE localId = :localId")
    suspend fun deletePendingSyncEvent(localId: Int)
    @Query("DELETE FROM pending_sync_events WHERE localId=:localId AND scopeKey=:scopeKey")
    suspend fun deleteScopedPendingSyncEvent(localId: Int,scopeKey: String): Int


    @Query("""SELECT p.* FROM pending_payments p JOIN mobile_active_scope a
        ON a.id=1 AND p.scopeKey=a.scopeKey ORDER BY p.createdAt DESC""")
    fun getAllPendingPaymentsFlow(): Flow<List<PendingPaymentEntity>>

    @Query("SELECT * FROM pending_payments WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getPendingPaymentsWithStatus(status: String): List<PendingPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingPayment(payment: PendingPaymentEntity): Long

    @Query("UPDATE pending_payments SET status = :status WHERE localId = :localId")
    suspend fun updatePaymentStatus(localId: Int, status: String)

    // All user-facing queries and sync operations bind to the active Room scope.
    @Query("SELECT * FROM mobile_active_scope WHERE id=1")
    suspend fun getActiveScope(): MobileActiveScopeEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun setActiveScope(value: MobileActiveScopeEntity)

    @Query("UPDATE mobile_active_scope SET status='bootstrapping' WHERE scopeKey=:scopeKey")
    suspend fun quarantineActiveScope(scopeKey: String)

    @Query("DELETE FROM mobile_sync_states WHERE scopeKey=:scopeKey AND mode='bootstrap' AND completed=0")
    suspend fun forgetIncompleteBootstrap(scopeKey: String)

    @Query("DELETE FROM mobile_sync_states WHERE scopeKey=:scopeKey AND completed=0 AND mode='delta'")
    suspend fun forgetIncompleteDelta(scopeKey: String)

    @Query("DELETE FROM mobile_sync_states WHERE scopeKey=:scopeKey AND completed=0 AND scopeVersion<>:scopeVersion")
    suspend fun forgetOtherIncompleteScopes(scopeKey: String,scopeVersion: String)

    @Query("DELETE FROM mobile_active_scope")
    suspend fun clearActiveScope()

    @Query("""SELECT * FROM mobile_sync_states WHERE scopeKey=:scopeKey
        AND scopeVersion=:scopeVersion LIMIT 1""")
    suspend fun getSyncState(scopeKey: String, scopeVersion: String): MobileSyncStateEntity?

    @Query("""SELECT * FROM mobile_sync_states WHERE scopeKey=:scopeKey AND completed=1
        ORDER BY lastAppliedAt DESC LIMIT 1""")
    suspend fun getLatestCompletedSyncState(scopeKey: String): MobileSyncStateEntity?

    @Query("""SELECT * FROM mobile_sync_states WHERE scopeKey=:scopeKey
        AND scopeVersion=:scopeVersion AND mode='delta'
        AND completed=0 ORDER BY lastAppliedAt DESC LIMIT 1""")
    suspend fun getIncompleteDeltaState(scopeKey: String,scopeVersion: String): MobileSyncStateEntity?

    @Query("""SELECT * FROM mobile_sync_states WHERE scopeKey=:scopeKey AND mode='bootstrap'
        AND completed=0 ORDER BY lastAppliedAt DESC LIMIT 1""")
    suspend fun getIncompleteBootstrapState(scopeKey: String): MobileSyncStateEntity?

    @Query("SELECT COUNT(*) FROM subscribers WHERE scopeKey=:scopeKey AND scopeVersion=:scopeVersion AND isVisible=1")
    suspend fun visibleSubscriberCount(scopeKey: String, scopeVersion: String): Int

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: MobileSyncStateEntity)

    @Query("""UPDATE pending_readings SET status='retryable',
        lastError='استرداد قراءة بعد انقطاع أثناء الإرسال'
        WHERE scopeKey=:scopeKey AND status='syncing'""")
    suspend fun resetInterruptedReadings(scopeKey: String)

    @Query("SELECT * FROM pending_readings WHERE scopeKey=:scopeKey AND status=:status ORDER BY createdAt")
    suspend fun getScopedPendingReadingsWithStatus(scopeKey: String,status: String): List<PendingReadingEntity>

    @Query("SELECT * FROM pending_payments WHERE scopeKey=:scopeKey AND status=:status ORDER BY createdAt")
    suspend fun getScopedPendingPaymentsWithStatus(scopeKey: String,status: String): List<PendingPaymentEntity>

    @Query("SELECT * FROM pending_sync_events WHERE scopeKey=:scopeKey ORDER BY createdAt")
    suspend fun getScopedPendingSyncEvents(scopeKey: String): List<PendingSyncEventEntity>

    @Query("SELECT COUNT(*) FROM pending_readings WHERE scopeKey='__legacy__' AND status IN ('pending','syncing','retryable','needs_review')")
    suspend fun legacyUnsentReadingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_payments WHERE scopeKey='__legacy__' AND status IN ('pending','syncing','retryable','needs_review')")
    suspend fun legacyUnsentPaymentCount(): Int

    @Query("SELECT COUNT(*) FROM pending_sync_events WHERE scopeKey='__legacy__'")
    suspend fun legacySyncEventCount(): Int

    @Query("UPDATE OR IGNORE subscribers SET scopeKey=:scopeKey,scopeVersion='legacy-seeded',isVisible=1 WHERE scopeKey='__legacy__'")
    suspend fun claimLegacySubscribers(scopeKey: String)

    @Query("UPDATE pending_readings SET scopeKey=:scopeKey WHERE scopeKey='__legacy__'")
    suspend fun claimLegacyReadings(scopeKey: String)

    @Query("UPDATE pending_payments SET scopeKey=:scopeKey WHERE scopeKey='__legacy__'")
    suspend fun claimLegacyPayments(scopeKey: String)

    @Query("UPDATE pending_sync_events SET scopeKey=:scopeKey WHERE scopeKey='__legacy__'")
    suspend fun claimLegacySyncEvents(scopeKey: String)

    @Transaction
    suspend fun claimVerifiedLegacyScope(scopeKey: String) {
        require(scopeKey != "__legacy__")
        claimLegacySubscribers(scopeKey)
        claimLegacyReadings(scopeKey)
        claimLegacyPayments(scopeKey)
        claimLegacySyncEvents(scopeKey)
    }

    @Query("UPDATE subscribers SET isVisible=0 WHERE scopeKey=:scopeKey AND scopeVersion<>:scopeVersion")
    suspend fun hideOldScopeVersion(scopeKey: String,scopeVersion: String)

    @Query("""UPDATE subscribers SET isVisible=0 WHERE scopeKey=:scopeKey
        AND scopeVersion=:scopeVersion AND (bootstrapMarker IS NULL OR bootstrapMarker<>:marker)""")
    suspend fun hideMissingBootstrapRows(scopeKey: String,scopeVersion: String,marker: String)

    @Query("UPDATE subscribers SET isVisible=0 WHERE scopeKey=:scopeKey AND id IN (:ids)")
    suspend fun hideDeletedSubscribers(scopeKey: String,ids: List<String>)

    @Query("DELETE FROM pending_readings WHERE status='synced' AND scopeKey=:scopeKey")
    suspend fun clearSyncedReadingsForScope(scopeKey: String)

    /** Atomic page + cursor checkpoint. Never physically delete outbox data. */
    @Transaction
    suspend fun applyMobileDeltaPage(
        scopeKey: String, scopeVersion: String, branchId: String, mode: String,
        upserts: List<SubscriberEntity>, deletes: List<String>, nextCursor: String,
        hasMore: Boolean, serverTimeMillis: Long,
    ) {
        val active=getActiveScope() ?: error("No active mobile identity")
        require(active.scopeKey==scopeKey) { "Account or branch changed during sync" }
        require(scopeVersion.length==64 && scopeVersion.all { it in '0'..'9' || it in 'a'..'f' })
        require(mode=="bootstrap" || mode=="delta")
        require(nextCursor.isNotBlank() && nextCursor.length<=2048)
        require(upserts.map { it.id }.toSet().size==upserts.size)
        require(upserts.none { it.id in deletes } && deletes.toSet().size==deletes.size)
        require(upserts.all { it.scopeKey==scopeKey && it.scopeVersion==scopeVersion && it.branchId==branchId })
        val old=getSyncState(scopeKey,scopeVersion)
        if(mode=="delta") require(old?.completed==true || old?.mode=="delta") {
            "Delta cannot advance before a complete bootstrap"
        }
        if (hasMore) require(old?.cursor!=nextCursor) { "Delta cursor made no progress" }
        require((old?.pageCount?:0)<500) { "Maximum Delta page count exceeded" }
        val marker=if(mode=="bootstrap") {
            if(old?.mode=="bootstrap" && !old.completed) old.bootstrapMarker
            else java.util.UUID.randomUUID().toString()
        } else null
        if(mode=="bootstrap")require(!marker.isNullOrBlank())
        insertSubscribers(upserts.map { if(mode=="bootstrap") it.copy(
            bootstrapMarker=marker, isVisible=true
        ) else it.copy(isVisible=true) })
        if(mode=="delta" && deletes.isNotEmpty())hideDeletedSubscribers(scopeKey,deletes)
        if(mode=="bootstrap") {
            if(!hasMore) {
                hideOldScopeVersion(scopeKey,scopeVersion)
                hideMissingBootstrapRows(scopeKey,scopeVersion,marker!!)
                forgetOtherIncompleteScopes(scopeKey,scopeVersion)
                setActiveScope(active.copy(scopeVersion=scopeVersion,status="ready"))
            } else {
                setActiveScope(active.copy(scopeVersion=scopeVersion,status="bootstrapping"))
            }
        } else setActiveScope(active.copy(scopeVersion=scopeVersion,status="ready"))
        upsertSyncState(MobileSyncStateEntity(
            scopeKey=scopeKey,scopeVersion=scopeVersion,cursor=nextCursor,
            mode=if(mode=="bootstrap" && !hasMore)"delta" else mode,
            bootstrapMarker=marker?:old?.bootstrapMarker,
            pageCount=if(hasMore)(old?.pageCount?:0)+1 else 0,
            lastAppliedAt=serverTimeMillis,
            completed=!hasMore,
        ))
    }

    @Query("DELETE FROM subscribers")
    suspend fun clearAllSubscribers()

    @Query("DELETE FROM pending_readings WHERE status = 'synced'")
    suspend fun clearSyncedReadings()

    @Query("DELETE FROM pending_payments WHERE status = 'synced'")
    suspend fun clearSyncedPayments()
}
