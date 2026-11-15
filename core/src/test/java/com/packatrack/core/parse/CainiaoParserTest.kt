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
    {"data":{"AP00839790702074":{"status":"IN_TRANSIT","latestTrackingNumber":"36YPH337263201000935107","lastMileTrackingNumber":"UBI1234567890",
      "sections":[{"detailList":[{"time":"2026-08-20 09:00","desc":"Accepted"}]}]}}}
    """.trimIndent()

    private val traceNodeShape = """
    {"data":{"CNX1234567890ZZ":{"logisticsTrace":{"traceNodeList":[
       {"time":"2026-08-20 09:00","description":"Arrived at sorting center","location":"Melbourne VIC"},
       {"time":"2026-08-19 21:40","description":"Flight departed Shanghai PVG","location":"Shanghai"}
    ]}}}}
    """.trimIndent()

    // The shape the live global endpoint returns today: a top-level "module" array whose
    // elements carry a flat "detailList" (time as epoch millis, plus a formatted timeStr).
    private val moduleShape = """
    {"success":true,"module":[{
      "mailNo":"LP00432432432CN","status":"TRANSIT","statusDesc":"In transit",
      "detailList":[
        {"time":1755680400000,"timeStr":"2026-08-20 09:00:00","desc":"Arrived at sorting centre","standerdDesc":"Arrival","actionCode":"GWMS_ACCEPT"},
        {"time":1755594000000,"timeStr":"2026-08-19 09:00:00","desc":"Order information received","standerdDesc":"Accepted","actionCode":"GTMS_ACCEPT"}
      ]}]}
    """.trimIndent()

    @Test fun parsesModuleShape() {
        val snap = CainiaoParser.parse(moduleShape)
        assertNotNull(snap)
        assertEquals("LP00432432432CN", snap!!.trackingNumber)
        assertEquals(2, snap.events.size)
        // newest first, and the epoch-millis timestamp is parsed
        assertTrue(snap.events.first().description.startsWith("Arrived at sorting"))
        assertEquals(1755680400000L, snap.events.first().timeMs)
    }

    // A real UBI/Cainiao hand-off response: the parcel is tracked under an AP-article number,
    // and the downstream last-mile number is surfaced in realMailNo (labelled) and copyRealMailNo.
    private val handoffShape = """
    {"success":true,"module":[{
      "mailNo":"AP00839790702074",
      "realMailNo":"Latest Tracking Number:\t36YPH337263201000935107",
      "status":"DELIVERING","statusDesc":"Delivering",
      "detailList":[
        {"time":1787819700000,"timeStr":"2026-08-27 16:35:00","desc":"EXPORT CUSTOMS CLEARED","standerdDesc":"Leaving from departure country/region","actionCode":"LH_HO_AIRLINE"},
        {"time":1787663157906,"timeStr":"2026-08-25 21:05:57","desc":"Order received successfully","actionCode":"GWMS_ACCEPT"}
      ],
      "copyRealMailNo":"36YPH337263201000935107"}]}
    """.trimIndent()

    @Test fun extractsHandoffLastMileNumber() {
        val snap = CainiaoParser.parse(handoffShape)
        assertNotNull(snap)
        assertEquals("AP00839790702074", snap!!.trackingNumber)
        assertEquals(2, snap.events.size)
        // The downstream courier number is picked up so a new leg can be created for it.
        assertTrue(snap.relatedTrackingNumbers.contains("36YPH337263201000935107"))
    }

    // Live response where the reported "Latest Tracking Number" is another Cainiao id rather
    // than a hand-off: CNG00100180303113 belongs to the same carrier as CNG00100183257245.
    private val sameCarrierRealMailNoShape = """
    {"module":[{"mailNo":"CNG00100183257245","realMailNo":"Latest Tracking Number:\tCNG00100180303113",
      "status":"DELIVERING","statusDesc":"Delivering","copyRealMailNo":"CNG00100180303113",
      "detailList":[
        {"time":1794669621000,"timeStr":"2026-11-14 23:20:21","desc":"Outbound in sorting center","standerdDesc":"[Jiulong Town] Departed from sorting center","actionCode":"SC_OUTBOUND_SUCCESS"},
        {"time":1794667148000,"timeStr":"2026-11-14 22:39:08","desc":"Inbound in sorting center","standerdDesc":"[Jiulong Town] Processing at sorting center","actionCode":"SC_INBOUND_SUCCESS"},
        {"time":1794401618000,"timeStr":"2026-11-11 20:53:38","desc":"Accepted by carrier","standerdDesc":"Received by logistics company","actionCode":"PU_PICKUP_SUCCESS"}
      ]}],"success":true}
    """.trimIndent()

    @Test fun realMailNoIsSurfacedAsRelatedNumber() {
        val snap = CainiaoParser.parse(sameCarrierRealMailNoShape)
        assertNotNull(snap)
        assertEquals("CNG00100183257245", snap!!.trackingNumber)
        assertEquals(3, snap.events.size)
        // Surfaced once (realMailNo and copyRealMailNo agree), for the repository to weigh up; the
        // same-carrier check there is what stops it becoming a duplicate leg.
        assertEquals(listOf("CNG00100180303113"), snap.relatedTrackingNumbers)
    }

    @Test fun parsesSectionsShape() {
        val snap = CainiaoParser.parse(sectionsShape)
        assertNotNull(snap)
        assertEquals("CNJNR20260812000001N", snap!!.trackingNumber)
        assertEquals(3, snap.events.size)
        // newest first
        assertTrue(snap.events.first().description.contains("Delivered"))
        assertEquals("DELIVERED", snap.events.first().statusCode)
    }

    @Test fun extractsRelatedLastMileNumber() {
        val snap = CainiaoParser.parse(relatedNumberShape)
        assertNotNull(snap)
        assertEquals(listOf("36YPH337263201000935107", "UBI1234567890"), snap!!.relatedTrackingNumbers)
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

    @Test fun deliverySubstringsDoNotMapToDelivered() {
        // "DELIVER" is a substring of these, but none of them means the parcel arrived.
        assertEquals("OUT_FOR_DELIVERY", CainiaoParser.mapCode("OUT_FOR_DELIVERY"))
        assertEquals("OUT_FOR_DELIVERY", CainiaoParser.mapCode("Out for delivery"))
        assertEquals("OUT_FOR_DELIVERY", CainiaoParser.mapCode("GTMS_DELIVERING"))
        assertEquals("EXCEPTION", CainiaoParser.mapCode("DELIVERY_FAILED"))
        // "UNDELIVERABLE" contains DELIVER but must never read as delivered.
        assertEquals("EXCEPTION", CainiaoParser.mapCode("UNDELIVERABLE"))
        assertEquals("EXCEPTION", CainiaoParser.mapCode("Package undeliverable - returned"))
        assertEquals("EXCEPTION", CainiaoParser.mapCode("NOT_DELIVERED"))
        assertEquals("GTMS_ASSIGNED", CainiaoParser.mapCode("GTMS_ASSIGNED"))
        assertEquals("DELIVERED", CainiaoParser.mapCode("GTMS_SIGNED"))
        assertEquals("DELIVERED", CainiaoParser.mapCode("SIGNED_SUCCESS"))
        assertEquals("IN_TRANSIT", CainiaoParser.mapCode("GWMS_ACCEPT"))
    }

    @Test fun statusCodeMapsToNormalizedStatus() {
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.fromCode("DELIVERED"))
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.fromCode("ARRIVED"))
    }
}
