package com.packatrack.core.model

/**
 * A detected change between two polls/snapshots of a parcel:
 * renumbering, package combination/merge, weight change or progress.
 */
sealed interface ParcelChange {
    data class Renumbered(val oldNumber: String, val newNumber: String) : ParcelChange
    data class Combined(val mergedFrom: List<String>, val into: String) : ParcelChange
    data class Progress(
        val description: String,
        /** Epoch millis of the tracking event, or null when the carrier reports no time. */
        val timeMs: Long? = null,
    ) : ParcelChange
    data class WeightChanged(val fromGrams: Double?, val toGrams: Double) : ParcelChange
}
