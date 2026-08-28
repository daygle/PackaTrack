package com.packatrack.app.data

import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.TrackingLegEntity

/**
 * Pure decision logic for merging an imported backup into an existing database.
 *
 * Kept free of Room/Android so the tricky parcel-matching rules can be unit-tested directly.
 */
object BackupMerger {
    /**
     * Decides, for each incoming shipment, which existing shipment it should merge into.
     *
     * A shipment merges into an existing one when **any of its own legs** carries a tracking
     * number that is already in the database; otherwise it is new (maps to `null`). Each
     * incoming shipment is evaluated independently — one shipment matching an existing parcel
     * must not change how the others are classified.
     *
     * @param existingShipmentIdByTrackingNumber tracking number -> id of the existing shipment
     *        that already owns a leg with that number.
     * @return incoming shipment id -> existing shipment id to merge into, or `null` when new.
     */
    fun resolveShipmentTargets(
        shipments: List<ShipmentEntity>,
        legs: List<TrackingLegEntity>,
        existingShipmentIdByTrackingNumber: Map<String, Long>,
    ): Map<Long, Long?> {
        val legsByShipment = legs.groupBy { it.shipmentId }
        return shipments.associate { shipment ->
            val target = legsByShipment[shipment.id].orEmpty()
                .firstNotNullOfOrNull { leg -> existingShipmentIdByTrackingNumber[leg.trackingNumber] }
            shipment.id to target
        }
    }
}
