package com.packatrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
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
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            statusLabel(code),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

/** A compact chip naming a carrier in its brand accent colour. */
@Composable
fun CarrierChip(carrierId: String?, name: String, modifier: Modifier = Modifier) {
    val color = carrierColor(carrierId)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

/** A modern visual indicator of parcel progress. */
@Composable
fun ShipmentProgressTracker(status: String?, modifier: Modifier = Modifier) {
    val steps = listOf("LABEL_CREATED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED")
    val currentIndex = steps.indexOf(status?.uppercase()).coerceAtLeast(-1)

    val activeColor = statusColor(status)
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = index <= currentIndex
            val color = if (isActive) activeColor else inactiveColor

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
