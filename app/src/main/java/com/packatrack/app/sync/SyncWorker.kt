package com.packatrack.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.packatrack.app.PackaTrackApp
import com.packatrack.app.notify.Notifier
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PackaTrackApp).container
        val outcome = container.repository.refreshAll()
        if (outcome.notable.isNotEmpty()) {
            Notifier.postChanges(applicationContext, outcome.notable.map { it.message })
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours.toLong().coerceIn(1, 48), TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "packatrack-periodic-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun syncNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "packatrack-manual-sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
