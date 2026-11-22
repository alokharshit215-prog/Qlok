package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BlueFileManagerApp
import com.example.data.storage.MimeAndCategoryHelper
import com.example.model.StorageItem
import com.example.util.FormatUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUiState(
    val fileName: String = "",
    val pathOrUri: String = "",
    val isSafUri: Boolean = false,
    val parentPathOrUri: String = "",
    val content: String = "",
    val savedContent: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val hasDraftRecovery: Boolean = false,
    val recoveredDraftContent: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastSavedTimeMillis: Long = 0L
) {
    val hasUnsavedChanges: Boolean get() = content != savedContent
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BlueFileManagerApp
    private val repo = app.repository

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var autoSaveJob: Job? = null

    fun loadFile(item: StorageItem) {
        viewModelScope.launch {
            _state.value = EditorUiState(
                fileName = item.name,
                pathOrUri = item.pathOrUri,
                isSafUri = item.isSafUri,
                parentPathOrUri = item.parentPathOrUri,
                isLoading = true
            )
            undoStack.clear()
            redoStack.clear()

            val res = repo.readTextFile(item.pathOrUri, item.isSafUri)
            res.onSuccess { diskText ->
                val draft = repo.getDraft(item.pathOrUri)
                val hasRecovery = draft != null && draft.content != diskText
                _state.value = _state.value.copy(
                    content = diskText,
                    savedContent = diskText,
                    isLoading = false,
                    hasDraftRecovery = hasRecovery,
                    recoveredDraftContent = if (hasRecovery) draft?.content else null
                )
            }.onFailure { err ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    loadError = err.message ?: "Unable to open file"
                )
            }
        }
    }

    fun recoverDraft() {
        val draftText = _state.value.recoveredDraftContent ?: return
        undoStack.addLast(_state.value.content)
        _state.value = _state.value.copy(
            content = draftText,
            hasDraftRecovery = false,
            recoveredDraftContent = null,
            canUndo = undoStack.isNotEmpty()
        )
        _events.tryEmit("Recovered unsaved draft")
    }

    fun discardRecoveredDraft() {
        val path = _state.value.pathOrUri
        viewModelScope.launch { repo.deleteDraft(path) }
        _state.value = _state.value.copy(
            hasDraftRecovery = false,
            recoveredDraftContent = null
        )
    }

    fun updateContent(newContent: String, autoSaveIntervalSeconds: Int) {
        val current = _state.value.content
        if (newContent == current) return
        if (undoStack.size > 100) undoStack.removeFirst()
        undoStack.addLast(current)
        redoStack.clear()
        _state.value = _state.value.copy(
            content = newContent,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
        // Persist lightweight draft immediately in background for crash recovery
        val curState = _state.value
        viewModelScope.launch {
            repo.saveDraft(curState.pathOrUri, curState.fileName, newContent)
        }
        scheduleAutoSave(autoSaveIntervalSeconds)
    }

    private fun scheduleAutoSave(intervalSeconds: Int) {
        autoSaveJob?.cancel()
        if (intervalSeconds <= 0) return
        autoSaveJob = viewModelScope.launch {
            delay(intervalSeconds * 1000L)
            if (_state.value.hasUnsavedChanges) {
                saveCurrentFile(silent = true)
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(_state.value.content)
        _state.value = _state.value.copy(
            content = prev,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_state.value.content)
        _state.value = _state.value.copy(
            content = next,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun replaceAll(find: String, replace: String, matchCase: Boolean): Int {
        if (find.isEmpty()) return 0
        val text = _state.value.content
        var count = 0
        val regex = Regex(Regex.escape(find), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        val updated = regex.replace(text) {
            count++
            Regex.escapeReplacement(replace)
        }
        if (count > 0) {
            undoStack.addLast(text)
            redoStack.clear()
            _state.value = _state.value.copy(
                content = updated,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        return count
    }

    fun saveCurrentFile(silent: Boolean = false, onSaved: () -> Unit = {}) {
        val cur = _state.value
        viewModelScope.launch {
            _state.value = cur.copy(isSaving = true)
            val res = repo.writeTextFile(cur.pathOrUri, cur.isSafUri, cur.content, cur.fileName)
            res.onSuccess {
                _state.value = _state.value.copy(
                    isSaving = false,
                    savedContent = cur.content,
                    lastSavedTimeMillis = System.currentTimeMillis()
                )
                if (!silent) _events.tryEmit("Saved '${cur.fileName}'")
                onSaved()
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false)
                _events.tryEmit(err.message ?: "Save failed")
            }
        }
    }

    suspend fun destinationFileExists(destFolderPathOrUri: String, destIsSaf: Boolean, fileName: String): Boolean {
        val children = repo.listDirectory(destFolderPathOrUri, destIsSaf, includeHidden = true).getOrDefault(emptyList())
        return children.any { it.name == fileName }
    }

    fun saveAsOrCopy(
        destFolderPathOrUri: String,
        destIsSaf: Boolean,
        fileName: String,
        keepBothIfExists: Boolean,
        replaceIfExists: Boolean,
        isSaveCopy: Boolean,
        onComplete: () -> Unit = {}
    ) {
        val clean = fileName.trim()
        val (valid, err) = MimeAndCategoryHelper.isValidFileOrFolderName(clean)
        if (!valid) {
            _events.tryEmit(err ?: "Invalid filename")
            return
        }
        val contentToWrite = _state.value.content
        viewModelScope.launch {
            val children = repo.listDirectory(destFolderPathOrUri, destIsSaf, includeHidden = true).getOrDefault(emptyList())
            val existing = children.firstOrNull { it.name == clean }
            val targetName = when {
                existing == null -> clean
                keepBothIfExists -> FormatUtils.computeKeepBothName(clean, children.map { it.name }.toSet())
                replaceIfExists -> {
                    repo.writeTextFile(existing.pathOrUri, existing.isSafUri, contentToWrite, existing.name)
                        .onSuccess {
                            if (!isSaveCopy) {
                                _state.value = _state.value.copy(
                                    fileName = existing.name,
                                    pathOrUri = existing.pathOrUri,
                                    isSafUri = existing.isSafUri,
                                    parentPathOrUri = destFolderPathOrUri,
                                    savedContent = contentToWrite,
                                    lastSavedTimeMillis = System.currentTimeMillis()
                                )
                            }
                            _events.tryEmit(if (isSaveCopy) "Saved copy '${existing.name}'" else "Saved as '${existing.name}'")
                            onComplete()
                        }.onFailure { _events.tryEmit(it.message ?: "Save failed") }
                    return@launch
                }
                else -> return@launch
            }

            repo.createFile(destFolderPathOrUri, destIsSaf, targetName, contentToWrite)
                .onSuccess { created ->
                    if (!isSaveCopy) {
                        _state.value = _state.value.copy(
                            fileName = created.name,
                            pathOrUri = created.pathOrUri,
                            isSafUri = created.isSafUri,
                            parentPathOrUri = destFolderPathOrUri,
                            savedContent = contentToWrite,
                            lastSavedTimeMillis = System.currentTimeMillis()
                        )
                    }
                    _events.tryEmit(if (isSaveCopy) "Saved copy '${created.name}'" else "Saved as '${created.name}'")
                    onComplete()
                }.onFailure {
                    _events.tryEmit(it.message ?: "Failed to save file")
                }
        }
    }

    fun discardDraftOnExit() {
        val path = _state.value.pathOrUri
        viewModelScope.launch { repo.deleteDraft(path) }
    }
}
