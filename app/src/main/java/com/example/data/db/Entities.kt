package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.FileCategory
import com.example.model.StorageItem

@Entity(
    tableName = "indexed_files",
    indices = [
        Index(value = ["parentPathOrUri"]),
        Index(value = ["category"]),
        Index(value = ["name"]),
        Index(value = ["size"]),
        Index(value = ["isDirectory"])
    ]
)
data class IndexedFileEntity(
    @PrimaryKey val pathOrUri: String,
    val name: String,
    val parentPathOrUri: String,
    val isDirectory: Boolean,
    val isSafUri: Boolean,
    val size: Long,
    val lastModified: Long,
    val createdAt: Long?,
    val extension: String,
    val mimeType: String,
    val category: String,
    val isHidden: Boolean,
    val isProtected: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean,
    val lastOpenedAt: Long? = null
) {
    fun toStorageItem(isFavorite: Boolean = false): StorageItem {
        val parsedCategory = runCatching { FileCategory.valueOf(category) }.getOrDefault(FileCategory.OTHER)
        return StorageItem(
            pathOrUri = pathOrUri,
            name = name,
            parentPathOrUri = parentPathOrUri,
            isDirectory = isDirectory,
            isSafUri = isSafUri,
            size = size,
            lastModified = lastModified,
            createdAt = createdAt,
            extension = extension,
            mimeType = mimeType,
            category = parsedCategory,
            isHidden = isHidden,
            isProtected = isProtected,
            canRead = canRead,
            canWrite = canWrite,
            isFavorite = isFavorite
        )
    }

    companion object {
        fun fromStorageItem(item: StorageItem, lastOpenedAt: Long? = null): IndexedFileEntity =
            IndexedFileEntity(
                pathOrUri = item.pathOrUri,
                name = item.name,
                parentPathOrUri = item.parentPathOrUri,
                isDirectory = item.isDirectory,
                isSafUri = item.isSafUri,
                size = item.size,
                lastModified = item.lastModified,
                createdAt = item.createdAt,
                extension = item.extension,
                mimeType = item.mimeType,
                category = item.category.name,
                isHidden = item.isHidden,
                isProtected = item.isProtected,
                canRead = item.canRead,
                canWrite = item.canWrite,
                lastOpenedAt = lastOpenedAt
            )
    }
}

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val pathOrUri: String,
    val name: String,
    val parentPathOrUri: String,
    val isDirectory: Boolean,
    val isSafUri: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val category: String,
    val actionLabel: String, // Opened, Created, Edited, Modified
    val timestamp: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val pathOrUri: String,
    val name: String,
    val parentPathOrUri: String,
    val isDirectory: Boolean,
    val isSafUri: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val category: String,
    val addedAt: Long
)

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey val id: String,
    val name: String,
    val originalPathOrUri: String,
    val originalParentPath: String,
    val trashStoragePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String,
    val extension: String,
    val deletedAt: Long
)

@Entity(tableName = "operation_history")
data class OperationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,
    val targetName: String,
    val details: String,
    val timestamp: Long,
    val success: Boolean
)

@Entity(tableName = "editor_drafts")
data class EditorDraftEntity(
    @PrimaryKey val pathOrUri: String,
    val fileName: String,
    val content: String,
    val updatedAt: Long
)

@Entity(tableName = "cloud_connections")
data class CloudConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceType: String,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val passiveMode: Boolean = true,
    val createdAt: Long
)

@Entity(tableName = "saf_roots")
data class SafRootEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val addedAt: Long
)
