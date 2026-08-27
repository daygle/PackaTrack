@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.app.ui.settings

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
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.packatrack.app.sync.SyncWorker
import com.packatrack.app.ui.rememberAppContainer

private val intervals = listOf(1, 6, 12, 24)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val prefs = container.prefs

    var demo by remember { mutableStateOf(prefs.demoMode) }
    var key by remember { mutableStateOf(prefs.ausPostApiKey.orEmpty()) }
    var interval by remember { mutableStateOf(prefs.syncIntervalHours) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demo data mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Track fictional parcels offline to see renumbering and combined-shipment detection.\n\nDemo numbers:\n• DEMO600087654321 (renumbered mid-journey)\n• DEMO111222333 (consolidated)\n• CNDEMOCOMBO9X (combined parcel)\n• DEMOJOIN01 + DEMOJOIN02 (add both, refresh a few times — Cainiao merges them into one parcel)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = demo,
                    onCheckedChange = {
                        prefs.demoMode = it
                        demo = it
                    },
                )
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Australia Post API key", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Free key from developers.auspost.com.au. Cainiao works without a key.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AUTH-KEY") },
                    singleLine = true,
                )
                TextButton(onClick = { prefs.ausPostApiKey = key }) { Text("Save key") }
            }
        }

        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Background sync", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    intervals.forEach { hours ->
                        FilterChip(
                            selected = interval == hours,
                            onClick = {
                                prefs.syncIntervalHours = hours
                                SyncWorker.schedule(context, hours)
                                interval = hours
                            },
                            label = { Text("${hours}h") },
                        )
                    }
                }
            }
        }
            Spacer(Modifier.height(40.dp))
        }
    }
}
