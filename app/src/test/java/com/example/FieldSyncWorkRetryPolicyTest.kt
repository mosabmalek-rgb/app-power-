package com.example

import com.example.data.FieldSyncWorkRetryPolicy
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FieldSyncWorkRetryPolicyTest {
    private fun http(code: Int) = HttpException(
        Response.error<Any>(code,
            "{}".toResponseBody("application/json".toMediaType()))
    )

    @Test fun authorizationDenialWaitsForUserRatherThanRetryingIndefinitely() {
        assertFalse(FieldSyncWorkRetryPolicy.shouldRetry(http(401)))
        assertFalse(FieldSyncWorkRetryPolicy.shouldRetry(http(403)))
    }

    @Test fun transientServerAndNetworkProblemsRemainRetryable() {
        for (code in listOf(400, 409, 422, 429, 500, 503)) {
            assertTrue("status $code", FieldSyncWorkRetryPolicy.shouldRetry(http(code)))
        }
        assertTrue(FieldSyncWorkRetryPolicy.shouldRetry(IOException("synthetic outage")))
    }
}
