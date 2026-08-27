package com.packatrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AramexParserTest {

    private val response = """
    {"TrackingResults":[{"WaybillNumber":"1234567890","Value":[
        {"UpdateDateTime":"2026-08-26 15:00","UpdateDescription":"Delivered to consignee","UpdateLocation":"Sydney AU"},
        {"UpdateDateTime":"2026-08-26 07:30","UpdateDescription":"Out for delivery","UpdateLocation":"Sydney AU"},
        {"UpdateDateTime":"2026-08-24 22:15","UpdateDescription":"Shipment arrived at facility","UpdateLocation":"Sydney AU"}
    ]}]}
    """.trimIndent()

    @Test fun parsesResultsSortedNewestFirst() {
        val snap = AramexParser.parse(response, "1234567890")
        assertNotNull(snap)
        assertEquals("1234567890", snap!!.trackingNumber)
        assertEquals(3, snap.events.size)
        assertEquals("DELIVERED", snap.events[0].statusCode)
        assertEquals("OUT_FOR_DELIVERY", snap.events[1].statusCode)
        assertEquals("IN_TRANSIT", snap.events[2].statusCode)
    }

    @Test fun errorOrEmptyBodyReturnsNull() {
        assertNull(AramexParser.parse("""{"HasErrors":true,"Notifications":["not found"]}""", "1"))
        assertNull(AramexParser.parse("<html>not json</html>", "1"))
    }
}
