package com.example.myapplication.model

data class EditableAlbum(
    val draft: BookDraft,
    val pages: List<AlbumPage>
)

data class AlbumPage(
    val id: String,
    val draftId: String,
    val position: Int,
    val layoutId: String,
    val slots: List<AlbumSlot>,
    val stickers: List<AlbumSticker>,
    val caption: String = ""
) {
    val isComplete: Boolean = slots.all { it.photoId != null }
}

data class AlbumSlot(
    val id: String,
    val pageId: String,
    val slotKey: String,
    val photoId: String?,
    val remotePhotoId: String? = null,
    val caption: String = "",
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f
)

data class AlbumSticker(
    val id: String,
    val pageId: String,
    val sticker: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val zIndex: Int
)

data class PhotoTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)
