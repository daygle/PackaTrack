package com.packatrack.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.packatrack.data.di.DataEntryPoint
import com.packatrack.core.detect.CarrierDetector
import dagger.hilt.android.EntryPointAccessors
import com.google.api.services.gmail.Gmail
import java.util.concurrent.TimeUnit

class EmailImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DataEntryPoint::class.java)
        val repo = entryPoint.repository()
        val prefs = entryPoint.prefs()
        if (!prefs.smartImportEnabled || prefs.smartImportAccount.isNullOrBlank()) {
            return Result.success()
        }

        return try {
            val service = GmailAuth.gmailService(applicationContext, prefs.smartImportAccount!!)

            // Search for messages from the last 7 days containing common shipping keywords
            val query = "after:${(System.currentTimeMillis() / 1000) - 7 * 24 * 3600} (shipped OR tracking OR consignment)"
            val messagesResponse = service.users().messages().list("me").setQ(query).execute()
            val messages = messagesResponse.messages ?: emptyList()

            val broadPattern = Regex("\\b[A-Z0-9]{8,25}\\b", RegexOption.IGNORE_CASE)

            for (msg in messages) {
                val message = service.users().messages().get("me", msg.id).setFormat("full").execute()
                val body = message.snippet ?: "" // Simplest start: snippet often has the number

                broadPattern.findAll(body).forEach { match ->
                    val candidate = match.value
                    val detected = CarrierDetector.detectAll(candidate)
                    if (detected.isNotEmpty()) {
                        // Attempt to add. Repository should handle duplicates internally.
                        repo.addShipment(candidate, null, null, detected.first())
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("EmailImportWorker", "Email import failed", e)
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EmailImportWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "packatrack-email-import",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
