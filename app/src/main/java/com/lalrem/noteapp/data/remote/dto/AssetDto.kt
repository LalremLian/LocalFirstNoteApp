package com.lalrem.noteapp.data.remote.dto

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
