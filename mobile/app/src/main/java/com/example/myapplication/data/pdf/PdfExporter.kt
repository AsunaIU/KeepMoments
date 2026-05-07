package com.example.myapplication.data.pdf

import android.net.Uri
import com.example.myapplication.model.RenderedBook

interface PdfExporter {
    suspend fun export(book: RenderedBook, destination: Uri): Result<Unit>
}
