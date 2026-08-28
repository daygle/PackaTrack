@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packatrack.app.ui.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.packatrack.app.R
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packatrack.app.data.TrackingRepository.RefreshOutcome
import com.packatrack.app.data.db.ShipmentWithLegs
import com.packatrack.app.notify.Notifier
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.components.CarrierChip
import com.packatrack.app.ui.components.StatusPill
import com.packatrack.app.ui.daysInTransit
import com.packatrack.app.ui.overallStatusCode
import com.packatrack.app.ui.parcelName
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
    val firstEventTimes by repo.observeFirstEventTimes().collectAsStateWithLifecycle(initialValue = emptyMap())

    var syncing by remember { mutableStateOf(value = false) }
    var showAddDialog by remember { mutableStateOf(value = false) }
    var activityDismissedAt by remember { mutableLongStateOf(container.prefs.recentActivityDismissedAt) }
    val visibleChanges = recentChanges.filter { it.createdAt > activityDismissedAt }

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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { runSync { repo.refreshAll(force = true) } }) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh All")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Parcel") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { runSync { repo.refreshAll() } },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (visibleChanges.isNotEmpty()) {
                    item(key = "banner") {
                        RecentChangesCard(
                            messages = visibleChanges.asSequence().take(3).map { it.message }.toList(),
                            onDismiss = {
                                val newest = recentChanges.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()
                                container.prefs.recentActivityDismissedAt = newest
                                activityDismissedAt = newest
                            },
                        )
                    }
                }

                if (shipments.isEmpty()) {
                    item(key = "empty") { EmptyState() }
                }

                items(shipments, key = { it.shipment.id }) { entry ->
                    ParcelCard(
                        entry = entry,
                        firstEventMs = firstEventTimes[entry.shipment.id],
                        onOpen = { onOpenDetail(entry.shipment.id) },
                        onDelete = { scope.launch { repo.delete(entry.shipment.id) } },
                        onRefresh = { runSync { repo.refreshShipment(entry.shipment.id, force = true) } },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddShipmentDialog(
            onDismiss = { showAddDialog = false },
            onSave = { number, title, orderUrl, carrier ->
                showAddDialog = false
                // Always perform the add (never gated by an in-flight refresh), then poll
                // just the new parcel.
                syncing = true
                scope.launch {
                    android.util.Log.d("HomeScreen", "Adding shipment: $number")
                    val newId = runCatching {
                        repo.addShipment(number, title, orderUrl, carrier)
                    }.getOrNull()
                    android.util.Log.d("HomeScreen", "Shipment added with id: $newId")
                    val outcome = newId?.let { repo.refreshShipment(it, force = true) } ?: RefreshOutcome(0, emptyList())
                    android.util.Log.d("HomeScreen", "Refresh outcome: updated=${outcome.updated}, notable=${outcome.notable.size}")
                    Notifier.postChanges(context, outcome.notable.map { it.message })
                    syncing = false
                }
            },
        )
    }

    LaunchedEffect(container.prefs.syncIntervalHours, container.prefs.wifiOnlySync) {
        SyncWorker.schedule(context, container.prefs.syncIntervalHours, container.prefs.wifiOnlySync)
    }
}

@Composable
private fun RecentChangesCard(messages: List<String>, onDismiss: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 16.dp)) {
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
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss recent activity",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
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
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_parcels_v2),
            contentDescription = null,
            modifier = Modifier.size(240.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("No Parcels Tracking", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a tracking number to start monitoring your shipments in one place.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ParcelCard(
    entry: ShipmentWithLegs,
    firstEventMs: Long?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(value = false) }
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
        val days = daysInTransit(firstEventMs ?: shipment.createdAt)
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    if (days == 1) "1 day" else "$days days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
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
                                                Intent(Intent.ACTION_VIEW, carrier.publicUrl(leg.trackingNumber).toUri()),
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
    onSave: (number: String, title: String?, orderUrl: String?, carrier: Carrier?) -> Unit,
) {
    var number by remember { mutableStateOf(value = "") }
    var title by remember { mutableStateOf(value = "") }
    var orderUrl by remember { mutableStateOf(value = "") }
    var override by remember { mutableStateOf<Carrier?>(value = null) }
    var showManual by remember { mutableStateOf(value = false) }

    val detectedCarriers = CarrierDetector.detectAll(number)
    val detected = detectedCarriers.firstOrNull()
    val chosen = override
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
                        chosen,
                    )
                },
            ) { Text("Save & Track") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add Parcel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Tracking Number") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if ((detected != null) && !showManual) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudSync,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Auto-detected: ${detectedCarriers.joinToString(" + ") { it.displayName }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = { showManual = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Change", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else if (number.isNotBlank() || showManual) {
                    Column {
                        Text(
                            if (detected == null && !showManual) "Courier not recognised — pick one below:" else "Select Courier:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Carrier.entries.forEach { carrier ->
                                androidx.compose.material3.FilterChip(
                                    selected = chosen == carrier,
                                    onClick = {
                                        override = if (override == carrier) null else carrier
                                        if (override != null) showManual = true
                                    },
                                    label = { Text(carrier.displayName) },
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "We'll try to auto-detect the courier from the number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Item Name (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = orderUrl, onValueChange = { orderUrl = it }, label = { Text("Order Link (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
