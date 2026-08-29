@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packatrack.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import kotlin.math.abs
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.packatrack.app.notify.Notifier
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
import com.packatrack.app.data.db.EventEntity
import com.packatrack.app.data.db.TrackingLegEntity
import com.packatrack.app.ui.components.CarrierChip
import com.packatrack.app.ui.components.StatusPill
import com.packatrack.app.ui.daysInTransit
import com.packatrack.app.ui.overallStatusCode
import com.packatrack.app.ui.parcelName
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.theme.MonoNumber
import com.packatrack.app.ui.theme.daysInTransitColor as daysInTransitColorCompat
import com.packatrack.app.ui.theme.statusColor
import com.packatrack.core.detect.CarrierDetector
import com.packatrack.core.model.Carrier
import com.packatrack.core.util.TimeUtil
import kotlinx.coroutines.launch

import com.packatrack.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

@Composable
/** A timeline entry that may combine duplicate scans from multiple carriers. */
private data class TimelineDisplayEvent(
    val timeMs: Long?,
    val description: String,
    val location: String?,
    val statusCode: String?,
    val carriers: List<Pair<String?, String?>>, // (carrierId, displayName)
    val isLast: Boolean = false,
)

fun DetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()

    val entry by repo.observeShipment(id).collectAsStateWithLifecycle(initialValue = null)
    val changes by repo.observeChangesFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val timelineRaw by repo.observeEvents(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allParcels by repo.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs = container.prefs

    val shipment = entry?.shipment
    val legs = entry?.legs.orEmpty()
    val orders = entry?.orders.orEmpty()

    var historySort by remember { mutableStateOf(prefs.historySortOrder) }
    val timeline = remember(timelineRaw, historySort) {
        val sorted = if (historySort == "oldest") timelineRaw.sortedBy { it.timeMs ?: 0L }
        else timelineRaw.sortedByDescending { it.timeMs ?: 0L }
        // Deduplicate scans from multiple carriers reporting the same event
        // (e.g. Cainiao + UBI Smart Parcel share the same API response).
        deduplicateTimeline(sorted, legById)
    }
    val firstEventMs = remember(timelineRaw) { timelineRaw.mapNotNull { it.timeMs }.minOrNull() }

    var menuOpen by remember { mutableStateOf(value = false) }
    var historySortMenuOpen by remember { mutableStateOf(false) }
    var showAddCourier by remember { mutableStateOf(value = false) }
    var showAddOrder by remember { mutableStateOf(value = false) }
    var showCombine by remember { mutableStateOf(value = false) }
    var showEdit by remember { mutableStateOf(value = false) }
    var syncing by remember { mutableStateOf(value = false) }

    val legById = remember(legs) { legs.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entry?.let { parcelName(it.shipment, it.orders, it.legs) } ?: stringResource(R.string.parcel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_parcel))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, null) },
                                text = { Text(stringResource(R.string.combine_with_another)) },
                                onClick = { menuOpen = false; showCombine = true },
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                text = { Text(stringResource(R.string.delete_parcel), color = MaterialTheme.colorScheme.error) },
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
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = {
                if (syncing) return@PullToRefreshBox
                syncing = true
                scope.launch {
                    val outcome = repo.refreshShipment(id, force = true)
                    Notifier.postChanges(context, outcome.notable.map { it.message })
                    syncing = false
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                item(key = "hero") {
                    HeroSection(entry, firstEventMs, prefs)
                }

                item(key = "couriers_title") {
                    SectionHeader(
                        title = stringResource(R.string.tracking_numbers_title),
                        count = legs.size
                    ) { showAddCourier = true }
                }
                items(legs, key = { "leg_${it.id}" }) { leg ->
                    CourierRow(
                        leg = leg,
                        canRemove = legs.size > 1,
                        onOpenSite = {
                            Carrier.fromId(leg.carrierId)?.let { carrier ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, carrier.publicUrl(leg.trackingNumber).toUri()),
                                    )
                                }
                            }
                        },
                        onRemove = { scope.launch { repo.removeCourier(leg.id) } },
                    )
                }

                item(key = "orders_title") {
                    SectionHeader(
                        title = stringResource(R.string.items_title),
                        count = orders.size
                    ) { showAddOrder = true }
                }
                if (orders.isEmpty()) {
                    item(key = "orders_empty") {
                        EmptySectionText(stringResource(R.string.no_items_hint))
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

                if (changes.isNotEmpty()) {
                item(key = "changes_title") { SectionHeader(stringResource(R.string.insights_title), count = changes.size) }
                items(changes, key = { "chg_${it.id}" }) { change ->
                    InsightRow(change.message, change.createdAt, prefs.dateTimeFormat)
                }
            }

            item(key = "timeline_title") {
                SectionHeader(
                    title = stringResource(R.string.timeline_title),
                    count = timeline.size,
                    action = {
                        Box {
                            IconButton(onClick = { historySortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_history), modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = historySortMenuOpen, onDismissRequest = { historySortMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.newest_first)) },
                                    onClick = {
                                        historySort = "newest"
                                        prefs.historySortOrder = "newest"
                                        historySortMenuOpen = false
                                    },
                                    trailingIcon = { if (historySort == "newest") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.oldest_first)) },
                                    onClick = {
                                        historySort = "oldest"
                                        prefs.historySortOrder = "oldest"
                                        historySortMenuOpen = false
                                    },
                                    trailingIcon = { if (historySort == "oldest") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                )
            }
            if (timeline.isEmpty()) {
                item(key = "timeline_empty") {
                    EmptySectionText(stringResource(R.string.scanning_updates))
                }
            }
            itemsIndexed(timeline, key = { _, ev -> "ev_${ev.timeMs}_${ev.description}" }) { index, ev ->
                TimelineRow(
                    time = TimeUtil.format(ev.timeMs, prefs.dateTimeFormat) ?: stringResource(R.string.time_unknown),
                    description = ev.description,
                    location = ev.location,
                    statusCode = ev.statusCode,
                    carriers = ev.carriers,
                    isLast = index == timeline.lastIndex
                )
            }
                item(key = "footer") { Spacer(Modifier.height(40.dp)) }
            }
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
                    repo.refreshShipment(id, force = true)
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

    if ((showAddOrder) && (shipment != null)) {
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
            onDismiss = { showEdit = false },
            onSave = { title ->
                showEdit = false
                scope.launch { repo.updateShipment(id, title) }
            },
        )
    }
}

@Composable
private fun HeroSection(entry: ShipmentWithLegs?, firstEventMs: Long?, prefs: com.packatrack.app.data.PrefsStore) {
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
                entry?.let { parcelName(it.shipment, it.orders, it.legs) } ?: stringResource(R.string.parcel),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val days = daysInTransit(firstEventMs ?: shipment?.createdAt)
                val greenThreshold = prefs.transitGreenDays
                val yellowThreshold = prefs.transitYellowDays
                val transitColor = daysInTransitColorCompat(days, greenThreshold, yellowThreshold)
                Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = transitColor)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.days_in_transit_label, pluralStringResource(R.plurals.day_count, days, days)),
                    style = MaterialTheme.typography.labelMedium,
                    color = transitColor
                )
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.last_updated_label, legs.asSequence().mapNotNull { it.lastSyncAt }.maxOrNull()?.let { TimeUtil.format(it, prefs.dateTimeFormat) } ?: stringResource(R.string.never)),
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
                AssistChip(
                    onClick = {},
                    label = { Text(pluralStringResource(R.plurals.couriers_count_label, legs.size, legs.size)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int = 0, action: (@Composable () -> Unit)? = null, onAdd: (() -> Unit)? = null) {
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
        action?.invoke()
        if (onAdd != null) {
            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
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
                CarrierChip(leg.carrierId, carrier?.displayName ?: stringResource(R.string.courier))
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
            val context = LocalContext.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    leg.trackingNumber,
                    style = MonoNumber,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("tracking number", leg.trackingNumber))
                            Toast.makeText(context, "Tracking number copied", Toast.LENGTH_SHORT).show()
                        }
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy_tracking_number),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("tracking number", leg.trackingNumber))
                            Toast.makeText(context, "Tracking number copied", Toast.LENGTH_SHORT).show()
                        },
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
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
private fun InsightRow(message: String, time: Long, dateFormat: String) {
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
                TimeUtil.format(time, dateFormat) ?: "",
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
    carriers: List<Pair<String?, String?>>,
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
            if (!location.isNullOrBlank() || carriers.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    location?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    carriers.forEach { (carrierId, carrierName) ->
                        carrierName?.let {
                            CarrierChip(carrierId, it)
                        }
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
    var number by remember { mutableStateOf(value = "") }
    var override by remember { mutableStateOf<Carrier?>(value = null) }
    var showManual by remember { mutableStateOf(value = false) }

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
            ) { Text(stringResource(R.string.add_courier_title)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.add_courier_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.add_courier_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text(stringResource(R.string.tracking_number_label)) }, singleLine = true,
                    isError = duplicate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (duplicate) {
                    Text(stringResource(R.string.duplicate_number_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                if (detected != null && !showManual) {
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
                                stringResource(R.string.auto_detected, detected.displayName),
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
                            Text(stringResource(R.string.change_courier), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else if (trimmed.isNotBlank() || showManual) {
                    Column {
                        Text(
                            if (detected == null && !showManual) stringResource(R.string.courier_not_recognized) else stringResource(R.string.select_courier),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.combine_parcels_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.no_candidates_hint))
            } else {
                Column {
                    Text(
                        stringResource(R.string.combine_hint),
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
    onDismiss: () -> Unit,
    onSave: (title: String?) -> Unit,
) {
    var title by remember { mutableStateOf(value = initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(title.trim().ifBlank { null })
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.edit_parcel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.parcel_name_label)) },
                    supportingText = { Text(stringResource(R.string.parcel_name_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun AddOrderDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, orderUrl: String?) -> Unit,
) {
    var name by remember { mutableStateOf(value = "") }
    var link by remember { mutableStateOf(value = "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = name.isNotBlank() || link.isNotBlank(),
                onClick = { onAdd(name.trim(), link.trim().ifBlank { null }) },
            ) { Text(stringResource(R.string.add_item_title)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.add_item_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.add_item_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.item_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text(stringResource(R.string.order_link_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

/**
 * Collapses duplicate scan events from multiple carriers (e.g. Cainiao + UBI Smart Parcel)
 * that share the same timestamp and description into a single entry with multiple carrier chips.
 */
private fun deduplicateTimeline(
    sorted: List<EventEntity>,
    legById: Map<Long, TrackingLegEntity>,
): List<TimelineDisplayEvent> {
    if (sorted.isEmpty()) return emptyList()
    val result = mutableListOf<TimelineDisplayEvent>()
    var i = 0
    while (i < sorted.size) {
        val ev = sorted[i]
        val evTime = ev.timeMs ?: 0L
        val evDesc = ev.description
        val carriers = mutableListOf<Pair<String?, String?>>()
        // Collect all events within 60 seconds with the same description
        while (i < sorted.size &&
            abs((sorted[i].timeMs ?: 0L) - evTime) <= 60_000 &&
            sorted[i].description == evDesc
        ) {
            val leg = legById[sorted[i].legId]
            val carrier = Carrier.fromId(leg?.carrierId)
            carriers.add(leg?.carrierId to carrier?.displayName)
            i++
        }
        // Deduplicate carriers (same carrier reported twice)
        val uniqueCarriers = carriers.distinctBy { it.first }
        result.add(
            TimelineDisplayEvent(
                timeMs = ev.timeMs,
                description = ev.description,
                location = ev.location,
                statusCode = ev.statusCode,
                carriers = uniqueCarriers,
            )
        )
    }
    return result
}
