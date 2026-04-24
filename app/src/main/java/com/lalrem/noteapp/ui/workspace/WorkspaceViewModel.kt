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

    // Persistent UI state for process death recovery
    private val _inputBuffer = savedStateHandle.getStateFlow("input_buffer", "")
    val inputBuffer = _inputBuffer

    val notes: StateFlow<List<Note>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateInputBuffer(text: String) {
        savedStateHandle["input_buffer"] = text
    }

    // Persistent Session State (mirrored to repository for hard-close recovery)
    private val _openNoteIds = savedStateHandle.getStateFlow<List<String>>("open_note_ids", 
        repository.getSession().first // Initialize from deep storage
    )
    val openNoteIds: StateFlow<List<String>> = _openNoteIds

    private val _selectedNoteId = savedStateHandle.getStateFlow<String?>("selected_note_id", 
        repository.getSession().second // Initialize from deep storage
    )
    val selectedNoteId: StateFlow<String?> = _selectedNoteId

    private fun saveLocalSession() {
        repository.saveSession(_openNoteIds.value, _selectedNoteId.value)
    }

    // Map of noteId -> current unsaved content (mirrored for deep recovery)
    private val _drafts = savedStateHandle.getStateFlow<Map<String, String>>("draft_contents", 
        repository.getDrafts() // Initialize from deep storage
    )
    val drafts: StateFlow<Map<String, String>> = _drafts

    fun openNote(noteId: String) {
        if (!_openNoteIds.value.contains(noteId)) {
            savedStateHandle["open_note_ids"] = _openNoteIds.value + noteId
        }
        savedStateHandle["selected_note_id"] = noteId
        saveLocalSession()
    }

    fun closeNote(noteId: String) {
        val newList = _openNoteIds.value.filter { it != noteId }
        savedStateHandle["open_note_ids"] = newList
        // Clean up draft permanently
        val newDrafts = _drafts.value.toMutableMap().apply { remove(noteId) }
        savedStateHandle["draft_contents"] = newDrafts
        repository.clearDraft(noteId)
        
        if (_selectedNoteId.value == noteId) {
            savedStateHandle["selected_note_id"] = newList.lastOrNull()
        }
        saveLocalSession()
    }

    fun updateDraft(noteId: String, content: String) {
        val newDrafts = _drafts.value.toMutableMap().apply { put(noteId, content) }
        savedStateHandle["draft_contents"] = newDrafts
        repository.saveDrafts(newDrafts)
    }

    // Helper to check if a specific note has modifications
    fun isModified(note: Note): Boolean {
        val draft = _drafts.value[note.id]
        return draft != null && draft != note.content
    }

    val hasUnsavedChanges: StateFlow<Boolean> = combine(notes, _openNoteIds, _drafts) { allNotes, openIds, currentDrafts ->
        openIds.any { id ->
            val note = allNotes.find { it.id == id }
            val draft = currentDrafts[id]
            note != null && draft != null && draft != note.content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectNote(noteId: String) {
        if (hasUnsavedChanges.value) return // Block if unsaved changes exist
        savedStateHandle["selected_note_id"] = noteId
        saveLocalSession()
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
            openNote(id) // Open it immediately as a tab
            updateInputBuffer("")
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            closeNote(noteId)
            repository.deleteNote(noteId)
        }
    }

    fun updateNoteContent(note: Note, newContent: String) {
        viewModelScope.launch {
            repository.updateNote(note.copy(content = newContent, timestamp = System.currentTimeMillis()))
            // Remove draft after successful save
            val newDrafts = _drafts.value.toMutableMap().apply { remove(note.id) }
            savedStateHandle["draft_contents"] = newDrafts
            repository.clearDraft(note.id)
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
                // Add to existing note
                val note = notes.value.find { it.id == noteId } ?: return@launch
                if (base64Data != null) {
                    val newAsset = Asset(UUID.randomUUID().toString(), noteId, base64Data)
                    repository.updateNote(note.copy(assets = note.assets + newAsset))
                } else {
                    repository.updateNote(note.copy(content = note.content + "\n[Failed to add image]"))
                }
            } else {
                // Create new note for the asset
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
}
