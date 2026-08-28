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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packatrack.app.data.db.OrderItemEntity
import com.packatrack.app.data.db.ShipmentWithLegs
import com.packatrack.app.data.db.TrackingLegEntity
import com.packatrack.app.ui.components.CarrierChip
import com.packatrack.app.ui.components.StatusPill
import com.packatrack.app.ui.humanWeight
import com.packatrack.app.ui.overallStatusCode
import com.packatrack.app.ui.parcelName
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
    val orders = entry?.orders.orEmpty()

    var menuOpen by remember { mutableStateOf(value = false) }
    var showAddCourier by remember { mutableStateOf(false) }
    var showAddOrder by remember { mutableStateOf(false) }
    var showCombine by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    val legById = remember(legs) { legs.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entry?.let { parcelName(it.shipment, it.orders, it.legs) } ?: "Parcel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, null) },
                                text = { Text("Combine with another parcel") },
                                onClick = { menuOpen = false; showCombine = true },
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                text = { Text("Delete parcel", color = MaterialTheme.colorScheme.error) },
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item(key = "hero") {
                HeroSection(entry)
            }

            item(key = "orders_title") {
                SectionHeader(
                    title = "Orders",
                    count = orders.size,
                    onAdd = { showAddOrder = true }
                )
            }
            if (orders.isEmpty()) {
                item(key = "orders_empty") {
                    EmptySectionText("No orders linked. Add AliExpress orders here to track combined shipments easily.")
                }
            }
            items(orders, key = { "order_${it.id}" }) { order ->
                OrderRow(
                    order = order,
                    onOpenLink = {
                        order.orderUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        }
                    },
                    onRemove = { scope.launch { repo.removeOrder(order.id) } },
                )
            }

            item(key = "couriers_title") {
                SectionHeader(
                    title = "Tracking Numbers",
                    count = legs.size,
                    onAdd = { showAddCourier = true }
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
                item(key = "changes_title") { SectionHeader("Insights", count = changes.size) }
                items(changes, key = { "chg_${it.id}" }) { change ->
                    InsightRow(change.message, change.createdAt)
                }
            }

            item(key = "timeline_title") { SectionHeader("Timeline", count = timeline.size) }
            if (timeline.isEmpty()) {
                item(key = "timeline_empty") {
                    EmptySectionText("Scanning for updates... Pull to refresh on home screen.")
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
                    isLast = timeline.last() == ev
                )
            }
            item(key = "footer") { Spacer(Modifier.height(40.dp)) }
        }
    }

    if ((showAddCourier) && (shipment != null)) {
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

    if (showAddOrder && shipment != null) {
        AddOrderDialog(
            onDismiss = { showAddOrder = false },
            onAdd = { name, link ->
                showAddOrder = false
                scope.launch { repo.addOrder(id, name, link) }
            },
        )
    }

    if (showEdit && shipment != null) {
        EditParcelDialog(
            initialTitle = shipment.title.orEmpty(),
            initialWeight = shipment.weightGrams?.toInt()?.toString().orEmpty(),
            onDismiss = { showEdit = false },
            onSave = { title, weight ->
                showEdit = false
                scope.launch { repo.updateShipment(id, title, weight) }
            },
        )
    }
}

@Composable
private fun HeroSection(entry: ShipmentWithLegs?) {
    val shipment = entry?.shipment
    val legs = entry?.legs.orEmpty()
    val status = overallStatusCode(legs)

    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusPill(status)
            Spacer(Modifier.height(16.dp))
            Text(
                entry?.let { parcelName(it.shipment, it.orders, it.legs) } ?: "Parcel",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Last updated: ${legs.mapNotNull { it.lastSyncAt }.maxOrNull()?.let { TimeUtil.format(it) } ?: "Never"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(24.dp))
            com.packatrack.app.ui.components.ShipmentProgressTracker(status)

            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                parcelWeight(shipment?.weightGrams, legs)?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(humanWeight(it)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${legs.size} Couriers") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int = 0, onAdd: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        if (onAdd != null) {
            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun OrderRow(
    order: OrderItemEntity,
    onOpenLink: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(order.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!order.orderUrl.isNullOrBlank()) {
                IconButton(onClick = onOpenLink) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
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
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarrierChip(leg.carrierId, carrier?.displayName ?: "Courier")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSite) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(leg.trackingNumber, style = MonoNumber)
            if (leg.aliasNumbers.isNotBlank()) {
                Text(
                    "Aliases: ${leg.aliasNumbers}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InsightRow(message: String, time: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                TimeUtil.format(time) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
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
    isLast: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val color = statusColor(statusCode)
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            if (!location.isNullOrBlank() || !carrierName.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    location?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    carrierName?.let {
                        CarrierChip(carrierId, it)
                    }
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
                        val label = parcelName(cand.shipment, cand.orders, cand.legs)
                        OutlinedButton(
                            onClick = { onCombine(cand.shipment.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(18.dp))
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
    initialWeight: String,
    onDismiss: () -> Unit,
    onSave: (title: String?, weight: Double?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var weight by remember { mutableStateOf(initialWeight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(title.trim().ifBlank { null }, weight.trim().toDoubleOrNull())
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit parcel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Parcel name (optional)") },
                    supportingText = { Text("Leave blank to name it after its orders") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight in grams") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun AddOrderDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, orderUrl: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = name.isNotBlank() || link.isNotBlank(),
                onClick = { onAdd(name.trim(), link.trim().ifBlank { null }) },
            ) { Text("Add order") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add an order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Record another AliExpress order carried in this parcel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Order name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("AliExpress order link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
