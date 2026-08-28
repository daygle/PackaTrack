package com.packatrack.app.data

import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.TrackingLegEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupMergerTest {

    private fun shipment(id: Long) = ShipmentEntity(id = id, createdAt = id)

    private fun leg(id: Long, shipmentId: Long, number: String) =
        TrackingLegEntity(id = id, shipmentId = shipmentId, trackingNumber = number, carrierId = "cainiao")

    @Test
    fun everyShipmentIsNewWhenDatabaseIsEmpty() {
        val shipments = listOf(shipment(1), shipment(2))
        val legs = listOf(leg(10, 1, "AA"), leg(11, 2, "BB"))

        val targets = BackupMerger.resolveShipmentTargets(shipments, legs, emptyMap())

        assertNull(targets.getValue(1))
        assertNull(targets.getValue(2))
    }

    @Test
    fun shipmentMergesIntoExistingWhenOneLegMatches() {
        val shipments = listOf(shipment(1))
        val legs = listOf(leg(10, 1, "AA"))

        val targets = BackupMerger.resolveShipmentTargets(shipments, legs, mapOf("AA" to 100L))

        assertEquals(100L, targets.getValue(1))
    }

    @Test
    fun shipmentWithOneExistingAndOneNewLegStillMerges() {
        // Regression: a shipment with a mix of existing and new legs must merge into the
        // existing parcel, not be re-inserted as a duplicate.
        val shipments = listOf(shipment(1))
        val legs = listOf(leg(10, 1, "AA"), leg(11, 1, "NEW"))

        val targets = BackupMerger.resolveShipmentTargets(shipments, legs, mapOf("AA" to 100L))

        assertEquals(100L, targets.getValue(1))
    }

    @Test
    fun shipmentsAreClassifiedIndependently() {
        // Regression for the original bug: one shipment matching an existing parcel must not
        // change how the others are classified.
        val shipments = listOf(shipment(1), shipment(2))
        val legs = listOf(leg(10, 1, "AA"), leg(11, 2, "CC"))

        val targets = BackupMerger.resolveShipmentTargets(shipments, legs, mapOf("AA" to 100L))

        assertEquals(100L, targets.getValue(1))
        assertNull(targets.getValue(2))
    }

    @Test
    fun shipmentWithNoLegsIsNew() {
        val targets = BackupMerger.resolveShipmentTargets(
            listOf(shipment(3)),
            legs = emptyList(),
            existingShipmentIdByTrackingNumber = mapOf("AA" to 100L),
        )

        assertNull(targets.getValue(3))
    }
}
