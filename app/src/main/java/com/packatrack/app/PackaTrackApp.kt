package com.packatrack.app

import android.app.Application
import android.content.Context
import com.packatrack.app.data.PrefsStore
import com.packatrack.app.data.TrackingRepository
import com.packatrack.app.notify.Notifier
import com.packatrack.app.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tiny manual DI graph - plenty for an app this size. */
class AppContainer(context: Context) {
    val prefs = PrefsStore(context)
    val repository = TrackingRepository(context.applicationContext, prefs)
}

class PackaTrackApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _container = MutableStateFlow<AppContainer?>(null)
    val containerState: StateFlow<AppContainer?> = _container.asStateFlow()

    /** Synchronous access for workers/background tasks. Block until ready if needed. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)

        // Initialize heavy components (Database, Keystore) on a background thread
        // to avoid Skipping frames on cold start.
        applicationScope.launch {
            val initializedContainer = AppContainer(this@PackaTrackApp)
            // Trigger first DB access which loads SQLCipher and decrypts the key
            initializedContainer.repository.observeActive()

            container = initializedContainer
            _container.value = initializedContainer

            withContext(Dispatchers.Main) {
                SyncWorker.schedule(
                    this@PackaTrackApp,
                    initializedContainer.prefs.syncIntervalHours,
                    initializedContainer.prefs.wifiOnlySync
                )
            }
        }
    }
}
