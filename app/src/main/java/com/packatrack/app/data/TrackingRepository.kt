package com.packatrack.app.data

import android.content.Context
import com.packatrack.app.data.db.AppDatabase
import com.packatrack.app.data.db.ChangeEntity
import com.packatrack.app.data.db.EventEntity
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.fetch.DemoTrackingFetcher
import com.packatrack.app.data.fetch.HttpTrackingFetcher
import com.packatrack.core.changelog.ChangeLogService
import com.packatrack.core.model.Carrier
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.util.FingerprintUtil
import kotlinx.coroutines.flow.Flow

class TrackingRepository(
    context: Context,
    private val prefs: PrefsStore,
) {
    private val db = AppDatabase.get(context)
    private val shipments = db.shipmentDao()
    private val events = db.eventDao()
    private val changes = db.changeDao()

    val httpFetcher = HttpTrackingFetcher { prefs.ausPostApiKey }
    val demoFetcher = DemoTrackingFetcher()

    /* ---------- observers ---------- */

    fun observeActive(): Flow<List<ShipmentEntity>> = shipments.observeActive()
    fun observeRecentChanges(): Flow<List<ChangeEntity>> = changes.observeRecent()
    fun observeShipment(number: String): Flow<ShipmentEntity?> = shipments.observeByTrackingNumber(number)
    fun shipmentByIdFlow(id: Long): Flow<ShipmentEntity?> = shipments.observeById(id)
    fun observeEvents(id: Long): Flow<List<EventEntity>> = events.observeFor(id)
    fun observeChangesFor(id: Long): Flow<List<ChangeEntity>> = changes.observeFor(id)

    /* ---------- mutations ---------- */

    /** Adds or replaces a tracked parcel. Returns its database id. */
    suspend fun addOrUpdate(
        rawNumber: String,
        title: String?,
        orderUrl: String?,
        weightGrams: Double?,
        carrierOverride: Carrier?,
    ): Long {
        val number = FingerprintUtil.normalize(rawNumber)
        require(number.length >= 6) { "Tracking number looks too short" }

        val existing = shipments.findByTrackingNumber(number)
        if (existing != null) {
            shipments.update(
                existing.copy(title = title ?: existing.title, orderUrl = orderUrl ?: existing.orderUrl),
            )
            return existing.id
        }
        val carrier = carrierOverride
            ?: CarrierDetectorSafe.detect(number)
            ?: Carrier.CAINIAO
        return shipments.insert(
            ShipmentEntity(
                trackingNumber = number,
                carrierId = carrier.id,
                title = title?.takeIf { it.isNotBlank() },
                orderUrl = orderUrl?.takeIf { it.isNotBlank() },
                weightGrams = weightGrams,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: Long) {
        events.deleteForShipment(id)
        changes.deleteForShipment(id)
        shipments.deleteById(id)
    }

    suspend fun findByTrackingNumberSync(number: String): ShipmentEntity? =
        shipments.findByTrackingNumber(number)

    /* ---------- refresh / detection ---------- */

    data class RefreshOutcome(val updated: Int, val notable: List<ChangeEntity>)

    /**
     * Refreshes every active shipment (ordered oldest-sync first) and runs the
     * renumbering + combination detectors on each result.
     */
    suspend fun refreshAll(): RefreshOutcome {
        val active = shipments.all().filterNot { it.archived }.sortedBy { it.lastSyncAt ?: 0L }
        var updated = 0
        val notable = mutableListOf<ChangeEntity>()
        for (shipment in active) {
            when (val result = refreshOne(shipment)) {
                null -> Unit
                else -> {
                    if (result.first) updated++
                    notable += result.second
                }
            }
        }
        return RefreshOutcome(updated, notable)
    }

    /**
     * @return null on fetch failure, otherwise (dataChanged, notable changes).
     */
    private suspend fun refreshOne(shipment: ShipmentEntity): Pair<Boolean, List<ChangeEntity>>? {
        val carrier = Carrier.fromId(shipment.carrierId) ?: Carrier.CAINIAO
        val snapshot = fetchSnapshot(carrier, shipment.trackingNumber, shipment.pollCount)
            ?: return null

        var mutable = shipment
        val newChanges = mutableListOf<ChangeEntity>()
        val now = System.currentTimeMillis()

        // Build this parcel's previous view so the change detector can compare.
        val prevEvents = events.eventsFor(shipment.id).map {
            TrackingEvent(it.trackingNumber, it.timeMs, it.description, it.location, it.statusCode)
        }
        val prevSnapshot = Snapshot(shipment.trackingNumber, shipment.weightGrams, null, prevEvents)

        // Detect renumbered / weight changes / progress.
        val detected = ChangeLogService.detect(mapOf(prevSnapshot.trackingNumber to prevSnapshot), snapshot)

        // Renumbering: the carrier now tracks this parcel under a different number.
        val adopted = snapshot.trackingNumber != FingerprintUtil.normalize(shipment.trackingNumber)
        if (adopted) {
            val oldNo = mutable.trackingNumber
            val aliasCsv = listOf(oldNo, *mutable.aliasNumbers.split(',').filter{ it.isNotBlank() }.toTypedArray())
                .distinct().joinToString(",")
            mutable = mutable.copy(trackingNumber = snapshot.trackingNumber, aliasNumbers = aliasCsv)
            val change = ChangeEntity(
                shipmentId = mutable.id, type = "RENUMBERED",
                message = ChangeLogService.humanReadable(
                    com.packatrack.core.model.ParcelChange.Renumbered(oldNo, snapshot.trackingNumber),
                ),
                createdAt = now,
            )
            if (changes.countByType(mutable.id, "RENUMBERED") == 0) newChanges += change
        } else {
            val renum = detected.filterIsInstance<com.packatrack.core.model.ParcelChange.Renumbered>().firstOrNull()
            if (renum != null && renum.newNumber != mutable.trackingNumber) {
                mutable = mutable.copy(
                    trackingNumber = renum.newNumber,
                    aliasNumbers = (mutable.aliasNumbers.split(',') + renum.oldNumber)
                        .filter { it.isNotBlank() }.distinct().joinToString(","),
                )
                newChanges += ChangeEntity(
                    shipmentId = mutable.id, type = "RENUMBERED",
                    message = ChangeLogService.humanReadable(renum), createdAt = now,
                )
            }
        }

        // Combination: does the fresh snapshot look like several existing parcels merged?
        val others = shipments.all()
            .filterNot { it.id == mutable.id || it.archived }
            .mapNotNull { other ->
                val oEvents = events.eventsFor(other.id)
                if (oEvents.isEmpty()) null else Snapshot(
                    other.trackingNumber, other.weightGrams, null,
                    oEvents.map { e -> TrackingEvent(e.trackingNumber, e.timeMs, e.description, e.location, e.statusCode) },
                )
            }
            .associateBy { it.trackingNumber }
        val combined = ChangeLogService.detectCombination(others, snapshot)
        if (combined != null) {
            val msg = ChangeLogService.humanReadable(combined)
            if (changes.countByType(mutable.id, "COMBINED") == 0) {
                newChanges += ChangeEntity(
                    shipmentId = mutable.id, type = "COMBINED",
                    message = msg, createdAt = now,
                )
            }
            // Note on each absorbed parcel too (deduped by type).
            for ((absorbedNo, _) in others) {
                if (FingerprintUtil.normalize(absorbedNo) == FingerprintUtil.normalize(snapshot.trackingNumber)) continue
                val entity = shipments.findByTrackingNumber(absorbedNo) ?: continue
                if (changes.countByType(entity.id, "COMBINED") == 0) {
                    val entry = ChangeEntity(
                        shipmentId = entity.id, type = "COMBINED",
                        message = "Folded into combined parcel ${snapshot.trackingNumber}",
                        createdAt = now,
                    )
                    changes.insert(entry)
                    if (!entity.title.orEmpty().startsWith("(part of")) {
                        shipments.update(entity.copy(archived = false)) // keep visible
                    }
                }
            }
        }

        // Weight & progress entries from the same-number comparison path.
        for (change in detected) {
            if (adopted && change is com.packatrack.core.model.ParcelChange.Progress) continue
            val type = when (change) {
                is com.packatrack.core.model.ParcelChange.WeightChanged -> "WEIGHT"
                is com.packatrack.core.model.ParcelChange.Progress -> "PROGRESS"
                else -> null
            } ?: continue
            val message = ChangeLogService.humanReadable(change)
            newChanges += ChangeEntity(
                shipmentId = mutable.id, type = type,
                message = message, createdAt = now,
            )
        }

        // Persist new events (IGNORE on unique index keeps duplicates out).
        val snapWeight = snapshot.weightGrams ?: mutable.weightGrams
        if (snapshot.events.isNotEmpty()) {
            events.insertAll(
                snapshot.events.map {
                    EventEntity(
                        shipmentId = mutable.id,
                        trackingNumber = it.trackingNumber.ifBlank { snapshot.trackingNumber },
                        timeMs = it.timeMs,
                        description = it.description,
                        location = it.location,
                        statusCode = it.statusCode,
                    )
                },
            )
        }
        val latest = snapshot.events.maxByOrNull { it.timeMs ?: 0L }
        val before = prevEvents.maxByOrNull { it.timeMs ?: 0L }
        val dataChanged = snapshot.events.map { it.description } !=
            prevEvents.map { it.description } ||
            adopted ||
            snapshot.weightGrams != null && mutable.weightGrams != null &&
            !FingerprintUtil.weightClose(snapshot.weightGrams, mutable.weightGrams)

        shipments.update(
            mutable.copy(
                weightGrams = snapWeight,
                pollCount = mutable.pollCount + 1,
                lastSyncAt = now,
                lastStatusCode = latest?.statusCode ?: mutable.lastStatusCode,
            ),
        )

        // Deduplicate within-run duplicates.
        val persisted = mutableListOf<ChangeEntity>()
        for (c in newChanges.distinctBy { it.type to it.message }) {
            changes.insert(c)
            persisted += c
        }
        return Pair(dataChanged, persisted)
    }

    private suspend fun fetchSnapshot(carrier: Carrier, number: String, stageHint: Int): Snapshot? =
        if (prefs.demoMode) {
            if (DemoTrackingFetcher.isDemoNumber(number)) demoFetcher.fetch(carrier, number, stageHint) else null
        } else {
            httpFetcher.fetch(carrier, number, stageHint)
        }
}

/** Thin wrapper so repository code stays readable without importing the detector everywhere. */
private object CarrierDetectorSafe {
    fun detect(number: String): Carrier? = com.packatrack.core.detect.CarrierDetector.detect(number)
}
