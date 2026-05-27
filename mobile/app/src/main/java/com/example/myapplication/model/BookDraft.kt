package com.example.myapplication.model

data class BookDraft(
    val id: String,
    val ownerType: DraftOwnerType,
    val ownerUserId: Long?,
    val title: String?,
    val storyPrompt: String?,
    val generateCaptions: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedPhotos: List<SelectedPhoto>
)
