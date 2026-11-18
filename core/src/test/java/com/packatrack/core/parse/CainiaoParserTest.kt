package com.packatrack.core.parse

import com.packatrack.core.model.CarrierProgress
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

    // The warehouse phase of a live UBI Smart Parcel response: five scans, no city/country
    // keys at all (the facility rides in standerdDesc's bracketed prefix), and CW_/PU_ codes.
    private val warehouseShape = """
    {"success":true,"module":[{"mailNo":"AP00100193611525","originCountry":"Mainland China",
      "destCountry":"Australia","status":"DELIVERING","statusDesc":"Delivering",
      "mailNoSource":"OTHER","daysNumber":"2\tday(s)",
      "processInfo":{"progressStatus":"NORMAL","progressRate":0.2222222222222222,"type":"OTHER",
        "progressPointList":[{"pointName":"Mainland China","light":true},{"pointName":"Australia"},{"pointName":"Delivered"}]},
      "detailList":[
        {"time":1794706248000,"timeStr":"2026-11-15 09:30:48","desc":"Received by warehouse","standerdDesc":"Received by warehouse","descTitle":"Carrier note:","timeZone":"GMT+8","actionCode":"CW_INBOUND"},
        {"time":1794704506000,"timeStr":"2026-11-15 09:01:46","desc":"Arrived at warehouse","standerdDesc":"Arrived at warehouse","actionCode":"CW_SIGN_IN_SUCCESS"},
        {"time":1794693101000,"timeStr":"2026-11-15 05:51:41","desc":"Outbound in sorting center","standerdDesc":"[Shatian Town] Departed from sorting center","actionCode":"SC_OUTBOUND_SUCCESS"},
        {"time":1794641249000,"timeStr":"2026-11-14 15:27:29","desc":"Inbound in sorting center","standerdDesc":"[Shatian Town] Processing at sorting center","actionCode":"SC_INBOUND_SUCCESS"},
        {"time":1794633051000,"timeStr":"2026-11-14 13:10:51","desc":"Accepted by carrier","standerdDesc":"Received by logistics company","actionCode":"PU_PICKUP_SUCCESS"}
      ]}]}
    """.trimIndent()

    @Test fun parsesWarehousePhaseScans() {
        val snap = CainiaoParser.parse(warehouseShape)
        assertNotNull(snap)
        assertEquals("AP00100193611525", snap!!.trackingNumber)
        assertEquals(5, snap.events.size)
        assertTrue(snap.events.first().description.startsWith("Received by warehouse"))
    }

    @Test fun facilityTownIsTakenFromStandardDescBrackets() {
        val snap = CainiaoParser.parse(warehouseShape)!!
        // The payload has no city key, so the town only exists in standerdDesc.
        val outbound = snap.events.first { it.description.startsWith("Outbound in sorting") }
        assertEquals("Shatian Town", outbound.location)
        // A scan whose standerdDesc has no bracket must not invent a location.
        val received = snap.events.first { it.description.startsWith("Received by warehouse") }
        assertEquals(null, received.location)
    }

    @Test fun warehouseAndPickupScansAreInTransit() {
        val snap = CainiaoParser.parse(warehouseShape)!!
        // Every scan in this phase is movement. An unmapped code falls through as the raw
        // actionCode, which the UI has no label for and renders as a grey dot.
        assertEquals(listOf("IN_TRANSIT"), snap.events.mapNotNull { it.statusCode }.distinct())
    }

    // UBI Smart Parcel and Cainiao are one network behind one parser, so a Cainiao-numbered copy
    // of this payload must come out identical. This is the CI guard that the shared-parser fixes
    // (CW_/PU_ codes, the facility town, journey stages) reach both brands, not just UBI.
    @Test fun cainiaoNumberGetsTheSameFixesAsUbi() {
        val ubi = CainiaoParser.parse(warehouseShape)!!
        val cainiao = CainiaoParser.parse(
            warehouseShape.replace("AP00100193611525", "CNG00100183257245"),
        )!!
        assertEquals("CNG00100183257245", cainiao.trackingNumber)
        assertEquals(5, cainiao.events.size)
        // Identical scans, codes and towns; only the number the carrier reports differs.
        assertEquals(
            ubi.events.map { Triple(it.description, it.timeMs, it.statusCode) },
            cainiao.events.map { Triple(it.description, it.timeMs, it.statusCode) },
        )
        assertEquals(ubi.events.map { it.location }, cainiao.events.map { it.location })
        assertEquals(ubi.progress, cainiao.progress)
        assertEquals(listOf("IN_TRANSIT"), cainiao.events.mapNotNull { it.statusCode }.distinct())
    }

    @Test fun warehousePrefixDoesNotReadAsDelivered() {
        assertEquals("IN_TRANSIT", CainiaoParser.mapCode("CW_INBOUND"))
        // CW_SIGN_IN_SUCCESS is a warehouse stock-in, not a delivery signature.
        assertEquals("IN_TRANSIT", CainiaoParser.mapCode("CW_SIGN_IN_SUCCESS"))
        assertEquals("IN_TRANSIT", CainiaoParser.mapCode("PU_PICKUP_SUCCESS"))
        // Failures still win over the new prefixes.
        assertEquals("EXCEPTION", CainiaoParser.mapCode("PU_PICKUP_FAIL"))
        assertEquals("DELIVERED", CainiaoParser.mapCode("CW_SIGNED"))
    }

    @Test fun extractsCarrierJourneyStagesAndProgress() {
        val snap = CainiaoParser.parse(warehouseShape)!!
        val progress = snap.progress
        assertNotNull(progress)
        assertEquals(0.2222222222222222, progress!!.rate!!, 0.0001)
        // The route the carrier reports, and the one stage it has lit so far.
        assertEquals(listOf("Mainland China", "Australia", "Delivered"), progress.stages.map { it.name })
        assertEquals(listOf(true, false, false), progress.stages.map { it.reached })
        assertEquals(0, progress.reachedIndex)
        // The leg row stores the stages; the detail screen must get the same thing back.
        assertEquals(
            progress,
            CarrierProgress.fromStored(progress.toStoredStages(), progress.rate, progress.reachedIndex),
        )
    }

    @Test fun missingProcessInfoLeavesProgressNull() {
        // Older shapes carry no processInfo, so the detail screen keeps its own steps.
        assertEquals(null, CainiaoParser.parse(moduleShape)!!.progress)
        assertEquals(null, CarrierProgress.fromStored("", null, -1))
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

    @Test fun plainTextDescriptionsUseTheSharedWording() {
        // No action code, only the description: the shared wording rules decide, and an
        // unknown code is still kept as it is.
        assertEquals("IN_TRANSIT", CainiaoParser.mapCode("Arrived at sorting center"))
        assertEquals("EXCEPTION", CainiaoParser.mapCode("Delivery attempted - customer not home"))
        assertEquals("GTMS_ASSIGNED", CainiaoParser.mapCode("GTMS_ASSIGNED"))
    }

    @Test
    fun `Alibaba punish page is a bot check, not tracking data`() {
        // Head of the interstitial Cainiao served instead of detail.json (payload trimmed).
        val punish = """
            <!DOCTYPE html><html><head>
            <script charset="utf-8" src="//g.alicdn.com/AWSC/CAPTCHA/0.0.1/awsc.js" crossorigin></script>
            <link rel="stylesheet" href="//g.alicdn.com/bsop-static/sufei-punish/0.1.130/build/main.css">
            </head><body><punish-component /></body></html>
        """.trimIndent()
        assertEquals(null, CainiaoParser.parse(punish))
        assertTrue(CainiaoParser.isBotCheck(punish))
        assertTrue(!CainiaoParser.isBotCheck("""{"module":[]}"""))
    }
}
