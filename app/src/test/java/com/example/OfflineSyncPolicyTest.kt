package com.example

import com.example.data.OfflineSyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSyncPolicyTest {
    @Test
    fun `network failure remains retryable`() {
        assertEquals("retryable", OfflineSyncPolicy.forNetworkFailure().status)
    }

    @Test
    fun `authentication failure requires technician review`() {
        assertEquals("needs_review", OfflineSyncPolicy.forHttpStatus(401).status)
    }

    @Test
    fun `validation conflict requires technician review`() {
        assertEquals("needs_review", OfflineSyncPolicy.forHttpStatus(409).status)
    }

    @Test
    fun `server failure remains retryable`() {
        assertEquals("retryable", OfflineSyncPolicy.forHttpStatus(503).status)
    }
}
