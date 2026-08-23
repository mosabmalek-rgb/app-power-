package com.example

import com.example.data.PendingSyncEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class PendingSyncEventEntityTest {
    @Test
    fun `queued sync events receive unique idempotency keys and zero counters`() {
        val first = PendingSyncEventEntity(branchId = 7, eventType = "sync_cycle", status = "success")
        val second = PendingSyncEventEntity(branchId = 7, eventType = "sync_cycle", status = "success")

        UUID.fromString(first.eventKey)
        assertNotEquals(first.eventKey, second.eventKey)
        assertEquals(0, first.syncedCount)
        assertEquals(0, first.retryableCount)
        assertEquals(0, first.needsReviewCount)
    }
}
