package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncQueueStatusPolicyTest {
    @Test
    fun countsEveryStatusExceptSyncedAsUnresolved() {
        val statuses = listOf(
            "synced",
            "pending",
            "syncing",
            "retryable",
            "needs_review",
            "failed",
            "unexpected"
        )

        assertEquals(6, SyncQueueStatusPolicy.countUnresolved(statuses))
    }

    @Test
    fun returnsZeroWhenQueueIsEmptyOrFullySynced() {
        assertEquals(0, SyncQueueStatusPolicy.countUnresolved(emptyList()))
        assertEquals(
            0,
            SyncQueueStatusPolicy.countUnresolved(listOf("synced", "synced"))
        )
    }
}
