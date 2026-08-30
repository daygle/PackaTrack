package com.packatrack.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.packatrack.app.MainActivity
import com.packatrack.app.PackaTrackApp
import com.packatrack.app.R
import com.packatrack.app.data.db.ChangeEntity

object Notifier {
    private const val CHANNEL_ID = "parcel_changes"
    private const val SUMMARY_ID = 1001

    /** Extra on the notification's launch intent: the parcel to open on tap, if unambiguous. */
    const val EXTRA_OPEN_SHIPMENT_ID = "com.packatrack.app.OPEN_SHIPMENT_ID"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Parcel changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Delivery updates, exceptions and parcel changes"
            })
        }
    }

    fun postChanges(context: Context, changes: List<ChangeEntity>) {
        if (changes.isEmpty()) return
        val prefs = (context.applicationContext as? PackaTrackApp)?.containerState?.value?.prefs
            ?: return
        if (!prefs.notificationsEnabled) return
        val filtered = changes.filter { change ->
            val text = change.message.lowercase()
            when {
                text.contains("deliver") -> prefs.notifyOnDelivered
                text.contains("exception") || text.contains("failed") || text.contains("delay") || text.contains("customs") || text.contains("return") -> prefs.notifyOnExceptions
                else -> prefs.notifyOnTransit
            }
        }
        if (filtered.isEmpty()) return
        // Tapping opens the specific parcel when every update is about the same one; a mixed
        // batch has no single target, so it falls back to the parcel list.
        val targetShipmentId = filtered.map { it.shipmentId }.distinct().singleOrNull()
        post(context, filtered.map { it.message }, targetShipmentId)
    }

    private fun post(context: Context, messages: List<String>, shipmentId: Long?) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).apply {
            if (shipmentId != null) putExtra(EXTRA_OPEN_SHIPMENT_ID, shipmentId)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle().also { messages.take(5).forEach(it::addLine) }
        runCatching {
            NotificationManagerCompat.from(context).notify(
                SUMMARY_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_parcel)
                    .setContentTitle("PackaTrack Update")
                    .setContentText(messages.first())
                    .setStyle(style)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build(),
            )
        }
    }
}
