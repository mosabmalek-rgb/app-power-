package com.example.data

import android.content.SharedPreferences

/**
 * Durable fail-closed cache quarantine for permission/cursor scope rejection.
 * Rows and offline outbox remain on disk; their UI is inaccessible until a
 * successful, authorized subscriber refresh revalidates the scope.
 */
internal class SubscriberCacheTrust(private val preferences: SharedPreferences) {
    private companion object {
        const val QUARANTINE_KEY = "subscriber_cache_requires_validation"
    }

    fun isQuarantined(): Boolean = preferences.getBoolean(QUARANTINE_KEY, false)

    fun quarantine() {
        check(preferences.edit().putBoolean(QUARANTINE_KEY, true).commit()) {
            "Unable to persist subscriber cache quarantine"
        }
    }

    fun releaseAfterVerifiedRefresh() {
        check(preferences.edit().putBoolean(QUARANTINE_KEY, false).commit()) {
            "Unable to persist authorized subscriber cache refresh"
        }
    }
}
