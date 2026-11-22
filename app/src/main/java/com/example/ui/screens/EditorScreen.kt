package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppSettings
import com.example.data.storage.MimeAndCategoryHelper
import com.example.model.StorageItem
import com.example.model.StorageRoot
import com.example.ui.components.FolderPickerDialog
import com.example.ui.theme.EditorDarkBg
import com.example.ui.theme.EditorDarkGutter
import com.example.ui.theme.EditorDarkText
import com.example.ui.theme.EditorLightBg
import com.example.ui.theme.EditorLightGutter
import com.example.ui.theme.EditorLightText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    item: StorageItem,
    viewModel: EditorViewModel,
    settings: AppSettings,
    roots: List<StorageRoot>,
    loadSubfolders: suspend (String, Boolean) -> List<StorageItem>,
    onSetDarkEditor: (Boolean) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
    onSetLineNumbers: (Boolean) -> Unit,
    onSetFontSize: (Int) -> Unit,
    onExit: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var fieldValue by remember(item.pathOrUri) { mutableStateOf(TextFieldValue("")) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingScrollToOffset by remember { mutableStateOf<Int?>(null) }

    // Save As / Save Copy flow state
    var pendingSaveActionIsCopy by remember { mutableStateOf<Boolean?>(null) }
    var pickedDestForSave by remember { mutableStateOf<Triple<String, Boolean, String>?>(null) }
    var saveFilenameInput by remember { mutableStateOf(item.name) }
    var conflictFilenameForSave by remember { mutableStateOf<Triple<String, Boolean, String>?>(null) }

    LaunchedEffect(item.pathOrUri) {
        viewModel.loadFile(item)
    }

    LaunchedEffect(state.content) {
        if (state.content != fieldValue.text) {
            val sel = fieldValue.selection
            val clampedStart = sel.start.coerceIn(0, state.content.length)
            val clampedEnd = sel.end.coerceIn(0, state.content.length)
            fieldValue = TextFieldValue(state.content, TextRange(clampedStart, clampedEnd))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun handleExitRequest() {
        if (state.hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            viewModel.discardDraftOnExit()
            onExit()
        }
    }

    BackHandler { handleExitRequest() }

    val bgColor = if (settings.editorDarkTheme) EditorDarkBg else EditorLightBg
    val gutterColor = if (settings.editorDarkTheme) EditorDarkGutter else EditorLightGutter
    val textColor = if (settings.editorDarkTheme) EditorDarkText else EditorLightText
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(pendingScrollToOffset) {
        val target = pendingScrollToOffset ?: return@LaunchedEffect
        val y = textLayoutResult?.getCursorRect(target.coerceIn(0, fieldValue.text.length))?.top?.toInt()
        if (y != null) {
            verticalScroll.animateScrollTo((y - 80).coerceAtLeast(0))
        }
        pendingScrollToOffset = null
    }

    val ext = remember(state.fileName) { MimeAndCategoryHelper.extractExtension(state.fileName) }
    val syntaxTransformation = remember(settings.editorSyntaxHighlighting, settings.editorDarkTheme, ext) {
        if (settings.editorSyntaxHighlighting) {
            CodeSyntaxTransformation(darkTheme = settings.editorDarkTheme, extension = ext)
        } else {
            VisualTransformation.None
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { handleExitRequest() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = (if (state.hasUnsavedChanges) "• " else "") + state.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val autoSaveLabel = when (settings.editorAutoSaveSeconds) {
                            30 -> "Auto-save 30s"
                            60 -> "Auto-save 1m"
                            300 -> "Auto-save 5m"
                            else -> "Manual save"
                        }
                        Text(
                            text = "${state.content.lines().size} lines • $autoSaveLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { viewModel.saveCurrentFile() },
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Editor menu")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Save As...") },
                                onClick = {
                                    showOverflowMenu = false
                                    saveFilenameInput = state.fileName
                                    pendingSaveActionIsCopy = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save a Copy...") },
                                onClick = {
                                    showOverflowMenu = false
                                    saveFilenameInput = state.fileName
                                    pendingSaveActionIsCopy = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Find & Replace") },
                                leadingIcon = { Icon(Icons.Rounded.FindReplace, null) },
                                onClick = { showOverflowMenu = false; showFindBar = !showFindBar }
                            )
                            DropdownMenuItem(
                                text = { Text("Go to Line") },
                                onClick = { showOverflowMenu = false; showGoToLineDialog = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                leadingIcon = { Icon(Icons.Rounded.SelectAll, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Selection") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val s = fieldValue.selection
                                    val slice = if (!s.collapsed) fieldValue.text.substring(s.min, s.max) else fieldValue.text
                                    clipboard.setText(AnnotatedString(slice))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cut Selection") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCut, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val s = fieldValue.selection
                                    if (!s.collapsed) {
                                        clipboard.setText(AnnotatedString(fieldValue.text.substring(s.min, s.max)))
                                        val updated = fieldValue.text.removeRange(s.min, s.max)
                                        fieldValue = TextFieldValue(updated, TextRange(s.min))
                                        viewModel.updateContent(updated, settings.editorAutoSaveSeconds)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Paste") },
                                leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val clip = clipboard.getText()?.text ?: return@DropdownMenuItem
                                    val s = fieldValue.selection
                                    val updated = fieldValue.text.replaceRange(s.min, s.max, clip)
                                    val cursor = s.min + clip.length
                                    fieldValue = TextFieldValue(updated, TextRange(cursor))
                                    viewModel.updateContent(updated, settings.editorAutoSaveSeconds)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (settings.editorDarkTheme) "Light Editor" else "Dark Editor") },
                                leadingIcon = {
                                    Icon(if (settings.editorDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null)
                                },
                                onClick = { showOverflowMenu = false; onSetDarkEditor(!settings.editorDarkTheme) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (settings.editorWordWrap) "Disable Word Wrap" else "Enable Word Wrap") },
                                onClick = { showOverflowMenu = false; onSetWordWrap(!settings.editorWordWrap) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (settings.editorLineNumbers) "Hide Line Numbers" else "Show Line Numbers") },
                                onClick = { showOverflowMenu = false; onSetLineNumbers(!settings.editorLineNumbers) }
                            )
                            DropdownMenuItem(
                                text = { Text("Font Size +") },
                                onClick = { onSetFontSize((settings.editorFontSizeSp + 2).coerceAtMost(24)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Font Size -") },
                                onClick = { onSetFontSize((settings.editorFontSizeSp - 2).coerceAtLeast(10)) }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            // Crash-recovery Draft Banner
            AnimatedVisibility(visible = state.hasDraftRecovery) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Unsaved draft found from an earlier session.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.recoverDraft() }) { Text("Recover") }
                        TextButton(onClick = { viewModel.discardRecoveredDraft() }) { Text("Dismiss") }
                    }
                }
            }

            // Find & Replace Bar
            AnimatedVisibility(visible = showFindBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it },
                                placeholder = { Text("Find") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (findQuery.isNotEmpty()) {
                                        val startFrom = fieldValue.selection.end
                                        var idx = fieldValue.text.indexOf(findQuery, startIndex = startFrom, ignoreCase = true)
                                        if (idx == -1) idx = fieldValue.text.indexOf(findQuery, ignoreCase = true)
                                        if (idx >= 0) {
                                            fieldValue = fieldValue.copy(selection = TextRange(idx, idx + findQuery.length))
                                            pendingScrollToOffset = idx
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("No match found") }
                                        }
                                    }
                                }
                            ) { Text("Find") }
                            IconButton(onClick = { showFindBar = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close find")
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                placeholder = { Text("Replace with") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    val count = viewModel.replaceAll(findQuery, replaceQuery, matchCase = false)
                                    scope.launch { snackbarHostState.showSnackbar("Replaced $count occurrence(s)") }
                                }
                            ) { Text("Replace All") }
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.loadError != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.loadError ?: "Error loading file", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    val lineCount = remember(fieldValue.text) { fieldValue.text.count { it == '\n' } + 1 }
                    val fontSp = settings.editorFontSizeSp.sp
                    val lineHeightSp = (settings.editorFontSizeSp + 6).sp

                    val lineNumbersText = remember(fieldValue.text, lineCount, settings.editorWordWrap, textLayoutResult) {
                        val layout = textLayoutResult
                        if (settings.editorWordWrap && layout != null && layout.lineCount > 0) {
                            val raw = fieldValue.text
                            val sb = StringBuilder()
                            var logicalLine = 1
                            for (vLine in 0 until layout.lineCount) {
                                if (vLine > 0) sb.append('\n')
                                val startOffset = layout.getLineStart(vLine)
                                val isLogicalStart = vLine == 0 ||
                                    (startOffset > 0 && startOffset <= raw.length && raw[startOffset - 1] == '\n')
                                if (isLogicalStart) {
                                    sb.append(logicalLine++)
                                }
                            }
                            sb.toString()
                        } else {
                            (1..lineCount).joinToString("\n")
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(bgColor)
                    ) {
                        val viewportMinHeight = maxHeight
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                        ) {
                            if (settings.editorLineNumbers) {
                                Box(
                                    modifier = Modifier
                                        .background(gutterColor)
                                        .defaultMinSize(minHeight = viewportMinHeight)
                                        .padding(horizontal = 10.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Text(
                                        text = lineNumbersText,
                                        textAlign = TextAlign.End,
                                        style = TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = fontSp,
                                            lineHeight = lineHeightSp,
                                            color = textColor.copy(alpha = 0.48f)
                                        )
                                    )
                                }
                            }

                            val editorModifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = viewportMinHeight)
                                .then(if (!settings.editorWordWrap) Modifier.horizontalScroll(horizontalScroll) else Modifier)
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .testTag("code_editor_input")

                            BasicTextField(
                                value = fieldValue,
                                onValueChange = { updated ->
                                    fieldValue = updated
                                    viewModel.updateContent(updated.text, settings.editorAutoSaveSeconds)
                                },
                                onTextLayout = { layout ->
                                    textLayoutResult = layout
                                },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = fontSp,
                                    lineHeight = lineHeightSp,
                                    color = textColor
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = syntaxTransformation,
                                modifier = editorModifier
                            )
                        }
                    }
                }
            }
        }
    }

    // Go to Line Dialog
    if (showGoToLineDialog) {
        var lineInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line") },
            text = {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter(Char::isDigit) },
                    label = { Text("Line number (1–${fieldValue.text.lines().size})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val targetLine = (lineInput.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val lines = fieldValue.text.split('\n')
                    val clampedLine = targetLine.coerceAtMost(lines.size)
                    var offset = 0
                    for (i in 0 until clampedLine - 1) {
                        offset += lines[i].length + 1
                    }
                    fieldValue = fieldValue.copy(selection = TextRange(offset))
                    pendingScrollToOffset = offset
                    showGoToLineDialog = false
                }) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Unsaved Changes Dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("Save your changes to \"${state.fileName}\" before leaving?") },
            confirmButton = {
                Button(onClick = {
                    showUnsavedDialog = false
                    viewModel.saveCurrentFile { onExit() }
                }) { Text("Save & Exit") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        viewModel.discardDraftOnExit()
                        onExit()
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    // Step 1 of Save As / Save Copy: Pick Folder
    if (pendingSaveActionIsCopy != null && pickedDestForSave == null) {
        FolderPickerDialog(
            title = if (pendingSaveActionIsCopy == true) "Save Copy To..." else "Save As Destination...",
            roots = roots,
            loadSubfolders = loadSubfolders,
            onDismiss = { pendingSaveActionIsCopy = null },
            onSelectFolder = { path, saf, label ->
                pickedDestForSave = Triple(path, saf, label)
            }
        )
    }

    // Step 2 of Save As / Save Copy: Enter Filename
    pickedDestForSave?.let { (destPath, destIsSaf, destLabel) ->
        val isCopy = pendingSaveActionIsCopy ?: false
        AlertDialog(
            onDismissRequest = { pickedDestForSave = null; pendingSaveActionIsCopy = null },
            title = { Text(if (isCopy) "Save Copy Filename" else "Save As Filename") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Destination: $destLabel", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = saveFilenameInput,
                        onValueChange = { saveFilenameInput = it },
                        label = { Text("Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = saveFilenameInput.trim()
                    scope.launch {
                        val exists = viewModel.destinationFileExists(destPath, destIsSaf, trimmed)
                        if (exists) {
                            conflictFilenameForSave = Triple(destPath, destIsSaf, trimmed)
                            pickedDestForSave = null
                        } else {
                            viewModel.saveAsOrCopy(destPath, destIsSaf, trimmed, keepBothIfExists = false, replaceIfExists = false, isSaveCopy = isCopy) {
                                pickedDestForSave = null
                                pendingSaveActionIsCopy = null
                            }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pickedDestForSave = null; pendingSaveActionIsCopy = null }) { Text("Cancel") }
            }
        )
    }

    // Step 3: Conflict dialog for Save As / Save Copy
    conflictFilenameForSave?.let { (destPath, destIsSaf, filename) ->
        val isCopy = pendingSaveActionIsCopy ?: false
        AlertDialog(
            onDismissRequest = { conflictFilenameForSave = null; pendingSaveActionIsCopy = null },
            title = { Text("File Already Exists") },
            text = { Text("\"$filename\" already exists in the chosen folder.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveAsOrCopy(destPath, destIsSaf, filename, keepBothIfExists = false, replaceIfExists = true, isSaveCopy = isCopy) {
                        conflictFilenameForSave = null
                        pendingSaveActionIsCopy = null
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        viewModel.saveAsOrCopy(destPath, destIsSaf, filename, keepBothIfExists = true, replaceIfExists = false, isSaveCopy = isCopy) {
                            conflictFilenameForSave = null
                            pendingSaveActionIsCopy = null
                        }
                    }) { Text("Keep Both") }
                    TextButton(onClick = { conflictFilenameForSave = null; pendingSaveActionIsCopy = null }) { Text("Cancel") }
                }
            }
        )
    }
}

private class CodeSyntaxTransformation(
    private val darkTheme: Boolean,
    private val extension: String
) : VisualTransformation {

    private val keywordColor = if (darkTheme) Color(0xFF79B8FF) else Color(0xFF0D47A1)
    private val stringColor = if (darkTheme) Color(0xFF9ECBFF) else Color(0xFF1B5E20)
    private val commentColor = if (darkTheme) Color(0xFF6A737D) else Color(0xFF757575)
    private val numberColor = if (darkTheme) Color(0xFFFFAB70) else Color(0xFFE65100)

    private val keywordsRegex = Regex(
        "\\b(fun|val|var|class|object|interface|return|if|else|when|for|while|import|package|def|print|from|as|async|await|try|except|catch|throw|public|private|protected|void|int|float|double|boolean|string|const|let|function|export|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|TABLE|true|false|null|None|True|False)\\b"
    )
    private val stringRegex = Regex("(\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')")
    private val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val commentRegex = if (extension in setOf("py", "sh", "bash", "yaml", "yml", "ini", "conf")) {
        Regex("#[^\\n]*")
    } else {
        Regex("//[^\\n]*")
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length > 25_000) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val highlighted = buildAnnotatedString {
            append(raw)
            keywordsRegex.findAll(raw).forEach {
                addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
            }
            numberRegex.findAll(raw).forEach {
                addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
            }
            stringRegex.findAll(raw).forEach {
                addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
            }
            commentRegex.findAll(raw).forEach {
                addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
