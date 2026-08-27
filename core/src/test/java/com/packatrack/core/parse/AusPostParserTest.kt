package com.packatrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AusPostParserTest {

    private val response = """
    {"queryTrackedResponseItems":[{
      "tracking_id":"EV938507560AU",
      "shipments":[{
        "statusSummary":"Delivered",
        "tracking_events":[
          {"event_date_time":"2026-08-24T07:15:00+10:00","event_type_description":"Delivered",
           "description":"Delivered - signature obtained","location":"MELBOURNE VIC"},
          {"event_date_time":"2026-08-24T05:03:00+10:00","event_type_description":"On board for delivery",
           "description":"With delivery driver","location":"DANDENONG SOUTH VIC"},
          {"event_date_time":"2026-08-22T18:44:10+10:00","event_type_description":"In transit",
           "description":"Processed at facility","location":"BRISBANE QLD"}
        ],
        "articles":[{"event_date_time":"2026-08-20T11:02:00+10:00",
           "event_type_description":"Shipping information received","description":"Article received"}]
      }]}]}
    """.trimIndent()

    @Test fun parsesEventsAndSortsNewestFirst() {
        val snap = AusPostParser.parse(response, "EV938507560AU")
        assertNotNull(snap)
        assertEquals("EV938507560AU", snap!!.trackingNumber)
        assertEquals(4, snap.events.size)
        assertEquals("DELIVERED", snap.events[0].statusCode)
        assertEquals("OUT_FOR_DELIVERY", snap.events[1].statusCode)
        assertEquals("LABEL_CREATED", snap.events[3].statusCode)
        assertNotNull(snap.events[0].timeMs)
    }

    @Test fun errorBodyReturnsNull() {
        val body = """{"error":{"message":"AUTHENTICATION_FAILED"}}"""
        assertNull(AusPostParser.parse(body, "X"))
    }
}
