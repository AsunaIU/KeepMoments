package com.example.myapplication.data.album

import com.example.myapplication.data.draft.DraftDao
import com.example.myapplication.data.draft.DraftPhotoEntity
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.AlbumPage
import com.example.myapplication.model.AlbumSlot
import com.example.myapplication.model.AlbumSticker
import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.model.EditableAlbum
import com.example.myapplication.model.RenderedBook
import com.example.myapplication.model.SelectedPhoto
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlbumRepository(
    private val draftDao: DraftDao,
    private val albumDao: AlbumDao
) {
    fun observeAlbum(draftId: String): Flow<EditableAlbum?> {
        return albumDao.observeAlbumInvalidations(draftId).map {
            getAlbum(draftId)
        }
    }

    suspend fun getAlbum(draftId: String): EditableAlbum? {
        val draftWithPhotos = draftDao.getDraft(draftId) ?: return null
        val pages = albumDao.getPages(draftId)
        if (pages.isEmpty()) return null
        val pageIds = pages.map { it.id }
        val slotsByPage = albumDao.getSlots(pageIds).groupBy { it.pageId }
        val stickersByPage = albumDao.getStickers(pageIds).groupBy { it.pageId }
        return EditableAlbum(
            draft = draftWithPhotos.toDraftModel(),
            pages = pages.map { page ->
                AlbumPage(
                    id = page.id,
                    draftId = page.draftId,
                    position = page.position,
                    layoutId = page.layoutId,
                    slots = slotsByPage[page.id].orEmpty().sortedBy { slot -> slot.slotKey }.map { it.toModel() },
                    stickers = stickersByPage[page.id].orEmpty().sortedBy { sticker -> sticker.zIndex }.map { it.toModel() }
                )
            }
        )
    }

    suspend fun createInitialAlbumFromRenderedBook(book: RenderedBook) {
        val draft = draftDao.getDraft(book.draftId) ?: return
        val photosById = draft.photos.associateBy { it.id }
        val storyPrompt = draft.draft.storyPrompt.orEmpty()
        val generateCaptions = draft.draft.generateCaptions
        val now = System.currentTimeMillis()
        val pageEntities = mutableListOf<AlbumPageEntity>()
        val slotEntities = mutableListOf<AlbumSlotEntity>()

        book.filledTemplate.pages.forEachIndexed { index, page ->
            val firstSlot = page.slots.firstOrNull()
            val firstPhoto = firstSlot?.photoId?.let(photosById::get)
            val selectedPhoto = firstPhoto?.toSelectedPhoto()
            val pageId = page.id.ifBlank { "page-${index + 1}" }
            val layoutId = AlbumLayouts.defaultSingleLayout(selectedPhoto)
            pageEntities += AlbumPageEntity(
                id = pageId,
                draftId = book.draftId,
                position = index,
                layoutId = layoutId,
                createdAt = now,
                updatedAt = now
            )
            AlbumLayouts.require(layoutId).slots.forEach { slotSpec ->
                slotEntities += AlbumSlotEntity(
                    id = "$pageId-${slotSpec.key}",
                    pageId = pageId,
                    slotKey = slotSpec.key,
                    photoId = firstPhoto?.id,
                    remotePhotoId = firstSlot?.remotePhotoId,
                    caption = if (generateCaptions) captionFor(index = index, storyPrompt = storyPrompt) else "",
                    cropScale = 1f,
                    cropOffsetX = 0f,
                    cropOffsetY = 0f
                )
            }
        }

        albumDao.replaceAlbum(
            draftId = book.draftId,
            pages = pageEntities,
            slots = slotEntities
        )
        draftDao.touchDraft(draftId = book.draftId, timestamp = now)
    }

    suspend fun ensureDemoAlbumForDraft(draftId: String): Boolean {
        if (getAlbum(draftId) != null) return true
        val draft = draftDao.getDraft(draftId) ?: return false
        val photos = draft.photos.sortedBy { it.position }.filter { it.isValid }
        if (photos.isEmpty()) return false
        val storyPrompt = draft.draft.storyPrompt.orEmpty()
        val generateCaptions = draft.draft.generateCaptions

        val now = System.currentTimeMillis()
        val pageEntities = mutableListOf<AlbumPageEntity>()
        val slotEntities = mutableListOf<AlbumSlotEntity>()
        photos.forEachIndexed { index, photo ->
            val selectedPhoto = photo.toSelectedPhoto()
            val pageId = "demo-page-${index + 1}"
            val layoutId = AlbumLayouts.defaultSingleLayout(selectedPhoto)
            pageEntities += AlbumPageEntity(
                id = pageId,
                draftId = draftId,
                position = index,
                layoutId = layoutId,
                createdAt = now,
                updatedAt = now
            )
            AlbumLayouts.require(layoutId).slots.forEach { slotSpec ->
                slotEntities += AlbumSlotEntity(
                    id = "$pageId-${slotSpec.key}",
                    pageId = pageId,
                    slotKey = slotSpec.key,
                    photoId = photo.id,
                    remotePhotoId = null,
                    caption = if (generateCaptions) captionFor(index = index, storyPrompt = storyPrompt) else "",
                    cropScale = 1f,
                    cropOffsetX = 0f,
                    cropOffsetY = 0f
                )
            }
        }
        albumDao.replaceAlbum(
            draftId = draftId,
            pages = pageEntities,
            slots = slotEntities
        )
        draftDao.touchDraft(draftId = draftId, timestamp = now)
        return true
    }

    suspend fun savePage(page: AlbumPage) {
        val now = System.currentTimeMillis()
        val existing = albumDao.getPage(page.id)
        albumDao.replacePage(
            page = AlbumPageEntity(
                id = page.id,
                draftId = page.draftId,
                position = page.position,
                layoutId = page.layoutId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            ),
            slots = page.slots.map { it.toEntity() },
            stickers = page.stickers.map { it.toEntity() }
        )
        draftDao.touchDraft(draftId = page.draftId, timestamp = now)
    }

    fun buildPageWithLayout(page: AlbumPage, layoutId: String): AlbumPage {
        val layout = AlbumLayouts.require(layoutId)
        val existingSlots = page.slots.filter { it.photoId != null }
        val existingByKey = page.slots.associateBy { it.slotKey }
        return page.copy(
            layoutId = layoutId,
            slots = layout.slots.mapIndexed { index, spec ->
                val previous = existingByKey[spec.key]
                val fallback = existingSlots.getOrNull(index)
                AlbumSlot(
                    id = "${page.id}-${spec.key}",
                    pageId = page.id,
                    slotKey = spec.key,
                    photoId = previous?.photoId ?: fallback?.photoId,
                    remotePhotoId = previous?.remotePhotoId ?: fallback?.remotePhotoId,
                    caption = previous?.caption.orEmpty(),
                    cropScale = previous?.cropScale ?: 1f,
                    cropOffsetX = previous?.cropOffsetX ?: 0f,
                    cropOffsetY = previous?.cropOffsetY ?: 0f
                )
            }
        )
    }

    fun newSticker(pageId: String, sticker: String, zIndex: Int): AlbumSticker {
        return AlbumSticker(
            id = UUID.randomUUID().toString(),
            pageId = pageId,
            sticker = sticker,
            x = 0.5f,
            y = 0.5f,
            scale = 1f,
            rotation = 0f,
            zIndex = zIndex
        )
    }

    private fun AlbumSlotEntity.toModel() = AlbumSlot(
        id = id,
        pageId = pageId,
        slotKey = slotKey,
        photoId = photoId,
        remotePhotoId = remotePhotoId,
        caption = caption,
        cropScale = cropScale,
        cropOffsetX = cropOffsetX,
        cropOffsetY = cropOffsetY
    )

    private fun AlbumSlot.toEntity() = AlbumSlotEntity(
        id = id,
        pageId = pageId,
        slotKey = slotKey,
        photoId = photoId,
        remotePhotoId = remotePhotoId,
        caption = caption,
        cropScale = cropScale,
        cropOffsetX = cropOffsetX,
        cropOffsetY = cropOffsetY
    )

    private fun AlbumStickerEntity.toModel() = AlbumSticker(
        id = id,
        pageId = pageId,
        sticker = sticker,
        x = x,
        y = y,
        scale = scale,
        rotation = rotation,
        zIndex = zIndex
    )

    private fun AlbumSticker.toEntity() = AlbumStickerEntity(
        id = id,
        pageId = pageId,
        sticker = sticker,
        x = x,
        y = y,
        scale = scale,
        rotation = rotation,
        zIndex = zIndex
    )

    private fun com.example.myapplication.data.draft.DraftWithPhotos.toDraftModel(): BookDraft {
        return BookDraft(
            id = draft.id,
            ownerType = DraftOwnerType.fromStorageValue(draft.ownerType),
            ownerUserId = draft.ownerUserId,
            title = draft.title,
            storyPrompt = draft.storyPrompt,
            generateCaptions = draft.generateCaptions,
            createdAt = draft.createdAt,
            updatedAt = draft.updatedAt,
            selectedPhotos = photos.sortedBy { it.position }.map { it.toSelectedPhoto() }
        )
    }

    private fun DraftPhotoEntity.toSelectedPhoto(): SelectedPhoto = SelectedPhoto(
        id = id,
        uriString = uriString,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        isValid = isValid,
        validationMessage = validationMessage,
        position = position
    )

    private fun captionFor(index: Int, storyPrompt: String): String {
        if (index == 0 && storyPrompt.isNotBlank()) {
            return storyPrompt.trim().take(80)
        }
        return defaultCaptions[index % defaultCaptions.size]
    }

    private companion object {
        val defaultCaptions = listOf(
            "Первый день путешествия",
            "Момент, который хочется сохранить",
            "Прекрасный день с близкими людьми",
            "Прогулка в солнечный день",
            "Новая страница нашей истории"
        )
    }
}
