package com.packatrack.app.data

import android.content.Context
import android.content.SharedPreferences

/** User-configurable tracking behaviour backed by SharedPreferences. */
class PrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("packatrack_prefs", Context.MODE_PRIVATE)

    var ausPostApiKey: String?
        get() = prefs.getString(KEY_AUSPOST_KEY, null)
        set(value) = prefs.edit().putString(KEY_AUSPOST_KEY, value?.trim()).apply()

    var syncIntervalHours: Int
        get() = prefs.getInt(KEY_SYNC_HOURS, 6)
        set(value) = prefs.edit().putInt(KEY_SYNC_HOURS, value.coerceIn(1, 48)).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var notifyOnDelivered: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_DELIVERED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_DELIVERED, value).apply()

    var notifyOnExceptions: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_EXCEPTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_EXCEPTIONS, value).apply()

    var notifyOnTransit: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_TRANSIT, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_TRANSIT, value).apply()

    var wifiOnlySync: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    companion object {
        const val KEY_AUSPOST_KEY = "auspost_key"
        const val KEY_SYNC_HOURS = "sync_interval_hours"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_NOTIFY_DELIVERED = "notify_delivered"
        const val KEY_NOTIFY_EXCEPTIONS = "notify_exceptions"
        const val KEY_NOTIFY_TRANSIT = "notify_transit"
        const val KEY_WIFI_ONLY = "wifi_only_sync"
        const val KEY_THEME = "theme_mode"
    }
}
