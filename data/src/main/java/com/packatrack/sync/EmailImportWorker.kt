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
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class EmailImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DataEntryPoint::class.java)
        val repo = entryPoint.repository()
        val prefs = entryPoint.prefs()
        // Read once: the account can be changed from Settings while this worker runs.
        val account = prefs.smartImportAccount
        if (!prefs.smartImportEnabled || account.isNullOrBlank()) {
            return Result.success()
        }

        val startedAt = System.currentTimeMillis()
        return try {
            val service = GmailAuth.gmailService(applicationContext, account)

            // Only scan mail that arrived since the last successful run (capped to the last
            // 7 days), so a parcel the user deleted is not re-imported from the same email.
            val since = maxOf(prefs.smartImportLastRunAt, startedAt - LOOKBACK_MS) / 1000
            val query = "after:$since (shipped OR tracking OR consignment)"
            val messages = service.users().messages().list("me").setQ(query).execute().messages.orEmpty()

            val candidates = linkedSetOf<String>()
            for (msg in messages) {
                // The snippet is all we read, so skip downloading the full body.
                val message = service.users().messages().get("me", msg.id).setFormat("metadata").execute()
                CANDIDATE.findAll(message.snippet.orEmpty())
                    .map { it.value.uppercase() }
                    // Real tracking numbers are digit-heavy; this keeps ordinary words such as
                    // "IMPORTANT" (which fits the iMile pattern) from becoming parcels.
                    .filter { candidate -> candidate.count(Char::isDigit) >= MIN_DIGITS }
                    .filter { CarrierDetector.detectAll(it).isNotEmpty() }
                    .forEach(candidates::add)
            }
            for (candidate in candidates) {
                // Same automatic multi-carrier setup as a manual add; existing numbers are a no-op.
                // One bad number must not abort the rest of the scan, but cancellation must escape.
                try {
                    repo.addShipment(candidate, null, null, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not import a detected tracking number", e)
                }
            }

            prefs.smartImportLastRunAt = startedAt
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Email import failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "EmailImportWorker"
        private const val LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000
        private const val MIN_DIGITS = 6
        private val CANDIDATE = Regex("\\b[A-Z0-9]{8,25}\\b", RegexOption.IGNORE_CASE)

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
