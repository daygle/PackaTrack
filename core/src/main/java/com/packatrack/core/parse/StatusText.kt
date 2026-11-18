package com.packatrack.core.parse

/**
 * Maps a carrier's free-text scan description or status label onto PackaTrack's status codes.
 *
 * Shared by every parser so a wording trap fixed once is fixed for all carriers. Each parser
 * keeps its own carrier-specific signals (Cainiao's action codes, iMile's stage numbers) and
 * falls back to this for text.
 *
 * Order matters - most carriers word a failure using the success word inside it:
 *  - failures first: "Not delivered", "Undeliverable", "Delivery failed" and
 *    "Attempted delivery" all contain "deliver" but mean the parcel did not arrive;
 *  - "signed" only as its own word, so "Driver assigned" is not a delivery;
 *  - label-created phrases before in-transit, so "Shipping information received" is not
 *    read as a parcel received at a depot.
 */
object StatusText {

    fun map(text: String?): String? {
        val t = text?.lowercase()?.trim().orEmpty()
        if (t.isEmpty()) return null
        return when {
            EXCEPTION.any { it in t } -> "EXCEPTION"
            PICKUP.any { it in t } || ("held" in t && "collection" in t) -> "PICKUP_AVAILABLE"
            OUT_FOR_DELIVERY.any { it in t } -> "OUT_FOR_DELIVERY"
            "delivered" in t || "proof of delivery" in t || SIGNED.containsMatchIn(t) -> "DELIVERED"
            LABEL_CREATED.any { it in t } -> "LABEL_CREATED"
            IN_TRANSIT.any { it in t } -> "IN_TRANSIT"
            else -> null
        }
    }

    private val EXCEPTION = listOf(
        "not delivered", "undeliver", "unable to deliver", "could not be delivered",
        "attempted", "fail", "refus", "return", "damage", "lost", "cancel",
    )
    private val PICKUP = listOf("held at post office", "awaiting collection", "ready for collection")
    private val OUT_FOR_DELIVERY = listOf("out for delivery", "on board for delivery", "on vehicle", "dispatcher")
    private val LABEL_CREATED = listOf(
        "shipping information received", "label created", "created", "booked", "manifested",
    )
    private val IN_TRANSIT = listOf(
        "transit", "processed", "arrived", "departed", "sorting", "sorted", "received",
        "picked up", "collected from shipper", "accepted", "flight",
    )
    private val SIGNED = Regex("\\bsigned\\b")
}
