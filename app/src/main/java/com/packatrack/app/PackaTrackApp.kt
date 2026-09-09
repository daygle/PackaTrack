package com.packatrack.app

import android.app.Application
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import com.packatrack.notify.Notifier
import com.packatrack.sync.EmailImportWorker
import com.packatrack.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class PackaTrackApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var prefs: PrefsStore

    @Inject
    lateinit var repository: TrackingRepository

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)

        // Initialize heavy components (Database, Keystore) on a background thread
        // to avoid skipping frames on cold start.
        applicationScope.launch {
            // Trigger first DB access which loads SQLCipher and decrypts the key
            repository.observeActive()

            withContext(Dispatchers.Main) {
                SyncWorker.schedule(
                    this@PackaTrackApp,
                    prefs.syncIntervalHours,
                    prefs.wifiOnlySync
                )
                EmailImportWorker.schedule(this@PackaTrackApp)
            }
        }
    }
}
