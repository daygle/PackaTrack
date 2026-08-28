@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packatrack.app.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packatrack.app.data.TrackingRepository.RefreshOutcome
import com.packatrack.app.data.db.ShipmentWithLegs
import com.packatrack.app.notify.Notifier
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.components.CarrierChip
import com.packatrack.app.ui.components.StatusPill
import com.packatrack.app.ui.humanWeight
import com.packatrack.app.ui.overallStatusCode
import com.packatrack.app.ui.parcelName
import com.packatrack.app.ui.parcelWeight
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.theme.MonoNumber
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

    // Manual refresh: a no-op while one is already running.
    fun runSync(block: suspend () -> RefreshOutcome) {
        if (syncing) return
        syncing = true
        scope.launch {
            val outcome = block()
            Notifier.postChanges(context, outcome.notable.map { it.message })
            syncing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PackaTrack", style = MaterialTheme.typography.titleLarge)
                        if (shipments.isNotEmpty()) {
                            val inTransit = shipments.count { overallStatusCode(it.legs) == "IN_TRANSIT" }
                            Text(
                                "$inTransit in transit · ${shipments.size} total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { runSync { repo.refreshAll() } }) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh all")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add parcel") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (recentChanges.isNotEmpty()) {
                item(key = "banner") { RecentChangesCard(recentChanges.take(3).map { it.message }) }
            }

            if (shipments.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }

            items(shipments, key = { it.shipment.id }) { entry ->
                ParcelCard(
                    entry = entry,
                    onOpen = { onOpenDetail(entry.shipment.id) },
                    onDelete = { scope.launch { repo.delete(entry.shipment.id) } },
                    onRefresh = { runSync { repo.refreshShipment(entry.shipment.id) } },
                )
            }
        }
    }

    if (showAddDialog) {
        AddShipmentDialog(
            onDismiss = { showAddDialog = false },
            onSave = { number, title, orderUrl, weight, carrier ->
                showAddDialog = false
                // Always perform the add (never gated by an in-flight refresh), then poll
                // just the new parcel.
                syncing = true
                scope.launch {
                    val newId = runCatching {
                        repo.addShipment(number, title, orderUrl, weight, carrier)
                    }.getOrNull()
                    val outcome = newId?.let { repo.refreshShipment(it) } ?: RefreshOutcome(0, emptyList())
                    Notifier.postChanges(context, outcome.notable.map { it.message })
                    syncing = false
                }
            },
        )
    }

    LaunchedEffectOnce { SyncWorker.schedule(context, container.prefs.syncIntervalHours) }
}

@Composable
private fun RecentChangesCard(messages: List<String>) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            messages.forEach {
                Text(
                    "•  $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("No parcels tracking", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a tracking number to start monitoring your AliExpress shipments in one place.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ParcelCard(
    entry: ShipmentWithLegs,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shipment = entry.shipment
    val legs = entry.legs
    val primary = legs.firstOrNull()
    val title = parcelName(shipment, entry.orders, legs)
    val status = overallStatusCode(legs)

    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        legs.firstOrNull()?.trackingNumber ?: "No tracking number",
                        style = MonoNumber,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = MaterialTheme.colorScheme.outline)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            text = { Text("Refresh") },
                            onClick = { menuOpen = false; onRefresh() },
                        )
                        primary?.let { leg ->
                            Carrier.fromId(leg.carrierId)?.let { carrier ->
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    text = { Text("View on Web") },
                                    onClick = {
                                        menuOpen = false
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(carrier.publicUrl(leg.trackingNumber))),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            com.packatrack.app.ui.components.ShipmentProgressTracker(status)

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status)
                Spacer(Modifier.weight(1f))
                legs.forEachIndexed { index, leg ->
                    if (index < 2) {
                        CarrierChip(leg.carrierId, Carrier.fromId(leg.carrierId)?.displayName ?: "Courier")
                        Spacer(Modifier.width(8.dp))
                    }
                }
                if (legs.size > 2) {
                    Text("+${legs.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
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
    var override by remember { mutableStateOf<Carrier?>(null) }

    val detected = CarrierDetector.detect(number)
    val chosen = override ?: detected
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = number.trim().length >= 6,
                onClick = {
                    onSave(
                        number.trim(),
                        title.trim().ifBlank { null },
                        orderUrl.trim().ifBlank { null },
                        weight.trim().toDoubleOrNull(),
                        chosen,
                    )
                },
            ) { Text("Save & track") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add parcel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Tracking number") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    when {
                        number.isBlank() -> "You can add more couriers to this parcel later."
                        override != null -> "Carrier: ${chosen?.displayName}"
                        detected != null -> "Detected carrier: ${detected.displayName}"
                        else -> "Carrier not recognised — pick one below or it defaults to Cainiao UBI."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Carrier.entries.forEach { carrier ->
                        androidx.compose.material3.FilterChip(
                            selected = chosen == carrier,
                            onClick = { override = if (override == carrier) null else carrier },
                            label = { Text(carrier.displayName) },
                        )
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Order name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = orderUrl, onValueChange = { orderUrl = it }, label = { Text("AliExpress order link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight in grams (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

/** Runs [block] exactly once per composition lifetime of the calling screen. */
@Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
