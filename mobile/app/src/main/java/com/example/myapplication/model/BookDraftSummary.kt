package com.example.myapplication.model

data class BookDraftSummary(
    val id: String,
    val ownerType: DraftOwnerType,
    val ownerUserId: Long?,
    val title: String?,
    val updatedAt: Long,
    val photoCount: Int,
    val validPhotoCount: Int,
    val coverUriString: String?,
    val coverDisplayName: String?
)
