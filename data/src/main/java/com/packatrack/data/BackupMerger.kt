package com.packatrack.data

import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.TrackingLegEntity

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
     * incoming shipment is evaluated independently - one shipment matching an existing parcel
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

    /**
     * Validates reference integrity of a parsed backup. Throws [IllegalArgumentException]
     * for malformed backups so callers can reject them before any database write.
     *
     * Checks:
     *  - every shipment id is unique (Room would fail the insert later anyway),
     *  - every leg/order/event/change references a shipment that exists in the backup,
     *  - events reference a leg that exists in the backup,
     *  - no two legs share the same (trackingNumber, carrierId) pair - the `tracking_legs`
     *    table has a unique index on exactly that pair, so a violating backup would only
     *    fail later with a raw SQLiteConstraintException mid-restore. Same number under
     *    different carriers is legitimate (multi-carrier legs), so number-only checks
     *    would false-positive.
     */
    fun validate(
        shipments: List<ShipmentEntity>,
        legs: List<TrackingLegEntity>,
        orders: List<com.packatrack.core.db.OrderItemEntity>,
        events: List<com.packatrack.core.db.EventEntity>,
        changes: List<com.packatrack.core.db.ChangeEntity>,
    ) {
        val shipmentIds = shipments.map { it.id }.toSet()
        val legIds = legs.map { it.id }.toSet()
        require(shipments.size == shipmentIds.size) { "Duplicate backup shipment identifiers" }
        require(legs.size == legIds.size) { "Duplicate backup leg identifiers" }
        require(legs.all { it.shipmentId in shipmentIds }) { "Invalid leg reference" }
        require(orders.all { it.shipmentId in shipmentIds }) { "Invalid order reference" }
        require(events.all { it.shipmentId in shipmentIds && it.legId in legIds }) { "Invalid event reference" }
        require(changes.all { it.shipmentId in shipmentIds }) { "Invalid change reference" }

        val uniqueKey = legs.map { it.trackingNumber to it.carrierId }
        require(uniqueKey.size == uniqueKey.distinct().size) {
            "Duplicate tracking number/carrier pair in backup"
        }
    }
}
