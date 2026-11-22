package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryAggregation(
    val category: String,
    val totalSize: Long,
    val itemCount: Int
)

@Dao
interface FileIndexDao {

    // Indexed files
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(items: List<IndexedFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(item: IndexedFileEntity)

    @Query("DELETE FROM indexed_files")
    suspend fun clearFileIndex()

    @Query("DELETE FROM indexed_files WHERE pathOrUri = :pathOrUri OR pathOrUri LIKE :prefix")
    suspend fun deleteByPathOrPrefix(pathOrUri: String, prefix: String)

    @Query("SELECT COUNT(*) FROM indexed_files WHERE isDirectory = 0")
    fun observeIndexedFileCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM indexed_files WHERE isDirectory = 1")
    fun observeIndexedFolderCount(): Flow<Int>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0 AND category = :category AND (:includeHidden = 1 OR isHidden = 0) ORDER BY lastModified DESC")
    fun observeFilesByCategory(category: String, includeHidden: Boolean): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0 AND (LOWER(parentPathOrUri) LIKE '%/download%' OR LOWER(parentPathOrUri) LIKE '%/downloads%') AND (:includeHidden = 1 OR isHidden = 0) ORDER BY lastModified DESC")
    fun observeDownloadFolderFiles(includeHidden: Boolean): Flow<List<IndexedFileEntity>>

    @Query("SELECT category, COALESCE(SUM(size), 0) AS totalSize, COUNT(*) AS itemCount FROM indexed_files WHERE isDirectory = 0 GROUP BY category")
    fun observeCategoryAggregations(): Flow<List<CategoryAggregation>>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0 AND size >= :minSizeBytes ORDER BY size DESC LIMIT 200")
    fun observeLargeFiles(minSizeBytes: Long): Flow<List<IndexedFileEntity>>

    @Query(
        "SELECT * FROM indexed_files " +
            "WHERE (:includeHidden = 1 OR isHidden = 0) " +
            "AND (:query = '' OR LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(extension) LIKE '%' || LOWER(:query) || '%') " +
            "AND (:category IS NULL OR category = :category) " +
            "ORDER BY isDirectory DESC, name ASC LIMIT 300"
    )
    suspend fun searchIndexedFiles(
        query: String,
        category: String?,
        includeHidden: Boolean
    ): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE pathOrUri = :pathOrUri LIMIT 1")
    suspend fun getByPath(pathOrUri: String): IndexedFileEntity?

    // Recent files
    @Query("SELECT * FROM recent_files ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentFile(entry: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE pathOrUri = :pathOrUri")
    suspend fun removeRecentFile(pathOrUri: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentFiles()

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT pathOrUri FROM favorites")
    fun observeFavoriteKeys(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE pathOrUri = :pathOrUri)")
    suspend fun isFavorite(pathOrUri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE pathOrUri = :pathOrUri")
    suspend fun removeFavorite(pathOrUri: String)

    // Recycle bin
    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun observeRecycleBin(): Flow<List<RecycleBinEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycleItem(item: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteRecycleItem(id: String)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearRecycleBinTable()

    @Query("SELECT * FROM recycle_bin WHERE deletedAt < :cutoffTimestamp")
    suspend fun getExpiredRecycleItems(cutoffTimestamp: Long): List<RecycleBinEntity>

    // Operation history
    @Query("SELECT * FROM operation_history ORDER BY timestamp DESC LIMIT 100")
    fun observeOperationHistory(): Flow<List<OperationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperationHistory(entry: OperationHistoryEntity)

    @Query("DELETE FROM operation_history")
    suspend fun clearOperationHistory()

    // Editor drafts (safe auto-save & crash recovery)
    @Query("SELECT * FROM editor_drafts WHERE pathOrUri = :pathOrUri LIMIT 1")
    suspend fun getDraft(pathOrUri: String): EditorDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: EditorDraftEntity)

    @Query("DELETE FROM editor_drafts WHERE pathOrUri = :pathOrUri")
    suspend fun deleteDraft(pathOrUri: String)

    // Cloud / FTP connections
    @Query("SELECT * FROM cloud_connections ORDER BY createdAt DESC")
    fun observeCloudConnections(): Flow<List<CloudConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudConnection(connection: CloudConnectionEntity): Long

    @Query("DELETE FROM cloud_connections WHERE id = :id")
    suspend fun deleteCloudConnection(id: Long)

    // Persisted SAF roots metadata
    @Query("SELECT * FROM saf_roots ORDER BY addedAt DESC")
    fun observeSafRoots(): Flow<List<SafRootEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSafRoot(root: SafRootEntity)

    @Query("DELETE FROM saf_roots WHERE uriString = :uriString")
    suspend fun deleteSafRoot(uriString: String)
}
