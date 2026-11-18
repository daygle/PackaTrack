package com.packatrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedNumbersTest {

    private fun leg(
        id: Long,
        shipmentId: Long,
        number: String,
        aliases: String = "",
    ) = NumberedLeg(
        legId = id,
        shipmentId = shipmentId,
        trackingNumber = number,
        aliasNumbers = aliases,
    )

    @Test fun sameCurrentNumberOnTwoParcelsMergesTheOlderLegsParcel() {
        val merges = findSharedNumberMerges(
            listOf(
                leg(2, shipmentId = 20, number = "AP00100209209377"),
                leg(1, shipmentId = 10, number = "AP00100209209377"),
            ),
        )
        assertEquals(1, merges.size)
        assertEquals(1L, merges.single().ownerLegId)
        assertEquals(10L, merges.single().ownerShipmentId)
        assertEquals(2L, merges.single().mergedLegId)
        assertEquals(20L, merges.single().mergedShipmentId)
        assertEquals("AP00100209209377", merges.single().number)
    }

    @Test fun aliasMatchingAnotherParcelsLiveNumberMerges() {
        // This parcel was renumbered and kept the hand-off number as an alias; the other parcel is
        // tracked under that number now, so both parcels are that one shipment.
        val merges = findSharedNumberMerges(
            listOf(
                leg(1, shipmentId = 10, number = "AP00100209209377", aliases = "3781062090701"),
                leg(2, shipmentId = 20, number = "3781062090701"),
            ),
        )
        assertEquals(1, merges.size)
        // The number is live on the second parcel, so that parcel is the one that survives.
        assertEquals(2L, merges.single().ownerLegId)
        assertEquals(20L, merges.single().ownerShipmentId)
        assertEquals(1L, merges.single().mergedLegId)
        assertEquals(10L, merges.single().mergedShipmentId)
        assertEquals("3781062090701", merges.single().number)
    }

    @Test fun aliasAndLiveNumberInTheSameParcelDoNotMerge() {
        // The Wiring Terminal case: the iMile hand-off number is both an alias of the Cainiao leg
        // and a leg of its own, on one parcel. Nothing to combine.
        val merges = findSharedNumberMerges(
            listOf(
                leg(1, shipmentId = 10, number = "AP00100209209377", aliases = "3781062090701"),
                leg(2, shipmentId = 10, number = "3781062090701"),
            ),
        )
        assertTrue(merges.isEmpty())
    }

    @Test fun aliasMatchingAnotherParcelsAliasDoesNotMerge() {
        // Neither parcel is tracked under the number any more. A withdrawn number (or one a
        // carrier re-issued to a different shipment) is not evidence they are the same parcel.
        val merges = findSharedNumberMerges(
            listOf(
                leg(1, shipmentId = 10, number = "AP00100193611525", aliases = "OLD123"),
                leg(2, shipmentId = 20, number = "AP00100216719577", aliases = "OLD123"),
            ),
        )
        assertTrue(merges.isEmpty())
    }

    @Test fun numbersCompareAfterNormalization() {
        val merges = findSharedNumberMerges(
            listOf(
                leg(1, shipmentId = 10, number = "ap001 0020-9209377"),
                leg(2, shipmentId = 20, number = "AP00100209209377"),
            ),
        )
        assertEquals(1, merges.size)
        assertEquals("AP00100209209377", merges.single().number)
    }

    @Test fun aliasesAreReadAfterNormalizationToo() {
        val merges = findSharedNumberMerges(
            listOf(
                leg(1, shipmentId = 10, number = "AP001", aliases = " 3781 0620-90701 , OLD9"),
                leg(2, shipmentId = 20, number = "3781062090701"),
            ),
        )
        assertEquals(1, merges.size)
        assertEquals("3781062090701", merges.single().number)
    }

    @Test fun oneParcelWithSeveralLegsIsLeftAlone() {
        val merges = findSharedNumberMerges(
            listOf(
                leg(1, shipmentId = 10, number = "AP001", aliases = "AP002"),
                leg(2, shipmentId = 10, number = "AP002"),
                leg(3, shipmentId = 10, number = "AP003"),
            ),
        )
        assertTrue(merges.isEmpty())
    }
}
