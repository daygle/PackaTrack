package com.packatrack.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.packatrack.app.AppContainer
import com.packatrack.app.PackaTrackApp

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as PackaTrackApp).container

fun statusLabel(code: String?): String =
    code?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Waiting for scans"

fun humanWeight(g: Double?): String = when {
    g == null -> "—"
    g >= 1000.0 -> String.format(java.util.Locale.US, "%.2f kg", g / 1000.0)
    else -> String.format(java.util.Locale.US, "%.0f g", g)
}
