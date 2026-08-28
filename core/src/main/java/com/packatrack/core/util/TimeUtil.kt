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

    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    /** Returns epoch ms or null when unparseable. */
    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        for (p in patterns) {
            val fmt = SimpleDateFormat(p, Locale.US)
            fmt.isLenient = false
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            try {
                return fmt.parse(raw)?.time ?: continue
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
    fun format(ms: Long?, formatPattern: String = "dd MMM yyyy, HH:mm"): String? {
        if (ms == null) return null
        return try {
            val fmt = SimpleDateFormat(formatPattern, Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.format(Date(ms))
        } catch (_: Exception) {
            // Fallback for safety
            val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.format(Date(ms))
        }
    }
}
