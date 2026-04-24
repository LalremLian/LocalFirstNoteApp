package com.lalrem.noteapp.data.remote

import com.lalrem.noteapp.domain.model.Note
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import com.lalrem.noteapp.data.remote.dto.NoteDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseSync @Inject constructor(
    firebaseFireStore: FirebaseFirestore
) {
    private val notesCollection = firebaseFireStore
        .collection("workspaces")
        .document("global")
        .collection("notes")

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



