package com.example.myapplication.data.media

import android.net.Uri
import com.example.myapplication.model.SelectedPhoto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoImportService(
    private val mediaMetadataReader: MediaMetadataReader,
    private val photoValidator: PhotoValidator,
    private val photoLocalStorage: PhotoLocalStorage
) {

    suspend fun createSelectedPhotos(uris: List<Uri>): List<SelectedPhoto> = withContext(Dispatchers.IO) {
        val importedPhotos = mutableListOf<SelectedPhoto>()
        try {
            uris.distinctBy(Uri::toString).forEach { uri ->
                val metadata = mediaMetadataReader.read(uri)
                val validationResult = photoValidator.validate(metadata)
                val localUri = photoLocalStorage.copyToLocalStorage(
                    sourceUri = uri,
                    displayName = metadata.displayName,
                    mimeType = metadata.mimeType
                )
                importedPhotos += SelectedPhoto(
                    id = UUID.randomUUID().toString(),
                    uriString = localUri.toString(),
                    displayName = metadata.displayName,
                    mimeType = metadata.mimeType,
                    sizeBytes = metadata.sizeBytes,
                    width = metadata.width,
                    height = metadata.height,
                    isValid = validationResult.isValid,
                    validationMessage = validationResult.message
                )
            }
            importedPhotos
        } catch (throwable: Throwable) {
            deleteLocalCopies(importedPhotos)
            throw throwable
        }
    }

    fun deleteLocalCopies(photos: List<SelectedPhoto>) {
        photos.forEach { photo ->
            photoLocalStorage.deleteLocalCopy(Uri.parse(photo.uriString))
        }
    }
}
