package com.packatrack.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** User-configurable tracking behaviour backed by SharedPreferences. */
class PrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("packatrack_prefs", Context.MODE_PRIVATE)

    var ausPostApiKey: String?
        get() = prefs.getString(KEY_AUSPOST_KEY, null)
        set(value) = prefs.edit { putString(KEY_AUSPOST_KEY, value?.trim()) }

    var syncIntervalHours: Int
        get() = prefs.getInt(KEY_SYNC_HOURS, 6)
        set(value) = prefs.edit { putInt(KEY_SYNC_HOURS, value.coerceIn(1, 48)) }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    var notifyOnDelivered: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_DELIVERED, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_DELIVERED, value) }

    var notifyOnExceptions: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_EXCEPTIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_EXCEPTIONS, value) }

    var notifyOnTransit: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_TRANSIT, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_TRANSIT, value) }

    var wifiOnlySync: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit { putBoolean(KEY_WIFI_ONLY, value) }

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit { putString(KEY_THEME, value) }

    var sortOrder: String
        get() = prefs.getString(KEY_SORT_ORDER, "newest") ?: "newest"
        set(value) = prefs.edit { putString(KEY_SORT_ORDER, value) }

    var dateTimeFormat: String
        get() = prefs.getString(KEY_DATE_FORMAT, "dd MMM yyyy, HH:mm") ?: "dd MMM yyyy, HH:mm"
        set(value) = prefs.edit { putString(KEY_DATE_FORMAT, value) }

    var historySortOrder: String
        get() = prefs.getString(KEY_HISTORY_SORT, "newest") ?: "newest"
        set(value) = prefs.edit { putString(KEY_HISTORY_SORT, value) }

    var autoArchiveDelivered: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ARCHIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_ARCHIVE, value) }

    /** Newest change timestamp the user has dismissed from the home "Recent Activity" banner. */
    var recentActivityDismissedAt: Long
        get() = prefs.getLong(KEY_ACTIVITY_DISMISSED, 0L)
        set(value) = prefs.edit { putLong(KEY_ACTIVITY_DISMISSED, value) }

    companion object {
        const val KEY_AUSPOST_KEY = "auspost_key"
        const val KEY_SYNC_HOURS = "sync_interval_hours"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_NOTIFY_DELIVERED = "notify_delivered"
        const val KEY_NOTIFY_EXCEPTIONS = "notify_exceptions"
        const val KEY_NOTIFY_TRANSIT = "notify_transit"
        const val KEY_WIFI_ONLY = "wifi_only_sync"
        const val KEY_THEME = "theme_mode"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_DATE_FORMAT = "date_time_format"
        const val KEY_HISTORY_SORT = "history_sort_order"
        const val KEY_AUTO_ARCHIVE = "auto_archive_delivered"
        const val KEY_ACTIVITY_DISMISSED = "recent_activity_dismissed_at"
    }
}
