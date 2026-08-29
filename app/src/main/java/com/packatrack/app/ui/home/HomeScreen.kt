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
import com.packatrack.app.ui.theme.daysInTransitColor as daysInTransitColorDynamic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.packatrack.core.detect.CarrierDetector
import com.packatrack.core.model.Carrier
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.packatrack.app.util.ConnectivityObserver
import com.packatrack.app.util.NetworkConnectivityObserver

@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    initialNumber: String? = null,
) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()

    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val networkStatus by connectivityObserver.observe().collectAsStateWithLifecycle(initialValue = ConnectivityObserver.Status.Available)

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Archived

    val activeShipments by repo.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedShipments by repo.observeArchived().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentChanges by repo.observeRecentChanges().collectAsStateWithLifecycle(initialValue = emptyList())
    val firstEventTimes by repo.observeFirstEventTimes().collectAsStateWithLifecycle(initialValue = emptyMap())
    val latestEvents by repo.observeLatestEvents().collectAsStateWithLifecycle(initialValue = emptyMap())

    val currentShipments = if (selectedTab == 0) activeShipments else archivedShipments
    val filteredShipments = remember(currentShipments, searchQuery) {
        currentShipments.filter { entry ->
            val title = parcelName(entry.shipment, entry.orders, entry.legs)
            val numbers = entry.legs.map { it.trackingNumber }
            val status = overallStatusCode(entry.legs) ?: ""

            title.contains(searchQuery, ignoreCase = true) ||
                    numbers.any { it.contains(searchQuery, ignoreCase = true) } ||
                    status.contains(searchQuery, ignoreCase = true)
        }
    }

    var syncing by remember { mutableStateOf(value = false) }
    var showAddDialog by remember { mutableStateOf(initialNumber != null) }
    var activityDismissedAt by remember {
        val stored = container.prefs.recentActivityDismissedAt
        // Auto-mark changes as seen on each recomposition (app open / config change)
        // so the banner only appears for genuinely new activity.
        if (stored == 0L && recentChanges.isNotEmpty()) {
            container.prefs.recentActivityDismissedAt = recentChanges.maxOf { it.createdAt }
        }
        mutableLongStateOf(stored)
    }
    val visibleChanges = recentChanges.filter { it.createdAt > activityDismissedAt }

    // Manual refresh: a no-op while one is already running.
    fun runSync(block: suspend () -> RefreshOutcome) {
        if (syncing) return
        syncing = true
        scope.launch {
            try {
                val outcome = block()
                Notifier.postChanges(context, outcome.notable.map { it.message })
            } finally {
                // Always clear the flag or a single failure would disable every refresh control.
                syncing = false
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showSearch = false
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
                                    }
                                },
                                singleLine = true
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                                    if (activeShipments.isNotEmpty()) {
                                        val inTransit = activeShipments.count { overallStatusCode(it.legs) == "IN_TRANSIT" }
                                        Text(
                                            pluralStringResource(R.plurals.shipment_summary, activeShipments.size, inTransit, activeShipments.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                                if (networkStatus != ConnectivityObserver.Status.Available) {
                                    Icon(
                                        Icons.Default.WifiOff,
                                        contentDescription = stringResource(R.string.offline),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 8.dp).size(20.dp)
                                    )
                                    Text(
                                        stringResource(R.string.offline),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                            IconButton(onClick = { runSync { repo.refreshAll(force = true) } }) {
                                if (syncing) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_all))
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_active)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.tab_archived)) }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_parcel)) },
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

                if (filteredShipments.isEmpty()) {
                    item(key = "empty") { EmptyState(isSearch = searchQuery.isNotEmpty()) }
                }

                items(filteredShipments, key = { it.shipment.id }) { entry ->
                    ParcelCard(
                        entry = entry,
                        firstEventMs = firstEventTimes[entry.shipment.id],
                        latestEvent = latestEvents[entry.shipment.id],
                        onOpen = { onOpenDetail(entry.shipment.id) },
                        onDelete = { scope.launch { repo.delete(entry.shipment.id) } },
                        onArchive = { scope.launch {
                            if (entry.shipment.archived) repo.unarchive(entry.shipment.id)
                            else repo.archive(entry.shipment.id)
                        } },
                        onRefresh = { runSync { repo.refreshShipment(entry.shipment.id, force = true) } },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddShipmentDialog(
            initialNumber = initialNumber,
            onDismiss = { showAddDialog = false },
            onSave = { number, title, orderUrl, carrier ->
                showAddDialog = false
                // Always perform the add (never gated by an in-flight refresh), then poll
                // just the new parcel.
                syncing = true
                scope.launch {
                    try {
                        val newId = runCatching {
                            repo.addShipment(number, title, orderUrl, carrier)
                        }.getOrNull()
                        val outcome = newId?.let { repo.refreshShipment(it, force = true) } ?: RefreshOutcome(0, emptyList())
                        Notifier.postChanges(context, outcome.notable.map { it.message })
                    } finally {
                        syncing = false
                    }
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
                    stringResource(R.string.recent_activity),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss_activity),
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
private fun EmptyState(isSearch: Boolean = false) {
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
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSearch) stringResource(R.string.no_results) else stringResource(R.string.no_parcels),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isSearch) stringResource(R.string.search_empty_hint) else stringResource(R.string.no_parcels_hint),
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
    latestEvent: com.packatrack.app.data.db.EventEntity?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(value = false) }
    val context = LocalContext.current
    val container = rememberAppContainer()
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
    val greenThreshold = container.prefs.transitGreenDays
    val yellowThreshold = container.prefs.transitYellowDays
    val orangeThreshold = container.prefs.transitOrangeDays
    val transitColor = daysInTransitColorDynamic(days, greenThreshold, yellowThreshold, orangeThreshold)
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
                        legs.firstOrNull()?.trackingNumber ?: stringResource(R.string.no_tracking_number),
                        style = MonoNumber,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.day_count, days, days),
                    style = MaterialTheme.typography.labelSmall,
                    color = transitColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(transitColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.actions), tint = MaterialTheme.colorScheme.outline)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            text = { Text(stringResource(R.string.refresh)) },
                            onClick = { menuOpen = false; onRefresh() },
                        )
                        primary?.let { leg ->
                            Carrier.fromId(leg.carrierId)?.let { carrier ->
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    text = { Text(stringResource(R.string.view_on_web)) },
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
                            leadingIcon = { Icon(if (shipment.archived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                            text = { Text(stringResource(if (shipment.archived) R.string.unarchive else R.string.archive)) },
                            onClick = { menuOpen = false; onArchive() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            com.packatrack.app.ui.components.ShipmentProgressTracker(status)

            latestEvent?.let { event ->
                Spacer(Modifier.height(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.latest_tracking_entry),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        event.timeMs?.let { timeMs ->
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatEventDateTime(timeMs, container.prefs.dateTimeFormat),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Text(
                        event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.location?.takeIf { it.isNotBlank() }?.let { location ->
                        Text(
                            location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status)
                Spacer(Modifier.weight(1f))
                legs.forEachIndexed { index, leg ->
                    if (index < 2) {
                        CarrierChip(leg.carrierId, Carrier.fromId(leg.carrierId)?.displayName ?: stringResource(R.string.courier))
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
    initialNumber: String? = null,
    onDismiss: () -> Unit,
    onSave: (number: String, title: String?, orderUrl: String?, carrier: Carrier?) -> Unit,
) {
    var number by remember { mutableStateOf(value = initialNumber ?: "") }
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
            ) { Text(stringResource(R.string.save_and_track)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.add_parcel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text(stringResource(R.string.tracking_number_label)) }, singleLine = true,
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
                                stringResource(R.string.auto_detected, detectedCarriers.joinToString(" + ") { it.displayName }),
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
                } else if (number.isNotBlank() || showManual) {
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
                } else {
                    Text(
                        stringResource(R.string.auto_detect_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.item_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = orderUrl, onValueChange = { orderUrl = it }, label = { Text(stringResource(R.string.order_link_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

private fun formatEventDateTime(timeMs: Long, pattern: String): String {
    val sdf = try {
        SimpleDateFormat(pattern, Locale.getDefault())
    } catch (_: IllegalArgumentException) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }
    return sdf.format(Date(timeMs))
}
