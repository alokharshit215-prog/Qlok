package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.storage.ZipArchiveService
import com.example.data.storage.ZipPasswordRequiredException
import com.example.data.storage.ZipWrongPasswordException
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.model.ZipEntryItem
import com.example.ui.components.FolderPickerDialog
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipBrowserScreen(
    item: StorageItem,
    zipService: ZipArchiveService,
    roots: List<StorageRoot>,
    loadSubfolders: suspend (String, Boolean) -> List<StorageItem>,
    onExtractCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var allEntries by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
    var currentArchivePath by remember { mutableStateOf("") }
    var selectedEntryNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var password by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingExtractAfterPassword by remember { mutableStateOf<Pair<File, List<String>?>?>(null) }

    var showExtractToPicker by remember { mutableStateOf(false) }
    var extractSelectedOnly by remember { mutableStateOf(false) }
    var renameTargetEntry by remember { mutableStateOf<ZipEntryItem?>(null) }

    fun reloadEntries() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                allEntries = zipService.listEntries(item.pathOrUri, item.isSafUri, password)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unable to read ZIP archive"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(item.pathOrUri) {
        reloadEntries()
    }

    fun performExtract(destDir: File, selectedNames: List<String>?, pwd: String?) {
        scope.launch {
            isLoading = true
            val res = zipService.extractEntries(
                pathOrUri = item.pathOrUri,
                isSafUri = item.isSafUri,
                destinationDir = destDir,
                selectedEntryNames = selectedNames,
                password = pwd
            )
            isLoading = false
            res.onSuccess { count ->
                snackbarHostState.showSnackbar("Extracted $count item(s) to ${destDir.name}")
                onExtractCompleted()
            }.onFailure { err ->
                when (err) {
                    is ZipPasswordRequiredException -> {
                        pendingExtractAfterPassword = destDir to selectedNames
                        passwordError = null
                        showPasswordDialog = true
                    }
                    is ZipWrongPasswordException -> {
                        pendingExtractAfterPassword = destDir to selectedNames
                        passwordError = "Incorrect password. Please try again."
                        showPasswordDialog = true
                    }
                    else -> {
                        snackbarHostState.showSnackbar(err.message ?: "Extraction failed")
                    }
                }
            }
        }
    }

    val visibleEntries = remember(allEntries, currentArchivePath) {
        val directFolders = mutableSetOf<String>()
        val result = mutableListOf<ZipEntryItem>()

        allEntries.forEach { entry ->
            val clean = entry.entryName.trimEnd('/')
            if (currentArchivePath.isEmpty()) {
                val firstSlash = clean.indexOf('/')
                if (firstSlash == -1) {
                    result.add(entry)
                } else {
                    val topFolder = clean.substring(0, firstSlash)
                    if (directFolders.add(topFolder)) {
                        result.add(
                            ZipEntryItem(
                                entryName = "$topFolder/",
                                simpleName = topFolder,
                                parentEntryPath = "",
                                isDirectory = true,
                                uncompressedSize = 0L,
                                compressedSize = 0L,
                                lastModified = entry.lastModified,
                                isEncrypted = entry.isEncrypted
                            )
                        )
                    }
                }
            } else {
                val prefix = "$currentArchivePath/"
                if (clean.startsWith(prefix)) {
                    val rem = clean.removePrefix(prefix)
                    if (rem.isNotEmpty()) {
                        val slash = rem.indexOf('/')
                        if (slash == -1) {
                            result.add(entry)
                        } else {
                            val subFolder = rem.substring(0, slash)
                            if (directFolders.add(subFolder)) {
                                result.add(
                                    ZipEntryItem(
                                        entryName = "$prefix$subFolder/",
                                        simpleName = subFolder,
                                        parentEntryPath = currentArchivePath,
                                        isDirectory = true,
                                        uncompressedSize = 0L,
                                        compressedSize = 0L,
                                        lastModified = entry.lastModified,
                                        isEncrypted = entry.isEncrypted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        result.distinctBy { it.entryName }
            .sortedWith(compareByDescending<ZipEntryItem> { it.isDirectory }.thenBy { it.simpleName.lowercase() })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentArchivePath.isNotEmpty()) {
                            currentArchivePath = currentArchivePath.substringBeforeLast('/', "")
                            selectedEntryNames = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (currentArchivePath.isEmpty()) "Archive Root (${allEntries.size} entries)" else "/$currentArchivePath",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (!item.isSafUri) {
                        IconButton(onClick = {
                            val folderName = item.name.substringBeforeLast('.')
                            val dest = File(item.parentPathOrUri, folderName)
                            performExtract(dest, null, password)
                        }) {
                            Icon(Icons.Rounded.Unarchive, contentDescription = "Extract Here")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!item.isSafUri) {
                    Button(
                        onClick = {
                            val folderName = item.name.substringBeforeLast('.')
                            performExtract(File(item.parentPathOrUri, folderName), null, password)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Extract Here") }
                }
                OutlinedButton(
                    onClick = {
                        extractSelectedOnly = false
                        showExtractToPicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Extract To...") }
                if (selectedEntryNames.isNotEmpty()) {
                    Button(
                        onClick = {
                            extractSelectedOnly = true
                            showExtractToPicker = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Extract (${selectedEntryNames.size})") }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "Invalid archive", color = MaterialTheme.colorScheme.error)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visibleEntries.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Empty archive folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(visibleEntries, key = { it.entryName }) { entry ->
                            val isSelected = entry.entryName in selectedEntryNames
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (entry.isDirectory) {
                                            currentArchivePath = entry.entryName.trimEnd('/')
                                            selectedEntryNames = emptySet()
                                        } else {
                                            selectedEntryNames = if (isSelected) {
                                                selectedEntryNames - entry.entryName
                                            } else {
                                                selectedEntryNames + entry.entryName
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isSelected -> Icons.Rounded.CheckCircle
                                            entry.isDirectory -> Icons.Rounded.Folder
                                            else -> Icons.Rounded.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(entry.simpleName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (entry.isEncrypted) {
                                                Spacer(Modifier.width(6.dp))
                                                Icon(Icons.Rounded.Lock, contentDescription = "Encrypted", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Text(
                                            if (entry.isDirectory) "Folder inside archive" else FormatUtils.formatBytes(entry.uncompressedSize),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!item.isSafUri && !entry.isDirectory) {
                                        IconButton(onClick = { renameTargetEntry = entry }) {
                                            Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename entry")
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                zipService.removeEntryFromZip(File(item.pathOrUri), entry.entryName, password)
                                                    .onSuccess { reloadEntries() }
                                                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Remove failed") }
                                            }
                                        }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from ZIP", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        var pwdInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; pendingExtractAfterPassword = null },
            icon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Encrypted Archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter password for \"${item.name}\"")
                    OutlinedTextField(
                        value = pwdInput,
                        onValueChange = { pwdInput = it; passwordError = null },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { msg -> { Text(msg) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    password = pwdInput
                    showPasswordDialog = false
                    val pending = pendingExtractAfterPassword
                    pendingExtractAfterPassword = null
                    if (pending != null) {
                        performExtract(pending.first, pending.second, pwdInput)
                    } else {
                        reloadEntries()
                    }
                }) { Text("Unlock & Extract") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; pendingExtractAfterPassword = null }) { Text("Cancel") }
            }
        )
    }

    if (showExtractToPicker) {
        FolderPickerDialog(
            title = "Extract Archive To",
            roots = roots,
            onlyFileSystemFolders = true,
            loadSubfolders = loadSubfolders,
            onDismiss = { showExtractToPicker = false },
            onSelectFolder = { path, _, _ ->
                showExtractToPicker = false
                val selected = if (extractSelectedOnly) selectedEntryNames.toList() else null
                performExtract(File(path), selected, password)
            }
        )
    }

    renameTargetEntry?.let { entry ->
        var newSimple by remember { mutableStateOf(entry.simpleName) }
        AlertDialog(
            onDismissRequest = { renameTargetEntry = null },
            title = { Text("Rename Archive Entry") },
            text = {
                OutlinedTextField(
                    value = newSimple,
                    onValueChange = { newSimple = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val parent = entry.parentEntryPath
                    val targetPath = if (parent.isEmpty()) newSimple.trim() else "$parent/${newSimple.trim()}"
                    renameTargetEntry = null
                    scope.launch {
                        zipService.renameEntryInZip(File(item.pathOrUri), entry.entryName, targetPath, password)
                            .onSuccess { reloadEntries() }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Rename failed") }
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetEntry = null }) { Text("Cancel") }
            }
        )
    }
}
