package com.packatrack.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.packatrack.core.changelog.ChangeLogService
import com.packatrack.core.db.ChangeEntity
import com.packatrack.core.model.ChangeDisplay
import com.packatrack.core.util.TimeUtil
import com.packatrack.data.PrefsStore
import com.packatrack.data.R

object Notifier {
    private const val CHANNEL_ID = "parcel_changes"
    private const val SUMMARY_ID = 1001

    /** Extra on the notification's launch intent: the parcel to open on tap, if unambiguous. */
    const val EXTRA_OPEN_SHIPMENT_ID = "com.packatrack.app.OPEN_SHIPMENT_ID"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = context.getString(R.string.notice_channel_name)
            // A channel keeps the name it was first created with, so an install that predates the
            // translations would stay English for ever. Recreate it once, when the stored name no
            // longer matches the translated one (this also resets the user's importance choice).
            manager.getNotificationChannel(CHANNEL_ID)?.let { existing ->
                if (existing.name != name) manager.deleteNotificationChannel(CHANNEL_ID)
            }
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.notice_channel_description)
                },
            )
        }
    }

    fun postChanges(context: Context, changes: List<ChangeEntity>) {
        if (changes.isEmpty()) return
        val prefs = PrefsStore(context)
        if (!prefs.notificationsEnabled) return
        val filtered = changes.filter { change ->
            when (change.display?.category ?: ChangeDisplay.categoryFromWording(change.message)) {
                ChangeDisplay.Category.DELIVERED -> prefs.notifyOnDelivered
                ChangeDisplay.Category.EXCEPTION -> prefs.notifyOnExceptions
                ChangeDisplay.Category.TRANSIT -> prefs.notifyOnTransit
            }
        }
        if (filtered.isEmpty()) return
        // Tapping opens the specific parcel when every update is about the same one; a mixed
        // batch has no single target, so it falls back to the parcel list.
        val targetShipmentId = filtered.map { it.shipmentId }.distinct().singleOrNull()
        post(
            context,
            filtered.map { localizedText(context.resources, it) },
            targetShipmentId,
            prefs.biometricLock,
        )
    }

    /**
     * Wording for one change: translated from the [ChangeDisplay] attached when the change was
     * detected, or the stored English message when the row was read back from storage (a change
     * imported from a backup predates any display data).
     */
    fun localizedText(res: Resources, change: ChangeEntity): String {
        val display = change.display ?: return change.message
        val parcel = display.parcel.orEmpty()
        return when (display.kind) {
            ChangeDisplay.Kind.PROGRESS -> {
                val text = display.description.orEmpty()
                val timeMs = display.timeMs
                if (timeMs == null) {
                    res.getString(R.string.notice_progress, parcel, text)
                } else {
                    res.getString(R.string.notice_progress_timed, parcel, text, utcStamp(timeMs))
                }
            }
            ChangeDisplay.Kind.RENUMBERED -> res.getString(
                R.string.notice_renumbered,
                parcel,
                display.oldNumber.orEmpty(),
                display.newNumber.orEmpty(),
            )
            // The count is both the plural selector and an argument: "3 parcels", not "3 parcel".
            ChangeDisplay.Kind.COMBINED -> res.getQuantityString(
                R.plurals.notice_combined,
                display.count,
                parcel,
                display.count,
                display.into.orEmpty(),
            )
            ChangeDisplay.Kind.MERGED_WITH -> res.getString(
                R.string.notice_merged_with,
                parcel,
                display.into.orEmpty(),
            )
            ChangeDisplay.Kind.CONSOLIDATED -> res.getString(
                R.string.notice_consolidated,
                parcel,
                display.into.orEmpty(),
            )
            ChangeDisplay.Kind.FOLDED_INTO -> res.getString(
                R.string.notice_folded_into,
                parcel,
                display.into.orEmpty(),
            )
            ChangeDisplay.Kind.COURIER_ADDED -> res.getString(
                R.string.notice_courier_added,
                display.carrier.orEmpty(),
                display.newNumber.orEmpty(),
            )
            ChangeDisplay.Kind.DUPLICATE_REMOVED -> res.getString(
                R.string.notice_duplicate_removed,
                parcel,
                display.carrier.orEmpty(),
                display.oldNumber.orEmpty(),
                display.newNumber.orEmpty(),
            )
        }
    }

    /** UTC stamp shown beside a scan, matching the wording the change log already uses. */
    private fun utcStamp(timeMs: Long): String =
        "${TimeUtil.format(timeMs, ChangeLogService.UTC_STAMP_PATTERN).orEmpty()} UTC"

    private fun post(
        context: Context,
        messages: List<String>,
        shipmentId: Long?,
        redactContent: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            if (shipmentId != null) putExtra(EXTRA_OPEN_SHIPMENT_ID, shipmentId)
            // SINGLE_TOP with CLEAR_TOP delivers the tap to the running activity's onNewIntent.
            // CLEAR_TOP alone finishes and recreates a standard-launch activity, which drops its
            // back stack and App Lock state, and never reaches onNewIntent.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        } ?: return
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle().also { messages.take(5).forEach(it::addLine) }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_parcel)
            .setContentTitle(context.getString(R.string.notice_title))
            .setContentText(messages.first())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pending)
        if (redactContent) {
            // With App Lock on, a locked device must not show parcel names or numbers.
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_parcel)
                        .setContentTitle(context.getString(R.string.notice_title))
                        .setContentText(context.getString(R.string.notice_public_text))
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build(),
                )
        }
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, builder.build()) }
    }
}
