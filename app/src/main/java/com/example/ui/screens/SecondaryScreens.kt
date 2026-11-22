package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.FavoriteEntity
import com.example.data.db.OperationHistoryEntity
import com.example.data.db.RecentFileEntity
import com.example.data.db.RecycleBinEntity
import com.example.model.FileCategory
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.components.FolderPickerDialog
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.util.FormatUtils
import java.io.File

// -----------------------------------------------------------------------------
// 1. FILTERED CATEGORY SCREEN
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredCategoryScreen(
    category: FileCategory,
    items: List<StorageItem>,
    onBack: () -> Unit,
    onScanStorage: () -> Unit,
    onOpenItem: (StorageItem) -> Unit,
    onShareItem: (StorageItem) -> Unit,
    onRenameItem: (StorageItem) -> Unit,
    onDeleteItem: (StorageItem) -> Unit,
    onPropertiesItem: (StorageItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("${category.displayName} (${items.size})") }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No files found in ${category.displayName}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scan your device storage to discover and index ${category.displayName.lowercase()} files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onScanStorage) { Text("Scan Storage") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.pathOrUri }) { item ->
                    SimpleFileActionCard(
                        item = item,
                        onOpen = { onOpenItem(item) },
                        onShare = { onShareItem(item) },
                        onRename = { onRenameItem(item) },
                        onProperties = { onPropertiesItem(item) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. SEARCH SCREEN
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    results: List<StorageItem>,
    isSearching: Boolean,
    hasCurrentFolder: Boolean,
    currentFolderLabel: String,
    onSearch: (query: String, category: FileCategory?, onlyCurrentFolder: Boolean) -> Unit,
    onOpenItem: (StorageItem) -> Unit,
    onPropertiesItem: (StorageItem) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<FileCategory?>(null) }
    var onlyCurrentFolder by remember { mutableStateOf(false) }

    LaunchedEffect(query, selectedCategory, onlyCurrentFolder) {
        onSearch(query, selectedCategory, onlyCurrentFolder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Search Files") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search filename or extension (e.g. main.py, pdf)") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasCurrentFolder) {
                    FilterChip(
                        selected = onlyCurrentFolder,
                        onClick = { onlyCurrentFolder = !onlyCurrentFolder },
                        label = { Text("In $currentFolderLabel") }
                    )
                }
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Types") }
                )
                FileCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.displayName) }
                    )
                }
            }

            if (isSearching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank() && selectedCategory == null) "Type a filename or extension to search indexed storage."
                        else "No matching files found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.pathOrUri }) { item ->
                        Surface(
                            onClick = { onOpenItem(item) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FileTypeIconBadge(item = item, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${if (item.isDirectory) "Folder" else FormatUtils.formatBytes(item.size)} • ${FormatUtils.formatShortDate(item.lastModified)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        item.parentPathOrUri,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { onPropertiesItem(item) }) { Text("Info") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. FAVORITES SCREEN
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<FavoriteEntity>,
    checkExists: suspend (String, Boolean) -> Boolean,
    onOpenFavorite: (StorageItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRenameItem: (StorageItem) -> Unit,
    onShareItem: (StorageItem) -> Unit,
    onPropertiesItem: (StorageItem) -> Unit,
    onBack: () -> Unit
) {
    val availabilityMap = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(favorites) {
        favorites.forEach { fav ->
            availabilityMap[fav.pathOrUri] = checkExists(fav.pathOrUri, fav.isSafUri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Favorites (${favorites.size})") }
            )
        }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.StarOutline, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No Favorites Yet", style = MaterialTheme.typography.titleMedium)
                    Text("Mark files or folders as favorites from any menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites, key = { it.pathOrUri }) { fav ->
                    val available = availabilityMap[fav.pathOrUri] ?: true
                    val item = StorageItem(
                        pathOrUri = fav.pathOrUri,
                        name = fav.name,
                        parentPathOrUri = fav.parentPathOrUri,
                        isDirectory = fav.isDirectory,
                        isSafUri = fav.isSafUri,
                        size = fav.size,
                        lastModified = fav.lastModified,
                        extension = fav.extension,
                        mimeType = fav.mimeType,
                        isFavorite = true
                    )
                    var menuOpen by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable(enabled = available) { onOpenFavorite(item) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileTypeIconBadge(item = item, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fav.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!available) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Warning, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Unavailable (moved or deleted)", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                                    }
                                } else {
                                    Text(fav.parentPathOrUri, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (!available) {
                                TextButton(onClick = { onRemoveFavorite(fav.pathOrUri) }) { Text("Clean Up") }
                            } else {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Favorite actions") }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpenFavorite(item) })
                                        DropdownMenuItem(text = { Text("Remove Favorite") }, onClick = { menuOpen = false; onRemoveFavorite(fav.pathOrUri) })
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRenameItem(item) })
                                        if (!item.isDirectory) {
                                            DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShareItem(item) })
                                        }
                                        DropdownMenuItem(text = { Text("Properties") }, onClick = { menuOpen = false; onPropertiesItem(item) })
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

// -----------------------------------------------------------------------------
// 4. RECENT FILES SCREEN
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentFilesScreen(
    recentFiles: List<RecentFileEntity>,
    recentModifiedFiles: List<StorageItem>,
    onOpenRecent: (StorageItem) -> Unit,
    onShareRecent: (StorageItem) -> Unit,
    onRenameItem: (StorageItem) -> Unit,
    onBatchRename: (List<StorageItem>) -> Unit,
    onDeleteItems: (List<StorageItem>) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onScanStorage: () -> Unit,
    onBack: () -> Unit
) {
    var filterTab by remember { mutableStateOf(0) } // 0 = All, 1 = Opened/Edited, 2 = Latest on Device
    var selectMode by remember { mutableStateOf(false) }
    val selectedMap = remember { mutableStateMapOf<String, StorageItem>() }

    val openedItems = remember(recentFiles) {
        recentFiles.map { entry ->
            StorageItem(
                pathOrUri = entry.pathOrUri,
                name = entry.name,
                parentPathOrUri = entry.parentPathOrUri,
                isDirectory = entry.isDirectory,
                isSafUri = entry.isSafUri,
                size = entry.size,
                lastModified = entry.lastModified,
                extension = entry.extension,
                mimeType = entry.mimeType
            ) to "${entry.actionLabel} • ${FormatUtils.formatShortDate(entry.timestamp)}"
        }
    }

    val combinedItems = remember(filterTab, openedItems, recentModifiedFiles) {
        when (filterTab) {
            1 -> openedItems
            2 -> recentModifiedFiles.map { it to "Modified • ${FormatUtils.formatShortDate(it.lastModified)}" }
            else -> {
                val seen = HashSet<String>()
                val merged = mutableListOf<Pair<StorageItem, String>>()
                openedItems.forEach { pair ->
                    if (seen.add(pair.first.pathOrUri)) merged.add(pair)
                }
                recentModifiedFiles.forEach { item ->
                    if (seen.add(item.pathOrUri)) {
                        merged.add(item to "Modified • ${FormatUtils.formatShortDate(item.lastModified)}")
                    }
                }
                merged
            }
        }
    }

    val isSelecting = selectMode || selectedMap.isNotEmpty()

    Scaffold(
        topBar = {
            if (isSelecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            selectMode = false
                            selectedMap.clear()
                        }) {
                            Icon(Icons.Rounded.Close, "Cancel selection")
                        }
                    },
                    title = { Text("${selectedMap.size} selected") },
                    actions = {
                        IconButton(onClick = {
                            combinedItems.forEach { (item, _) -> selectedMap[item.pathOrUri] = item }
                        }) {
                            Icon(Icons.Rounded.SelectAll, "Select all")
                        }
                        if (selectedMap.isNotEmpty()) {
                            IconButton(onClick = {
                                val list = selectedMap.values.toList()
                                selectMode = false
                                selectedMap.clear()
                                if (list.size == 1) onRenameItem(list.first()) else onBatchRename(list)
                            }) {
                                Icon(Icons.Rounded.DriveFileRenameOutline, "Rename selected")
                            }
                            IconButton(onClick = {
                                val list = selectedMap.values.toList()
                                selectMode = false
                                selectedMap.clear()
                                onDeleteItems(list)
                            }) {
                                Icon(Icons.Rounded.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    title = { Text("Recent Files (${combinedItems.size})") },
                    actions = {
                        if (combinedItems.isNotEmpty()) {
                            IconButton(onClick = { selectMode = true }) {
                                Icon(Icons.Rounded.Checklist, "Select multiple")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterTab == 0,
                    onClick = { filterTab = 0 },
                    label = { Text("All Recent") }
                )
                FilterChip(
                    selected = filterTab == 1,
                    onClick = { filterTab = 1 },
                    label = { Text("Opened & Edited (${recentFiles.size})") }
                )
                FilterChip(
                    selected = filterTab == 2,
                    onClick = { filterTab = 2 },
                    label = { Text("Device Latest (${recentModifiedFiles.size})") }
                )
            }

            if (combinedItems.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("No recent files yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Open files or scan your device storage to see your latest files here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onScanStorage) { Text("Scan Storage") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(combinedItems, key = { it.first.pathOrUri }) { (item, subLabel) ->
                        var menuOpen by remember { mutableStateOf(false) }
                        val isSelected = item.pathOrUri in selectedMap
                        val isInOpenedTable = recentFiles.any { it.pathOrUri == item.pathOrUri }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelecting) {
                                            if (isSelected) selectedMap.remove(item.pathOrUri) else selectedMap[item.pathOrUri] = item
                                        } else {
                                            onOpenRecent(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelected) selectedMap.remove(item.pathOrUri) else selectedMap[item.pathOrUri] = item
                                    }
                                )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FileTypeIconBadge(item = item, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "$subLabel • ${FormatUtils.formatBytes(item.size)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelecting) {
                                    IconButton(onClick = {
                                        if (isSelected) selectedMap.remove(item.pathOrUri) else selectedMap[item.pathOrUri] = item
                                    }) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = "Select",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Recent actions") }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpenRecent(item) })
                                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRenameItem(item) })
                                            DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShareRecent(item) })
                                            if (isInOpenedTable) {
                                                DropdownMenuItem(text = { Text("Remove from Recent") }, onClick = { menuOpen = false; onRemoveRecent(item.pathOrUri) })
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                onClick = { menuOpen = false; onDeleteItems(listOf(item)) }
                                            )
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
}

// -----------------------------------------------------------------------------
// 5. RECYCLE BIN SCREEN
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    items: List<RecycleBinEntity>,
    roots: List<StorageRoot>,
    loadSubfolders: suspend (String, Boolean) -> List<StorageItem>,
    onRestore: (RecycleBinEntity, File?) -> Unit,
    onDeleteForever: (RecycleBinEntity) -> Unit,
    onEmptyAll: () -> Unit,
    onBack: () -> Unit
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingFallbackRestore by remember { mutableStateOf<RecycleBinEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Recycle Bin (${items.size})") },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text("Empty", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Recycle Bin is Empty", style = MaterialTheme.typography.titleMedium)
                    Text("Deleted local files appear here so you can restore them anytime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${if (entry.isDirectory) "Folder" else FormatUtils.formatBytes(entry.size)} • Deleted ${FormatUtils.formatShortDate(entry.deletedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Original: ${entry.originalParentPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val origParent = File(entry.originalParentPath)
                                        if (origParent.exists() && origParent.canWrite()) {
                                            onRestore(entry, null)
                                        } else {
                                            pendingFallbackRestore = entry
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.RestoreFromTrash, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restore")
                                }
                                OutlinedButton(
                                    onClick = { onDeleteForever(entry) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("This will permanently delete all ${items.size} item(s). This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { confirmEmpty = false; onEmptyAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Empty Forever") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            }
        )
    }

    pendingFallbackRestore?.let { entry ->
        FolderPickerDialog(
            title = "Original folder unavailable — Restore '${entry.name}' To",
            roots = roots,
            onlyFileSystemFolders = true,
            loadSubfolders = loadSubfolders,
            onDismiss = { pendingFallbackRestore = null },
            onSelectFolder = { path, _, _ ->
                pendingFallbackRestore = null
                onRestore(entry, File(path))
            }
        )
    }
}

// -----------------------------------------------------------------------------
// 6. OPERATION HISTORY SCREEN
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationHistoryScreen(
    history: List<OperationHistoryEntity>,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Recent Operations") },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No file operations logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (entry.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                contentDescription = if (entry.success) "Success" else "Failed",
                                tint = if (entry.success) SuccessGreen else ErrorRed
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${entry.operationType}: ${entry.targetName}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(FormatUtils.formatDate(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleFileActionCard(
    item: StorageItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileTypeIconBadge(item = item, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${FormatUtils.formatBytes(item.size)} • ${FormatUtils.formatShortDate(item.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Actions") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Properties") }, onClick = { menuOpen = false; onProperties() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
