package com.example.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ClipboardMode
import com.example.model.ClipboardState
import com.example.model.SortDirection
import com.example.model.SortOption
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.model.ViewMode
import com.example.ui.components.FileTypeIconBadge
import com.example.util.FormatUtils
import com.example.viewmodel.BrowserBreadcrumb
import com.example.viewmodel.BrowserUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesBrowserScreen(
    uiState: BrowserUiState,
    roots: List<StorageRoot>,
    currentRoot: StorageRoot?,
    breadcrumbs: List<BrowserBreadcrumb>,
    viewMode: ViewMode,
    sortOption: SortOption,
    sortDirection: SortDirection,
    searchQuery: String,
    selectedItems: Map<String, StorageItem>,
    clipboard: ClipboardState,
    onSelectRoot: (StorageRoot) -> Unit,
    onAddSafFolder: () -> Unit,
    onGrantAllFilesAccess: () -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onNavigateUp: () -> Boolean,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onToggleViewMode: (ViewMode) -> Unit,
    onSortChange: (SortOption, SortDirection) -> Unit,
    onItemClick: (StorageItem) -> Unit,
    onToggleSelect: (StorageItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onPasteHere: () -> Unit,
    onClearClipboard: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onNewCodeFile: () -> Unit,
    onCreateZip: (List<StorageItem>) -> Unit,
    onCopyItems: (List<StorageItem>) -> Unit,
    onCutItems: (List<StorageItem>) -> Unit,
    onMoveItems: (List<StorageItem>) -> Unit,
    onDeleteItems: (List<StorageItem>) -> Unit,
    onShareItems: (List<StorageItem>) -> Unit,
    onBatchRename: (List<StorageItem>) -> Unit,
    onFavoriteSelected: (List<StorageItem>) -> Unit,
    onContextOpenWith: (StorageItem) -> Unit,
    onContextEdit: (StorageItem) -> Unit,
    onContextRename: (StorageItem) -> Unit,
    onContextDuplicate: (StorageItem) -> Unit,
    onContextSaveCopy: (StorageItem) -> Unit,
    onContextProperties: (StorageItem) -> Unit,
    onContextFavorite: (StorageItem) -> Unit,
    onDropOntoFolder: (draggedPathOrUri: String, targetFolder: StorageItem, isMove: Boolean) -> Unit
) {
    val isSelectionMode = selectedItems.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var quickCreateExpanded by remember { mutableStateOf(false) }
    var pendingDropChoice by remember { mutableStateOf<Pair<String, StorageItem>?>(null) }

    BackHandler(enabled = isSelectionMode || breadcrumbs.size > 1) {
        onNavigateUp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Action Bar or Multi-Select Action Bar
            if (isSelectionMode) {
                MultiSelectTopBar(
                    selectedCount = selectedItems.size,
                    onClear = onClearSelection,
                    onSelectAll = onSelectAll,
                    onCopy = { onCopyItems(selectedItems.values.toList()) },
                    onCut = { onCutItems(selectedItems.values.toList()) },
                    onMove = { onMoveItems(selectedItems.values.toList()) },
                    onDelete = { onDeleteItems(selectedItems.values.toList()) },
                    onShare = { onShareItems(selectedItems.values.toList()) },
                    onZip = { onCreateZip(selectedItems.values.toList()) },
                    onBatchRename = { onBatchRename(selectedItems.values.toList()) },
                    onFavorite = { onFavoriteSelected(selectedItems.values.toList()) }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Files", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Filter folder")
                        }
                        IconButton(
                            onClick = {
                                onToggleViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                            }
                        ) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                                contentDescription = "Toggle Grid/List"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                            }
                            SortDropdownMenu(
                                expanded = showSortMenu,
                                currentOption = sortOption,
                                currentDirection = sortDirection,
                                onDismiss = { showSortMenu = false },
                                onSelect = { opt, dir ->
                                    showSortMenu = false
                                    onSortChange(opt, dir)
                                }
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh folder")
                        }
                    }
                }
            }

            // Optional Filter Search Bar inside Browser
            AnimatedVisibility(visible = showSearchBar && !isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Filter current folder...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear filter")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenGlobalSearch) {
                        Text("All")
                    }
                }
            }

            // Storage Roots Selector Chips
            if (roots.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    roots.forEach { root ->
                        FilterChip(
                            selected = currentRoot?.id == root.id,
                            onClick = { onSelectRoot(root) },
                            label = { Text(root.label) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onAddSafFolder,
                        leadingIcon = { Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Add Folder") }
                    )
                }
            }

            // Breadcrumb Trail
            if (breadcrumbs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breadcrumbs.forEachIndexed { idx, crumb ->
                        val isLast = idx == breadcrumbs.lastIndex
                        Surface(
                            onClick = { onNavigateBreadcrumb(idx) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = crumb.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Clipboard Sticky Paste Banner
            AnimatedVisibility(visible = !clipboard.isEmpty && uiState is BrowserUiState.Success) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (clipboard.mode == ClipboardMode.CUT) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${clipboard.items.size} item(s) ready to ${if (clipboard.mode == ClipboardMode.CUT) "move" else "copy"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onPasteHere,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("paste_here_button")
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste")
                        }
                        IconButton(onClick = onClearClipboard) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear clipboard")
                        }
                    }
                }
            }

            // Main Directory Content States
            when (uiState) {
                BrowserUiState.PermissionRequired -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text("Storage Access Needed", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Grant All Files Access or pick a storage folder via Android's Storage Access Framework to browse and manage real files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onGrantAllFilesAccess) { Text("Grant Storage Access") }
                                OutlinedButton(onClick = onAddSafFolder) { Text("Pick Folder (SAF)") }
                            }
                        }
                    }
                }

                BrowserUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is BrowserUiState.Protected -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = "Protected Folder",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(56.dp)
                            )
                            Text("Protected Folder: ${uiState.folderName}", style = MaterialTheme.typography.titleLarge)
                            Text(
                                uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { onNavigateUp() }) { Text("Go Back") }
                        }
                    }
                }

                is BrowserUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Could Not Open Folder", style = MaterialTheme.typography.titleMedium)
                            Text(uiState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRefresh) { Text("Retry") }
                        }
                    }
                }

                is BrowserUiState.Success -> {
                    if (uiState.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text("This folder is empty", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Tap + Create to add a folder, text file, or code file here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (viewMode == ViewMode.LIST) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.items, key = { it.pathOrUri }) { item ->
                                val isSelected = item.pathOrUri in selectedItems
                                FileRowItem(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    hasClipboard = !clipboard.isEmpty,
                                    onClick = {
                                        if (isSelectionMode) onToggleSelect(item) else onItemClick(item)
                                    },
                                    onLongClick = { onToggleSelect(item) },
                                    onPasteHere = onPasteHere,
                                    onContextOpen = { onItemClick(item) },
                                    onContextOpenWith = { onContextOpenWith(item) },
                                    onContextEdit = { onContextEdit(item) },
                                    onContextShare = { onShareItems(listOf(item)) },
                                    onContextCopy = { onCopyItems(listOf(item)) },
                                    onContextCut = { onCutItems(listOf(item)) },
                                    onContextMove = { onMoveItems(listOf(item)) },
                                    onContextRename = { onContextRename(item) },
                                    onContextDuplicate = { onContextDuplicate(item) },
                                    onContextSaveCopy = { onContextSaveCopy(item) },
                                    onContextProperties = { onContextProperties(item) },
                                    onContextFavorite = { onContextFavorite(item) },
                                    onContextZip = { onCreateZip(listOf(item)) },
                                    onContextDelete = { onDeleteItems(listOf(item)) },
                                    onDropReceived = { draggedPath ->
                                        if (item.isDirectory && !item.isProtected && draggedPath != item.pathOrUri) {
                                            pendingDropChoice = draggedPath to item
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 116.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.items, key = { it.pathOrUri }) { item ->
                                val isSelected = item.pathOrUri in selectedItems
                                FileGridItem(
                                    item = item,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) onToggleSelect(item) else onItemClick(item)
                                    },
                                    onLongClick = { onToggleSelect(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // + Create Expandable Quick Menu FAB
        if (uiState is BrowserUiState.Success || uiState is BrowserUiState.Error) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(visible = quickCreateExpanded) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickFabOption("New Folder", Icons.Rounded.CreateNewFolder) {
                            quickCreateExpanded = false
                            onNewFolder()
                        }
                        QuickFabOption("New File", Icons.Rounded.InsertDriveFile) {
                            quickCreateExpanded = false
                            onNewFile()
                        }
                        QuickFabOption("New Code File", Icons.Rounded.Code) {
                            quickCreateExpanded = false
                            onNewCodeFile()
                        }
                        if (selectedItems.isNotEmpty()) {
                            QuickFabOption("Create ZIP", Icons.Rounded.FolderZip) {
                                quickCreateExpanded = false
                                onCreateZip(selectedItems.values.toList())
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { quickCreateExpanded = !quickCreateExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("quick_create_fab")
                ) {
                    Icon(
                        imageVector = if (quickCreateExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = "Create"
                    )
                }
            }
        }
    }

    pendingDropChoice?.let { (draggedPath, targetFolder) ->
        AlertDialog(
            onDismissRequest = { pendingDropChoice = null },
            title = { Text("Drop into '${targetFolder.name}'") },
            text = { Text("Choose whether to move or copy the dragged item into ${targetFolder.name}.") },
            confirmButton = {
                Button(onClick = {
                    pendingDropChoice = null
                    onDropOntoFolder(draggedPath, targetFolder, true)
                }) { Text("Move Here") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        pendingDropChoice = null
                        onDropOntoFolder(draggedPath, targetFolder, false)
                    }) { Text("Copy Here") }
                    TextButton(onClick = { pendingDropChoice = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun QuickFabOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRowItem(
    item: StorageItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    hasClipboard: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPasteHere: () -> Unit,
    onContextOpen: () -> Unit,
    onContextOpenWith: () -> Unit,
    onContextEdit: () -> Unit,
    onContextShare: () -> Unit,
    onContextCopy: () -> Unit,
    onContextCut: () -> Unit,
    onContextMove: () -> Unit,
    onContextRename: () -> Unit,
    onContextDuplicate: () -> Unit,
    onContextSaveCopy: () -> Unit,
    onContextProperties: () -> Unit,
    onContextFavorite: () -> Unit,
    onContextZip: () -> Unit,
    onContextDelete: () -> Unit,
    onDropReceived: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var isDropTargetHighlighted by remember { mutableStateOf(false) }

    val dropTarget = remember(item.pathOrUri, item.isDirectory) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                if (item.isDirectory && !item.isProtected) isDropTargetHighlighted = true
            }
            override fun onExited(event: DragAndDropEvent) {
                isDropTargetHighlighted = false
            }
            override fun onEnded(event: DragAndDropEvent) {
                isDropTargetHighlighted = false
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDropTargetHighlighted = false
                val clip = event.toAndroidDragEvent().clipData
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
                if (!text.isNullOrBlank()) {
                    onDropReceived(text)
                    return true
                }
                return false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            isDropTargetHighlighted -> MaterialTheme.colorScheme.primaryContainer
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isHidden) 0.65f else 1f)
            .border(
                width = if (isSelected || isDropTargetHighlighted) 1.5.dp else 1.dp,
                color = if (isSelected || isDropTargetHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .dragAndDropSource {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        onLongClick()
                        startTransfer(
                            DragAndDropTransferData(
                                clipData = ClipData.newPlainText("storage_item", item.pathOrUri)
                            )
                        )
                    }
                )
            }
            .then(
                if (item.isDirectory) {
                    Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        },
                        target = dropTarget
                    )
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                FileTypeIconBadge(item = item, size = 46.dp)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    if (item.isDirectory) {
                        if (item.isProtected) append("Protected by Android")
                        else {
                            item.childCount?.let { append("$it items • ") }
                            append(FormatUtils.formatShortDate(item.lastModified))
                        }
                    } else {
                        append(FormatUtils.formatBytes(item.size))
                        append(" • ")
                        append(FormatUtils.formatShortDate(item.lastModified))
                    }
                    if (item.isHidden) append(" • Hidden")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More actions for ${item.name}")
                    }
                    FileContextDropdownMenu(
                        expanded = showMenu,
                        item = item,
                        hasClipboard = hasClipboard,
                        onDismiss = { showMenu = false },
                        onOpen = { showMenu = false; onContextOpen() },
                        onOpenWith = { showMenu = false; onContextOpenWith() },
                        onEdit = { showMenu = false; onContextEdit() },
                        onShare = { showMenu = false; onContextShare() },
                        onCopy = { showMenu = false; onContextCopy() },
                        onCut = { showMenu = false; onContextCut() },
                        onPaste = { showMenu = false; onPasteHere() },
                        onMove = { showMenu = false; onContextMove() },
                        onRename = { showMenu = false; onContextRename() },
                        onDuplicate = { showMenu = false; onContextDuplicate() },
                        onSaveCopy = { showMenu = false; onContextSaveCopy() },
                        onProperties = { showMenu = false; onContextProperties() },
                        onFavorite = { showMenu = false; onContextFavorite() },
                        onZip = { showMenu = false; onContextZip() },
                        onDelete = { showMenu = false; onContextDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    item: StorageItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isHidden) 0.65f else 1f)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileTypeIconBadge(item = item, size = 54.dp)
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.isDirectory) (if (item.isProtected) "Protected" else "Folder") else FormatUtils.formatBytes(item.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FileContextDropdownMenu(
    expanded: Boolean,
    item: StorageItem,
    hasClipboard: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onSaveCopy: () -> Unit,
    onProperties: () -> Unit,
    onFavorite: () -> Unit,
    onZip: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Open") }, onClick = onOpen)
        if (!item.isDirectory) {
            DropdownMenuItem(text = { Text("Open With") }, onClick = onOpenWith)
            DropdownMenuItem(text = { Text("Edit as Text/Code") }, onClick = onEdit)
            DropdownMenuItem(text = { Text("Share") }, onClick = onShare)
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Copy") }, onClick = onCopy)
        DropdownMenuItem(text = { Text("Cut") }, onClick = onCut)
        if (hasClipboard) {
            DropdownMenuItem(text = { Text("Paste Here") }, onClick = onPaste)
        }
        DropdownMenuItem(text = { Text("Move To...") }, onClick = onMove)
        DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
        DropdownMenuItem(text = { Text("Duplicate") }, onClick = onDuplicate)
        if (!item.isDirectory) {
            DropdownMenuItem(text = { Text("Save a Copy") }, onClick = onSaveCopy)
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (item.isFavorite) "Remove Favorite" else "Add to Favorites") },
            onClick = onFavorite
        )
        if (!item.isSafUri) {
            DropdownMenuItem(text = { Text("Create ZIP") }, onClick = onZip)
        }
        DropdownMenuItem(text = { Text("Properties") }, onClick = onProperties)
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = onDelete
        )
    }
}

@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onZip: () -> Unit,
    onBatchRename: () -> Unit,
    onFavorite: () -> Unit
) {
    var overflow by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") }
            Text("$selectedCount selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = onCut) { Icon(Icons.Rounded.ContentCut, contentDescription = "Cut") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            Box {
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More selection actions")
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) },
                        text = { Text("Move To...") },
                        onClick = { overflow = false; onMove() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        text = { Text("Share") },
                        onClick = { overflow = false; onShare() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.FolderZip, null) },
                        text = { Text("Create ZIP") },
                        onClick = { overflow = false; onZip() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        text = { Text("Batch Rename") },
                        onClick = { overflow = false; onBatchRename() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Star, null) },
                        text = { Text("Add to Favorites") },
                        onClick = { overflow = false; onFavorite() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentOption: SortOption,
    currentDirection: SortDirection,
    onDismiss: () -> Unit,
    onSelect: (SortOption, SortDirection) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val entries = listOf(
            Triple("Name (A–Z)", SortOption.NAME, SortDirection.ASCENDING),
            Triple("Name (Z–A)", SortOption.NAME, SortDirection.DESCENDING),
            Triple("Date (Newest)", SortOption.DATE, SortDirection.DESCENDING),
            Triple("Date (Oldest)", SortOption.DATE, SortDirection.ASCENDING),
            Triple("Size (Largest)", SortOption.SIZE, SortDirection.DESCENDING),
            Triple("Size (Smallest)", SortOption.SIZE, SortDirection.ASCENDING),
            Triple("Type", SortOption.TYPE, SortDirection.ASCENDING)
        )
        entries.forEach { (label, opt, dir) ->
            val selected = currentOption == opt && currentDirection == dir
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (selected) "✓ $label" else label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSelect(opt, dir) }
            )
        }
    }
}
