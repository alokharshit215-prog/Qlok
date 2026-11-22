package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.storage.MimeAndCategoryHelper
import com.example.model.FileCategory
import com.example.model.StorageItem
import com.example.ui.components.BatchRenameDialog
import com.example.ui.components.ConflictResolutionDialog
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.CreateZipDialog
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.ExtensionWarningDialog
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.OperationProgressDialog
import com.example.ui.components.PinLockGate
import com.example.ui.components.PropertiesBottomSheet
import com.example.ui.components.RenameDialog
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.screens.CloudScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FilesBrowserScreen
import com.example.ui.screens.FilteredCategoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.OperationHistoryScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.RecentFilesScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StorageAnalyzerScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.ZipBrowserScreen
import com.example.ui.theme.BlueFileManagerTheme
import com.example.viewmodel.BrowserUiState
import com.example.viewmodel.CloudFtpViewModel
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.MainFileManagerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private enum class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("tab_home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    FILES("tab_files", "Files", Icons.Rounded.Folder, Icons.Outlined.Folder),
    STORAGE("tab_storage", "Storage", Icons.Rounded.Analytics, Icons.Outlined.Analytics),
    CLOUD("tab_cloud", "Cloud", Icons.Rounded.Cloud, Icons.Outlined.Cloud),
    SETTINGS("tab_settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

private object Routes {
    const val EDITOR = "screen_editor"
    const val IMAGE = "screen_image"
    const val VIDEO = "screen_video"
    const val AUDIO = "screen_audio"
    const val PDF = "screen_pdf"
    const val ZIP = "screen_zip"
    const val CATEGORY = "screen_category/{category}"
    const val SEARCH = "screen_search"
    const val FAVORITES = "screen_favorites"
    const val RECENT = "screen_recent"
    const val RECYCLE_BIN = "screen_recycle_bin"
    const val HISTORY = "screen_history"
}

class MainActivity : FragmentActivity() {
    private val externalLaunchIntentFlow = MutableStateFlow<Pair<Int, Intent>?>(null)
    private var externalIntentSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isFileOpenIntent(intent)) {
            externalIntentSeq++
            externalLaunchIntentFlow.value = externalIntentSeq to intent
        }
        setContent {
            val mainVm: MainFileManagerViewModel = viewModel()
            val settings by mainVm.settings.collectAsStateWithLifecycle()
            val isUnlocked by mainVm.isAppUnlocked.collectAsStateWithLifecycle()
            val externalIntentEvent by externalLaunchIntentFlow.asStateFlow().collectAsStateWithLifecycle()

            var showSplash by remember { mutableStateOf(externalIntentEvent == null) }
            LaunchedEffect(externalIntentEvent) {
                if (externalIntentEvent != null) {
                    showSplash = false
                }
            }

            BlueFileManagerTheme(
                themeMode = settings.themeMode,
                useBlueBrandTheme = settings.useBlueBrandTheme
            ) {
                when {
                    showSplash -> SplashScreen(onFinished = { showSplash = false })
                    settings.appLockEnabled && !isUnlocked -> {
                        PinLockGate(
                            biometricEnabled = settings.appLockBiometricEnabled,
                            onVerifyPin = { pin -> mainVm.unlockAppWithPin(pin) },
                            onBiometricSuccess = { mainVm.unlockAppWithBiometric() }
                        )
                    }
                    else -> BlueFileManagerRoot(
                        mainVm = mainVm,
                        externalIntentEvent = externalIntentEvent,
                        onExternalIntentHandled = { externalLaunchIntentFlow.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isFileOpenIntent(intent)) {
            externalIntentSeq++
            externalLaunchIntentFlow.value = externalIntentSeq to intent
        }
    }
}

@Composable
private fun BlueFileManagerRoot(
    mainVm: MainFileManagerViewModel,
    externalIntentEvent: Pair<Int, Intent>?,
    onExternalIntentHandled: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val hasStorageAccess by mainVm.hasStorageAccess.collectAsStateWithLifecycle()
    val storageRoots by mainVm.storageRoots.collectAsStateWithLifecycle()
    val currentRoot by mainVm.currentRoot.collectAsStateWithLifecycle()
    val breadcrumbs by mainVm.breadcrumbs.collectAsStateWithLifecycle()
    val browserUiState by mainVm.browserUiState.collectAsStateWithLifecycle()
    val viewMode by mainVm.viewMode.collectAsStateWithLifecycle()
    val sortOption by mainVm.sortOption.collectAsStateWithLifecycle()
    val sortDirection by mainVm.sortDirection.collectAsStateWithLifecycle()
    val browserSearchQuery by mainVm.browserSearchQuery.collectAsStateWithLifecycle()
    val selectedItems by mainVm.selectedItems.collectAsStateWithLifecycle()
    val clipboard by mainVm.clipboard.collectAsStateWithLifecycle()
    val storageStats by mainVm.storageStats.collectAsStateWithLifecycle()
    val scanProgress by mainVm.scanProgress.collectAsStateWithLifecycle()
    val operationProgress by mainVm.operationProgress.collectAsStateWithLifecycle()
    val largeFiles by mainVm.largeFiles.collectAsStateWithLifecycle()
    val recentFiles by mainVm.recentFiles.collectAsStateWithLifecycle()
    val recentModifiedFiles by mainVm.recentModifiedFiles.collectAsStateWithLifecycle()
    val favorites by mainVm.favorites.collectAsStateWithLifecycle()
    val recycleBinItems by mainVm.recycleBinItems.collectAsStateWithLifecycle()
    val operationHistory by mainVm.operationHistory.collectAsStateWithLifecycle()
    val indexedFileCount by mainVm.indexedFileCount.collectAsStateWithLifecycle()
    val indexedFolderCount by mainVm.indexedFolderCount.collectAsStateWithLifecycle()
    val conflictPrompt by mainVm.conflictPrompt.collectAsStateWithLifecycle()
    val pendingDelete by mainVm.pendingDeleteRequest.collectAsStateWithLifecycle()
    val pendingExtWarning by mainVm.pendingExtensionWarning.collectAsStateWithLifecycle()
    val globalSearchResults by mainVm.globalSearchResults.collectAsStateWithLifecycle()
    val isSearchingGlobal by mainVm.isSearchingGlobal.collectAsStateWithLifecycle()

    // Active Viewer/Editor target
    var activeItem by remember { mutableStateOf<StorageItem?>(null) }
    var activeAudioPlaylist by remember { mutableStateOf<List<StorageItem>>(emptyList()) }

    // Modal Dialogs state
    var showStorageAccessChoiceDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFileDialogIsCode by remember { mutableStateOf<Boolean?>(null) }
    var renameTarget by remember { mutableStateOf<StorageItem?>(null) }
    var propertiesTarget by remember { mutableStateOf<StorageItem?>(null) }
    var moveTargets by remember { mutableStateOf<List<StorageItem>?>(null) }
    var batchRenameTargets by remember { mutableStateOf<List<StorageItem>?>(null) }
    var zipCreateTargets by remember { mutableStateOf<List<StorageItem>?>(null) }
    var saveCopyTarget by remember { mutableStateOf<StorageItem?>(null) }

    // Official Storage Access Launchers
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainVm.refreshStorageAccessState()
        if (mainVm.storageAccessHelper.hasAnyStorageAccess()) {
            mainVm.startStorageScan()
        }
    }

    val safTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            mainVm.onSafTreeGranted(uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            mainVm.emitMessage("Notification permission declined")
        }
    }

    LaunchedEffect(Unit) {
        mainVm.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun launchGrantStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showStorageAccessChoiceDialog = true
        } else {
            safTreeLauncher.launch(null)
        }
    }

    fun navigateToTopTab(tab: TopTab) {
        if (navController.currentDestination?.route == tab.route) return
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openStorageItem(item: StorageItem) {
        if (item.isDirectory) {
            mainVm.navigateToLocation(item.pathOrUri, item.isSafUri)
            navigateToTopTab(TopTab.FILES)
            return
        }
        mainVm.markItemOpened(item)
        activeItem = item
        when {
            MimeAndCategoryHelper.isZip(item.name, item.mimeType) -> {
                navController.navigate(Routes.ZIP)
            }
            MimeAndCategoryHelper.isEditableTextOrCode(item.name, item.mimeType) -> {
                navController.navigate(Routes.EDITOR)
            }
            MimeAndCategoryHelper.isImage(item.name, item.mimeType) -> {
                navController.navigate(Routes.IMAGE)
            }
            MimeAndCategoryHelper.isVideo(item.name, item.mimeType) -> {
                navController.navigate(Routes.VIDEO)
            }
            MimeAndCategoryHelper.isAudio(item.name, item.mimeType) -> {
                val siblings = (browserUiState as? BrowserUiState.Success)?.items
                    ?.filter { !it.isDirectory && MimeAndCategoryHelper.isAudio(it.name, it.mimeType) }
                    ?: listOf(item)
                activeAudioPlaylist = siblings
                navController.navigate(Routes.AUDIO)
            }
            MimeAndCategoryHelper.isPdf(item.name, item.mimeType) -> {
                navController.navigate(Routes.PDF)
            }
            else -> {
                mainVm.openWithExternalApp(context, item)
            }
        }
    }

    LaunchedEffect(externalIntentEvent) {
        val event = externalIntentEvent ?: return@LaunchedEffect
        val incomingIntent = event.second
        val resolvedItem = resolveExternalIntentToStorageItem(context, incomingIntent)
        onExternalIntentHandled()
        if (resolvedItem != null) {
            mainVm.markItemOpened(resolvedItem)
            activeItem = resolvedItem
            when {
                MimeAndCategoryHelper.isZip(resolvedItem.name, resolvedItem.mimeType) -> {
                    navController.navigate(Routes.ZIP) { launchSingleTop = true }
                }
                MimeAndCategoryHelper.isEditableTextOrCode(resolvedItem.name, resolvedItem.mimeType) ||
                    incomingIntent.action == Intent.ACTION_EDIT -> {
                    navController.navigate(Routes.EDITOR) { launchSingleTop = true }
                }
                MimeAndCategoryHelper.isImage(resolvedItem.name, resolvedItem.mimeType) -> {
                    navController.navigate(Routes.IMAGE) { launchSingleTop = true }
                }
                MimeAndCategoryHelper.isVideo(resolvedItem.name, resolvedItem.mimeType) -> {
                    navController.navigate(Routes.VIDEO) { launchSingleTop = true }
                }
                MimeAndCategoryHelper.isAudio(resolvedItem.name, resolvedItem.mimeType) -> {
                    activeAudioPlaylist = listOf(resolvedItem)
                    navController.navigate(Routes.AUDIO) { launchSingleTop = true }
                }
                MimeAndCategoryHelper.isPdf(resolvedItem.name, resolvedItem.mimeType) -> {
                    navController.navigate(Routes.PDF) { launchSingleTop = true }
                }
                MimeAndCategoryHelper.isApk(resolvedItem.name, resolvedItem.mimeType) -> {
                    propertiesTarget = resolvedItem
                }
                looksLikeUtf8Text(context, resolvedItem) -> {
                    navController.navigate(Routes.EDITOR) { launchSingleTop = true }
                }
                else -> {
                    propertiesTarget = resolvedItem
                    mainVm.emitMessage("Opened '${resolvedItem.name}' (${resolvedItem.mimeType})")
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelNav = TopTab.entries.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = if (showTopLevelNav) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useNavigationRail) {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            TopTab.entries.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (showTopLevelNav && useNavigationRail) {
                    NavigationRail {
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = TopTab.HOME.route
                    ) {
                        composable(TopTab.HOME.route) {
                            HomeScreen(
                                hasStorageAccess = hasStorageAccess,
                                stats = storageStats,
                                scanProgress = scanProgress,
                                recentFiles = recentFiles,
                                favorites = favorites,
                                onGrantStorageAccess = { launchGrantStorageAccess() },
                                onScanStorage = {
                                    if (!hasStorageAccess) launchGrantStorageAccess()
                                    else mainVm.startStorageScan()
                                },
                                onCancelScan = { mainVm.cancelStorageScan() },
                                onRefresh = { mainVm.refreshStorageAccessState() },
                                onAnalyzeStorage = { navigateToTopTab(TopTab.STORAGE) },
                                onOpenBrowseFiles = { navigateToTopTab(TopTab.FILES) },
                                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                                onOpenCategory = { cat -> navController.navigate("screen_category/${cat.name}") },
                                onOpenRecentScreen = { navController.navigate(Routes.RECENT) },
                                onOpenFavoritesScreen = { navController.navigate(Routes.FAVORITES) },
                                onOpenRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                                onQuickNewFolder = {
                                    if (!hasStorageAccess) launchGrantStorageAccess()
                                    else {
                                        if (mainVm.currentFolderInfo() == null) mainVm.refreshStorageAccessState()
                                        showCreateFolderDialog = true
                                    }
                                },
                                onQuickNewFile = {
                                    if (!hasStorageAccess) launchGrantStorageAccess()
                                    else {
                                        if (mainVm.currentFolderInfo() == null) mainVm.refreshStorageAccessState()
                                        createFileDialogIsCode = false
                                    }
                                },
                                onQuickNewCodeFile = {
                                    if (!hasStorageAccess) launchGrantStorageAccess()
                                    else {
                                        if (mainVm.currentFolderInfo() == null) mainVm.refreshStorageAccessState()
                                        createFileDialogIsCode = true
                                    }
                                },
                                onQuickZip = {
                                    navigateToTopTab(TopTab.FILES)
                                    mainVm.emitMessage("Tap Select (✓) or long-press items in Files to create a ZIP archive")
                                },
                                onOpenRecentItem = { rec ->
                                    openStorageItem(
                                        StorageItem(
                                            pathOrUri = rec.pathOrUri,
                                            name = rec.name,
                                            parentPathOrUri = rec.parentPathOrUri,
                                            isDirectory = rec.isDirectory,
                                            isSafUri = rec.isSafUri,
                                            size = rec.size,
                                            lastModified = rec.lastModified,
                                            extension = rec.extension,
                                            mimeType = rec.mimeType
                                        )
                                    )
                                },
                                onOpenFavoriteItem = { fav ->
                                    openStorageItem(
                                        StorageItem(
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
                                    )
                                }
                            )
                        }

                        composable(TopTab.FILES.route) {
                            FilesBrowserScreen(
                                uiState = browserUiState,
                                roots = storageRoots,
                                currentRoot = currentRoot,
                                breadcrumbs = breadcrumbs,
                                viewMode = viewMode,
                                sortOption = sortOption,
                                sortDirection = sortDirection,
                                searchQuery = browserSearchQuery,
                                selectedItems = selectedItems,
                                clipboard = clipboard,
                                onSelectRoot = { mainVm.openRoot(it) },
                                onAddSafFolder = { safTreeLauncher.launch(null) },
                                onGrantAllFilesAccess = { launchGrantStorageAccess() },
                                onNavigateBreadcrumb = { mainVm.navigateToBreadcrumb(it) },
                                onNavigateUp = { mainVm.navigateUp() },
                                onRefresh = { mainVm.refreshCurrentDirectory() },
                                onSearchQueryChange = { mainVm.setBrowserSearchQuery(it) },
                                onOpenGlobalSearch = { navController.navigate(Routes.SEARCH) },
                                onOpenRecentFiles = { navController.navigate(Routes.RECENT) },
                                onToggleViewMode = { mainVm.setViewMode(it) },
                                onSortChange = { opt, dir -> mainVm.setSort(opt, dir) },
                                onItemClick = { item ->
                                    if (item.isDirectory) mainVm.openFolder(item)
                                    else openStorageItem(item)
                                },
                                onToggleSelect = { mainVm.toggleItemSelection(it) },
                                onSelectAll = { mainVm.selectAllVisible() },
                                onClearSelection = { mainVm.clearSelection() },
                                onPasteHere = { mainVm.pasteClipboardHere() },
                                onClearClipboard = { mainVm.clearClipboard() },
                                onNewFolder = { showCreateFolderDialog = true },
                                onNewFile = { createFileDialogIsCode = false },
                                onNewCodeFile = { createFileDialogIsCode = true },
                                onCreateZip = { list -> zipCreateTargets = list },
                                onCopyItems = { list -> mainVm.copyToClipboard(list) },
                                onCutItems = { list -> mainVm.cutToClipboard(list) },
                                onMoveItems = { list -> moveTargets = list },
                                onDeleteItems = { list -> mainVm.requestDelete(list) },
                                onShareItems = { list -> mainVm.shareItems(context, list) },
                                onBatchRename = { list -> batchRenameTargets = list },
                                onFavoriteSelected = { list -> list.forEach { mainVm.toggleFavorite(it) }; mainVm.clearSelection() },
                                onContextOpenWith = { mainVm.openWithExternalApp(context, it) },
                                onContextEdit = { item ->
                                    activeItem = item
                                    navController.navigate(Routes.EDITOR)
                                },
                                onContextRename = { renameTarget = it },
                                onContextDuplicate = { mainVm.duplicateItem(it) },
                                onContextSaveCopy = { saveCopyTarget = it },
                                onContextProperties = { propertiesTarget = it },
                                onContextFavorite = { mainVm.toggleFavorite(it) },
                                onDropOntoFolder = { draggedPath, targetFolder, isMove ->
                                    val visible = (browserUiState as? BrowserUiState.Success)?.items ?: emptyList()
                                    val draggedItem = visible.firstOrNull { it.pathOrUri == draggedPath }
                                    if (draggedItem != null) {
                                        mainVm.performCopyOrMove(
                                            items = listOf(draggedItem),
                                            destPathOrUri = targetFolder.pathOrUri,
                                            destIsSaf = targetFolder.isSafUri,
                                            isMove = isMove
                                        )
                                    }
                                }
                            )
                        }

                        composable(TopTab.STORAGE.route) {
                            StorageAnalyzerScreen(
                                stats = storageStats,
                                scanProgress = scanProgress,
                                largeFiles = largeFiles,
                                thresholdMb = settings.largeFileThresholdMb,
                                onThresholdChange = { mainVm.setLargeFileThresholdMb(it) },
                                onScanStorage = { mainVm.startStorageScan() },
                                onCancelScan = { mainVm.cancelStorageScan() },
                                onOpenCategory = { cat -> navController.navigate("screen_category/${cat.name}") },
                                onOpenRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                                onOpenFile = { openStorageItem(it) },
                                onShareFile = { mainVm.shareItems(context, listOf(it)) },
                                onMoveFile = { moveTargets = listOf(it) },
                                onDeleteFile = { mainVm.requestDelete(listOf(it)) },
                                onPropertiesFile = { propertiesTarget = it }
                            )
                        }

                        composable(TopTab.CLOUD.route) {
                            val cloudVm: CloudFtpViewModel = viewModel()
                            val savedConns by cloudVm.savedConnections.collectAsStateWithLifecycle()
                            val activeConn by cloudVm.activeConnection.collectAsStateWithLifecycle()
                            val remotePath by cloudVm.currentRemotePath.collectAsStateWithLifecycle()
                            val remoteEntries by cloudVm.remoteEntries.collectAsStateWithLifecycle()
                            val isBusy by cloudVm.isBusy.collectAsStateWithLifecycle()
                            val errorMsg by cloudVm.errorMessage.collectAsStateWithLifecycle()

                            LaunchedEffect(Unit) {
                                cloudVm.events.collect { msg -> snackbarHostState.showSnackbar(msg) }
                            }

                            CloudScreen(
                                savedConnections = savedConns,
                                activeConnection = activeConn,
                                currentRemotePath = remotePath,
                                remoteEntries = remoteEntries,
                                isBusy = isBusy,
                                errorMessage = errorMsg,
                                localRoots = storageRoots,
                                loadLocalSubfolders = { path, saf -> mainVm.listFolderForPicker(path, saf) },
                                onAddFtpConnection = { l, h, p, u, pw, pass -> cloudVm.addFtpConnection(l, h, p, u, pw, pass) },
                                onDeleteConnection = { cloudVm.deleteConnection(it) },
                                onConnectFtp = { conn, path -> cloudVm.connectAndBrowse(conn, path) },
                                onDisconnect = { cloudVm.disconnect() },
                                onNavigateUp = { cloudVm.navigateUp() },
                                onDownloadEntry = { entry, destDir -> cloudVm.downloadRemoteFile(entry, destDir) },
                                onUploadLocalFile = { file -> cloudVm.uploadLocalFile(file) },
                                onCreateRemoteFolder = { name -> cloudVm.createRemoteFolder(name) },
                                onDeleteRemoteEntry = { entry -> cloudVm.deleteRemoteEntry(entry) }
                            )
                        }

                        composable(TopTab.SETTINGS.route) {
                            SettingsScreen(
                                settings = settings,
                                hasStorageAccess = hasStorageAccess,
                                indexedFilesCount = indexedFileCount,
                                indexedFoldersCount = indexedFolderCount,
                                isScanning = scanProgress.isScanning,
                                onGrantStorageAccess = { launchGrantStorageAccess() },
                                onAddSafFolder = { safTreeLauncher.launch(null) },
                                onScanStorage = { mainVm.startStorageScan() },
                                onRefresh = { mainVm.refreshCurrentDirectory() },
                                onClearFileIndex = { mainVm.clearFileIndexOnly() },
                                onSetAutoRefresh = { mainVm.setAutoRefresh(it) },
                                onSetShowHiddenFiles = { mainVm.setShowHiddenFiles(it) },
                                onSetThemeMode = { mainVm.setThemeMode(it) },
                                onSetBlueBrandTheme = { mainVm.setBlueBrandTheme(it) },
                                onSetDefaultViewMode = { mainVm.setViewMode(it) },
                                onSetDefaultSort = { opt, dir -> mainVm.setSort(opt, dir) },
                                onSetEditorFontSize = { mainVm.setEditorFontSizeSp(it) },
                                onSetEditorWordWrap = { mainVm.setEditorWordWrap(it) },
                                onSetEditorLineNumbers = { mainVm.setEditorLineNumbers(it) },
                                onSetEditorSyntaxHighlight = { mainVm.setEditorSyntaxHighlight(it) },
                                onSetEditorDarkTheme = { mainVm.setEditorDarkTheme(it) },
                                onSetEditorAutoSaveSeconds = { mainVm.setEditorAutoSaveSeconds(it) },
                                onSetRecycleBinEnabled = { mainVm.setRecycleBinEnabled(it) },
                                onOpenRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                                onSetAppLock = { en, pin, bio -> mainVm.setAppLock(en, pin, bio) },
                                onSetScanNotifications = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    mainVm.setScanNotifications(enabled)
                                },
                                onSetOperationNotifications = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    mainVm.setOperationNotifications(enabled)
                                }
                            )
                        }

                        // Built-in Text/Code Editor
                        composable(Routes.EDITOR) {
                            val item = activeItem
                            if (item != null) {
                                val editorVm: EditorViewModel = viewModel()
                                EditorScreen(
                                    item = item,
                                    viewModel = editorVm,
                                    settings = settings,
                                    roots = storageRoots,
                                    loadSubfolders = { path, saf -> mainVm.listFolderForPicker(path, saf) },
                                    onSetDarkEditor = { mainVm.setEditorDarkTheme(it) },
                                    onSetWordWrap = { mainVm.setEditorWordWrap(it) },
                                    onSetLineNumbers = { mainVm.setEditorLineNumbers(it) },
                                    onSetFontSize = { mainVm.setEditorFontSizeSp(it) },
                                    onExit = {
                                        mainVm.refreshCurrentDirectory()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        // Built-in Image Viewer
                        composable(Routes.IMAGE) {
                            activeItem?.let { item ->
                                ImageViewerScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onShare = { mainVm.shareItems(context, listOf(item)) },
                                    onRename = { renameTarget = item },
                                    onDelete = { mainVm.requestDelete(listOf(item)); navController.popBackStack() },
                                    onProperties = { propertiesTarget = item }
                                )
                            }
                        }

                        // Built-in Video Player
                        composable(Routes.VIDEO) {
                            activeItem?.let { item ->
                                VideoPlayerScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onShare = { mainVm.shareItems(context, listOf(item)) },
                                    onRename = { renameTarget = item },
                                    onDelete = { mainVm.requestDelete(listOf(item)); navController.popBackStack() },
                                    onProperties = { propertiesTarget = item }
                                )
                            }
                        }

                        // Built-in Audio Player
                        composable(Routes.AUDIO) {
                            activeItem?.let { item ->
                                AudioPlayerScreen(
                                    initialItem = item,
                                    playlist = activeAudioPlaylist,
                                    onBack = { navController.popBackStack() },
                                    onShare = { mainVm.shareItems(context, listOf(it)) },
                                    onRename = { renameTarget = it },
                                    onDelete = { mainVm.requestDelete(listOf(it)); navController.popBackStack() },
                                    onProperties = { propertiesTarget = it }
                                )
                            }
                        }

                        // Built-in PDF Viewer
                        composable(Routes.PDF) {
                            activeItem?.let { item ->
                                PdfViewerScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onShare = { mainVm.shareItems(context, listOf(item)) },
                                    onRename = { renameTarget = item },
                                    onProperties = { propertiesTarget = item },
                                    onOpenWith = { mainVm.openWithExternalApp(context, item) }
                                )
                            }
                        }

                        // Built-in ZIP Archive Browser
                        composable(Routes.ZIP) {
                            activeItem?.let { item ->
                                ZipBrowserScreen(
                                    item = item,
                                    zipService = mainVm.zipService,
                                    roots = storageRoots,
                                    loadSubfolders = { path, saf -> mainVm.listFolderForPicker(path, saf) },
                                    onExtractCompleted = { mainVm.refreshCurrentDirectory() },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        // Category Screen
                        composable(Routes.CATEGORY) { backStack ->
                            val catName = backStack.arguments?.getString("category") ?: FileCategory.OTHER.name
                            val category = runCatching { FileCategory.valueOf(catName) }.getOrDefault(FileCategory.OTHER)
                            val catItems by remember(category) { mainVm.observeCategory(category) }
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            FilteredCategoryScreen(
                                category = category,
                                items = catItems,
                                onBack = { navController.popBackStack() },
                                onScanStorage = { mainVm.startStorageScan() },
                                onOpenItem = { openStorageItem(it) },
                                onShareItem = { mainVm.shareItems(context, listOf(it)) },
                                onRenameItem = { renameTarget = it },
                                onDeleteItem = { mainVm.requestDelete(listOf(it)) },
                                onPropertiesItem = { propertiesTarget = it }
                            )
                        }

                        composable(Routes.SEARCH) {
                            val currentFolder = mainVm.currentFolderInfo()
                            SearchScreen(
                                results = globalSearchResults,
                                isSearching = isSearchingGlobal,
                                hasCurrentFolder = currentFolder != null,
                                currentFolderLabel = currentFolder?.label ?: "Folder",
                                onSearch = { q, cat, onlyCurrent -> mainVm.runGlobalSearch(q, cat, onlyCurrent) },
                                onOpenItem = { openStorageItem(it) },
                                onPropertiesItem = { propertiesTarget = it },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.FAVORITES) {
                            FavoritesScreen(
                                favorites = favorites,
                                checkExists = { p, saf -> mainVm.checkExists(p, saf) },
                                onOpenFavorite = { openStorageItem(it) },
                                onRemoveFavorite = { mainVm.removeFavorite(it) },
                                onRenameItem = { renameTarget = it },
                                onShareItem = { mainVm.shareItems(context, listOf(it)) },
                                onPropertiesItem = { propertiesTarget = it },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.RECENT) {
                            RecentFilesScreen(
                                recentFiles = recentFiles,
                                recentModifiedFiles = recentModifiedFiles,
                                onOpenRecent = { openStorageItem(it) },
                                onShareRecent = { mainVm.shareItems(context, listOf(it)) },
                                onRenameItem = { renameTarget = it },
                                onBatchRename = { batchRenameTargets = it },
                                onDeleteItems = { mainVm.requestDelete(it) },
                                onRemoveRecent = { mainVm.removeRecent(it) },
                                onScanStorage = {
                                    if (!hasStorageAccess) launchGrantStorageAccess()
                                    else mainVm.startStorageScan()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.RECYCLE_BIN) {
                            RecycleBinScreen(
                                items = recycleBinItems,
                                roots = storageRoots,
                                loadSubfolders = { path, saf -> mainVm.listFolderForPicker(path, saf) },
                                onRestore = { entry, fallback -> mainVm.restoreRecycleItem(entry, fallback) },
                                onDeleteForever = { mainVm.permanentlyDeleteRecycleItem(it) },
                                onEmptyAll = { mainVm.emptyRecycleBin() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.HISTORY) {
                            OperationHistoryScreen(
                                history = operationHistory,
                                onClear = { mainVm.clearOperationHistory() },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared Modals & Confirmations
    // -------------------------------------------------------------------------
    if (showStorageAccessChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showStorageAccessChoiceDialog = false },
            title = { Text("Grant Storage Access") },
            text = {
                Text(
                    "Choose how to grant Blue File Manager access to your device files:\n\n" +
                        "• All Files Access: Recommended for full file management across Download, Documents, DCIM, and custom project folders.\n" +
                        "• Select Folder (SAF): Grant access to individual folders via Android's Storage Access Framework."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showStorageAccessChoiceDialog = false
                    allFilesAccessLauncher.launch(mainVm.storageAccessHelper.createAllFilesAccessIntent())
                }) { Text("All Files Access") }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        showStorageAccessChoiceDialog = false
                        safTreeLauncher.launch(null)
                    }) { Text("Select Folder") }
                    TextButton(onClick = { showStorageAccessChoiceDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        val currentLabel = mainVm.currentFolderInfo()?.label ?: "Current Folder"
        CreateFolderDialog(
            currentFolderName = currentLabel,
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                showCreateFolderDialog = false
                mainVm.createFolder(name)
            }
        )
    }

    createFileDialogIsCode?.let { isCode ->
        val currentLabel = mainVm.currentFolderInfo()?.label ?: "Current Folder"
        CreateFileDialog(
            isCodeFile = isCode,
            currentFolderName = currentLabel,
            onDismiss = { createFileDialogIsCode = null },
            onCreate = { name ->
                createFileDialogIsCode = null
                mainVm.createFile(name) { createdItem ->
                    if (MimeAndCategoryHelper.isEditableTextOrCode(createdItem.name, createdItem.mimeType)) {
                        activeItem = createdItem
                        navController.navigate(Routes.EDITOR)
                    }
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            item = target,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                renameTarget = null
                mainVm.requestRename(target, newName)
            }
        )
    }

    pendingExtWarning?.let { pending ->
        ExtensionWarningDialog(
            newName = pending.newName,
            onConfirm = { mainVm.confirmExtensionRename() },
            onDismiss = { mainVm.dismissExtensionRenameWarning() }
        )
    }

    pendingDelete?.let { req ->
        DeleteConfirmationDialog(
            items = req.items,
            canMoveToRecycleBin = req.canMoveToRecycleBin,
            onConfirm = { forcePerm -> mainVm.confirmDelete(forcePerm) },
            onDismiss = { mainVm.dismissDeleteRequest() }
        )
    }

    conflictPrompt?.let { prompt ->
        ConflictResolutionDialog(
            prompt = prompt,
            onResolve = { action, applyAll -> mainVm.resolveConflict(action, applyAll) }
        )
    }

    moveTargets?.let { items ->
        FolderPickerDialog(
            title = "Move ${items.size} Item(s) To",
            roots = storageRoots,
            loadSubfolders = { path, saf -> mainVm.listFolderForPicker(path, saf) },
            onDismiss = { moveTargets = null },
            onSelectFolder = { destPath, destSaf, _ ->
                moveTargets = null
                mainVm.performCopyOrMove(items, destPath, destSaf, isMove = true)
            }
        )
    }

    saveCopyTarget?.let { item ->
        FolderPickerDialog(
            title = "Save Copy of '${item.name}' To",
            roots = storageRoots,
            loadSubfolders = { path, saf -> mainVm.listFolderForPicker(path, saf) },
            onDismiss = { saveCopyTarget = null },
            onSelectFolder = { destPath, destSaf, _ ->
                saveCopyTarget = null
                mainVm.performCopyOrMove(listOf(item), destPath, destSaf, isMove = false)
            }
        )
    }

    batchRenameTargets?.let { items ->
        BatchRenameDialog(
            items = items,
            onDismiss = { batchRenameTargets = null },
            onApply = { cfg ->
                batchRenameTargets = null
                mainVm.executeBatchRename(items, cfg)
            }
        )
    }

    zipCreateTargets?.let { items ->
        val defaultZip = if (items.size == 1) "${items.first().name.substringBeforeLast('.')}.zip" else "Archive.zip"
        val currentDir = mainVm.currentFolderInfo()
        val destFolderPath = currentDir?.takeIf { !it.isSafUri }?.pathOrUri
            ?: storageRoots.firstOrNull { !it.isSafUri }?.pathOrUri
            ?: context.filesDir.absolutePath

        CreateZipDialog(
            selectedCount = items.size,
            defaultZipName = defaultZip,
            onDismiss = { zipCreateTargets = null },
            onCreate = { zipName, compression, pwd ->
                zipCreateTargets = null
                mainVm.createZipArchive(items, destFolderPath, zipName, compression, pwd)
            }
        )
    }

    propertiesTarget?.let { item ->
        val apkMeta = remember(item.pathOrUri) { mainVm.inspectApk(item) }
        PropertiesBottomSheet(
            item = item,
            apkMetadata = apkMeta,
            computeFolderMetrics = { mainVm.computeFolderMetrics(it) },
            onCopyPath = { mainVm.copyPathToClipboard(item) },
            onRename = { propertiesTarget = null; renameTarget = item },
            onShare = { propertiesTarget = null; mainVm.shareItems(context, listOf(item)) },
            onOpenLocation = {
                propertiesTarget = null
                if (item.parentPathOrUri.isNotBlank()) {
                    mainVm.navigateToLocation(item.parentPathOrUri, item.isSafUri)
                    navigateToTopTab(TopTab.FILES)
                }
            },
            onDismiss = { propertiesTarget = null }
        )
    }

    OperationProgressDialog(
        progress = operationProgress,
        onCancel = { mainVm.cancelActiveOperation() }
    )
}

private fun extractIntentUri(intent: Intent?): Uri? {
    if (intent == null) return null
    intent.data?.let { return it }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

private fun isFileOpenIntent(intent: Intent?): Boolean {
    val action = intent?.action ?: return false
    if (action != Intent.ACTION_VIEW && action != Intent.ACTION_EDIT) return false
    val uri = extractIntentUri(intent) ?: return false
    val scheme = uri.scheme?.lowercase()
    return scheme == "content" || scheme == "file"
}

private fun resolveExternalIntentToStorageItem(context: Context, intent: Intent): StorageItem? {
    val uri = extractIntentUri(intent) ?: return null
    if (uri.scheme.equals("file", ignoreCase = true) && !uri.path.isNullOrBlank()) {
        val file = File(uri.path!!)
        val ext = MimeAndCategoryHelper.extractExtension(file.name)
        val mime = intent.type?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: MimeAndCategoryHelper.getMimeType(file.name)
        return StorageItem(
            pathOrUri = file.absolutePath,
            name = file.name.ifBlank { "opened_file" },
            parentPathOrUri = file.parent ?: "",
            isDirectory = file.isDirectory,
            isSafUri = false,
            size = if (file.exists()) file.length() else 0L,
            lastModified = if (file.exists()) file.lastModified() else System.currentTimeMillis(),
            extension = ext,
            mimeType = mime,
            category = MimeAndCategoryHelper.categorize(file.name, file.absolutePath, mime)
        )
    }

    var displayName: String? = null
    var fileSize = 0L
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
            }
        }
    }

    val rawMime = intent.type?.takeIf { it.isNotBlank() && it != "*/*" }
        ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()

    var finalName = displayName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "opened_file"

    if (MimeAndCategoryHelper.extractExtension(finalName).isEmpty() && !rawMime.isNullOrBlank()) {
        val inferredExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(rawMime)
        if (!inferredExt.isNullOrBlank()) {
            finalName = "$finalName.$inferredExt"
        }
    }

    val resolvedMime = MimeAndCategoryHelper.getMimeType(finalName, rawMime)
    val ext = MimeAndCategoryHelper.extractExtension(finalName)
    return StorageItem(
        pathOrUri = uri.toString(),
        name = finalName,
        parentPathOrUri = "",
        isDirectory = false,
        isSafUri = true,
        size = fileSize,
        lastModified = System.currentTimeMillis(),
        extension = ext,
        mimeType = resolvedMime,
        category = MimeAndCategoryHelper.categorize(finalName, uri.toString(), resolvedMime)
    )
}

private fun looksLikeUtf8Text(context: Context, item: StorageItem): Boolean {
    if (item.size > 2L * 1024L * 1024L) return false
    return runCatching {
        val stream = if (item.isSafUri) {
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
        } else {
            File(item.pathOrUri).inputStream()
        }
        stream?.use { input ->
            val buf = ByteArray(512)
            val read = input.read(buf)
            if (read <= 0) return@runCatching true
            var suspiciousControl = 0
            for (i in 0 until read) {
                val b = buf[i].toInt() and 0xFF
                if (b == 0) return@runCatching false
                if (b < 0x09 || (b in 0x0E..0x1F && b != 0x1B)) {
                    suspiciousControl++
                }
            }
            suspiciousControl * 10 < read
        } ?: false
    }.getOrDefault(false)
}
