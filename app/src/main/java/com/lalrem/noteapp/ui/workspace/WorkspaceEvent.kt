package com.lalrem.noteapp.ui.workspace

import android.net.Uri
import com.lalrem.noteapp.domain.model.Note

sealed class WorkspaceEvent {
    data class OnInputBufferChange(val text: String) : WorkspaceEvent()
    data class OnOpenNote(val noteId: String) : WorkspaceEvent()
    data class OnAddNote(val content: String) : WorkspaceEvent()
    data class OnDeleteNote(val noteId: String) : WorkspaceEvent()
    data class OnMoveNote(val fromIndex: Int, val toIndex: Int) : WorkspaceEvent()
    data class OnAssetDrop(val noteId: String?, val uri: Uri) : WorkspaceEvent()
    data class OnUpdateAssetRotation(val note: Note, val assetId: String, val degrees: Float) : WorkspaceEvent()
    data class OnToggleAddSheet(val show: Boolean) : WorkspaceEvent()
}
