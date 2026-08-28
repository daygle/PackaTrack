package com.packatrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImileParserTest {

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
}
