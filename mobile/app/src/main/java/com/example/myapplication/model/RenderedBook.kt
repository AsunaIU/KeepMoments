package com.example.myapplication.model

data class RenderedBook(
    val draftId: String,
    val templateId: String,
    val filledTemplate: FilledTemplate
)

data class FilledTemplate(
    val id: String,
    val pages: List<BookPage>
)

data class BookPage(
    val id: String,
    val slots: List<BookSlot>,
    val layoutId: String = id,
    val caption: String = ""
)

data class BookSlot(
    val id: String,
    val photoId: String,
    val remotePhotoId: String? = null,
    val caption: String = ""
)
