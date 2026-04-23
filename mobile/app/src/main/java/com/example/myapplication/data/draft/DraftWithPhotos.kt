package com.example.myapplication.data.draft

import androidx.room.Embedded
import androidx.room.Relation

data class DraftWithPhotos(
    @Embedded val draft: BookDraftEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "draftId"
    )
    val photos: List<DraftPhotoEntity>
)
