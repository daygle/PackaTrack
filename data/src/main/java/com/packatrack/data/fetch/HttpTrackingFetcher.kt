package com.packatrack.data.fetch

import android.util.Log
import com.packatrack.core.model.Carrier
import com.packatrack.core.model.RawResponse
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.rawResponseKey
import com.packatrack.core.parse.AramexParser
import com.packatrack.core.parse.AusPostParser
import com.packatrack.core.parse.CainiaoParser
import com.packatrack.core.parse.ImileParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

/**
 * Live HTTP fetchers built on each carrier's public endpoint.
 *
 * - Cainiao: public global detail JSON (no key).
 * - Australia Post: official v2 track API - needs a free AUTH-KEY in Settings.
 * - iMile: the signed query endpoint behind imile.com's own web tracker (no key).
 */
class HttpTrackingFetcher(
    private val ausPostKey: () -> String?,
    /**
     * Receives every request's key and outcome so the app can show a user what a courier actually
     * returned. A callback rather than fetcher state, so the fetcher itself stays stateless.
     */
    private val onRawResponse: (String, RawResponse) -> Unit = { _, _ -> },
) {

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        // Never let OkHttp auto-follow redirects: it would forward request headers
        // (including the AusPost AUTH-KEY, which OkHttp does not strip) to whatever
        // host the redirect points at. get() follows same-host HTTPS hops itself.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Fetches a tracking snapshot for one number from its carrier; null when there is none. */
    suspend fun fetch(carrier: Carrier, trackingNumber: String): Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            when (carrier) {
                Carrier.UBI_SMART_PARCEL,
                Carrier.CAINIAO -> fetchCainiao(rawResponseKey(carrier.id, trackingNumber), trackingNumber)
                Carrier.AUSTRALIA_POST -> fetchAusPost(rawResponseKey(carrier.id, trackingNumber), trackingNumber)
                Carrier.IMILE -> fetchImile(rawResponseKey(carrier.id, trackingNumber), trackingNumber)
                Carrier.ARAMEX -> fetchAramex(rawResponseKey(carrier.id, trackingNumber), trackingNumber)
                // No usable public scan endpoint; the parcel stays visible and the
                // "Open on carrier website" link still works.
                Carrier.MORNING_GLOBAL -> null
                // Orange Connex serves tracking data only through its SPA; no verified
                // public JSON endpoint, so SpeedPAK is link-only like Morning Global.
                Carrier.SPEED_PAK -> null
            }
        }.getOrElse { error ->
            // Cancellation must propagate so a stopped sync actually stops; only genuine
            // fetch failures degrade to "no data".
            if (error is CancellationException) throw error
            null
        }
    }

    private fun get(
        owner: String,
        url: HttpUrl,
        headers: Map<String, String>,
        redirectsLeft: Int = MAX_REDIRECTS,
        sink: (RawResponse) -> Unit = { onRawResponse(owner, it) },
    ): String? {
        // Every exit records what happened, so a failed request is as visible to the user as a
        // successful one - "no data" on screen is exactly when the payload matters most.
        fun record(body: String?, error: String? = null) {
            sink(RawResponse(stored(body), System.currentTimeMillis(), error))
        }
        val builder = Request.Builder().url(url)
            // Let OkHttp add Accept-Encoding: gzip itself so it also decompresses the
            // response transparently. Setting the header manually would make us receive
            // raw gzip bytes and read them as garbage, breaking every parser.
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36",
            )
        headers.forEach { (k, v) -> builder.header(k, v) }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.code in 300..399) {
                    val target = resp.header("Location")?.let { resp.request.url.resolve(it) }
                    // Follow a redirect only when it stays on the same host over HTTPS,
                    // so request headers (e.g. the AusPost AUTH-KEY) are never sent to
                    // another host. Anything else is treated as no data.
                    return@use if (target != null && redirectsLeft > 0 &&
                        target.isHttps && target.host == resp.request.url.host
                    ) {
                        get(owner, target, headers, redirectsLeft - 1, sink)
                    } else {
                        Log.w(TAG, "Not following redirect ${resp.code} from ${url.host} to ${target?.host ?: "?"}")
                        record(null, "Redirect ${resp.code} from ${url.host} not followed")
                        null
                    }
                }
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code} from ${url.host}")
                    record(null, "HTTP ${resp.code} from ${url.host}")
                    return@use null
                }
                // Cap what a single response may buffer: these are undocumented endpoints, and a
                // misbehaving or hostile one should cost a skipped poll, not the whole process.
                val length = resp.body.contentLength()
                if (length > MAX_BODY_BYTES) {
                    Log.w(TAG, "Response from ${url.host} too large ($length bytes), skipping")
                    record(null, "Response from ${url.host} too large ($length bytes)")
                    return@use null
                }
                // peekBody also bounds responses with no declared length (chunked).
                resp.peekBody(MAX_BODY_BYTES).string().also { body ->
                    record(body)
                    debug { "GET ${url.host}${url.encodedPath} -> ${resp.code} (${body.length} bytes)" }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Request to ${url.host} failed", e)
            record(null, e.message ?: e.javaClass.simpleName)
            null
        }
    }

    /** One Cainiao lookup, shared by the UBI and Cainiao legs of the same number. */
    private class CainiaoResult(val capturedAt: Long, val response: RawResponse?, val snapshot: Snapshot?)

    private val recentCainiao = ConcurrentHashMap<String, CainiaoResult>()

    /**
     * UBI Smart Parcel and Cainiao are served by the same endpoint, and an AP number is tracked on
     * both, so one refresh used to ask Cainiao the same question twice in a row. Doubling the
     * request rate is what trips Cainiao's bot check, which then hides every scan and every
     * hand-off number (the "Latest Tracking Number") until it lifts. A lookup made moments ago for
     * the same number is reused instead, and its outcome recorded for this leg too.
     */
    private fun fetchCainiao(owner: String, number: String): Snapshot? {
        val key = number.trim().uppercase()
        val now = System.currentTimeMillis()
        recentCainiao.entries.removeIf { now - it.value.capturedAt > CAINIAO_REUSE_MS }
        recentCainiao[key]?.let { cached ->
            cached.response?.let { onRawResponse(owner, it) }
            return cached.snapshot
        }
        var last: RawResponse? = null
        val snapshot = fetchCainiaoUncached(owner, number) { last = it }
        recentCainiao[key] = CainiaoResult(now, last, snapshot)
        return snapshot
    }

    private fun fetchCainiaoUncached(owner: String, number: String, captured: (RawResponse) -> Unit): Snapshot? {
        // Wraps the owner's recorder so the shared result knows what this lookup returned.
        val record = { response: RawResponse ->
            captured(response)
            onRawResponse(owner, response)
        }
        // The standard JSON endpoint first, then the newer one that takes otherMailNoList.
        val attempts = listOf(
            listOf("mailNos" to number),
            listOf("mailNoList" to number, "otherMailNoList" to ""),
        )
        for (params in attempts) {
            val url = CAINIAO_DETAIL.toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .addQueryParameter("lang", "en")
                .build()
            val referer = CAINIAO_PAGE.toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            val body = get(owner, url, mapOf("Referer" to referer.toString(), "Accept" to "application/json"), sink = record)
                ?: continue
            debug { "Cainiao response: ${body.take(500)}" }
            CainiaoParser.parse(body)?.let { snap ->
                debug { "Parsed ${snap.events.size} events from Cainiao" }
                return snap
            }
            // Only an unparseable body counts as a bot check: real scans can legitimately say
            // things like "address verify" and must not be discarded. Asking the second endpoint
            // on the same host would only prolong the block, so stop here.
            if (CainiaoParser.isBotCheck(body)) {
                Log.w(TAG, "Cainiao returned a bot-check (captcha) page")
                record(RawResponse(stored(body), System.currentTimeMillis(), CAINIAO_BOT_CHECK))
                return null
            }
        }
        Log.w(TAG, "All Cainiao endpoints failed")
        return null
    }

    private fun fetchAusPost(owner: String, number: String): Snapshot? {
        val key = ausPostKey()?.takeIf { it.isNotBlank() }
        if (key == null) {
            Log.w(TAG, "AusPost API key not configured, skipping")
            return null
        }
        val url = "https://digitalapi.auspost.com.au/v2/postage/track/events".toHttpUrl().newBuilder()
            .addQueryParameter("q", number)
            .build()
        val body = get(owner, url, mapOf("AUTH-KEY" to key, "Accept" to "application/json")) ?: return null
        return AusPostParser.parse(body, number)
    }

    private fun fetchImile(owner: String, number: String): Snapshot? {
        // Endpoint used by imile.com's own web tracker. The previously used hosts are gone:
        // customer.track.imile.com no longer resolves and www.imile.com/api/track now 404s.
        // The site signs every query, so mirror that signing instead of guessing endpoints:
        //   code = MD5(waybillNo + salt) in the query string
        //   sign = base64(RSA-PKCS1 v1.5 of waybillNo) with the public key published in
        //          the track page bundle (headers may omit it; the query still resolves).
        val headers = mutableMapOf(
            "lang" to "en",
            "Accept" to "application/json",
        )
        imileSign(number)?.let { headers["sign"] = it }
        val url = "https://www.imile.com/saastms/mobileWeb/track/query".toHttpUrl().newBuilder()
            .addQueryParameter("waybillNo", number)
            .addQueryParameter("code", imileQueryCode(number))
            .build()
        val body = get(owner, url, headers) ?: return null
        debug { "iMile response: ${body.take(500)}" }
        return ImileParser.parse(body, number)
    }

    /** Query signature iMile's web tracker appends to every track query. */
    private fun imileQueryCode(number: String): String =
        MessageDigest.getInstance("MD5")
            .digest((number + IMILE_CODE_SALT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * iMile's public key, decoded once rather than on every poll. If decoding throws, nothing is
     * cached: [imileSign] falls back to an unsigned query and the next poll tries again.
     */
    private val imilePublicKey: PublicKey by lazy {
        KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(android.util.Base64.decode(IMILE_PUBLIC_KEY, android.util.Base64.DEFAULT)),
        )
    }

    /** RSA (PKCS#1 v1.5) encryption of the waybill with iMile's published public key. */
    private fun imileSign(number: String): String? = try {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, imilePublicKey)
        android.util.Base64.encodeToString(
            cipher.doFinal(number.toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP,
        )
    } catch (e: Exception) {
        // If iMile ever rotates the key we degrade to an unsigned query rather than
        // dropping the fetch entirely.
        Log.w(TAG, "iMile sign unavailable, querying unsigned", e)
        null
    }

    /** [body] as kept for the troubleshooting view, cut down to [MAX_STORED_CHARS]. */
    private fun stored(body: String?): String? = when {
        body == null -> null
        body.length <= MAX_STORED_CHARS -> body
        else -> body.take(MAX_STORED_CHARS) + "\n[truncated: ${body.length} characters total]"
    }

    /** Best-effort: Aramex's public shipment-tracking endpoints; graceful null when unreachable. */
    private fun fetchAramex(owner: String, number: String): Snapshot? {
        val candidates = listOf(
            "https://www.aramex.com/api/tracking/gettrackingresults".toHttpUrl().newBuilder()
                .addQueryParameter("shipmentNumber", number).build(),
            "https://tracking.aramex.com/api/shipments/track".toHttpUrl().newBuilder()
                .addQueryParameter("ShipmentNumber", number).build(),
        )
        for (url in candidates) {
            val body = get(owner, url, mapOf("Accept" to "application/json")) ?: continue
            val snap = AramexParser.parse(body, number)
            if (snap != null) return snap
        }
        return null
    }

    private companion object {
        const val TAG = "TrackingFetcher"
        const val MAX_REDIRECTS = 5

        /** Largest response body accepted from a carrier (2 MiB); real timelines are far smaller. */
        const val MAX_BODY_BYTES = 2L * 1024 * 1024

        /**
         * Largest body kept for the troubleshooting view (128 KiB). Real carrier responses are a
         * few kilobytes; the cap keeps a pathological one from sitting in memory per parcel.
         */
        const val MAX_STORED_CHARS = 128 * 1024
        const val CAINIAO_DETAIL = "https://global.cainiao.com/global/detail.json"
        const val CAINIAO_PAGE = "https://global.cainiao.com/newDetail.htm"

        /** How long one Cainiao lookup answers for every leg of the same number. */
        const val CAINIAO_REUSE_MS = 60_000L

        const val CAINIAO_BOT_CHECK =
            "Cainiao answered with a bot check (captcha page) instead of tracking data. " +
                "Nothing was updated; the next refresh will try again."

        /**
         * Response bodies can carry delivery details, so they are only logged when debug
         * logging is switched on for this tag (`adb shell setprop log.tag.TrackingFetcher DEBUG`).
         */
        inline fun debug(message: () -> String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message())
        }
        const val IMILE_CODE_SALT = "imileTrackQuery2024"

        /**
         * iMile's track-page RSA public key (X.509/SPKI, base64). The web tracker encrypts
         * the waybill number with it and sends the result as the `sign` header.
         */
        const val IMILE_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3dFPiKNZwt+HoBbPAG/t7kZC2k3pBX2eCl5L" +
                "eyeW8woNuEV5bA5kB9Y9KKTOQng62ERGPLwi84CdIB8s265ljQUib//iO3jVrZesJueO5Xu+s80s3Z/8" +
                "9jgJleT1XawN1GubgkGXOoT1a7tvX8+aItkGgR//48ELqJVVUL+yGsBtXxFjNmOEWxBJNQuwAf9yWcCI" +
                "l1enD60GjZjPWrsfw8QUqam7K5e45ealcPEYGenNePwuPpCq6twdD0YYYzKdRN0dZP1uTviFpNfph90c" +
                "9YgQ8kgDkRMcpjVv6KZ+bg5JZ4sK6LkV4vwOjPijisthHBvUXhu3fyhMgvoDO/j5gwIDAQAB"
    }
}
