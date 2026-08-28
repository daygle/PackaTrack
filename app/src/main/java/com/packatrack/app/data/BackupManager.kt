package com.packatrack.app.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val backupMutex = Mutex()

    suspend fun export(uri: Uri, passphrase: CharArray) = backupMutex.withLock {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters" }
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("shipments", JSONArray(db.shipmentDao().all().map(ShipmentEntity::toJson)))
            .put("legs", JSONArray(db.legDao().all().map(TrackingLegEntity::toJson)))
            .put("orders", JSONArray(db.orderDao().all().map(OrderItemEntity::toJson)))
            .put("events", JSONArray(db.eventDao().all().map(EventEntity::toJson)))
            .put("changes", JSONArray(db.changeDao().all().map(ChangeEntity::toJson)))
        val encrypted = encrypt(payload.toString().toByteArray(Charsets.UTF_8), passphrase)
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(MAGIC)
            output.write(byteArrayOf(VERSION.toByte()))
            output.write(encrypted.salt)
            output.write(encrypted.iv)
            output.write(encrypted.ciphertext)
        } ?: throw IOException("Unable to open backup destination")
    }

    suspend fun import(uri: Uri, passphrase: CharArray, replaceExisting: Boolean = false) = backupMutex.withLock {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters" }
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Unable to open backup file")
        val headerSize = MAGIC.size + 1 + SALT_LENGTH + IV_LENGTH
        require(bytes.size > headerSize + TAG_LENGTH) { "Backup file is incomplete" }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a PackaTrack backup" }
        require(bytes[MAGIC.size].toInt() == VERSION) { "Unsupported backup version" }
        val headerStart = MAGIC.size + 1
        val salt = bytes.copyOfRange(headerStart, headerStart + SALT_LENGTH)
        val ivStart = headerStart + SALT_LENGTH
        val iv = bytes.copyOfRange(ivStart, ivStart + IV_LENGTH)
        val plaintext = decrypt(iv, salt, passphrase, bytes.copyOfRange(ivStart + IV_LENGTH, bytes.size))
        val root = JSONObject(String(plaintext, Charsets.UTF_8))
        require(root.optString("format") == FORMAT && root.optInt("version") == VERSION) { "Unsupported backup format" }

        val shipments = root.requiredArray("shipments").map { it.toShipment() }
        val legs = root.requiredArray("legs").map { it.toLeg() }
        val orders = root.requiredArray("orders").map { it.toOrder() }
        val events = root.requiredArray("events").map { it.toEvent() }
        val changes = root.requiredArray("changes").map { it.toChange() }
        validate(shipments, legs, orders, events, changes)

        db.withTransaction {
            if (replaceExisting) db.clearAllTables()
            val existingShipmentByKey = db.shipmentDao().all()
                .flatMap { shipment -> db.legDao().legsForShipment(shipment.id).map { it.trackingNumber to shipment.id } }
                .toMap()
            val newShipments = if (replaceExisting) shipments else shipments.filter { shipment ->
                legs.none { leg -> existingShipmentByKey[leg.trackingNumber] != null }
            }
            val shipmentIds = db.shipmentDao().insertAll(newShipments.map { it.copy(id = 0) })
            val shipmentMap = shipments.map { source ->
                source.id to (existingShipmentByKey[legs.firstOrNull { it.shipmentId == source.id }?.trackingNumber]
                    ?: shipmentIds[newShipments.indexOfFirst { it.id == source.id }])
            }.toMap()
            val newLegs = legs.filter { leg -> existingShipmentByKey[leg.trackingNumber] == null }
            val legIds = db.legDao().insertAll(newLegs.map { it.copy(id = 0, shipmentId = shipmentMap.requireValue(it.shipmentId)) })
            val legMap = legs.filter { it in newLegs }.map { it.id }.zip(legIds).toMap()
            db.orderDao().insertAll(orders.map { it.copy(id = 0, shipmentId = shipmentMap.requireValue(it.shipmentId)) }.filterNot { candidate -> db.orderDao().findDuplicate(candidate.shipmentId, candidate.name, candidate.orderUrl) != null })
            db.eventDao().insertAll(events.mapNotNull { source ->
                val mappedLeg = legMap[source.legId] ?: return@mapNotNull null
                source.copy(id = 0, shipmentId = shipmentMap.requireValue(source.shipmentId), legId = mappedLeg)
            }.filterNot { candidate -> db.eventDao().findDuplicate(candidate.legId, candidate.timeMs, candidate.description) != null })
            db.changeDao().insertAll(changes.map { it.copy(id = 0, shipmentId = shipmentMap.requireValue(it.shipmentId)) }.filterNot { candidate -> db.changeDao().findDuplicate(candidate.shipmentId, candidate.type, candidate.message) != null })
        }
    }

    private data class Encrypted(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

    private fun encrypt(data: ByteArray, passphrase: CharArray): Encrypted {
        val salt = ByteArray(SALT_LENGTH).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return Encrypted(salt, iv, cipher.doFinal(data))
    }

    private fun decrypt(iv: ByteArray, salt: ByteArray, passphrase: CharArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(data)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun validate(shipments: List<ShipmentEntity>, legs: List<TrackingLegEntity>, orders: List<OrderItemEntity>, events: List<EventEntity>, changes: List<ChangeEntity>) {
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

    private fun <T> Map<Long, T>.requireValue(key: Long): T = get(key) ?: error("Invalid backup reference")

    companion object {
        private const val FORMAT = "packatrack-backup"
        private const val VERSION = 2
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2 = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val TAG_LENGTH = 16
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val MIN_PASSPHRASE_LENGTH = 12
        private val MAGIC = "PKTB".toByteArray(Charsets.US_ASCII)
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
