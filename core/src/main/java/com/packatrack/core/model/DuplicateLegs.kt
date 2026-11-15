package com.packatrack.core.model

/** One courier leg and the scans it holds, for duplicate detection. */
data class LegScans(
    val legId: Long,
    val carrierId: String,
    val trackingNumber: String,
    /**
     * Creation order. The smallest value is the leg the user added first, and the one that is
     * kept when two legs duplicate each other.
     */
    val order: Long,
    /** Fingerprints of this leg's scans (see [scanFingerprint]); empty when nothing is stored. */
    val scans: Set<String>,
)

/** A leg that repeats another leg scan for scan, and the leg that survives it. */
data class DuplicateLeg(
    /** The leg to remove. */
    val legId: Long,
    /** The leg that keeps the scans. */
    val survivorLegId: Long,
    /** Tracking number of the removed leg, so it can be kept as an alias of the survivor. */
    val trackingNumber: String,
)

/** Stable fingerprint of one scan, for comparing two legs' timelines. */
fun scanFingerprint(timeMs: Long?, description: String): String =
    "${timeMs ?: "none"}|${description.trim()}"

/**
 * Legs that repeat another leg of the **same courier**, scan for scan.
 *
 * A carrier can report one parcel under several of its own numbers (Cainiao's `realMailNo` is
 * one), and an earlier version of the app turned each of those into an extra leg - so a parcel
 * showed the same courier twice with the same timeline. Such a leg carries no information: the
 * leg that already holds those scans is kept, and the later duplicate is reported for removal.
 *
 * Deliberately conservative:
 *  - different couriers are never duplicates, even with identical scans (a hand-off between two
 *    carriers legitimately reports the same journey twice);
 *  - the scan sets must be *equal*, not merely overlapping, so a leg that is ahead of the other is
 *    never dropped;
 *  - a leg with no scans is never treated as a duplicate, since two empty timelines prove nothing.
 *
 * @param legs legs of a single parcel, in any order.
 * @return duplicates to remove, each pointing at its survivor.
 */
fun findDuplicateLegs(legs: List<LegScans>): List<DuplicateLeg> {
    val survivors = HashMap<Pair<String, Set<String>>, LegScans>()
    val duplicates = mutableListOf<DuplicateLeg>()
    for (leg in legs.sortedBy { it.order }) {
        if (leg.scans.isEmpty()) continue
        val key = leg.carrierId to leg.scans
        val survivor = survivors[key]
        if (survivor == null) {
            survivors[key] = leg
        } else {
            duplicates += DuplicateLeg(
                legId = leg.legId,
                survivorLegId = survivor.legId,
                trackingNumber = leg.trackingNumber,
            )
        }
    }
    return duplicates
}
