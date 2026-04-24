package com.example.myapplication.data.draft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Transaction
    @Query("SELECT * FROM drafts WHERE ownerType = 'GUEST' OR ownerUserId = :userId ORDER BY updatedAt DESC")
    fun observeVisibleDrafts(userId: Long?): Flow<List<DraftWithPhotos>>

    @Transaction
    @Query("SELECT * FROM drafts WHERE id = :draftId AND (ownerType = 'GUEST' OR ownerUserId = :userId) LIMIT 1")
    fun observeVisibleDraft(draftId: String, userId: Long?): Flow<DraftWithPhotos?>

    @Transaction
    @Query("SELECT * FROM drafts WHERE id = :draftId LIMIT 1")
    suspend fun getDraft(draftId: String): DraftWithPhotos?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: BookDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<DraftPhotoEntity>)

    @Update
    suspend fun updatePhotos(photos: List<DraftPhotoEntity>)

    @Query("DELETE FROM draft_photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)

    @Query("DELETE FROM drafts WHERE id = :draftId")
    suspend fun deleteDraftById(draftId: String)

    @Query("SELECT COUNT(*) FROM draft_photos WHERE draftId = :draftId")
    suspend fun countPhotos(draftId: String): Int

    @Query("UPDATE drafts SET updatedAt = :timestamp, lastOpenedAt = :timestamp WHERE id = :draftId")
    suspend fun touchDraft(draftId: String, timestamp: Long)

    @Query("UPDATE drafts SET title = :title, updatedAt = :timestamp WHERE id = :draftId")
    suspend fun updateDraftTitle(draftId: String, title: String?, timestamp: Long)
}
