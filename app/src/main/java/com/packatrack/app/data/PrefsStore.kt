package com.packatrack.app.data

import android.content.Context
import android.content.SharedPreferences

/** Tiny settings store backed by SharedPreferences. */
class PrefsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("packatrack_prefs", Context.MODE_PRIVATE)

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, true)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    var ausPostApiKey: String?
        get() = prefs.getString(KEY_AUSPOST_KEY, null)
        set(value) = prefs.edit().putString(KEY_AUSPOST_KEY, value?.trim()).apply()

    /** Background sync interval hours. */
    var syncIntervalHours: Int
        get() = prefs.getInt(KEY_SYNC_HOURS, 6)
        set(value) = prefs.edit().putInt(KEY_SYNC_HOURS, value.coerceIn(1, 48)).apply()

    companion object {
        const val KEY_DEMO = "demo_mode"
        const val KEY_AUSPOST_KEY = "auspost_key"
        const val KEY_SYNC_HOURS = "sync_interval_hours"
    }
}
