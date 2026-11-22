package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        IndexedFileEntity::class,
        RecentFileEntity::class,
        FavoriteEntity::class,
        RecycleBinEntity::class,
        OperationHistoryEntity::class,
        EditorDraftEntity::class,
        CloudConnectionEntity::class,
        SafRootEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BlueFileManagerDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao

    companion object {
        @Volatile
        private var INSTANCE: BlueFileManagerDatabase? = null

        fun getInstance(context: Context): BlueFileManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlueFileManagerDatabase::class.java,
                    "blue_file_manager.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
