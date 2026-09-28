package com.packatrack.core.changelog

import com.packatrack.core.model.ParcelChange
import com.packatrack.core.model.Snapshot
import com.packatrack.core.util.FingerprintUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The heart of PackaTrack's "understands AliExpress" logic.
 *
 * Compares two consecutive [Snapshot]s (previous vs. latest poll) for parcels under the
 * same user order and produces human-readable [ParcelChange]s:
 *
 *  - **Tracking number changed** - the old number no longer resolves but a new number's
 *    fingerprint suffix matches the old one (carriers append prefixes/suffixes when
 *    re-issuing), or the physical signature matches within tolerance.
 *  - **Packages combined** - several earlier numbers report the consolidation event that a
 *    new number now carries.
 *  - **Progress** - ordinary checkpoint added.
 */
object ChangeLogService {

    /**
     * @param previousByNumber last known snapshot keyed by tracking number (may be empty)
     * @param current the fresh snapshot just fetched
     */
    fun detect(
        previousByNumber: Map<String, Snapshot>,
        current: Snapshot,
    ): List<ParcelChange> {
        val changes = mutableListOf<ParcelChange>()
        if (previousByNumber.isEmpty()) return changes

        // 1. Straight update on same number?
        val prevSame = previousByNumber[FingerprintUtil.normalize(current.trackingNumber)]
        if (prevSame != null) {
            val previousDescriptions = prevSame.events.mapTo(HashSet()) { it.description }
            val newestNewEvent = current.events
                .asSequence()
                .filterNot { it.description in previousDescriptions }
                .maxByOrNull { it.timeMs ?: Long.MIN_VALUE }
            if (newestNewEvent != null) {
                changes += ParcelChange.Progress(newestNewEvent.description, newestNewEvent.timeMs)
            }
            return changes
        }

        // 2. Renumbered? Find previous snapshot with matching fingerprint.
        val renames = mutableListOf<Pair<String, String>>()
        for ((oldNo, oldSnap) in previousByNumber) {
            val numMatch = FingerprintUtil.commonSuffixLength(oldNo, current.trackingNumber) >= 8
            val dimsMatch =
                oldSnap.dimensionsCm != null && current.dimensionsCm != null &&
                    kotlin.math.abs(oldSnap.dimensionsCm.lengthCm - current.dimensionsCm.lengthCm) <= 2.0
            if (numMatch || dimsMatch) renames += oldNo to current.trackingNumber
        }
        when {
            renames.isNotEmpty() ->
                changes += ParcelChange.Renumbered(renames.first().first, renames.first().second)
            else -> Unit
        }
        return changes
    }

    /** Event wording carriers use when several parcels are consolidated into one. */
    val MERGE_KEYWORDS = listOf("consolidat", "combined", "merged into", "packaged together")

    /** True when any of [snapshot]'s events mentions consolidation. */
    fun mentionsConsolidation(
        snapshot: Snapshot,
        mergeKeywords: List<String> = MERGE_KEYWORDS,
    ): Boolean = snapshot.events.any { ev -> mergeKeywords.any { kw -> ev.description.contains(kw, ignoreCase = true) } }

    /**
     * Detects "combined shipment": at least two parcels previously tracked separately report
     * the same consolidation event (same description, ignoring case) that the
     * [combinedSnapshot] now reports.
     *
     * A consolidation keyword on some *other* parcel alone is not enough - unrelated parcels
     * pass through the same consolidation warehouses - so the combined parcel must share it.
     */
    fun detectCombination(
        previousByNumber: Map<String, Snapshot>,
        combinedSnapshot: Snapshot,
        mergeKeywords: List<String> = MERGE_KEYWORDS,
    ): ParcelChange.Combined? {
        val sharedDescriptions = combinedSnapshot.events
            .map { it.description }
            .filter { desc -> mergeKeywords.any { kw -> desc.contains(kw, ignoreCase = true) } }
            .mapTo(HashSet()) { it.lowercase() }
        if (sharedDescriptions.isEmpty()) return null

        val combinedNumber = FingerprintUtil.normalize(combinedSnapshot.trackingNumber)
        val involved = previousByNumber
            .filter { (number, snap) ->
                FingerprintUtil.normalize(number) != combinedNumber &&
                    snap.events.any { it.description.lowercase() in sharedDescriptions }
            }
            .keys
        if (involved.size < 2) return null
        return ParcelChange.Combined(involved.toList(), combinedSnapshot.trackingNumber)
    }

    /** Generates short display lines for the UI/notifications. */
    fun humanReadable(change: ParcelChange): String = when (change) {
        is ParcelChange.Renumbered ->
            "Tracking number changed from ${change.oldNumber} -> ${change.newNumber}"
        is ParcelChange.Combined ->
            "${change.mergedFrom.size} parcels combined into ${change.into}"
        is ParcelChange.Progress -> {
            val stamp = change.timeMs
                ?.let { formatUtcTimestamp(it) }
                ?.let { " — $it" }
                ?: ""
            "${change.description}$stamp"
        }
    }

    /**
     * Formats epoch millis as "yyyy-MM-dd HH:mm" in UTC.
     * Uses SimpleDateFormat (available on all API levels) instead of java.time.Instant.
     */
    private fun formatUtcTimestamp(timeMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timeMs)) + " UTC"
    }
}
