@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packatrack.app.ui.detail

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packatrack.app.data.db.ShipmentWithLegs
import com.packatrack.app.data.db.TrackingLegEntity
import com.packatrack.app.ui.components.CarrierChip
import com.packatrack.app.ui.components.StatusPill
import com.packatrack.app.ui.humanWeight
import com.packatrack.app.ui.overallStatusCode
import com.packatrack.app.ui.parcelWeight
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.theme.MonoNumber
import com.packatrack.app.ui.theme.statusColor
import com.packatrack.core.detect.CarrierDetector
import com.packatrack.core.model.Carrier
import com.packatrack.core.util.TimeUtil
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberAppContainer().repository
    val scope = rememberCoroutineScope()

    val entry by repo.observeShipment(id).collectAsStateWithLifecycle(initialValue = null)
    val changes by repo.observeChangesFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val timeline by repo.observeEvents(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allParcels by repo.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())

    val shipment = entry?.shipment
    val legs = entry?.legs.orEmpty()

    var menuOpen by remember { mutableStateOf(false) }
    var showAddCourier by remember { mutableStateOf(false) }
    var showCombine by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    val legById = remember(legs) { legs.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shipment?.title ?: "Parcel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit parcel")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.CallMerge, null) },
                                text = { Text("Combine with another parcel") },
                                onClick = { menuOpen = false; showCombine = true },
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                text = { Text("Delete parcel") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { repo.delete(id) }
                                    onBack()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "header") {
                HeaderCard(entry, context)
            }

            item(key = "couriers_title") {
                SectionHeader(
                    title = "Couriers (${legs.size})",
                    action = {
                        TextButton(onClick = { showAddCourier = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add courier")
                        }
                    },
                )
            }
            items(legs, key = { "leg_${it.id}" }) { leg ->
                CourierRow(
                    leg = leg,
                    canRemove = legs.size > 1,
                    onOpenSite = {
                        Carrier.fromId(leg.carrierId)?.let { carrier ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(carrier.publicUrl(leg.trackingNumber))),
                                )
                            }
                        }
                    },
                    onRemove = { scope.launch { repo.removeCourier(leg.id) } },
                )
            }

            if (changes.isNotEmpty()) {
                item(key = "changes_title") { SectionHeader("What PackaTrack noticed") }
                items(changes, key = { "chg_${it.id}" }) { change ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(change.message, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                TimeUtil.format(change.createdAt) ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "timeline_title") { SectionHeader("Timeline") }
            if (timeline.isEmpty()) {
                item(key = "timeline_empty") {
                    Text(
                        "No scans yet — refresh from the home screen to check for updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(timeline, key = { "ev_${it.id}" }) { ev ->
                val carrier = Carrier.fromId(legById[ev.legId]?.carrierId)
                TimelineRow(
                    time = TimeUtil.format(ev.timeMs) ?: "(time unknown)",
                    description = ev.description,
                    location = ev.location,
                    statusCode = ev.statusCode,
                    carrierId = legById[ev.legId]?.carrierId,
                    carrierName = carrier?.displayName,
                )
            }
            item(key = "footer") { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showAddCourier && shipment != null) {
        AddCourierDialog(
            existing = legs,
            onDismiss = { showAddCourier = false },
            onAdd = { number, carrier ->
                showAddCourier = false
                scope.launch {
                    repo.addCourier(id, number, carrier)
                    repo.refreshShipment(id)
                }
            },
        )
    }

    if (showCombine) {
        CombineDialog(
            candidates = allParcels.filter { it.shipment.id != id },
            onDismiss = { showCombine = false },
            onCombine = { sourceId ->
                showCombine = false
                scope.launch { repo.combineInto(targetId = id, sourceId = sourceId) }
            },
        )
    }

    if (showEdit && shipment != null) {
        EditParcelDialog(
            initialTitle = shipment.title.orEmpty(),
            initialOrderUrl = shipment.orderUrl.orEmpty(),
            initialWeight = shipment.weightGrams?.let { it.toInt().toString() }.orEmpty(),
            onDismiss = { showEdit = false },
            onSave = { title, orderUrl, weight ->
                showEdit = false
                scope.launch { repo.updateShipment(id, title, orderUrl, weight) }
            },
        )
    }
}

@Composable
private fun HeaderCard(entry: ShipmentWithLegs?, context: android.content.Context) {
    val shipment = entry?.shipment
    val legs = entry?.legs.orEmpty()
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(overallStatusCode(legs))
                parcelWeight(shipment?.weightGrams, legs)?.let {
                    AssistChip(onClick = {}, label = { Text(humanWeight(it)) })
                }
            }
            shipment?.orderUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open AliExpress order")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun CourierRow(
    leg: TrackingLegEntity,
    canRemove: Boolean,
    onOpenSite: () -> Unit,
    onRemove: () -> Unit,
) {
    val carrier = Carrier.fromId(leg.carrierId)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarrierChip(leg.carrierId, carrier?.displayName ?: "Carrier", Modifier.weight(1f))
                IconButton(onClick = onOpenSite) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on carrier site", modifier = Modifier.size(20.dp))
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove courier", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(leg.trackingNumber, style = MonoNumber)
            leg.aliasNumbers.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Previously: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            StatusPill(leg.lastStatusCode)
        }
    }
}

@Composable
private fun TimelineRow(
    time: String,
    description: String,
    location: String?,
    statusCode: String?,
    carrierId: String?,
    carrierName: String?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(statusColor(statusCode)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                location?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (carrierName != null) {
                    CarrierChip(carrierId, carrierName)
                }
            }
        }
    }
}

/* ---------------- dialogs ---------------- */

@Composable
private fun AddCourierDialog(
    existing: List<TrackingLegEntity>,
    onDismiss: () -> Unit,
    onAdd: (number: String, carrier: Carrier?) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var override by remember { mutableStateOf<Carrier?>(null) }
    val trimmed = number.trim()
    val detected = CarrierDetector.detect(trimmed)
    val chosen = override ?: detected
    val duplicate = existing.any { it.trackingNumber.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = trimmed.length >= 6 && !duplicate,
                onClick = { onAdd(trimmed, chosen) },
            ) { Text("Add courier") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add a courier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Track this same parcel with another provider's number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Tracking number") }, singleLine = true,
                    isError = duplicate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (duplicate) {
                    Text("That number is already on this parcel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text("Carrier", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Carrier.entries.forEach { carrier ->
                        androidx.compose.material3.FilterChip(
                            selected = chosen == carrier,
                            onClick = { override = carrier },
                            label = { Text(carrier.displayName) },
                        )
                    }
                }
                if (override == null && detected != null) {
                    Text("Auto-detected: ${detected.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
private fun CombineDialog(
    candidates: List<ShipmentWithLegs>,
    onDismiss: () -> Unit,
    onCombine: (sourceId: Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Combine parcels") },
        text = {
            if (candidates.isEmpty()) {
                Text("There are no other parcels to combine with.")
            } else {
                Column {
                    Text(
                        "Pick a parcel to fold into this one. Its couriers, scans and history move here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    candidates.forEach { cand ->
                        val label = cand.shipment.title ?: cand.legs.firstOrNull()?.trackingNumber ?: "Parcel ${cand.shipment.id}"
                        OutlinedButton(
                            onClick = { onCombine(cand.shipment.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun EditParcelDialog(
    initialTitle: String,
    initialOrderUrl: String,
    initialWeight: String,
    onDismiss: () -> Unit,
    onSave: (title: String?, orderUrl: String?, weight: Double?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var orderUrl by remember { mutableStateOf(initialOrderUrl) }
    var weight by remember { mutableStateOf(initialWeight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onSave(
                    title.trim().ifBlank { null },
                    orderUrl.trim().ifBlank { null },
                    weight.trim().toDoubleOrNull(),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit parcel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = orderUrl, onValueChange = { orderUrl = it }, label = { Text("AliExpress order link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight in grams") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
