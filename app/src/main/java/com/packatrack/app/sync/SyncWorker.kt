package com.packatrack.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.packatrack.app.PackaTrackApp
import com.packatrack.app.notify.Notifier
import androidx.work.Constraints
import androidx.work.NetworkType
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
        fun schedule(context: Context, intervalHours: Int, wifiOnly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours.toLong().coerceIn(1, 48), TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "packatrack-periodic-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
