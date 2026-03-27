package com.example.myapplication.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

interface MediaMetadataReader {
    fun read(uri: Uri): MediaMetadata
}

data class MediaMetadata(
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?
)

class AndroidMediaMetadataReader(
    private val contentResolver: ContentResolver
) : MediaMetadataReader {

    override fun read(uri: Uri): MediaMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex)
                }

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        return MediaMetadata(
            displayName = displayName,
            mimeType = contentResolver.getType(uri),
            sizeBytes = sizeBytes
        )
    }
}
