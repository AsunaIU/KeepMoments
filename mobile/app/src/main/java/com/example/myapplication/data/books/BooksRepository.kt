package com.example.myapplication.data.books

import com.example.myapplication.model.RenderedBook
import com.example.myapplication.model.SelectedPhoto

interface BooksRepository {
    suspend fun generateBook(
        photos: List<SelectedPhoto>,
        storyPrompt: String
    ): Result<RenderedBook>
}
