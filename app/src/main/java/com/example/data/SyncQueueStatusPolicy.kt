package com.example.data

/**
 * Queue records are complete only after the local sync state is explicitly "synced".
 * Unknown and failure states remain unresolved so the UI cannot report a false success.
 */
object SyncQueueStatusPolicy {
    fun countUnresolved(statuses: Iterable<String>): Int =
        statuses.count { !it.equals(SYNCED_STATUS, ignoreCase = true) }

    private const val SYNCED_STATUS = "synced"
}
