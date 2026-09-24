package com.packatrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImileParserTest {

    // Legacy envelope ({"code","data","records"}) as documented by the old customer track API.
    private val response = """
    {"code":"200","success":true,"data":{
       "waybillNo":"IML00098765432",
       "weight":"2.4",
       "records":[
         {"time":"2026-08-26 14:31","status":"Delivered","content":"Parcel signed by GLEN",
          "location":"Perth WA"},
         {"time":"2026-08-26 08:05","status":"On vehicle","content":"Out for delivery","location":"Perth WA"},
         {"time":"2026-08-20 23:10","status":"In transit","content":"Arrived in destination country","location":"Perth AU"}
       ]}}
    """.trimIndent()

    // Live envelope served by imile.com's signed query endpoint
    // (/saastms/mobileWeb/track/query), mirroring the shape of their own web tracker.
    private val liveResponse = """
    {"status":"success","resultCode":"","message":"Successful operation!","resultObject":{
       "waybillNo":"IML00098765432",
       "sendSite":"Shenzhen",
       "dispatchStation":"Dubai",
       "country":"ARE",
       "trackInfos":[
         {"content":"【Dubai】Delivered.","trackStage":2060,"trackStageTx":"Delivered",
          "time":"2026-08-26 14:31:00","operateStationName":"Dubai"},
         {"content":"【Dubai】Out for delivery.","trackStage":2050,"trackStageTx":"Out for Delivery",
          "time":"2026-08-26 08:30:00","operateStationName":"Dubai"},
         {"content":"Order Submitted.","trackStage":1001,"trackStageTx":"Order Creation",
          "time":"2026-08-20 23:10:00","operateStationName":null}
       ]}}
    """.trimIndent()

    @Test fun parsesRecordsSortedNewestFirst() {
        val snap = ImileParser.parse(response, "IML00098765432")
        assertNotNull(snap)
        assertEquals("IML00098765432", snap!!.trackingNumber)
        assertEquals(3, snap.events.size)
        assertEquals("DELIVERED", snap.events[0].statusCode)
        assertEquals("OUT_FOR_DELIVERY", snap.events[1].statusCode)
    }

    @Test fun unauthenticatedResponseReturnsNull() {
        val body = """{"code":"500","success":false,"message":"waybill not found"}"""
        assertNull(ImileParser.parse(body, "IML1"))
    }

    @Test fun parsesLiveTrackInfosEnvelope() {
        val snap = ImileParser.parse(liveResponse, "IML00098765432")
        assertNotNull(snap)
        assertEquals("IML00098765432", snap!!.trackingNumber)
        assertEquals(3, snap.events.size)
        // newest first, status derived from trackStageTx
        assertEquals("DELIVERED", snap.events[0].statusCode)
        assertEquals("OUT_FOR_DELIVERY", snap.events[1].statusCode)
        assertEquals("Dubai", snap.events[0].location)
        // seconds-precision timestamp parsed as UTC epoch millis
        assertEquals(1787754660000L, snap.events[0].timeMs)
        // unmappable stage text falls back to iMile's numeric trackStage code
        assertEquals("LABEL_CREATED", snap.events[2].statusCode)
        // null operateStationName must not become a "null" location string
        assertNull(snap.events[2].location)
    }

    @Test fun liveErrorResponseReturnsNull() {
        val body =
            """{"status":"error","resultObject":null,"message":"Tracking details not found. Please check the number and try again."}"""
        assertNull(ImileParser.parse(body, "IML1"))
    }

    @Test fun liveSuccessWithoutPayloadReturnsNull() {
        val body = """{"status":"success","resultCode":"","resultObject":null,"message":"Successful operation!"}"""
        assertNull(ImileParser.parse(body, "IML1"))
    }

    @Test fun liveEnvelopeWithUntrackedWaybillStillResolves() {
        // iMile resolved the waybill but has no scans yet: keep an empty timeline instead
        // of reporting the fetch as failed.
        val body = """{"status":"success","resultObject":{"waybillNo":"IML1","trackInfos":[]},"message":"Successful operation!"}"""
        val snap = ImileParser.parse(body, "IML1")
        assertNotNull(snap)
        assertEquals(0, snap!!.events.size)
        assertEquals("IML1", snap.trackingNumber)
    }
}
