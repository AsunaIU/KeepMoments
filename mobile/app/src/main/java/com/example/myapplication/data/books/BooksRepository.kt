package com.example.myapplication.data.books

import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.RenderedBook

interface BooksRepository {
    suspend fun generateRenderedBook(draft: BookDraft): Result<RenderedBook>
}
