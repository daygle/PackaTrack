package com.packatrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierTest {

    private val number = "CNG00100183257245"

    @Test fun webLinksAreTrackingPagesNotApiPayloads() {
        // The Cainiao legs used to link to /global/detail.json - the endpoint the app parses -
        // so the browser showed raw JSON instead of the tracking page.
        Carrier.entries.forEach { carrier ->
            val url = carrier.publicUrl(number)
            assertFalse("$carrier opens an API payload: $url", url.contains("detail.json"))
            assertTrue("$carrier link lost the number: $url", url.contains(number))
        }
    }

    @Test fun cainiaoAndUbiOpenTheCainiaoTrackingPage() {
        assertEquals(
            "https://global.cainiao.com/newDetail.htm?mailNoList=$number&lang=en",
            Carrier.CAINIAO.publicUrl(number),
        )
        assertEquals(
            "https://global.cainiao.com/newDetail.htm?mailNoList=$number&lang=en",
            Carrier.UBI_SMART_PARCEL.publicUrl(number),
        )
    }
}
