package com.example.myapplication.model

data class RenderedBook(
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
    val photoId: String,
    val caption: String,
    val orientation: String
)
