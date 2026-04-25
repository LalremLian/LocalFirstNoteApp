package com.lalrem.noteapp.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalrem.noteapp.data.repository.NoteRepository
import com.lalrem.noteapp.domain.model.Asset
import com.lalrem.noteapp.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class EditViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        combine(
            repository.notes,
            repository.openNoteIds,
            repository.selectedNoteId,
            repository.drafts
        ) { allNotes, openIds, selectedId, currentDrafts ->
            val hasUnsaved = openIds.any { id ->
                val note = allNotes.find { it.id == id }
                val draft = currentDrafts[id]
                note != null && draft != null && draft != note.content
            }
            _uiState.update { it.copy(
                notes = allNotes,
                openNoteIds = openIds,
                selectedNoteId = selectedId,
                drafts = currentDrafts,
                hasUnsavedChanges = hasUnsaved
            ) }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: EditEvent) {
        when (event) {
            is EditEvent.OnSelectNote -> selectNote(event.noteId)
            is EditEvent.OnCloseNote -> closeNote(event.noteId)
            is EditEvent.OnUpdateDraft -> updateDraft(event.noteId, event.content)
            is EditEvent.OnSaveNote -> updateNoteContent(event.note, event.newContent)
            is EditEvent.OnUpdateAssetRotation -> updateAssetRotation(event.note, event.assetId, event.degrees)
            is EditEvent.OnAssetDrop -> handleAssetDrop(event.noteId, event.uri)
            is EditEvent.OnAddNote -> addNote(event.content)
            is EditEvent.OnToggleAddSheet -> {
                _uiState.update { it.copy(showAddSheet = event.show) }
            }
        }
    }

    private fun selectNote(noteId: String) {
        val currentOpen = uiState.value.openNoteIds
        if (!currentOpen.contains(noteId)) {
            repository.saveSession(currentOpen + noteId, noteId)
        } else {
            repository.saveSession(currentOpen, noteId)
        }
    }

    private fun closeNote(noteId: String) {
        val newList = uiState.value.openNoteIds.filter { it != noteId }
        val newSelectedId = if (uiState.value.selectedNoteId == noteId) newList.lastOrNull() else uiState.value.selectedNoteId
        repository.saveSession(newList, newSelectedId)
        repository.clearDraft(noteId)
    }

    private fun updateDraft(noteId: String, content: String) {
        val newDrafts = uiState.value.drafts.toMutableMap().apply { put(noteId, content) }
        repository.saveDrafts(newDrafts)
    }

    private fun updateNoteContent(note: Note, newContent: String) {
        viewModelScope.launch {
            repository.updateNote(note.copy(content = newContent, timestamp = System.currentTimeMillis()))
            repository.clearDraft(note.id)
        }
    }

    private fun updateAssetRotation(note: Note, assetId: String, degrees: Float) {
        viewModelScope.launch {
            val updatedAssets = note.assets.map {
                if (it.id == assetId) it.copy(rotationDegrees = degrees) else it
            }
            val updatedNote = note.copy(assets = updatedAssets)
            repository.updateNote(updatedNote)
        }
    }

    private fun handleAssetDrop(noteId: String?, uri: Uri) {
        viewModelScope.launch {
            val base64Data = repository.saveImageAsBase64(uri)
            
            if (noteId != null) {
                val note = uiState.value.notes.find { it.id == noteId } ?: return@launch
                if (base64Data != null) {
                    val newAsset = Asset(UUID.randomUUID().toString(), noteId, base64Data)
                    repository.updateNote(note.copy(assets = note.assets + newAsset))
                }
            } else {
                val id = UUID.randomUUID().toString()
                val asset = if (base64Data != null) {
                    listOf(Asset(UUID.randomUUID().toString(), id, base64Data))
                } else emptyList()
                
                val newNote = Note(
                    id = id,
                    content = if (base64Data != null) "Image Note" else "Failed to load image",
                    timestamp = System.currentTimeMillis(),
                    orderIndex = (uiState.value.notes.firstOrNull()?.orderIndex ?: 0.0) + 1.0,
                    version = 1,
                    assets = asset
                )
                repository.updateNote(newNote)
                repository.saveSession(uiState.value.openNoteIds + id, id)
            }
        }
    }

    private fun addNote(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val newNote = Note(
                id = id,
                content = content,
                timestamp = System.currentTimeMillis(),
                orderIndex = (uiState.value.notes.firstOrNull()?.orderIndex ?: 0.0) + 1.0,
                version = 1
            )
            repository.updateNote(newNote)
            repository.saveSession(uiState.value.openNoteIds + id, id)
        }
    }
}
