package com.packatrack.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** User-configurable tracking behaviour backed by Keystore-encrypted preferences. */
class PrefsStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            appContext,
            "packatrack_prefs_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migrateLegacy(appContext.getSharedPreferences("packatrack_prefs", Context.MODE_PRIVATE))
    }

    private fun migrateLegacy(legacy: SharedPreferences) {
        if (prefs.getBoolean(KEY_MIGRATED, false) || !legacy.all.keys.any { it != KEY_MIGRATED }) return
        prefs.edit {
            legacy.getString(KEY_AUSPOST_KEY, null)?.let { putString(KEY_AUSPOST_KEY, it) }
            if (legacy.contains(KEY_SYNC_HOURS)) putInt(KEY_SYNC_HOURS, legacy.getInt(KEY_SYNC_HOURS, 6))
            if (legacy.contains(KEY_NOTIFICATIONS)) putBoolean(KEY_NOTIFICATIONS, legacy.getBoolean(KEY_NOTIFICATIONS, true))
            if (legacy.contains(KEY_NOTIFY_DELIVERED)) putBoolean(KEY_NOTIFY_DELIVERED, legacy.getBoolean(KEY_NOTIFY_DELIVERED, true))
            if (legacy.contains(KEY_NOTIFY_EXCEPTIONS)) putBoolean(KEY_NOTIFY_EXCEPTIONS, legacy.getBoolean(KEY_NOTIFY_EXCEPTIONS, true))
            if (legacy.contains(KEY_NOTIFY_TRANSIT)) putBoolean(KEY_NOTIFY_TRANSIT, legacy.getBoolean(KEY_NOTIFY_TRANSIT, true))
            if (legacy.contains(KEY_WIFI_ONLY)) putBoolean(KEY_WIFI_ONLY, legacy.getBoolean(KEY_WIFI_ONLY, false))
            if (legacy.contains(KEY_THEME)) putString(KEY_THEME, legacy.getString(KEY_THEME, "system"))
            if (legacy.contains(KEY_SORT_ORDER)) putString(KEY_SORT_ORDER, legacy.getString(KEY_SORT_ORDER, "newest"))
            if (legacy.contains(KEY_DATE_FORMAT)) putString(KEY_DATE_FORMAT, legacy.getString(KEY_DATE_FORMAT, DEFAULT_DATE_FORMAT))
            if (legacy.contains(KEY_HISTORY_SORT)) putString(KEY_HISTORY_SORT, legacy.getString(KEY_HISTORY_SORT, "newest"))
            if (legacy.contains(KEY_AUTO_ARCHIVE)) putBoolean(KEY_AUTO_ARCHIVE, legacy.getBoolean(KEY_AUTO_ARCHIVE, false))
            if (legacy.contains(KEY_ACTIVITY_DISMISSED)) putLong(KEY_ACTIVITY_DISMISSED, legacy.getLong(KEY_ACTIVITY_DISMISSED, 0L))
            putBoolean(KEY_MIGRATED, true)
        }
        legacy.edit().clear().apply()
    }

    var ausPostApiKey: String?
        get() = prefs.getString(KEY_AUSPOST_KEY, null)
        set(value) = prefs.edit { if (value.isNullOrBlank()) remove(KEY_AUSPOST_KEY) else putString(KEY_AUSPOST_KEY, value.trim()) }

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
        get() = prefs.getString(KEY_DATE_FORMAT, DEFAULT_DATE_FORMAT) ?: DEFAULT_DATE_FORMAT
        set(value) = prefs.edit { putString(KEY_DATE_FORMAT, value) }

    var historySortOrder: String
        get() = prefs.getString(KEY_HISTORY_SORT, "newest") ?: "newest"
        set(value) = prefs.edit { putString(KEY_HISTORY_SORT, value) }

    var autoArchiveDelivered: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ARCHIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_ARCHIVE, value) }

    var recentActivityDismissedAt: Long
        get() = prefs.getLong(KEY_ACTIVITY_DISMISSED, 0L)
        set(value) = prefs.edit { putLong(KEY_ACTIVITY_DISMISSED, value) }

    companion object {
        private const val DEFAULT_DATE_FORMAT = "dd MMM yyyy, HH:mm"
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
        private const val KEY_MIGRATED = "encrypted_preferences_migrated"
    }
}
