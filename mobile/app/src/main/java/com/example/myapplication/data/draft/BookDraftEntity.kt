package com.example.myapplication.data.draft

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drafts",
    indices = [
        Index(value = ["ownerUserId"]),
        Index(value = ["updatedAt"])
    ]
)
data class BookDraftEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerUserId: Long?,
    val title: String?,
    val bookType: String,
    val storyPrompt: String?,
    val styleId: String?,
    val tone: String?,
    val fontSet: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long
)
