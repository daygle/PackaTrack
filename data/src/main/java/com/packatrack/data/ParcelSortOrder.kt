package com.packatrack.data

enum class ParcelSortOrder(
    val key: String
) {
    LAST_ACTIVITY("last_activity"),
    DATE_ADDED("date_added"),
    NAME("name"),
    DAYS_IN_TRANSIT("days_in_transit"),
    STATUS("status");

    companion object {
        fun fromKey(key: String?): ParcelSortOrder =
            entries.find { it.key == key } ?: LAST_ACTIVITY
    }
}
