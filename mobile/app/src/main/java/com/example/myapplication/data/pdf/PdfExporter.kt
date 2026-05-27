package com.example.myapplication.data.pdf

import android.net.Uri
import com.example.myapplication.model.EditableAlbum

interface PdfExporter {
    suspend fun export(album: EditableAlbum, destination: Uri): Result<Unit>
}
