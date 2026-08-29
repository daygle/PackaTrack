package com.packatrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.packatrack.app.PackaTrackApp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/* ---------------- Brand palette (matches the app icon) ---------------- */

private val Indigo = Color(0xFF6366F1)
private val IndigoDark = Color(0xFF4338CA)
private val Sky = Color(0xFF0EA5E9)
private val Emerald = Color(0xFF10B981)

/** Warm accent used for the parcel age / transit-duration badge. */
val daysInTransitColor = Color(0xFFB45309)

/** Green when transit days < greenThreshold, amber when < yellowThreshold, orange when < orangeThreshold, red otherwise. */
@Composable
fun daysInTransitColor(days: Int, greenThreshold: Int = 15, yellowThreshold: Int = 30, orangeThreshold: Int = 45): Color =
    when {
        days < greenThreshold -> Color(0xFF10B981)  // green
        days < yellowThreshold -> Color(0xFFF59E0B)  // amber/yellow
        days < orangeThreshold -> Color(0xFFF97316)  // orange
        else -> Color(0xFFEF4444)  // red
    }

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Sky,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF075985),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    // `outline` is used only for secondary text/icons in this app (borders use
    // outlineVariant), so keep it dark enough to read - slate-500, not slate-300.
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF312E81),
    primaryContainer = IndigoDark,
    onPrimaryContainer = Color(0xFFEEF2FF),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF075985),
    tertiaryContainer = Color(0xFF0C4A6E),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    // Secondary text/icons on dark surfaces - lift to slate-400 so it stays legible.
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
)

private val AppTypography = Typography().run {
    val defaultFont = FontFamily.Default
    copy(
        displayMedium = displayMedium.copy(fontFamily = defaultFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = defaultFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = defaultFont, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontFamily = defaultFont, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = defaultFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = defaultFont, letterSpacing = 0.2.sp),
        bodyMedium = bodyMedium.copy(fontFamily = defaultFont, letterSpacing = 0.2.sp),
    )
}

/** Monospace style for tracking numbers. */
val MonoNumber: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
)

/* ---------------- Status + carrier colours ---------------- */

/** Dot / accent colour for a normalized status code. */
fun statusColor(code: String?): Color = when (code?.uppercase()) {
    "DELIVERED" -> Emerald
    "OUT_FOR_DELIVERY" -> Color(0xFFF59E0B)
    "PICKUP_AVAILABLE" -> Color(0xFF8B5CF6)
    "EXCEPTION" -> Color(0xFFEF4444)
    "LABEL_CREATED" -> Color(0xFF94A3B8)
    "IN_TRANSIT" -> Sky
    else -> Color(0xFF64748B)
}

/** Accent colour per carrier, used on courier chips. */
fun carrierColor(carrierId: String?): Color = when (carrierId) {
    "australia_post" -> Color(0xFFD32F2F)
    "cainiao" -> Indigo
    "imile" -> Color(0xFF00897B)
    "aramex" -> Color(0xFFE4572E)
    "morning_global" -> Color(0xFF3F7D58)
    else -> Color(0xFF5B6470)
}

@Composable
fun PackaTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val app = LocalContext.current.applicationContext as PackaTrackApp
    val themeMode = app.containerState.value?.prefs?.themeMode ?: "system"
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
