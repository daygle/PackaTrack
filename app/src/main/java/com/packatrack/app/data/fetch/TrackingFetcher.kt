package com.packatrack.app.data.fetch

import com.packatrack.core.model.Carrier
import com.packatrack.core.model.Snapshot

/**
 * Fetches a tracking snapshot for one number from its carrier.
 *
 * [stageHint] lets offline/demo fetchers replay progressive stories; HTTP fetchers ignore it.
 */
fun interface TrackingFetcher {
    suspend fun fetch(carrier: Carrier, trackingNumber: String, stageHint: Int): Snapshot?
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
        .callTimeout(java.time.Duration.ofSeconds(15))
        .build()

    override suspend fun fetch(
        carrier: Carrier,
        trackingNumber: String,
        stageHint: Int,
    ): Snapshot? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            when (carrier) {
                Carrier.CAINIAO -> fetchCainiao(trackingNumber)
                Carrier.AUSTRALIA_POST -> fetchAusPost(trackingNumber)
                Carrier.IMILE -> fetchImile(trackingNumber)
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
        return client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }

    private fun fetchCainiao(number: String): Snapshot? {
        val body = get(
            "https://global.cainiao.com/global/detail.json?mailNos=$number&lang=en",
            mapOf("Referer" to "https://global.cainiao.com/newDetail.htm?mailNos=$number"),
        ) ?: return null
        return com.packatrack.core.parse.CainiaoParser.parse(body)
    }

    private fun fetchAusPost(number: String): Snapshot? {
        val key = ausPostKey()?.takeIf { it.isNotBlank() } ?: return null
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
            if (snap != null) return snap
        }
        return null
    }
}
