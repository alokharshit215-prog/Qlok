package com.example.data.storage

import com.example.data.db.CloudConnectionEntity
import com.example.model.RemoteFtpEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FtpNetworkService {

    private suspend fun <T> withFtpConnection(
        connection: CloudConnectionEntity,
        block: (FTPClient) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connectTimeout = 10_000
            client.defaultTimeout = 15_000
            client.connect(connection.host.trim(), connection.port)
            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                return@withContext Result.failure(IllegalStateException("FTP server refused connection (code $reply)"))
            }
            val user = connection.username.ifBlank { "anonymous" }
            val pass = connection.password
            if (!client.login(user, pass)) {
                return@withContext Result.failure(IllegalStateException("FTP login failed for user '$user'"))
            }
            if (connection.passiveMode) {
                client.enterLocalPassiveMode()
            } else {
                client.enterLocalActiveMode()
            }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            Result.success(block(client))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            }
        }
    }

    suspend fun testConnection(connection: CloudConnectionEntity): Result<String> =
        withFtpConnection(connection) { client ->
            val pwd = client.printWorkingDirectory() ?: "/"
            "Connected ($pwd)"
        }

    suspend fun listDirectory(
        connection: CloudConnectionEntity,
        remotePath: String
    ): Result<List<RemoteFtpEntry>> = withFtpConnection(connection) { client ->
        val normalized = if (remotePath.isBlank()) "/" else remotePath
        if (!client.changeWorkingDirectory(normalized)) {
            throw IllegalStateException("Cannot open remote folder: $normalized")
        }
        val files = client.listFiles()
        files.filter { it.name != "." && it.name != ".." }
            .map { ftpFile ->
                val prefix = normalized.trimEnd('/')
                val full = "$prefix/${ftpFile.name}"
                RemoteFtpEntry(
                    name = ftpFile.name,
                    fullPath = full,
                    isDirectory = ftpFile.isDirectory,
                    size = ftpFile.size.coerceAtLeast(0L),
                    timestamp = ftpFile.timestamp?.timeInMillis ?: 0L
                )
            }
            .sortedWith(compareByDescending<RemoteFtpEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun downloadFile(
        connection: CloudConnectionEntity,
        remoteFilePath: String,
        localDestinationFile: File
    ): Result<File> = withFtpConnection(connection) { client ->
        localDestinationFile.parentFile?.mkdirs()
        FileOutputStream(localDestinationFile).use { out ->
            val ok = client.retrieveFile(remoteFilePath, out)
            if (!ok) {
                throw IllegalStateException("Failed to download $remoteFilePath (FTP reply: ${client.replyString})")
            }
        }
        localDestinationFile
    }

    suspend fun uploadFile(
        connection: CloudConnectionEntity,
        localFile: File,
        remoteFolder: String
    ): Result<Unit> = withFtpConnection(connection) { client ->
        val targetFolder = if (remoteFolder.isBlank()) "/" else remoteFolder
        if (!client.changeWorkingDirectory(targetFolder)) {
            throw IllegalStateException("Remote folder does not exist: $targetFolder")
        }
        FileInputStream(localFile).use { input ->
            val ok = client.storeFile(localFile.name, input)
            if (!ok) {
                throw IllegalStateException("Failed to upload ${localFile.name} (FTP reply: ${client.replyString})")
            }
        }
    }

    suspend fun createDirectory(
        connection: CloudConnectionEntity,
        parentFolder: String,
        folderName: String
    ): Result<Unit> = withFtpConnection(connection) { client ->
        val target = "${parentFolder.trimEnd('/')}/$folderName"
        if (!client.makeDirectory(target)) {
            throw IllegalStateException("Could not create folder $target")
        }
    }

    suspend fun deleteRemoteItem(
        connection: CloudConnectionEntity,
        remotePath: String,
        isDirectory: Boolean
    ): Result<Unit> = withFtpConnection(connection) { client ->
        val ok = if (isDirectory) client.removeDirectory(remotePath) else client.deleteFile(remotePath)
        if (!ok) {
            throw IllegalStateException("Could not delete remote item: $remotePath")
        }
    }
}
