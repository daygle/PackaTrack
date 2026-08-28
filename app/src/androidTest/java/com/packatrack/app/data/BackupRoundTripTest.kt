package com.packatrack.app.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.packatrack.app.data.db.AppDatabase
import com.packatrack.app.data.db.EventEntity
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.TrackingLegEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end backup export -> import against the real (encrypted) database, including the merge
 * path that the [BackupMerger] fix targets.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase get() = "backup-passphrase-123".toCharArray()

    @Test
    fun exportThenReplaceImportRestoresData() = runBlocking {
        val db = AppDatabase.get(context)
        db.clearAllTables()
        val shipmentId = db.shipmentDao().insert(ShipmentEntity(title = "Roundtrip parcel"))
        val legId = db.legDao().insert(
            TrackingLegEntity(shipmentId = shipmentId, trackingNumber = "RT12345678", carrierId = "cainiao"),
        )
        db.eventDao().insertAll(
            listOf(
                EventEntity(
                    shipmentId = shipmentId,
                    legId = legId,
                    trackingNumber = "RT12345678",
                    timeMs = 1_000L,
                    description = "In transit",
                ),
            ),
        )

        val file = File(context.cacheDir, "roundtrip_backup.pkt")
        val uri = Uri.fromFile(file)
        val manager = BackupManager(context)
        try {
            manager.export(uri, passphrase)

            db.clearAllTables()
            assertEquals(0, db.shipmentDao().all().size)

            manager.import(uri, passphrase, replaceExisting = true)
            assertEquals(1, db.shipmentDao().all().size)
            assertEquals("Roundtrip parcel", db.shipmentDao().all().first().title)
            assertEquals(1, db.legDao().all().size)
            assertEquals(1, db.eventDao().all().size)

            // Importing the same file again as a merge must dedupe, not duplicate.
            manager.import(uri, passphrase, replaceExisting = false)
            assertEquals(1, db.shipmentDao().all().size)
            assertEquals(1, db.legDao().all().size)
            assertEquals(1, db.eventDao().all().size)
        } finally {
            file.delete()
        }
    }
}
