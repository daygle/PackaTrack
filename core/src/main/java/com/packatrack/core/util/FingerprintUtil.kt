package com.packatrack.core.util

/**
 * Canonical form of tracking numbers, so numbers typed, pasted or reported by carriers with
 * different spacing, separators or case compare equal.
 */
object FingerprintUtil {

    /** Shortest normalized tracking number PackaTrack accepts. */
    const val MIN_NUMBER_LENGTH = 6

    private val NON_ALNUM = Regex("[^A-Za-z0-9]")

    /** Uppercased alphanumeric-only tracking number used for storage and comparisons. */
    fun normalize(raw: String): String = NON_ALNUM.replace(raw.trim(), "").uppercase()
}
