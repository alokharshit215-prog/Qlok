package com.example.data.storage

import android.content.Context
import android.net.Uri
import com.example.model.ZipCompressionMode
import com.example.model.ZipEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileOutputStream

class ZipPasswordRequiredException(message: String) : Exception(message)
class ZipWrongPasswordException(message: String) : Exception(message)
class ZipPathTraversalException(message: String) : Exception(message)

class ZipArchiveService(private val context: Context) {

    /**
     * Resolves either a direct File path or copies a SAF content:// archive into a temp cache copy
     * for non-destructive browsing/extraction. For File paths, operates directly on the real file.
     */
    private fun resolveArchiveFile(pathOrUri: String, isSafUri: Boolean): Pair<File, Boolean> {
        if (!isSafUri) {
            return File(pathOrUri) to false
        }
        val cacheFile = File(context.cacheDir, "saf_zip_${pathOrUri.hashCode()}.zip")
        context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open ZIP stream from URI")
        return cacheFile to true
    }

    suspend fun listEntries(
        pathOrUri: String,
        isSafUri: Boolean,
        password: String? = null
    ): List<ZipEntryItem> = withContext(Dispatchers.IO) {
        val (archiveFile, isTemp) = resolveArchiveFile(pathOrUri, isSafUri)
        try {
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(archiveFile, password.toCharArray())
            } else {
                ZipFile(archiveFile)
            }
            if (!zip.isValidZipFile) {
                throw ZipException("Invalid or corrupted ZIP archive")
            }
            val headers = zip.fileHeaders
            headers.map { header ->
                val cleanName = header.fileName.trimEnd('/')
                val slash = cleanName.lastIndexOf('/')
                val simpleName = if (slash >= 0) cleanName.substring(slash + 1) else cleanName
                val parentPath = if (slash >= 0) cleanName.substring(0, slash) else ""
                ZipEntryItem(
                    entryName = header.fileName,
                    simpleName = simpleName,
                    parentEntryPath = parentPath,
                    isDirectory = header.isDirectory,
                    uncompressedSize = header.uncompressedSize,
                    compressedSize = header.compressedSize,
                    lastModified = header.lastModifiedTimeEpoch,
                    isEncrypted = header.isEncrypted
                )
            }
        } finally {
            if (isTemp) archiveFile.delete()
        }
    }

    suspend fun isPasswordProtected(pathOrUri: String, isSafUri: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val (archiveFile, isTemp) = resolveArchiveFile(pathOrUri, isSafUri)
            try {
                val zip = ZipFile(archiveFile)
                zip.isEncrypted
            } catch (_: Exception) {
                false
            } finally {
                if (isTemp) archiveFile.delete()
            }
        }

    suspend fun createZip(
        sourceFiles: List<File>,
        destinationZipFile: File,
        compressionMode: ZipCompressionMode,
        password: String? = null,
        onProgress: (currentName: String, completed: Int, total: Int) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (destinationZipFile.exists()) {
                destinationZipFile.delete()
            }
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(destinationZipFile, password.toCharArray())
            } else {
                ZipFile(destinationZipFile)
            }

            val params = ZipParameters().apply {
                compressionLevel = when (compressionMode) {
                    ZipCompressionMode.FAST -> CompressionLevel.FASTEST
                    ZipCompressionMode.NORMAL -> CompressionLevel.NORMAL
                    ZipCompressionMode.MAXIMUM -> CompressionLevel.ULTRA
                }
                if (!password.isNullOrEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                }
            }

            val total = sourceFiles.size
            sourceFiles.forEachIndexed { index, file ->
                onProgress(file.name, index, total)
                if (file.isDirectory) {
                    zip.addFolder(file, params)
                } else if (file.exists()) {
                    zip.addFile(file, params)
                }
                onProgress(file.name, index + 1, total)
            }
            destinationZipFile
        }
    }

    /**
     * Extracts either all entries or selected entries into destinationDir with strict
     * Zip Slip / Path Traversal verification on every single header.
     */
    suspend fun extractEntries(
        pathOrUri: String,
        isSafUri: Boolean,
        destinationDir: File,
        selectedEntryNames: List<String>? = null,
        password: String? = null,
        onProgress: (currentName: String, completed: Int, total: Int) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        val (archiveFile, isTemp) = resolveArchiveFile(pathOrUri, isSafUri)
        try {
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(archiveFile, password.toCharArray())
            } else {
                ZipFile(archiveFile)
            }

            if (!zip.isValidZipFile) {
                return@withContext Result.failure(ZipException("Archive is corrupted or not a valid ZIP file"))
            }

            if (zip.isEncrypted && password.isNullOrEmpty()) {
                return@withContext Result.failure(ZipPasswordRequiredException("Password required to extract encrypted archive"))
            }

            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("Cannot create destination folder ${destinationDir.absolutePath}"))
            }

            val destCanonical = destinationDir.canonicalFile
            val headers = zip.fileHeaders.filter { header ->
                selectedEntryNames == null || selectedEntryNames.any { sel ->
                    header.fileName == sel || header.fileName.startsWith(sel.trimEnd('/') + "/")
                }
            }

            // Strictly validate EVERY entry against path traversal BEFORE extracting
            for (header in headers) {
                val safeRelative = sanitizeEntryPath(header.fileName)
                val targetFile = File(destCanonical, safeRelative).canonicalFile
                val destPrefix = destCanonical.path.trimEnd(File.separatorChar) + File.separator
                if (targetFile.path != destCanonical.path && !targetFile.path.startsWith(destPrefix)) {
                    return@withContext Result.failure(
                        ZipPathTraversalException("Blocked unsafe archive entry path: ${header.fileName}")
                    )
                }
            }

            var extractedCount = 0
            val total = headers.size
            for ((index, header) in headers.withIndex()) {
                onProgress(header.fileName, index, total)
                val safeRelative = sanitizeEntryPath(header.fileName)
                val targetFile = File(destCanonical, safeRelative).canonicalFile
                if (header.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    try {
                        zip.extractFile(header, destCanonical.absolutePath, safeRelative)
                        extractedCount++
                    } catch (e: ZipException) {
                        if (e.type == ZipException.Type.WRONG_PASSWORD ||
                            e.message?.contains("Wrong password", ignoreCase = true) == true
                        ) {
                            return@withContext Result.failure(ZipWrongPasswordException("Incorrect archive password"))
                        }
                        throw e
                    }
                }
                onProgress(header.fileName, index + 1, total)
            }

            Result.success(extractedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (isTemp) archiveFile.delete()
        }
    }

    suspend fun addFilesToZip(
        zipFile: File,
        filesToAdd: List<File>,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(zipFile, password.toCharArray())
            } else {
                ZipFile(zipFile)
            }
            val params = ZipParameters()
            filesToAdd.forEach { file ->
                if (file.isDirectory) zip.addFolder(file, params) else zip.addFile(file, params)
            }
        }
    }

    suspend fun removeEntryFromZip(
        zipFile: File,
        entryName: String,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(zipFile, password.toCharArray())
            } else {
                ZipFile(zipFile)
            }
            zip.removeFile(entryName)
        }
    }

    suspend fun renameEntryInZip(
        zipFile: File,
        oldEntryName: String,
        newEntryName: String,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val safeNew = sanitizeEntryPath(newEntryName)
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(zipFile, password.toCharArray())
            } else {
                ZipFile(zipFile)
            }
            zip.renameFile(oldEntryName, safeNew)
        }
    }

    companion object {
        fun sanitizeEntryPath(rawPath: String): String {
            val normalized = rawPath.replace('\\', '/').trimStart('/')
            val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.any { it == ".." }) {
                throw ZipPathTraversalException("Blocked path traversal entry: $rawPath")
            }
            return parts.joinToString("/")
        }
    }
}
