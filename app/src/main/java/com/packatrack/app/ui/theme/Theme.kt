package com.packatrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/* ---------------- Brand palette (matches the app icon) ---------------- */

private val Indigo = Color(0xFF4F46E5)
private val IndigoDark = Color(0xFF3730A3)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF16135C),
    secondary = Color(0xFF9A4A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF321300),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB3EBF7),
    onTertiaryContainer = Color(0xFF002F3A),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC0C1FF),
    onPrimary = Color(0xFF211D6E),
    primaryContainer = IndigoDark,
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFFFB782),
    onSecondary = Color(0xFF522300),
    secondaryContainer = Color(0xFF743500),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF58D6EF),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF004E5C),
    onTertiaryContainer = Color(0xFFB3EBF7),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    )
}

/** Monospace style for tracking numbers. */
val MonoNumber: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 0.5.sp)

/* ---------------- Status + carrier colours ---------------- */

/** Dot / accent colour for a normalized status code. */
fun statusColor(code: String?): Color = when (code?.uppercase()) {
    "DELIVERED" -> Color(0xFF1B873A)
    "OUT_FOR_DELIVERY" -> Color(0xFFB26A00)
    "PICKUP_AVAILABLE" -> Color(0xFF6A4FB6)
    "EXCEPTION" -> Color(0xFFC62828)
    "LABEL_CREATED" -> Color(0xFF607D8B)
    "IN_TRANSIT" -> Color(0xFF1565C0)
    else -> Color(0xFF5B6470)
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
