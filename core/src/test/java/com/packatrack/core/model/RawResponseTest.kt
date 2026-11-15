package com.packatrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawResponseTest {

    private fun response(body: String) = RawResponse(body = body, capturedAt = 0L)

    @Test fun keyIsScopedByCarrierAndIgnoresNumberCase() {
        assertEquals("${Carrier.CAINIAO.id}:CNG001", rawResponseKey(Carrier.CAINIAO.id, " cng001 "))
        // The same number under another courier is a different entry: each leg has its own request.
        assertEquals(
            "${Carrier.UBI_SMART_PARCEL.id}:CNG001",
            rawResponseKey(Carrier.UBI_SMART_PARCEL.id, "CNG001"),
        )
        // An unknown or missing carrier id falls back to the same carrier the fetcher would use.
        assertEquals(
            rawResponseKey(Carrier.CAINIAO.id, "CNG001"),
            rawResponseKey(null, "CNG001"),
        )
    }

    @Test fun responseIsFoundUnderTheCurrentNumber() {
        val responses = mapOf(rawResponseKey(Carrier.CAINIAO.id, "CNG002") to response("current"))
        assertEquals("current", responses.rawResponseFor(Carrier.CAINIAO.id, "CNG002")?.body)
    }

    @Test fun responseIsFoundUnderAnAliasAfterRenumbering() {
        // The leg was polled as CNG001, then the carrier reissued CNG002 and the old number moved
        // into the aliases; the captured payload is still the one to show.
        val responses = mapOf(rawResponseKey(Carrier.CAINIAO.id, "CNG001") to response("previous"))
        val found = responses.rawResponseFor(
            carrierId = Carrier.CAINIAO.id,
            trackingNumber = "CNG002",
            aliasNumbers = "CNG001,CNG000",
        )
        assertEquals("previous", found?.body)
    }

    @Test fun nothingCapturedReturnsNull() {
        assertNull(emptyMap<String, RawResponse>().rawResponseFor(Carrier.CAINIAO.id, "CNG002", ""))
        assertNull(emptyMap<String, RawResponse>().rawResponseFor(Carrier.CAINIAO.id, "CNG002", ", ,"))
    }
}
