package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.storage.MimeAndCategoryHelper
import com.example.model.FileCategory
import com.example.model.StorageItem
import com.example.ui.theme.BrightBlue
import com.example.ui.theme.CategoryApkColor
import com.example.ui.theme.CategoryArchivesColor
import com.example.ui.theme.CategoryAudioColor
import com.example.ui.theme.CategoryDocumentsColor
import com.example.ui.theme.CategoryDownloadsColor
import com.example.ui.theme.CategoryImagesColor
import com.example.ui.theme.CategoryOtherColor
import com.example.ui.theme.CategoryVideosColor
import com.example.ui.theme.DarkBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LightBlue
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.ProtectedOrange
import java.io.File
import java.util.Locale

@Composable
fun BlueFileManagerLogo(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(
                Brush.linearGradient(
                    colors = listOf(BrightBlue, PrimaryBlue, DarkBlue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.68f)) {
            val w = this.size.width
            val h = this.size.height

            // Back folder tab
            val tabPath = Path().apply {
                moveTo(w * 0.05f, h * 0.14f)
                lineTo(w * 0.42f, h * 0.14f)
                lineTo(w * 0.52f, h * 0.26f)
                lineTo(w * 0.95f, h * 0.26f)
                lineTo(w * 0.95f, h * 0.50f)
                lineTo(w * 0.05f, h * 0.50f)
                close()
            }
            drawPath(tabPath, color = Color(0xFFBBDEFB))

            // Peeking document sheet
            drawRoundRect(
                color = LightBlue,
                topLeft = Offset(w * 0.14f, h * 0.29f),
                size = Size(w * 0.72f, h * 0.26f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )

            // Front crisp white folder body
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.05f, h * 0.40f),
                size = Size(w * 0.90f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )

            // Signature blue accent lines inside folder
            drawRoundRect(
                color = PrimaryBlue,
                topLeft = Offset(w * 0.18f, h * 0.55f),
                size = Size(w * 0.46f, h * 0.075f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
            drawRoundRect(
                color = Color(0xFF64B5F6),
                topLeft = Offset(w * 0.18f, h * 0.68f),
                size = Size(w * 0.30f, h * 0.075f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
        }
    }
}

@Composable
fun FileTypeIconBadge(
    item: StorageItem,
    size: Dp = 46.dp,
    showThumbnail: Boolean = true
) {
    val ext = item.extension.lowercase(Locale.ROOT)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            item.isDirectory -> {
                val bgColors = if (item.isProtected) {
                    listOf(Color(0xFFFFB74D).copy(alpha = 0.25f), ProtectedOrange.copy(alpha = 0.18f))
                } else {
                    listOf(BrightBlue.copy(alpha = 0.20f), PrimaryBlue.copy(alpha = 0.12f))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(bgColors))
                        .border(
                            width = 1.dp,
                            color = if (item.isProtected) ProtectedOrange.copy(alpha = 0.35f) else PrimaryBlue.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = if (item.isProtected) "Protected folder" else "Folder",
                        tint = if (item.isProtected) ProtectedOrange else PrimaryBlue,
                        modifier = Modifier.size(size * 0.56f)
                    )
                    if (item.isProtected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(ProtectedOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Protected",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            showThumbnail && MimeAndCategoryHelper.isImage(item.name, item.mimeType) && item.canRead -> {
                val model: Any = if (item.isSafUri) android.net.Uri.parse(item.pathOrUri) else File(item.pathOrUri)
                AsyncImage(
                    model = model,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CategoryImagesColor.copy(alpha = 0.12f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                )
            }

            else -> {
                val (accent, icon, tag) = resolveFileVisual(ext, item.mimeType)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.09f))
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.name,
                        tint = accent,
                        modifier = Modifier
                            .size(size * 0.50f)
                            .offset(y = if (tag != null) (-3).dp else 0.dp)
                    )
                    if (tag != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent)
                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = tag,
                                color = Color.White,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMonoFontFamily,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (item.isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB300))
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Favorite",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private fun resolveFileVisual(ext: String, mimeType: String): Triple<Color, ImageVector, String?> {
    return when {
        ext == "pdf" || mimeType == "application/pdf" -> Triple(Color(0xFFD32F2F), Icons.Rounded.PictureAsPdf, "PDF")
        ext in MimeAndCategoryHelper.apkExtensions -> Triple(CategoryApkColor, Icons.Rounded.Android, "APK")
        ext in MimeAndCategoryHelper.archiveExtensions -> Triple(CategoryArchivesColor, Icons.Rounded.FolderZip, ext.uppercase().take(4))
        ext in MimeAndCategoryHelper.videoExtensions || mimeType.startsWith("video/") -> Triple(CategoryVideosColor, Icons.Rounded.VideoFile, ext.uppercase().take(4).ifEmpty { "VID" })
        ext in MimeAndCategoryHelper.audioExtensions || mimeType.startsWith("audio/") -> Triple(CategoryAudioColor, Icons.Rounded.AudioFile, ext.uppercase().take(4).ifEmpty { "AUD" })
        ext in MimeAndCategoryHelper.imageExtensions || mimeType.startsWith("image/") -> Triple(CategoryImagesColor, Icons.Rounded.Image, ext.uppercase().take(4).ifEmpty { "IMG" })
        ext == "py" -> Triple(Color(0xFF1565C0), Icons.Rounded.Code, "PY")
        ext == "kt" || ext == "kts" -> Triple(Color(0xFF6A1B9A), Icons.Rounded.Code, "KT")
        ext == "java" -> Triple(Color(0xFFD84315), Icons.Rounded.Code, "JAVA")
        ext == "js" || ext == "jsx" -> Triple(Color(0xFFE6A100), Icons.Rounded.Code, "JS")
        ext == "ts" || ext == "tsx" -> Triple(Color(0xFF0277BD), Icons.Rounded.Code, "TS")
        ext == "html" || ext == "htm" -> Triple(Color(0xFFE65100), Icons.Rounded.Code, "HTML")
        ext == "css" || ext == "scss" -> Triple(Color(0xFF0288D1), Icons.Rounded.Code, "CSS")
        ext == "json" -> Triple(Color(0xFF00838F), Icons.Rounded.Code, "JSON")
        ext == "xml" -> Triple(Color(0xFF558B2F), Icons.Rounded.Code, "XML")
        ext == "sql" -> Triple(Color(0xFF283593), Icons.Rounded.Code, "SQL")
        ext == "sh" || ext == "bash" -> Triple(Color(0xFF2E7D32), Icons.Rounded.Terminal, "SH")
        ext in MimeAndCategoryHelper.codeExtensions -> Triple(PrimaryBlue, Icons.Rounded.Code, ext.uppercase().take(4))
        ext in MimeAndCategoryHelper.plainTextExtensions || ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx") ->
            Triple(CategoryDocumentsColor, Icons.Rounded.Description, ext.uppercase().take(4).ifEmpty { "DOC" })
        else -> Triple(CategoryOtherColor, Icons.AutoMirrored.Rounded.InsertDriveFile, ext.uppercase().take(4).ifEmpty { null })
    }
}

fun categoryColor(category: FileCategory): Color = when (category) {
    FileCategory.IMAGES -> CategoryImagesColor
    FileCategory.VIDEOS -> CategoryVideosColor
    FileCategory.AUDIO -> CategoryAudioColor
    FileCategory.DOCUMENTS -> CategoryDocumentsColor
    FileCategory.DOWNLOADS -> CategoryDownloadsColor
    FileCategory.APK -> CategoryApkColor
    FileCategory.ARCHIVES -> CategoryArchivesColor
    FileCategory.OTHER -> CategoryOtherColor
}

fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGES -> Icons.Rounded.Image
    FileCategory.VIDEOS -> Icons.Rounded.VideoFile
    FileCategory.AUDIO -> Icons.Rounded.AudioFile
    FileCategory.DOCUMENTS -> Icons.Rounded.Description
    FileCategory.DOWNLOADS -> Icons.Rounded.Download
    FileCategory.APK -> Icons.Rounded.Android
    FileCategory.ARCHIVES -> Icons.Rounded.Archive
    FileCategory.OTHER -> Icons.Rounded.InsertDriveFile
}
