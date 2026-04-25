package com.lalrem.noteapp.data.repository

import android.app.Application
import android.content.Context
import android.net.Uri
import com.lalrem.noteapp.data.local.NoteDao
import com.lalrem.noteapp.data.local.entity.AssetEntity
import com.lalrem.noteapp.data.local.entity.NoteEntity
import com.lalrem.noteapp.data.local.entity.toDomain
import com.lalrem.noteapp.data.remote.FirebaseSync
import com.lalrem.noteapp.data.util.ProtectionManager
import com.lalrem.noteapp.domain.model.Asset
import com.lalrem.noteapp.domain.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val firebaseSync: FirebaseSync,
    private val protectionManager: ProtectionManager,
    private val externalScope: CoroutineScope,
    private val app: Application
) {
    private val prefs = app.getSharedPreferences("workspace_session", Context.MODE_PRIVATE)

    private val _openNoteIds = MutableStateFlow(getSessionFromPrefs().first)
    val openNoteIds: StateFlow<List<String>> = _openNoteIds

    private val _selectedNoteId = MutableStateFlow(getSessionFromPrefs().second)
    val selectedNoteId: StateFlow<String?> = _selectedNoteId

    private val _drafts = MutableStateFlow(getDraftsFromPrefs())
    val drafts: StateFlow<Map<String, String>> = _drafts

    fun saveSession(openIds: List<String>, selectedId: String?) {
        prefs.edit().apply {
            putString("open_ids", openIds.joinToString(","))
            putString("selected_id", selectedId)
            apply()
        }
        _openNoteIds.value = openIds
        _selectedNoteId.value = selectedId
    }

    private fun getSessionFromPrefs(): Pair<List<String>, String?> {
        val ids = prefs.getString("open_ids", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val selected = prefs.getString("selected_id", null)
        return ids to selected
    }

    fun getSession(): Pair<List<String>, String?> = getSessionFromPrefs()

    fun saveDrafts(drafts: Map<String, String>) {
        val editor = prefs.edit()
        drafts.forEach { (id, content) ->
            editor.putString("draft_$id", content)
        }
        editor.putString("draft_keys", drafts.keys.joinToString(","))
        editor.apply()
        _drafts.value = drafts
    }

    private fun getDraftsFromPrefs(): Map<String, String> {
        val keys = prefs.getString("draft_keys", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return keys.associateWith { id -> 
            prefs.getString("draft_$id", "") ?: ""
        }
    }

    fun getDrafts(): Map<String, String> = getDraftsFromPrefs()

    fun clearDraft(noteId: String) {
        prefs.edit().remove("draft_$noteId").apply()
        val currentKeys = prefs.getString("draft_keys", "")?.split(",")?.filter { it != noteId } ?: emptyList()
        prefs.edit().putString("draft_keys", currentKeys.joinToString(",")).apply()
        _drafts.value = _drafts.value.toMutableMap().apply { remove(noteId) }
    }

    fun setProtectionPassword(password: String) {
        protectionManager.setPassword(password)
        // Refresh notes flow by triggering a reload or just let the observer handle it
        // In a more complex app, we would re-encrypt existing local data here.
    }
    val notes: Flow<List<Note>> = noteDao.getNotesWithAssets().map { list ->
        list.map { noteWithAssets ->
            val domainAssets = noteWithAssets.assets.map { 
                Asset(
                    it.id, it.noteId, protectionManager.decrypt(it.url), it.rotationDegrees, it.posX, it.posY
                )
            }
            val domainNote = noteWithAssets.note.toDomain(domainAssets)
            domainNote.copy(content = protectionManager.decrypt(domainNote.content))
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
        val remoteIds = remoteNotes.map { it.id }.toSet()
        val localEntities = noteDao.getAllNotes()

        // 1. Handle Deletions: Remove local notes that are no longer in remote
        // But only if they are not "dirty" (unsynced local changes)
        localEntities.forEach { local ->
            if (!remoteIds.contains(local.id) && !local.isDirty) {
                noteDao.deleteNote(local)
            }
        }

        // 2. Handle Additions/Updates
        remoteNotes.forEach { remote ->
            val local = noteDao.getNoteById(remote.id)
            if (local == null) {
                // New note from remote
                saveToLocal(remote, isDirty = false)
            } else if (!local.isDirty && remote.version > local.version) {
                // Only overwrite if the remote version actually has assets OR if local has none
                // This prevents race conditions where Firestore emits an old/empty version temporarily
                val localWithAssets = noteDao.getNoteWithAssetsById(remote.id)
                if (remote.assets.isNotEmpty() || (localWithAssets?.assets?.isEmpty() == true)) {
                    saveToLocal(remote, isDirty = false)
                }
            } else if (local.isDirty && remote.version > local.version) {
                // CONFLICT! Both local and remote changed
                // In a real app, we'd trigger the Conflict UI here.
                // For now, remote wins but we could preserve local in a 'conflict' table.
            }
        }
    }

    suspend fun updateNote(note: Note) {
        val encryptedContent = protectionManager.encrypt(note.content)
        val encryptedAssets = note.assets.map {
            it.copy(url = protectionManager.encrypt(it.url))
        }
        
        val entity = NoteEntity(note.id, encryptedContent, note.timestamp, note.orderIndex, true, note.version + 1)
        val assets = encryptedAssets.map { 
            AssetEntity(it.id, it.noteId, it.url, it.rotationDegrees, it.posX, it.posY)
        }
        noteDao.upsertNoteWithAssets(entity, assets)
        
        // Push to Firebase
        try {
            firebaseSync.pushNote(note.copy(
                content = encryptedContent, 
                version = note.version + 1,
                assets = encryptedAssets
            ))
            noteDao.upsertNoteWithAssets(entity.copy(isDirty = false), assets)
        } catch (e: Exception) {
            // Stay dirty, retry later
        }
    }

    suspend fun updateNoteOrder(noteId: String, newOrderIndex: Double) {
        val entity = noteDao.getNoteById(noteId) ?: return
        val updated = entity.copy(orderIndex = newOrderIndex, isDirty = true, version = entity.version + 1)
        val noteWithAssets = noteDao.getNoteWithAssetsById(noteId) ?: return
        
        noteDao.upsertNoteWithAssets(updated, noteWithAssets.assets)

        try {
            val domainAssets = noteWithAssets.assets.map { 
                Asset(it.id, it.noteId, it.url, it.rotationDegrees, it.posX, it.posY)
            }
            val noteToPush = Note(
                id = noteId,
                content = updated.content,
                timestamp = updated.timestamp,
                orderIndex = updated.orderIndex,
                version = updated.version,
                assets = domainAssets
            )
            firebaseSync.pushNote(noteToPush)
            noteDao.upsertNoteWithAssets(updated.copy(isDirty = false), noteWithAssets.assets)
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
        // The content from remote might already be encrypted if pushed from another device
        // But for consistency, we treat the domain Note content as the ground truth.
        // If we want E2EE, we should NOT decrypt before pushing, and NOT encrypt after pulling.
        // Currently, updateNote encrypts before pushing. So reconcile gets encrypted notes.
        val entity = NoteEntity(note.id, note.content, note.timestamp, note.orderIndex, isDirty, note.version)
        val assets = note.assets.map { 
            AssetEntity(it.id, it.noteId, it.url, it.rotationDegrees, it.posX, it.posY)
        }
        noteDao.upsertNoteWithAssets(entity, assets)
    }

    suspend fun saveImageAsBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = app.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return@withContext null

            val maxDim = 800
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDim || height > maxDim) {
                maxDim.toFloat() / width.coerceAtLeast(height)
            } else 1f
            
            val scaledBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    originalBitmap, 
                    (width * scale).toInt(), 
                    (height * scale).toInt(), 
                    true
                )
            } else {
                originalBitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            return@withContext "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
