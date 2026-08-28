package com.packatrack.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A logical parcel the user cares about.
 *
 * A parcel is a *container*: the tracking numbers and carriers that carry it live in
 * [TrackingLegEntity] rows, so one parcel can be followed across several couriers at once
 * (e.g. a Cainiao leg for the China portion and an Australia Post leg for the final mile).
 */
@Entity(tableName = "shipments")
data class ShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Optional custom parcel name; when null the name is derived from its orders/legs. */
    val title: String? = null,
    /** Legacy single order link; superseded by [OrderItemEntity]. Kept for older rows. */
    val orderUrl: String? = null,
    /** True when this parcel was folded into another (kept for history, hidden by default). */
    val archived: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * One courier tracking a [ShipmentEntity] under one tracking number.
 *
 * Users add and remove legs freely, and each leg is polled independently. When a carrier
 * re-issues a number mid-journey the leg adopts the new number and keeps the old one in
 * [aliasNumbers].
 */
@Entity(
    tableName = "tracking_legs",
    indices = [
        Index("shipmentId"),
        Index(value = ["trackingNumber", "carrierId"], unique = true),
    ],
)
data class TrackingLegEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    /** Current tracking number for this leg; previous numbers move into [aliasNumbers]. */
    val trackingNumber: String,
    /** Carrier id (see core Carrier). */
    val carrierId: String,
    /** Comma-separated numbers this leg was previously tracked under. */
    val aliasNumbers: String = "",
    /** How many times this leg has been refreshed. */
    val pollCount: Int = 0,
    val lastSyncAt: Long? = null,
    /** Normalized status code of this leg's most recent event. */
    val lastStatusCode: String? = null,
    val createdAt: Long = 0L,
)

/**
 * One AliExpress order carried inside a parcel.
 *
 * When Cainiao consolidates several orders under one tracking number, that shared-number
 * parcel holds several of these — so the physical shipment is one parcel, but its contents
 * (and their order links) are recorded individually.
 */
@Entity(tableName = "orders", indices = [Index("shipmentId")])
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    val name: String,
    /** AliExpress order page link for this order, optional. */
    val orderUrl: String? = null,
    val createdAt: Long = 0L,
)

/** A parcel together with the couriers tracking it and the orders it carries. */
data class ShipmentWithLegs(
    @Embedded val shipment: ShipmentEntity,
    @Relation(parentColumn = "id", entityColumn = "shipmentId")
    val legs: List<TrackingLegEntity>,
    @Relation(parentColumn = "id", entityColumn = "shipmentId")
    val orders: List<OrderItemEntity>,
)

@Entity(
    tableName = "events",
    indices = [
        Index("shipmentId"),
        Index("legId"),
        Index(value = ["legId", "timeMs", "description"], unique = true),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Parcel this event belongs to — used for the merged timeline. */
    val shipmentId: Long,
    /** Courier leg that produced this event. */
    val legId: Long,
    val trackingNumber: String,
    val timeMs: Long?,
    val description: String,
    val location: String? = null,
    val statusCode: String? = null,
)

/** Human-readable change entries: renumbered / combined / reweighed / progress. */
@Entity(tableName = "changes", indices = [Index("shipmentId")])
data class ChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    /** One of: RENUMBERED | COMBINED | WEIGHT | PROGRESS | COURIER */
    val type: String,
    val message: String,
    val createdAt: Long = 0L,
)
