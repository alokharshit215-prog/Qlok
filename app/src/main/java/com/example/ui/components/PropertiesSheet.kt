package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ApkMetadata
import com.example.model.StorageItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesBottomSheet(
    item: StorageItem,
    apkMetadata: ApkMetadata?,
    computeFolderMetrics: suspend (StorageItem) -> Triple<Int, Int, Long>,
    onCopyPath: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onOpenLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    var folderMetrics by remember(item.pathOrUri) { mutableStateOf<Triple<Int, Int, Long>?>(null) }

    LaunchedEffect(item.pathOrUri) {
        if (item.isDirectory && !item.isProtected) {
            folderMetrics = computeFolderMetrics(item)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIconBadge(item = item, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (item.isDirectory) "Folder" else item.category.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            PropertyRow("Full Path / URI", item.pathOrUri, monospace = true)
            PropertyRow("Location", item.parentPathOrUri, monospace = true)

            if (item.isDirectory) {
                val metrics = folderMetrics
                PropertyRow(
                    "Contents",
                    if (item.isProtected) "Protected by Android"
                    else if (metrics == null) "Calculating..."
                    else "${metrics.first} files, ${metrics.second} folders"
                )
                PropertyRow(
                    "Total Size",
                    if (item.isProtected) "Unavailable"
                    else if (metrics == null) "Calculating..."
                    else FormatUtils.formatBytes(metrics.third)
                )
            } else {
                PropertyRow("Size", "${FormatUtils.formatBytes(item.size)} (${item.size} bytes)")
                PropertyRow("Type", if (item.extension.isBlank()) "File" else item.extension.uppercase())
                PropertyRow("MIME Type", item.mimeType, monospace = true)
                PropertyRow("Extension", item.extension.ifBlank { "None" })
            }

            item.createdAt?.let { PropertyRow("Created", FormatUtils.formatDate(it)) }
            PropertyRow("Modified", FormatUtils.formatDate(item.lastModified))
            PropertyRow("Readable / Writable", "${if (item.canRead) "Yes" else "No"} / ${if (item.canWrite) "Yes" else "No"}")

            if (apkMetadata != null) {
                HorizontalDivider()
                Text("APK Package Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                PropertyRow("App Label", apkMetadata.appName)
                PropertyRow("Package Name", apkMetadata.packageName, monospace = true)
                PropertyRow("Version", "${apkMetadata.versionName} (${apkMetadata.versionCode})")
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCopyPath,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Path")
                }
                FilledTonalButton(
                    onClick = onOpenLocation,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Location")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRename,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rename")
                }
                if (!item.isDirectory) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
            } else {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            },
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
