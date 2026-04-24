package com.lalrem.noteapp.data.local.entity

import androidx.room.*
import com.lalrem.noteapp.domain.model.Asset
import com.lalrem.noteapp.domain.model.Note

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val content: String,
    val timestamp: Long,
    val orderIndex: Double,
    val isDirty: Boolean,
    val version: Int
)

@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class AssetEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val url: String,
    val rotationDegrees: Float,
    val posX: Float,
    val posY: Float
)

fun NoteEntity.toDomain(assets: List<Asset>): Note = Note(
    id = id,
    content = content,
    timestamp = timestamp,
    orderIndex = orderIndex,
    isDirty = isDirty,
    version = version,
    assets = assets
)

data class NoteWithAssets(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val assets: List<AssetEntity>
)
