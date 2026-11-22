package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CloudConnectionEntity
import com.example.model.CloudServiceType
import com.example.model.RemoteFtpEntry
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.ui.components.FolderPickerDialog
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun CloudScreen(
    savedConnections: List<CloudConnectionEntity>,
    activeConnection: CloudConnectionEntity?,
    currentRemotePath: String,
    remoteEntries: List<RemoteFtpEntry>,
    isBusy: Boolean,
    errorMessage: String?,
    localRoots: List<StorageRoot>,
    loadLocalSubfolders: suspend (String, Boolean) -> List<StorageItem>,
    onAddFtpConnection: (label: String, host: String, port: Int, user: String, pass: String, passive: Boolean) -> Unit,
    onDeleteConnection: (Long) -> Unit,
    onConnectFtp: (CloudConnectionEntity, String) -> Unit,
    onDisconnect: () -> Unit,
    onNavigateUp: () -> Unit,
    onDownloadEntry: (RemoteFtpEntry, File) -> Unit,
    onUploadLocalFile: (File) -> Unit,
    onCreateRemoteFolder: (String) -> Unit,
    onDeleteRemoteEntry: (RemoteFtpEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddFtpDialog by remember { mutableStateOf(false) }
    var showCreateRemoteFolderDialog by remember { mutableStateOf(false) }
    var pendingDownloadEntry by remember { mutableStateOf<RemoteFtpEntry?>(null) }

    // Upload real file via Android document picker -> temp file -> FTP upload
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val tempFile = withContext(Dispatchers.IO) {
                    val fileName = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name ?: "upload_file"
                    val out = File(context.cacheDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                    out
                }
                onUploadLocalFile(tempFile)
            }
        }
    }

    if (activeConnection != null) {
        // Active Remote FTP Browser
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(activeConnection.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${activeConnection.host}:$currentRemotePath",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showCreateRemoteFolderDialog = true }) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New remote folder")
                }
                IconButton(onClick = { uploadPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.Upload, contentDescription = "Upload file to FTP")
                }
                IconButton(onClick = { onConnectFtp(activeConnection, currentRemotePath) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh remote")
                }
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = "Disconnect")
                }
            }

            if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (remoteEntries.isEmpty() && !isBusy) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Remote folder is empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(remoteEntries, key = { it.fullPath }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (entry.isDirectory) {
                                        onConnectFtp(activeConnection, entry.fullPath)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (entry.isDirectory) "Remote Folder" else FormatUtils.formatBytes(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!entry.isDirectory) {
                                    IconButton(onClick = { pendingDownloadEntry = entry }) {
                                        Icon(Icons.Rounded.Download, contentDescription = "Download")
                                    }
                                }
                                IconButton(onClick = { onDeleteRemoteEntry(entry) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete remote", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Cloud & Network Connections Dashboard
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cloud & Network", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Explicit user-initiated transfers only. Zero background uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { showAddFtpDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add FTP")
                    }
                }
            }

            errorMessage?.let { msg ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (isBusy) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                Text("Saved Connections", style = MaterialTheme.typography.titleMedium)
            }

            if (savedConnections.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No FTP servers added yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Tap \"Add FTP\" to connect to a real FTP server on your local network or remote host to browse, upload, and download files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(savedConnections, key = { it.id }) { conn ->
                    Surface(
                        onClick = { onConnectFtp(conn, "/") },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conn.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "ftp://${conn.host}:${conn.port} (${conn.username.ifBlank { "anonymous" }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteConnection(conn.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove connection", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Supported & Upcoming Services", style = MaterialTheme.typography.titleMedium)
            }

            items(CloudServiceType.entries) { service ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable {
                            if (service == CloudServiceType.FTP) showAddFtpDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (service.isSupportedNow) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (service == CloudServiceType.NAS || service == CloudServiceType.SMB) Icons.Rounded.Storage else Icons.Rounded.Cloud,
                                contentDescription = service.title,
                                tint = if (service.isSupportedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(service.title, style = MaterialTheme.typography.titleSmall)
                            Text(service.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (service.isSupportedNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (service.isSupportedNow) "Ready" else "Coming Soon",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (service.isSupportedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddFtpDialog) {
        AddFtpDialog(
            onDismiss = { showAddFtpDialog = false },
            onSave = { label, host, port, user, pass, passive ->
                showAddFtpDialog = false
                onAddFtpConnection(label, host, port, user, pass, passive)
            }
        )
    }

    if (showCreateRemoteFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateRemoteFolderDialog = false },
            title = { Text("New Remote Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (folderName.isNotBlank()) {
                        showCreateRemoteFolderDialog = false
                        onCreateRemoteFolder(folderName.trim())
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRemoteFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingDownloadEntry?.let { entry ->
        FolderPickerDialog(
            title = "Download '${entry.name}' To",
            roots = localRoots,
            onlyFileSystemFolders = true,
            loadSubfolders = loadLocalSubfolders,
            onDismiss = { pendingDownloadEntry = null },
            onSelectFolder = { path, _, _ ->
                pendingDownloadEntry = null
                onDownloadEntry(entry, File(path))
            }
        )
    }
}

@Composable
private fun AddFtpDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String, Boolean) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passive by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add FTP Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Connection Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host / IP") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit) },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { passive = !passive }) {
                    Checkbox(checked = passive, onCheckedChange = { passive = it })
                    Text("Passive Mode (Recommended)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = host.isNotBlank(),
                onClick = { onSave(label, host, port.toIntOrNull() ?: 21, username, password, passive) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
