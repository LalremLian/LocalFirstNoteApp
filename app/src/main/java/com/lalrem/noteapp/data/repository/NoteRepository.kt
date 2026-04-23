package com.lalrem.noteapp.data.repository

import android.app.Application
import android.content.Context
import com.lalrem.noteapp.data.local.NoteDao
import com.lalrem.noteapp.data.local.entity.AssetEntity
import com.lalrem.noteapp.data.local.entity.NoteEntity
import com.lalrem.noteapp.data.local.entity.toDomain
import com.lalrem.noteapp.data.remote.FirebaseSync
import com.lalrem.noteapp.domain.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val firebaseSync: FirebaseSync,
    private val externalScope: CoroutineScope,
    private val app: Application
) {
    private val prefs = app.getSharedPreferences("workspace_session", Context.MODE_PRIVATE)

    fun saveSession(openIds: List<String>, selectedId: String?) {
        prefs.edit().apply {
            putString("open_ids", openIds.joinToString(","))
            putString("selected_id", selectedId)
            apply()
        }
    }

    fun getSession(): Pair<List<String>, String?> {
        val ids = prefs.getString("open_ids", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val selected = prefs.getString("selected_id", null)
        return ids to selected
    }

    fun saveDrafts(drafts: Map<String, String>) {
        val editor = prefs.edit()
        // Save all current drafts
        drafts.forEach { (id, content) ->
            editor.putString("draft_$id", content)
        }
        // Save keys list to know what to clean up or load
        editor.putString("draft_keys", drafts.keys.joinToString(","))
        editor.apply()
    }

    fun getDrafts(): Map<String, String> {
        val keys = prefs.getString("draft_keys", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return keys.associateWith { id -> 
            prefs.getString("draft_$id", "") ?: ""
        }
    }

    fun clearDraft(noteId: String) {
        prefs.edit().remove("draft_$noteId").apply()
        // Also update draft_keys list
        val currentKeys = prefs.getString("draft_keys", "")?.split(",")?.filter { it != noteId } ?: emptyList()
        prefs.edit().putString("draft_keys", currentKeys.joinToString(",")).apply()
    }
    val notes: Flow<List<Note>> = noteDao.getNotesWithAssets().map { list ->
        list.map { noteWithAssets ->
            val domainAssets = noteWithAssets.assets.map { 
                com.lalrem.noteapp.domain.model.Asset(
                    it.id, it.noteId, it.url, it.rotationDegrees, it.posX, it.posY
                )
            }
            noteWithAssets.note.toDomain(domainAssets)
        }
    }

    init {
        // Start background sync from remote to local
        externalScope.launch(Dispatchers.IO) {
            firebaseSync.observeNotes().collectLatest { remoteNotes ->
                reconcile(remoteNotes)
            }
        }
    }

    private suspend fun reconcile(remoteNotes: List<Note>) {
        remoteNotes.forEach { remote ->
            val local = noteDao.getNoteById(remote.id)
            if (local == null) {
                // New note from remote
                saveToLocal(remote, isDirty = false)
            } else if (!local.isDirty && remote.version > local.version) {
                // Remote is newer and local isn't modified
                saveToLocal(remote, isDirty = false)
            } else if (local.isDirty && remote.version > local.version) {
                // CONFLICT! Both local and remote changed
                // In a real app, we'd trigger the Conflict UI here.
                // For now, remote wins but we could preserve local in a 'conflict' table.
            }
        }
    }

    suspend fun updateNote(note: Note) {
        val entity = NoteEntity(note.id, note.content, note.timestamp, note.orderIndex, true, note.version + 1)
        val assets = note.assets.map { 
            AssetEntity(it.id, it.noteId, it.url, it.rotationDegrees, it.posX, it.posY)
        }
        noteDao.upsertNoteWithAssets(entity, assets)
        
        // Push to Firebase
        try {
            firebaseSync.pushNote(note.copy(version = note.version + 1))
            noteDao.insertNote(entity.copy(isDirty = false))
        } catch (e: Exception) {
            // Stay dirty, retry later
        }
    }

    suspend fun updateNoteOrder(noteId: String, newOrderIndex: Double) {
        val entity = noteDao.getNoteById(noteId) ?: return
        val updated = entity.copy(orderIndex = newOrderIndex, isDirty = true, version = entity.version + 1)
        noteDao.insertNote(updated)

        try {
            // Collect the current value of the Flow
            val note = notes.firstOrNull()?.find { it.id == noteId }?.copy(orderIndex = newOrderIndex, version = updated.version)
            if (note != null) {
                firebaseSync.pushNote(note)
                noteDao.insertNote(updated.copy(isDirty = false))
            }
        } catch (e: Exception) {
            // Stay dirty
        }
    }

    suspend fun deleteNote(noteId: String) {
        val entity = noteDao.getNoteById(noteId) ?: return
        noteDao.deleteNote(entity)
        try {
            firebaseSync.deleteNote(noteId)
        } catch (e: Exception) {
            // In a real app, queue for remote deletion
        }
    }

    private suspend fun saveToLocal(note: Note, isDirty: Boolean) {
        val entity = NoteEntity(note.id, note.content, note.timestamp, note.orderIndex, isDirty, note.version)
        val assets = note.assets.map { 
            AssetEntity(it.id, it.noteId, it.url, it.rotationDegrees, it.posX, it.posY)
        }
        noteDao.upsertNoteWithAssets(entity, assets)
    }
}
