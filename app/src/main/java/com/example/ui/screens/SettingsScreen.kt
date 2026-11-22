package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppSettings
import com.example.model.SortDirection
import com.example.model.SortOption
import com.example.model.ThemeMode
import com.example.model.ViewMode
import com.example.ui.components.BlueFileManagerLogo
import com.example.ui.components.SetPinDialog
import com.example.util.FormatUtils

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasStorageAccess: Boolean,
    indexedFilesCount: Int,
    indexedFoldersCount: Int,
    isScanning: Boolean,
    onGrantStorageAccess: () -> Unit,
    onAddSafFolder: () -> Unit,
    onScanStorage: () -> Unit,
    onRefresh: () -> Unit,
    onClearFileIndex: () -> Unit,
    onSetAutoRefresh: (Boolean) -> Unit,
    onSetShowHiddenFiles: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetBlueBrandTheme: (Boolean) -> Unit,
    onSetDefaultViewMode: (ViewMode) -> Unit,
    onSetDefaultSort: (SortOption, SortDirection) -> Unit,
    onSetEditorFontSize: (Int) -> Unit,
    onSetEditorWordWrap: (Boolean) -> Unit,
    onSetEditorLineNumbers: (Boolean) -> Unit,
    onSetEditorSyntaxHighlight: (Boolean) -> Unit,
    onSetEditorDarkTheme: (Boolean) -> Unit,
    onSetEditorAutoSaveSeconds: (Int) -> Unit,
    onSetRecycleBinEnabled: (Boolean) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onSetAppLock: (enabled: Boolean, pin: String?, biometric: Boolean?) -> Unit,
    onSetScanNotifications: (Boolean) -> Unit,
    onSetOperationNotifications: (Boolean) -> Unit
) {
    var showClearIndexDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // STORAGE
        item {
            SettingsCard(title = "Storage", icon = Icons.Rounded.Storage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Storage Access", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (hasStorageAccess) "Storage access granted" else "Permission required to scan device",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasStorageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = onGrantStorageAccess,
                        modifier = Modifier.testTag("settings_grant_storage_access")
                    ) {
                        Text("GRANT STORAGE ACCESS")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddSafFolder, modifier = Modifier.weight(1f)) {
                        Text("Add SAF Folder")
                    }
                    Button(
                        onClick = onScanStorage,
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isScanning) "Scanning..." else "SCAN STORAGE")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                        Text("Refresh")
                    }
                    OutlinedButton(
                        onClick = { showClearIndexDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear File Index")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                InfoRow("Last Scan", if (settings.lastScanTimeMillis > 0L) FormatUtils.formatDate(settings.lastScanTimeMillis) else "Never")
                InfoRow("Indexed Files", indexedFilesCount.toString())
                InfoRow("Indexed Folders", indexedFoldersCount.toString())

                SettingsSwitchRow(
                    title = "Auto Refresh",
                    subtitle = "Automatically update folder views after changes",
                    checked = settings.autoRefresh,
                    onCheckedChange = onSetAutoRefresh
                )
                SettingsSwitchRow(
                    title = "Show Hidden Files",
                    subtitle = "Display dotfiles and hidden directories where permitted",
                    checked = settings.showHiddenFiles,
                    onCheckedChange = onSetShowHiddenFiles
                )
            }
        }

        // APPEARANCE
        item {
            SettingsCard(title = "Appearance", icon = Icons.Rounded.Palette) {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                SettingsSwitchRow(
                    title = "Blue Brand Theme",
                    subtitle = "Use signature Blue & White Material 3 palette",
                    checked = settings.useBlueBrandTheme,
                    onCheckedChange = onSetBlueBrandTheme
                )
            }
        }

        // FILE BROWSER
        item {
            SettingsCard(title = "File Browser", icon = Icons.Rounded.Folder) {
                Text("Default Layout", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.defaultViewMode == mode,
                            onClick = { onSetDefaultViewMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Default Sort", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortOption.entries.forEach { opt ->
                        FilterChip(
                            selected = settings.defaultSortOption == opt,
                            onClick = { onSetDefaultSort(opt, settings.defaultSortDirection) },
                            label = { Text(opt.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortDirection.entries.forEach { dir ->
                        FilterChip(
                            selected = settings.defaultSortDirection == dir,
                            onClick = { onSetDefaultSort(settings.defaultSortOption, dir) },
                            label = { Text(dir.label) }
                        )
                    }
                }
            }
        }

        // EDITOR
        item {
            SettingsCard(title = "Text & Code Editor", icon = Icons.Rounded.Code) {
                Text("Font Size: ${settings.editorFontSizeSp} sp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = settings.editorFontSizeSp.toFloat(),
                    onValueChange = { onSetEditorFontSize(it.toInt()) },
                    valueRange = 10f..24f,
                    steps = 13
                )
                SettingsSwitchRow("Word Wrap", "Wrap long lines within editor viewport", settings.editorWordWrap, onSetEditorWordWrap)
                SettingsSwitchRow("Line Numbers", "Show line gutter numbers", settings.editorLineNumbers, onSetEditorLineNumbers)
                SettingsSwitchRow("Syntax Highlighting", "Colorize code keywords & strings", settings.editorSyntaxHighlighting, onSetEditorSyntaxHighlight)
                SettingsSwitchRow("Dark Editor", "Use dark code canvas", settings.editorDarkTheme, onSetEditorDarkTheme)

                Spacer(Modifier.height(4.dp))
                Text("Auto Save", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(0 to "Off", 30 to "30s", 60 to "1 min", 300 to "5 min")
                    options.forEach { (secs, label) ->
                        FilterChip(
                            selected = settings.editorAutoSaveSeconds == secs,
                            onClick = { onSetEditorAutoSaveSeconds(secs) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // RECYCLE BIN
        item {
            SettingsCard(title = "Recycle Bin", icon = Icons.Rounded.DeleteSweep) {
                SettingsSwitchRow(
                    title = "Enable Recycle Bin",
                    subtitle = "Move deleted local files to Trash instead of immediate deletion",
                    checked = settings.recycleBinEnabled,
                    onCheckedChange = onSetRecycleBinEnabled
                )
                OutlinedButton(onClick = onOpenRecycleBin, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage Recycle Bin")
                }
            }
        }

        // SECURITY
        item {
            SettingsCard(title = "Security & App Lock", icon = Icons.Rounded.Lock) {
                SettingsSwitchRow(
                    title = "Enable App Lock",
                    subtitle = "Require PIN or Biometric verification on launch",
                    checked = settings.appLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable && settings.appLockPinHash.isBlank()) {
                            showPinSetupDialog = true
                        } else {
                            onSetAppLock(enable, null, null)
                        }
                    }
                )
                if (settings.appLockEnabled) {
                    SettingsSwitchRow(
                        title = "Biometric Unlock",
                        subtitle = "Allow fingerprint / face unlock when supported",
                        checked = settings.appLockBiometricEnabled,
                        onCheckedChange = { bio -> onSetAppLock(true, null, bio) }
                    )
                    OutlinedButton(onClick = { showPinSetupDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Change PIN")
                    }
                }
            }
        }

        // NOTIFICATIONS
        item {
            SettingsCard(title = "Notifications", icon = Icons.Rounded.Notifications) {
                SettingsSwitchRow("Scan Notifications", "Show completion status for storage scans", settings.scanNotificationsEnabled, onSetScanNotifications)
                SettingsSwitchRow("Operation Notifications", "Notify after large copy, move, or ZIP tasks", settings.operationNotificationsEnabled, onSetOperationNotifications)
            }
        }

        // ABOUT
        item {
            SettingsCard(title = "About", icon = Icons.Rounded.Info) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .padding(vertical = 4.dp)
                ) {
                    BlueFileManagerLogo(size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Blue File Manager", style = MaterialTheme.typography.titleMedium)
                        Text("Version 1.0.0 • Made by Alok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Support by Harshit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPrivacyDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Privacy")
                    }
                    OutlinedButton(onClick = { showAboutDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("About & Licenses")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showClearIndexDialog) {
        AlertDialog(
            onDismissRequest = { showClearIndexDialog = false },
            title = { Text("Clear File Index?") },
            text = {
                Text("This clears only the local search & category index database. None of your real files or folders will be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearIndexDialog = false
                        onClearFileIndex()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Clear Index") }
            },
            dismissButton = {
                TextButton(onClick = { showClearIndexDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPinSetupDialog) {
        SetPinDialog(
            onDismiss = { showPinSetupDialog = false },
            onSavePin = { pin ->
                showPinSetupDialog = false
                onSetAppLock(true, pin, settings.appLockBiometricEnabled)
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy First") },
            text = {
                Text(
                    "Blue File Manager keeps your file index 100% local on your device.\n\n" +
                        "• Zero silent file uploads\n" +
                        "• Zero automatic cloud backup\n" +
                        "• Zero hidden network transmission\n" +
                        "• Network connections (FTP) and sharing run only when you explicitly initiate them."
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) { Text("Got It") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Blue File Manager") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version 1.0.0", fontWeight = FontWeight.SemiBold)
                    Text("Made by Alok", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("Support by Harshit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Text(
                        "Built with Kotlin, Jetpack Compose, Material 3, Room, Storage Access Framework, Media3 ExoPlayer, Zip4j, and Apache Commons Net.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
