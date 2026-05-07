package com.example.myapplication.data.media

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns

interface MediaMetadataReader {
    fun read(uri: Uri): MediaMetadata
}

data class MediaMetadata(
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?
)

class AndroidMediaMetadataReader(
    private val contentResolver: ContentResolver
) : MediaMetadataReader {

    override fun read(uri: Uri): MediaMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null
        var width: Int? = null
        var height: Int? = null

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

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            width = options.outWidth.takeIf { it > 0 }
            height = options.outHeight.takeIf { it > 0 }
        }

        return MediaMetadata(
            displayName = displayName,
            mimeType = contentResolver.getType(uri),
            sizeBytes = sizeBytes,
            width = width,
            height = height
        )
    }
}
