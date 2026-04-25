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

    private val _inputBuffer = savedStateHandle.getStateFlow("input_buffer", "")
    val inputBuffer = _inputBuffer

    val notes: StateFlow<List<Note>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val openNoteIds: StateFlow<List<String>> = repository.openNoteIds
    val selectedNoteId: StateFlow<String?> = repository.selectedNoteId

    fun updateInputBuffer(text: String) {
        savedStateHandle["input_buffer"] = text
    }

    fun openNote(noteId: String) {
        val currentOpen = repository.openNoteIds.value
        if (!currentOpen.contains(noteId)) {
            repository.saveSession(currentOpen + noteId, noteId)
        } else {
            repository.saveSession(currentOpen, noteId)
        }
    }

    fun addNote(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val newNote = Note(
                id = id,
                content = content,
                timestamp = System.currentTimeMillis(),
                orderIndex = (notes.value.firstOrNull()?.orderIndex ?: 0.0) + 1.0,
                version = 1
            )
            repository.updateNote(newNote)
            openNote(id)
            updateInputBuffer("")
        }
    }

    fun deleteNote(noteId: String) {
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

    fun updateAssetRotation(note: Note, assetId: String, degrees: Float) {
        viewModelScope.launch {
            val updatedAssets = note.assets.map {
                if (it.id == assetId) it.copy(rotationDegrees = degrees) else it
            }
            val updatedNote = note.copy(assets = updatedAssets)
            repository.updateNote(updatedNote)
        }
    }

    fun handleAssetDrop(noteId: String?, uri: Uri) {
        viewModelScope.launch {
            val base64Data = repository.saveImageAsBase64(uri)
            
            if (noteId != null) {
                // This shouldn't really be called from WorkspaceViewModel for a specific noteId anymore
                // but keeping it for safety or if WorkspaceScreen ever allows dropping on a card
                val note = notes.value.find { it.id == noteId } ?: return@launch
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
                    orderIndex = (notes.value.firstOrNull()?.orderIndex ?: 0.0) + 1.0,
                    version = 1,
                    assets = asset
                )
                repository.updateNote(newNote)
                openNote(id)
            }
        }
    }

    fun moveNote(fromIndex: Int, toIndex: Int) {
        val currentNotes = notes.value.toMutableList()
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

    fun updateProtectionPassword(password: String) {
        repository.setProtectionPassword(password)
    }
}
