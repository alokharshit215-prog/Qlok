package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BlueFileManagerApp
import com.example.data.db.CloudConnectionEntity
import com.example.model.RemoteFtpEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class CloudFtpViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BlueFileManagerApp
    private val dao = app.database.fileIndexDao()
    private val ftpService = app.ftpNetworkService

    val savedConnections: StateFlow<List<CloudConnectionEntity>> = dao.observeCloudConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConnection = MutableStateFlow<CloudConnectionEntity?>(null)
    val activeConnection: StateFlow<CloudConnectionEntity?> = _activeConnection.asStateFlow()

    private val _currentRemotePath = MutableStateFlow("/")
    val currentRemotePath: StateFlow<String> = _currentRemotePath.asStateFlow()

    private val _remoteEntries = MutableStateFlow<List<RemoteFtpEntry>>(emptyList())
    val remoteEntries: StateFlow<List<RemoteFtpEntry>> = _remoteEntries.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun addFtpConnection(
        label: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        passiveMode: Boolean
    ) {
        viewModelScope.launch {
            val entity = CloudConnectionEntity(
                serviceType = "FTP",
                label = label.ifBlank { host },
                host = host.trim(),
                port = port,
                username = username.trim(),
                password = password,
                passiveMode = passiveMode,
                createdAt = System.currentTimeMillis()
            )
            dao.insertCloudConnection(entity)
            _events.tryEmit("Saved FTP connection '${entity.label}'")
        }
    }

    fun deleteConnection(id: Long) {
        viewModelScope.launch {
            if (_activeConnection.value?.id == id) {
                disconnect()
            }
            dao.deleteCloudConnection(id)
            _events.tryEmit("Connection removed")
        }
    }

    fun connectAndBrowse(connection: CloudConnectionEntity, path: String = "/") {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            val res = ftpService.listDirectory(connection, path)
            _isBusy.value = false
            res.onSuccess { entries ->
                _activeConnection.value = connection
                _currentRemotePath.value = path
                _remoteEntries.value = entries
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "FTP connection failed"
            }
        }
    }

    fun disconnect() {
        _activeConnection.value = null
        _currentRemotePath.value = "/"
        _remoteEntries.value = emptyList()
        _errorMessage.value = null
    }

    fun navigateUp() {
        val conn = _activeConnection.value ?: return
        val cur = _currentRemotePath.value.trimEnd('/')
        if (cur.isEmpty() || cur == "/") return
        val parent = cur.substringBeforeLast('/', "/")
        connectAndBrowse(conn, parent.ifEmpty { "/" })
    }

    fun downloadRemoteFile(entry: RemoteFtpEntry, destinationFolder: File) {
        val conn = _activeConnection.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val destFile = File(destinationFolder, entry.name)
            val res = ftpService.downloadFile(conn, entry.fullPath, destFile)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Downloaded '${entry.name}' to ${destinationFolder.name}")
            }.onFailure {
                _events.tryEmit("Download failed: ${it.message}")
            }
        }
    }

    fun uploadLocalFile(localFile: File) {
        val conn = _activeConnection.value ?: return
        val remoteDir = _currentRemotePath.value
        viewModelScope.launch {
            _isBusy.value = true
            val res = ftpService.uploadFile(conn, localFile, remoteDir)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Uploaded '${localFile.name}' to $remoteDir")
                connectAndBrowse(conn, remoteDir)
            }.onFailure {
                _events.tryEmit("Upload failed: ${it.message}")
            }
        }
    }

    fun createRemoteFolder(folderName: String) {
        val conn = _activeConnection.value ?: return
        val remoteDir = _currentRemotePath.value
        viewModelScope.launch {
            _isBusy.value = true
            val res = ftpService.createDirectory(conn, remoteDir, folderName)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Created remote folder '$folderName'")
                connectAndBrowse(conn, remoteDir)
            }.onFailure {
                _events.tryEmit("Failed: ${it.message}")
            }
        }
    }

    fun deleteRemoteEntry(entry: RemoteFtpEntry) {
        val conn = _activeConnection.value ?: return
        val remoteDir = _currentRemotePath.value
        viewModelScope.launch {
            _isBusy.value = true
            val res = ftpService.deleteRemoteItem(conn, entry.fullPath, entry.isDirectory)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Deleted '${entry.name}'")
                connectAndBrowse(conn, remoteDir)
            }.onFailure {
                _events.tryEmit("Delete failed: ${it.message}")
            }
        }
    }
}
