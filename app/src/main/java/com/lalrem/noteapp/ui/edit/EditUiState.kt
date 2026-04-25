package com.lalrem.noteapp.ui.edit

import com.lalrem.noteapp.domain.model.Note

data class EditUiState(
    val notes: List<Note> = emptyList(),
    val openNoteIds: List<String> = emptyList(),
    val selectedNoteId: String? = null,
    val drafts: Map<String, String> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddSheet: Boolean = false
)
