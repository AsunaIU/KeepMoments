package com.example.myapplication.model

data class BookDraft(
    val id: String,
    val ownerType: DraftOwnerType,
    val ownerUserId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedPhotos: List<SelectedPhoto>
)
