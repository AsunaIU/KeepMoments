package com.example.myapplication.data.draft

import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.BookDraftSummary
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.model.SelectedPhoto
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DraftRepository(
    private val draftDao: DraftDao
) {

    fun observeVisibleDrafts(userId: Long?): Flow<List<BookDraftSummary>> {
        return draftDao.observeVisibleDrafts(userId).map { drafts ->
            drafts.map { it.toSummary() }
        }
    }

    fun observeVisibleDraft(draftId: String, userId: Long?): Flow<BookDraft?> {
        return draftDao.observeVisibleDraft(draftId, userId).map { draft ->
            draft?.toModel()
        }
    }

    suspend fun getDraft(draftId: String): BookDraft? {
        return draftDao.getDraft(draftId)?.toModel()
    }

    suspend fun createDraft(
        ownerType: DraftOwnerType,
        ownerUserId: Long?,
        photos: List<SelectedPhoto>
    ): String {
        val now = System.currentTimeMillis()
        val draftId = UUID.randomUUID().toString()
        draftDao.insertDraft(
            BookDraftEntity(
                id = draftId,
                ownerType = ownerType.name,
                ownerUserId = ownerUserId,
                title = null,
                bookType = "PHOTOBOOK",
                storyPrompt = null,
                styleId = null,
                tone = null,
                fontSet = null,
                createdAt = now,
                updatedAt = now,
                lastOpenedAt = now
            )
        )
        draftDao.insertPhotos(
            photos.mapIndexed { index, photo ->
                photo.toEntity(draftId = draftId, position = index)
            }
        )
        return draftId
    }

    suspend fun addPhotos(draftId: String, photos: List<SelectedPhoto>) {
        if (photos.isEmpty()) return

        val existingCount = draftDao.countPhotos(draftId)
        draftDao.insertPhotos(
            photos.mapIndexed { index, photo ->
                photo.toEntity(draftId = draftId, position = existingCount + index)
            }
        )
        draftDao.touchDraft(draftId = draftId, timestamp = System.currentTimeMillis())
    }

    suspend fun removePhoto(draftId: String, photoId: String) {
        val draft = draftDao.getDraft(draftId) ?: return
        val remainingPhotos = draft.photos
            .filterNot { it.id == photoId }
            .sortedBy { it.position }
            .mapIndexed { index, photo -> photo.copy(position = index) }

        draftDao.deletePhotoById(photoId)
        if (remainingPhotos.isNotEmpty()) {
            draftDao.updatePhotos(remainingPhotos)
        }
        draftDao.touchDraft(draftId = draftId, timestamp = System.currentTimeMillis())
    }

    suspend fun deleteDraft(draftId: String) {
        draftDao.deleteDraftById(draftId)
    }

    suspend fun touchDraft(draftId: String) {
        draftDao.touchDraft(draftId = draftId, timestamp = System.currentTimeMillis())
    }

    suspend fun updateDraftTitle(draftId: String, title: String?) {
        draftDao.updateDraftTitle(
            draftId = draftId,
            title = title?.trim()?.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis()
        )
    }

    private fun DraftWithPhotos.toModel(): BookDraft {
        return BookDraft(
            id = draft.id,
            ownerType = DraftOwnerType.valueOf(draft.ownerType),
            ownerUserId = draft.ownerUserId,
            title = draft.title,
            createdAt = draft.createdAt,
            updatedAt = draft.updatedAt,
            selectedPhotos = photos
                .sortedBy { it.position }
                .map { photo ->
                    SelectedPhoto(
                        id = photo.id,
                        uriString = photo.uriString,
                        displayName = photo.displayName,
                        mimeType = photo.mimeType,
                        sizeBytes = photo.sizeBytes,
                        width = photo.width,
                        height = photo.height,
                        isValid = photo.isValid,
                        validationMessage = photo.validationMessage,
                        position = photo.position
                    )
                }
        )
    }

    private fun DraftWithPhotos.toSummary(): BookDraftSummary {
        val sortedPhotos = photos.sortedBy { it.position }
        return BookDraftSummary(
            id = draft.id,
            ownerType = DraftOwnerType.valueOf(draft.ownerType),
            ownerUserId = draft.ownerUserId,
            title = draft.title,
            updatedAt = draft.updatedAt,
            photoCount = sortedPhotos.size,
            validPhotoCount = sortedPhotos.count { it.isValid },
            coverUriString = sortedPhotos.firstOrNull()?.uriString,
            coverDisplayName = sortedPhotos.firstOrNull()?.displayName
        )
    }

    private fun SelectedPhoto.toEntity(draftId: String, position: Int): DraftPhotoEntity {
        return DraftPhotoEntity(
            id = id,
            draftId = draftId,
            uriString = uriString,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            position = position,
            isValid = isValid,
            validationMessage = validationMessage
        )
    }
}
