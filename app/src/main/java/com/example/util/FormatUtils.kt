package com.example.util

import com.example.model.BatchRenameConfig
import com.example.model.SortDirection
import com.example.model.SortOption
import com.example.model.StorageItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
        }
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "Unknown"
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        if (millis <= 0L) return "—"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "00:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun sortItems(
        items: List<StorageItem>,
        option: SortOption,
        direction: SortDirection
    ): List<StorageItem> {
        val comparator = when (option) {
            SortOption.NAME -> compareBy<StorageItem> { it.name.lowercase(Locale.getDefault()) }
            SortOption.DATE -> compareBy<StorageItem> { it.lastModified }
            SortOption.SIZE -> compareBy<StorageItem> { it.size }
            SortOption.TYPE -> compareBy<StorageItem> { it.extension.lowercase(Locale.getDefault()) }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        }
        val directed = if (direction == SortDirection.ASCENDING) comparator else comparator.reversed()
        // Always keep folders grouped before files
        return items.sortedWith(compareByDescending<StorageItem> { it.isDirectory }.then(directed))
    }

    fun computeKeepBothName(baseFileName: String, existingNames: Set<String>): String {
        if (baseFileName !in existingNames) return baseFileName
        val dotIndex = baseFileName.lastIndexOf('.')
        val stem = if (dotIndex > 0) baseFileName.substring(0, dotIndex) else baseFileName
        val ext = if (dotIndex > 0) baseFileName.substring(dotIndex) else ""
        var counter = 1
        while (true) {
            val candidate = "$stem ($counter)$ext"
            if (candidate !in existingNames) return candidate
            counter++
        }
    }

    fun previewBatchRename(
        items: List<StorageItem>,
        config: BatchRenameConfig
    ): List<Pair<StorageItem, String>> {
        return items.mapIndexed { index, item ->
            val dotIndex = if (!item.isDirectory) item.name.lastIndexOf('.') else -1
            val stem = if (dotIndex > 0) item.name.substring(0, dotIndex) else item.name
            val ext = if (dotIndex > 0) item.name.substring(dotIndex) else ""

            var newStem = if (config.useNumbering) {
                val number = (config.startNumber + index).toString().padStart(config.padDigits.coerceIn(1, 6), '0')
                val base = config.baseName.ifBlank { stem }
                "${base}_$number"
            } else {
                stem
            }

            if (config.findText.isNotEmpty()) {
                newStem = newStem.replace(config.findText, config.replaceText)
            }

            val finalName = "${config.prefix}$newStem${config.suffix}$ext"
            item to finalName
        }
    }
}
