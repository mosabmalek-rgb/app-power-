package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MobileSessionResponse
import com.example.data.api.MobileSessionStore
import com.example.data.api.MobileUser
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Fake session-only tests: no network, device, or hosted authentication. */
@RunWith(RobolectricTestRunner::class)
class MobileSessionRefreshRaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = MobileSessionStore(context)
    private val user = MobileUser(
        2L, "Synthetic", "field", 1L, listOf(7L), "2099-01-01T00:00:00Z"
    )

    private fun response(access: String, refresh: String) =
        MobileSessionResponse(access, refresh, 3600L, user)

    @After fun clean() { store.clear() }

    @Test fun lateRefreshAfterLogoutCannotResurrectCredentials() {
        store.clear()
        store.save(response("access-old", "refresh-old"))
        store.clear()
        assertFalse(store.saveRefreshResponseIfCurrent(
            "refresh-old", response("access-late", "refresh-late")
        ))
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertFalse(store.isLoggedIn())
    }

    @Test fun oldRefreshResponseCannotOverwriteNewerAccountSession() {
        store.clear()
        store.save(response("access-old", "refresh-old"))
        store.save(response("access-new", "refresh-new"))
        assertFalse(store.saveRefreshResponseIfCurrent(
            "refresh-old", response("access-stale", "refresh-stale")
        ))
        assertEquals("access-new", store.accessToken())
        assertEquals("refresh-new", store.refreshToken())
    }

    @Test fun oldRefreshResponseCannotOverwriteAuthenticatorRotation() {
        store.clear()
        store.save(response("access-old", "refresh-old"))
        store.saveRefreshedSession("access-new", "refresh-new", 3600, null)
        assertFalse(store.saveRefreshResponseIfCurrent(
            "refresh-old", response("access-stale", "refresh-stale")
        ))
        assertEquals("access-new", store.accessToken())
        assertEquals("refresh-new", store.refreshToken())
    }

    @Test fun currentRefreshResponseRotatesTokensWithoutRemovingLocalPin() {
        store.clear()
        store.save(response("access-old", "refresh-old"))
        store.setLocalPin("1234")
        assertTrue(store.saveRefreshResponseIfCurrent(
            "refresh-old", response("access-current", "refresh-current")
        ))
        assertEquals("access-current", store.accessToken())
        assertEquals("refresh-current", store.refreshToken())
        assertTrue(store.verifyLocalPin("1234"))
    }
}
