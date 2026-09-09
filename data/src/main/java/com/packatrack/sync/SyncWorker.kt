package com.packatrack.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.packatrack.data.di.DataEntryPoint
import com.packatrack.notify.Notifier
import dagger.hilt.android.EntryPointAccessors
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DataEntryPoint::class.java)
        val repo = entryPoint.repository()
        val prefs = entryPoint.prefs()

        // Adaptive Sync: Skip during quiet hours (11 PM - 7 AM) or low battery (<15%) to save power.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 23 || hour < 7) return Result.success()

        val batteryStatus = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level != -1 && scale != -1) {
            val pct = level / scale.toFloat()
            if (pct < 0.15f) return Result.success()
        }

        return runCatching {
            val outcome = repo.refreshAll()
            if (outcome.notable.isNotEmpty()) {
                Notifier.postChanges(applicationContext, outcome.notable)
            }
            Result.success()
        }.getOrElse { error ->
            android.util.Log.e("SyncWorker", "Background sync failed", error)
            Result.retry()
        }
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
