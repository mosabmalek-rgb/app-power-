package com.example

import com.example.data.OfflineSyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSyncPolicyTest {
    @Test
    fun `reading values preserve entered precision from zero to two decimals`() {
        assertEquals("128", ReadingValuePrecision.normalizeForSync(128.0, "128"))
        assertEquals("128.0", ReadingValuePrecision.normalizeForSync(128.0, "128.0"))
        assertEquals("128.5", ReadingValuePrecision.normalizeForSync(128.5, "128.5"))
        assertEquals("128.50", ReadingValuePrecision.normalizeForSync(128.5, "128.50"))
        assertEquals("128.52", ReadingValuePrecision.normalizeForSync(128.52, "128.52"))
    }

    @Test
    fun `reading formatter rejects more than two decimals and invalid numbers`() {
        assertEquals(null, ReadingValuePrecision.normalizeForSync(128.123, "128.123"))
        assertEquals(null, ReadingValuePrecision.normalizeForSync(128.0, "128.123"))
        assertEquals(null, ReadingValuePrecision.normalizeForSync(-1.0, "-1"))
        assertEquals(null, ReadingValuePrecision.normalizeForSync(Double.NaN, "NaN"))
        assertEquals("128", ReadingValuePrecision.normalizeForSync(128.0))
        assertEquals("128.52", ReadingValuePrecision.normalizeForSync(128.52))
    }

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
