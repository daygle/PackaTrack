package com.packatrack.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.packatrack.app.data.db.AppDatabase
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.TrackingLegEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the SQLCipher + Android Keystore path that only runs on a device: the native library
 * loads, the encrypted database opens, data round-trips, and the file on disk is not plaintext.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun encryptedDatabaseOpensRoundTripsAndIsNotPlaintext() = runBlocking {
        val db = AppDatabase.get(context)
        db.clearAllTables()

        val shipmentId = db.shipmentDao().insert(ShipmentEntity(title = "Encrypted parcel"))
        db.legDao().insert(
            TrackingLegEntity(shipmentId = shipmentId, trackingNumber = "ENCTEST123", carrierId = "cainiao"),
        )

        assertEquals("Encrypted parcel", db.shipmentDao().byId(shipmentId)?.title)
        assertEquals(1, db.legDao().all().size)

        // The SQLCipher database header is encrypted, so it must NOT be a plaintext SQLite file.
        val header = ByteArray(16)
        File(context.getDatabasePath("packatrack.db").path).inputStream().use { it.read(header) }
        assertFalse(
            "Database file should be encrypted, not plaintext SQLite",
            String(header, Charsets.US_ASCII).startsWith("SQLite format 3"),
        )
    }
}
