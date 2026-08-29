package com.packatrack.core.util

import kotlin.math.min

/**
 * Utilities for comparing parcel identities.
 *
 * AliExpress/Cainiao frequently issue a *new* tracking number when a package is
 * transferred between carriers or consolidated into another parcel. To still connect
 * these together we fingerprint each parcel by:
 *  - its (normalized) tracking number suffixes of several lengths, and
 *  - a fuzzy weight/size signature, so a renumbered parcel can be linked even when the
 *    number changed completely but weight stays close.
 */
object FingerprintUtil {

    private val NON_ALNUM = Regex("[^A-Za-z0-9]")

    /** Lowercased alphanumeric-only tracking number used for comparisons. */
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

    /** True when the last [minChars] characters of both numbers match. */
    fun suffixMatch(a: String, b: String, minChars: Int): Boolean =
        commonSuffixLength(a, b) >= minChars

    /**
     * Two weights are considered "same parcel" within the default tolerance
     * (couriers round in steps of 10-50g). Nulls never match.
     */
    fun weightClose(w1: Double?, w2: Double?, toleranceGrams: Double = 30.0): Boolean {
        if (w1 == null || w2 == null) return false
        return kotlin.math.abs(w1 - w2) <= toleranceGrams
    }
}
