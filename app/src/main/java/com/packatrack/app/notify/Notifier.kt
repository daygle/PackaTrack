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

object Notifier {
    private const val CHANNEL_ID = "parcel_changes"
    private const val SUMMARY_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Parcel changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Delivery updates, exceptions and parcel changes"
            })
        }
    }

    fun postChanges(context: Context, messages: List<String>) {
        if (messages.isEmpty()) return
        val prefs = (context.applicationContext as? PackaTrackApp)?.container?.prefs
        if (prefs != null) {
            if (!prefs.notificationsEnabled) return
            val filtered = messages.filter { message ->
                val text = message.lowercase()
                when {
                    text.contains("deliver") -> prefs.notifyOnDelivered
                    text.contains("exception") || text.contains("failed") || text.contains("delay") || text.contains("customs") || text.contains("return") -> prefs.notifyOnExceptions
                    else -> prefs.notifyOnTransit
                }
            }
            if (filtered.isEmpty()) return
            return post(context, filtered)
        }
        post(context, messages)
    }

    private fun post(context: Context, messages: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle().also { messages.take(5).forEach(it::addLine) }
        runCatching {
            NotificationManagerCompat.from(context).notify(
                SUMMARY_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_parcel)
                    .setContentTitle("PackaTrack update")
                    .setContentText(messages.first())
                    .setStyle(style)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build(),
            )
        }
    }
}
