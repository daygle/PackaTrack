package com.packatrack.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TealPrimary = Color(0xFF00696D)
private val AmberSecondary = Color(0xFF7A5900)

fun statusColor(code: String?): Color = when (code?.uppercase()) {
    "DELIVERED" -> Color(0xFF1B873A)
    "OUT_FOR_DELIVERY" -> Color(0xFFB26A00)
    "PICKUP_AVAILABLE" -> Color(0xFF6A4FB6)
    "EXCEPTION" -> Color(0xFFC62828)
    "LABEL_CREATED" -> Color(0xFF607D8B)
    else -> Color(0xFF1565C0)
}

@Composable
fun PackaTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val base = lightColorScheme(primary = TealPrimary, secondary = AmberSecondary)
        if (darkTheme) {
            darkColorScheme(primary = Color(0xFF4DDAD9), secondary = Color(0xFFE7C24B))
        } else base
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
