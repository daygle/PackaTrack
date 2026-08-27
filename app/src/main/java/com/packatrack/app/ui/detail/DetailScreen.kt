@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packatrack.app.ui.humanWeight
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.statusLabel
import com.packatrack.core.model.Carrier
import com.packatrack.core.util.TimeUtil
import com.packatrack.app.ui.theme.statusColor

@Composable
fun DetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberAppContainer().repository

    val shipment by repo.shipmentByIdFlow(id).collectAsStateWithLifecycle(initialValue = null)
    val changes by repo.observeChangesFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val timeline by repo.observeEvents(id).collectAsStateWithLifecycle(initialValue = emptyList())

    val carrier = Carrier.fromId(shipment?.carrierId)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopAppBar(
                title = { Text(shipment?.title ?: "Parcel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
        item(key = "header") {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        shipment?.trackingNumber ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (shipment?.aliasNumbers?.takeIf { it.isNotBlank() })?.let { aliases ->
                        Text("Previously: $aliases", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(carrier?.displayName ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(10.dp))
                        AssistChip(onClick = {}, label = { Text(statusLabel(shipment?.lastStatusCode)) })
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text(humanWeight(shipment?.weightGrams)) })
                    }
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            carrier?.publicUrl(shipment?.trackingNumber ?: "")?.let { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Carrier site")
                        }
                        shipment?.orderUrl?.let { url ->
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Order")
                            }
                        }
                    }
                }
            }
        }

        if (changes.isNotEmpty()) {
            item(key = "changes_title") {
                Text(
                    "Parcel changes PackaTrack detected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                )
            }
            items(changes, key = { it.id }) { change ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(change.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            TimeUtil.format(change.createdAt) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item(key = "timeline_title") {
            Text(
                "Timeline",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp),
            )
        }
        items(timeline, key = { it.id }) { ev ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(10.dp)
                        .background(statusColor(ev.statusCode), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        TimeUtil.format(ev.timeMs) ?: "(time unknown)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(ev.description, style = MaterialTheme.typography.bodyMedium)
                    ev.location?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
        item(key = "footer_spacer") { Spacer(Modifier.height(40.dp)) }
    }
}
