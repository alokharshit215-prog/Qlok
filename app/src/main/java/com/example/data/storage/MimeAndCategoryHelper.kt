package com.example.data.storage

import android.webkit.MimeTypeMap
import com.example.model.FileCategory
import java.util.Locale

object MimeAndCategoryHelper {

    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg")
    val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv")
    val audioExtensions = setOf("mp3", "wav", "m4a", "ogg", "flac", "aac", "opus", "wma", "amr")
    val archiveExtensions = setOf("zip", "7z", "tar", "gz", "bz2", "xz", "rar", "tgz")
    val apkExtensions = setOf("apk", "apks", "xapk")
    val pdfExtensions = setOf("pdf")

    val codeExtensions = setOf(
        "py", "java", "kt", "kts", "js", "jsx", "ts", "tsx", "c", "cpp", "cc", "h", "hpp",
        "html", "htm", "css", "scss", "json", "xml", "yaml", "yml", "sql", "php", "sh", "bash",
        "md", "markdown", "ini", "conf", "cfg", "toml", "gradle", "properties", "rs", "go", "rb", "swift", "dart"
    )

    val plainTextExtensions = setOf("txt", "log", "csv", "tsv", "env", "gitignore", "makefile")

    val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub"
    ) + plainTextExtensions + codeExtensions

    val codeTemplateSuggestions = listOf(
        "main.py", "Main.kt", "Main.java", "index.html", "style.css", "script.js",
        "app.cpp", "main.c", "config.json", "data.xml", "query.sql", "script.sh",
        "README.md", "config.yaml", "config.yml", "file.php", "notes.txt", "app.log",
        "config.ini", "server.conf"
    )

    fun extractExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == fileName.length - 1) return ""
        return fileName.substring(lastDot + 1).lowercase(Locale.ROOT)
    }

    fun getMimeType(fileName: String, explicitMime: String? = null): String {
        if (!explicitMime.isNullOrBlank() && explicitMime != "application/octet-stream") {
            return explicitMime
        }
        val ext = extractExtension(fileName)
        if (ext.isEmpty()) return "application/octet-stream"
        val custom = when (ext) {
            "kt", "kts" -> "text/x-kotlin"
            "py" -> "text/x-python"
            "java" -> "text/x-java-source"
            "js", "jsx" -> "application/javascript"
            "ts", "tsx" -> "application/typescript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "yaml", "yml" -> "text/yaml"
            "md", "markdown" -> "text/markdown"
            "sql" -> "application/sql"
            "sh", "bash" -> "application/x-sh"
            "c", "h" -> "text/x-c"
            "cpp", "cc", "hpp" -> "text/x-c++"
            "php" -> "application/x-httpd-php"
            "csv" -> "text/csv"
            "log", "ini", "conf", "cfg", "toml", "properties" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "pdf" -> "application/pdf"
            else -> null
        }
        if (custom != null) return custom
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun categorize(fileName: String, pathOrUri: String, mimeType: String?): FileCategory {
        val ext = extractExtension(fileName)
        val lowerMime = mimeType?.lowercase(Locale.ROOT) ?: ""
        val lowerPath = pathOrUri.lowercase(Locale.ROOT)

        return when {
            ext in apkExtensions || lowerMime == "application/vnd.android.package-archive" -> FileCategory.APK
            ext in archiveExtensions || lowerMime.contains("zip") || lowerMime.contains("archive") || lowerMime.contains("compressed") -> FileCategory.ARCHIVES
            ext in imageExtensions || lowerMime.startsWith("image/") -> FileCategory.IMAGES
            ext in videoExtensions || lowerMime.startsWith("video/") -> FileCategory.VIDEOS
            ext in audioExtensions || lowerMime.startsWith("audio/") -> FileCategory.AUDIO
            ext in documentExtensions || lowerMime.startsWith("text/") || lowerMime.contains("pdf") || lowerMime.contains("document") || lowerMime.contains("json") || lowerMime.contains("xml") -> FileCategory.DOCUMENTS
            lowerPath.contains("/download/") || lowerPath.contains("/downloads/") || lowerPath.contains("%2fdownload") -> FileCategory.DOWNLOADS
            else -> FileCategory.OTHER
        }
    }

    fun isEditableTextOrCode(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        if (ext in codeExtensions || ext in plainTextExtensions) return true
        val m = mimeType?.lowercase(Locale.ROOT) ?: ""
        return m.startsWith("text/") || m == "application/json" || m == "application/xml" || m == "application/javascript"
    }

    fun isImage(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        return ext in imageExtensions || (mimeType?.startsWith("image/", ignoreCase = true) == true)
    }

    fun isVideo(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        return ext in videoExtensions || (mimeType?.startsWith("video/", ignoreCase = true) == true)
    }

    fun isAudio(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        return ext in audioExtensions || (mimeType?.startsWith("audio/", ignoreCase = true) == true)
    }

    fun isPdf(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        return ext in pdfExtensions || mimeType.equals("application/pdf", ignoreCase = true)
    }

    fun isZip(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        return ext == "zip" || mimeType.equals("application/zip", ignoreCase = true)
    }

    fun isApk(fileName: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(fileName)
        return ext == "apk" || mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)
    }

    fun isValidFileOrFolderName(name: String): Pair<Boolean, String?> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false to "Name cannot be empty"
        if (trimmed == "." || trimmed == "..") return false to "Invalid reserved name"
        val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
        if (trimmed.any { it in illegal }) {
            return false to "Name cannot contain / \\ : * ? \" < > |"
        }
        if (trimmed.length > 240) return false to "Name is too long"
        return true to null
    }
}
