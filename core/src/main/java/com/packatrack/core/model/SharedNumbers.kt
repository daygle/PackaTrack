package com.packatrack.core.model

import com.packatrack.core.util.FingerprintUtil

/** One courier leg reduced to the numbers it is known under, for shared-number detection. */
data class NumberedLeg(
    val legId: Long,
    /** Parcel this leg belongs to. */
    val shipmentId: Long,
    /** Number the leg is currently polled under. */
    val trackingNumber: String,
    /** Previous/alternate numbers for the same leg, comma separated as stored on the leg. */
    val aliasNumbers: String = "",
) {
    /** Alias entries, trimmed and without blanks. */
    fun aliases(): List<String> = aliasNumbers.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Two parcels that are tracked under one number, so they are one physical parcel: [ownerLegId]
 * sits on the parcel that survives and [mergedLegId] on the parcel folded into it.
 */
data class SharedNumberMerge(
    val ownerLegId: Long,
    val ownerShipmentId: Long,
    val mergedLegId: Long,
    val mergedShipmentId: Long,
    /** Normalized number the parcels share; live on at least one leg, an alias on the other. */
    val number: String,
)

/**
 * Parcels that must be combined because one number shows up on both of them.
 *
 * Two kinds of evidence count, strongest first:
 *  - the same **current** number is tracked on two parcels, so a carrier issued one number to
 *    both of them;
 *  - a number another parcel tracks **now** appears in this parcel's alias list, which is how a
 *    parcel keeps the hand-off number it was polled under before a renumbering, or one no courier
 *    had claimed yet when it was first reported.
 *
 * What deliberately does **not** count is an alias matching another parcel's alias. Aliases are a
 * mixed bag: a number withdrawn after a renumbering, or an unconfirmed hand-off number. When two
 * parcels overlap only through an alias, neither has a courier saying the number is live, so the
 * shared text may just be a stale hand-off or a number a carrier later re-issued to a different
 * shipment - merging on that would fold unrelated parcels together. Requiring one live leg anchors
 * the rule to what a carrier is actually tracking. The number must also match in full after
 * normalization, never as a prefix or substring of another number.
 *
 * @param legs legs of the active parcels, in any order.
 * @return one merge per parcel pair, oldest leg winning when the number is live on several.
 */
fun findSharedNumberMerges(legs: List<NumberedLeg>): List<SharedNumberMerge> {
    if (legs.size < 2) return emptyList()
    val ordered = legs.sortedBy { it.legId }
    val liveByNumber = ordered
        .groupBy { FingerprintUtil.normalize(it.trackingNumber) }
        .filterKeys { it.isNotEmpty() }

    // One merge per parcel pair: the strongest evidence (a live number) is collected first.
    val merges = LinkedHashMap<Pair<Long, Long>, SharedNumberMerge>()

    fun addMerge(merge: SharedNumberMerge) {
        val pair = minOf(merge.ownerShipmentId, merge.mergedShipmentId) to
            maxOf(merge.ownerShipmentId, merge.mergedShipmentId)
        merges.putIfAbsent(pair, merge)
    }

    for ((number, tracked) in liveByNumber) {
        if (tracked.map { it.shipmentId }.distinct().size < 2) continue
        val owner = tracked.first()
        for (other in tracked) {
            if (other.shipmentId == owner.shipmentId) continue
            addMerge(
                SharedNumberMerge(
                    ownerLegId = owner.legId,
                    ownerShipmentId = owner.shipmentId,
                    mergedLegId = other.legId,
                    mergedShipmentId = other.shipmentId,
                    number = number,
                ),
            )
        }
    }

    for (leg in ordered) {
        for (alias in leg.aliases()) {
            val number = FingerprintUtil.normalize(alias)
            // Only a number another parcel tracks today makes an alias meaningful.
            for (owner in liveByNumber[number].orEmpty()) {
                if (owner.shipmentId == leg.shipmentId) continue
                addMerge(
                    SharedNumberMerge(
                        ownerLegId = owner.legId,
                        ownerShipmentId = owner.shipmentId,
                        mergedLegId = leg.legId,
                        mergedShipmentId = leg.shipmentId,
                        number = number,
                    ),
                )
            }
        }
    }

    return merges.values.toList()
}
