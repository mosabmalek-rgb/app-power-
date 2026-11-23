package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SubscriberCacheTrust
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriberCacheTrustTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun revokedPermissionQuarantineSurvivesRepositoryRestartUntilVerifiedRefresh() {
        val preferences = context.getSharedPreferences(
            "delta-cache-trust-test", Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val original = SubscriberCacheTrust(preferences)
        assertFalse(original.isQuarantined())
        original.quarantine()

        // A newly constructed repository reads the same persisted flag.
        val afterProcessRestart = SubscriberCacheTrust(preferences)
        assertTrue(afterProcessRestart.isQuarantined())
        // Repeated failures cannot accidentally re-authorize local data.
        afterProcessRestart.quarantine()
        assertTrue(SubscriberCacheTrust(preferences).isQuarantined())

        // Only the caller that completed a validated Delta refresh may release it.
        afterProcessRestart.releaseAfterVerifiedRefresh()
        assertFalse(SubscriberCacheTrust(preferences).isQuarantined())
        preferences.edit().clear().commit()
    }
}
