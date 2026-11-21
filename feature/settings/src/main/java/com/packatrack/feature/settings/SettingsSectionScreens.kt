@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packatrack.feature.settings

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.packatrack.data.BackupCodec
import com.packatrack.data.BackupManager
import com.packatrack.data.ParcelSortOrder
import com.packatrack.data.security.AppLockAuthenticators
import com.packatrack.feature.common.R
import com.packatrack.sync.EmailImportWorker
import com.packatrack.sync.GmailAuth
import com.packatrack.sync.SyncWorker
import kotlinx.coroutines.launch

private val intervals = listOf(1, 2, 6, 12, 24)
private val themeValues = listOf("system", "light", "dark")
private val graceOptions = listOf(0, 30, 60, 300)
private val greenOptions = listOf(7, 10, 15, 21, 30)
private val yellowOptions = listOf(15, 21, 30, 45, 60)
private val orangeOptions = listOf(30, 45, 60, 75, 90)
// Each label is the pattern applied to a sample date, so the choice previews exactly.
private val dateFormats = listOf(
    "dd MMM yyyy, HH:mm" to "28 Aug 2026, 12:30",
    "MMM dd, yyyy HH:mm" to "Aug 28, 2026 12:30",
    "yyyy-MM-dd HH:mm" to "2026-08-28 12:30",
    "dd/MM/yyyy HH:mm" to "28/08/2026 12:30",
)

private val statusGreen = Color(0xFF10B981)
private const val GITHUB_URL = "https://github.com/daygle/PackaTrack"

/** Routes a settings sub-page route key to its screen. */
@Composable
fun SettingsSectionScreen(sectionKey: String?, onBack: () -> Unit) {
    val section = SettingsSection.fromKey(sectionKey)
    if (section == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    when (section) {
        SettingsSection.SYSTEM_STATUS -> SystemStatusScreen(onBack)
        SettingsSection.TRACKING -> TrackingScreen(onBack)
        SettingsSection.APP_LOCK -> AppLockScreen(onBack)
        SettingsSection.NOTIFICATIONS -> NotificationsScreen(onBack)
        SettingsSection.COURIERS -> CouriersScreen(onBack)
        SettingsSection.TRANSIT -> TransitScreen(onBack)
        SettingsSection.SMART_IMPORT -> SmartImportScreen(onBack)
        SettingsSection.APPEARANCE -> AppearanceScreen(onBack)
        SettingsSection.BACKUP -> BackupScreen(onBack)
        SettingsSection.ABOUT -> AboutScreen(onBack)
    }
}

private fun hasPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

// ─── System status ───────────────────────────────────────────────────────────

@Composable
private fun SystemStatusScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var hasNotificationPermission by remember { mutableStateOf(hasPostNotifications(context)) }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }

    OnResume {
        hasNotificationPermission = hasPostNotifications(context)
        isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    SettingsSubScreen(stringResource(R.string.system_status), onBack) {
        SettingsCard(Icons.Default.Info, stringResource(R.string.system_status)) {
            SettingsRow(
                title = stringResource(R.string.notifications_permission),
                subtitle = if (hasNotificationPermission) stringResource(R.string.granted)
                else stringResource(R.string.denied_hint),
                subtitleColor = if (hasNotificationPermission) statusGreen else MaterialTheme.colorScheme.error,
                action = if (hasNotificationPermission) null else ({
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    ) { Text(stringResource(R.string.fix)) }
                }),
            )
            CardDivider()
            SettingsRow(
                title = stringResource(R.string.background_sync),
                subtitle = stringResource(
                    R.string.sync_status_hint,
                    prefs.syncIntervalHours,
                    if (prefs.wifiOnlySync) stringResource(R.string.wifi_only_parentheses) else "",
                ),
            )
            CardDivider()
            SettingsRow(
                title = stringResource(R.string.battery_usage),
                subtitle = if (isIgnoringBatteryOptimizations) stringResource(R.string.unrestricted)
                else stringResource(R.string.optimized_hint),
                subtitleColor = if (isIgnoringBatteryOptimizations) statusGreen else null,
                action = if (isIgnoringBatteryOptimizations) null else ({
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                    ) { Text(stringResource(R.string.fix)) }
                }),
            )
        }
    }
}

// ─── Tracking & refresh ──────────────────────────────────────────────────────

@Composable
private fun TrackingScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    var interval by remember { mutableIntStateOf(prefs.syncIntervalHours) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnlySync) }

    SettingsSubScreen(stringResource(R.string.tracking_refresh), onBack) {
        SettingsCard(Icons.Default.CloudSync, stringResource(R.string.tracking_refresh)) {
            SettingsBlockRow(
                title = stringResource(R.string.background_sync),
                subtitle = stringResource(R.string.refresh_frequency),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    intervals.forEachIndexed { index, hours ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = intervals.size),
                            onClick = {
                                interval = hours
                                prefs.syncIntervalHours = hours
                                SyncWorker.schedule(context, hours, wifiOnly)
                            },
                            selected = interval == hours,
                        ) {
                            Text("${hours}h", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.wifi_only),
                subtitle = stringResource(R.string.wifi_only_hint),
                checked = wifiOnly,
                onCheckedChange = {
                    wifiOnly = it
                    prefs.wifiOnlySync = it
                    SyncWorker.schedule(context, interval, it)
                },
            )
        }
    }
}

// ─── App lock ────────────────────────────────────────────────────────────────

@Composable
private fun AppLockScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    var biometricLock by remember { mutableStateOf(prefs.biometricLock) }
    var lockGrace by remember { mutableIntStateOf(prefs.lockGraceSeconds) }
    val biometricManager = remember { BiometricManager.from(context) }
    var biometricStatus by remember {
        mutableIntStateOf(biometricManager.canAuthenticate(AppLockAuthenticators.allowed))
    }
    OnResume { biometricStatus = biometricManager.canAuthenticate(AppLockAuthenticators.allowed) }

    SettingsSubScreen(stringResource(R.string.biometric_lock), onBack) {
        SettingsCard(Icons.Default.Security, stringResource(R.string.biometric_lock)) {
            val isLockAvailable = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS
            val biometricError = when (biometricStatus) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> stringResource(R.string.app_lock_error_no_hardware)
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> stringResource(R.string.app_lock_error_hardware_unavailable)
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> stringResource(R.string.app_lock_error_none_enrolled)
                else -> null
            }

            SwitchRow(
                title = stringResource(R.string.biometric_lock),
                subtitle = biometricError ?: stringResource(R.string.biometric_lock_hint),
                checked = biometricLock,
                // Allow disabling even when the sensor reports an error.
                enabled = isLockAvailable || biometricLock,
                onCheckedChange = {
                    biometricLock = it
                    prefs.biometricLock = it
                },
            )

            if (biometricLock) {
                CardDivider()
                SettingsBlockRow(title = stringResource(R.string.lock_after_title)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        graceOptions.forEach { seconds ->
                            SettingsChip(
                                selected = lockGrace == seconds,
                                onClick = {
                                    lockGrace = seconds
                                    prefs.lockGraceSeconds = seconds
                                },
                                label = stringResource(
                                    when (seconds) {
                                        30 -> R.string.lock_after_30_seconds
                                        60 -> R.string.lock_after_1_minute
                                        300 -> R.string.lock_after_5_minutes
                                        else -> R.string.lock_after_immediately
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            CardDivider()
            Text(
                stringResource(R.string.app_lock_threat_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

// ─── Notifications ───────────────────────────────────────────────────────────

@Composable
private fun NotificationsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs = viewModel.prefs
    var notifications by remember { mutableStateOf(prefs.notificationsEnabled) }
    var delivered by remember { mutableStateOf(prefs.notifyOnDelivered) }
    var exceptions by remember { mutableStateOf(prefs.notifyOnExceptions) }
    var transit by remember { mutableStateOf(prefs.notifyOnTransit) }

    SettingsSubScreen(stringResource(R.string.notifications_group), onBack) {
        SettingsCard(Icons.Default.Notifications, stringResource(R.string.notifications_group)) {
            SwitchRow(
                title = stringResource(R.string.app_alerts),
                subtitle = stringResource(R.string.app_alerts_hint),
                checked = notifications,
                onCheckedChange = {
                    notifications = it
                    prefs.notificationsEnabled = it
                },
            )
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.delivered),
                subtitle = stringResource(R.string.delivered_hint),
                checked = delivered,
                enabled = notifications,
                onCheckedChange = {
                    delivered = it
                    prefs.notifyOnDelivered = it
                },
            )
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.exceptions),
                subtitle = stringResource(R.string.exceptions_hint),
                checked = exceptions,
                enabled = notifications,
                onCheckedChange = {
                    exceptions = it
                    prefs.notifyOnExceptions = it
                },
            )
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.transit),
                subtitle = stringResource(R.string.transit_hint),
                checked = transit,
                enabled = notifications,
                onCheckedChange = {
                    transit = it
                    prefs.notifyOnTransit = it
                },
            )
        }
    }
}

// ─── Courier access ──────────────────────────────────────────────────────────

@Composable
private fun CouriersScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs = viewModel.prefs
    // The stored key is Keystore-encrypted; read it once rather than on every recomposition.
    var savedKey by remember { mutableStateOf(prefs.ausPostApiKey.orEmpty()) }
    var key by remember { mutableStateOf(savedKey) }

    SettingsSubScreen(stringResource(R.string.courier_access), onBack) {
        SettingsCard(Icons.Default.VpnKey, stringResource(R.string.courier_access)) {
            SettingsBlockRow(
                title = stringResource(R.string.auspost),
                subtitle = stringResource(R.string.auspost_hint),
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_key_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Button(
                    onClick = {
                        prefs.ausPostApiKey = key
                        savedKey = key.trim()
                    },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = key.trim() != savedKey,
                ) {
                    Text(stringResource(R.string.save_credentials))
                }
            }
        }
    }
}

// ─── Transit thresholds ──────────────────────────────────────────────────────

@Composable
private fun TransitScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs = viewModel.prefs
    var transitGreenDays by remember { mutableIntStateOf(prefs.transitGreenDays) }
    var transitYellowDays by remember { mutableIntStateOf(prefs.transitYellowDays) }
    var transitOrangeDays by remember { mutableIntStateOf(prefs.transitOrangeDays) }

    SettingsSubScreen(stringResource(R.string.transit_thresholds), onBack) {
        SettingsCard(Icons.Default.CalendarToday, stringResource(R.string.transit_thresholds)) {
            SettingsBlockRow(title = stringResource(R.string.transit_green_hint)) {
                SettingsChipRow(greenOptions, transitGreenDays) { value ->
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
                }
            }
            CardDivider()
            SettingsBlockRow(title = stringResource(R.string.transit_yellow_hint)) {
                SettingsChipRow(yellowOptions, transitYellowDays) { value ->
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
                }
            }
            CardDivider()
            SettingsBlockRow(title = stringResource(R.string.transit_orange_hint)) {
                SettingsChipRow(orangeOptions, transitOrangeDays) { value ->
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
                }
            }
        }
    }
}

/** A row of day-count chips, e.g. "7 d", with [selected] highlighted. */
@Composable
private fun SettingsChipRow(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val suffix = stringResource(R.string.days_suffix)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { value ->
            SettingsChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = "$value $suffix",
                small = true,
            )
        }
    }
}

@Composable
private fun SettingsChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    small: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
        },
        modifier = Modifier.padding(bottom = 4.dp),
        shape = RoundedCornerShape(12.dp),
    )
}

// ─── Smart import ────────────────────────────────────────────────────────────

@Composable
private fun SmartImportScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    var smartImportEnabled by remember { mutableStateOf(prefs.smartImportEnabled) }
    var smartImportAccount by remember { mutableStateOf(prefs.smartImportAccount) }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                smartImportAccount = accountName
                prefs.smartImportAccount = accountName
                smartImportEnabled = true
                prefs.smartImportEnabled = true
                EmailImportWorker.schedule(context)
            }
        }
    }

    SettingsSubScreen(stringResource(R.string.smart_import), onBack) {
        SettingsCard(Icons.Default.Email, stringResource(R.string.smart_import)) {
            SwitchRow(
                title = stringResource(R.string.smart_import),
                subtitle = stringResource(R.string.smart_import_hint),
                checked = smartImportEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && smartImportAccount == null) {
                        accountPickerLauncher.launch(GmailAuth.newChooseAccountIntent(context))
                    } else {
                        smartImportEnabled = enabled
                        prefs.smartImportEnabled = enabled
                        if (enabled) EmailImportWorker.schedule(context)
                    }
                },
            )
            smartImportAccount?.let { account ->
                CardDivider()
                SettingsRow(
                    title = stringResource(R.string.smart_import_account, account),
                    action = {
                        TextButton(
                            onClick = {
                                accountPickerLauncher.launch(GmailAuth.newChooseAccountIntent(context))
                            },
                        ) { Text(stringResource(R.string.change_courier)) }
                    },
                )
            }
        }
    }
}

// ─── Appearance ──────────────────────────────────────────────────────────────

@Composable
private fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs = viewModel.prefs
    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var sortOrder by remember { mutableStateOf(ParcelSortOrder.fromKey(prefs.sortOrder)) }
    var dateFormat by remember { mutableStateOf(prefs.dateTimeFormat) }

    SettingsSubScreen(stringResource(R.string.appearance), onBack) {
        SettingsCard(Icons.Default.Palette, stringResource(R.string.appearance)) {
            SettingsBlockRow(title = stringResource(R.string.theme_mode)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themeValues.forEachIndexed { index, value ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeValues.size),
                            onClick = {
                                themeMode = value
                                prefs.themeMode = value
                            },
                            selected = themeMode == value,
                        ) {
                            Text(
                                stringResource(
                                    when (value) {
                                        "light" -> R.string.theme_light
                                        "dark" -> R.string.theme_dark
                                        else -> R.string.theme_system
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            CardDivider()
            SettingsBlockRow(title = stringResource(R.string.sort_by)) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ParcelSortOrder.entries.forEach { order ->
                        val labelRes = when (order) {
                            ParcelSortOrder.LAST_ACTIVITY -> R.string.sort_last_activity
                            ParcelSortOrder.DATE_ADDED -> R.string.sort_date_added
                            ParcelSortOrder.NAME -> R.string.sort_name
                            ParcelSortOrder.DAYS_IN_TRANSIT -> R.string.sort_days_in_transit
                            ParcelSortOrder.STATUS -> R.string.sort_status
                        }
                        SettingsChip(
                            selected = sortOrder == order,
                            onClick = {
                                sortOrder = order
                                prefs.sortOrder = order.key
                            },
                            label = stringResource(labelRes),
                            small = true,
                        )
                    }
                }
            }
            CardDivider()
            SettingsBlockRow(title = stringResource(R.string.date_format)) {
                Column {
                    dateFormats.forEach { (pattern, label) ->
                        SettingsChip(
                            selected = dateFormat == pattern,
                            onClick = {
                                dateFormat = pattern
                                prefs.dateTimeFormat = pattern
                            },
                            label = label,
                        )
                    }
                }
            }
        }
    }
}

// ─── Backup & restore ────────────────────────────────────────────────────────

@Composable
private fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }

    val backupPassphraseState = rememberTextFieldState()
    val restorePassphraseState = rememberTextFieldState()
    var backupError by remember { mutableStateOf<String?>(null) }

    // Resolved during composition so they follow configuration changes. Reading a resource off
    // LocalContext inside the coroutine below is what lint's LocalContextGetResourceValueCall
    // check (an error) rejects.
    val backupExportedMessage = stringResource(R.string.backup_exported)
    val backupRestoredMessage = stringResource(R.string.backup_restored)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            val text = backupPassphraseState.text
            val passphrase = CharArray(text.length) { text[it] }
            scope.launch {
                runCatching { backupManager.export(uri, passphrase) }
                    .onSuccess {
                        backupError = null
                        backupPassphraseState.clearText()
                        Toast.makeText(context, backupExportedMessage, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { backupError = it.message ?: "Export failed" }
                passphrase.fill('\u0000')
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = restorePassphraseState.text
            val passphrase = CharArray(text.length) { text[it] }
            scope.launch {
                runCatching { backupManager.import(uri, passphrase) }
                    .onSuccess {
                        backupError = null
                        restorePassphraseState.clearText()
                        Toast.makeText(context, backupRestoredMessage, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { backupError = it.message ?: "Restore failed" }
                passphrase.fill('\u0000')
            }
        }
    }

    SettingsSubScreen(stringResource(R.string.backup_restore), onBack) {
        SettingsCard(Icons.Default.SettingsBackupRestore, stringResource(R.string.backup_restore)) {
            SettingsBlockRow(
                title = stringResource(R.string.portable_backup),
                subtitle = stringResource(R.string.backup_hint),
            ) {
                OutlinedSecureTextField(
                    state = backupPassphraseState,
                    label = { Text(stringResource(R.string.passphrase_label)) },
                    supportingText = { Text(stringResource(R.string.passphrase_hint)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = backupPassphraseState.text.length >= BackupCodec.MIN_PASSPHRASE_LENGTH,
                        onClick = { exportLauncher.launch("packatrack-backup.pktb") },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.export_file))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        // A shorter passphrase can never open a backup; don't send the user through
                        // the file picker only to be told so.
                        enabled = restorePassphraseState.text.length >= BackupCodec.MIN_PASSPHRASE_LENGTH,
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.import_file))
                    }
                }
                OutlinedSecureTextField(
                    state = restorePassphraseState,
                    label = { Text(stringResource(R.string.restore_passphrase_label)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                backupError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ─── About ───────────────────────────────────────────────────────────────────

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsSubScreen(stringResource(R.string.about), onBack) {
        SettingsCard(Icons.Default.Info, stringResource(R.string.about)) {
            val packageInfo = remember {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 33) {
                        context.packageManager.getPackageInfo(
                            context.packageName,
                            PackageManager.PackageInfoFlags.of(0),
                        )
                    } else {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    }
                }.getOrNull()
            }
            val versionName = packageInfo?.versionName.orEmpty()

            SettingsRow(
                title = "PackaTrack",
                subtitle = stringResource(R.string.version_label, versionName),
            )
            CardDivider()
            SettingsRow(
                title = stringResource(R.string.github_repo),
                subtitle = GITHUB_URL,
                action = {
                    IconButton(
                        onClick = {
                            // No browser installed is possible (work profiles, kiosks): never crash.
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri()))
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    }
                },
            )
        }
    }
}
