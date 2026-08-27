package com.packatrack.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {
    @Query("SELECT * FROM shipments WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ShipmentEntity>>

    @Query("SELECT * FROM shipments ORDER BY createdAt DESC")
    suspend fun all(): List<ShipmentEntity>

    @Query("SELECT * FROM shipments WHERE trackingNumber = :number LIMIT 1")
    suspend fun findByTrackingNumber(number: String): ShipmentEntity?

    @Query("SELECT * FROM shipments WHERE trackingNumber = :number LIMIT 1")
    fun observeByTrackingNumber(number: String): Flow<ShipmentEntity?>

    @Query("SELECT * FROM shipments WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ShipmentEntity?>

    @Insert
    suspend fun insert(shipment: ShipmentEntity): Long

    @Update
    suspend fun update(shipment: ShipmentEntity)

    @Query("DELETE FROM shipments WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface EventDao {
    /** IGNORE keeps old rows when (shipmentId,timeMs,description) duplicates arrive. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE shipmentId = :shipmentId")
    suspend fun deleteForShipment(shipmentId: Long)

    @Query("SELECT * FROM events WHERE shipmentId = :shipmentId")
    suspend fun eventsFor(shipmentId: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE shipmentId = :shipmentId ORDER BY timeMs IS NULL, timeMs DESC, id DESC")
    fun observeFor(shipmentId: Long): Flow<List<EventEntity>>
}

@Dao
interface ChangeDao {
    @Insert
    suspend fun insert(change: ChangeEntity): Long

    @Query("SELECT * FROM changes ORDER BY createdAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<ChangeEntity>>

    @Query("SELECT * FROM changes WHERE shipmentId = :shipmentId ORDER BY createdAt DESC")
    fun observeFor(shipmentId: Long): Flow<List<ChangeEntity>>

    @Query("DELETE FROM changes WHERE shipmentId = :shipmentId")
    suspend fun deleteForShipment(shipmentId: Long)

    @Query("SELECT COUNT(*) FROM changes WHERE shipmentId = :shipmentId AND type = :type")
    suspend fun countByType(shipmentId: Long, type: String): Int
}
