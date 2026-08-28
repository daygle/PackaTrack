@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.rememberAppContainer

private val intervals = listOf(1, 6, 12, 24)
private val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = rememberAppContainer().prefs
    var key by remember { mutableStateOf(prefs.ausPostApiKey.orEmpty()) }
    var interval by remember { mutableStateOf(prefs.syncIntervalHours) }
    var notifications by remember { mutableStateOf(prefs.notificationsEnabled) }
    var delivered by remember { mutableStateOf(prefs.notifyOnDelivered) }
    var exceptions by remember { mutableStateOf(prefs.notifyOnExceptions) }
    var transit by remember { mutableStateOf(prefs.notifyOnTransit) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnlySync) }
    var theme by remember { mutableStateOf(prefs.themeMode) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Make PackaTrack work your way", style = MaterialTheme.typography.headlineSmall)
            Text("Control refreshes, alerts, and appearance from one place.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SettingsCard(Icons.Default.CloudSync, "Tracking & refresh", "Keep parcel statuses current automatically.") {
                Text("Background refresh", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                    intervals.forEach { hours ->
                        FilterChip(selected = interval == hours, onClick = { interval = hours; prefs.syncIntervalHours = hours; SyncWorker.schedule(context, hours) }, label = { Text("${hours}h") })
                    }
                }
                SettingSwitch("Wi‑Fi only", "Avoid background refresh on mobile data", wifiOnly) { wifiOnly = it; prefs.wifiOnlySync = it }
            }

            SettingsCard(Icons.Default.Notifications, "Notifications", "Choose which tracking changes deserve your attention.") {
                SettingSwitch("Parcel notifications", "Allow PackaTrack to notify you", notifications) { notifications = it; prefs.notificationsEnabled = it }
                SettingSwitch("Delivered", "When a parcel reaches its destination", delivered, notifications) { delivered = it; prefs.notifyOnDelivered = it }
                SettingSwitch("Exceptions", "Delays, failed delivery, or customs issues", exceptions, notifications) { exceptions = it; prefs.notifyOnExceptions = it }
                SettingSwitch("Transit updates", "Routine movement scans", transit, notifications) { transit = it; prefs.notifyOnTransit = it }
            }

            SettingsCard(Icons.Default.Security, "Courier access", "Credentials stay on this device and are only used for tracking.") {
                Text("Australia Post API key", style = MaterialTheme.typography.titleSmall)
                Text("Cainiao and other supported couriers work without a key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("AUTH-KEY") }, singleLine = true)
                TextButton(onClick = { prefs.ausPostApiKey = key }) { Text("Save key") }
            }

            SettingsCard(Icons.Default.Palette, "Appearance", "Choose how PackaTrack looks.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.forEach { (value, label) -> FilterChip(selected = theme == value, onClick = { theme = value; prefs.themeMode = value }, label = { Text(label) }) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(horizontal = 6.dp))
                Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, description: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
