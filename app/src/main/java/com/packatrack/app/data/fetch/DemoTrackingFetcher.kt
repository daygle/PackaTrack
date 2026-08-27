package com.packatrack.app.data.fetch

import com.packatrack.core.model.Carrier
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent

/**
 * Offline demo fetcher so PackaTrack works out of the box without API keys.
 *
 * Add these numbers (demo mode ON) to see every feature:
 *  - DEMO600087654321 → Cainiao-style parcel later **renumbered** to AU600087654321,
 *    then out-for-delivery → delivered.
 *  - DEMO111222333    → small parcel consolidated into another shipment.
 *  - CNDEMOCOMBO9X    → combined parcel whose weight equals the sum of both parcels
 *    above — PackaTrack flags "parcels combined".
 */
class DemoTrackingFetcher : TrackingFetcher {

    override suspend fun fetch(
        carrier: Carrier,
        trackingNumber: String,
        stageHint: Int,
    ): Snapshot? = when {
        trackingNumber.contains("600087654321", true) -> ausPostStory(stageHint)
        trackingNumber.contains("111222333", true) -> smallParcelStory(stageHint)
        trackingNumber.contains("COMBO9X", true) -> combinedSnapshot()
        else -> null
    }

    private fun ausPostStory(stage: Int): Snapshot {
        val cnEvents = listOf(
            TrackingEvent("DEMO600087654321", epoch(0), "[Demo] Label created - AliExpress seller, Shenzhen", "Shenzhen, CN", "LABEL_CREATED"),
            TrackingEvent("DEMO600087654321", epoch(1), "[Demo] Departed origin facility", "Guangzhou, CN", "IN_TRANSIT"),
        )
        val auEvents = listOf(
            TrackingEvent("AU600087654321", epoch(2), "[Demo] Received by Australia Post", "Sydney NSW 2000", "IN_TRANSIT"),
            TrackingEvent("AU600087654321", epoch(3), "[Demo] On board for delivery", "Melbourne VIC 3000", "OUT_FOR_DELIVERY"),
            TrackingEvent("AU600087654321", epoch(4), "[Demo] Delivered - signature obtained", "Melbourne VIC 3000", "DELIVERED"),
        )
        return when (stage.coerceIn(0, 4)) {
            0 -> Snapshot(currentFrom(stage), 350.0, null, cnEvents.take(1))
            1 -> Snapshot(currentFrom(stage), 350.0, null, cnEvents)
            2 -> Snapshot(currentFrom(stage), 352.0, null, cnEvents + auEvents.take(1))
            3 -> Snapshot(currentFrom(stage), 352.0, null, cnEvents + auEvents.take(2))
            else -> Snapshot(currentFrom(stage), 352.0, null, cnEvents + auEvents)
        }
    }

    /**
     * Before the handoff the parcel is tracked under its original number; afterwards the
     * app has adopted the Australia Post number, which is what the repository stores.
     */
    private fun currentFrom(stage: Int): String =
        if (stage >= 2) "AU600087654321" else "DEMO600087654321"

    private fun smallParcelStory(stage: Int): Snapshot {
        val events = listOf(
            TrackingEvent("DEMO111222333", epoch(1), "[Demo] Parcel accepted from sender", "Yiwu, CN", "IN_TRANSIT"),
            TrackingEvent("DEMO111222333", epoch(2), "[Demo] In transit to overseas", "Shanghai, CN", "IN_TRANSIT"),
            TrackingEvent("DEMO111222333", epoch(3), "[Demo] Parcel consolidated into a larger shipment at Sydney warehouse", "Sydney NSW 2000", "IN_TRANSIT"),
        )
        val take = (stage + 1).coerceIn(1, events.size)
        return Snapshot("DEMO111222333", 120.0, null, events.take(take))
    }

    /** Weight ≈ sum of the two demo parcels (350 + 120 = 470 g → 480 g, within tolerance). */
    private fun combinedSnapshot() = Snapshot(
        "CNDEMOCOMBO9X",
        480.0,
        null,
        listOf(
            TrackingEvent("CNDEMOCOMBO9X", epoch(4), "[Demo] Combined shipment handed to Australia Post", "Sydney NSW 2000", "IN_TRANSIT"),
            TrackingEvent("CNDEMOCOMBO9X", epoch(5), "[Demo] Delivered", "Canberra ACT 2600", "DELIVERED"),
        ),
    )

    private fun epoch(daysAgo: Int): Long =
        System.currentTimeMillis() - daysAgo.toLong() * 24L * 60L * 60L * 1000L

    companion object {
        fun isDemoNumber(number: String): Boolean = number.contains(
            Regex("DEMO|600087654321|111222333|COMBO9X", RegexOption.IGNORE_CASE),
        )
    }
}
