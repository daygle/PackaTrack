package com.packatrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintUtilTest {

    @Test fun normalizeStripsSeparatorsAndUppercases() {
        assertEquals("AB123456789AU", FingerprintUtil.normalize("ab-123 456\n789 au"))
    }

    @Test fun commonSuffixLengthCountsFromEnd() {
        assertEquals(12, FingerprintUtil.commonSuffixLength("XX600087654321", "600087654321"))
        assertEquals(0, FingerprintUtil.commonSuffixLength("AAA111", "BBB222"))
    }

    @Test fun suffixMatchRespectsMinimum() {
        assertTrue(FingerprintUtil.suffixMatch("AU1234567890001", "1234567890001", 10))
        assertFalse(FingerprintUtil.suffixMatch("AU1", "1", 10))
    }

    @Test fun weightCloseWithinTolerance() {
        assertTrue(FingerprintUtil.weightClose(500.0, 512.0))
        assertFalse(FingerprintUtil.weightClose(null, 512.0))
        assertFalse(FingerprintUtil.weightClose(500.0, 900.0))
    }
}
