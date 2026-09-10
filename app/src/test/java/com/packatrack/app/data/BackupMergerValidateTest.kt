package com.packatrack.app.data

import com.packatrack.core.db.ChangeEntity
import com.packatrack.core.db.EventEntity
import com.packatrack.core.db.OrderItemEntity
import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.TrackingLegEntity
import com.packatrack.data.BackupMerger
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM tests for the reference-integrity validation applied to every parsed backup before any
 * database write. Duplicate identifiers or (trackingNumber, carrierId) pairs must be rejected
 * up front instead of failing mid-restore with a raw SQLiteConstraintException.
 */
class BackupMergerValidateTest {

    private fun shipment(id: Long) = ShipmentEntity(id = id, createdAt = id)

    private fun leg(id: Long, shipmentId: Long, number: String, carrier: String = "cainiao") =
        TrackingLegEntity(id = id, shipmentId = shipmentId, trackingNumber = number, carrierId = carrier)

    private fun order(id: Long, shipmentId: Long) = OrderItemEntity(id = id, shipmentId = shipmentId, name = "Item $id")

    private fun event(id: Long, shipmentId: Long, legId: Long) = EventEntity(
        id = id,
        shipmentId = shipmentId,
        legId = legId,
        trackingNumber = "NUM$legId",
        timeMs = id * 1_000L,
        description = "Scan $id",
    )

    private fun change(id: Long, shipmentId: Long) = ChangeEntity(
        id = id,
        shipmentId = shipmentId,
        type = "PROGRESS",
        message = "Change $id",
    )

    private fun validate(
        shipments: List<ShipmentEntity>,
        legs: List<TrackingLegEntity>,
        orders: List<OrderItemEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
        changes: List<ChangeEntity> = emptyList(),
    ) = BackupMerger.validate(shipments, legs, orders, events, changes)

    @Test
    fun `valid backup passes`() {
        validate(
            shipments = listOf(shipment(1), shipment(2)),
            legs = listOf(leg(10, 1, "AA"), leg(11, 2, "BB")),
            orders = listOf(order(20, 1)),
            events = listOf(event(30, 1, 10)),
            changes = listOf(change(40, 2)),
        )
    }

    @Test
    fun `duplicate shipment identifiers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(shipments = listOf(shipment(1), shipment(1)), legs = emptyList())
        }
    }

    @Test
    fun `duplicate leg identifiers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                shipments = listOf(shipment(1)),
                legs = listOf(leg(10, 1, "AA"), leg(10, 1, "BB")),
            )
        }
    }

    @Test
    fun `duplicate tracking number and carrier pair is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                shipments = listOf(shipment(1), shipment(2)),
                legs = listOf(leg(10, 1, "AA"), leg(11, 2, "AA")),
            )
        }
    }

    @Test
    fun `same number under different carriers is allowed`() {
        // Multi-carrier legs (e.g. Cainiao + UBI reading the same physical parcel) are
        // legitimate: the unique index is on (trackingNumber, carrierId), not the number alone.
        validate(
            shipments = listOf(shipment(1)),
            legs = listOf(
                leg(10, 1, "AA", carrier = "cainiao"),
                leg(11, 1, "AA", carrier = "ubi_smart_parcel"),
            ),
        )
    }

    @Test
    fun `order referencing missing shipment is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(shipments = listOf(shipment(1)), legs = emptyList(), orders = listOf(order(20, 99)))
        }
    }

    @Test
    fun `event referencing missing leg is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(shipments = listOf(shipment(1)), legs = listOf(leg(10, 1, "AA")), events = listOf(event(30, 1, 99)))
        }
    }

    @Test
    fun `event referencing missing shipment is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(shipments = listOf(shipment(1)), legs = listOf(leg(10, 1, "AA")), events = listOf(event(30, 99, 10)))
        }
    }

    @Test
    fun `change referencing missing shipment is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(shipments = listOf(shipment(1)), legs = emptyList(), changes = listOf(change(40, 99)))
        }
    }
}
