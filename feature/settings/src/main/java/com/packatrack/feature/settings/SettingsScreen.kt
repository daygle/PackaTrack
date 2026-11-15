@file:OptIn(ExperimentalMaterial3Api::class)

package com.packatrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.packatrack.feature.common.R
import com.packatrack.feature.common.theme.PackaTrackTheme

/** The settings categories, each opening its own page. */
enum class SettingsSection(val key: String) {
    SYSTEM_STATUS("system"),
    TRACKING("tracking"),
    APP_LOCK("lock"),
    NOTIFICATIONS("notifications"),
    COURIERS("couriers"),
    TRANSIT("transit"),
    SMART_IMPORT("import"),
    APPEARANCE("appearance"),
    BACKUP("backup"),
    ABOUT("about");

    companion object {
        fun fromKey(key: String?): SettingsSection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Settings landing page: a short menu of category rows rather than one long scroll of every
 * control, matching the LocaPeer settings menu. Each row opens a focused page
 * (see [SettingsSectionScreen]) that owns just its own card, dialogs and state.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
) {
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
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsMenuCard(onOpenSection)
            }
        }
    }
}

@Composable
private fun SettingsMenuCard(onOpenSection: (SettingsSection) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            CategoryRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.system_status),
                subtitle = stringResource(R.string.settings_menu_system_subtitle),
                onClick = { onOpenSection(SettingsSection.SYSTEM_STATUS) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.tracking_refresh),
                subtitle = stringResource(R.string.settings_menu_tracking_subtitle),
                onClick = { onOpenSection(SettingsSection.TRACKING) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.Security,
                title = stringResource(R.string.biometric_lock),
                subtitle = stringResource(R.string.settings_menu_lock_subtitle),
                onClick = { onOpenSection(SettingsSection.APP_LOCK) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.notifications_group),
                subtitle = stringResource(R.string.settings_menu_notifications_subtitle),
                onClick = { onOpenSection(SettingsSection.NOTIFICATIONS) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.VpnKey,
                title = stringResource(R.string.courier_access),
                subtitle = stringResource(R.string.settings_menu_couriers_subtitle),
                onClick = { onOpenSection(SettingsSection.COURIERS) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.CalendarToday,
                title = stringResource(R.string.transit_thresholds),
                subtitle = stringResource(R.string.settings_menu_transit_subtitle),
                onClick = { onOpenSection(SettingsSection.TRANSIT) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.Email,
                title = stringResource(R.string.smart_import),
                subtitle = stringResource(R.string.settings_menu_import_subtitle),
                onClick = { onOpenSection(SettingsSection.SMART_IMPORT) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.settings_menu_appearance_subtitle),
                onClick = { onOpenSection(SettingsSection.APPEARANCE) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.SettingsBackupRestore,
                title = stringResource(R.string.backup_restore),
                subtitle = stringResource(R.string.settings_menu_backup_subtitle),
                onClick = { onOpenSection(SettingsSection.BACKUP) },
            )
            CardDivider()
            CategoryRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.settings_menu_about_subtitle),
                onClick = { onOpenSection(SettingsSection.ABOUT) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsMenuPreview() {
    PackaTrackTheme(themeMode = "light") {
        SettingsMenuCard {}
    }
}
