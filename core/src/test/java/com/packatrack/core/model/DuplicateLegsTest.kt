package com.packatrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateLegsTest {

    private fun leg(
        id: Long,
        scans: List<String>,
        carrier: String = Carrier.CAINIAO.id,
        number: String = "CNG$id",
    ) = LegScans(
        legId = id,
        carrierId = carrier,
        trackingNumber = number,
        order = id,
        scans = scans.toSet(),
    )

    @Test fun identicalScansFromOneCourierCollapse() {
        val duplicates = findDuplicateLegs(
            listOf(
                leg(1, listOf("a|Outbound", "b|Inbound"), number = "CNG001"),
                leg(2, listOf("b|Inbound", "a|Outbound"), number = "CNG002"),
            ),
        )
        assertEquals(1, duplicates.size)
        assertEquals(2L, duplicates.single().legId)
        assertEquals(1L, duplicates.single().survivorLegId)
        // The removed number is reported so it can be kept as an alias of the survivor.
        assertEquals("CNG002", duplicates.single().trackingNumber)
    }

    @Test fun earlierLegSurvivesRegardlessOfInputOrder() {
        val duplicates = findDuplicateLegs(
            listOf(
                leg(9, listOf("a|Outbound")),
                leg(4, listOf("a|Outbound")),
            ),
        )
        assertEquals(9L, duplicates.single().legId)
        assertEquals(4L, duplicates.single().survivorLegId)
    }

    @Test fun differentCouriersAreNeverDuplicates() {
        // A hand-off between two carriers reports the same journey twice, and both are wanted.
        val duplicates = findDuplicateLegs(
            listOf(
                leg(1, listOf("a|Outbound"), carrier = Carrier.CAINIAO.id),
                leg(2, listOf("a|Outbound"), carrier = Carrier.AUSTRALIA_POST.id),
            ),
        )
        assertTrue(duplicates.isEmpty())
    }

    @Test fun aLegThatIsAheadIsKept() {
        // Overlapping is not enough: the leg with more scans carries information the other lacks.
        val duplicates = findDuplicateLegs(
            listOf(
                leg(1, listOf("a|Outbound")),
                leg(2, listOf("a|Outbound", "b|Delivered")),
            ),
        )
        assertTrue(duplicates.isEmpty())
    }

    @Test fun emptyTimelinesProveNothing() {
        val duplicates = findDuplicateLegs(listOf(leg(1, emptyList()), leg(2, emptyList())))
        assertTrue(duplicates.isEmpty())
    }

    @Test fun threeCopiesLeaveOneAndReportTwo() {
        val duplicates = findDuplicateLegs(
            listOf(
                leg(1, listOf("a|Outbound")),
                leg(2, listOf("a|Outbound")),
                leg(3, listOf("a|Outbound")),
            ),
        )
        assertEquals(listOf(2L, 3L), duplicates.map { it.legId })
        assertEquals(listOf(1L, 1L), duplicates.map { it.survivorLegId })
    }

    @Test fun fingerprintsIgnoreSurroundingWhitespaceAndKeepMissingTimesApart() {
        assertEquals(scanFingerprint(100L, " Delivered "), scanFingerprint(100L, "Delivered"))
        // An undated scan must not collide with one at epoch 0.
        assertTrue(scanFingerprint(null, "Scan") != scanFingerprint(0L, "Scan"))
    }
}
