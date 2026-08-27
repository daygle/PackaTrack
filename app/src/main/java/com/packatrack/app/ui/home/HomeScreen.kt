@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.notify.Notifier
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.humanWeight
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.statusLabel
import com.packatrack.core.detect.CarrierDetector
import com.packatrack.core.model.Carrier
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()

    val shipments by repo.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentChanges by repo.observeRecentChanges().collectAsStateWithLifecycle(initialValue = emptyList())

    var syncing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PackaTrack") },
                actions = {
                    IconButton(onClick = {
                        syncing = true
                        scope.launch {
                            val outcome = repo.refreshAll()
                            Notifier.postChanges(context, outcome.notable.map { it.message })
                            syncing = false
                        }
                    }) {
                        if (syncing) CircularProgressIndicator(Modifier.width(22.dp))
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add parcel")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (recentChanges.isNotEmpty()) {
                item(key = "banner") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Recent parcel changes", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.padding(2.dp))
                        recentChanges.take(3).forEach { change ->
                            Text(
                                "• ${change.message}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (shipments.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text("No parcels yet.\nTap ＋ to add an AliExpress tracking number.")
                    }
                }
            }

            items(shipments, key = { it.id }) { shipment ->
                ShipmentCard(
                    shipment = shipment,
                    onOpen = { onOpenDetail(shipment.id) },
                    onDelete = {
                        scope.launch { repo.delete(shipment.id) }
                    },
                    onRefreshOne = {
                        scope.launch {
                            val all = shipments.firstOrNull { it.id == shipment.id }
                            if (all != null) repo.refreshAll()
                        }
                    },
                )
            }

            item(key = "spacer_footer") { Spacer(Modifier.padding(bottom = 48.dp)) }
        }
    }

    if (showAddDialog) {
        AddShipmentDialog(
            onDismiss = { showAddDialog = false },
            onSave = { number, title, orderUrl, weight, carrierOverride ->
                showAddDialog = false
                syncing = true
                scope.launch {
                    runCatching {
                        repo.addOrUpdate(number, title, orderUrl, weight, carrierOverride)
                        val outcome = repo.refreshAll()
                        Notifier.postChanges(context, outcome.notable.map { it.message })
                    }
                    syncing = false
                }
            },
        )
    }

    // keep worker scheduling fresh whenever app opens
    LaunchedEffectOnce { SyncWorker.schedule(context, container.prefs.syncIntervalHours) }
}

@Composable
private fun ShipmentCard(
    shipment: ShipmentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRefreshOne: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val carrier = Carrier.fromId(shipment.carrierId)

    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        shipment.title ?: shipment.trackingNumber,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        shipment.aliasNumbers.takeIf { it.isNotBlank() }
                            ?.let { "$it → ${shipment.trackingNumber}" }
                            ?: shipment.trackingNumber,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            text = { Text("Refresh all") },
                            onClick = { menuOpen = false; onRefreshOne() },
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                            text = { Text("Open on carrier website") },
                            onClick = {
                                menuOpen = false
                                carrier?.publicUrl(shipment.trackingNumber)?.let { url ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.padding(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(carrier?.displayName ?: "Carrier?") })
                AssistChip(onClick = {}, label = { Text(statusLabel(shipment.lastStatusCode)) })
                AssistChip(onClick = {}, label = { Text(humanWeight(shipment.weightGrams)) })
            }
        }
    }
}

@Composable
private fun AddShipmentDialog(
    onDismiss: () -> Unit,
    onSave: (number: String, title: String?, orderUrl: String?, weightGrams: Double?, carrier: Carrier?) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var orderUrl by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    val detected = CarrierDetector.detect(number)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = number.length >= 6,
                onClick = {
                    onSave(
                        number.trim(),
                        title.trim(),
                        orderUrl.trim(),
                        weight.trim().toDoubleOrNull(),
                        detected,
                    )
                },
            ) { Text("Save & track") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add parcel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Tracking number") }, singleLine = true,
                )
                Text(
                    when {
                        number.isBlank() -> "Cainiao UBI numbers start with CN…"
                        detected != null -> "Detected: ${detected.displayName}"
                        else -> "Carrier not recognised — will default to Cainiao UBI"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(value = orderUrl, onValueChange = { orderUrl = it }, label = { Text("AliExpress order link (optional)") }, singleLine = true)
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight in grams (optional)") }, singleLine = true)
            }
        },
    )
}

/** Runs [block] exactly once per composition lifetime of the calling screen. */
@Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
