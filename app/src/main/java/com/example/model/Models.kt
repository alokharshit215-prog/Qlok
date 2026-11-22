package com.example.model

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ViewMode {
    LIST,
    GRID
}

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE("Date Modified"),
    SIZE("Size"),
    TYPE("Type")
}

enum class SortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class FileCategory(val displayName: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads"),
    APK("APK"),
    ARCHIVES("Archives"),
    OTHER("Other")
}

data class StorageItem(
    val pathOrUri: String,
    val name: String,
    val parentPathOrUri: String,
    val isDirectory: Boolean,
    val isSafUri: Boolean = false,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val createdAt: Long? = null,
    val extension: String = "",
    val mimeType: String = "application/octet-stream",
    val category: FileCategory = FileCategory.OTHER,
    val isHidden: Boolean = false,
    val isProtected: Boolean = false,
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val isFavorite: Boolean = false,
    val childCount: Int? = null
)

data class StorageRoot(
    val id: String,
    val label: String,
    val pathOrUri: String,
    val isSafUri: Boolean,
    val isInternalAppWorkspace: Boolean = false,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L
)

data class StorageStats(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usagePercent: Float = 0f,
    val categorySizes: Map<FileCategory, Long> = emptyMap(),
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val totalIndexedFiles: Int = 0,
    val totalIndexedFolders: Int = 0,
    val largeFiles: List<StorageItem> = emptyList()
)

data class ScanProgress(
    val isScanning: Boolean = false,
    val currentFolder: String = "",
    val filesScanned: Int = 0,
    val foldersScanned: Int = 0,
    val errorCount: Int = 0,
    val protectedCount: Int = 0,
    val lastMessage: String? = null
)

data class OperationProgress(
    val isRunning: Boolean = false,
    val operationName: String = "",
    val currentFile: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val isCancellable: Boolean = true
)

enum class ClipboardMode {
    COPY,
    CUT
}

data class ClipboardState(
    val mode: ClipboardMode = ClipboardMode.COPY,
    val items: List<StorageItem> = emptyList()
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

enum class ConflictAction {
    REPLACE,
    KEEP_BOTH,
    SKIP,
    CANCEL
}

data class ConflictPrompt(
    val fileName: String,
    val sourcePathOrUri: String,
    val destinationFolder: String,
    val remainingCount: Int
)

enum class ZipCompressionMode(val label: String) {
    FAST("Fast"),
    NORMAL("Normal"),
    MAXIMUM("Maximum")
}

data class ZipEntryItem(
    val entryName: String,
    val simpleName: String,
    val parentEntryPath: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val lastModified: Long,
    val isEncrypted: Boolean
)

data class ApkMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

data class BatchRenameConfig(
    val prefix: String = "",
    val suffix: String = "",
    val findText: String = "",
    val replaceText: String = "",
    val useNumbering: Boolean = false,
    val baseName: String = "",
    val startNumber: Int = 1,
    val padDigits: Int = 3
)

enum class CloudServiceType(val title: String, val isSupportedNow: Boolean, val subtitle: String) {
    FTP("FTP Server", true, "Real FTP protocol browser & file transfer"),
    GOOGLE_DRIVE("Google Drive", false, "Coming Soon"),
    DROPBOX("Dropbox", false, "Coming Soon"),
    ONEDRIVE("OneDrive", false, "Coming Soon"),
    BOX("Box", false, "Coming Soon"),
    NAS("NAS", false, "Coming Soon"),
    SMB("SMB / Windows Share", false, "Coming Soon")
}

data class RemoteFtpEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val timestamp: Long
)
