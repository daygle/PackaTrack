package com.packatrack.core.parse

import com.packatrack.core.model.ShipmentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CainiaoParserTest {

    private val sectionsShape = """
    {"data":{"CNJNR20260812000001N":{
      "status":"DELIVERED",
      "weight": 0.352,
      "sections":[
        {"sectionType":"ORIGIN_COUNTRY","detailList":[
          {"time":"2026-08-12 10:11","desc":"Parcel data processed","city":"Hangzhou","actionCode":"CW_FUNCTION_GOT"},
          {"time":"2026-08-14 06:30","desc":"Departed from first facility","city":"Shanghai"}
        ]},
        {"sectionType":"DESTINATION_COUNTRY","detailList":[
          {"time":"2026-08-25 07:04","desc":"Delivered to letterbox","city":"Sydney NSW","actionCode":"SIGNED_SUCCESS"}
        ]}
      ]}}}
    """.trimIndent()

    private val relatedNumberShape = """
    {"data":{"AP00839790702074":{"status":"IN_TRANSIT","lastMileTrackingNumber":"UBI1234567890",
      "sections":[{"detailList":[{"time":"2026-08-20 09:00","desc":"Accepted"}]}]}}}
    """.trimIndent()

    private val traceNodeShape = """
    {"data":{"CNX1234567890ZZ":{"logisticsTrace":{"traceNodeList":[
       {"time":"2026-08-20 09:00","description":"Arrived at sorting center","location":"Melbourne VIC"},
       {"time":"2026-08-19 21:40","description":"Flight departed Shanghai PVG","location":"Shanghai"}
    ]}}}}
    """.trimIndent()

    @Test fun parsesSectionsShape() {
        val snap = CainiaoParser.parse(sectionsShape)
        assertNotNull(snap)
        assertEquals("CNJNR20260812000001N", snap!!.trackingNumber)
        assertEquals(3, snap.events.size)
        // newest first
        assertTrue(snap.events.first().description.contains("Delivered"))
        assertEquals("DELIVERED", snap.events.first().statusCode)
        // 0.352 kg → 352 g
        assertNotNull(snap.weightGrams)
        assertTrue(kotlin.math.abs(snap.weightGrams!! - 352.0) < 0.01)
    }

    @Test fun extractsRelatedLastMileNumber() {
        val snap = CainiaoParser.parse(relatedNumberShape)
        assertNotNull(snap)
        assertEquals(listOf("UBI1234567890"), snap!!.relatedTrackingNumbers)
    }

    @Test fun parsesTraceNodeListShape() {
        val snap = CainiaoParser.parse(traceNodeShape)
        assertNotNull(snap)
        assertEquals(2, snap!!.events.size)
        assertTrue(snap.events.first().description.startsWith("Arrived at sorting"))
    }

    @Test fun invalidJsonReturnsNull() {
        assertEquals(null, CainiaoParser.parse("{oops"))
    }

    @Test fun statusCodeMapsToNormalizedStatus() {
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.fromCode("DELIVERED"))
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.fromCode("ARRIVED"))
    }
}
