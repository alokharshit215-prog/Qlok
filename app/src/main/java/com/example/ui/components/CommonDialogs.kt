package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.storage.MimeAndCategoryHelper
import com.example.model.BatchRenameConfig
import com.example.model.ConflictAction
import com.example.model.ConflictPrompt
import com.example.model.OperationProgress
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.model.ZipCompressionMode
import com.example.util.FormatUtils

@Composable
fun CreateFolderDialog(
    currentFolderName: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("New Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Destination: $currentFolderName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Folder name") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_folder_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (ok, err) = MimeAndCategoryHelper.isValidFileOrFolderName(name)
                    if (!ok) errorText = err else onCreate(name.trim())
                },
                modifier = Modifier.testTag("create_folder_confirm")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateFileDialog(
    isCodeFile: Boolean,
    currentFolderName: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var fileName by remember { mutableStateOf(if (isCodeFile) "main.py" else "notes.txt") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val quickChips = if (isCodeFile) {
        MimeAndCategoryHelper.codeTemplateSuggestions
    } else {
        listOf("notes.txt", "data.csv", "config.json", "config.ini", "app.log", "README.md")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isCodeFile) Icons.Rounded.Code else Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(if (isCodeFile) "New Code File" else "New File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Destination: $currentFolderName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = {
                        fileName = it
                        errorText = null
                    },
                    label = { Text("Filename (with extension)") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_file_name_input")
                )
                Text(
                    text = "Quick presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.forEach { preset ->
                        AssistChip(
                            onClick = {
                                fileName = preset
                                errorText = null
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (ok, err) = MimeAndCategoryHelper.isValidFileOrFolderName(fileName)
                    if (!ok) errorText = err else onCreate(fileName.trim())
                },
                modifier = Modifier.testTag("create_file_confirm")
            ) {
                Text("Create & Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameDialog(
    item: StorageItem,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${if (item.isDirectory) "Folder" else "File"}") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorText = null
                },
                label = { Text("New name") },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { msg -> { Text(msg) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val (ok, err) = MimeAndCategoryHelper.isValidFileOrFolderName(name)
                    if (!ok) errorText = err else onRename(name.trim())
                },
                modifier = Modifier.testTag("rename_confirm")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ExtensionWarningDialog(
    newName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Change File Extension?") },
        text = {
            Text("Changing the extension when renaming to \"$newName\" may make the file unusable. Continue?")
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    items: List<StorageItem>,
    canMoveToRecycleBin: Boolean,
    onConfirm: (forcePermanent: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val countLabel = if (items.size == 1) "\"${items.first().name}\"" else "${items.size} selected items"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(if (canMoveToRecycleBin) "Move to Recycle Bin?" else "Permanently Delete?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (canMoveToRecycleBin) {
                        "Are you sure you want to delete $countLabel? You can restore it later from the Recycle Bin."
                    } else {
                        "Are you sure you want to permanently delete $countLabel? This location does not support Recycle Bin and this cannot be undone."
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(false) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text(if (canMoveToRecycleBin) "Move to Trash" else "Delete Forever")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canMoveToRecycleBin) {
                    TextButton(onClick = { onConfirm(true) }) {
                        Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun ConflictResolutionDialog(
    prompt: ConflictPrompt,
    onResolve: (ConflictAction, Boolean) -> Unit
) {
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onResolve(ConflictAction.CANCEL, false) },
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("File Already Exists") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("\"${prompt.fileName}\" already exists in the destination folder.")
                if (prompt.remainingCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyToAll = !applyToAll }
                    ) {
                        Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                        Spacer(Modifier.width(4.dp))
                        Text("Apply to all (${prompt.remainingCount} remaining)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onResolve(ConflictAction.REPLACE, applyToAll) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Replace") }
                    OutlinedButton(
                        onClick = { onResolve(ConflictAction.KEEP_BOTH, applyToAll) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Keep Both (Rename)") }
                    OutlinedButton(
                        onClick = { onResolve(ConflictAction.SKIP, applyToAll) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Skip") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onResolve(ConflictAction.CANCEL, false) }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BatchRenameDialog(
    items: List<StorageItem>,
    onDismiss: () -> Unit,
    onApply: (BatchRenameConfig) -> Unit
) {
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var useNumbering by remember { mutableStateOf(false) }
    var baseName by remember { mutableStateOf("File") }
    var startNumber by remember { mutableStateOf("1") }

    val config = BatchRenameConfig(
        prefix = prefix,
        suffix = suffix,
        findText = findText,
        replaceText = replaceText,
        useNumbering = useNumbering,
        baseName = baseName,
        startNumber = startNumber.toIntOrNull() ?: 1,
        padDigits = 3
    )
    val previews = remember(items, config) { FormatUtils.previewBatchRename(items, config) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Batch Rename (${items.size} items)", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Prefix") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = { Text("Suffix") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { useNumbering = !useNumbering }
                ) {
                    Checkbox(checked = useNumbering, onCheckedChange = { useNumbering = it })
                    Text("Sequential Numbering (e.g. Trip_001)", style = MaterialTheme.typography.bodyMedium)
                }
                if (useNumbering) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = baseName,
                            onValueChange = { baseName = it },
                            label = { Text("Base name") },
                            singleLine = true,
                            modifier = Modifier.weight(1.6f)
                        )
                        OutlinedTextField(
                            value = startNumber,
                            onValueChange = { startNumber = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Start #") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Live Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(previews) { (orig, updated) ->
                        Text(
                            text = "${orig.name} → $updated",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(config) }) { Text("Apply Rename") }
                }
            }
        }
    }
}

@Composable
fun CreateZipDialog(
    selectedCount: Int,
    defaultZipName: String,
    onDismiss: () -> Unit,
    onCreate: (zipName: String, compression: ZipCompressionMode, password: String?) -> Unit
) {
    var zipName by remember { mutableStateOf(defaultZipName) }
    var compression by remember { mutableStateOf(ZipCompressionMode.NORMAL) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Create ZIP Archive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Compressing $selectedCount selected item(s). Originals are preserved.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("Archive name (.zip)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Compression level", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZipCompressionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = compression == mode,
                            onClick = { compression = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { usePassword = !usePassword }
                ) {
                    Checkbox(checked = usePassword, onCheckedChange = { usePassword = it })
                    Text("Encrypt with password (AES-256)", style = MaterialTheme.typography.bodyMedium)
                }
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Archive password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (zipName.isNotBlank()) {
                        onCreate(zipName.trim(), compression, if (usePassword && password.isNotEmpty()) password else null)
                    }
                }
            ) { Text("Create ZIP") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FolderPickerDialog(
    title: String,
    roots: List<StorageRoot>,
    onlyFileSystemFolders: Boolean = false,
    loadSubfolders: suspend (pathOrUri: String, isSafUri: Boolean) -> List<StorageItem>,
    onDismiss: () -> Unit,
    onSelectFolder: (pathOrUri: String, isSafUri: Boolean, displayName: String) -> Unit
) {
    val availableRoots = remember(roots, onlyFileSystemFolders) {
        if (onlyFileSystemFolders) roots.filter { !it.isSafUri } else roots
    }
    var stack by remember { mutableStateOf(listOf<Triple<String, String, Boolean>>()) }
    var subfolders by remember { mutableStateOf<List<StorageItem>>(emptyList()) }

    LaunchedEffect(availableRoots) {
        if (stack.isEmpty() && availableRoots.isNotEmpty()) {
            val first = availableRoots.first()
            stack = listOf(Triple(first.label, first.pathOrUri, first.isSafUri))
        }
    }

    val current = stack.lastOrNull()
    LaunchedEffect(current) {
        if (current != null) {
            subfolders = loadSubfolders(current.second, current.third)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                // Root Switcher Chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableRoots.forEach { root ->
                        FilterChip(
                            selected = stack.firstOrNull()?.second == root.pathOrUri,
                            onClick = { stack = listOf(Triple(root.label, root.pathOrUri, root.isSafUri)) },
                            label = { Text(root.label) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (stack.size > 1) {
                        IconButton(onClick = { stack = stack.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up")
                        }
                    }
                    Text(
                        text = stack.joinToString(" / ") { it.first },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    if (subfolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No subfolders here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(subfolders, key = { it.pathOrUri }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        stack = stack + Triple(folder.name, folder.pathOrUri, folder.isSafUri)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = current != null,
                        onClick = {
                            current?.let { (label, path, saf) ->
                                onSelectFolder(path, saf, label)
                            }
                        },
                        modifier = Modifier.testTag("folder_picker_confirm")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select Here")
                    }
                }
            }
        }
    }
}

@Composable
fun OperationProgressDialog(
    progress: OperationProgress,
    onCancel: () -> Unit
) {
    if (!progress.isRunning) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(progress.operationName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = progress.currentFile.ifBlank { "Working..." },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress.totalFiles > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.completedFiles.toFloat() / progress.totalFiles.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${progress.completedFiles} / ${progress.totalFiles} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (progress.isCancellable) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}
