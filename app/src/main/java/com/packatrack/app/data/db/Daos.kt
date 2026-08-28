package com.packatrack.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {
    @Transaction
    @Query("SELECT * FROM shipments WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeActiveWithLegs(): Flow<List<ShipmentWithLegs>>

    @Transaction
    @Query("SELECT * FROM shipments WHERE id = :id LIMIT 1")
    fun observeWithLegs(id: Long): Flow<ShipmentWithLegs?>

    @Query("SELECT * FROM shipments ORDER BY createdAt DESC")
    suspend fun all(): List<ShipmentEntity>

    @Query("SELECT * FROM shipments WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ShipmentEntity?

    @Insert
    suspend fun insert(shipment: ShipmentEntity): Long

    @Insert
    suspend fun insertAll(shipments: List<ShipmentEntity>): List<Long>

    @Update
    suspend fun update(shipment: ShipmentEntity)

    @Query("DELETE FROM shipments WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface LegDao {
    @Query("SELECT * FROM tracking_legs WHERE shipmentId = :shipmentId ORDER BY createdAt ASC")
    suspend fun legsForShipment(shipmentId: Long): List<TrackingLegEntity>

    @Query("SELECT * FROM tracking_legs ORDER BY createdAt ASC")
    suspend fun all(): List<TrackingLegEntity>

    @Query("SELECT * FROM tracking_legs WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TrackingLegEntity?

    @Query("SELECT * FROM tracking_legs WHERE trackingNumber = :number LIMIT 1")
    suspend fun findByTrackingNumber(number: String): TrackingLegEntity?

    @Query("SELECT * FROM tracking_legs WHERE trackingNumber = :number AND carrierId = :carrierId LIMIT 1")
    suspend fun findByTrackingNumberAndCarrier(number: String, carrierId: String): TrackingLegEntity?

    @Insert
    suspend fun insert(leg: TrackingLegEntity): Long

    @Insert
    suspend fun insertAll(legs: List<TrackingLegEntity>): List<Long>

    @Update
    suspend fun update(leg: TrackingLegEntity)

    @Query("DELETE FROM tracking_legs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tracking_legs WHERE shipmentId = :shipmentId")
    suspend fun deleteForShipment(shipmentId: Long)

    @Query("UPDATE tracking_legs SET shipmentId = :newShipmentId WHERE shipmentId = :oldShipmentId")
    suspend fun reassignShipment(oldShipmentId: Long, newShipmentId: Long)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE shipmentId = :shipmentId ORDER BY createdAt ASC")
    suspend fun ordersForShipment(shipmentId: Long): List<OrderItemEntity>

    @Insert
    suspend fun insert(order: OrderItemEntity): Long

    @Insert
    suspend fun insertAll(orders: List<OrderItemEntity>): List<Long>

    @Query("SELECT * FROM orders WHERE shipmentId = :shipmentId AND name = :name AND ((orderUrl = :orderUrl) OR (orderUrl IS NULL AND :orderUrl IS NULL)) LIMIT 1")
    suspend fun findDuplicate(shipmentId: Long, name: String, orderUrl: String?): OrderItemEntity?

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteById(orderId: Long)

    @Query("DELETE FROM orders WHERE shipmentId = :shipmentId")
    suspend fun deleteForShipment(shipmentId: Long)

    @Query("UPDATE orders SET shipmentId = :newShipmentId WHERE shipmentId = :oldShipmentId")
    suspend fun reassignShipment(oldShipmentId: Long, newShipmentId: Long)
}

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE shipmentId = :shipmentId")
    suspend fun deleteForShipment(shipmentId: Long)

    @Query("DELETE FROM events WHERE legId = :legId")
    suspend fun deleteForLeg(legId: Long)

    @Query("SELECT * FROM events WHERE legId = :legId")
    suspend fun eventsForLeg(legId: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE legId = :legId AND ((timeMs = :timeMs) OR (timeMs IS NULL AND :timeMs IS NULL)) AND description = :description LIMIT 1")
    suspend fun findDuplicate(legId: Long, timeMs: Long?, description: String): EventEntity?


    @Query("SELECT * FROM events WHERE shipmentId = :shipmentId ORDER BY timeMs IS NULL, timeMs DESC, id DESC")
    fun observeForShipment(shipmentId: Long): Flow<List<EventEntity>>

    @Query("SELECT shipmentId AS shipmentId, MIN(timeMs) AS firstMs FROM events WHERE timeMs IS NOT NULL GROUP BY shipmentId")
    fun observeFirstEventTimes(): Flow<List<ShipmentFirstEvent>>

    @Query("UPDATE events SET shipmentId = :newShipmentId WHERE shipmentId = :oldShipmentId")
    suspend fun reassignShipment(oldShipmentId: Long, newShipmentId: Long)
}

@Dao
interface ChangeDao {
    @Insert
    suspend fun insert(change: ChangeEntity): Long

    @Insert
    suspend fun insertAll(changes: List<ChangeEntity>): List<Long>

    @Query("SELECT * FROM changes ORDER BY createdAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<ChangeEntity>>

    @Query("SELECT * FROM changes WHERE shipmentId = :shipmentId ORDER BY createdAt DESC")
    fun observeFor(shipmentId: Long): Flow<List<ChangeEntity>>

    @Query("DELETE FROM changes WHERE shipmentId = :shipmentId")
    suspend fun deleteForShipment(shipmentId: Long)

    @Query("SELECT * FROM changes WHERE shipmentId = :shipmentId AND type = :type AND message = :message LIMIT 1")
    suspend fun findDuplicate(shipmentId: Long, type: String, message: String): ChangeEntity?

    @Query("SELECT COUNT(*) FROM changes WHERE shipmentId = :shipmentId AND type = :type AND message = :message")
    suspend fun countByMessage(shipmentId: Long, type: String, message: String): Int

    @Query("UPDATE changes SET shipmentId = :newShipmentId WHERE shipmentId = :oldShipmentId")
    suspend fun reassignShipment(oldShipmentId: Long, newShipmentId: Long)
}
