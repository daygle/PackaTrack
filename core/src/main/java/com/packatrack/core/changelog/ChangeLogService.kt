package com.packatrack.core.changelog

import com.packatrack.core.model.ParcelChange
import com.packatrack.core.model.Snapshot
import com.packatrack.core.util.FingerprintUtil
import com.packatrack.core.util.TimeUtil

/**
 * The heart of PackaTrack's "understands AliExpress" logic.
 *
 * Compares two consecutive [Snapshot]s (previous vs. latest poll) for parcels under the
 * same user order and produces human-readable [ParcelChange]s:
 *
 *  - **Tracking number changed** - the old number no longer resolves but a new number's
 *    fingerprint suffix matches the old one (carriers append prefixes/suffixes when
 *    re-issuing).
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

        // 2. Renumbered? Find the first previous number sharing a long enough suffix.
        val renamedFrom = previousByNumber.keys.firstOrNull { oldNo ->
            FingerprintUtil.commonSuffixLength(oldNo, current.trackingNumber) >= 8
        }
        if (renamedFrom != null) {
            changes += ParcelChange.Renumbered(renamedFrom, current.trackingNumber)
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
                ?.let { " - $it" }
                ?: ""
            "${change.description}$stamp"
        }
    }

    /** Formats epoch millis as "yyyy-MM-dd HH:mm UTC", reusing TimeUtil's cached formatter. */
    private fun formatUtcTimestamp(timeMs: Long): String =
        "${TimeUtil.format(timeMs, UTC_STAMP_PATTERN)} UTC"

    /** Pattern of the UTC stamp in stored change messages; notifications reuse it. */
    const val UTC_STAMP_PATTERN = "yyyy-MM-dd HH:mm"
}
