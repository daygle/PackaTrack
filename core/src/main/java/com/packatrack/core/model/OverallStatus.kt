package com.packatrack.core.model

import com.packatrack.core.db.TrackingLegEntity

/**
 * How far a leg's cached status can be behind the newest scan of the parcel and still win the
 * "overall" vote. Legs whose `lastStatusCode` predates another leg's newest event by more than
 * this are treated as stale so a parcel never shows DELIVERED while a fresher leg is in transit.
 */
private const val STATUS_RECENCY_WINDOW_MS = 24L * 60 * 60 * 1000

/**
 * Overall status across a parcel's legs, shown as the parcel's single status pill.
 *
 * Rules, in order:
 *  1. Any leg currently in an EXCEPTION state -> EXCEPTION.
 *  2. Legs whose newest scan falls within [STATUS_RECENCY_WINDOW_MS] of the parcel's newest
 *     scan count as current; among them [STATUS_RANK] decides. Carriers report the same
 *     physical event at slightly different times (and origin feeds often go quiet after
 *     handoff), so a fresh DELIVERED beat still outranks a near-simultaneous IN_TRANSIT scan.
 *     A DELIVERED leg whose newest scan is more than a day older than another leg's newest
 *     scan is treated as stale and ignored - that is, a mis-delivered beat (e.g. the origin
 *     carrier's premature "delivered to destination stream") must not hide a parcel that is
 *     demonstrably still moving.
 *  3. Legs with no known recency (no timestamped events and no lastSyncAt) are ignored unless
 *     no leg has any recency, in which case the legacy [STATUS_RANK] order is preserved
 *     (ties break toward the original, oldest leg).
 *
 * @param newestEventMsByLeg optional legId -> newest event time for the parcel's legs. When
 *        omitted, leg recency falls back to [TrackingLegEntity.lastSyncAt] alone - which is
 *        nearly identical across legs polled together, so callers that can supply the map
 *        should.
 */
fun overallStatusCode(
    legs: List<TrackingLegEntity>,
    newestEventMsByLeg: Map<Long, Long?>? = null,
): String? {
    val codes = legs.associateBy({ it.id }) {
        it.lastStatusCode?.trim()?.uppercase()?.takeIf(String::isNotEmpty)
    }
    if (codes.isEmpty()) return null
    if (codes.values.any { it == "EXCEPTION" || it?.startsWith("EXCEPTION_") == true }) return "EXCEPTION"

    val legById = legs.associateBy(TrackingLegEntity::id)

    fun recency(id: Long): Long? {
        // Newest timestamped scan for the leg, from the DAO projection; a leg whose map entry
        // is absent or null (no timestamped scans) falls back to its last successful poll.
        newestEventMsByLeg?.get(id)?.let { return it }
        return (legById[id] ?: return null).lastSyncAt
    }

    val withRecency = codes.filterValues { it != null }
    if (withRecency.isEmpty()) return null
    val withTime = withRecency.filterKeys { recency(it) != null }
    if (withTime.isEmpty()) {
        // No timestamps anywhere: STATUS_RANK, then leg order (original leg first).
        return STATUS_RANK.firstOrNull { it in withRecency.values } ?: withRecency.values.first()
    }
    val newest = withTime.keys.maxOf { recency(it)!! }
    val freshest = withTime.keys.filter { newest - recency(it)!! <= STATUS_RECENCY_WINDOW_MS }
        .sortedBy { legById.keys.indexOf(it) }
    return STATUS_RANK.firstOrNull { code -> freshest.any { codes[it] == code } }
        ?: codes[freshest.first()]
}

private val STATUS_RANK = listOf(
    "DELIVERED",
    "OUT_FOR_DELIVERY",
    "PICKUP_AVAILABLE",
    "IN_TRANSIT",
    "LABEL_CREATED",
)
