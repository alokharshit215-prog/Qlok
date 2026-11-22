package com.example

import android.app.Application
import com.example.data.db.BlueFileManagerDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.storage.FtpNetworkService
import com.example.data.storage.RealFileOperationsRepository
import com.example.data.storage.StorageAccessHelper
import com.example.data.storage.ZipArchiveService

class BlueFileManagerApp : Application() {

    val database: BlueFileManagerDatabase by lazy {
        BlueFileManagerDatabase.getInstance(this)
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    val storageAccessHelper: StorageAccessHelper by lazy {
        StorageAccessHelper(this)
    }

    val repository: RealFileOperationsRepository by lazy {
        RealFileOperationsRepository(
            context = this,
            dao = database.fileIndexDao(),
            storageAccessHelper = storageAccessHelper,
            settingsDataStore = settingsDataStore
        )
    }

    val zipArchiveService: ZipArchiveService by lazy {
        ZipArchiveService(this)
    }

    val ftpNetworkService: FtpNetworkService by lazy {
        FtpNetworkService()
    }
}
