package com.packatrack.core.util

import kotlin.math.min

/**
 * Utilities for comparing parcel identities.
 *
 * AliExpress/Cainiao frequently issue a *new* tracking number when a package is
 * transferred between carriers or consolidated into another parcel. Numbers are compared in
 * normalized form, and a long shared suffix links a re-issued number to the original.
 */
object FingerprintUtil {

    /** Shortest normalized tracking number PackaTrack accepts. */
    const val MIN_NUMBER_LENGTH = 6

    private val NON_ALNUM = Regex("[^A-Za-z0-9]")

    /** Uppercased alphanumeric-only tracking number used for storage and comparisons. */
    fun normalize(raw: String): String = NON_ALNUM.replace(raw.trim(), "").uppercase()

    /** Longest common suffix length of two normalized tracking numbers. */
    fun commonSuffixLength(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        var len = 0
        val max = min(x.length, y.length)
        while ((len < max) && (x[x.length - 1 - len] == y[y.length - 1 - len])) len++
        return len
    }
}
