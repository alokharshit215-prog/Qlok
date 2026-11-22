package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory
import com.example.model.ScanProgress
import com.example.model.StorageItem
import com.example.model.StorageStats
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryIcon
import com.example.util.FormatUtils

@Composable
fun StorageAnalyzerScreen(
    stats: StorageStats,
    scanProgress: ScanProgress,
    largeFiles: List<StorageItem>,
    thresholdMb: Int,
    onThresholdChange: (Int) -> Unit,
    onScanStorage: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFile: (StorageItem) -> Unit,
    onShareFile: (StorageItem) -> Unit,
    onMoveFile: (StorageItem) -> Unit,
    onDeleteFile: (StorageItem) -> Unit,
    onPropertiesFile: (StorageItem) -> Unit
) {
    val thresholds = listOf(5, 10, 25, 50, 100, 500)

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
                Text("Storage Analyzer", style = MaterialTheme.typography.headlineMedium)
                if (scanProgress.isScanning) {
                    OutlinedButton(onClick = onCancelScan) { Text("Cancel Scan") }
                } else {
                    Button(onClick = onScanStorage) {
                        Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analyze")
                    }
                }
            }
        }

        // Storage Summary Card
        item {
            val pct = (stats.usagePercent * 100).toInt().coerceIn(0, 100)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Capacity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatBytes(stats.totalBytes), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Usage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pct%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { stats.usagePercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Used: ${FormatUtils.formatBytes(stats.usedBytes)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Free: ${FormatUtils.formatBytes(stats.freeBytes)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Recycle Bin & Operation History Shortcuts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onOpenRecycleBin,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Recycle Bin", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Surface(
                    onClick = onOpenHistory,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("History", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // Category Breakdown
        item {
            Text("Storage by Category", style = MaterialTheme.typography.titleMedium)
        }
        items(FileCategory.entries) { cat ->
            val size = stats.categorySizes[cat] ?: 0L
            val count = stats.categoryCounts[cat] ?: 0
            val totalUsedForBar = stats.categorySizes.values.sum().coerceAtLeast(1L)
            val fraction = (size.toDouble() / totalUsedForBar.toDouble()).toFloat().coerceIn(0f, 1f)
            val accent = categoryColor(cat)

            Surface(
                onClick = { onOpenCategory(cat) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(categoryIcon(cat), contentDescription = cat.displayName, tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("$count files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(FormatUtils.formatBytes(size), style = MaterialTheme.typography.titleSmall, color = accent)
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = accent,
                        trackColor = accent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // Large Files Section
        item {
            Spacer(Modifier.height(4.dp))
            Text("Large Files (≥ $thresholdMb MB)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                thresholds.forEach { mb ->
                    FilterChip(
                        selected = thresholdMb == mb,
                        onClick = { onThresholdChange(mb) },
                        label = { Text("≥ $mb MB") }
                    )
                }
            }
        }

        if (largeFiles.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No indexed files larger than $thresholdMb MB. Run Storage Scan to refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(largeFiles, key = { it.pathOrUri }) { item ->
                var menuOpen by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable { onOpenFile(item) }
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
                                "${FormatUtils.formatBytes(item.size)} • ${FormatUtils.formatShortDate(item.lastModified)}",
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
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Large file actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpenFile(item) })
                                DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShareFile(item) })
                                DropdownMenuItem(text = { Text("Move") }, onClick = { menuOpen = false; onMoveFile(item) })
                                DropdownMenuItem(text = { Text("Properties") }, onClick = { menuOpen = false; onPropertiesFile(item) })
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; onDeleteFile(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
