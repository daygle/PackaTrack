package com.packatrack.app

import android.app.Application
import android.content.Context
import com.packatrack.app.data.PrefsStore
import com.packatrack.app.data.TrackingRepository
import com.packatrack.app.notify.Notifier
import com.packatrack.app.sync.SyncWorker

/** Tiny manual DI graph — plenty for an app this size. */
class AppContainer(context: Context) {
    val prefs = PrefsStore(context)
    val repository = TrackingRepository(context.applicationContext, prefs)
}

class PackaTrackApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannel(this)
        SyncWorker.schedule(this, container.prefs.syncIntervalHours, container.prefs.wifiOnlySync)
    }
}
