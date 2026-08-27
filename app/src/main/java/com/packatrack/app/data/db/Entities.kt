package com.packatrack.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shipments",
    indices = [Index(value = ["trackingNumber"], unique = true)],
)
data class ShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Current tracking number; previous numbers move into [aliasNumbers]. */
    val trackingNumber: String,
    /** Carrier id (see core Carrier). */
    val carrierId: String,
    val title: String? = null,
    /** AliExpress order page link, optional. */
    val orderUrl: String? = null,
    val weightGrams: Double? = null,
    /** Comma-separated numbers this parcel was previously tracked under. */
    val aliasNumbers: String = "",
    /** How many times this shipment has been refreshed. */
    val pollCount: Int = 0,
    val lastSyncAt: Long? = null,
    /** Normalized status code of the most recent event. */
    val lastStatusCode: String? = null,
    /** True when this parcel was folded into a combined shipment. */
    val archived: Boolean = false,
    val createdAt: Long = 0L,
)

@Entity(
    tableName = "events",
    indices = [
        Index("shipmentId"),
        Index(value = ["shipmentId", "timeMs", "description"], unique = true),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
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
    /** One of: RENUMBERED | COMBINED | WEIGHT | PROGRESS */
    val type: String,
    val message: String,
    val createdAt: Long = 0L,
)
