package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.FavoriteEntity
import com.example.data.db.RecentFileEntity
import com.example.model.FileCategory
import com.example.model.ScanProgress
import com.example.model.StorageItem
import com.example.model.StorageStats
import com.example.ui.components.BlueFileManagerLogo
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.theme.BrightBlue
import com.example.ui.theme.DarkBlue
import com.example.ui.theme.LightBlue
import com.example.ui.theme.PrimaryBlue
import com.example.util.FormatUtils

@Composable
fun HomeScreen(
    hasStorageAccess: Boolean,
    stats: StorageStats,
    scanProgress: ScanProgress,
    recentFiles: List<RecentFileEntity>,
    favorites: List<FavoriteEntity>,
    onGrantStorageAccess: () -> Unit,
    onScanStorage: () -> Unit,
    onCancelScan: () -> Unit,
    onRefresh: () -> Unit,
    onAnalyzeStorage: () -> Unit,
    onOpenBrowseFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit,
    onOpenRecentScreen: () -> Unit,
    onOpenFavoritesScreen: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenHistory: () -> Unit,
    onQuickNewFolder: () -> Unit,
    onQuickNewFile: () -> Unit,
    onQuickNewCodeFile: () -> Unit,
    onQuickZip: () -> Unit,
    onOpenRecentItem: (RecentFileEntity) -> Unit,
    onOpenFavoriteItem: (FavoriteEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Polished Top Header with Brand Badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BlueFileManagerLogo(size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Blue File Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hasStorageAccess) {
                                "${stats.totalIndexedFiles} files • ${stats.totalIndexedFolders} folders indexed"
                            } else {
                                "Grant storage access to browse & scan"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.testTag("home_search_button")
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search files",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("home_refresh_button")
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Quick Search Pill Bar
        item {
            Surface(
                onClick = onOpenSearch,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Search files, folders, documents, code...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Storage Hero Overview Card with Decorative Radial Accents
        item {
            val pctInt = (stats.usagePercent * 100).toInt().coerceIn(0, 100)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(DarkBlue, PrimaryBlue, BrightBlue)
                        )
                    )
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            radius = size.height * 0.65f,
                            center = Offset(size.width * 0.92f, size.height * 0.15f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.06f),
                            radius = size.height * 0.45f,
                            center = Offset(size.width * 0.12f, size.height * 0.95f)
                        )
                    }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${FormatUtils.formatBytes(stats.usedBytes)} used of ${FormatUtils.formatBytes(stats.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LightBlue.copy(alpha = 0.92f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "$pctInt%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { stats.usagePercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.26f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Free Space", style = MaterialTheme.typography.labelSmall, color = LightBlue.copy(alpha = 0.88f))
                            Text(
                                FormatUtils.formatBytes(stats.freeBytes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = onAnalyzeStorage,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = DarkBlue
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("analyze_storage_button")
                        ) {
                            Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Analyze", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Storage Access / Scan Prompt / Live Scan Progress
        item {
            AnimatedVisibility(visible = scanProgress.isScanning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scanning Storage...", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            TextButton(onClick = onCancelScan) { Text("Cancel") }
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = scanProgress.currentFolder,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${scanProgress.filesScanned} files • ${scanProgress.foldersScanned} folders • ${scanProgress.protectedCount} protected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!hasStorageAccess) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage Access Required", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Grant official Android storage access to scan and manage real files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onGrantStorageAccess,
                                modifier = Modifier.testTag("home_grant_storage_access")
                            ) {
                                Text("GRANT STORAGE ACCESS")
                            }
                        }
                    }
                }
            } else if (!scanProgress.isScanning && stats.totalIndexedFiles == 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Index Your Device Files", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Scan accessible folders to populate categories & instant search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onScanStorage,
                            modifier = Modifier.testTag("home_scan_storage_button")
                        ) {
                            Text("SCAN")
                        }
                    }
                }
            }
        }

        // Categories 2x4 Grid
        item {
            SectionHeader(title = "Categories", actionLabel = "Browse Files", onAction = onOpenBrowseFiles)
            Spacer(Modifier.height(6.dp))
            val categories = FileCategory.entries
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { category ->
                            val count = stats.categoryCounts[category] ?: 0
                            val size = stats.categorySizes[category] ?: 0L
                            CategoryTile(
                                category = category,
                                count = count,
                                sizeBytes = size,
                                onClick = { onOpenCategory(category) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions (Crisp 2x4 Grid with non-truncating labels & >=48dp touch targets)
        item {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val actions = listOf(
                Triple("New Folder", Icons.Rounded.CreateNewFolder, onQuickNewFolder),
                Triple("New File", Icons.Rounded.InsertDriveFile, onQuickNewFile),
                Triple("New Code", Icons.Rounded.Code, onQuickNewCodeFile),
                Triple("Scan", Icons.Rounded.Radar, onScanStorage),
                Triple("Refresh", Icons.Rounded.Refresh, onRefresh),
                Triple("Recent", Icons.Rounded.History, onOpenRecentScreen),
                Triple("Favorites", Icons.Rounded.Star, onOpenFavoritesScreen),
                Triple("Create ZIP", Icons.Rounded.FolderZip, onQuickZip)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(4).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowActions.forEach { (label, icon, action) ->
                            QuickActionChip(
                                label = label,
                                icon = icon,
                                onClick = action,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Utility Tool Cards: Recycle Bin & Operation History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeToolCard(
                    title = "Recycle Bin",
                    subtitle = "Recover deleted files",
                    icon = Icons.Rounded.DeleteSweep,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onOpenRecycleBin,
                    modifier = Modifier.weight(1f).testTag("home_recycle_bin_card")
                )
                HomeToolCard(
                    title = "Operations",
                    subtitle = "History & activity",
                    icon = Icons.Rounded.History,
                    accent = BrightBlue,
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f).testTag("home_operations_card")
                )
            }
        }

        // Recent Files Strip
        item {
            SectionHeader(title = "Recent Files", actionLabel = "View All", onAction = onOpenRecentScreen)
            Spacer(Modifier.height(6.dp))
            if (recentFiles.isEmpty()) {
                ActionableEmptyStripNote(
                    title = "No recent files yet",
                    subtitle = "Tap to browse your folders or open any file to track it here.",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = onOpenBrowseFiles
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentFiles.take(12), key = { it.pathOrUri }) { entry ->
                        RecentOrFavoriteMiniCard(
                            name = entry.name,
                            subtitle = entry.actionLabel,
                            item = StorageItem(
                                pathOrUri = entry.pathOrUri,
                                name = entry.name,
                                parentPathOrUri = entry.parentPathOrUri,
                                isDirectory = entry.isDirectory,
                                isSafUri = entry.isSafUri,
                                size = entry.size,
                                lastModified = entry.lastModified,
                                extension = entry.extension,
                                mimeType = entry.mimeType
                            ),
                            onClick = { onOpenRecentItem(entry) }
                        )
                    }
                }
            }
        }

        // Favorites Strip
        item {
            SectionHeader(title = "Favorites", actionLabel = "Manage", onAction = onOpenFavoritesScreen)
            Spacer(Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                ActionableEmptyStripNote(
                    title = "No favorites pinned",
                    subtitle = "Star files or folders in the browser to pin them here.",
                    icon = Icons.Rounded.Star,
                    onClick = onOpenBrowseFiles
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favorites.take(12), key = { it.pathOrUri }) { fav ->
                        RecentOrFavoriteMiniCard(
                            name = fav.name,
                            subtitle = if (fav.isDirectory) "Folder" else FormatUtils.formatBytes(fav.size),
                            item = StorageItem(
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
                            ),
                            onClick = { onOpenFavoriteItem(fav) }
                        )
                    }
                }
            }
        }

        // Subtle Footer Credits
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Blue File Manager • Made by Alok • Support by Harshit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CategoryTile(
    category: FileCategory,
    count: Int,
    sizeBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = categoryColor(category)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .testTag("category_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category),
                    contentDescription = category.displayName,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count • ${FormatUtils.formatBytes(sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .defaultMinSize(minHeight = 76.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ActionableEmptyStripNote(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RecentOrFavoriteMiniCard(
    name: String,
    subtitle: String,
    item: StorageItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .width(146.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileTypeIconBadge(item = item, size = 40.dp)
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
