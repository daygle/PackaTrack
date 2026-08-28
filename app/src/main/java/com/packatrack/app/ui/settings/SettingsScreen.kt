@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.packatrack.app.data.BackupManager
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.rememberAppContainer
import kotlinx.coroutines.launch

private val intervals = listOf(1, 2, 6, 12, 24)
private val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
private val dateFormats = listOf("dd MMM yyyy, HH:mm" to "28 Aug 2026, 12:30", "MMM dd, yyyy HH:mm" to "Aug 28, 2026 12:30", "yyyy-MM-dd HH:mm" to "2026-08-28 12:30", "dd/MM/yyyy HH:mm" to "28/08/2026 12:30")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val prefs = container.prefs
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupPassphraseConfirmation by remember { mutableStateOf("") }
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
                    .onSuccess { backupError = null; Toast.makeText(context, "Portable backup exported", Toast.LENGTH_SHORT).show() }
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
                    .onSuccess { backupError = null; Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show() }
                    .onFailure { backupError = it.message ?: "Restore failed" }
                passphrase.fill('\u0000')
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroupHeader("Tracking & Refresh")
            ListItem(headlineContent = { Text("Background Refresh Interval") }, supportingContent = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { intervals.forEach { hours -> FilterChip(selected = interval == hours, onClick = { interval = hours; prefs.syncIntervalHours = hours; SyncWorker.schedule(context, hours, wifiOnly) }, label = { Text("${hours}h") }) } } }, leadingContent = { Icon(Icons.Default.CloudSync, null) })
            ListItem(headlineContent = { Text("Wi‑Fi Only Syncing") }, supportingContent = { Text("Save mobile data by only refreshing on Wi‑Fi") }, trailingContent = { Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; prefs.wifiOnlySync = it; SyncWorker.schedule(context, interval, it) }) })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroupHeader("Notifications")
            ListItem(headlineContent = { Text("Master Toggle") }, supportingContent = { Text("Enable or disable all parcel alerts") }, leadingContent = { Icon(Icons.Default.Notifications, null) }, trailingContent = { Switch(checked = notifications, onCheckedChange = { notifications = it; prefs.notificationsEnabled = it }) })
            SettingSwitchItem("Delivered Alerts", "When a parcel reaches its destination", delivered, notifications) { delivered = it; prefs.notifyOnDelivered = it }
            SettingSwitchItem("Exception Alerts", "Delays, failed delivery, or customs issues", exceptions, notifications) { exceptions = it; prefs.notifyOnExceptions = it }
            SettingSwitchItem("Transit Updates", "Routine movement scans", transit, notifications) { transit = it; prefs.notifyOnTransit = it }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroupHeader("Courier Access")
            ListItem(headlineContent = { Text("Australia Post API Key") }, supportingContent = { Column { Text("Required for live Australia Post tracking."); OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("API Key") }, singleLine = true); TextButton(onClick = { prefs.ausPostApiKey = key }, modifier = Modifier.padding(top = 4.dp)) { Text("Save Key") } } }, leadingContent = { Icon(Icons.Default.Security, null) })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroupHeader("Backup & Restore")
            OutlinedTextField(value = backupPassphrase, onValueChange = { backupPassphrase = it; backupError = null }, label = { Text("Backup passphrase") }, supportingText = { Text("Use at least 12 characters. Keep it safe; it cannot be recovered.") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            OutlinedTextField(value = backupPassphraseConfirmation, onValueChange = { backupPassphraseConfirmation = it; backupError = null }, label = { Text("Confirm backup passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = backupPassphrase.length >= 12 && backupPassphrase == backupPassphraseConfirmation, onClick = { exportLauncher.launch("packatrack-backup.pktb") }) { Text("Export") }
                TextButton(enabled = restorePassphrase.length >= 12, onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }) { Text("Restore") }
            }
            OutlinedTextField(value = restorePassphrase, onValueChange = { restorePassphrase = it; backupError = null }, label = { Text("Restore passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            backupError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroupHeader("Appearance")
            ListItem(headlineContent = { Text("Theme Mode") }, supportingContent = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { themes.forEach { (value, label) -> FilterChip(selected = themeMode == value, onClick = { themeMode = value; prefs.themeMode = value }, label = { Text(label) }) } } }, leadingContent = { Icon(Icons.Default.Palette, null) })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroupHeader("Date & Time")
            ListItem(headlineContent = { Text("Display Format") }, supportingContent = { Column(Modifier.padding(top = 8.dp)) { dateFormats.forEach { (pattern, label) -> FilterChip(selected = dateFormat == pattern, onClick = { dateFormat = pattern; prefs.dateTimeFormat = pattern }, label = { Text(label) }, modifier = Modifier.padding(bottom = 4.dp)) } } }, leadingContent = { Icon(Icons.Default.History, null) })
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) = Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
private fun SettingSwitchItem(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), supportingColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)))
}
