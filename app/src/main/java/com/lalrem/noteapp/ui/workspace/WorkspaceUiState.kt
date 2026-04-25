package com.lalrem.noteapp.ui.workspace

import com.lalrem.noteapp.domain.model.Note

data class WorkspaceUiState(
    val notes: List<Note> = emptyList(),
    val openNoteIds: List<String> = emptyList(),
    val selectedNoteId: String? = null,
    val inputBuffer: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddSheet: Boolean = false
)
