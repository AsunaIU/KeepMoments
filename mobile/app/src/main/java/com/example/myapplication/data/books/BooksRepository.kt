package com.example.myapplication.data.books

import com.example.myapplication.model.SelectedPhoto

interface BooksRepository {
    suspend fun submitDraft(photos: List<SelectedPhoto>): Result<Unit>
}