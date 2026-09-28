package com.packatrack.core.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Carrier timestamps arrive in several shapes and usually without a timezone.
 * We parse best-effort to epoch millis (UTC assumption) and always keep the raw string
 * so the UI can show exactly what the carrier reported.
 */
object TimeUtil {

    private const val DEFAULT_FORMAT = "dd MMM yyyy, HH:mm"

    private val parsePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    // SimpleDateFormat is not thread-safe and costly to build, and parse() may try every
    // pattern for each scan of each poll, so keep one instance per pattern per thread.
    private val formatters = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
        override fun initialValue() = HashMap<String, SimpleDateFormat>()
    }

    private fun fmt(pattern: String): SimpleDateFormat =
        formatters.get()!!.getOrPut(pattern) {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

    /** Returns epoch ms or null when unparseable. */
    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        for (p in parsePatterns) {
            try {
                return fmt(p).parse(raw)?.time ?: continue
            } catch (_: ParseException) {
                // try next pattern
            } catch (_: IllegalArgumentException) {
            }
        }
        // Epoch-seconds or millis as plain number?
        raw.toLongOrNull()?.let { secs ->
            return if (secs > 1_000_000_000_000L) secs else secs * 1000L
        }
        return null
    }

    /** Formats epoch ms using [formatPattern] UTC for display. */
    fun format(ms: Long?, formatPattern: String = DEFAULT_FORMAT): String? {
        if (ms == null) return null
        return try {
            fmt(formatPattern).format(Date(ms))
        } catch (_: IllegalArgumentException) {
            fmt(DEFAULT_FORMAT).format(Date(ms))
        }
    }
}
