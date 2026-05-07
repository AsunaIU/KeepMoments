package com.example.myapplication.data.draft

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookDraftEntity::class, DraftPhotoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DraftDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
}
