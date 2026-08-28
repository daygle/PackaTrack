@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.settings

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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.packatrack.app.data.BackupManager
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.rememberAppContainer
import com.packatrack.app.ui.theme.PackaTrackTheme
import kotlinx.coroutines.launch

private val intervals = listOf(1, 2, 6, 12, 24)
private val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val passphrase = backupPassphrase.toCharArray()
            scope.launch {
                runCatching { backupManager.export(uri, passphrase) }
                    .onSuccess {
                        backupError = null
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
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            // --- Tracking & Refresh Section ---
            SettingsCard {
                SettingsGroupHeader("Tracking & Refresh")

                ListItem(
                    headlineContent = { Text("Background Sync") },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Refresh frequency", style = MaterialTheme.typography.bodySmall)
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
                    title = "Wi‑Fi Only",
                    subtitle = "Reduce mobile data usage",
                    icon = Icons.Default.Wifi,
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        prefs.wifiOnlySync = it
                        SyncWorker.schedule(context, interval, it)
                    }
                )
            }

            // --- Notifications Section ---
            SettingsCard {
                SettingsGroupHeader("Notifications")

                ListItem(
                    headlineContent = { Text("App Alerts") },
                    supportingContent = { Text("Enable all parcel updates") },
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

                SettingSwitchItem("Delivered", "When a parcel arrives", null, delivered, notifications) { delivered = it; prefs.notifyOnDelivered = it }
                SettingSwitchItem("Exceptions", "Delays or customs issues", null, exceptions, notifications) { exceptions = it; prefs.notifyOnExceptions = it }
                SettingSwitchItem("Transit", "Movement between facilities", null, transit, notifications) { transit = it; prefs.notifyOnTransit = it }
            }

            // --- Courier Access Section ---
            SettingsCard {
                SettingsGroupHeader("Courier Access")

                ListItem(
                    headlineContent = { Text("Australia Post") },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Required for live local tracking.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = key,
                                onValueChange = { key = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text("API Key") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = { prefs.ausPostApiKey = key },
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = key != prefs.ausPostApiKey
                            ) {
                                Text("Save Credentials")
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            // --- Appearance Section ---
            SettingsCard {
                SettingsGroupHeader("Appearance")

                ListItem(
                    headlineContent = { Text("Theme Mode") },
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
                    headlineContent = { Text("Date Format") },
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
                SettingsGroupHeader("Backup & Restore")

                ListItem(
                    headlineContent = { Text("Portable Backup") },
                    supportingContent = {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Export your tracking history to an encrypted file.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            OutlinedTextField(
                                value = backupPassphrase,
                                onValueChange = { backupPassphrase = it; backupError = null },
                                label = { Text("Security Passphrase") },
                                supportingText = { Text("Min 12 chars. Required for restore.") },
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
                                    Text("Export File")
                                }

                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Import File")
                                }
                            }

                            if (restorePassphrase.isEmpty() && backupPassphrase.length < 12) {
                                // Simple hint if they haven't started typing a restore passphrase
                                OutlinedTextField(
                                    value = restorePassphrase,
                                    onValueChange = { restorePassphrase = it; backupError = null },
                                    label = { Text("Restore Passphrase") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

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
