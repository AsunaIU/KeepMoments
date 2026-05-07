package com.example.myapplication.data.draft

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_photos",
    foreignKeys = [
        ForeignKey(
            entity = BookDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["draftId"]),
        Index(value = ["position"])
    ]
)
data class DraftPhotoEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val uriString: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val position: Int,
    val isValid: Boolean,
    val validationMessage: String?
)
