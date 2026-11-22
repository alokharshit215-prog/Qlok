package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BlueFileManagerApp
import com.example.data.db.FavoriteEntity
import com.example.data.db.OperationHistoryEntity
import com.example.data.db.RecentFileEntity
import com.example.data.db.RecycleBinEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.storage.MimeAndCategoryHelper
import com.example.model.ApkMetadata
import com.example.model.BatchRenameConfig
import com.example.model.ClipboardMode
import com.example.model.ClipboardState
import com.example.model.ConflictAction
import com.example.model.ConflictPrompt
import com.example.model.FileCategory
import com.example.model.OperationProgress
import com.example.model.ScanProgress
import com.example.model.SortDirection
import com.example.model.SortOption
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.model.StorageStats
import com.example.model.ThemeMode
import com.example.model.ViewMode
import com.example.model.ZipCompressionMode
import com.example.util.FormatUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserBreadcrumb(
    val label: String,
    val pathOrUri: String,
    val isSafUri: Boolean
)

sealed class BrowserUiState {
    data object PermissionRequired : BrowserUiState()
    data object Loading : BrowserUiState()
    data class Protected(val folderName: String, val message: String) : BrowserUiState()
    data class Error(val message: String) : BrowserUiState()
    data class Success(val items: List<StorageItem>) : BrowserUiState()
}

data class PendingDeleteRequest(
    val items: List<StorageItem>,
    val canMoveToRecycleBin: Boolean
)

data class PendingExtensionRenameWarning(
    val item: StorageItem,
    val newName: String
)

class MainFileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BlueFileManagerApp
    private val repo = app.repository
    val settingsStore: SettingsDataStore = app.settingsDataStore
    val storageAccessHelper = app.storageAccessHelper
    val zipService = app.zipArchiveService

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val scanProgress: StateFlow<ScanProgress> = repo.scanProgress
    val operationProgress: StateFlow<OperationProgress> = repo.operationProgress

    val indexedFileCount: StateFlow<Int> = repo.indexedFileCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val indexedFolderCount: StateFlow<Int> = repo.indexedFolderCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val recentFiles: StateFlow<List<RecentFileEntity>> = repo.recentFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentModifiedFiles: StateFlow<List<StorageItem>> = repo.observeRecentlyModifiedFiles(includeHidden = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites: StateFlow<List<FavoriteEntity>> = repo.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteKeys: StateFlow<Set<String>> = repo.favoriteKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val recycleBinItems: StateFlow<List<RecycleBinEntity>> = repo.recycleBinItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val operationHistory: StateFlow<List<OperationHistoryEntity>> = repo.operationHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _hasStorageAccess = MutableStateFlow(storageAccessHelper.hasAnyStorageAccess())
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    private val _storageRoots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val storageRoots: StateFlow<List<StorageRoot>> = _storageRoots.asStateFlow()

    private val _storageBytes = MutableStateFlow(storageAccessHelper.getPrimaryStorageBytes())

    private val _largeFilesThresholdMb = MutableStateFlow(10)

    val storageStats: StateFlow<StorageStats> = combine(
        _storageBytes,
        repo.categoryAggregations,
        indexedFileCount,
        indexedFolderCount,
        repo.observeLargeFiles(10L * 1024L * 1024L)
    ) { bytesPair, aggs, fileCount, folderCount, _ ->
        val (total, free) = bytesPair
        val used = (total - free).coerceAtLeast(0L)
        val pct = if (total > 0L) (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
        val sizeMap = mutableMapOf<FileCategory, Long>()
        val countMap = mutableMapOf<FileCategory, Int>()
        aggs.forEach { row ->
            val cat = runCatching { FileCategory.valueOf(row.category) }.getOrNull()
            if (cat != null) {
                sizeMap[cat] = row.totalSize
                countMap[cat] = row.itemCount
            }
        }
        StorageStats(
            totalBytes = total,
            usedBytes = used,
            freeBytes = free,
            usagePercent = pct,
            categorySizes = sizeMap,
            categoryCounts = countMap,
            totalIndexedFiles = fileCount,
            totalIndexedFolders = folderCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    private val _largeFiles = MutableStateFlow<List<StorageItem>>(emptyList())
    val largeFiles: StateFlow<List<StorageItem>> = _largeFiles.asStateFlow()

    // File Browser State
    private val _currentRoot = MutableStateFlow<StorageRoot?>(null)
    val currentRoot: StateFlow<StorageRoot?> = _currentRoot.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<BrowserBreadcrumb>>(emptyList())
    val breadcrumbs: StateFlow<List<BrowserBreadcrumb>> = _breadcrumbs.asStateFlow()

    private val _rawDirectoryItems = MutableStateFlow<List<StorageItem>>(emptyList())
    private val _browserStatus = MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _browserSearchQuery = MutableStateFlow("")
    val browserSearchQuery: StateFlow<String> = _browserSearchQuery.asStateFlow()

    val browserUiState: StateFlow<BrowserUiState> = combine(
        _browserStatus,
        _rawDirectoryItems,
        _sortOption,
        _sortDirection,
        _browserSearchQuery,
        favoriteKeys
    ) { values ->
        val status = values[0] as BrowserUiState
        @Suppress("UNCHECKED_CAST")
        val raw = values[1] as List<StorageItem>
        val sortOpt = values[2] as SortOption
        val sortDir = values[3] as SortDirection
        val query = values[4] as String
        @Suppress("UNCHECKED_CAST")
        val favs = values[5] as Set<String>

        if (status !is BrowserUiState.Success) {
            status
        } else {
            val withFavs = raw.map { it.copy(isFavorite = it.pathOrUri in favs) }
            val filtered = if (query.isBlank()) {
                withFavs
            } else {
                withFavs.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            BrowserUiState.Success(FormatUtils.sortItems(filtered, sortOpt, sortDir))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowserUiState.Loading)

    // Selection & Clipboard
    private val _selectedItems = MutableStateFlow<Map<String, StorageItem>>(emptyMap())
    val selectedItems: StateFlow<Map<String, StorageItem>> = _selectedItems.asStateFlow()

    private val _clipboard = MutableStateFlow(ClipboardState())
    val clipboard: StateFlow<ClipboardState> = _clipboard.asStateFlow()

    // Conflict Dialog State
    private val _conflictPrompt = MutableStateFlow<ConflictPrompt?>(null)
    val conflictPrompt: StateFlow<ConflictPrompt?> = _conflictPrompt.asStateFlow()
    private var pendingConflictDeferred: CompletableDeferred<Pair<ConflictAction, Boolean>>? = null

    // Pending Confirmations
    private val _pendingDeleteRequest = MutableStateFlow<PendingDeleteRequest?>(null)
    val pendingDeleteRequest: StateFlow<PendingDeleteRequest?> = _pendingDeleteRequest.asStateFlow()

    private val _pendingExtensionWarning = MutableStateFlow<PendingExtensionRenameWarning?>(null)
    val pendingExtensionWarning: StateFlow<PendingExtensionRenameWarning?> = _pendingExtensionWarning.asStateFlow()

    // Global Search State
    private val _globalSearchResults = MutableStateFlow<List<StorageItem>>(emptyList())
    val globalSearchResults: StateFlow<List<StorageItem>> = _globalSearchResults.asStateFlow()
    private val _isSearchingGlobal = MutableStateFlow(false)
    val isSearchingGlobal: StateFlow<Boolean> = _isSearchingGlobal.asStateFlow()

    // Events (Snackbars / Messages)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    private var scanJob: Job? = null
    private var operationCancelled = false

    // App Lock State
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsStore.settingsFlow.first()
            _viewMode.value = s.defaultViewMode
            _sortOption.value = s.defaultSortOption
            _sortDirection.value = s.defaultSortDirection
            _largeFilesThresholdMb.value = s.largeFileThresholdMb
            _isAppUnlocked.value = !s.appLockEnabled
            if (s.recycleBinEnabled && s.recycleBinAutoCleanDays > 0) {
                repo.cleanupExpiredRecycleBin(s.recycleBinAutoCleanDays)
            }
            refreshStorageAccessState()
            observeLargeFilesForThreshold(s.largeFileThresholdMb)
        }
    }

    private var largeFilesJob: Job? = null
    private fun observeLargeFilesForThreshold(thresholdMb: Int) {
        largeFilesJob?.cancel()
        largeFilesJob = viewModelScope.launch {
            val minBytes = thresholdMb.toLong() * 1024L * 1024L
            repo.observeLargeFiles(minBytes).collect { list ->
                _largeFiles.value = list
            }
        }
    }

    fun unlockAppWithPin(pin: String): Boolean {
        val storedHash = settings.value.appLockPinHash
        val ok = SettingsDataStore.verifyPin(pin, storedHash)
        if (ok) _isAppUnlocked.value = true
        return ok
    }

    fun unlockAppWithBiometric() {
        _isAppUnlocked.value = true
    }

    fun refreshStorageAccessState() {
        val granted = storageAccessHelper.hasAnyStorageAccess()
        _hasStorageAccess.value = granted
        _storageBytes.value = storageAccessHelper.getPrimaryStorageBytes()
        val roots = storageAccessHelper.discoverAccessibleRoots()
        _storageRoots.value = roots

        if (!granted || roots.isEmpty()) {
            _browserStatus.value = BrowserUiState.PermissionRequired
            _currentRoot.value = null
            _breadcrumbs.value = emptyList()
            _rawDirectoryItems.value = emptyList()
        } else {
            val activeCrumb = _breadcrumbs.value.lastOrNull()
            if (activeCrumb != null) {
                loadDirectory(activeCrumb.pathOrUri, activeCrumb.isSafUri)
            } else {
                openRoot(roots.first())
            }
        }
    }

    fun onSafTreeGranted(uri: Uri) {
        viewModelScope.launch {
            repo.registerSafRoot(uri)
            refreshStorageAccessState()
            val roots = storageAccessHelper.discoverAccessibleRoots()
            roots.firstOrNull { it.pathOrUri == uri.toString() }?.let { openRoot(it) }
            _messages.tryEmit("Storage folder added")
        }
    }

    fun startStorageScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val res = repo.scanAllAccessibleStorage()
            res.onSuccess { progress ->
                _messages.tryEmit("Scan complete: ${progress.filesScanned} files, ${progress.foldersScanned} folders")
                refreshCurrentDirectory()
            }.onFailure { err ->
                if (err !is kotlinx.coroutines.CancellationException) {
                    _messages.tryEmit(err.message ?: "Storage scan failed")
                }
            }
        }
    }

    fun cancelStorageScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun clearFileIndexOnly() {
        viewModelScope.launch {
            repo.clearFileIndex()
            _messages.tryEmit("Local file index cleared. Your real files were not modified.")
        }
    }

    fun openRoot(root: StorageRoot) {
        _currentRoot.value = root
        _breadcrumbs.value = listOf(BrowserBreadcrumb(root.label, root.pathOrUri, root.isSafUri))
        _selectedItems.value = emptyMap()
        _browserSearchQuery.value = ""
        loadDirectory(root.pathOrUri, root.isSafUri)
    }

    fun openFolder(item: StorageItem) {
        if (!item.isDirectory) return
        if (item.isProtected) {
            _breadcrumbs.value = _breadcrumbs.value + BrowserBreadcrumb(item.name, item.pathOrUri, item.isSafUri)
            _browserStatus.value = BrowserUiState.Protected(
                folderName = item.name,
                message = "Android restricts access to ${item.name} (such as Android/data or Android/obb) for system security. Blue File Manager respects Android's storage protection."
            )
            _rawDirectoryItems.value = emptyList()
            return
        }
        _breadcrumbs.value = _breadcrumbs.value + BrowserBreadcrumb(item.name, item.pathOrUri, item.isSafUri)
        _selectedItems.value = emptyMap()
        loadDirectory(item.pathOrUri, item.isSafUri)
    }

    fun navigateToLocation(pathOrUri: String, isSafUri: Boolean) {
        if (isSafUri) {
            val root = _storageRoots.value.firstOrNull { it.pathOrUri == pathOrUri }
            if (root != null) {
                openRoot(root)
            } else {
                _breadcrumbs.value = listOf(BrowserBreadcrumb("Folder", pathOrUri, true))
                loadDirectory(pathOrUri, true)
            }
            return
        }
        val file = File(pathOrUri)
        val matchingRoot = _storageRoots.value.firstOrNull { !it.isSafUri && file.absolutePath.startsWith(it.pathOrUri) }
        if (matchingRoot != null) {
            val crumbs = mutableListOf(BrowserBreadcrumb(matchingRoot.label, matchingRoot.pathOrUri, false))
            val relative = file.absolutePath.removePrefix(matchingRoot.pathOrUri).trim('/')
            if (relative.isNotEmpty()) {
                var running = matchingRoot.pathOrUri.trimEnd('/')
                relative.split('/').forEach { seg ->
                    running = "$running/$seg"
                    crumbs.add(BrowserBreadcrumb(seg, running, false))
                }
            }
            _currentRoot.value = matchingRoot
            _breadcrumbs.value = crumbs
        } else {
            _breadcrumbs.value = listOf(BrowserBreadcrumb(file.name.ifBlank { pathOrUri }, pathOrUri, false))
        }
        loadDirectory(pathOrUri, false)
    }

    fun navigateToBreadcrumb(index: Int) {
        val list = _breadcrumbs.value
        if (index !in list.indices) return
        val updated = list.subList(0, index + 1)
        _breadcrumbs.value = updated
        _selectedItems.value = emptyMap()
        val target = updated.last()
        loadDirectory(target.pathOrUri, target.isSafUri)
    }

    fun navigateUp(): Boolean {
        if (_selectedItems.value.isNotEmpty()) {
            _selectedItems.value = emptyMap()
            return true
        }
        val list = _breadcrumbs.value
        if (list.size <= 1) return false
        navigateToBreadcrumb(list.size - 2)
        return true
    }

    fun refreshCurrentDirectory() {
        _storageBytes.value = storageAccessHelper.getPrimaryStorageBytes()
        val current = _breadcrumbs.value.lastOrNull() ?: return
        loadDirectory(current.pathOrUri, current.isSafUri)
    }

    private fun loadDirectory(pathOrUri: String, isSafUri: Boolean) {
        viewModelScope.launch {
            _browserStatus.value = BrowserUiState.Loading
            val includeHidden = settings.value.showHiddenFiles
            val res = repo.listDirectory(pathOrUri, isSafUri, includeHidden)
            res.onSuccess { items ->
                _rawDirectoryItems.value = items
                _browserStatus.value = BrowserUiState.Success(items)
            }.onFailure { err ->
                _rawDirectoryItems.value = emptyList()
                _browserStatus.value = if (err is SecurityException) {
                    BrowserUiState.Protected(
                        folderName = _breadcrumbs.value.lastOrNull()?.label ?: "Protected Folder",
                        message = err.message ?: "Android restricts access to this protected directory."
                    )
                } else {
                    BrowserUiState.Error(err.message ?: "Unable to read folder")
                }
            }
        }
    }

    fun currentFolderInfo(): BrowserBreadcrumb? = _breadcrumbs.value.lastOrNull()

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        viewModelScope.launch { settingsStore.setDefaultViewMode(mode) }
    }

    fun setSort(option: SortOption, direction: SortDirection) {
        _sortOption.value = option
        _sortDirection.value = direction
        viewModelScope.launch { settingsStore.setDefaultSort(option, direction) }
    }

    fun setBrowserSearchQuery(query: String) {
        _browserSearchQuery.value = query
    }

    // Selection
    fun toggleItemSelection(item: StorageItem) {
        val map = _selectedItems.value.toMutableMap()
        if (item.pathOrUri in map) {
            map.remove(item.pathOrUri)
        } else {
            map[item.pathOrUri] = item
        }
        _selectedItems.value = map
    }

    fun selectAllVisible() {
        val state = browserUiState.value
        if (state is BrowserUiState.Success) {
            _selectedItems.value = state.items.filter { !it.isProtected }.associateBy { it.pathOrUri }
        }
    }

    fun clearSelection() {
        _selectedItems.value = emptyMap()
    }

    // Create Folder & File
    fun createFolder(name: String, onSuccess: () -> Unit = {}) {
        val current = currentFolderInfo()
        if (current == null) {
            _messages.tryEmit("Please open an accessible folder first")
            return
        }
        viewModelScope.launch {
            val res = repo.createFolder(current.pathOrUri, current.isSafUri, name)
            res.onSuccess {
                _messages.tryEmit("Folder '${it.name}' created")
                refreshCurrentDirectory()
                onSuccess()
            }.onFailure {
                _messages.tryEmit(it.message ?: "Failed to create folder")
            }
        }
    }

    fun createFile(name: String, initialContent: String = "", onCreated: (StorageItem) -> Unit) {
        val current = currentFolderInfo()
        if (current == null) {
            _messages.tryEmit("Please open an accessible folder first")
            return
        }
        viewModelScope.launch {
            val res = repo.createFile(current.pathOrUri, current.isSafUri, name, initialContent)
            res.onSuccess { item ->
                _messages.tryEmit("Created '${item.name}'")
                refreshCurrentDirectory()
                onCreated(item)
            }.onFailure {
                _messages.tryEmit(it.message ?: "Failed to create file")
            }
        }
    }

    // Clipboard Copy / Cut / Paste & Direct Move / Copy
    fun copyToClipboard(items: List<StorageItem>) {
        if (items.isEmpty()) return
        _clipboard.value = ClipboardState(ClipboardMode.COPY, items)
        _selectedItems.value = emptyMap()
        _messages.tryEmit("${items.size} item(s) copied to clipboard")
    }

    fun cutToClipboard(items: List<StorageItem>) {
        if (items.isEmpty()) return
        _clipboard.value = ClipboardState(ClipboardMode.CUT, items)
        _selectedItems.value = emptyMap()
        _messages.tryEmit("${items.size} item(s) ready to move")
    }

    fun clearClipboard() {
        _clipboard.value = ClipboardState()
    }

    fun pasteClipboardHere() {
        val current = currentFolderInfo() ?: return
        val clip = _clipboard.value
        if (clip.isEmpty) return
        performCopyOrMove(
            items = clip.items,
            destPathOrUri = current.pathOrUri,
            destIsSaf = current.isSafUri,
            isMove = clip.mode == ClipboardMode.CUT,
            clearClipboardOnSuccess = true
        )
    }

    fun performCopyOrMove(
        items: List<StorageItem>,
        destPathOrUri: String,
        destIsSaf: Boolean,
        isMove: Boolean,
        clearClipboardOnSuccess: Boolean = false
    ) {
        operationCancelled = false
        viewModelScope.launch {
            val res = repo.copyOrMoveItems(
                items = items,
                destFolderPathOrUri = destPathOrUri,
                destIsSaf = destIsSaf,
                isMove = isMove,
                onConflictPrompt = { prompt ->
                    val deferred = CompletableDeferred<Pair<ConflictAction, Boolean>>()
                    pendingConflictDeferred = deferred
                    _conflictPrompt.value = prompt
                    deferred.await()
                },
                shouldCancel = { operationCancelled }
            )
            _conflictPrompt.value = null
            pendingConflictDeferred = null
            _selectedItems.value = emptyMap()

            res.onSuccess { count ->
                if (clearClipboardOnSuccess) _clipboard.value = ClipboardState()
                refreshCurrentDirectory()
                val verb = if (isMove) "Moved" else "Copied"
                _messages.tryEmit("$verb $count item(s)")
            }.onFailure {
                _messages.tryEmit(it.message ?: "Operation failed")
            }
        }
    }

    fun resolveConflict(action: ConflictAction, applyToAll: Boolean) {
        pendingConflictDeferred?.complete(action to applyToAll)
        _conflictPrompt.value = null
    }

    fun cancelActiveOperation() {
        operationCancelled = true
        pendingConflictDeferred?.complete(ConflictAction.CANCEL to false)
    }

    // Rename & Extension Change Warning
    fun requestRename(item: StorageItem, newName: String) {
        val clean = newName.trim()
        val (valid, err) = MimeAndCategoryHelper.isValidFileOrFolderName(clean)
        if (!valid) {
            _messages.tryEmit(err ?: "Invalid name")
            return
        }
        if (!item.isDirectory) {
            val oldExt = MimeAndCategoryHelper.extractExtension(item.name)
            val newExt = MimeAndCategoryHelper.extractExtension(clean)
            if (oldExt.isNotEmpty() && !oldExt.equals(newExt, ignoreCase = true)) {
                _pendingExtensionWarning.value = PendingExtensionRenameWarning(item, clean)
                return
            }
        }
        executeRename(item, clean)
    }

    fun confirmExtensionRename() {
        val pending = _pendingExtensionWarning.value ?: return
        _pendingExtensionWarning.value = null
        executeRename(pending.item, pending.newName)
    }

    fun dismissExtensionRenameWarning() {
        _pendingExtensionWarning.value = null
    }

    private fun executeRename(item: StorageItem, newName: String) {
        viewModelScope.launch {
            repo.renameItem(item, newName).onSuccess {
                _selectedItems.value = emptyMap()
                _messages.tryEmit("Renamed to '${it.name}'")
                refreshCurrentDirectory()
            }.onFailure {
                _messages.tryEmit(it.message ?: "Rename failed")
            }
        }
    }

    fun executeBatchRename(items: List<StorageItem>, config: BatchRenameConfig) {
        viewModelScope.launch {
            repo.batchRename(items, config).onSuccess { count ->
                _selectedItems.value = emptyMap()
                refreshCurrentDirectory()
                _messages.tryEmit("Batch renamed $count item(s)")
            }.onFailure {
                _messages.tryEmit(it.message ?: "Batch rename failed")
            }
        }
    }

    fun duplicateItem(item: StorageItem) {
        viewModelScope.launch {
            repo.duplicateItem(item).onSuccess {
                _messages.tryEmit("Duplicated as '${it.name}'")
                refreshCurrentDirectory()
            }.onFailure {
                _messages.tryEmit(it.message ?: "Duplicate failed")
            }
        }
    }

    // Delete & Recycle Bin
    fun requestDelete(items: List<StorageItem>) {
        if (items.isEmpty()) return
        val rbEnabled = settings.value.recycleBinEnabled
        val canTrashAll = items.all { repo.canMoveToRecycleBin(it, rbEnabled) }
        _pendingDeleteRequest.value = PendingDeleteRequest(items, canTrashAll)
    }

    fun dismissDeleteRequest() {
        _pendingDeleteRequest.value = null
    }

    fun confirmDelete(forcePermanent: Boolean = false) {
        val pending = _pendingDeleteRequest.value ?: return
        _pendingDeleteRequest.value = null
        val useRecycle = !forcePermanent && pending.canMoveToRecycleBin
        viewModelScope.launch {
            repo.deleteItems(pending.items, useRecycleBin = useRecycle).onSuccess { count ->
                _selectedItems.value = emptyMap()
                refreshCurrentDirectory()
                _messages.tryEmit(
                    if (useRecycle) "Moved $count item(s) to Recycle Bin"
                    else "Permanently deleted $count item(s)"
                )
            }.onFailure {
                _messages.tryEmit(it.message ?: "Delete failed")
            }
        }
    }

    fun restoreRecycleItem(entry: RecycleBinEntity, fallbackDir: File? = null) {
        viewModelScope.launch {
            repo.restoreFromRecycleBin(entry, fallbackDir).onSuccess { restored ->
                refreshCurrentDirectory()
                _messages.tryEmit("Restored '${restored.name}'")
            }.onFailure {
                _messages.tryEmit(it.message ?: "Restore failed")
            }
        }
    }

    fun permanentlyDeleteRecycleItem(entry: RecycleBinEntity) {
        viewModelScope.launch {
            repo.permanentlyDeleteFromRecycleBin(entry)
            _messages.tryEmit("Permanently deleted '${entry.name}'")
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repo.emptyRecycleBin()
            _messages.tryEmit("Recycle Bin emptied")
        }
    }

    // ZIP Creation
    fun createZipArchive(
        items: List<StorageItem>,
        destFolderPath: String,
        zipFileName: String,
        compressionMode: ZipCompressionMode,
        password: String?
    ) {
        val safeName = if (zipFileName.endsWith(".zip", ignoreCase = true)) zipFileName else "$zipFileName.zip"
        val fileItems = items.filter { !it.isSafUri }.map { File(it.pathOrUri) }
        if (fileItems.isEmpty()) {
            _messages.tryEmit("ZIP creation requires accessible local file paths")
            return
        }
        viewModelScope.launch {
            val destFile = File(destFolderPath, safeName)
            val res = zipService.createZip(
                sourceFiles = fileItems,
                destinationZipFile = destFile,
                compressionMode = compressionMode,
                password = password?.takeIf { it.isNotEmpty() }
            )
            res.onSuccess { created ->
                repo.recordOperation("ZIP", created.name, "Compressed ${fileItems.size} item(s)", true)
                refreshCurrentDirectory()
                _selectedItems.value = emptyMap()
                _messages.tryEmit("Created ${created.name}")
            }.onFailure {
                repo.recordOperation("ZIP", safeName, it.message ?: "Failed", false)
                _messages.tryEmit(it.message ?: "ZIP creation failed")
            }
        }
    }

    // Favorites, Recents, Clipboard Path, Share, Open With, Properties
    fun toggleFavorite(item: StorageItem) {
        viewModelScope.launch {
            val added = repo.toggleFavorite(item)
            _messages.tryEmit(if (added) "Added '${item.name}' to Favorites" else "Removed from Favorites")
        }
    }

    fun removeFavorite(pathOrUri: String) {
        viewModelScope.launch {
            repo.removeFavoriteByKey(pathOrUri)
            _messages.tryEmit("Removed from Favorites")
        }
    }

    fun removeRecent(pathOrUri: String) {
        viewModelScope.launch {
            repo.removeRecent(pathOrUri)
            _messages.tryEmit("Removed from Recent")
        }
    }

    fun markItemOpened(item: StorageItem) {
        viewModelScope.launch {
            repo.recordRecent(item, "Opened")
        }
    }

    fun copyPathToClipboard(item: StorageItem) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("File Path", item.pathOrUri))
        _messages.tryEmit("Copied path to clipboard")
    }

    fun shareItems(context: Context, items: List<StorageItem>) {
        val filesOnly = items.filter { !it.isDirectory }
        if (filesOnly.isEmpty()) {
            _messages.tryEmit("Select one or more files to share")
            return
        }
        try {
            val intent = if (filesOnly.size == 1) {
                val first = filesOnly.first()
                storageAccessHelper.buildShareSingleIntent(first.pathOrUri, first.isSafUri, first.mimeType)
            } else {
                storageAccessHelper.buildShareMultipleIntent(filesOnly.map { it.pathOrUri to it.isSafUri })
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _messages.tryEmit("No app available to share: ${e.message}")
        }
    }

    fun openWithExternalApp(context: Context, item: StorageItem) {
        if (item.isDirectory) return
        viewModelScope.launch { repo.recordRecent(item, "Opened") }
        try {
            val intent = storageAccessHelper.buildOpenWithIntent(item.pathOrUri, item.isSafUri, item.mimeType)
            context.startActivity(intent)
        } catch (e: Exception) {
            _messages.tryEmit("No installed app can open '${item.name}'")
        }
    }

    fun inspectApk(item: StorageItem): ApkMetadata? {
        if (item.isSafUri || !MimeAndCategoryHelper.isApk(item.name, item.mimeType)) return null
        return storageAccessHelper.inspectApk(item.pathOrUri)
    }

    suspend fun computeFolderMetrics(item: StorageItem): Triple<Int, Int, Long> =
        repo.computeFolderMetrics(item)

    suspend fun checkExists(pathOrUri: String, isSafUri: Boolean): Boolean =
        repo.itemStillExists(pathOrUri, isSafUri)

    fun observeCategory(category: FileCategory) =
        repo.observeCategoryFiles(category, settings.value.showHiddenFiles)

    fun runGlobalSearch(query: String, category: FileCategory?, onlyCurrentFolder: Boolean) {
        viewModelScope.launch {
            _isSearchingGlobal.value = true
            val folderScope = if (onlyCurrentFolder) currentFolderInfo()?.pathOrUri else null
            _globalSearchResults.value = repo.searchFiles(
                query = query,
                category = category,
                includeHidden = settings.value.showHiddenFiles,
                currentFolderPath = folderScope
            )
            _isSearchingGlobal.value = false
        }
    }

    suspend fun listFolderForPicker(pathOrUri: String, isSafUri: Boolean): List<StorageItem> {
        return repo.listDirectory(pathOrUri, isSafUri, includeHidden = false)
            .getOrDefault(emptyList())
            .filter { it.isDirectory && !it.isProtected && it.canRead }
            .sortedBy { it.name.lowercase() }
    }

    // Settings Actions
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setBlueBrandTheme(enabled: Boolean) = viewModelScope.launch { settingsStore.setBlueBrandTheme(enabled) }
    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch {
        settingsStore.setShowHiddenFiles(show)
        refreshCurrentDirectory()
    }
    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoRefresh(enabled) }
    fun setLargeFileThresholdMb(mb: Int) = viewModelScope.launch {
        settingsStore.setLargeFileThresholdMb(mb)
        _largeFilesThresholdMb.value = mb
        observeLargeFilesForThreshold(mb)
    }
    fun setEditorFontSizeSp(sp: Int) = viewModelScope.launch { settingsStore.setEditorFontSizeSp(sp) }
    fun setEditorWordWrap(enabled: Boolean) = viewModelScope.launch { settingsStore.setEditorWordWrap(enabled) }
    fun setEditorLineNumbers(enabled: Boolean) = viewModelScope.launch { settingsStore.setEditorLineNumbers(enabled) }
    fun setEditorSyntaxHighlight(enabled: Boolean) = viewModelScope.launch { settingsStore.setEditorSyntaxHighlighting(enabled) }
    fun setEditorDarkTheme(enabled: Boolean) = viewModelScope.launch { settingsStore.setEditorDarkTheme(enabled) }
    fun setEditorAutoSaveSeconds(seconds: Int) = viewModelScope.launch { settingsStore.setEditorAutoSaveSeconds(seconds) }
    fun setRecycleBinEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setRecycleBinEnabled(enabled) }
    fun setAppLock(enabled: Boolean, pin: String? = null, biometric: Boolean? = null) =
        viewModelScope.launch { settingsStore.setAppLock(enabled, pin, biometric) }
    fun setScanNotifications(enabled: Boolean) = viewModelScope.launch { settingsStore.setScanNotifications(enabled) }
    fun setOperationNotifications(enabled: Boolean) = viewModelScope.launch { settingsStore.setOperationNotifications(enabled) }
    fun clearOperationHistory() = viewModelScope.launch { repo.clearOperationHistory() }

    fun emitMessage(msg: String) {
        _messages.tryEmit(msg)
    }
}
