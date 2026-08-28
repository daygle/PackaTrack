package com.packatrack.core.detect

import com.packatrack.core.model.Carrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarrierDetectorTest {

    @Test fun upuNumberGoesToAuspost() {
        assertEquals(Carrier.AUSTRALIA_POST, CarrierDetector.detect("EV938507560AU"))
        assertEquals(Carrier.AUSTRALIA_POST, CarrierDetector.detect("lp123456789au"))
    }

    @Test fun domesticAuspostNumber() {
        assertEquals(Carrier.AUSTRALIA_POST, CarrierDetector.detect("AB12345678A"))
    }

    @Test fun sevenPrefixConsignmentIsAuspost() {
        assertEquals(Carrier.AUSTRALIA_POST, CarrierDetector.detect("7001234567891"))
    }

    @Test fun cainiaoNumber() {
        assertEquals(Carrier.CAINIAO, CarrierDetector.detect("CNJNR2024050010234N"))
        assertEquals(Carrier.CAINIAO, CarrierDetector.detect("AP00839790702074"))
    }

    @Test fun detectsAllMatchingCarriers() {
        assertEquals(listOf(Carrier.AUSTRALIA_POST), CarrierDetector.detectAll("EV938507560AU"))
        assertEquals(listOf(Carrier.CAINIAO), CarrierDetector.detectAll("AP00839790702074"))
    }

    @Test fun imileNumber() {
        assertEquals(Carrier.IMILE, CarrierDetector.detect("IML00012345678"))
    }

    @Test fun aramexNumericNumber() {
        assertEquals(Carrier.ARAMEX, CarrierDetector.detect("1234567890"))
        assertEquals(Carrier.ARAMEX, CarrierDetector.detect("123456789012"))
    }

    @Test fun morningGlobalNumber() {
        assertEquals(Carrier.MORNING_GLOBAL, CarrierDetector.detect("MG0099887766"))
    }

    @Test fun unknownReturnsNull() {
        assertNull(CarrierDetector.detect(""))
        assertNull(CarrierDetector.detect("HELLOWORLD123"))
    }
}
