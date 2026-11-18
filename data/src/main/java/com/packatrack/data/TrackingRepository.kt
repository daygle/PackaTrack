package com.packatrack.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.packatrack.core.changelog.ChangeLogService
import com.packatrack.core.db.ChangeEntity
import com.packatrack.core.db.EventEntity
import com.packatrack.core.db.OrderItemEntity
import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.ShipmentWithLegs
import com.packatrack.core.db.TrackingLegEntity
import com.packatrack.core.detect.CarrierDetector
import com.packatrack.core.model.Carrier
import com.packatrack.core.model.ChangeDisplay
import com.packatrack.core.model.LegScans
import com.packatrack.core.model.NumberedLeg
import com.packatrack.core.model.ParcelChange
import com.packatrack.core.model.RawResponse
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.model.aliasesWithoutLiveNumbers
import com.packatrack.core.model.findDuplicateLegs
import com.packatrack.core.model.findSharedNumberMerges
import com.packatrack.core.model.overallStatusCode
import com.packatrack.core.model.scanFingerprint
import com.packatrack.core.util.FingerprintUtil
import com.packatrack.data.db.AppDatabase
import com.packatrack.data.fetch.HttpTrackingFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsStore,
) {
    private val db = AppDatabase.get(context)
    private val shipments = db.shipmentDao()
    private val legs = db.legDao()
    private val orders = db.orderDao()
    private val events = db.eventDao()
    private val changes = db.changeDao()
    private val refreshMutex = Mutex()

    /**
     * Last raw payload per courier number, for the troubleshooting view on a parcel. Session
     * memory only: it describes what this run has fetched, not stored parcel data.
     */
    private val _rawResponses = MutableStateFlow<Map<String, RawResponse>>(emptyMap())
    val rawResponses: StateFlow<Map<String, RawResponse>> = _rawResponses.asStateFlow()

    private val httpFetcher = HttpTrackingFetcher(
        ausPostKey = { prefs.ausPostApiKey },
        onRawResponse = { key, response -> _rawResponses.update { it + (key to response) } },
    )

    /* ---------- observers ---------- */

    fun observeActive(): Flow<List<ShipmentWithLegs>> = shipments.observeActiveWithLegs()
    fun observeArchived(): Flow<List<ShipmentWithLegs>> = shipments.observeArchivedWithLegs()
    fun observeRecentChanges(): Flow<List<ChangeEntity>> = changes.observeRecent()
    fun observeShipment(id: Long): Flow<ShipmentWithLegs?> = shipments.observeWithLegs(id)
    fun observeEvents(shipmentId: Long): Flow<List<EventEntity>> = events.observeForShipment(shipmentId)

    /** Changes the app itself recorded for one parcel, newest first (shown in its timeline). */
    fun observeChangesFor(shipmentId: Long): Flow<List<ChangeEntity>> = changes.observeFor(shipmentId)

    /** Earliest scan time per shipment (shipmentId -> first event ms), for "days in transit". */
    fun observeFirstEventTimes(): Flow<Map<Long, Long>> =
        events.observeFirstEventTimes().map { rows ->
            rows.mapNotNull { row -> row.firstMs?.let { row.shipmentId to it } }.toMap()
        }

    /** Latest known tracking event per parcel, ordered newest first by the database. */
    fun observeLatestEvents(): Flow<Map<Long, EventEntity>> =
        events.observeLatestByShipment().map { rows ->
            rows.asSequence()
                .distinctBy { it.shipmentId }
                .associateBy { it.shipmentId }
        }

    /** Newest timestamped scan per leg (legId -> max event ms), used for status recency votes. */
    fun observeLatestEventMsByLeg(): Flow<Map<Long, Long>> =
        events.observeLatestEventMsByLeg().map { rows ->
            rows.mapNotNull { row -> row.latestMs?.let { row.legId to it } }.toMap()
        }

    /* ---------- parcel mutations ---------- */

    /**
     * Adds a new parcel with its first courier leg. If the number is already tracked on some
     * parcel this is a no-op that returns that parcel's id.
     */
    suspend fun addShipment(
        rawNumber: String,
        title: String?,
        orderUrl: String?,
        carrierOverride: Carrier?,
    ): Long = refreshMutex.withLock { addShipmentUnlocked(rawNumber, title, orderUrl, carrierOverride) }

    private suspend fun addShipmentUnlocked(
        rawNumber: String,
        title: String?,
        orderUrl: String?,
        carrierOverride: Carrier?,
    ): Long {
        val number = FingerprintUtil.normalize(rawNumber)
        require(number.length >= FingerprintUtil.MIN_NUMBER_LENGTH) { "Tracking number looks too short" }

        legs.findByTrackingNumber(number)?.let { existing ->
            // An older install may already contain this number under only some of its
            // carriers. Complete the auto setup when the same number is added again
            // (addCourierUnlocked is a no-op for carriers that already have a leg).
            if (carrierOverride == null) {
                CarrierDetector.detectAll(number).forEach { detected ->
                    addCourierUnlocked(existing.shipmentId, number, detected)
                }
            }
            return existing.shipmentId
        }

        val now = System.currentTimeMillis()
        val detectedCarriers = CarrierDetector.detectAll(number)
        val carriersToAdd = if (carrierOverride != null) listOf(carrierOverride) else {
            detectedCarriers.ifEmpty { listOf(Carrier.CAINIAO) }
        }
        val shipmentId = shipments.insert(
            ShipmentEntity(createdAt = now),
        )
        carriersToAdd.forEach { detected ->
            legs.insert(
                TrackingLegEntity(
                    shipmentId = shipmentId,
                    trackingNumber = number,
                    carrierId = detected.id,
                    createdAt = now,
                ),
            )
        }
        // Record the order this parcel started as (name and/or link), if either was given.
        val orderName = title?.takeIf { it.isNotBlank() }
        val orderLink = orderUrl?.takeIf { it.isNotBlank() }
        if ((orderName != null) || (orderLink != null)) {
            orders.insert(
                OrderItemEntity(
                    shipmentId = shipmentId,
                    name = orderName ?: "Item",
                    orderUrl = orderLink,
                    createdAt = now,
                ),
            )
        }
        // Each auto-created leg (one per matching carrier) is polled by the caller's first
        // refresh, so this method only sets up the rows.
        return shipmentId
    }

    /** Adds another order carried inside a parcel (e.g. after a Cainiao consolidation). */
    suspend fun addOrder(shipmentId: Long, name: String, orderUrl: String?) {
        orders.insert(
            OrderItemEntity(
                shipmentId = shipmentId,
                name = name.trim().ifBlank { "Item" },
                orderUrl = orderUrl?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Renames an item and changes its order link. Blank values are stored the way [addOrder]
     * would store them, so an item never ends up with an empty name.
     */
    suspend fun updateOrder(orderId: Long, name: String, orderUrl: String?) {
        orders.update(
            orderId = orderId,
            name = name.trim().ifBlank { "Item" },
            orderUrl = orderUrl?.takeIf { it.isNotBlank() },
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
    ): Long = refreshMutex.withLock { addCourierUnlocked(shipmentId, rawNumber, carrierOverride) }

    private suspend fun addCourierUnlocked(
        shipmentId: Long,
        rawNumber: String,
        carrierOverride: Carrier?,
    ): Long {
        val number = FingerprintUtil.normalize(rawNumber)
        require(number.length >= FingerprintUtil.MIN_NUMBER_LENGTH) { "Tracking number looks too short" }

        val carrier = carrierOverride ?: CarrierDetector.detectAll(number).firstOrNull() ?: Carrier.CAINIAO
        legs.findByTrackingNumberAndCarrier(number, carrier.id)?.let { existing ->
            // The number is live on this parcel already (an older install may also have kept it as
            // an alias of another leg here), so any alias spelling it is stale.
            pruneSiblingAliases(shipmentId)
            return existing.id
        }
        val now = System.currentTimeMillis()
        val legId = legs.insert(
            TrackingLegEntity(
                shipmentId = shipmentId,
                trackingNumber = number,
                carrierId = carrier.id,
                createdAt = now,
            ),
        )
        // The number has a leg of its own here now, so it is current, not previous: drop it from
        // the alias list it may still sit in on a sibling leg.
        pruneSiblingAliases(shipmentId)
        changes.insert(
            ChangeEntity(
                shipmentId = shipmentId,
                type = "COURIER",
                message = "Added ${carrier.displayName} tracking $number",
                createdAt = now,
            ).withDisplay(
                ChangeDisplay(
                    kind = ChangeDisplay.Kind.COURIER_ADDED,
                    category = ChangeDisplay.Category.TRANSIT,
                    carrier = carrier.displayName,
                    newNumber = number,
                ),
            ),
        )
        return legId
    }

    /**
     * Drops aliases from this parcel's legs when another leg of the same parcel tracks that number
     * as its current one. An alias records a number the leg was tracked under, so once the number
     * has a leg of its own the alias is stale - it showed the same number twice on the parcel, once
     * as an "Aliases:" line and once as its own courier card. Renumbered aliases stay untouched,
     * because "View response" falls back to them to find the payload of the previous number.
     */
    private suspend fun pruneSiblingAliases(shipmentId: Long) {
        val parcelLegs = legs.legsForShipment(shipmentId)
        if (parcelLegs.size < 2) return
        val liveNumbers = parcelLegs.map { it.trackingNumber }
        for (leg in parcelLegs) {
            val pruned = aliasesWithoutLiveNumbers(leg.aliasNumbers, liveNumbers)
            if (pruned != leg.aliasNumbers) legs.update(leg.copy(aliasNumbers = pruned))
        }
    }

    /** Removes one courier leg (and its scans) from a parcel. */
    suspend fun removeCourier(legId: Long) = refreshMutex.withLock {
        db.withTransaction {
            events.deleteForLeg(legId)
            legs.deleteById(legId)
        }
    }

    /**
     * Removes legs that only repeat another leg of the same courier, scan for scan.
     *
     * Carriers report one parcel under several of their own numbers (Cainiao's `realMailNo` is
     * one), and an earlier version of the app turned each of those into an extra leg, so a parcel
     * listed the same courier twice with an identical timeline. The repeating leg adds nothing, so
     * it is dropped and its number is kept as an alias of the leg that survives - the number stays
     * visible on that courier and still matches the parcel if the carrier reports it again.
     *
     * @return the change recorded for each removed leg, so the caller can notify about them.
     */
    suspend fun removeDuplicateLegs(shipmentId: Long): List<ChangeEntity> = refreshMutex.withLock {
        val allLegs = legs.legsForShipment(shipmentId)
        if (allLegs.size < 2) return@withLock emptyList()
        val scansByLeg = events.eventsForLegs(allLegs.map { it.id })
            .groupBy { it.legId }
            .mapValues { (_, rows) ->
                rows.mapTo(HashSet()) { row -> scanFingerprint(row.timeMs, row.description) }
            }
        val duplicates = findDuplicateLegs(
            allLegs.map { leg ->
                LegScans(
                    legId = leg.id,
                    carrierId = leg.carrierId,
                    trackingNumber = leg.trackingNumber,
                    order = leg.id,
                    scans = scansByLeg[leg.id].orEmpty(),
                )
            },
        )
        if (duplicates.isEmpty()) return@withLock emptyList()
        val label = parcelLabel(shipmentId)
        val now = System.currentTimeMillis()
        val recorded = mutableListOf<ChangeEntity>()
        db.withTransaction {
            for (duplicate in duplicates) {
                val survivor = allLegs.first { it.id == duplicate.survivorLegId }
                val aliases = (survivor.aliasNumbers.split(',') + duplicate.trackingNumber)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != survivor.trackingNumber }
                    .distinct()
                    .joinToString(",")
                if (aliases != survivor.aliasNumbers) {
                    legs.update(survivor.copy(aliasNumbers = aliases))
                }
                events.deleteForLeg(duplicate.legId)
                legs.deleteById(duplicate.legId)
                // Recorded so the history explains why a tracking number disappeared; the number
                // itself stays on the survivor's alias list.
                val carrierName = Carrier.fromId(survivor.carrierId)?.displayName
                val change = ChangeEntity(
                    shipmentId = shipmentId,
                    type = "DUPLICATE",
                    message = "$label: Merged duplicate ${carrierName ?: survivor.carrierId} " +
                        "tracking ${duplicate.trackingNumber} into ${survivor.trackingNumber}",
                    createdAt = now,
                ).withDisplay(
                    ChangeDisplay(
                        kind = ChangeDisplay.Kind.DUPLICATE_REMOVED,
                        category = ChangeDisplay.Category.TRANSIT,
                        parcel = label,
                        carrier = carrierName,
                        oldNumber = duplicate.trackingNumber,
                        newNumber = survivor.trackingNumber,
                    ),
                )
                changes.insert(change)
                recorded += change
            }
        }
        Log.i("TrackingRepository", "Removed ${recorded.size} duplicate leg(s) from parcel $shipmentId")
        recorded
    }

    /** Updates a parcel's custom name. */
    suspend fun updateShipment(shipmentId: Long, title: String?) =
        shipments.setTitle(shipmentId, title?.takeIf { it.isNotBlank() })

    /**
     * Combines two tracked parcels into one: every courier leg, order, scan and change on
     * [sourceId] moves onto [targetId], and the now-empty source parcel is removed.
     */
    suspend fun combineInto(targetId: Long, sourceId: Long) = refreshMutex.withLock {
        db.withTransaction {
            mergeParcels(targetId, sourceId, ChangeDisplay.Kind.MERGED_WITH) { label ->
                "Combined with $label"
            }
        }
    }

    /**
     * Moves everything from [sourceId] onto [targetId] and deletes the source. Returns the
     * change row recorded on the target, or null if the merge was a no-op.
     */
    private suspend fun mergeParcels(
        targetId: Long,
        sourceId: Long,
        kind: ChangeDisplay.Kind,
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

        // Carry the source's name forward if the target lacked one.
        if (target.title == null && source.title != null) shipments.setTitle(targetId, source.title)

        val label = parcelLabel(targetId)
        val change = ChangeEntity(
            shipmentId = targetId,
            type = "COMBINED",
            message = "$label: ${message(sourceLabel)}",
            createdAt = System.currentTimeMillis(),
        ).withDisplay(
            ChangeDisplay(
                kind = kind,
                category = ChangeDisplay.Category.TRANSIT,
                parcel = label,
                into = sourceLabel,
            ),
        )
        changes.insert(change)
        return change
    }

    /** Deletes a parcel and everything under it. */
    suspend fun delete(shipmentId: Long) = refreshMutex.withLock {
        db.withTransaction {
            events.deleteForShipment(shipmentId)
            changes.deleteForShipment(shipmentId)
            orders.deleteForShipment(shipmentId)
            legs.deleteForShipment(shipmentId)
            shipments.deleteById(shipmentId)
        }
        prefs.clearActivitySeen(shipmentId)
    }

    /** Hides a parcel from the main list. */
    suspend fun archive(shipmentId: Long) = shipments.setArchived(shipmentId, archived = true)

    /** Restores a parcel to the main list. */
    suspend fun unarchive(shipmentId: Long) = shipments.setArchived(shipmentId, archived = false)

    /* ---------- refresh / detection ---------- */

    data class RefreshOutcome(val updated: Int, val notable: List<ChangeEntity>)

    /**
     * Refreshes every courier leg on every active parcel (oldest-sync first).
     * @param force When true, bypasses the background cooldown.
     */
    suspend fun refreshAll(force: Boolean = false): RefreshOutcome = refreshMutex.withLock {
        refreshLegs(legs.allActiveLegs(), force)
    }

    /**
     * Refreshes just the couriers on one parcel.
     * @param force When true, bypasses the background cooldown.
     */
    suspend fun refreshShipment(shipmentId: Long, force: Boolean = false): RefreshOutcome = refreshMutex.withLock {
        refreshLegs(legs.legsForShipment(shipmentId), force)
    }

    private suspend fun refreshLegs(toPoll: List<TrackingLegEntity>, force: Boolean): RefreshOutcome {
        var updated = 0
        val notable = mutableListOf<ChangeEntity>()
        val consolidations = mutableListOf<Consolidation>()
        val now = System.currentTimeMillis()

        for (leg in toPoll.sortedBy { it.lastSyncAt ?: 0L }) {
            // Safeguard: Cooldown to prevent flooding carriers.
            // Background sync: 10 minute cooldown.
            // Manual refresh: 1 minute cooldown to prevent button spamming.
            val cooldownMs = if (force) 60_000L else 10 * 60_000L
            if (now - (leg.lastSyncAt ?: 0L) < cooldownMs) continue

            val result = refreshLeg(leg) ?: continue
            if (result.dataChanged) updated++
            notable += result.changes
            consolidations += result.consolidations
        }
        // Apply consolidations after the poll loop so we never mutate parcels mid-iteration.
        for (c in consolidations) {
            applyConsolidation(c)?.let { notable += it }
        }
        // Parcels still tracked under one number - a hand-off number both of them carry, or
        // duplicate data from an older install - are one physical parcel: combine them.
        notable += mergeParcelsSharingNumbers()

        if (prefs.autoArchiveDelivered) {
            autoArchiveDeliveredParcels(toPoll.map { it.shipmentId }.distinct())
        }

        return RefreshOutcome(updated, notable)
    }

    /**
     * Auto-archives parcels the moment their OVERALL status (not any single leg) is DELIVERED.
     *
     * Uses the same recency-aware heuristic as the UI's status pill (see
     * com.packatrack.core.model.overallStatusCode): a single leg reporting DELIVERED does not
     * settle the parcel while another leg's newest scan is fresher - e.g. the origin carrier's
     * premature "delivered to destination stream" beat must not archive a parcel that is still
     * moving on the destination leg. Skips parcels already archived, and only touches shipments
     * that actually have legs.
     */
    private suspend fun autoArchiveDeliveredParcels(shipmentIds: List<Long>) {
        if (shipmentIds.isEmpty()) return
        val newestEventMsByLeg = events.latestEventMsByLeg()
            .mapNotNull { row -> row.latestMs?.let { row.legId to it } }
            .toMap()
        for (shipmentId in shipmentIds) {
            val shipmentLegs = legs.legsForShipment(shipmentId)
            if (shipmentLegs.isEmpty()) continue
            if (overallStatusCode(shipmentLegs, newestEventMsByLeg) != "DELIVERED") continue
            val shipment = shipments.byId(shipmentId) ?: continue
            if (!shipment.archived) shipments.setArchived(shipmentId, archived = true)
        }
    }

    /**
     * A poll proved that [sourceLegId]'s parcel and [ownerLegId]'s parcel are the same physical
     * parcel because both track [sharedNumber]: either a carrier renumbered its leg onto a number
     * another parcel already has, or a hand-off number reported for one leg is already tracked on
     * (or kept among the previous numbers of) the other parcel.
     */
    private data class Consolidation(
        val sourceLegId: Long,
        val ownerLegId: Long,
        /**
         * The number the two parcels share: live on one leg, and possibly only a previous number
         * on the other. Kept as a previous number when the source leg is dropped.
         */
        val sharedNumber: String,
        /**
         * True when the source leg is only a second view of [sharedNumber] (the carrier moved it
         * onto that number), so it is dropped with its duplicate scans. False for a shared
         * hand-off number, where each leg keeps its own number and its own scans.
         */
        val dropSourceLeg: Boolean = true,
    )

    /**
     * Merges the source parcel into the parcel that owns the shared number. For a renumbering the
     * now-redundant source leg (a duplicate view of that number) is dropped and its number kept
     * as a previous number on the owner's leg, so the timeline is not doubled up; for a hand-off
     * number both legs stay, because each carries scans of its own.
     */
    private suspend fun applyConsolidation(c: Consolidation): ChangeEntity? =
        // Atomic: a crash between dropping the source leg and merging must not leave
        // the source parcel without its leg (or the merge half-applied).
        db.withTransaction {
            // Re-read both legs here: an earlier consolidation in the same refresh may already
            // have moved either one to another parcel, or dropped the source leg. Their current
            // parcels are what must be merged, and nothing is deleted unless both still exist -
            // otherwise the source leg's scans would be lost with no parcel to merge them into.
            val ownerLeg = legs.byId(c.ownerLegId) ?: return@withTransaction null
            val sourceLeg = legs.byId(c.sourceLegId) ?: return@withTransaction null
            if (ownerLeg.shipmentId == sourceLeg.shipmentId) return@withTransaction null

            if (c.dropSourceLeg) {
                events.deleteForLeg(sourceLeg.id)
                legs.deleteById(sourceLeg.id)
            }

            // Keep the parcel the user still sees: a delivered parcel is auto-archived, and
            // folding an active parcel into it would hide the combined result from the list.
            val ownerHidden = shipments.byId(ownerLeg.shipmentId)?.archived == true
            val sourceHidden = shipments.byId(sourceLeg.shipmentId)?.archived == true
            val keepOwnerParcel = !(ownerHidden && !sourceHidden)
            val targetShipmentId =
                if (keepOwnerParcel) ownerLeg.shipmentId else sourceLeg.shipmentId
            val mergedShipmentId =
                if (keepOwnerParcel) sourceLeg.shipmentId else ownerLeg.shipmentId

            val change = mergeParcels(
                targetId = targetShipmentId,
                sourceId = mergedShipmentId,
                kind = if (c.dropSourceLeg) {
                    ChangeDisplay.Kind.CONSOLIDATED
                } else {
                    ChangeDisplay.Kind.MERGED_WITH
                },
            ) { label ->
                if (c.dropSourceLeg) {
                    "Cainiao consolidated “$label” into this parcel"
                } else {
                    // "Shared", not "both track": an alias match means the number is live on one
                    // parcel and only kept as a previous/alternate number on the other.
                    "Combined with “$label”: shared tracking number ${c.sharedNumber}"
                }
            }

            if (c.dropSourceLeg) {
                val aliasCsv = (ownerLeg.aliasNumbers.split(',').filter { it.isNotBlank() } + c.sharedNumber)
                    .distinct()
                    .joinToString(",")
                if (aliasCsv != ownerLeg.aliasNumbers) legs.update(ownerLeg.copy(aliasNumbers = aliasCsv))
                // The merged-in parcel can bring a leg that tracks this number, which would make the
                // alias just written stale.
                pruneSiblingAliases(ownerLeg.shipmentId)
            }
            change
        }

    /**
     * Combines parcels that are tracked under the same number, which is how a parcel's journey
     * ends up shared: a carrier starts reporting a hand-off number that another parcel already
     * carries - as its live number, or as a previous number kept in its alias list - or an older
     * install left the same number on two parcels.
     *
     * The parcel whose leg tracks the number survives (see [findSharedNumberMerges] for the rule
     * and the alias-matching guard), and no leg is dropped here: legs of different couriers each
     * hold their own scans for that number, and identical duplicates are pruned when the parcel is
     * opened (see [removeDuplicateLegs]).
     */
    private suspend fun mergeParcelsSharingNumbers(): List<ChangeEntity> {
        val active = legs.allActiveLegs().map { it.toNumberedLeg() }
        val recorded = mutableListOf<ChangeEntity>()
        for (merge in findSharedNumberMerges(active)) {
            val change = applyConsolidation(
                Consolidation(
                    sourceLegId = merge.mergedLegId,
                    ownerLegId = merge.ownerLegId,
                    sharedNumber = merge.number,
                    dropSourceLeg = false,
                ),
            ) ?: continue
            recorded += change
        }
        return recorded
    }

    private data class LegPoll(
        val dataChanged: Boolean,
        val changes: List<ChangeEntity>,
        /** Other parcels this leg proved are the same physical parcel, merged after the loop. */
        val consolidations: List<Consolidation> = emptyList(),
    )

    /**
     * Display label for a parcel, mirroring the home-list naming: custom title first, then
     * the first order name (with a "+N more" suffix), then the first tracking number. Used to
     * prefix change messages so notifications identify which parcel changed.
     */
    private suspend fun parcelLabel(shipmentId: Long): String {
        val shipment = shipments.byId(shipmentId) ?: return "Parcel"
        shipment.title?.takeIf { it.isNotBlank() }?.let { return it }
        val orderItems = orders.ordersForShipment(shipmentId)
        orderItems.firstOrNull()?.let { first ->
            return if (orderItems.size == 1) first.name else "${first.name} +${orderItems.size - 1} more"
        }
        return legs.legsForShipment(shipmentId).firstOrNull()?.trackingNumber ?: "Parcel"
    }

    /**
     * The courier that recognises [number], a hand-off number in no known format, or null when
     * none does yet. A courier already tracking the number counts without asking again; otherwise
     * a courier counts once its lookup resolves the number, even with no scans (iMile answers
     * that way for a waybill it has registered but not yet scanned).
     */
    private suspend fun findHandoffCarrier(number: String, reportedBy: Carrier): Carrier? =
        HANDOFF_PROBE_CARRIERS.firstOrNull { candidate ->
            candidate != reportedBy && (
                legs.findByTrackingNumberAndCarrier(number, candidate.id) != null ||
                    httpFetcher.fetch(candidate, number) != null
                )
        }

    /**
     * A leg on another parcel that lists [number] among its previous/alternate numbers. Used when
     * no leg tracks [number] as its live number: the number was kept as an alias after a
     * renumbering, or it was an unconfirmed hand-off nobody had claimed yet.
     */
    private suspend fun parcelListingAlias(number: String, shipmentId: Long): TrackingLegEntity? =
        legs.allActiveLegs().firstOrNull { leg ->
            leg.shipmentId != shipmentId &&
                leg.aliasNumbers.split(',').any { alias -> FingerprintUtil.normalize(alias) == number }
        }

    /** @return null on fetch failure, otherwise this leg's poll result. */
    private suspend fun refreshLeg(leg: TrackingLegEntity): LegPoll? {
        val carrier = Carrier.fromId(leg.carrierId) ?: Carrier.CAINIAO
        val snapshot = httpFetcher.fetch(carrier, leg.trackingNumber)
        if (snapshot == null) {
            Log.w("TrackingRepository", "No snapshot for leg ${leg.id} (${carrier.displayName})")
            return null
        }

        // Cainiao/UBI commonly reports the destination-carrier number in the same
        // response. Keep it as a second leg so its scans can be fetched independently, but only
        // when it belongs to a different courier: Cainiao also fills realMailNo/copyRealMailNo
        // with its own alternative identifiers, and turning those into legs gave one parcel two
        // identical Cainiao entries tracking the same scans.
        val unconfirmedHandoffs = mutableListOf<String>()
        val merges = mutableListOf<Consolidation>()
        for (relatedNumber in snapshot.relatedTrackingNumbers) {
            val normalized = FingerprintUtil.normalize(relatedNumber)
            if (normalized.isBlank() || normalized == FingerprintUtil.normalize(leg.trackingNumber)) continue
            // Another parcel may already know this hand-off number: it is tracked there as a live
            // number, or it was kept as a previous/unconfirmed number in that leg's alias list.
            // Either way the carrier has chained both parcels onto one number, so they are the
            // same physical parcel - combine them after the poll loop instead of leaving two
            // parcels tracking the same number.
            val owner = legs.findByTrackingNumber(normalized)
                ?: parcelListingAlias(normalized, leg.shipmentId)
            if (owner != null && owner.shipmentId != leg.shipmentId) {
                merges += Consolidation(
                    sourceLegId = leg.id,
                    ownerLegId = owner.id,
                    sharedNumber = normalized,
                    dropSourceLeg = false,
                )
                continue
            }
            val detected = CarrierDetector.detectAll(normalized)
            if (detected.isNotEmpty()) {
                for (relatedCarrier in detected) {
                    if (relatedCarrier == carrier) continue
                    addCourierUnlocked(leg.shipmentId, normalized, relatedCarrier)
                }
                continue
            }
            // No known format, so the courier cannot be told from the number alone (iMile's
            // Australian numbers, for one, carry no prefix). Ask the couriers that issue such
            // numbers and add the leg only for one that knows the number - possibly with no
            // scans yet. This used to fall back to Cainiao, which only re-fetched this same
            // timeline under the new number.
            val confirmed = findHandoffCarrier(normalized, reportedBy = carrier)
            if (confirmed != null) {
                addCourierUnlocked(leg.shipmentId, normalized, confirmed)
            } else {
                // Nobody claims it yet: keep it visible as an alias and ask again next refresh.
                unconfirmedHandoffs += normalized
            }
        }

        var mutable = leg
        val newChanges = mutableListOf<ChangeEntity>()
        val now = System.currentTimeMillis()
        val firstPoll = leg.pollCount == 0

        val prevEvents = events.eventsForLeg(leg.id).map { it.toTrackingEvent() }
        val prevSnapshot = Snapshot(
            trackingNumber = FingerprintUtil.normalize(leg.trackingNumber),
            events = prevEvents
        )
        val detected = ChangeLogService.detect(mapOf(prevSnapshot.trackingNumber to prevSnapshot), snapshot)

        // Renumbering & consolidation: the carrier now reports this leg under a different number.
        //  - If no other leg owns that number, this leg simply renumbers (adopts it).
        //  - If another *parcel's* leg already owns it, Cainiao has merged the two shipments,
        //    so flag this parcel to be consolidated into that one after the poll loop.
        //  - If our own parcel already owns it (a second leg converged), do nothing.
        val renumberedTo = FingerprintUtil.normalize(snapshot.trackingNumber)
        val numberChanged = renumberedTo != FingerprintUtil.normalize(leg.trackingNumber)
        val owner = if (numberChanged) legs.findByTrackingNumber(renumberedTo) else null
        val adopted = numberChanged && ((owner == null) || (owner.id == leg.id))
        if (!adopted && owner != null && owner.shipmentId != leg.shipmentId) {
            // The leg now carries another parcel's number: the carrier merged the two shipments.
            merges += Consolidation(
                sourceLegId = leg.id,
                ownerLegId = owner.id,
                sharedNumber = leg.trackingNumber,
            )
        }
        if (adopted) {
            val oldNo = mutable.trackingNumber
            val aliasCsv = (listOf(oldNo) + mutable.aliasNumbers.split(',').filter { it.isNotBlank() })
                .asSequence()
                .distinct()
                .joinToString(",")
            mutable = mutable.copy(trackingNumber = renumberedTo, aliasNumbers = aliasCsv)
            val label = parcelLabel(mutable.shipmentId)
            val msg = "$label: ${ChangeLogService.humanReadable(ParcelChange.Renumbered(oldNo, snapshot.trackingNumber))}"
            if (changes.countByMessage(mutable.shipmentId, "RENUMBERED", msg) == 0) {
                newChanges += ChangeEntity(
                    shipmentId = mutable.shipmentId,
                    type = "RENUMBERED",
                    message = msg,
                    createdAt = now,
                ).withDisplay(
                    ChangeDisplay(
                        kind = ChangeDisplay.Kind.RENUMBERED,
                        category = ChangeDisplay.Category.TRANSIT,
                        parcel = label,
                        oldNumber = oldNo,
                        newNumber = snapshot.trackingNumber,
                    ),
                )
            }
        }

        // Combination: does this leg's fresh snapshot look like several other parcels merged?
        // Only worth the (all-parcel) lookup when the carrier actually reports a consolidation.
        if (ChangeLogService.mentionsConsolidation(snapshot)) {
            detectCombination(mutable.shipmentId, snapshot, renumberedTo, now)?.let { newChanges += it }
        }

        // Progress from the same-number comparison path. Skipped on the very first
        // poll - there is no prior state for a scan to have "changed" from, so a fresh parcel
        // does not spam a notification for its opening scan.
        if (!firstPoll && !adopted) {
            val label = parcelLabel(mutable.shipmentId)
            // Filed under the parcel's newest status, so a delivery scan respects the user's
            // Delivered / Exception / Transit notification switches.
            val latestCode = snapshot.events.maxByOrNull { it.timeMs ?: 0L }?.statusCode
            for (change in detected.filterIsInstance<ParcelChange.Progress>()) {
                newChanges += ChangeEntity(
                    shipmentId = mutable.shipmentId,
                    type = "PROGRESS",
                    message = "$label: ${ChangeLogService.humanReadable(change)}",
                    createdAt = now,
                ).withDisplay(
                    ChangeDisplay(
                        kind = ChangeDisplay.Kind.PROGRESS,
                        // The scan's own code first, then its wording, so a delivery scan is
                        // filed under Delivered rather than the default Transit switch.
                        category = notificationCategory(latestCode, change.description),
                        parcel = label,
                        description = change.description,
                        timeMs = change.timeMs,
                    ),
                )
            }
        }

        // Persist new events. The unique index (IGNORE) keeps timestamped duplicates out, but
        // SQLite treats NULLs as distinct there, so undated scans are filtered here instead.
        val knownUndated = prevEvents.filter { it.timeMs == null }.mapTo(HashSet()) { it.description }
        val fresh = snapshot.events.filter { it.timeMs != null || it.description !in knownUndated }
        if (fresh.isNotEmpty()) {
            events.insertAll(
                fresh.map {
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
        // Snapshots are cumulative, so a size change means the carrier added scans.
        val dataChanged = snapshot.events.size != prevEvents.size || adopted

        // The carrier's own route and percentage, so the detail screen can show it even after a
        // failed poll. Only overwritten when this response actually reported it.
        val progress = snapshot.progress
        // A number that has a leg of its own on this parcel is never a previous number of this one:
        // adding that leg pruned it (see [pruneSiblingAliases]), so this update must not write the
        // stale entry back from the leg row read before it. The leg's own current number is
        // excluded too, while the number it was renumbered from stays an alias for "View response".
        val liveNumbersHere = legs.legsForShipment(mutable.shipmentId)
            .filter { it.id != mutable.id }
            .map { it.trackingNumber } + mutable.trackingNumber
        val aliasNumbers = aliasesWithoutLiveNumbers(
            (mutable.aliasNumbers.split(',') + unconfirmedHandoffs).joinToString(","),
            liveNumbersHere,
        )
        legs.update(
            mutable.copy(
                aliasNumbers = aliasNumbers,
                pollCount = mutable.pollCount + 1,
                lastSyncAt = now,
                lastStatusCode = latest?.statusCode ?: mutable.lastStatusCode,
                progressStages = progress?.toStoredStages() ?: mutable.progressStages,
                progressRate = progress?.rate ?: mutable.progressRate,
                progressStageIndex = progress?.reachedIndex ?: mutable.progressStageIndex,
            ),
        )

        val persisted = mutableListOf<ChangeEntity>()
        for (c in newChanges.distinctBy { it.type to it.message }) {
            changes.insert(c)
            persisted += c
        }
        return LegPoll(dataChanged, persisted, merges)
    }

    /**
     * Checks whether [snapshot] reports the same consolidation event as two or more other active
     * parcels. If so, returns the COMBINED change for [shipmentId] (null when already recorded)
     * and records a "folded into" note on each involved parcel.
     */
    private suspend fun detectCombination(
        shipmentId: Long,
        snapshot: Snapshot,
        combinedNumber: String,
        now: Long,
    ): ChangeEntity? {
        val otherShipments = shipments.all().filterNot { it.id == shipmentId || it.archived }
        if (otherShipments.size < 2) return null

        // Batch-load each other parcel's first leg and its events to avoid N+1 queries.
        val legsByShipment = legs.allActiveLegs().groupBy { it.shipmentId }
        val firstLegs = otherShipments.mapNotNull { legsByShipment[it.id]?.firstOrNull() }
        if (firstLegs.isEmpty()) return null
        val eventsByLeg = events.eventsForLegs(firstLegs.map { it.id }).groupBy { it.legId }
        val others = firstLegs.mapNotNull { firstLeg ->
            val legEvents = eventsByLeg[firstLeg.id].orEmpty()
            if (legEvents.isEmpty()) return@mapNotNull null
            Snapshot(
                trackingNumber = firstLeg.trackingNumber,
                events = legEvents.map { it.toTrackingEvent() },
            )
        }.associateBy { it.trackingNumber }

        val combined = ChangeLogService.detectCombination(others, snapshot) ?: return null
        for (absorbedNo in combined.mergedFrom) {
            if (FingerprintUtil.normalize(absorbedNo) == combinedNumber) continue
            val absorbed = legs.findByTrackingNumber(absorbedNo) ?: continue
            val absorbedLabel = parcelLabel(absorbed.shipmentId)
            val foldMsg = "$absorbedLabel: Folded into combined parcel ${snapshot.trackingNumber}"
            if (changes.countByMessage(absorbed.shipmentId, "COMBINED", foldMsg) == 0) {
                changes.insert(
                    ChangeEntity(
                        shipmentId = absorbed.shipmentId,
                        type = "COMBINED",
                        message = foldMsg,
                        createdAt = now,
                    ).withDisplay(
                        ChangeDisplay(
                            kind = ChangeDisplay.Kind.FOLDED_INTO,
                            category = ChangeDisplay.Category.TRANSIT,
                            parcel = absorbedLabel,
                            into = snapshot.trackingNumber,
                        ),
                    ),
                )
            }
        }
        val label = parcelLabel(shipmentId)
        val msg = "$label: ${ChangeLogService.humanReadable(combined)}"
        if (changes.countByMessage(shipmentId, "COMBINED", msg) > 0) return null
        return ChangeEntity(
            shipmentId = shipmentId,
            type = "COMBINED",
            message = msg,
            createdAt = now,
        ).withDisplay(
            ChangeDisplay(
                kind = ChangeDisplay.Kind.COMBINED,
                category = ChangeDisplay.Category.TRANSIT,
                parcel = label,
                count = combined.mergedFrom.size,
                into = snapshot.trackingNumber,
            ),
        )
    }

    /** Attaches the in-memory display facts a notification is worded from. */
    private fun ChangeEntity.withDisplay(display: ChangeDisplay): ChangeEntity =
        apply { this.display = display }

    /**
     * Which notification switch a scan belongs to: its normalized code when the carrier sends
     * one, otherwise the wording heuristic the filters were originally built on.
     */
    private fun notificationCategory(
        statusCode: String?,
        description: String?,
    ): ChangeDisplay.Category = when {
        statusCode.equals("DELIVERED", ignoreCase = true) -> ChangeDisplay.Category.DELIVERED
        statusCode?.startsWith("EXCEPTION", ignoreCase = true) == true -> ChangeDisplay.Category.EXCEPTION
        else -> ChangeDisplay.categoryFromWording(description)
    }

    private fun EventEntity.toTrackingEvent() =
        TrackingEvent(trackingNumber, timeMs, description, location, statusCode)

    private fun TrackingLegEntity.toNumberedLeg() = NumberedLeg(
        legId = id,
        shipmentId = shipmentId,
        trackingNumber = trackingNumber,
        aliasNumbers = aliasNumbers,
    )
}

/**
 * Couriers asked about a hand-off number whose format no [CarrierDetector] pattern recognises,
 * in order. Only couriers that issue prefix-less numbers and have a lookup that can say
 * "unknown" belong here; the first one that recognises the number gets the leg.
 */
private val HANDOFF_PROBE_CARRIERS = listOf(Carrier.IMILE)
