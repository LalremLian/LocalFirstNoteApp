package com.lalrem.noteapp.data.local

import androidx.room.*
import com.lalrem.noteapp.data.local.entity.AssetEntity
import com.lalrem.noteapp.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Transaction
    @Query("SELECT * FROM notes ORDER BY orderIndex DESC, timestamp DESC")
    fun getNotesWithAssets(): Flow<List<com.lalrem.noteapp.data.local.entity.NoteWithAssets>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetEntity>)

    @Transaction
    suspend fun upsertNoteWithAssets(note: NoteEntity, assets: List<AssetEntity>) {
        insertNote(note)
        // Clean old assets if necessary or just replace
        assets.forEach { insertAsset(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE notes SET orderIndex = :newOrder WHERE id = :noteId")
    suspend fun updateNoteOrder(noteId: String, newOrder: Double)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?
}
