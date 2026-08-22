package com.example

import com.example.data.api.RemoteConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigValidatorTest {
    @Test
    fun `accepts HTTPS origin and normalizes trailing slash`() {
        assertEquals("https://powerhub.example.com/", RemoteConfigValidator.normalizeApiBaseUrl("https://powerhub.example.com"))
    }

    @Test
    fun `rejects non HTTPS configuration`() {
        assertNull(RemoteConfigValidator.normalizeApiBaseUrl("http://powerhub.example.com/"))
    }

    @Test
    fun `rejects API path because mobile routes already include api prefix`() {
        assertNull(RemoteConfigValidator.normalizeApiBaseUrl("https://powerhub.example.com/api/"))
    }
}
