package com.example.myapplication.testutil.factories

import com.example.myapplication.data.auth.AuthResponse
import com.example.myapplication.model.AuthSession
import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.BookPage
import com.example.myapplication.model.BookSlot
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.model.FilledTemplate
import com.example.myapplication.model.RenderedBook
import com.example.myapplication.model.SelectedPhoto
import java.util.UUID

fun authSession(
    accessToken: String = "access",
    refreshToken: String = "refresh",
    tokenType: String = "Bearer",
    email: String = "user@example.com",
    userId: Long = 1L
) = AuthSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    tokenType = tokenType,
    accessExpiresAt = null,
    refreshExpiresAt = null,
    email = email,
    userId = userId
)

fun authResponse(
    accessToken: String = "access",
    refreshToken: String = "refresh",
    email: String = "user@example.com",
    userId: Long = 1L
) = AuthResponse(
    accessToken = accessToken,
    refreshToken = refreshToken,
    tokenType = "Bearer",
    accessExpiresAt = null,
    refreshExpiresAt = null,
    email = email,
    userId = userId
)

fun selectedPhoto(
    id: String = UUID.randomUUID().toString(),
    uriString: String = "file:///photo/$id.jpg",
    isValid: Boolean = true,
    position: Int = 0
) = SelectedPhoto(
    id = id,
    uriString = uriString,
    displayName = "$id.jpg",
    mimeType = "image/jpeg",
    sizeBytes = 1_000L,
    width = 1200,
    height = 900,
    isValid = isValid,
    validationMessage = if (isValid) null else "invalid",
    position = position
)

fun bookDraft(
    id: String = "draft-1",
    ownerType: DraftOwnerType = DraftOwnerType.USER,
    ownerUserId: Long? = 1L,
    storyPrompt: String? = null,
    photos: List<SelectedPhoto> = listOf(selectedPhoto())
) = BookDraft(
    id = id,
    ownerType = ownerType,
    ownerUserId = ownerUserId,
    title = null,
    storyPrompt = storyPrompt,
    generateCaptions = true,
    createdAt = 0L,
    updatedAt = 0L,
    selectedPhotos = photos
)

fun renderedBook(draftId: String = "draft-1") = RenderedBook(
    draftId = draftId,
    templateId = "template-1",
    filledTemplate = FilledTemplate(
        id = "template-1",
        pages = listOf(
            BookPage(
                id = "page-1",
                slots = listOf(BookSlot(id = "slot-1", photoId = "photo-1"))
            )
        )
    )
)
