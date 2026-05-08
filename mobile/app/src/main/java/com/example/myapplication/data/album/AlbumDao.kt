package com.example.myapplication.data.album

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM drafts WHERE id = :draftId) +
            (SELECT COUNT(*) FROM draft_photos WHERE draftId = :draftId) +
            (SELECT COUNT(*) FROM album_pages WHERE draftId = :draftId) +
            (SELECT COUNT(*) FROM album_slots WHERE pageId IN (SELECT id FROM album_pages WHERE draftId = :draftId)) +
            (SELECT COUNT(*) FROM album_stickers WHERE pageId IN (SELECT id FROM album_pages WHERE draftId = :draftId))
        """
    )
    fun observeAlbumInvalidations(draftId: String): Flow<Int>

    @Query("SELECT * FROM album_pages WHERE draftId = :draftId ORDER BY position ASC")
    fun observePages(draftId: String): Flow<List<AlbumPageEntity>>

    @Query("SELECT * FROM album_pages WHERE draftId = :draftId ORDER BY position ASC")
    suspend fun getPages(draftId: String): List<AlbumPageEntity>

    @Query("SELECT * FROM album_pages WHERE id = :pageId LIMIT 1")
    suspend fun getPage(pageId: String): AlbumPageEntity?

    @Query("SELECT * FROM album_slots WHERE pageId IN (:pageIds)")
    suspend fun getSlots(pageIds: List<String>): List<AlbumSlotEntity>

    @Query("SELECT * FROM album_stickers WHERE pageId IN (:pageIds) ORDER BY zIndex ASC")
    suspend fun getStickers(pageIds: List<String>): List<AlbumStickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<AlbumPageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<AlbumSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<AlbumStickerEntity>)

    @Update
    suspend fun updatePage(page: AlbumPageEntity)

    @Query("DELETE FROM album_pages WHERE draftId = :draftId")
    suspend fun deleteAlbum(draftId: String)

    @Query("DELETE FROM album_slots WHERE pageId = :pageId")
    suspend fun deleteSlotsForPage(pageId: String)

    @Query("DELETE FROM album_stickers WHERE pageId = :pageId")
    suspend fun deleteStickersForPage(pageId: String)

    @Transaction
    suspend fun replaceAlbum(
        draftId: String,
        pages: List<AlbumPageEntity>,
        slots: List<AlbumSlotEntity>
    ) {
        deleteAlbum(draftId)
        insertPages(pages)
        insertSlots(slots)
    }

    @Transaction
    suspend fun replacePage(
        page: AlbumPageEntity,
        slots: List<AlbumSlotEntity>,
        stickers: List<AlbumStickerEntity>
    ) {
        updatePage(page)
        deleteSlotsForPage(page.id)
        deleteStickersForPage(page.id)
        insertSlots(slots)
        if (stickers.isNotEmpty()) {
            insertStickers(stickers)
        }
    }
}
