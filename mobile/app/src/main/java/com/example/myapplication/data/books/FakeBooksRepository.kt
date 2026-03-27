package com.example.myapplication.data.books

import com.example.myapplication.model.SelectedPhoto
import kotlinx.coroutines.delay
import kotlin.random.Random

class FakeBooksRepository : BooksRepository {
    override suspend fun submitDraft(photos: List<SelectedPhoto>): Result<Unit> {
        delay(Random.nextLong(from = 500L, until = 1_000L))
        return Result.success(Unit)
    }
}