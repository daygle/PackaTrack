package com.packatrack.app.data

import androidx.annotation.StringRes
import com.packatrack.app.R

enum class ParcelSortOrder(
    @StringRes val labelRes: Int,
    val key: String
) {
    LAST_ACTIVITY(R.string.sort_last_activity, "last_activity"),
    DATE_ADDED(R.string.sort_date_added, "date_added"),
    NAME(R.string.sort_name, "name"),
    DAYS_IN_TRANSIT(R.string.sort_days_in_transit, "days_in_transit"),
    STATUS(R.string.sort_status, "status");

    companion object {
        fun fromKey(key: String?): ParcelSortOrder =
            entries.find { it.key == key } ?: LAST_ACTIVITY
    }
}
