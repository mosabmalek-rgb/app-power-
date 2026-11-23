package com.example

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CycleDateRange
import com.example.data.FieldReadingDatabase
import com.example.data.PendingReadingEntity
import com.example.data.ReadingCycleType
import com.example.data.api.MobileSessionResponse
import com.example.data.api.MobileSessionStore
import com.example.data.api.MobileUser
import com.example.data.repository.FieldReadingRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure Robolectric / Room regression: queued writes must recheck identity AFTER
 * waiting for the same lock used by login, logout and Delta refresh.
 * No HTTP, production data or Android device is involved.
 */
@RunWith(RobolectricTestRunner::class)
class FieldReadingAccountWriteRaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
        .allowMainThreadQueries().build()
    private val store = MobileSessionStore(context)
    private val prefs = context.getSharedPreferences("field_reading_prefs", Context.MODE_PRIVATE)
    private val scope = "1:2:7"

    // Existing private process-wide mutex: reflection is confined to this regression test.
    // Never replace it with a test-only mutex; that would not test the production lock.
    private fun accountMutex(): Mutex {
        val field = FieldReadingRepository::class.java.declaredFields
            .single { it.name == "accountScopeMutex" }
        field.isAccessible = true
        return field.get(null) as Mutex
    }

    private fun authorize() {
        store.clear()
        prefs.edit().clear().commit()
        val payload = """{"tenantId":1,"sessionId":99,"sub":"2"}"""
        val encoded = Base64.encodeToString(payload.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val token = "test.$encoded.signature"
        store.save(MobileSessionResponse(
            accessToken = token,
            refreshToken = "test-refresh",
            expiresIn = 3600,
            user = MobileUser(2, "Tester", "field", 1, listOf(7),
                "2099-01-01T00:00:00Z")
        ))
        prefs.edit().putLong("profile_user_id", 2).putLong("profile_tenant_id", 1)
            .putLong("active_branch_id", 7).putString("subscriber_cache_scope", scope)
            .commit()
    }

    @After fun close() {
        store.clear()
        prefs.edit().clear().commit()
        db.close()
    }

    @Test fun pendingPaymentCannotBeInsertedAfterSessionRevokedWhileWaitingForAccountLock() = runBlocking {
        authorize()
        val repo = FieldReadingRepository(context, db.dao(), store)
        val lock = accountMutex()
        lock.lock()
        try {
            val attempted = async(start = CoroutineStart.UNDISPATCHED) {
                repo.savePayment("15", 21.5, "CASH", "offline")
            }
            assertFalse(attempted.isCompleted)
            store.clear() // Simulates credential revocation/account rotation while queued.
            lock.unlock()
            assertFalse(attempted.await())
            assertEquals(0, db.dao().getPendingPaymentsWithStatus("pending").size)
        } finally {
            if (lock.isLocked) lock.unlock()
        }
    }

    @Test fun pendingReadingCannotBeInsertedAfterSessionRevokedWhileWaitingForAccountLock() = runBlocking {
        authorize()
        val repo = FieldReadingRepository(context, db.dao(), store)
        val originalKey = "original-outbox-reference"
        db.dao().insertPendingReading(PendingReadingEntity(
            subscriberId = "15", currentReading = 5.0, readingDate = "2026-11-01 12:00",
            notes = "", latitude = 0.0, longitude = 0.0, photoPath = null,
            status = "retryable", clientReferenceId = originalKey
        ))
        val lock = accountMutex()
        lock.lock()
        try {
            val attempted = async(start = CoroutineStart.UNDISPATCHED) {
                repo.saveReading(
                    subscriberId = "16", currentReading = 9.0, notes = "",
                    latitude = 0.0, longitude = 0.0, photoPath = null,
                    isReadingReset = false,
                    cycleRange = CycleDateRange(
                        cycleType = ReadingCycleType.MONTHLY,
                        startMillis = 0,
                        endMillis = Long.MAX_VALUE - 1,
                        labelAr = "test", shortRangeText = "test",
                        cycleKey = "cycle-test"
                    )
                )
            }
            assertFalse(attempted.isCompleted)
            store.clear()
            lock.unlock()
            assertTrue(attempted.await().isFailure)
            val existing = db.dao().getPendingReadingsWithStatus("retryable")
            assertEquals(1, existing.size)
            assertEquals(originalKey, existing.single().clientReferenceId)
            assertEquals(0, db.dao().getPendingReadingsWithStatus("pending").size)
        } finally {
            if (lock.isLocked) lock.unlock()
        }
    }
}
