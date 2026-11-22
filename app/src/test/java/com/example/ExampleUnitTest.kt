package com.example

import com.example.data.preferences.SettingsDataStore
import com.example.data.storage.MimeAndCategoryHelper
import com.example.data.storage.ZipArchiveService
import com.example.data.storage.ZipPathTraversalException
import com.example.model.BatchRenameConfig
import com.example.model.FileCategory
import com.example.model.SortDirection
import com.example.model.SortOption
import com.example.model.StorageItem
import com.example.util.FormatUtils
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExampleUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `pin hashing verifies valid pin and rejects wrong pin`() {
        val hash = SettingsDataStore.hashPin("4829")
        assertTrue(SettingsDataStore.verifyPin("4829", hash))
        assertFalse(SettingsDataStore.verifyPin("1234", hash))
        assertFalse(SettingsDataStore.verifyPin("4829", ""))
    }

    @Test
    fun `filename validation rejects illegal characters and reserved names`() {
        assertTrue(MimeAndCategoryHelper.isValidFileOrFolderName("main.py").first)
        assertTrue(MimeAndCategoryHelper.isValidFileOrFolderName("MyProject").first)
        assertFalse(MimeAndCategoryHelper.isValidFileOrFolderName("").first)
        assertFalse(MimeAndCategoryHelper.isValidFileOrFolderName("..").first)
        assertFalse(MimeAndCategoryHelper.isValidFileOrFolderName("bad/name").first)
        assertFalse(MimeAndCategoryHelper.isValidFileOrFolderName("bad*name").first)
    }

    @Test
    fun `categorization and editable code detection work accurately`() {
        assertEquals(FileCategory.APK, MimeAndCategoryHelper.categorize("release.apk", "/Download/release.apk", null))
        assertEquals(FileCategory.ARCHIVES, MimeAndCategoryHelper.categorize("backup.zip", "/Backup/backup.zip", null))
        assertEquals(FileCategory.IMAGES, MimeAndCategoryHelper.categorize("photo.jpg", "/Pictures/photo.jpg", null))
        assertEquals(FileCategory.VIDEOS, MimeAndCategoryHelper.categorize("clip.mp4", "/Movies/clip.mp4", null))
        assertEquals(FileCategory.AUDIO, MimeAndCategoryHelper.categorize("song.mp3", "/Music/song.mp3", null))
        assertTrue(MimeAndCategoryHelper.isEditableTextOrCode("main.py"))
        assertTrue(MimeAndCategoryHelper.isEditableTextOrCode("Main.kt"))
        assertTrue(MimeAndCategoryHelper.isEditableTextOrCode("config.json"))
        assertFalse(MimeAndCategoryHelper.isEditableTextOrCode("photo.png"))
    }

    @Test
    fun `keep both conflict naming generates incremented filenames`() {
        val existing = setOf("notes.txt", "notes (1).txt", "photo.jpg")
        assertEquals("notes (2).txt", FormatUtils.computeKeepBothName("notes.txt", existing))
        assertEquals("photo (1).jpg", FormatUtils.computeKeepBothName("photo.jpg", existing))
        assertEquals("readme.md", FormatUtils.computeKeepBothName("readme.md", existing))
    }

    @Test
    fun `batch rename preview formats numbering prefix suffix and replacements`() {
        val items = listOf(
            StorageItem("/a/photo.jpg", "photo.jpg", "/a", isDirectory = false),
            StorageItem("/a/photo2.jpg", "photo2.jpg", "/a", isDirectory = false),
            StorageItem("/a/photo3.jpg", "photo3.jpg", "/a", isDirectory = false)
        )
        val previews = FormatUtils.previewBatchRename(
            items,
            BatchRenameConfig(useNumbering = true, baseName = "Trip", startNumber = 1, padDigits = 3)
        )
        assertEquals("Trip_001.jpg", previews[0].second)
        assertEquals("Trip_002.jpg", previews[1].second)
        assertEquals("Trip_003.jpg", previews[2].second)
    }

    @Test
    fun `sorting keeps folders before files and respects sort direction`() {
        val items = listOf(
            StorageItem("/f1", "zebra.txt", "/", isDirectory = false, size = 100),
            StorageItem("/d1", "Beta", "/", isDirectory = true, size = 0),
            StorageItem("/f2", "alpha.txt", "/", isDirectory = false, size = 500),
            StorageItem("/d2", "Alpha", "/", isDirectory = true, size = 0)
        )
        val sortedNameAsc = FormatUtils.sortItems(items, SortOption.NAME, SortDirection.ASCENDING)
        assertEquals(listOf("Alpha", "Beta", "alpha.txt", "zebra.txt"), sortedNameAsc.map { it.name })

        val sortedSizeDesc = FormatUtils.sortItems(items, SortOption.SIZE, SortDirection.DESCENDING)
        assertTrue(sortedSizeDesc[0].isDirectory && sortedSizeDesc[1].isDirectory)
        assertEquals("alpha.txt", sortedSizeDesc[2].name)
    }

    @Test
    fun `zip path sanitizer blocks directory traversal attempts`() {
        assertEquals("src/main.py", ZipArchiveService.sanitizeEntryPath("/src/./main.py"))
        var threw = false
        try {
            ZipArchiveService.sanitizeEntryPath("../../evil.sh")
        } catch (_: ZipPathTraversalException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `zip4j encrypted zip round trip works on real temporary files`() {
        val src = tempFolder.newFile("main.py").apply { writeText("print('Hello World')") }
        val zipFile = File(tempFolder.root, "code.zip")
        val zip = ZipFile(zipFile, "alok123".toCharArray())
        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
        }
        zip.addFile(src, params)
        assertTrue(zipFile.exists())
        assertTrue(ZipFile(zipFile).isEncrypted)

        val outDir = tempFolder.newFolder("extracted")
        ZipFile(zipFile, "alok123".toCharArray()).extractAll(outDir.absolutePath)
        assertEquals("print('Hello World')", File(outDir, "main.py").readText())
    }
}
