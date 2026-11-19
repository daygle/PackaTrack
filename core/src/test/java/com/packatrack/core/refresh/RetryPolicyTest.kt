package com.packatrack.core.refresh

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {

    @Test fun `a healthy leg keeps the normal cooldown`() {
        assertEquals(60_000L, RetryPolicy.delayMs(consecutiveFailures = 0, manual = true))
        assertEquals(10 * 60_000L, RetryPolicy.delayMs(consecutiveFailures = 0, manual = false))
    }

    @Test fun `a single failure does not slow the normal cadence`() {
        assertEquals(60_000L, RetryPolicy.delayMs(consecutiveFailures = 1, manual = true))
        assertEquals(10 * 60_000L, RetryPolicy.delayMs(consecutiveFailures = 1, manual = false))
    }

    @Test fun `repeated failures back off and stop at the cap`() {
        assertEquals(2 * 60_000L, RetryPolicy.delayMs(consecutiveFailures = 2, manual = true))
        assertEquals(4 * 60_000L, RetryPolicy.delayMs(consecutiveFailures = 3, manual = true))
        // 2^7 from a minute would be over two hours; the cap holds it to half an hour.
        assertEquals(RetryPolicy.MAX_DELAY_MS, RetryPolicy.delayMs(consecutiveFailures = 8, manual = true))
        assertEquals(RetryPolicy.MAX_DELAY_MS, RetryPolicy.delayMs(consecutiveFailures = 6, manual = false))
    }
}
