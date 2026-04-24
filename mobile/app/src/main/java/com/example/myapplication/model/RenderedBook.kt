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
    val slots: List<BookSlot>
)

data class BookSlot(
    val id: String,
    // For local preview/PDF we store Uri.toString() here.
    val photoId: String,
    val caption: String = ""
)
