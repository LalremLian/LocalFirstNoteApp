package com.lalrem.noteapp.ui.edit

import android.net.Uri
import com.lalrem.noteapp.domain.model.Note

sealed class EditEvent {
    data class OnSelectNote(val noteId: String) : EditEvent()
    data class OnCloseNote(val noteId: String) : EditEvent()
    data class OnUpdateDraft(val noteId: String, val content: String) : EditEvent()
    data class OnSaveNote(val note: Note, val newContent: String) : EditEvent()
    data class OnUpdateAssetRotation(val note: Note, val assetId: String, val degrees: Float) : EditEvent()
    data class OnAssetDrop(val noteId: String?, val uri: Uri) : EditEvent()
    data class OnAddNote(val content: String) : EditEvent()
    data class OnToggleAddSheet(val show: Boolean) : EditEvent()
}
