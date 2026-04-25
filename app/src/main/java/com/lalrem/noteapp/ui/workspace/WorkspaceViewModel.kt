package com.lalrem.noteapp.ui.workspace

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
class WorkspaceViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private val _effect = kotlinx.coroutines.channels.Channel<WorkspaceEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        combine(
            repository.notes,
            repository.openNoteIds,
            repository.selectedNoteId,
            savedStateHandle.getStateFlow("input_buffer", "")
        ) { notes, openIds, selectedId, inputBuffer ->
            _uiState.update { it.copy(
                notes = notes,
                openNoteIds = openIds,
                selectedNoteId = selectedId,
                inputBuffer = inputBuffer
            ) }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: WorkspaceEvent) {
        when (event) {
            is WorkspaceEvent.OnInputBufferChange -> {
                savedStateHandle["input_buffer"] = event.text
            }
            is WorkspaceEvent.OnOpenNote -> {
                if (event.noteId.isNotBlank()) {
                    openNote(event.noteId)
                    viewModelScope.launch { _effect.send(WorkspaceEffect.NavigateToEdit) }
                }
            }
            is WorkspaceEvent.OnAddNote -> {
                addNote(event.content)
            }
            is WorkspaceEvent.OnDeleteNote -> {
                deleteNote(event.noteId)
            }
            is WorkspaceEvent.OnMoveNote -> {
                moveNote(event.fromIndex, event.toIndex)
            }
            is WorkspaceEvent.OnAssetDrop -> {
                handleAssetDrop(event.noteId, event.uri)
            }
            is WorkspaceEvent.OnUpdateAssetRotation -> {
                updateAssetRotation(event.note, event.assetId, event.degrees)
            }
            is WorkspaceEvent.OnToggleAddSheet -> {
                _uiState.update { it.copy(showAddSheet = event.show) }
            }
        }
    }

    private fun openNote(noteId: String) {
        if (noteId.isBlank()) return
        val currentOpen = repository.openNoteIds.value
        if (!currentOpen.contains(noteId)) {
            repository.saveSession(currentOpen + noteId, noteId)
        } else {
            repository.saveSession(currentOpen, noteId)
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
            openNote(id)
            savedStateHandle["input_buffer"] = ""
        }
    }

    private fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val currentOpen = repository.openNoteIds.value
            if (currentOpen.contains(noteId)) {
                val newList = currentOpen.filter { it != noteId }
                val newSelected = if (repository.selectedNoteId.value == noteId) newList.lastOrNull() else repository.selectedNoteId.value
                repository.saveSession(newList, newSelected)
            }
            repository.deleteNote(noteId)
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
                openNote(id)
                _effect.send(WorkspaceEffect.NavigateToEdit)
            }
        }
    }

    private fun moveNote(fromIndex: Int, toIndex: Int) {
        val currentNotes = uiState.value.notes.toMutableList()
        if (fromIndex !in currentNotes.indices || toIndex !in currentNotes.indices) return
        
        val note = currentNotes.removeAt(fromIndex)
        currentNotes.add(toIndex, note)
        
        val newOrderIndex = if (currentNotes.size == 1) {
            currentNotes[0].orderIndex
        } else if (toIndex == 0) {
            currentNotes[1].orderIndex + 1.0
        } else if (toIndex == currentNotes.size - 1) {
            currentNotes[toIndex - 1].orderIndex - 1.0
        } else {
            (currentNotes[toIndex - 1].orderIndex + currentNotes[toIndex + 1].orderIndex) / 2.0
        }
        
        viewModelScope.launch {
            repository.updateNoteOrder(note.id, newOrderIndex)
        }
    }
}

sealed class WorkspaceEffect {
    object NavigateToEdit : WorkspaceEffect()
}
