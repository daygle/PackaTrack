package com.packatrack.feature.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.packatrack.core.model.JourneyStage
import com.packatrack.feature.common.statusLabel
import com.packatrack.feature.common.theme.carrierColor
import com.packatrack.feature.common.theme.statusColor
import java.text.NumberFormat

/** A rounded status pill with a coloured dot, e.g. "Out for delivery". */
@Composable
fun StatusPill(code: String?, modifier: Modifier = Modifier) {
    val color = statusColor(code)
    val containerColor = when (code?.uppercase()) {
        "DELIVERED" -> Color(0xFFE8F7EF)
        "OUT_FOR_DELIVERY" -> Color(0xFFFFF3D6)
        "EXCEPTION" -> Color(0xFFFDE8E8)
        "IN_TRANSIT" -> Color(0xFFE0F2FE)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
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
            .background(color.copy(alpha = 0.12f))
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

/**
 * The carrier's own journey stages ("Mainland China" -> "Australia" -> "Delivered") with the
 * percentage it reports, so the route comes from the courier instead of being inferred from
 * status codes. Use [ShipmentProgressTracker] for carriers that report no stages.
 */
@Composable
fun CarrierJourneyTracker(
    stages: List<JourneyStage>,
    rate: Double?,
    status: String?,
    modifier: Modifier = Modifier,
) {
    val activeColor = statusColor(status)
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val reachedIndex = stages.indexOfLast { it.reached }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stages.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index <= reachedIndex) activeColor else inactiveColor),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            stages.forEachIndexed { index, stage ->
                Text(
                    stage.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= reachedIndex) activeColor else MaterialTheme.colorScheme.outline,
                    fontWeight = if (index == reachedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    // First label hugs the left, last hugs the right, so the names line up with
                    // the segments above them.
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        stages.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rate?.let { fraction ->
            Spacer(Modifier.height(6.dp))
            Text(
                NumberFormat.getPercentInstance().format(fraction),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
