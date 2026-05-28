package com.example.myapplication.data.album

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.draft.BookDraftEntity
import com.example.myapplication.data.draft.DraftPhotoEntity

@Entity(
    tableName = "album_pages",
    foreignKeys = [
        ForeignKey(
            entity = BookDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["draftId"]), Index(value = ["position"])]
)
data class AlbumPageEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val position: Int,
    val layoutId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "album_slots",
    foreignKeys = [
        ForeignKey(
            entity = AlbumPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DraftPhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["pageId"]), Index(value = ["photoId"])]
)
data class AlbumSlotEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val slotKey: String,
    val photoId: String?,
    val remotePhotoId: String?,
    val caption: String,
    val cropScale: Float,
    val cropOffsetX: Float,
    val cropOffsetY: Float
)

@Entity(
    tableName = "album_stickers",
    foreignKeys = [
        ForeignKey(
            entity = AlbumPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"]), Index(value = ["zIndex"])]
)
data class AlbumStickerEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val sticker: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val zIndex: Int
)
