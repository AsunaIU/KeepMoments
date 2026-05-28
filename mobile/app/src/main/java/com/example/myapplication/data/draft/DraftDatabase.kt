package com.example.myapplication.data.draft

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myapplication.data.album.AlbumPageEntity
import com.example.myapplication.data.album.AlbumSlotEntity
import com.example.myapplication.data.album.AlbumStickerEntity
import com.example.myapplication.data.album.AlbumDao

@Database(
    entities = [
        BookDraftEntity::class,
        DraftPhotoEntity::class,
        AlbumPageEntity::class,
        AlbumSlotEntity::class,
        AlbumStickerEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class DraftDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun albumDao(): AlbumDao
}
