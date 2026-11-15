package com.packatrack.core.model

/**
 * The last payload a carrier's endpoint returned for one tracking number.
 *
 * Kept so a user can see exactly what the app received and parsed, rather than inferring it from
 * the timeline. It is diagnostic data for the current session and is never persisted.
 */
data class RawResponse(
    /** Response body as received, possibly truncated; null when the request produced none. */
    val body: String?,
    /** Wall-clock time of the capture. */
    val capturedAt: Long,
    /** Why the request failed (HTTP status, size cap, network error); null on success. */
    val error: String? = null,
)

/**
 * Map key for [RawResponse]: one entry per courier (by id) and tracking number.
 *
 * Takes the carrier id rather than a [Carrier] so a caller holding a stored leg builds exactly the
 * same key as the fetcher, without repeating the unknown-carrier fallback on either side.
 */
fun rawResponseKey(carrierId: String?, trackingNumber: String): String =
    "${carrierId ?: Carrier.CAINIAO.id}:${trackingNumber.trim().uppercase()}"

/**
 * The payload captured for this leg: the number it is currently polled under, or - when the carrier
 * renumbered it mid-journey - the number it was polled under before, which now sits in the leg's
 * aliases.
 */
fun Map<String, RawResponse>.rawResponseFor(
    carrierId: String?,
    trackingNumber: String,
    aliasNumbers: String = "",
): RawResponse? {
    this[rawResponseKey(carrierId, trackingNumber)]?.let { return it }
    return aliasNumbers.split(',')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { alias -> this[rawResponseKey(carrierId, alias)] }
        .firstOrNull()
}
