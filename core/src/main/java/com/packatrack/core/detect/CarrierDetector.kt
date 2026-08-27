package com.packatrack.core.detect

import com.packatrack.core.model.Carrier

/**
 * Detects which carrier owns a tracking number from its format alone.
 *
 * Patterns follow each carrier's published/public number formats:
 *  - Australia Post: classic UPU two letters + 9 digits + "AU"; and domestic-style
 *    alphanumeric numbers such as "AA123456789A" or the long 7-prefixed consignment ids.
 *  - Cainiao (UBI Smart Parcel / Global): typically "CN" + digits/letters + "CN"
 *    (e.g. CNConn..., CNGR...) or other prefixes under Cainiao's global network.
 *  - iMile: usually starts with "IM" or "IML" followed by alphanumerics.
 */
object CarrierDetector {

    private val AUSPOST_UPU = Regex("^[A-Z]{2}[0-9]{9}AU$", RegexOption.IGNORE_CASE)
    private val AUSPOST_DOMESTIC = Regex("^[A-Z]{2}[0-9]{8}[0-9A-Z]$", RegexOption.IGNORE_CASE)
    private val AUSPOST_CONSIGNMENT = Regex("^7[0-9]{12}$")
    private val CAINIAO = Regex("^CN[A-Za-z0-9]{8,20}N?$", RegexOption.IGNORE_CASE)
    private val IMILE = Regex("^IM[L]?[A-Za-z0-9]{6,22}$", RegexOption.IGNORE_CASE)

    /**
     * Returns the detected [Carrier] or null if ambiguous/unknown.
     * More specific patterns are checked before looser ones.
     */
    fun detect(trackingNumberRaw: String): Carrier? {
        val n = trackingNumberRaw.trim().uppercase()
        if (n.isEmpty()) return null
        return when {
            n.endsWith("AU") && AUSPOST_UPU.matches(n) -> Carrier.AUSTRALIA_POST
            AUSPOST_CONSIGNMENT.matches(n) -> Carrier.AUSTRALIA_POST
            IMILE.matches(n) -> Carrier.IMILE
            CAINIAO.matches(n) -> Carrier.CAINIAO
            AUSPOST_DOMESTIC.matches(n) && !IMILE.matches(n) -> Carrier.AUSTRALIA_POST
            else -> null
        }
    }
}
