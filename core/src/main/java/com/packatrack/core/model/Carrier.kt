package com.packatrack.core.model

/**
 * Canonical carriers supported by PackaTrack.
 *
 * AliExpress parcels typically start with Cainiao (UBI Smart Parcel / Global) and are
 * handed to the destination-country carrier for final delivery.
 */
enum class Carrier(
    val id: String,
    val displayName: String,
    /** Public web tracking page used by the in-app link button. */
    val webUrlTemplate: String,
) {
    AUSTRALIA_POST(
        id = "australia_post",
        displayName = "Australia Post",
        webUrlTemplate = "https://auspost.com.au/mypost/track/details/%s",
    ),
    CAINIAO(
        id = "cainiao",
        displayName = "Cainiao UBI Smart Parcel",
        webUrlTemplate = "https://global.cainiao.com/global/detail.json?mailNos=%s&lang=en",
    ),
    IMILE(
        id = "imile",
        displayName = "iMile Delivery",
        webUrlTemplate = "https://www.imile.com/track?trackingNumbers=%s&lang=en",
    );

    companion object {
        fun fromId(id: String?): Carrier? = entries.firstOrNull { it.id == id }
    }

    fun publicUrl(trackingNumber: String): String = webUrlTemplate.format(trackingNumber)
}
