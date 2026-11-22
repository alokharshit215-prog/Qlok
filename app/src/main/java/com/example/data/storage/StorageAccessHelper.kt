package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.model.ApkMetadata
import com.example.model.StorageRoot
import java.io.File

class StorageAccessHelper(private val context: Context) {

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Below Android 11, SAF trees or app external workspace provide access without broad media permissions
            true
        }
    }

    fun getPersistedSafTrees(): List<Uri> {
        return context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
    }

    fun hasAnyStorageAccess(): Boolean {
        val extRoot = Environment.getExternalStorageDirectory()
        val canReadExternal = hasAllFilesAccess() && extRoot.exists() && extRoot.canRead()
        return canReadExternal || getPersistedSafTrees().isNotEmpty()
    }

    fun createAllFilesAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun discoverAccessibleRoots(): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()
        val extDir = Environment.getExternalStorageDirectory()

        if (extDir.exists() && (hasAllFilesAccess() || extDir.canRead())) {
            val stats = readStatFs(extDir)
            roots.add(
                StorageRoot(
                    id = "primary_external",
                    label = "Internal Storage",
                    pathOrUri = extDir.absolutePath,
                    isSafUri = false,
                    totalBytes = stats.first,
                    freeBytes = stats.second
                )
            )
        }

        // Discover any secondary mounted storage volumes accessible via standard File APIs
        val externalAppDirs = context.getExternalFilesDirs(null)
        externalAppDirs.filterNotNull().drop(1).forEachIndexed { idx, appDir ->
            // Walk up from /storage/XXXX-XXXX/Android/data/<pkg>/files if root is readable
            val rootCandidate = appDir.parentFile?.parentFile?.parentFile?.parentFile
            if (rootCandidate != null && rootCandidate.exists() && rootCandidate.canRead() &&
                roots.none { it.pathOrUri == rootCandidate.absolutePath }
            ) {
                val stats = readStatFs(rootCandidate)
                roots.add(
                    StorageRoot(
                        id = "external_volume_$idx",
                        label = "External Volume ${idx + 1}",
                        pathOrUri = rootCandidate.absolutePath,
                        isSafUri = false,
                        totalBytes = stats.first,
                        freeBytes = stats.second
                    )
                )
            }
        }

        // Add persisted SAF roots
        getPersistedSafTrees().forEach { uri ->
            val doc = DocumentFile.fromTreeUri(context, uri)
            if (doc != null && doc.exists() && doc.canRead()) {
                roots.add(
                    StorageRoot(
                        id = "saf_${uri.hashCode()}",
                        label = doc.name ?: "SAF Storage",
                        pathOrUri = uri.toString(),
                        isSafUri = true
                    )
                )
            }
        }

        return roots
    }

    fun getPrimaryStorageBytes(): Pair<Long, Long> {
        val target = Environment.getExternalStorageDirectory()
        return readStatFs(if (target.exists()) target else context.filesDir)
    }

    private fun readStatFs(dir: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(dir.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            total to free
        } catch (_: Exception) {
            0L to 0L
        }
    }

    fun isAndroidProtectedDirectory(dir: File): Boolean {
        val normalized = dir.absolutePath.replace('\\', '/')
        val isRestrictedSegment = normalized.endsWith("/Android/data") ||
            normalized.endsWith("/Android/obb") ||
            normalized.contains("/Android/data/") ||
            normalized.contains("/Android/obb/")
        if (isRestrictedSegment && (!dir.canRead() || dir.listFiles() == null)) {
            return true
        }
        return !dir.canRead() || dir.listFiles() == null
    }

    fun getShareableUri(pathOrUri: String, isSafUri: Boolean): Uri {
        return if (isSafUri) {
            Uri.parse(pathOrUri)
        } else {
            val file = File(pathOrUri)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    fun buildOpenWithIntent(pathOrUri: String, isSafUri: Boolean, mimeType: String): Intent {
        val uri = getShareableUri(pathOrUri, isSafUri)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return Intent.createChooser(viewIntent, "Open with").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareSingleIntent(pathOrUri: String, isSafUri: Boolean, mimeType: String): Intent {
        val uri = getShareableUri(pathOrUri, isSafUri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share file").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareMultipleIntent(items: List<Pair<String, Boolean>>): Intent {
        val uris = ArrayList<Uri>(items.map { (p, saf) -> getShareableUri(p, saf) })
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share ${items.size} items").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun inspectApk(filePath: String): ApkMetadata? {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(filePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(filePath, 0)
            } ?: return null
            info.applicationInfo?.sourceDir = filePath
            info.applicationInfo?.publicSourceDir = filePath
            val appName = info.applicationInfo?.loadLabel(pm)?.toString() ?: File(filePath).nameWithoutExtension
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            ApkMetadata(
                appName = appName,
                packageName = info.packageName ?: "Unknown",
                versionName = info.versionName ?: "Unknown",
                versionCode = versionCode
            )
        } catch (_: Exception) {
            null
        }
    }
}
