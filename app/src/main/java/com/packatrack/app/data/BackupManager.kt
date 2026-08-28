package com.packatrack.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.packatrack.app.data.db.AppDatabase
import com.packatrack.app.data.db.ChangeEntity
import com.packatrack.app.data.db.EventEntity
import com.packatrack.app.data.db.OrderItemEntity
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.TrackingLegEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class BackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val backupMutex = Mutex()

    suspend fun export(uri: Uri, passphrase: CharArray) = backupMutex.withLock {
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("version", BackupCodec.VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("shipments", JSONArray(db.shipmentDao().all().map(ShipmentEntity::toJson)))
            .put("legs", JSONArray(db.legDao().all().map(TrackingLegEntity::toJson)))
            .put("orders", JSONArray(db.orderDao().all().map(OrderItemEntity::toJson)))
            .put("events", JSONArray(db.eventDao().all().map(EventEntity::toJson)))
            .put("changes", JSONArray(db.changeDao().all().map(ChangeEntity::toJson)))
        val fileBytes = BackupCodec.seal(payload.toString().toByteArray(Charsets.UTF_8), passphrase)
        appContext.contentResolver.openOutputStream(uri)?.use { output -> output.write(fileBytes) }
            ?: throw IOException("Unable to open backup destination")
    }

    suspend fun import(uri: Uri, passphrase: CharArray, replaceExisting: Boolean = false) = backupMutex.withLock {
        val fileBytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Unable to open backup file")
        val root = JSONObject(String(BackupCodec.open(fileBytes, passphrase), Charsets.UTF_8))
        require(root.optString("format") == FORMAT && root.optInt("version") == BackupCodec.VERSION) {
            "Unsupported backup format"
        }

        val shipments = root.requiredArray("shipments").map { it.toShipment() }
        val legs = root.requiredArray("legs").map { it.toLeg() }
        val orders = root.requiredArray("orders").map { it.toOrder() }
        val events = root.requiredArray("events").map { it.toEvent() }
        val changes = root.requiredArray("changes").map { it.toChange() }
        validate(shipments, legs, orders, events, changes)

        db.withTransaction {
            if (replaceExisting) db.clearAllTables()

            // tracking number -> existing shipment already carrying a leg with that number.
            val existingShipmentIdByTrackingNumber = db.shipmentDao().all().flatMap { shipment ->
                db.legDao().legsForShipment(shipment.id).map { it.trackingNumber to shipment.id }
            }.toMap()

            val targets: Map<Long, Long?> = if (replaceExisting) {
                emptyMap()
            } else {
                BackupMerger.resolveShipmentTargets(shipments, legs, existingShipmentIdByTrackingNumber)
            }

            // Insert shipments that don't merge into an existing one, then build a complete
            // source-id -> target-id map (existing target, or the freshly inserted row).
            val newShipments = shipments.filter { targets[it.id] == null }
            val insertedIds = db.shipmentDao().insertAll(newShipments.map { it.copy(id = 0) })
            val insertedIdBySource = newShipments
                .mapIndexed { index, shipment -> shipment.id to insertedIds[index] }
                .toMap()
            val shipmentMap = shipments.associate { shipment ->
                shipment.id to (targets[shipment.id] ?: insertedIdBySource.getValue(shipment.id))
            }

            // A leg whose tracking number already exists is a duplicate: skip it (its events stay
            // with the leg already in the database). Only newly inserted legs receive events.
            val existingTrackingNumbers = existingShipmentIdByTrackingNumber.keys
            val newLegs = legs.filter { it.trackingNumber !in existingTrackingNumbers }
            val newLegIds = db.legDao().insertAll(
                newLegs.map { it.copy(id = 0, shipmentId = shipmentMap.getValue(it.shipmentId)) },
            )
            val legMap = newLegs.mapIndexed { index, leg -> leg.id to newLegIds[index] }.toMap()

            db.orderDao().insertAll(
                orders.map { it.copy(id = 0, shipmentId = shipmentMap.getValue(it.shipmentId)) }
                    .filterNot { candidate ->
                        db.orderDao().findDuplicate(candidate.shipmentId, candidate.name, candidate.orderUrl) != null
                    },
            )
            db.eventDao().insertAll(
                events.mapNotNull { source ->
                    val mappedLeg = legMap[source.legId] ?: return@mapNotNull null
                    source.copy(id = 0, shipmentId = shipmentMap.getValue(source.shipmentId), legId = mappedLeg)
                }.filterNot { candidate ->
                    db.eventDao().findDuplicate(candidate.legId, candidate.timeMs, candidate.description) != null
                },
            )
            db.changeDao().insertAll(
                changes.map { it.copy(id = 0, shipmentId = shipmentMap.getValue(it.shipmentId)) }
                    .filterNot { candidate ->
                        db.changeDao().findDuplicate(candidate.shipmentId, candidate.type, candidate.message) != null
                    },
            )
        }
    }

    private fun validate(
        shipments: List<ShipmentEntity>,
        legs: List<TrackingLegEntity>,
        orders: List<OrderItemEntity>,
        events: List<EventEntity>,
        changes: List<ChangeEntity>,
    ) {
        val shipmentIds = shipments.map { it.id }.toSet()
        val legIds = legs.map { it.id }.toSet()
        require(shipments.size == shipmentIds.size && legs.size == legIds.size) { "Duplicate backup identifiers" }
        require(legs.all { it.shipmentId in shipmentIds }) { "Invalid leg reference" }
        require(orders.all { it.shipmentId in shipmentIds }) { "Invalid order reference" }
        require(events.all { it.shipmentId in shipmentIds && it.legId in legIds }) { "Invalid event reference" }
        require(changes.all { it.shipmentId in shipmentIds }) { "Invalid change reference" }
    }

    private fun JSONObject.requiredArray(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: throw IllegalArgumentException("Missing backup section: $key")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    companion object {
        private const val FORMAT = "packatrack-backup"
    }
}

private fun ShipmentEntity.toJson() = JSONObject().put("id", id).put("title", title).put("orderUrl", orderUrl).put("archived", archived).put("createdAt", createdAt)
private fun TrackingLegEntity.toJson() = JSONObject().put("id", id).put("shipmentId", shipmentId).put("trackingNumber", trackingNumber).put("carrierId", carrierId).put("aliasNumbers", aliasNumbers).put("pollCount", pollCount).put("lastSyncAt", lastSyncAt).put("lastStatusCode", lastStatusCode).put("createdAt", createdAt)
private fun OrderItemEntity.toJson() = JSONObject().put("id", id).put("shipmentId", shipmentId).put("name", name).put("orderUrl", orderUrl).put("createdAt", createdAt)
private fun EventEntity.toJson() = JSONObject().put("id", id).put("shipmentId", shipmentId).put("legId", legId).put("trackingNumber", trackingNumber).put("timeMs", timeMs).put("description", description).put("location", location).put("statusCode", statusCode)
private fun ChangeEntity.toJson() = JSONObject().put("id", id).put("shipmentId", shipmentId).put("type", type).put("message", message).put("createdAt", createdAt)

private fun JSONObject.toShipment() = ShipmentEntity(getLong("id"), optString("title").takeIf { !isNull("title") }, optString("orderUrl").takeIf { !isNull("orderUrl") }, getBoolean("archived"), getLong("createdAt"))
private fun JSONObject.toLeg() = TrackingLegEntity(getLong("id"), getLong("shipmentId"), getString("trackingNumber"), getString("carrierId"), optString("aliasNumbers"), getInt("pollCount"), optLong("lastSyncAt").takeIf { !isNull("lastSyncAt") }, optString("lastStatusCode").takeIf { !isNull("lastStatusCode") }, getLong("createdAt"))
private fun JSONObject.toOrder() = OrderItemEntity(getLong("id"), getLong("shipmentId"), getString("name"), optString("orderUrl").takeIf { !isNull("orderUrl") }, getLong("createdAt"))
private fun JSONObject.toEvent() = EventEntity(getLong("id"), getLong("shipmentId"), getLong("legId"), getString("trackingNumber"), optLong("timeMs").takeIf { !isNull("timeMs") }, getString("description"), optString("location").takeIf { !isNull("location") }, optString("statusCode").takeIf { !isNull("statusCode") })
private fun JSONObject.toChange() = ChangeEntity(getLong("id"), getLong("shipmentId"), getString("type"), getString("message"), getLong("createdAt"))
