package com.packatrack.feature.common

import com.packatrack.core.db.TrackingLegEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for the overall-status heuristic: the parcel's single status pill must follow
 * the leg with the freshest scans, not blindly prefer a DELIVERED leg.
 */
class OverallStatusCodeTest {

    private fun leg(
        id: Long,
        statusCode: String?,
        lastSyncAt: Long? = null,
    ) = TrackingLegEntity(
        id = id,
        shipmentId = 1L,
        trackingNumber = "LEG$id",
        carrierId = "cainiao",
        lastSyncAt = lastSyncAt,
        lastStatusCode = statusCode,
    )

    @Test
    fun `delivered leg does not win while another leg is fresher beyond the window`() {
        // Regression: parcel showed DELIVERED because the last-mile leg outranked the still
        // moving Cainiao leg under the old STATUS_ORDER-first heuristic. Here the delivered
        // beat is ~25h older than the newest scan, so it is stale and must be ignored.
        val legs = listOf(
            leg(1, "IN_TRANSIT", lastSyncAt = 1_000L),
            leg(2, "DELIVERED", lastSyncAt = 1_000L),
        )
        val events = mapOf(1L to 90_000_000L, 2L to 1_000_000L)

        assertEquals("IN_TRANSIT", overallStatusCode(legs, events))
    }

    @Test
    fun `delivered leg wins when its scan is genuinely the newest`() {
        val legs = listOf(
            leg(1, "IN_TRANSIT", lastSyncAt = 1_000L),
            leg(2, "DELIVERED", lastSyncAt = 1_000L),
        )
        val events = mapOf(1L to 5_000L, 2L to 9_000L)

        assertEquals("DELIVERED", overallStatusCode(legs, events))
    }

    @Test
    fun `stale delivered leg is ignored beyond the recency window`() {
        val legs = listOf(
            leg(1, "IN_TRANSIT", lastSyncAt = 1_000L),
            leg(2, "DELIVERED", lastSyncAt = 1_000L),
        )
        // Last-mile leg delivered 2 days ago; the Cainiao leg scanned an hour ago.
        val day = 24L * 60 * 60 * 1000
        val now = 1_800_000_000_000L
        val events = mapOf(1L to now, 2L to now - 2 * day)

        assertEquals("IN_TRANSIT", overallStatusCode(legs, events))
    }

    @Test
    fun `out for delivery wins when the delivered beat is stale`() {
        val legs = listOf(
            leg(1, "DELIVERED", lastSyncAt = 1_000L),
            leg(2, "OUT_FOR_DELIVERY", lastSyncAt = 1_000L),
        )
        // The delivered beat is 2 days older than the out-for-delivery scan.
        val day = 24L * 60 * 60 * 1000
        val now = 1_800_000_000_000L
        val events = mapOf(1L to now - 2 * day, 2L to now)

        assertEquals("OUT_FOR_DELIVERY", overallStatusCode(legs, events))
    }

    @Test
    fun `fresh delivered outranks near-simultaneous in transit scan`() {
        // The Cainiao origin feed keeps scanning (or reported the handoff late) after the
        // last-mile leg genuinely delivered; within the recency window DELIVERED must win.
        val legs = listOf(
            leg(1, "IN_TRANSIT", lastSyncAt = 1_000L),
            leg(2, "DELIVERED", lastSyncAt = 1_000L),
        )
        val events = mapOf(1L to 9_000L, 2L to 9_500L)

        assertEquals("DELIVERED", overallStatusCode(legs, events))
    }

    @Test
    fun `exception always wins regardless of recency`() {
        val legs = listOf(
            leg(1, "DELIVERED", lastSyncAt = 1_000L),
            leg(2, "EXCEPTION", lastSyncAt = 1L),
        )
        val events = mapOf(1L to 100_000L, 2L to 1L)

        assertEquals("EXCEPTION", overallStatusCode(legs, events))
    }

    @Test
    fun `exception_prefix_codes_also_win`() {
        val legs = listOf(leg(1, "EXCEPTION_HELD_BY_CUSTOMS"))

        assertEquals("EXCEPTION", overallStatusCode(legs, null))
    }

    @Test
    fun `without event times the legacy rank still decides`() {
        val legs = listOf(
            leg(1, "IN_TRANSIT"),
            leg(2, "DELIVERED"),
        )

        // No event map and no lastSyncAt -> fallback to STATUS_RANK (DELIVERED first).
        assertEquals("DELIVERED", overallStatusCode(legs, null))
    }

    @Test
    fun `falls back to lastSyncAt when the event map omits a leg`() {
        // Only lastSyncAt is available here (e.g. no timestamped scans yet); a >24h gap
        // between the two legs' syncs still lets the fresher IN_TRANSIT leg win.
        val legs = listOf(
            leg(1, "IN_TRANSIT", lastSyncAt = 90_000_000L),
            leg(2, "DELIVERED", lastSyncAt = 1_000_000L),
        )

        assertEquals("IN_TRANSIT", overallStatusCode(legs, emptyMap()))
    }

    @Test
    fun `no status codes at all yields null`() {
        val legs = listOf(leg(1, null), leg(2, ""))

        assertNull(overallStatusCode(legs, mapOf(1L to 1L, 2L to 2L)))
    }

    @Test
    fun `empty legs yield null`() {
        assertNull(overallStatusCode(emptyList(), emptyMap()))
    }
}
