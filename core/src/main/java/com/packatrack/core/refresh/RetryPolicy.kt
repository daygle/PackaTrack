package com.packatrack.core.refresh

/**
 * How long to wait before asking a carrier about the same number again.
 *
 * A healthy leg keeps the app's normal cadence - a minute for a manual refresh, ten for background
 * sync - and a single failure does not change that, because most failures are one-off (a carrier
 * timeout, a dropped connection). Repeated failures do back off, doubling per failure, so a carrier
 * that is down or blocking the app is not hammered once a minute for hours. The wait never exceeds
 * [MAX_DELAY_MS], so a parcel that recovers is picked up again without user action.
 */
object RetryPolicy {

    /** Shortest gap between manual refreshes of one leg (the refresh button, pull-to-refresh). */
    const val MANUAL_COOLDOWN_MS = 60_000L

    /** Shortest gap between background syncs of one leg. */
    const val BACKGROUND_COOLDOWN_MS = 10 * 60_000L

    /** Ceiling for the backoff, so a recovered carrier is retried within half an hour. */
    const val MAX_DELAY_MS = 30 * 60_000L

    /** The 2^n factor is clamped here: 2^5 already exceeds [MAX_DELAY_MS] from either base. */
    private const val MAX_SHIFT = 5

    /**
     * @param consecutiveFailures failed polls since the last success, 0 or 1 for the normal cadence.
     * @param manual true for a user-initiated refresh, which is paced far more tightly than a
     *        background sync.
     */
    fun delayMs(consecutiveFailures: Int, manual: Boolean): Long {
        val base = if (manual) MANUAL_COOLDOWN_MS else BACKGROUND_COOLDOWN_MS
        if (consecutiveFailures <= 1) return base
        val factor = 1L shl (consecutiveFailures - 1).coerceAtMost(MAX_SHIFT)
        return minOf(base * factor, MAX_DELAY_MS)
    }
}
