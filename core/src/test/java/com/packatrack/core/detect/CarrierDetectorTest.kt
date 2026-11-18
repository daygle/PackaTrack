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
        assertEquals(Carrier.UBI_SMART_PARCEL, CarrierDetector.detect("AP00839790702074"))
    }

    @Test fun cainiaoAlternativeNumberStaysWithCainiao() {
        // Cainiao reports its own "Latest Tracking Number" (realMailNo) for a parcel; that number
        // is another Cainiao id, so it must not be mistaken for a hand-off to a second courier.
        assertEquals(listOf(Carrier.CAINIAO), CarrierDetector.detectAll("CNG00100180303113"))
    }

    @Test fun longAustraliaPostArticleNumber() {
        assertEquals(Carrier.AUSTRALIA_POST, CarrierDetector.detect("36YPH337263201000935107"))
    }

    @Test fun detectsAllMatchingCarriers() {
        assertEquals(listOf(Carrier.AUSTRALIA_POST), CarrierDetector.detectAll("EV938507560AU"))
        assertEquals(
            listOf(Carrier.UBI_SMART_PARCEL, Carrier.CAINIAO),
            CarrierDetector.detectAll("AP00839790702074"),
        )
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

    @Test fun speedPakNumber() {
        assertEquals(Carrier.SPEED_PAK, CarrierDetector.detect("ES10022929840340001010001B0N"))
        assertEquals(Carrier.SPEED_PAK, CarrierDetector.detect("ee10026617741410001030003b0n"))
    }

    @Test fun shortOrOddSpeedPakLikeNumbersDoNotMatch() {
        // Too short to be SpeedPAK and not an 11-character AusPost domestic number.
        assertNull(CarrierDetector.detect("ES12345678"))
        assertNull(CarrierDetector.detect("ZZ10022929840340001010001B0N"))
    }

    @Test fun unknownReturnsNull() {
        assertNull(CarrierDetector.detect(""))
        assertNull(CarrierDetector.detect("HELLOWORLD123"))
    }

    @Test fun unrecognisedCainiaoHandoffIsImile() {
        // Cainiao reports iMile's AU last-mile number with no IM prefix.
        assertEquals(listOf(Carrier.IMILE), CarrierDetector.detectHandoff("3781062090701", Carrier.CAINIAO))
        assertEquals(listOf(Carrier.IMILE), CarrierDetector.detectHandoff("3781062090701", Carrier.UBI_SMART_PARCEL))
    }

    @Test fun recognisedHandoffKeepsItsFormat() {
        assertEquals(listOf(Carrier.AUSTRALIA_POST), CarrierDetector.detectHandoff("EV938507560AU", Carrier.CAINIAO))
        assertEquals(listOf(Carrier.AUSTRALIA_POST), CarrierDetector.detectHandoff("7001234567891", Carrier.UBI_SMART_PARCEL))
    }

    @Test fun unrecognisedHandoffFromOtherCarriersFallsBackToCainiao() {
        assertEquals(listOf(Carrier.CAINIAO), CarrierDetector.detectHandoff("3781062090701", Carrier.AUSTRALIA_POST))
    }
}
