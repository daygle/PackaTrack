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
import com.packatrack.app.R

object Notifier {

    private const val CHANNEL_ID = "parcel_changes"
    private const val SUMMARY_ID = 1001

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Parcel changes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Renumbered parcels, combined shipments and delivery updates" }
        manager.createNotificationChannel(channel)
    }

    /** Posts a summary of detected changes; silent no-op when permission is missing. */
    fun postChanges(context: Context, messages: List<String>) {
        if (messages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle()
        for (m in messages.take(5)) style.addLine(m)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_parcel)
            .setContentTitle("PackaTrack update")
            .setContentText(messages.first())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(SUMMARY_ID, notification)
        }
    }
}
