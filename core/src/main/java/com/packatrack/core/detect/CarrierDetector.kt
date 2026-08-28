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
    // Australia Post often exposes a 14-digit article number while UBI retains the
    // same identifier with an AP prefix (for example AP00839790702074).
    private val CAINIAO_AP = Regex("^AP[0-9]{14}$", RegexOption.IGNORE_CASE)
    // UBI and Cainiao are two services in the same network; AP article numbers
    // should therefore create both legs in automatic mode. AP does not own this
    // identifier unless Cainiao later reports a distinct Australia Post number.
    private val CAINIAO = Regex("^CN[A-Za-z0-9]{8,20}N?$", RegexOption.IGNORE_CASE)
    private val IMILE = Regex("^IML?[A-Za-z0-9]{6,22}$", RegexOption.IGNORE_CASE)

    /** Aramex shipment numbers are all-digit, typically 10–12 long (best effort). */
    private val ARAMEX = Regex("^[0-9]{10,12}$")
    /** Morning Global uses an MG prefix in the wild (best effort). */
    private val MORNING_GLOBAL = Regex("^MG[A-Za-z0-9]{6,20}$", RegexOption.IGNORE_CASE)

    // Cainiao's Australia Post handoff can be a 21-digit numeric article number
    // without the normal two-letter AU suffix.
    private val AUSPOST_LONG_ARTICLE = Regex("^[0-9]{21}$")

    /** Returns every carrier whose known format matches the number. */
    fun detectAll(trackingNumberRaw: String): List<Carrier> {
        val n = trackingNumberRaw.trim().uppercase()
        if (n.isEmpty()) return emptyList()
        return buildList {
            if (n.endsWith("AU") && AUSPOST_UPU.matches(n)) add(Carrier.AUSTRALIA_POST)
            if (AUSPOST_CONSIGNMENT.matches(n) || AUSPOST_LONG_ARTICLE.matches(n)) add(Carrier.AUSTRALIA_POST)
            if (MORNING_GLOBAL.matches(n)) add(Carrier.MORNING_GLOBAL)
            if (IMILE.matches(n)) add(Carrier.IMILE)
            if (CAINIAO_AP.matches(n)) {
                add(Carrier.UBI_SMART_PARCEL)
                add(Carrier.CAINIAO)
            }
            if (CAINIAO.matches(n)) add(Carrier.UBI_SMART_PARCEL)
            if (AUSPOST_DOMESTIC.matches(n) && !IMILE.matches(n)) add(Carrier.AUSTRALIA_POST)
            if (ARAMEX.matches(n)) add(Carrier.ARAMEX)
        }.distinct()
    }

    /**
     * Returns the best detected [Carrier] for compatibility with existing callers.
     * Use [detectAll] when the UI/API needs to show all possible matches.
     */
    fun detect(trackingNumberRaw: String): Carrier? = detectAll(trackingNumberRaw).firstOrNull()
}
