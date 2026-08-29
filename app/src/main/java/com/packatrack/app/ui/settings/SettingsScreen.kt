@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.packatrack.app.R
import com.packatrack.app.data.BackupManager
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.theme.PackaTrackTheme
import kotlinx.coroutines.launch

private val intervals = listOf(1, 2, 6, 12, 24)
private val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
private val greenOptions = listOf(7, 10, 15, 21, 30)
private val yellowOptions = listOf(15, 21, 30, 45, 60)
private val orangeOptions = listOf(30, 45, 60, 75, 90)
private val dateFormats = listOf(
    "dd MMM yyyy, HH:mm" to "28 Aug, 12:30",
    "MMM dd, yyyy HH:mm" to "Aug 28, 12:30",
    "yyyy-MM-dd HH:mm" to "2026-08-28",
    "dd/MM/yyyy HH:mm" to "28/08/2026"
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val prefs = container.prefs
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }

    var backupPassphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }
    var backupError by remember { mutableStateOf<String?>(null) }

    var key by remember { mutableStateOf(prefs.ausPostApiKey.orEmpty()) }
    var interval by remember { mutableIntStateOf(prefs.syncIntervalHours) }
    var notifications by remember { mutableStateOf(prefs.notificationsEnabled) }
    var delivered by remember { mutableStateOf(prefs.notifyOnDelivered) }
    var exceptions by remember { mutableStateOf(prefs.notifyOnExceptions) }
    var transit by remember { mutableStateOf(prefs.notifyOnTransit) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnlySync) }
    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var dateFormat by remember { mutableStateOf(prefs.dateTimeFormat) }
    var biometricLock by remember { mutableStateOf(prefs.biometricLock) }
    var transitGreenDays by remember { mutableIntStateOf(prefs.transitGreenDays) }
    var transitYellowDays by remember { mutableIntStateOf(prefs.transitYellowDays) }
    var transitOrangeDays by remember { mutableIntStateOf(prefs.transitOrangeDays) }

    // --- Permission & System Monitoring ---
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val passphrase = backupPassphrase.toCharArray()
            scope.launch {
                runCatching { backupManager.export(uri, passphrase) }
                    .onSuccess {
                        backupError = null
                        backupPassphrase = ""
                        Toast.makeText(context, "Portable backup exported", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { backupError = it.message ?: "Export failed" }
                passphrase.fill('\u0000')
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val passphrase = restorePassphrase.toCharArray()
            scope.launch {
                runCatching { backupManager.import(uri, passphrase) }
                    .onSuccess {
                        backupError = null
                        restorePassphrase = ""
                        Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { backupError = it.message ?: "Restore failed" }
                passphrase.fill('\u0000')
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- System Status Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.system_status))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.notifications_permission)) },
                    supportingContent = {
                        Text(
                            if (hasNotificationPermission) stringResource(R.string.granted) else stringResource(R.string.denied_hint),
                            color = if (hasNotificationPermission) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (hasNotificationPermission) Icons.Default.Notifications else Icons.Default.Info,
                            null,
                            tint = if (hasNotificationPermission) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    },
                    trailingContent = {
                        if (!hasNotificationPermission) {
                            TextButton(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }) {
                                Text(stringResource(R.string.fix))
                            }
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.background_sync)) },
                    supportingContent = {
                        Text(stringResource(R.string.sync_status_hint, interval, if (wifiOnly) stringResource(R.string.wifi_only_parentheses) else ""))
                    },
                    leadingContent = { Icon(Icons.Default.CloudSync, null, tint = Color(0xFF10B981)) }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.battery_usage)) },
                    supportingContent = {
                        Text(
                            if (isIgnoringBatteryOptimizations) stringResource(R.string.unrestricted) else stringResource(R.string.optimized_hint),
                            color = if (isIgnoringBatteryOptimizations) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.OfflineBolt,
                            null,
                            tint = if (isIgnoringBatteryOptimizations) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingContent = {
                        if (!isIgnoringBatteryOptimizations) {
                            TextButton(onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                runCatching { context.startActivity(intent) }
                            }) {
                                Text(stringResource(R.string.fix))
                            }
                        }
                    }
                )
            }

            // --- Tracking & Refresh Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.tracking_refresh))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.background_sync)) },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.refresh_frequency), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                intervals.forEachIndexed { index, hours ->
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = intervals.size),
                                        onClick = {
                                            interval = hours
                                            prefs.syncIntervalHours = hours
                                            SyncWorker.schedule(context, hours, wifiOnly)
                                        },
                                        selected = interval == hours
                                    ) {
                                        Text("${hours}h", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary) }
                )

                SettingSwitchItem(
                    title = stringResource(R.string.wifi_only),
                    subtitle = stringResource(R.string.wifi_only_hint),
                    icon = Icons.Default.Wifi,
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        prefs.wifiOnlySync = it
                        SyncWorker.schedule(context, interval, it)
                    }
                )
            }

            // --- Security Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.biometric_lock))

                SettingSwitchItem(
                    title = stringResource(R.string.biometric_lock),
                    subtitle = stringResource(R.string.biometric_lock_hint),
                    icon = Icons.Default.Security,
                    checked = biometricLock,
                    onCheckedChange = {
                        biometricLock = it
                        prefs.biometricLock = it
                    }
                )
            }

            // --- Notifications Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.notifications_group))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_alerts)) },
                    supportingContent = { Text(stringResource(R.string.app_alerts_hint)) },
                    leadingContent = { Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = notifications,
                            onCheckedChange = {
                                notifications = it
                                prefs.notificationsEnabled = it
                            }
                        )
                    }
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingSwitchItem(stringResource(R.string.delivered), stringResource(R.string.delivered_hint), null, delivered, notifications) { delivered = it; prefs.notifyOnDelivered = it }
                SettingSwitchItem(stringResource(R.string.exceptions), stringResource(R.string.exceptions_hint), null, exceptions, notifications) { exceptions = it; prefs.notifyOnExceptions = it }
                SettingSwitchItem(stringResource(R.string.transit), stringResource(R.string.transit_hint), null, transit, notifications) { transit = it; prefs.notifyOnTransit = it }
            }

            // --- Courier Access Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.courier_access))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.auspost)) },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.auspost_hint), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = key,
                                onValueChange = { key = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text(stringResource(R.string.api_key_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = { prefs.ausPostApiKey = key },
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = key != prefs.ausPostApiKey
                            ) {
                                Text(stringResource(R.string.save_credentials))
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            // --- Transit Thresholds Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.transit_thresholds))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.transit_green_hint)) },
                    supportingContent = {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            greenOptions.forEach { value ->
                                FilterChip(
                                    selected = transitGreenDays == value,
                                    onClick = {
                                        transitGreenDays = value
                                        prefs.transitGreenDays = value
                                        // Ensure Yellow > Green
                                        if (transitYellowDays <= value) {
                                            val next = yellowOptions.firstOrNull { it > value } ?: yellowOptions.last()
                                            transitYellowDays = next
                                            prefs.transitYellowDays = next
                                        }
                                        // Ensure Orange > Yellow
                                        if (transitOrangeDays <= transitYellowDays) {
                                            val next = orangeOptions.firstOrNull { it > transitYellowDays } ?: orangeOptions.last()
                                            transitOrangeDays = next
                                            prefs.transitOrangeDays = next
                                        }
                                    },
                                    label = { Text("$value ${stringResource(R.string.days_suffix)}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF10B981)) }
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.transit_yellow_hint)) },
                    supportingContent = {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            yellowOptions.forEach { value ->
                                FilterChip(
                                    selected = transitYellowDays == value,
                                    onClick = {
                                        transitYellowDays = value
                                        prefs.transitYellowDays = value
                                        // Ensure Green < Yellow
                                        if (transitGreenDays >= value) {
                                            val next = greenOptions.lastOrNull { it < value } ?: greenOptions.first()
                                            transitGreenDays = next
                                            prefs.transitGreenDays = next
                                        }
                                        // Ensure Orange > Yellow
                                        if (transitOrangeDays <= value) {
                                            val next = orangeOptions.firstOrNull { it > value } ?: orangeOptions.last()
                                            transitOrangeDays = next
                                            prefs.transitOrangeDays = next
                                        }
                                    },
                                    label = { Text("$value ${stringResource(R.string.days_suffix)}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.CalendarToday, null, tint = Color(0xFFF59E0B)) }
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.transit_orange_hint)) },
                    supportingContent = {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            orangeOptions.forEach { value ->
                                FilterChip(
                                    selected = transitOrangeDays == value,
                                    onClick = {
                                        transitOrangeDays = value
                                        prefs.transitOrangeDays = value
                                        // Ensure Yellow < Orange
                                        if (transitYellowDays >= value) {
                                            val next = yellowOptions.lastOrNull { it < value } ?: yellowOptions.first()
                                            transitYellowDays = next
                                            prefs.transitYellowDays = next
                                        }
                                        // Ensure Green < Yellow
                                        if (transitGreenDays >= transitYellowDays) {
                                            val next = greenOptions.lastOrNull { it < transitYellowDays } ?: greenOptions.first()
                                            transitGreenDays = next
                                            prefs.transitGreenDays = next
                                        }
                                    },
                                    label = { Text("$value ${stringResource(R.string.days_suffix)}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.CalendarToday, null, tint = Color(0xFFF97316)) }
                )
            }

            // --- Appearance Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.appearance))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_mode)) },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            themes.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                                    onClick = {
                                        themeMode = value
                                        prefs.themeMode = value
                                    },
                                    selected = themeMode == value
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary) }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.date_format)) },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp)) {
                            dateFormats.forEach { (pattern, label) ->
                                FilterChipWrapper(
                                    selected = dateFormat == pattern,
                                    onClick = {
                                        dateFormat = pattern
                                        prefs.dateTimeFormat = pattern
                                    },
                                    label = label
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            // --- Backup & Restore Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.backup_restore))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.portable_backup)) },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.backup_hint),
                                style = MaterialTheme.typography.bodySmall
                            )

                            OutlinedTextField(
                                value = backupPassphrase,
                                onValueChange = { backupPassphrase = it; backupError = null },
                                label = { Text(stringResource(R.string.passphrase_label)) },
                                supportingText = { Text(stringResource(R.string.passphrase_hint)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    enabled = backupPassphrase.length >= 12,
                                    onClick = { exportLauncher.launch("packatrack-backup.pktb") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.export_file))
                                }

                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.import_file))
                                }
                            }

                            OutlinedTextField(
                                value = restorePassphrase,
                                onValueChange = { restorePassphrase = it; backupError = null },
                                label = { Text(stringResource(R.string.restore_passphrase_label)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            backupError?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            // --- About Section ---
            SettingsCard {
                SettingsGroupHeader(stringResource(R.string.about))

                val packageInfo = remember {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= 33) {
                            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }
                    }.getOrNull()
                }
                val versionName = packageInfo?.versionName ?: "2.0.0"

                ListItem(
                    headlineContent = { Text("PackaTrack") },
                    supportingContent = { Text(stringResource(R.string.version_label, versionName)) },
                    leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.github_repo)) },
                    supportingContent = { Text("https://github.com/daygle/PackaTrack") },
                    leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/daygle/PackaTrack".toUri())
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
    }
}

@Composable
private fun ColumnScope.SettingsGroupHeader(title: String) = Text(
    title.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
)

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon?.let { { Icon(it, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), modifier = Modifier.size(24.dp)) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            supportingColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    )
}

@Composable
private fun FilterChipWrapper(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(bottom = 4.dp),
        shape = RoundedCornerShape(12.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    PackaTrackTheme {
        // Not calling SettingsScreen directly to avoid context/container issues in preview
        Column(Modifier.padding(16.dp)) {
            SettingsCard {
                SettingsGroupHeader("Preview Group")
                SettingSwitchItem("Option One", "Description", Icons.Default.Wifi, true) {}
            }
        }
    }
}
