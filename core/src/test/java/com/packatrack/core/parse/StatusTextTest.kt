package com.packatrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wording traps every carrier shares. A case belongs here once, rather than in each parser's
 * test, because Australia Post, Aramex, iMile and Cainiao descriptions all go through
 * [StatusText].
 */
class StatusTextTest {

    private fun check(expected: String?, vararg texts: String?) {
        for (text in texts) assertEquals("\"$text\"", expected, StatusText.map(text))
    }

    @Test fun delivered() = check(
        "DELIVERED",
        "Delivered",
        "Delivered - signature obtained",
        "Delivered to consignee",
        "Parcel signed by recipient",
        "Proof of delivery available",
    )

    @Test fun assignedIsNotSigned() = check(null, "Your order has assigned delivery driver.", "Driver assigned")

    @Test fun failedDeliveriesAreNotDelivered() = check(
        "EXCEPTION",
        "Not delivered - card left",
        "Undeliverable",
        "Delivery failed - address not found",
        "Attempted delivery",
        "Unable to deliver",
        "Returned to sender",
        "Item damaged",
    )

    @Test fun outForDelivery() = check(
        "OUT_FOR_DELIVERY",
        "Out for delivery",
        "Our delivery associate is out for delivery.",
        "On board for delivery",
        "On vehicle for delivery",
    )

    @Test fun awaitingCollection() = check(
        "PICKUP_AVAILABLE",
        "Awaiting collection",
        "Held at post office",
        "Delivered to parcel locker - awaiting collection",
    )

    @Test fun labelCreatedBeforeReceived() = check(
        "LABEL_CREATED",
        "Shipping information received",
        "Label created",
        "Order created",
    )

    @Test fun inTransit() = check(
        "IN_TRANSIT",
        "Received by warehouse",
        "Arrived at sorting center",
        "Processed at facility",
        "In transit",
        "Departed facility",
    )

    @Test fun nothingToSay() = check(null, "Delivery", "", "   ", null)
}
