package com.packatrack.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.packatrack.app.PackaTrackApp
import com.packatrack.core.detect.CarrierDetector
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.util.Collections
import java.util.concurrent.TimeUnit

class EmailImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PackaTrackApp).containerState.value ?: return Result.retry()
        val prefs = container.prefs
        if (!prefs.smartImportEnabled || prefs.smartImportAccount.isNullOrBlank()) {
            return Result.success()
        }

        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext,
                Collections.singleton(GmailScopes.GMAIL_READONLY)
            ).setSelectedAccountName(prefs.smartImportAccount)

            val service = Gmail.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("PackaTrack").build()

            // Search for messages from the last 7 days containing common shipping keywords
            val query = "after:${(System.currentTimeMillis() / 1000) - 7 * 24 * 3600} (shipped OR tracking OR consignment)"
            val messagesResponse = service.users().messages().list("me").setQ(query).execute()
            val messages = messagesResponse.messages ?: emptyList()

            val repo = container.repository
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
