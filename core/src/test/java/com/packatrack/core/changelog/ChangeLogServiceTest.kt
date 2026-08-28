package com.packatrack.core.changelog

import com.packatrack.core.model.ParcelChange
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeLogServiceTest {

    @Test fun sameNumberWithNewLastEventProducesProgress() {
        val prev = Snapshot("CNX111", null,
            listOf(TrackingEvent("CNX111", 1L, "Arrived Sydney")))
        val cur = Snapshot("CNX111", null,
            listOf(TrackingEvent("CNX111", 2L, "Out for delivery")))

        val changes = ChangeLogService.detect(mapOf("CNX111" to prev), cur)
        assertEquals(1, changes.size)
        assertTrue(changes[0] is ParcelChange.Progress)
    }

    @Test fun renumberedViaSuffixFingerprint() {
        val prev = Snapshot("600087654321", null, emptyList())
        val cur = Snapshot("AU600087654321", null, emptyList())
        val changes = ChangeLogService.detect(mapOf("600087654321" to prev), cur)
        assertEquals(1, changes.size)
        assertEquals(ParcelChange.Renumbered::class.java, changes[0].javaClass)
    }

    @Test fun renumberedViaWeightPlusDimensions() {
        val prev = Snapshot("IML00AABBCCDD",
            com.packatrack.core.model.Dimensions(30.0, 20.0, 10.0), emptyList())
        val cur = Snapshot("NZ99XYZ",
            com.packatrack.core.model.Dimensions(31.0, 20.0, 10.0), emptyList())
        val changes = ChangeLogService.detect(mapOf(prev.trackingNumber to prev), cur)
        assertTrue(changes[0] is ParcelChange.Renumbered)
    }

    @Test fun combinedDetectedByKeyword() {
        val p1 = Snapshot("CNPART00001", null,
            listOf(TrackingEvent("CNPART00001", 1L, "Parcel consolidated in warehouse")))
        val p2 = Snapshot("CNPART00002", null, emptyList())
        val combined = Snapshot("CNCOMBO9XZ", null, emptyList())

        val result = ChangeLogService.detectCombination(
            previousByNumber = mapOf(p1.trackingNumber to p1, p2.trackingNumber to p2),
            combinedSnapshot = combined,
        )
        assertEquals(listOf("CNPART00001", "CNPART00002"), result!!.mergedFrom.toList())
    }

    @Test fun noFalseCombinationWhenNothingChanged() {
        val p = Snapshot("SNGL001", null, emptyList())
        assertEquals(null, ChangeLogService.detectCombination(mapOf(p.trackingNumber to p),
            Snapshot("SNGL001", null, emptyList())))
    }

    @Test fun humanReadableLines() {
        assertTrue(
            ChangeLogService.humanReadable(
                ParcelChange.Renumbered("old12345678", "new12345678"),
            ).contains("old12345678"),
        )
    }
}
