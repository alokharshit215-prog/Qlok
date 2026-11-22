package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.EditorDraftEntity
import com.example.data.db.FavoriteEntity
import com.example.data.db.FileIndexDao
import com.example.data.db.IndexedFileEntity
import com.example.data.db.OperationHistoryEntity
import com.example.data.db.RecentFileEntity
import com.example.data.db.RecycleBinEntity
import com.example.data.db.SafRootEntity
import com.example.data.preferences.SettingsDataStore
import com.example.model.BatchRenameConfig
import com.example.model.ConflictAction
import com.example.model.ConflictPrompt
import com.example.model.FileCategory
import com.example.model.OperationProgress
import com.example.model.ScanProgress
import com.example.model.StorageItem
import com.example.util.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

class RealFileOperationsRepository(
    private val context: Context,
    private val dao: FileIndexDao,
    val storageAccessHelper: StorageAccessHelper,
    private val settingsDataStore: SettingsDataStore
) {
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _operationProgress = MutableStateFlow(OperationProgress())
    val operationProgress: StateFlow<OperationProgress> = _operationProgress.asStateFlow()

    private val trashRootDir: File by lazy {
        File(context.filesDir, "recycle_bin_vault").apply { mkdirs() }
    }

    // Reactive Database Flows
    val indexedFileCount: Flow<Int> = dao.observeIndexedFileCount()
    val indexedFolderCount: Flow<Int> = dao.observeIndexedFolderCount()
    val recentFiles: Flow<List<RecentFileEntity>> = dao.observeRecentFiles()
    val favorites: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val favoriteKeys: Flow<Set<String>> = dao.observeFavoriteKeys().map { it.toSet() }
    val recycleBinItems: Flow<List<RecycleBinEntity>> = dao.observeRecycleBin()
    val operationHistory: Flow<List<OperationHistoryEntity>> = dao.observeOperationHistory()
    val categoryAggregations = dao.observeCategoryAggregations()

    fun observeCategoryFiles(category: FileCategory, includeHidden: Boolean): Flow<List<StorageItem>> {
        val source = if (category == FileCategory.DOWNLOADS) {
            dao.observeDownloadFolderFiles(includeHidden)
        } else {
            dao.observeFilesByCategory(category.name, includeHidden)
        }
        return source.map { list -> list.map { it.toStorageItem() } }
    }

    fun observeLargeFiles(minSizeBytes: Long): Flow<List<StorageItem>> =
        dao.observeLargeFiles(minSizeBytes).map { list -> list.map { it.toStorageItem() } }

    fun observeRecentlyModifiedFiles(includeHidden: Boolean): Flow<List<StorageItem>> =
        dao.observeRecentlyModifiedFiles(includeHidden).map { list -> list.map { it.toStorageItem() } }

    suspend fun searchFiles(
        query: String,
        category: FileCategory?,
        includeHidden: Boolean,
        currentFolderPath: String? = null
    ): List<StorageItem> = withContext(Dispatchers.IO) {
        val favSet = mutableSetOf<String>()
        val results = dao.searchIndexedFiles(
            query = query.trim(),
            category = category?.name,
            includeHidden = includeHidden
        )
        val filtered = if (currentFolderPath != null) {
            results.filter {
                it.parentPathOrUri == currentFolderPath ||
                    it.pathOrUri.startsWith(currentFolderPath.trimEnd('/') + "/")
            }
        } else {
            results
        }
        filtered.map { entity ->
            val fav = dao.isFavorite(entity.pathOrUri)
            if (fav) favSet.add(entity.pathOrUri)
            entity.toStorageItem(isFavorite = fav)
        }
    }

    // -------------------------------------------------------------------------
    // Directory Listing (Real Filesystem & SAF Document Trees)
    // -------------------------------------------------------------------------
    suspend fun listDirectory(
        parentPathOrUri: String,
        isSafUri: Boolean,
        includeHidden: Boolean
    ): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (isSafUri) {
                listSafDirectory(Uri.parse(parentPathOrUri), includeHidden)
            } else {
                listFileDirectory(File(parentPathOrUri), includeHidden)
            }
        }
    }

    private suspend fun listFileDirectory(dir: File, includeHidden: Boolean): List<StorageItem> {
        if (!dir.exists()) throw IllegalStateException("Folder not found: ${dir.absolutePath}")
        if (storageAccessHelper.isAndroidProtectedDirectory(dir)) {
            throw SecurityException("Android restricts direct access to ${dir.name}")
        }
        val children = dir.listFiles() ?: throw SecurityException("Permission denied for ${dir.absolutePath}")
        val items = mutableListOf<StorageItem>()
        for (child in children) {
            val isHidden = child.isHidden || child.name.startsWith(".")
            if (!includeHidden && isHidden) continue
            val isDir = child.isDirectory
            val isProtected = isDir && storageAccessHelper.isAndroidProtectedDirectory(child)
            val ext = if (isDir) "" else MimeAndCategoryHelper.extractExtension(child.name)
            val mime = if (isDir) "vnd.android.document/directory" else MimeAndCategoryHelper.getMimeType(child.name)
            val category = if (isDir) FileCategory.OTHER else MimeAndCategoryHelper.categorize(child.name, child.absolutePath, mime)
            val fav = dao.isFavorite(child.absolutePath)
            items.add(
                StorageItem(
                    pathOrUri = child.absolutePath,
                    name = child.name,
                    parentPathOrUri = dir.absolutePath,
                    isDirectory = isDir,
                    isSafUri = false,
                    size = if (isDir) 0L else child.length(),
                    lastModified = child.lastModified(),
                    createdAt = readCreatedTime(child),
                    extension = ext,
                    mimeType = mime,
                    category = category,
                    isHidden = isHidden,
                    isProtected = isProtected,
                    canRead = child.canRead(),
                    canWrite = child.canWrite(),
                    isFavorite = fav,
                    childCount = if (isDir && !isProtected) child.list()?.size else null
                )
            )
        }
        // Reconcile directory items in Room index cleanly
        if (items.isNotEmpty()) {
            dao.upsertFiles(items.map { IndexedFileEntity.fromStorageItem(it) })
        }
        return items
    }

    private suspend fun listSafDirectory(uri: Uri, includeHidden: Boolean): List<StorageItem> {
        val docDir = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: throw IllegalStateException("Cannot access SAF directory")
        if (!docDir.exists() || !docDir.canRead()) {
            throw SecurityException("Permission expired or folder inaccessible")
        }
        val items = mutableListOf<StorageItem>()
        for (child in docDir.listFiles()) {
            val name = child.name ?: continue
            val isHidden = name.startsWith(".")
            if (!includeHidden && isHidden) continue
            val isDir = child.isDirectory
            val childUri = child.uri.toString()
            val ext = if (isDir) "" else MimeAndCategoryHelper.extractExtension(name)
            val mime = if (isDir) DocumentsContract.Document.MIME_TYPE_DIR else MimeAndCategoryHelper.getMimeType(name, child.type)
            val category = if (isDir) FileCategory.OTHER else MimeAndCategoryHelper.categorize(name, childUri, mime)
            val fav = dao.isFavorite(childUri)
            items.add(
                StorageItem(
                    pathOrUri = childUri,
                    name = name,
                    parentPathOrUri = uri.toString(),
                    isDirectory = isDir,
                    isSafUri = true,
                    size = if (isDir) 0L else child.length(),
                    lastModified = child.lastModified(),
                    createdAt = null,
                    extension = ext,
                    mimeType = mime,
                    category = category,
                    isHidden = isHidden,
                    isProtected = isDir && !child.canRead(),
                    canRead = child.canRead(),
                    canWrite = child.canWrite(),
                    isFavorite = fav
                )
            )
        }
        if (items.isNotEmpty()) {
            dao.upsertFiles(items.map { IndexedFileEntity.fromStorageItem(it) })
        }
        return items
    }

    private fun readCreatedTime(file: File): Long? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                attrs.creationTime().toMillis()
            }.getOrNull()
        } else {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Recursive Background Storage Scanner
    // -------------------------------------------------------------------------
    suspend fun scanAllAccessibleStorage(): Result<ScanProgress> = withContext(Dispatchers.IO) {
        val roots = storageAccessHelper.discoverAccessibleRoots()
        if (roots.isEmpty()) {
            val msg = "No accessible storage granted. Please grant Storage Access first."
            _scanProgress.value = ScanProgress(isScanning = false, lastMessage = msg)
            return@withContext Result.failure(SecurityException(msg))
        }

        var filesCount = 0
        var foldersCount = 0
        var errorsCount = 0
        var protectedCount = 0
        val batchBuffer = mutableListOf<IndexedFileEntity>()

        suspend fun flushBatch() {
            if (batchBuffer.isNotEmpty()) {
                dao.upsertFiles(batchBuffer.toList())
                batchBuffer.clear()
            }
        }

        try {
            _scanProgress.value = ScanProgress(isScanning = true, currentFolder = "Preparing scan...")
            dao.clearFileIndex()

            val coroutineCtx = currentCoroutineContext()

            suspend fun scanFileTree(dir: File) {
                coroutineCtx.ensureActive()
                if (storageAccessHelper.isAndroidProtectedDirectory(dir)) {
                    protectedCount++
                    return
                }
                _scanProgress.value = _scanProgress.value.copy(
                    currentFolder = dir.absolutePath,
                    filesScanned = filesCount,
                    foldersScanned = foldersCount,
                    errorCount = errorsCount,
                    protectedCount = protectedCount
                )
                val children = try {
                    dir.listFiles()
                } catch (_: Exception) {
                    errorsCount++
                    null
                } ?: run {
                    protectedCount++
                    return
                }

                for (child in children) {
                    coroutineCtx.ensureActive()
                    try {
                        val isDir = child.isDirectory
                        val isHidden = child.isHidden || child.name.startsWith(".")
                        val isProtected = isDir && storageAccessHelper.isAndroidProtectedDirectory(child)
                        val ext = if (isDir) "" else MimeAndCategoryHelper.extractExtension(child.name)
                        val mime = if (isDir) "vnd.android.document/directory" else MimeAndCategoryHelper.getMimeType(child.name)
                        val cat = if (isDir) FileCategory.OTHER else MimeAndCategoryHelper.categorize(child.name, child.absolutePath, mime)

                        batchBuffer.add(
                            IndexedFileEntity(
                                pathOrUri = child.absolutePath,
                                name = child.name,
                                parentPathOrUri = dir.absolutePath,
                                isDirectory = isDir,
                                isSafUri = false,
                                size = if (isDir) 0L else child.length(),
                                lastModified = child.lastModified(),
                                createdAt = readCreatedTime(child),
                                extension = ext,
                                mimeType = mime,
                                category = cat.name,
                                isHidden = isHidden,
                                isProtected = isProtected,
                                canRead = child.canRead(),
                                canWrite = child.canWrite()
                            )
                        )
                        if (batchBuffer.size >= 200) flushBatch()

                        if (isDir) {
                            foldersCount++
                            if (!isProtected) {
                                scanFileTree(child)
                            } else {
                                protectedCount++
                            }
                        } else {
                            filesCount++
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        errorsCount++
                    }
                }
            }

            suspend fun scanSafTree(doc: DocumentFile, parentUri: String) {
                coroutineCtx.ensureActive()
                _scanProgress.value = _scanProgress.value.copy(
                    currentFolder = doc.name ?: parentUri,
                    filesScanned = filesCount,
                    foldersScanned = foldersCount,
                    errorCount = errorsCount,
                    protectedCount = protectedCount
                )
                val children = try {
                    doc.listFiles()
                } catch (_: Exception) {
                    errorsCount++
                    emptyArray()
                }
                for (child in children) {
                    coroutineCtx.ensureActive()
                    try {
                        val name = child.name ?: continue
                        val isDir = child.isDirectory
                        val uriStr = child.uri.toString()
                        val ext = if (isDir) "" else MimeAndCategoryHelper.extractExtension(name)
                        val mime = if (isDir) DocumentsContract.Document.MIME_TYPE_DIR else MimeAndCategoryHelper.getMimeType(name, child.type)
                        val cat = if (isDir) FileCategory.OTHER else MimeAndCategoryHelper.categorize(name, uriStr, mime)

                        batchBuffer.add(
                            IndexedFileEntity(
                                pathOrUri = uriStr,
                                name = name,
                                parentPathOrUri = parentUri,
                                isDirectory = isDir,
                                isSafUri = true,
                                size = if (isDir) 0L else child.length(),
                                lastModified = child.lastModified(),
                                createdAt = null,
                                extension = ext,
                                mimeType = mime,
                                category = cat.name,
                                isHidden = name.startsWith("."),
                                isProtected = isDir && !child.canRead(),
                                canRead = child.canRead(),
                                canWrite = child.canWrite()
                            )
                        )
                        if (batchBuffer.size >= 200) flushBatch()

                        if (isDir) {
                            foldersCount++
                            if (child.canRead()) scanSafTree(child, uriStr) else protectedCount++
                        } else {
                            filesCount++
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        errorsCount++
                    }
                }
            }

            for (root in roots) {
                coroutineCtx.ensureActive()
                if (root.isSafUri) {
                    val doc = DocumentFile.fromTreeUri(context, Uri.parse(root.pathOrUri))
                    if (doc != null && doc.canRead()) {
                        scanSafTree(doc, root.pathOrUri)
                    }
                } else {
                    scanFileTree(File(root.pathOrUri))
                }
            }
            flushBatch()

            settingsDataStore.setLastScanTime(System.currentTimeMillis())
            val finalState = ScanProgress(
                isScanning = false,
                currentFolder = "",
                filesScanned = filesCount,
                foldersScanned = foldersCount,
                errorCount = errorsCount,
                protectedCount = protectedCount,
                lastMessage = "Scanned $filesCount files & $foldersCount folders"
            )
            _scanProgress.value = finalState
            recordOperation("Scan", "Device Storage", finalState.lastMessage ?: "", true)
            Result.success(finalState)
        } catch (ce: CancellationException) {
            flushBatch()
            _scanProgress.value = _scanProgress.value.copy(
                isScanning = false,
                lastMessage = "Scan cancelled ($filesCount files indexed)"
            )
            Result.failure(ce)
        } catch (e: Exception) {
            flushBatch()
            _scanProgress.value = _scanProgress.value.copy(
                isScanning = false,
                lastMessage = e.message ?: "Scan failed"
            )
            Result.failure(e)
        }
    }

    suspend fun clearFileIndex() = withContext(Dispatchers.IO) {
        // Only clears the Room index cache; NEVER deletes user files!
        dao.clearFileIndex()
    }

    suspend fun registerSafRoot(uri: Uri) = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(context, uri)
        dao.upsertSafRoot(
            SafRootEntity(
                uriString = uri.toString(),
                displayName = doc?.name ?: "SAF Storage",
                addedAt = System.currentTimeMillis()
            )
        )
    }

    // -------------------------------------------------------------------------
    // Create Folder / Create File / Read & Write Text
    // -------------------------------------------------------------------------
    suspend fun createFolder(
        parentPathOrUri: String,
        isSafUri: Boolean,
        folderName: String
    ): Result<StorageItem> = withContext(Dispatchers.IO) {
        val cleanName = folderName.trim()
        val (valid, error) = MimeAndCategoryHelper.isValidFileOrFolderName(cleanName)
        if (!valid) return@withContext Result.failure(IllegalArgumentException(error))

        val result = runCatching {
            val item = if (isSafUri) {
                val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(parentPathOrUri))
                    ?: DocumentFile.fromSingleUri(context, Uri.parse(parentPathOrUri))
                    ?: throw IllegalStateException("Parent folder unavailable")
                if (parentDoc.findFile(cleanName) != null) {
                    throw IllegalStateException("Folder '$cleanName' already exists")
                }
                val created = parentDoc.createDirectory(cleanName)
                    ?: throw IllegalStateException("Failed to create folder '$cleanName'")
                StorageItem(
                    pathOrUri = created.uri.toString(),
                    name = created.name ?: cleanName,
                    parentPathOrUri = parentPathOrUri,
                    isDirectory = true,
                    isSafUri = true,
                    size = 0L,
                    lastModified = created.lastModified(),
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    isHidden = cleanName.startsWith(".")
                )
            } else {
                val parentDir = File(parentPathOrUri)
                if (!parentDir.exists() || !parentDir.canWrite()) {
                    throw SecurityException("Cannot write to folder ${parentDir.name}")
                }
                val targetDir = File(parentDir, cleanName)
                if (targetDir.exists()) {
                    throw IllegalStateException("Folder '$cleanName' already exists")
                }
                if (!targetDir.mkdir()) {
                    throw IllegalStateException("Failed to create folder '$cleanName'")
                }
                StorageItem(
                    pathOrUri = targetDir.absolutePath,
                    name = targetDir.name,
                    parentPathOrUri = parentDir.absolutePath,
                    isDirectory = true,
                    isSafUri = false,
                    size = 0L,
                    lastModified = targetDir.lastModified(),
                    createdAt = readCreatedTime(targetDir),
                    mimeType = "vnd.android.document/directory",
                    isHidden = cleanName.startsWith(".")
                )
            }
            dao.upsertFile(IndexedFileEntity.fromStorageItem(item))
            item
        }
        recordOperation("Create Folder", cleanName, result.exceptionOrNull()?.message ?: "Created in $parentPathOrUri", result.isSuccess)
        result
    }

    suspend fun createFile(
        parentPathOrUri: String,
        isSafUri: Boolean,
        fileName: String,
        initialContent: String = ""
    ): Result<StorageItem> = withContext(Dispatchers.IO) {
        val cleanName = fileName.trim()
        val (valid, error) = MimeAndCategoryHelper.isValidFileOrFolderName(cleanName)
        if (!valid) return@withContext Result.failure(IllegalArgumentException(error))

        val result = runCatching {
            val mime = MimeAndCategoryHelper.getMimeType(cleanName)
            val ext = MimeAndCategoryHelper.extractExtension(cleanName)
            val item = if (isSafUri) {
                val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(parentPathOrUri))
                    ?: DocumentFile.fromSingleUri(context, Uri.parse(parentPathOrUri))
                    ?: throw IllegalStateException("Parent folder unavailable")
                if (parentDoc.findFile(cleanName) != null) {
                    throw IllegalStateException("File '$cleanName' already exists")
                }
                val created = parentDoc.createFile(mime, cleanName)
                    ?: throw IllegalStateException("Failed to create file '$cleanName'")
                if (initialContent.isNotEmpty()) {
                    context.contentResolver.openOutputStream(created.uri, "wt")?.use {
                        it.write(initialContent.toByteArray(Charsets.UTF_8))
                    }
                }
                StorageItem(
                    pathOrUri = created.uri.toString(),
                    name = created.name ?: cleanName,
                    parentPathOrUri = parentPathOrUri,
                    isDirectory = false,
                    isSafUri = true,
                    size = created.length(),
                    lastModified = created.lastModified(),
                    extension = ext,
                    mimeType = mime,
                    category = MimeAndCategoryHelper.categorize(cleanName, created.uri.toString(), mime),
                    isHidden = cleanName.startsWith(".")
                )
            } else {
                val parentDir = File(parentPathOrUri)
                if (!parentDir.exists() || !parentDir.canWrite()) {
                    throw SecurityException("Cannot write to folder ${parentDir.name}")
                }
                val targetFile = File(parentDir, cleanName)
                if (targetFile.exists()) {
                    throw IllegalStateException("File '$cleanName' already exists")
                }
                if (!targetFile.createNewFile()) {
                    throw IllegalStateException("Failed to create file '$cleanName'")
                }
                if (initialContent.isNotEmpty()) {
                    targetFile.writeText(initialContent, Charsets.UTF_8)
                }
                StorageItem(
                    pathOrUri = targetFile.absolutePath,
                    name = targetFile.name,
                    parentPathOrUri = parentDir.absolutePath,
                    isDirectory = false,
                    isSafUri = false,
                    size = targetFile.length(),
                    lastModified = targetFile.lastModified(),
                    createdAt = readCreatedTime(targetFile),
                    extension = ext,
                    mimeType = mime,
                    category = MimeAndCategoryHelper.categorize(cleanName, targetFile.absolutePath, mime),
                    isHidden = cleanName.startsWith(".")
                )
            }
            dao.upsertFile(IndexedFileEntity.fromStorageItem(item))
            recordRecent(item, "Created")
            item
        }
        recordOperation("Create File", cleanName, result.exceptionOrNull()?.message ?: "Created", result.isSuccess)
        result
    }

    suspend fun readTextFile(pathOrUri: String, isSafUri: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isSafUri) {
                    context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: throw IllegalStateException("Cannot open file stream")
                } else {
                    val f = File(pathOrUri)
                    if (!f.exists() || !f.canRead()) throw SecurityException("Cannot read ${f.name}")
                    f.readText(Charsets.UTF_8)
                }
            }
        }

    suspend fun writeTextFile(
        pathOrUri: String,
        isSafUri: Boolean,
        content: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val res = runCatching {
            if (isSafUri) {
                try {
                    context.contentResolver.openOutputStream(Uri.parse(pathOrUri), "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("External file is read-only — use Menu (⋮) → Save As... to save to your device.")
                } catch (e: SecurityException) {
                    throw SecurityException("External file is read-only — use Menu (⋮) → Save As... to save a copy.", e)
                } catch (e: UnsupportedOperationException) {
                    throw IllegalStateException("External file is read-only — use Menu (⋮) → Save As... to save a copy.", e)
                }
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(pathOrUri))
                val existing = dao.getByPath(pathOrUri)
                if (existing != null) {
                    dao.upsertFile(
                        existing.copy(
                            size = doc?.length() ?: content.toByteArray().size.toLong(),
                            lastModified = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                val f = File(pathOrUri)
                if (f.parentFile?.canWrite() == false && !f.canWrite()) {
                    throw SecurityException("File is read-only: ${f.name}")
                }
                // Safe atomic-style write: write to temp file if possible or directly
                FileOutputStream(f, false).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                val item = StorageItem(
                    pathOrUri = f.absolutePath,
                    name = f.name,
                    parentPathOrUri = f.parent ?: "",
                    isDirectory = false,
                    isSafUri = false,
                    size = f.length(),
                    lastModified = f.lastModified(),
                    extension = MimeAndCategoryHelper.extractExtension(f.name),
                    mimeType = MimeAndCategoryHelper.getMimeType(f.name),
                    category = MimeAndCategoryHelper.categorize(f.name, f.absolutePath, null)
                )
                dao.upsertFile(IndexedFileEntity.fromStorageItem(item))
                recordRecent(item, "Edited")
            }
            dao.deleteDraft(pathOrUri)
        }
        recordOperation("Edit", fileName, res.exceptionOrNull()?.message ?: "Saved ${content.length} chars", res.isSuccess)
        res
    }

    suspend fun saveDraft(pathOrUri: String, fileName: String, content: String) = withContext(Dispatchers.IO) {
        dao.saveDraft(
            EditorDraftEntity(
                pathOrUri = pathOrUri,
                fileName = fileName,
                content = content,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getDraft(pathOrUri: String): EditorDraftEntity? = withContext(Dispatchers.IO) {
        dao.getDraft(pathOrUri)
    }

    suspend fun deleteDraft(pathOrUri: String) = withContext(Dispatchers.IO) {
        dao.deleteDraft(pathOrUri)
    }

    // -------------------------------------------------------------------------
    // Rename / Batch Rename / Duplicate
    // -------------------------------------------------------------------------
    suspend fun renameItem(item: StorageItem, newName: String): Result<StorageItem> =
        withContext(Dispatchers.IO) {
            val clean = newName.trim()
            val (valid, err) = MimeAndCategoryHelper.isValidFileOrFolderName(clean)
            if (!valid) return@withContext Result.failure(IllegalArgumentException(err))

            val res = runCatching {
                val updated = if (item.isSafUri) {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(item.pathOrUri))
                        ?: DocumentFile.fromTreeUri(context, Uri.parse(item.pathOrUri))
                        ?: throw IllegalStateException("Item unavailable")
                    if (!doc.renameTo(clean)) {
                        throw IllegalStateException("Rename failed for ${item.name}")
                    }
                    val newUri = doc.uri.toString()
                    val ext = if (item.isDirectory) "" else MimeAndCategoryHelper.extractExtension(clean)
                    val mime = if (item.isDirectory) item.mimeType else MimeAndCategoryHelper.getMimeType(clean)
                    item.copy(
                        pathOrUri = newUri,
                        name = doc.name ?: clean,
                        extension = ext,
                        mimeType = mime,
                        category = if (item.isDirectory) FileCategory.OTHER else MimeAndCategoryHelper.categorize(clean, newUri, mime),
                        lastModified = System.currentTimeMillis()
                    )
                } else {
                    val oldFile = File(item.pathOrUri)
                    val parent = oldFile.parentFile ?: throw IllegalStateException("Invalid parent")
                    val target = File(parent, clean)
                    if (target.exists() && target.absolutePath != oldFile.absolutePath) {
                        throw IllegalStateException("'$clean' already exists in this folder")
                    }
                    if (!oldFile.renameTo(target)) {
                        throw IllegalStateException("Could not rename '${item.name}'")
                    }
                    val ext = if (target.isDirectory) "" else MimeAndCategoryHelper.extractExtension(target.name)
                    val mime = if (target.isDirectory) item.mimeType else MimeAndCategoryHelper.getMimeType(target.name)
                    item.copy(
                        pathOrUri = target.absolutePath,
                        name = target.name,
                        extension = ext,
                        mimeType = mime,
                        category = if (target.isDirectory) FileCategory.OTHER else MimeAndCategoryHelper.categorize(target.name, target.absolutePath, mime),
                        lastModified = target.lastModified()
                    )
                }

                dao.deleteByPathOrPrefix(item.pathOrUri, item.pathOrUri.trimEnd('/') + "/%")
                dao.upsertFile(IndexedFileEntity.fromStorageItem(updated))
                if (item.isFavorite) {
                    dao.removeFavorite(item.pathOrUri)
                    addFavorite(updated)
                }
                updated
            }
            recordOperation("Rename", "${item.name} → $clean", res.exceptionOrNull()?.message ?: "Renamed", res.isSuccess)
            res
        }

    suspend fun batchRename(
        items: List<StorageItem>,
        config: BatchRenameConfig
    ): Result<Int> = withContext(Dispatchers.IO) {
        val previews = FormatUtils.previewBatchRename(items, config)
        var count = 0
        for ((item, newName) in previews) {
            if (newName == item.name) continue
            val r = renameItem(item, newName)
            if (r.isSuccess) count++ else return@withContext Result.failure(r.exceptionOrNull() ?: Exception("Batch rename failed on ${item.name}"))
        }
        Result.success(count)
    }

    suspend fun duplicateItem(item: StorageItem): Result<StorageItem> = withContext(Dispatchers.IO) {
        runCatching {
            val existingNames = listDirectory(item.parentPathOrUri, item.isSafUri, includeHidden = true)
                .getOrDefault(emptyList())
                .map { it.name }
                .toSet()
            val targetName = FormatUtils.computeKeepBothName(item.name, existingNames)
            copySingleItemToFolder(
                source = item,
                destParentPathOrUri = item.parentPathOrUri,
                destIsSaf = item.isSafUri,
                targetName = targetName
            )
        }
    }

    // -------------------------------------------------------------------------
    // Copy & Move with Conflict Handling + Recursive Folder Tree Support
    // -------------------------------------------------------------------------
    suspend fun copyOrMoveItems(
        items: List<StorageItem>,
        destFolderPathOrUri: String,
        destIsSaf: Boolean,
        isMove: Boolean,
        onConflictPrompt: suspend (ConflictPrompt) -> Pair<ConflictAction, Boolean>,
        shouldCancel: () -> Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        var applyAllAction: ConflictAction? = null
        val total = items.size

        _operationProgress.value = OperationProgress(
            isRunning = true,
            operationName = if (isMove) "Moving files" else "Copying files",
            currentFile = "",
            completedFiles = 0,
            totalFiles = total
        )

        try {
            for ((index, item) in items.withIndex()) {
                if (shouldCancel()) break
                if (item.parentPathOrUri == destFolderPathOrUri && isMove) {
                    successCount++
                    continue
                }
                // Prevent copying/moving folder into itself
                if (item.isDirectory && !item.isSafUri && !destIsSaf &&
                    (destFolderPathOrUri == item.pathOrUri ||
                        destFolderPathOrUri.startsWith(item.pathOrUri.trimEnd('/') + "/"))
                ) {
                    return@withContext Result.failure(IllegalArgumentException("Cannot place '${item.name}' inside itself"))
                }

                _operationProgress.value = _operationProgress.value.copy(
                    currentFile = item.name,
                    completedFiles = index
                )

                val existingChildren = listDirectory(destFolderPathOrUri, destIsSaf, includeHidden = true)
                    .getOrDefault(emptyList())
                val existingNames = existingChildren.map { it.name }.toSet()

                var resolvedTargetName = item.name
                var shouldReplace = false

                if (item.name in existingNames) {
                    val chosenAction = applyAllAction ?: run {
                        val (action, applyAll) = onConflictPrompt(
                            ConflictPrompt(
                                fileName = item.name,
                                sourcePathOrUri = item.pathOrUri,
                                destinationFolder = destFolderPathOrUri,
                                remainingCount = total - index - 1
                            )
                        )
                        if (applyAll && action != ConflictAction.CANCEL) {
                            applyAllAction = action
                        }
                        action
                    }

                    when (chosenAction) {
                        ConflictAction.CANCEL -> break
                        ConflictAction.SKIP -> continue
                        ConflictAction.KEEP_BOTH -> {
                            resolvedTargetName = FormatUtils.computeKeepBothName(item.name, existingNames)
                        }
                        ConflictAction.REPLACE -> {
                            shouldReplace = true
                        }
                    }
                }

                if (shouldReplace) {
                    existingChildren.firstOrNull { it.name == resolvedTargetName }?.let { conflictItem ->
                        permanentlyDeleteSingle(conflictItem)
                    }
                }

                // Fast native rename if moving on same File filesystem
                if (isMove && !item.isSafUri && !destIsSaf) {
                    val srcFile = File(item.pathOrUri)
                    val dstFile = File(destFolderPathOrUri, resolvedTargetName)
                    if (srcFile.renameTo(dstFile)) {
                        dao.deleteByPathOrPrefix(item.pathOrUri, item.pathOrUri.trimEnd('/') + "/%")
                        val moved = item.copy(
                            pathOrUri = dstFile.absolutePath,
                            name = dstFile.name,
                            parentPathOrUri = destFolderPathOrUri,
                            lastModified = dstFile.lastModified()
                        )
                        dao.upsertFile(IndexedFileEntity.fromStorageItem(moved))
                        successCount++
                        recordOperation("Move", item.name, "Moved to $destFolderPathOrUri", true)
                        continue
                    }
                }

                // Recursive copy (used for Copy, cross-volume Move, or SAF Move)
                copySingleItemToFolder(item, destFolderPathOrUri, destIsSaf, resolvedTargetName, shouldCancel)
                if (isMove) {
                    permanentlyDeleteSingle(item)
                    recordOperation("Move", item.name, "Moved to $destFolderPathOrUri", true)
                } else {
                    recordOperation("Copy", item.name, "Copied to $destFolderPathOrUri", true)
                }
                successCount++
            }
            Result.success(successCount)
        } catch (e: Exception) {
            recordOperation(if (isMove) "Move" else "Copy", items.firstOrNull()?.name ?: "", e.message ?: "Failed", false)
            Result.failure(e)
        } finally {
            _operationProgress.value = OperationProgress(isRunning = false)
        }
    }

    private suspend fun copySingleItemToFolder(
        source: StorageItem,
        destParentPathOrUri: String,
        destIsSaf: Boolean,
        targetName: String = source.name,
        shouldCancel: () -> Boolean = { false }
    ): StorageItem {
        if (shouldCancel()) throw CancellationException("Operation cancelled")

        if (source.isDirectory) {
            val createdFolder = createFolder(destParentPathOrUri, destIsSaf, targetName).getOrThrow()
            val children = listDirectory(source.pathOrUri, source.isSafUri, includeHidden = true).getOrDefault(emptyList())
            for (child in children) {
                if (shouldCancel()) throw CancellationException("Operation cancelled")
                if (!child.isProtected && child.canRead) {
                    copySingleItemToFolder(child, createdFolder.pathOrUri, createdFolder.isSafUri, child.name, shouldCancel)
                }
            }
            return createdFolder
        } else {
            val destItem = createFile(destParentPathOrUri, destIsSaf, targetName).getOrThrow()
            openInputStream(source).use { input ->
                openOutputStream(destItem).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (shouldCancel()) throw CancellationException("Operation cancelled")
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            val updatedSize = if (destItem.isSafUri) {
                DocumentFile.fromSingleUri(context, Uri.parse(destItem.pathOrUri))?.length() ?: source.size
            } else {
                File(destItem.pathOrUri).length()
            }
            val finalized = destItem.copy(size = updatedSize, lastModified = System.currentTimeMillis())
            dao.upsertFile(IndexedFileEntity.fromStorageItem(finalized))
            return finalized
        }
    }

    private fun openInputStream(item: StorageItem): InputStream {
        return if (item.isSafUri) {
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
                ?: throw IllegalStateException("Cannot read SAF file ${item.name}")
        } else {
            FileInputStream(File(item.pathOrUri))
        }
    }

    private fun openOutputStream(item: StorageItem): OutputStream {
        return if (item.isSafUri) {
            context.contentResolver.openOutputStream(Uri.parse(item.pathOrUri), "wt")
                ?: throw IllegalStateException("Cannot write SAF file ${item.name}")
        } else {
            FileOutputStream(File(item.pathOrUri), false)
        }
    }

    // -------------------------------------------------------------------------
    // Delete, Recycle Bin, and Restore
    // -------------------------------------------------------------------------
    fun canMoveToRecycleBin(item: StorageItem, recycleBinEnabled: Boolean): Boolean {
        // File-backed items can be moved safely to our private trash vault.
        // SAF items cannot be moved atomically across providers without copy/delete overhead, so warn before permanent delete.
        return recycleBinEnabled && !item.isSafUri
    }

    suspend fun deleteItems(
        items: List<StorageItem>,
        useRecycleBin: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        var deletedCount = 0
        _operationProgress.value = OperationProgress(
            isRunning = true,
            operationName = if (useRecycleBin) "Moving to Recycle Bin" else "Deleting permanently",
            totalFiles = items.size
        )
        try {
            for ((index, item) in items.withIndex()) {
                _operationProgress.value = _operationProgress.value.copy(
                    currentFile = item.name,
                    completedFiles = index
                )
                if (useRecycleBin && !item.isSafUri) {
                    val srcFile = File(item.pathOrUri)
                    if (!srcFile.exists()) continue
                    val id = UUID.randomUUID().toString()
                    val vaultFolder = File(trashRootDir, id).apply { mkdirs() }
                    val trashTarget = File(vaultFolder, item.name)
                    val moved = srcFile.renameTo(trashTarget) || run {
                        if (srcFile.isDirectory) srcFile.copyRecursively(trashTarget, overwrite = true) && srcFile.deleteRecursively()
                        else {
                            srcFile.copyTo(trashTarget, overwrite = true)
                            srcFile.delete()
                        }
                    }
                    if (!moved) {
                        throw IllegalStateException("Could not move '${item.name}' to Recycle Bin")
                    }
                    val actualSize = if (trashTarget.isDirectory) computeFolderSize(trashTarget) else trashTarget.length()
                    dao.insertRecycleItem(
                        RecycleBinEntity(
                            id = id,
                            name = item.name,
                            originalPathOrUri = item.pathOrUri,
                            originalParentPath = item.parentPathOrUri,
                            trashStoragePath = trashTarget.absolutePath,
                            isDirectory = item.isDirectory,
                            size = actualSize,
                            mimeType = item.mimeType,
                            extension = item.extension,
                            deletedAt = System.currentTimeMillis()
                        )
                    )
                    dao.deleteByPathOrPrefix(item.pathOrUri, item.pathOrUri.trimEnd('/') + "/%")
                    dao.removeFavorite(item.pathOrUri)
                    dao.removeRecentFile(item.pathOrUri)
                    deletedCount++
                    recordOperation("Move to Trash", item.name, "Moved to Recycle Bin", true)
                } else {
                    permanentlyDeleteSingle(item)
                    deletedCount++
                    recordOperation("Delete", item.name, "Deleted permanently", true)
                }
            }
            Result.success(deletedCount)
        } catch (e: Exception) {
            recordOperation("Delete", items.firstOrNull()?.name ?: "", e.message ?: "Failed", false)
            Result.failure(e)
        } finally {
            _operationProgress.value = OperationProgress(isRunning = false)
        }
    }

    private suspend fun permanentlyDeleteSingle(item: StorageItem) {
        val ok = if (item.isSafUri) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(item.pathOrUri))
                ?: DocumentFile.fromTreeUri(context, Uri.parse(item.pathOrUri))
            doc?.delete() == true
        } else {
            val f = File(item.pathOrUri)
            if (!f.exists()) true else if (f.isDirectory) f.deleteRecursively() else f.delete()
        }
        if (!ok) throw IllegalStateException("Failed to delete '${item.name}'")
        dao.deleteByPathOrPrefix(item.pathOrUri, item.pathOrUri.trimEnd('/') + "/%")
        dao.removeFavorite(item.pathOrUri)
        dao.removeRecentFile(item.pathOrUri)
    }

    suspend fun restoreFromRecycleBin(
        entry: RecycleBinEntity,
        fallbackParentDir: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val res = runCatching {
            val trashFile = File(entry.trashStoragePath)
            if (!trashFile.exists()) throw IllegalStateException("Trash payload missing for ${entry.name}")

            val parentDir = File(entry.originalParentPath).takeIf { it.exists() && it.canWrite() }
                ?: fallbackParentDir?.takeIf { it.exists() && it.canWrite() }
                ?: throw IllegalStateException("Original folder no longer accessible; please pick a restore destination")

            val existing = parentDir.list()?.toSet() ?: emptySet()
            val targetName = if (entry.name in existing) FormatUtils.computeKeepBothName(entry.name, existing) else entry.name
            val destFile = File(parentDir, targetName)

            val restored = trashFile.renameTo(destFile) || run {
                if (trashFile.isDirectory) trashFile.copyRecursively(destFile, overwrite = true) && trashFile.deleteRecursively()
                else {
                    trashFile.copyTo(destFile, overwrite = true)
                    trashFile.delete()
                }
            }
            if (!restored) throw IllegalStateException("Failed to restore ${entry.name}")

            trashFile.parentFile?.deleteRecursively()
            dao.deleteRecycleItem(entry.id)

            val restoredItem = StorageItem(
                pathOrUri = destFile.absolutePath,
                name = destFile.name,
                parentPathOrUri = parentDir.absolutePath,
                isDirectory = destFile.isDirectory,
                isSafUri = false,
                size = if (destFile.isDirectory) 0L else destFile.length(),
                lastModified = destFile.lastModified(),
                extension = if (destFile.isDirectory) "" else MimeAndCategoryHelper.extractExtension(destFile.name),
                mimeType = if (destFile.isDirectory) "vnd.android.document/directory" else MimeAndCategoryHelper.getMimeType(destFile.name),
                category = if (destFile.isDirectory) FileCategory.OTHER else MimeAndCategoryHelper.categorize(destFile.name, destFile.absolutePath, null)
            )
            dao.upsertFile(IndexedFileEntity.fromStorageItem(restoredItem))
            destFile
        }
        recordOperation("Restore", entry.name, res.exceptionOrNull()?.message ?: "Restored", res.isSuccess)
        res
    }

    suspend fun permanentlyDeleteFromRecycleBin(entry: RecycleBinEntity) = withContext(Dispatchers.IO) {
        val trashFile = File(entry.trashStoragePath)
        trashFile.parentFile?.deleteRecursively()
        dao.deleteRecycleItem(entry.id)
        recordOperation("Delete", entry.name, "Permanently removed from Recycle Bin", true)
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        trashRootDir.deleteRecursively()
        trashRootDir.mkdirs()
        dao.clearRecycleBinTable()
        recordOperation("Empty Trash", "Recycle Bin", "All items permanently deleted", true)
    }

    suspend fun cleanupExpiredRecycleBin(days: Int) = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        dao.getExpiredRecycleItems(cutoff).forEach { permanentlyDeleteFromRecycleBin(it) }
    }

    // -------------------------------------------------------------------------
    // Favorites, Recents, Properties, and Operation History
    // -------------------------------------------------------------------------
    suspend fun toggleFavorite(item: StorageItem): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = dao.isFavorite(item.pathOrUri)
        if (currentlyFav) {
            dao.removeFavorite(item.pathOrUri)
            false
        } else {
            addFavorite(item)
            true
        }
    }

    private suspend fun addFavorite(item: StorageItem) {
        dao.addFavorite(
            FavoriteEntity(
                pathOrUri = item.pathOrUri,
                name = item.name,
                parentPathOrUri = item.parentPathOrUri,
                isDirectory = item.isDirectory,
                isSafUri = item.isSafUri,
                size = item.size,
                lastModified = item.lastModified,
                extension = item.extension,
                mimeType = item.mimeType,
                category = item.category.name,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFavoriteByKey(pathOrUri: String) = withContext(Dispatchers.IO) {
        dao.removeFavorite(pathOrUri)
    }

    suspend fun itemStillExists(pathOrUri: String, isSafUri: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (isSafUri) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(pathOrUri))
                ?: DocumentFile.fromTreeUri(context, Uri.parse(pathOrUri))
            doc?.exists() == true
        } else {
            File(pathOrUri).exists()
        }
    }

    suspend fun recordRecent(item: StorageItem, actionLabel: String = "Opened") = withContext(Dispatchers.IO) {
        if (item.isDirectory) return@withContext
        dao.upsertRecentFile(
            RecentFileEntity(
                pathOrUri = item.pathOrUri,
                name = item.name,
                parentPathOrUri = item.parentPathOrUri,
                isDirectory = false,
                isSafUri = item.isSafUri,
                size = item.size,
                lastModified = item.lastModified,
                extension = item.extension,
                mimeType = item.mimeType,
                category = item.category.name,
                actionLabel = actionLabel,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeRecent(pathOrUri: String) = withContext(Dispatchers.IO) {
        // Removes only from Recent table, never deletes the real file!
        dao.removeRecentFile(pathOrUri)
    }

    suspend fun recordOperation(type: String, targetName: String, details: String, success: Boolean) {
        dao.insertOperationHistory(
            OperationHistoryEntity(
                operationType = type,
                targetName = targetName,
                details = details,
                timestamp = System.currentTimeMillis(),
                success = success
            )
        )
    }

    suspend fun clearOperationHistory() = withContext(Dispatchers.IO) {
        dao.clearOperationHistory()
    }

    suspend fun computeFolderMetrics(item: StorageItem): Triple<Int, Int, Long> = withContext(Dispatchers.IO) {
        var files = 0
        var folders = 0
        var totalSize = 0L

        fun walkFile(dir: File) {
            if (storageAccessHelper.isAndroidProtectedDirectory(dir)) return
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    folders++
                    walkFile(child)
                } else {
                    files++
                    totalSize += child.length()
                }
            }
        }

        fun walkSaf(doc: DocumentFile) {
            if (!doc.canRead()) return
            doc.listFiles().forEach { child ->
                if (child.isDirectory) {
                    folders++
                    walkSaf(child)
                } else {
                    files++
                    totalSize += child.length()
                }
            }
        }

        if (item.isSafUri) {
            DocumentFile.fromTreeUri(context, Uri.parse(item.pathOrUri))?.let { walkSaf(it) }
        } else {
            walkFile(File(item.pathOrUri))
        }
        Triple(files, folders, totalSize)
    }

    private fun computeFolderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { child ->
            size += if (child.isDirectory) computeFolderSize(child) else child.length()
        }
        return size
    }
}
