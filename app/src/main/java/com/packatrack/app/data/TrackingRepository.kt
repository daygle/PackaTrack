package com.packatrack.app.data

import android.content.Context
import com.packatrack.app.data.db.AppDatabase
import com.packatrack.app.data.db.ChangeEntity
import com.packatrack.app.data.db.EventEntity
import com.packatrack.app.data.db.OrderItemEntity
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.ShipmentWithLegs
import com.packatrack.app.data.db.TrackingLegEntity
import com.packatrack.app.data.fetch.HttpTrackingFetcher
import com.packatrack.core.changelog.ChangeLogService
import com.packatrack.core.model.Carrier
import com.packatrack.core.model.ParcelChange
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
    private val legs = db.legDao()
    private val orders = db.orderDao()
    private val events = db.eventDao()
    private val changes = db.changeDao()

    val httpFetcher = HttpTrackingFetcher { prefs.ausPostApiKey }

    /* ---------- observers ---------- */

    fun observeActive(): Flow<List<ShipmentWithLegs>> = shipments.observeActiveWithLegs()
    fun observeRecentChanges(): Flow<List<ChangeEntity>> = changes.observeRecent()
    fun observeShipment(id: Long): Flow<ShipmentWithLegs?> = shipments.observeWithLegs(id)
    fun observeEvents(shipmentId: Long): Flow<List<EventEntity>> = events.observeForShipment(shipmentId)
    fun observeChangesFor(shipmentId: Long): Flow<List<ChangeEntity>> = changes.observeFor(shipmentId)

    /* ---------- parcel mutations ---------- */

    /**
     * Adds a new parcel with its first courier leg. If the number is already tracked on some
     * parcel this is a no-op that returns that parcel's id.
     */
    suspend fun addShipment(
        rawNumber: String,
        title: String?,
        orderUrl: String?,
        weightGrams: Double?,
        carrierOverride: Carrier?,
    ): Long {
        val number = FingerprintUtil.normalize(rawNumber)
        require(number.length >= 6) { "Tracking number looks too short" }

        legs.findByTrackingNumber(number)?.let { return it.shipmentId }

        val now = System.currentTimeMillis()
        val carrier = carrierOverride ?: detectCarrier(number) ?: Carrier.CAINIAO
        val shipmentId = shipments.insert(
            ShipmentEntity(weightGrams = weightGrams, createdAt = now),
        )
        legs.insert(
            TrackingLegEntity(
                shipmentId = shipmentId,
                trackingNumber = number,
                carrierId = carrier.id,
                weightGrams = weightGrams,
                createdAt = now,
            ),
        )
        // Record the order this parcel started as (name and/or link), if either was given.
        val orderName = title?.takeIf { it.isNotBlank() }
        val orderLink = orderUrl?.takeIf { it.isNotBlank() }
        if (orderName != null || orderLink != null) {
            orders.insert(
                OrderItemEntity(
                    shipmentId = shipmentId,
                    name = orderName ?: "Order",
                    orderUrl = orderLink,
                    createdAt = now,
                ),
            )
        }
        return shipmentId
    }

    /** Adds another order carried inside a parcel (e.g. after a Cainiao consolidation). */
    suspend fun addOrder(shipmentId: Long, name: String, orderUrl: String?) {
        orders.insert(
            OrderItemEntity(
                shipmentId = shipmentId,
                name = name.trim().ifBlank { "Order" },
                orderUrl = orderUrl?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Removes one order from a parcel. */
    suspend fun removeOrder(orderId: Long) = orders.deleteById(orderId)

    /**
     * Adds another courier leg to an existing parcel. Returns the new leg id, or the existing
     * leg's id when the number is already tracked (kept idempotent).
     */
    suspend fun addCourier(
        shipmentId: Long,
        rawNumber: String,
        carrierOverride: Carrier?,
    ): Long {
        val number = FingerprintUtil.normalize(rawNumber)
        require(number.length >= 6) { "Tracking number looks too short" }

        legs.findByTrackingNumber(number)?.let { return it.id }

        val carrier = carrierOverride ?: detectCarrier(number) ?: Carrier.CAINIAO
        val now = System.currentTimeMillis()
        val legId = legs.insert(
            TrackingLegEntity(
                shipmentId = shipmentId,
                trackingNumber = number,
                carrierId = carrier.id,
                createdAt = now,
            ),
        )
        changes.insert(
            ChangeEntity(
                shipmentId = shipmentId,
                type = "COURIER",
                message = "Added ${carrier.displayName} tracking $number",
                createdAt = now,
            ),
        )
        return legId
    }

    /** Removes one courier leg (and its scans) from a parcel. */
    suspend fun removeCourier(legId: Long) {
        events.deleteForLeg(legId)
        legs.deleteById(legId)
    }

    /** Updates a parcel's custom name and declared weight. */
    suspend fun updateShipment(
        shipmentId: Long,
        title: String?,
        weightGrams: Double?,
    ) {
        val current = shipments.byId(shipmentId) ?: return
        shipments.update(
            current.copy(
                title = title?.takeIf { it.isNotBlank() },
                weightGrams = weightGrams ?: current.weightGrams,
            ),
        )
    }

    /**
     * Combines two tracked parcels into one: every courier leg, order, scan and change on
     * [sourceId] moves onto [targetId], and the now-empty source parcel is removed.
     */
    suspend fun combineInto(targetId: Long, sourceId: Long) {
        mergeParcels(targetId, sourceId) { label -> "Combined with $label" }
    }

    /**
     * Moves everything from [sourceId] onto [targetId] and deletes the source. Returns the
     * change row recorded on the target, or null if the merge was a no-op.
     */
    private suspend fun mergeParcels(
        targetId: Long,
        sourceId: Long,
        message: (sourceLabel: String) -> String,
    ): ChangeEntity? {
        if (targetId == sourceId) return null
        val target = shipments.byId(targetId) ?: return null
        val source = shipments.byId(sourceId) ?: return null

        val sourceLabel = source.title
            ?: orders.ordersForShipment(sourceId).firstOrNull()?.name
            ?: legs.legsForShipment(sourceId).firstOrNull()?.trackingNumber
            ?: "another parcel"

        legs.reassignShipment(sourceId, targetId)
        orders.reassignShipment(sourceId, targetId)
        events.reassignShipment(sourceId, targetId)
        changes.reassignShipment(sourceId, targetId)
        shipments.deleteById(sourceId)

        // Carry the source's name / declared weight forward if the target lacked one.
        val merged = target.copy(
            title = target.title ?: source.title,
            weightGrams = target.weightGrams ?: source.weightGrams,
        )
        if (merged != target) shipments.update(merged)

        val change = ChangeEntity(
            shipmentId = targetId,
            type = "COMBINED",
            message = message(sourceLabel),
            createdAt = System.currentTimeMillis(),
        )
        changes.insert(change)
        return change
    }

    /** Deletes a parcel and everything under it. */
    suspend fun delete(shipmentId: Long) {
        events.deleteForShipment(shipmentId)
        changes.deleteForShipment(shipmentId)
        orders.deleteForShipment(shipmentId)
        legs.deleteForShipment(shipmentId)
        shipments.deleteById(shipmentId)
    }

    /* ---------- refresh / detection ---------- */

    data class RefreshOutcome(val updated: Int, val notable: List<ChangeEntity>)

    /** Refreshes every courier leg on every active parcel (oldest-sync first). */
    suspend fun refreshAll(): RefreshOutcome {
        val activeIds = shipments.all().filterNot { it.archived }.map { it.id }.toSet()
        return refreshLegs(legs.all().filter { it.shipmentId in activeIds })
    }

    /** Refreshes just the couriers on one parcel. */
    suspend fun refreshShipment(shipmentId: Long): RefreshOutcome =
        refreshLegs(legs.legsForShipment(shipmentId))

    private suspend fun refreshLegs(toPoll: List<TrackingLegEntity>): RefreshOutcome {
        var updated = 0
        val notable = mutableListOf<ChangeEntity>()
        val consolidations = mutableListOf<Consolidation>()
        for (leg in toPoll.sortedBy { it.lastSyncAt ?: 0L }) {
            val result = refreshLeg(leg) ?: continue
            if (result.dataChanged) updated++
            notable += result.changes
            result.consolidation?.let { consolidations += it }
        }
        // Apply consolidations after the poll loop so we never mutate parcels mid-iteration.
        for (c in consolidations) {
            applyConsolidation(c)?.let { notable += it }
        }
        return RefreshOutcome(updated, notable)
    }

    /**
     * A carrier reported [sourceLegId]'s parcel under [ownerLegId]'s tracking number, so the two
     * shipments are the same physical parcel now.
     */
    private data class Consolidation(
        val sourceLegId: Long,
        val sourceShipmentId: Long,
        val sourceNumber: String,
        val ownerLegId: Long,
        val ownerShipmentId: Long,
    )

    /**
     * Merges the source parcel into the parcel that owns the shared number. The now-redundant
     * source leg (a duplicate view of that number) is dropped and its number kept as a previous
     * number on the owner's leg, so the timeline is not doubled up.
     */
    private suspend fun applyConsolidation(c: Consolidation): ChangeEntity? {
        val ownerLeg = legs.byId(c.ownerLegId) ?: return null

        events.deleteForLeg(c.sourceLegId)
        legs.deleteById(c.sourceLegId)

        val change = mergeParcels(targetId = c.ownerShipmentId, sourceId = c.sourceShipmentId) { label ->
            "Cainiao consolidated “$label” into this parcel"
        }

        val aliasCsv = (ownerLeg.aliasNumbers.split(',').filter { it.isNotBlank() } + c.sourceNumber)
            .distinct().joinToString(",")
        legs.byId(c.ownerLegId)?.let { legs.update(it.copy(aliasNumbers = aliasCsv)) }
        return change
    }

    private data class LegPoll(
        val dataChanged: Boolean,
        val changes: List<ChangeEntity>,
        /** When set, this leg's parcel should be consolidated into another. */
        val consolidation: Consolidation? = null,
    )

    /** @return null on fetch failure, otherwise this leg's poll result. */
    private suspend fun refreshLeg(leg: TrackingLegEntity): LegPoll? {
        val carrier = Carrier.fromId(leg.carrierId) ?: Carrier.CAINIAO
        val snapshot = fetchSnapshot(carrier, leg.trackingNumber, leg.pollCount) ?: return null

        var mutable = leg
        val newChanges = mutableListOf<ChangeEntity>()
        val now = System.currentTimeMillis()
        val firstPoll = leg.pollCount == 0

        val prevEvents = events.eventsForLeg(leg.id).map {
            TrackingEvent(it.trackingNumber, it.timeMs, it.description, it.location, it.statusCode)
        }
        val prevSnapshot = Snapshot(FingerprintUtil.normalize(leg.trackingNumber), leg.weightGrams, null, prevEvents)
        val detected = ChangeLogService.detect(mapOf(prevSnapshot.trackingNumber to prevSnapshot), snapshot)

        // Renumbering & consolidation: the carrier now reports this leg under a different number.
        //  - If no other leg owns that number, this leg simply renumbers (adopts it).
        //  - If another *parcel's* leg already owns it, Cainiao has merged the two shipments —
        //    flag this parcel to be consolidated into that one after the poll loop.
        //  - If our own parcel already owns it (a second leg converged), do nothing.
        val renumberedTo = FingerprintUtil.normalize(snapshot.trackingNumber)
        val numberChanged = renumberedTo != FingerprintUtil.normalize(leg.trackingNumber)
        val owner = if (numberChanged) legs.findByTrackingNumber(renumberedTo) else null
        val adopted = numberChanged && (owner == null || owner.id == leg.id)
        val consolidation = if (!adopted && owner != null && owner.shipmentId != leg.shipmentId) {
            Consolidation(
                sourceLegId = leg.id,
                sourceShipmentId = leg.shipmentId,
                sourceNumber = leg.trackingNumber,
                ownerLegId = owner.id,
                ownerShipmentId = owner.shipmentId,
            )
        } else {
            null
        }
        if (adopted) {
            val oldNo = mutable.trackingNumber
            val aliasCsv = (listOf(oldNo) + mutable.aliasNumbers.split(',').filter { it.isNotBlank() })
                .distinct().joinToString(",")
            mutable = mutable.copy(trackingNumber = renumberedTo, aliasNumbers = aliasCsv)
            val msg = ChangeLogService.humanReadable(ParcelChange.Renumbered(oldNo, snapshot.trackingNumber))
            if (changes.countByMessage(mutable.shipmentId, "RENUMBERED", msg) == 0) {
                newChanges += ChangeEntity(shipmentId = mutable.shipmentId, type = "RENUMBERED", message = msg, createdAt = now)
            }
        }

        // Combination: does this leg's fresh snapshot look like several other parcels merged?
        // Detection needs at least two other parcels, so skip the DB fan-out otherwise.
        val otherShipments = shipments.all().filterNot { it.id == mutable.shipmentId || it.archived }
        if (otherShipments.size >= 2) {
            val others = otherShipments.mapNotNull { other ->
                val firstLeg = legs.legsForShipment(other.id).firstOrNull() ?: return@mapNotNull null
                val oEvents = events.eventsForLeg(firstLeg.id)
                if (oEvents.isEmpty()) return@mapNotNull null
                Snapshot(
                    firstLeg.trackingNumber,
                    firstLeg.weightGrams ?: other.weightGrams,
                    null,
                    oEvents.map { e -> TrackingEvent(e.trackingNumber, e.timeMs, e.description, e.location, e.statusCode) },
                )
            }.associateBy { it.trackingNumber }
            val combined = ChangeLogService.detectCombination(others, snapshot)
            if (combined != null) {
                val msg = ChangeLogService.humanReadable(combined)
                if (changes.countByMessage(mutable.shipmentId, "COMBINED", msg) == 0) {
                    newChanges += ChangeEntity(shipmentId = mutable.shipmentId, type = "COMBINED", message = msg, createdAt = now)
                }
                for (absorbedNo in others.keys) {
                    if (FingerprintUtil.normalize(absorbedNo) == renumberedTo) continue
                    val absorbed = legs.findByTrackingNumber(absorbedNo) ?: continue
                    val foldMsg = "Folded into combined parcel ${snapshot.trackingNumber}"
                    if (changes.countByMessage(absorbed.shipmentId, "COMBINED", foldMsg) == 0) {
                        changes.insert(ChangeEntity(shipmentId = absorbed.shipmentId, type = "COMBINED", message = foldMsg, createdAt = now))
                    }
                }
            }
        }

        // Weight & progress from the same-number comparison path. Skipped on the very first
        // poll — there is no prior state for a scan to have "changed" from, so a fresh parcel
        // does not spam a notification for its opening scan.
        if (!firstPoll) {
            for (change in detected) {
                if (adopted && change is ParcelChange.Progress) continue
                val type = when (change) {
                    is ParcelChange.WeightChanged -> "WEIGHT"
                    is ParcelChange.Progress -> "PROGRESS"
                    else -> null
                } ?: continue
                newChanges += ChangeEntity(
                    shipmentId = mutable.shipmentId,
                    type = type,
                    message = ChangeLogService.humanReadable(change),
                    createdAt = now,
                )
            }
        }

        // Persist new events (IGNORE on unique index keeps duplicates out).
        val snapWeight = snapshot.weightGrams ?: mutable.weightGrams
        if (snapshot.events.isNotEmpty()) {
            events.insertAll(
                snapshot.events.map {
                    EventEntity(
                        shipmentId = mutable.shipmentId,
                        legId = mutable.id,
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
        val weightMoved = snapshot.weightGrams != null && mutable.weightGrams != null &&
            !FingerprintUtil.weightClose(snapshot.weightGrams, mutable.weightGrams)
        // Snapshots are cumulative, so a size change means the carrier added scans.
        val dataChanged = snapshot.events.size != prevEvents.size || adopted || weightMoved

        legs.update(
            mutable.copy(
                weightGrams = snapWeight,
                pollCount = mutable.pollCount + 1,
                lastSyncAt = now,
                lastStatusCode = latest?.statusCode ?: mutable.lastStatusCode,
            ),
        )

        val persisted = mutableListOf<ChangeEntity>()
        for (c in newChanges.distinctBy { it.type to it.message }) {
            changes.insert(c)
            persisted += c
        }
        return LegPoll(dataChanged, persisted, consolidation)
    }

    private suspend fun fetchSnapshot(carrier: Carrier, number: String, stageHint: Int): Snapshot? =
        httpFetcher.fetch(carrier, number, stageHint)

    private fun detectCarrier(number: String): Carrier? =
        com.packatrack.core.detect.CarrierDetector.detect(number)
}
