package com.packatrack.app

import android.app.Application
import android.util.Log
import com.packatrack.data.PrefsStore
import com.packatrack.data.db.warmUpDatabase
import com.packatrack.notify.Notifier
import com.packatrack.sync.EmailImportWorker
import com.packatrack.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PackaTrackApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var prefs: PrefsStore

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)

        applicationScope.launch {
            SyncWorker.schedule(this@PackaTrackApp, prefs.syncIntervalHours, prefs.wifiOnlySync)
            EmailImportWorker.schedule(this@PackaTrackApp)

            // Load SQLCipher and unwrap the database key via the Keystore off the main thread,
            // so the first screen's repository finds the database already initialised. A
            // failure here resurfaces (and is reported) on the database's first real use.
            runCatching { warmUpDatabase(this@PackaTrackApp) }
                .onFailure { Log.w("PackaTrackApp", "Database warm-up failed", it) }
        }
    }
}
