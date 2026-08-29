package com.packatrack.core.changelog

import com.packatrack.core.model.ParcelChange
import com.packatrack.core.model.Snapshot
import com.packatrack.core.util.FingerprintUtil

/**
 * The heart of PackaTrack's "understands AliExpress" logic.
 *
 * Compares two consecutive [Snapshot]s (previous vs. latest poll) for parcels under the
 * same user order and produces human-readable [ParcelChange]s:
 *
 *  - **Tracking number changed** - the old number no longer resolves but a new number's
 *    fingerprint suffix matches the old one (carriers append prefixes/suffixes when
 *    re-issuing), or the physical signature matches within tolerance.
 *  - **Packages combined** - several earlier numbers converge into one new number.
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

    /**
     * Detects "combined shipment": all previously tracked separately now
     * report a newer shared event whose description mentions consolidation.
     */
    fun detectCombination(
        previousByNumber: Map<String, Snapshot>,
        combinedSnapshot: Snapshot,
        mergeKeywords: List<String> =
            listOf("consolidat", "combined", "merged into", "packaged together"),
    ): ParcelChange.Combined? {
        val involved = previousByNumber.filterKeys { it != FingerprintUtil.normalize(combinedSnapshot.trackingNumber) }
        if (involved.size < 2) return null

        val keywordHit = involved.values.any { snap ->
            snap.events.any { ev -> mergeKeywords.any { kw -> ev.description.contains(kw, ignoreCase = true) } }
        }
        if (keywordHit) {
            return ParcelChange.Combined(involved.keys.toList(), combinedSnapshot.trackingNumber)
        }

        return null
    }

    /** Generates short display lines for the UI/notifications. */
    fun humanReadable(change: ParcelChange): String = when (change) {
        is ParcelChange.Renumbered ->
            "Tracking number changed from ${change.oldNumber} -> ${change.newNumber}"
        is ParcelChange.Combined ->
            "${change.mergedFrom.size} parcels combined into ${change.into}"
        is ParcelChange.Progress -> {
            val stamp = change.timeMs
                ?.let { java.time.Instant.ofEpochMilli(it).toString().replace('T', ' ').substringBefore('.') }
                ?.let { " — $it UTC" }
                ?: ""
            "${change.description}$stamp"
        }
        is ParcelChange.WeightChanged ->
            "Package re-weighed: ${change.fromGrams?.toInt() ?: "?"} g -> ${change.toGrams.toInt()} g"
    }
}
