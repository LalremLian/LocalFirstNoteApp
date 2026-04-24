package com.lalrem.noteapp.data.remote.dto

import com.lalrem.noteapp.domain.model.Note

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