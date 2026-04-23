package com.lalrem.noteapp.domain.model

data class Note(
    val id: String,
    val content: String,
    val timestamp: Long,
    val orderIndex: Double,
    val assets: List<Asset> = emptyList(),
    val isDirty: Boolean = false,
    val version: Int = 1
)

data class Asset(
    val id: String,
    val noteId: String,
    val url: String,
    val rotationDegrees: Float = 0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val type: AssetType = AssetType.IMAGE
)

enum class AssetType {
    IMAGE
}

data class MergeConflict(
    val noteId: String,
    val localNote: Note,
    val remoteNote: Note
)
