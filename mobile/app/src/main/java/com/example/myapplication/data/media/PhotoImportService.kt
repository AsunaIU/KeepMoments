package com.example.myapplication.data.media

import android.net.Uri
import com.example.myapplication.model.SelectedPhoto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoImportService(
    private val mediaMetadataReader: MediaMetadataReader,
    private val photoValidator: PhotoValidator
) {

    suspend fun createSelectedPhotos(uris: List<Uri>): List<SelectedPhoto> = withContext(Dispatchers.IO) {
        uris.distinctBy(Uri::toString).map { uri ->
            val metadata = mediaMetadataReader.read(uri)
            val validationResult = photoValidator.validate(metadata)
            SelectedPhoto(
                id = UUID.randomUUID().toString(),
                uriString = uri.toString(),
                displayName = metadata.displayName,
                mimeType = metadata.mimeType,
                sizeBytes = metadata.sizeBytes,
                width = metadata.width,
                height = metadata.height,
                isValid = validationResult.isValid,
                validationMessage = validationResult.message
            )
        }
    }
}
