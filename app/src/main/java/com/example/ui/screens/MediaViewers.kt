package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.model.StorageItem
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// -----------------------------------------------------------------------------
// 1. IMAGE VIEWER (Zoom, Pan, Rotate, Crop, Save, Share, Rename, Delete, Info)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    item: StorageItem,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(item.pathOrUri) { mutableStateOf<Bitmap?>(null) }
    var isModified by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(item.pathOrUri) {
        bitmap = withContext(Dispatchers.IO) {
            if (item.isSafUri) {
                context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                BitmapFactory.decodeFile(item.pathOrUri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = {
                        bitmap?.let { bmp ->
                            val matrix = Matrix().apply { postRotate(90f) }
                            bitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                            isModified = true
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees")
                    }
                    IconButton(onClick = {
                        bitmap?.let { bmp ->
                            // Center square crop
                            val size = minOf(bmp.width, bmp.height)
                            val x = (bmp.width - size) / 2
                            val y = (bmp.height - size) / 2
                            bitmap = Bitmap.createBitmap(bmp, x, y, size, size)
                            isModified = true
                            Toast.makeText(context, "Cropped to 1:1 center. Tap Save to persist.", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Rounded.Crop, contentDescription = "Crop 1:1")
                    }
                    if (isModified) {
                        IconButton(onClick = {
                            val bmpToSave = bitmap ?: return@IconButton
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val format = if (item.extension.equals("png", true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                        if (item.isSafUri) {
                                            context.contentResolver.openOutputStream(Uri.parse(item.pathOrUri), "wt")?.use {
                                                bmpToSave.compress(format, 95, it)
                                            }
                                        } else {
                                            FileOutputStream(File(item.pathOrUri), false).use {
                                                bmpToSave.compress(format, 95, it)
                                            }
                                        }
                                    }.isSuccess
                                }
                                if (ok) {
                                    isModified = false
                                    Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Save, contentDescription = "Save edited image")
                        }
                    }
                    IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = onRename) { Icon(Icons.Rounded.DriveFileRenameOutline, "Rename") }
                    IconButton(onClick = onProperties) { Icon(Icons.Rounded.Info, "Properties") }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. VIDEO PLAYER (Media3 ExoPlayer)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    item: StorageItem,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val uri = remember(item.pathOrUri) {
        if (item.isSafUri) Uri.parse(item.pathOrUri) else Uri.fromFile(File(item.pathOrUri))
    }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
                        IconButton(onClick = onRename) { Icon(Icons.Rounded.DriveFileRenameOutline, "Rename") }
                        IconButton(onClick = onProperties) { Icon(Icons.Rounded.Info, "Properties") }
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (duration > 0) {
                        Slider(
                            value = currentPos.toFloat(),
                            onValueChange = { exoPlayer.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${FormatUtils.formatDuration(currentPos)} / ${FormatUtils.formatDuration(duration)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause")
                            }
                            IconButton(onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }) {
                                Icon(if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Volume")
                            }
                            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                                Icon(if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Fullscreen")
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. AUDIO PLAYER (Media3 ExoPlayer with Playlist Prev/Next & Volume)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    initialItem: StorageItem,
    playlist: List<StorageItem>,
    onBack: () -> Unit,
    onShare: (StorageItem) -> Unit,
    onRename: (StorageItem) -> Unit,
    onDelete: (StorageItem) -> Unit,
    onProperties: (StorageItem) -> Unit
) {
    val context = LocalContext.current
    val tracks = remember(playlist, initialItem) {
        if (playlist.isEmpty()) listOf(initialItem) else playlist
    }
    var currentIndex by remember(initialItem, tracks) {
        mutableIntStateOf(tracks.indexOfFirst { it.pathOrUri == initialItem.pathOrUri }.coerceAtLeast(0))
    }
    val activeTrack = tracks[currentIndex.coerceIn(0, tracks.lastIndex)]

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(activeTrack.pathOrUri) {
        val uri = if (activeTrack.isSafUri) Uri.parse(activeTrack.pathOrUri) else Uri.fromFile(File(activeTrack.pathOrUri))
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Audio Player") },
                actions = {
                    IconButton(onClick = { onShare(activeTrack) }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = { onRename(activeTrack) }) { Icon(Icons.Rounded.DriveFileRenameOutline, "Rename") }
                    IconButton(onClick = { onProperties(activeTrack) }) { Icon(Icons.Rounded.Info, "Properties") }
                    IconButton(onClick = { onDelete(activeTrack) }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = "Audio track",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(76.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(activeTrack.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${FormatUtils.formatBytes(activeTrack.size)} • Track ${currentIndex + 1} of ${tracks.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Slider(
                value = currentPos.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(FormatUtils.formatDuration(currentPos), style = MaterialTheme.typography.labelSmall)
                Text(FormatUtils.formatDuration(duration), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = currentIndex > 0,
                    onClick = { currentIndex = (currentIndex - 1).coerceAtLeast(0) }
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(
                    enabled = currentIndex < tracks.lastIndex,
                    onClick = { currentIndex = (currentIndex + 1).coerceAtMost(tracks.lastIndex) }
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Volume")
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        exoPlayer.volume = it
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. PDF VIEWER (Built-in PdfRenderer with Zoom, Page Nav & Open With Fallback)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    item: StorageItem,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onOpenWith: () -> Unit
) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }

    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    DisposableEffect(item.pathOrUri) {
        try {
            val fd = if (item.isSafUri) {
                context.contentResolver.openFileDescriptor(Uri.parse(item.pathOrUri), "r")
            } else {
                ParcelFileDescriptor.open(File(item.pathOrUri), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            if (fd != null) {
                val r = PdfRenderer(fd)
                pfd = fd
                renderer = r
                pageCount = r.pageCount
            } else {
                renderError = "Unable to open PDF descriptor"
            }
        } catch (e: Exception) {
            renderError = e.message ?: "PDF cannot be rendered natively"
        }

        onDispose {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    LaunchedEffect(renderer, currentPage) {
        val r = renderer ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        pageBitmap = withContext(Dispatchers.IO) {
            runCatching {
                r.openPage(currentPage.coerceIn(0, pageCount - 1)).use { page ->
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pageCount > 0) {
                            Text("Page ${currentPage + 1} of $pageCount", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenWith) { Icon(Icons.Rounded.OpenInNew, "Open With") }
                    IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = onRename) { Icon(Icons.Rounded.DriveFileRenameOutline, "Rename") }
                    IconButton(onClick = onProperties) { Icon(Icons.Rounded.Info, "Properties") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (renderError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Built-in PDF preview unavailable", style = MaterialTheme.typography.titleMedium)
                        Text(renderError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onOpenWith) {
                            Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open With External PDF App")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = pageBitmap
                    if (bmp == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(scaleX = zoom, scaleY = zoom)
                        )
                    }
                }

                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            OutlinedButton(
                                enabled = currentPage > 0,
                                onClick = { currentPage-- }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, "Previous page")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                enabled = currentPage < pageCount - 1,
                                onClick = { currentPage++ }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateNext, "Next page")
                            }
                        }
                        Row {
                            IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(1f) }) {
                                Icon(Icons.Rounded.ZoomOut, "Zoom out")
                            }
                            IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(3f) }) {
                                Icon(Icons.Rounded.ZoomIn, "Zoom in")
                            }
                        }
                    }
                }
            }
        }
    }
}
