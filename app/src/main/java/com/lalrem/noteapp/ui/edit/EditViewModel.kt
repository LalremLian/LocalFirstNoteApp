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

    val notes: StateFlow<List<Note>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val openNoteIds: StateFlow<List<String>> = repository.openNoteIds
    val selectedNoteId: StateFlow<String?> = repository.selectedNoteId
    val drafts: StateFlow<Map<String, String>> = repository.drafts

    val inputBuffer = repository.openNoteIds.map { "" } // Placeholder if needed

    val hasUnsavedChanges: StateFlow<Boolean> = combine(notes, openNoteIds, drafts) { allNotes, openIds, currentDrafts ->
        openIds.any { id ->
            val note = allNotes.find { it.id == id }
            val draft = currentDrafts[id]
            note != null && draft != null && draft != note.content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectNote(noteId: String) {
        val currentOpen = openNoteIds.value
        if (!currentOpen.contains(noteId)) {
            repository.saveSession(currentOpen + noteId, noteId)
        } else {
            repository.saveSession(currentOpen, noteId)
        }
    }

    fun closeNote(noteId: String) {
        val newList = openNoteIds.value.filter { it != noteId }
        val newSelectedId = if (selectedNoteId.value == noteId) newList.lastOrNull() else selectedNoteId.value
        repository.saveSession(newList, newSelectedId)
        repository.clearDraft(noteId)
    }

    fun updateDraft(noteId: String, content: String) {
        val newDrafts = drafts.value.toMutableMap().apply { put(noteId, content) }
        repository.saveDrafts(newDrafts)
    }

    fun updateNoteContent(note: Note, newContent: String) {
        viewModelScope.launch {
            repository.updateNote(note.copy(content = newContent, timestamp = System.currentTimeMillis()))
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
                repository.saveSession(openNoteIds.value + id, id)
            }
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
            repository.saveSession(openNoteIds.value + id, id)
        }
    }
}
