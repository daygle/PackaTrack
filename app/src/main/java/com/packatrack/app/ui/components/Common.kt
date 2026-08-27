package com.packatrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.packatrack.app.ui.statusLabel
import com.packatrack.app.ui.theme.carrierColor
import com.packatrack.app.ui.theme.statusColor

/** A rounded status pill with a coloured dot, e.g. "Out for delivery". */
@Composable
fun StatusPill(code: String?, modifier: Modifier = Modifier) {
    val color = statusColor(code)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            statusLabel(code),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** A compact chip naming a carrier in its brand accent colour. */
@Composable
fun CarrierChip(carrierId: String?, name: String, modifier: Modifier = Modifier) {
    val color = carrierColor(carrierId)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(name, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
