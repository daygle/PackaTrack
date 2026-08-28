package com.packatrack.app.data.fetch

import com.packatrack.core.model.Carrier
import com.packatrack.core.model.Snapshot

/**
 * Fetches a tracking snapshot for one number from its carrier.
 *
 * @param pollCount is reserved for fetchers that need polling context; HTTP fetchers ignore it.
 */
fun interface TrackingFetcher {
    suspend fun fetch(carrier: Carrier, trackingNumber: String, pollCount: Int): Snapshot?
}

/**
 * Live HTTP fetchers built on each carrier's public endpoint.
 *
 * - Cainiao: public global detail JSON (no key).
 * - Australia Post: official v2 track API — needs a free AUTH-KEY in Settings.
 * - iMile: customer-facing endpoint (best effort; may require updating if iMile changes it).
 */
class HttpTrackingFetcher(
    private val ausPostKey: () -> String?,
) : TrackingFetcher {

    private val client = okhttp3.OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(
        carrier: Carrier,
        trackingNumber: String,
        pollCount: Int,
    ): Snapshot? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            when (carrier) {
                Carrier.UBI_SMART_PARCEL,
                Carrier.CAINIAO -> fetchCainiao(trackingNumber)
                Carrier.AUSTRALIA_POST -> fetchAusPost(trackingNumber)
                Carrier.IMILE -> fetchImile(trackingNumber)
                Carrier.ARAMEX -> fetchAramex(trackingNumber)
                // No usable public scan endpoint; the parcel stays visible and the
                // "Open on carrier website" link still works.
                Carrier.MORNING_GLOBAL -> null
            }
        }.getOrNull()
    }

    private fun get(url: String, headers: Map<String, String>): String? {
        val builder = okhttp3.Request.Builder().url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36",
            )
        headers.forEach { (k, v) -> builder.header(k, v) }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body.string()
                android.util.Log.d("TrackingFetcher", "GET $url → ${resp.code} (${body.length} bytes)")
                if (!resp.isSuccessful) {
                    android.util.Log.w("TrackingFetcher", "HTTP ${resp.code} for $url")
                    return@use null
                }
                body
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackingFetcher", "Failed to fetch $url", e)
            null
        }
    }

    private fun fetchCainiao(number: String): Snapshot? {
        // Try the standard JSON endpoint first
        val body = get(
            "https://global.cainiao.com/global/detail.json?mailNos=$number&lang=en",
            mapOf(
                "Referer" to "https://global.cainiao.com/newDetail.htm?mailNos=$number",
                "Accept" to "application/json",
            ),
        )
        if (body != null) {
            android.util.Log.d("TrackingFetcher", "Cainiao response: ${body.take(500)}")
            // Check for CAPTCHA or rate limiting
            if (body.contains("captcha", ignoreCase = true) || body.contains("verify", ignoreCase = true)) {
                android.util.Log.w("TrackingFetcher", "Cainiao returned CAPTCHA/verify page for $number")
                return null
            }
            val snap = com.packatrack.core.parse.CainiaoParser.parse(body)
            if (snap != null) {
                android.util.Log.d("TrackingFetcher", "Parsed ${snap.events.size} events from Cainiao")
                return snap
            }
        }
        // Fallback: try the newer API endpoint that supports otherMailNoList
        val body2 = get(
            "https://global.cainiao.com/global/detail.json?mailNoList=$number&otherMailNoList=&lang=en",
            mapOf(
                "Referer" to "https://global.cainiao.com/newDetail.htm?mailNoList=$number&otherMailNoList=",
                "Accept" to "application/json",
            ),
        )
        if (body2 != null) {
            android.util.Log.d("TrackingFetcher", "Cainiao fallback response: ${body2.take(500)}")
            return com.packatrack.core.parse.CainiaoParser.parse(body2)
        }
        android.util.Log.w("TrackingFetcher", "All Cainiao endpoints failed for $number")
        return null
    }

    private fun fetchAusPost(number: String): Snapshot? {
        val key = ausPostKey()?.takeIf { it.isNotBlank() }
        if (key == null) {
            android.util.Log.w("TrackingFetcher", "AusPost API key not configured, skipping")
            return null
        }
        val body = get(
            "https://digitalapi.auspost.com.au/v2/postage/track/events?q=$number",
            mapOf("AUTH-KEY" to key, "Accept" to "application/json"),
        ) ?: return null
        return com.packatrack.core.parse.AusPostParser.parse(body, number)
    }

    private fun fetchImile(number: String): Snapshot? {
        val candidates = listOf(
            "https://customer.track.imile.com/api/open/tracking/query?trackingNumbers=$number",
            "https://www.imile.com/api/track?trackingNumbers=$number&lang=en",
        )
        for (url in candidates) {
            val body = get(url, emptyMap()) ?: continue
            val snap = com.packatrack.core.parse.ImileParser.parse(body, number)
            if (snap != null) {
                return snap
            }
        }
        return null
    }

    /** Best-effort: Aramex's public shipment-tracking endpoints; graceful null when unreachable. */
    private fun fetchAramex(number: String): Snapshot? {
        val candidates = listOf(
            "https://www.aramex.com/api/tracking/gettrackingresults?shipmentNumber=$number",
            "https://tracking.aramex.com/api/shipments/track?ShipmentNumber=$number",
        )
        for (url in candidates) {
            val body = get(url, mapOf("Accept" to "application/json")) ?: continue
            val snap = com.packatrack.core.parse.AramexParser.parse(body, number)
            if (snap != null) return snap
        }
        return null
    }
}
