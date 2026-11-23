package com.example.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Durable fail-closed cache quarantine for permission/cursor scope rejection.
 * Rows and offline outbox stay on disk. Other repository instances must also
 * stop showing cached rows immediately when one instance is denied access.
 */
internal class SubscriberCacheTrust(private val preferences: SharedPreferences) {
    private companion object {
        const val QUARANTINE_KEY = "subscriber_cache_requires_validation"
        // UI and WorkManager run independent repository instances in one process.
        // Persistence survives process restarts; this signal reaches active flows.
        val revisions = MutableStateFlow(0L)
    }

    val changes: StateFlow<Long> get() = revisions

    fun isQuarantined(): Boolean = preferences.getBoolean(QUARANTINE_KEY, false)

    fun quarantine() {
        check(preferences.edit().putBoolean(QUARANTINE_KEY, true).commit()) {
            "Unable to persist subscriber cache quarantine"
        }
        revisions.update { it + 1L }
    }

    fun releaseAfterVerifiedRefresh() {
        check(preferences.edit().putBoolean(QUARANTINE_KEY, false).commit()) {
            "Unable to persist authorized subscriber cache refresh"
        }
        revisions.update { it + 1L }
    }
}
