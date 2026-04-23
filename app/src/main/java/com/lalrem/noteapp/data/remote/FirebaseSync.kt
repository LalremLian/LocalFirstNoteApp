package com.lalrem.noteapp.data.remote

import com.lalrem.noteapp.domain.model.Note
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseSync @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val notesCollection = firestore.collection("workspaces").document("global").collection("notes")

    fun observeNotes(): Flow<List<Note>> {
        return notesCollection.snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toObject(NoteDto::class.java)?.toDomain(it.id) }
        }
    }

    suspend fun pushNote(note: Note) {
        val dto = NoteDto.fromDomain(note)
        notesCollection.document(note.id).set(dto).await()
    }

    suspend fun deleteNote(noteId: String) {
        notesCollection.document(noteId).delete().await()
    }
}

// DTO for Firestore
data class NoteDto(
    val content: String = "",
    val timestamp: Long = 0,
    val orderIndex: Double = 0.0,
    val version: Int = 1,
    val assets: List<AssetDto> = emptyList()
) {
    fun toDomain(id: String) = Note(
        id = id,
        content = content,
        timestamp = timestamp,
        orderIndex = orderIndex,
        version = version,
        assets = assets.map { it.toDomain(id) }
    )

    companion object {
        fun fromDomain(note: Note) = NoteDto(
            content = note.content,
            timestamp = note.timestamp,
            orderIndex = note.orderIndex,
            version = note.version,
            assets = note.assets.map { AssetDto.fromDomain(it) }
        )
    }
}

data class AssetDto(
    val id: String = "",
    val url: String = "",
    val rotationDegrees: Float = 0f,
    val posX: Float = 0f,
    val posY: Float = 0f
) {
    fun toDomain(noteId: String) = com.lalrem.noteapp.domain.model.Asset(
        id = id,
        noteId = noteId,
        url = url,
        rotationDegrees = rotationDegrees,
        posX = posX,
        posY = posY
    )

    companion object {
        fun fromDomain(asset: com.lalrem.noteapp.domain.model.Asset) = AssetDto(
            id = asset.id,
            url = asset.url,
            rotationDegrees = asset.rotationDegrees,
            posX = asset.posX,
            posY = asset.posY
        )
    }
}
