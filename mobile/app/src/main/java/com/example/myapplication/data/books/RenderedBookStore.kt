package com.example.myapplication.data.books

import com.example.myapplication.model.RenderedBook
import java.util.concurrent.ConcurrentHashMap

class RenderedBookStore {

    private val books = ConcurrentHashMap<String, RenderedBook>()

    fun save(draftId: String, book: RenderedBook) {
        books[draftId] = book
    }

    fun get(draftId: String): RenderedBook? = books[draftId]

    fun remove(draftId: String) {
        books.remove(draftId)
    }
}
