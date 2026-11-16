package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.data.repository.FieldReadingRepository
import java.util.concurrent.TimeUnit

class FieldReadingSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = FieldReadingRepository(applicationContext, FieldReadingDatabase.getDatabase(applicationContext).dao())
        val sync = repository.syncAllData { _, _ -> }
        if (sync.isFailure) return Result.retry()
        // Subscriber refresh is independent of the pending-reading queue.
        // A failed refresh leaves the last complete local snapshot intact.
        val refresh = repository.refreshSubscribersIfStale()
        return if (sync.getOrThrow().retryable > 0 || refresh.isFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "powerhub_field_reading_sync"
        private const val PERIODIC_WORK_NAME = "powerhub_field_reading_periodic_sync"

        fun enqueue(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<FieldReadingSyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }
        }

        fun schedulePeriodic(context: Context) {
            runCatching {
                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val request = PeriodicWorkRequestBuilder<FieldReadingSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
