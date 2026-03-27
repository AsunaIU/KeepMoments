package com.example.myapplication.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.books.BooksRepository
import com.example.myapplication.data.media.MediaMetadataReader
import com.example.myapplication.model.BookDraftUiState
import com.example.myapplication.model.SelectedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookCreationViewModel(
    private val booksRepository: BooksRepository,
    private val mediaMetadataReader: MediaMetadataReader
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDraftUiState())
    val uiState: StateFlow<BookDraftUiState> = _uiState.asStateFlow()

    fun onPhotosPicked(uris: List<Uri>) {
        addPhotos(uris = uris, replaceExisting = true)
    }

    fun onAddMorePhotos(uris: List<Uri>) {
        addPhotos(uris = uris, replaceExisting = false)
    }

    fun onRemovePhoto(photoId: String) {
        _uiState.update { current ->
            val updatedPhotos = current.selectedPhotos.filterNot { it.id == photoId }
            current.copy(
                selectedPhotos = updatedPhotos,
                canContinue = updatedPhotos.isNotEmpty()
            )
        }
    }

    fun onContinueClicked() {
        val photos = _uiState.value.selectedPhotos
        if (photos.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Сначала добавьте фотографии") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = booksRepository.submitDraft(photos)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = if (result.isSuccess) {
                        "Дальше еще не готово"
                    } else {
                        result.exceptionOrNull()?.localizedMessage ?: "Что-то пошло не так"
                    }
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun addPhotos(uris: List<Uri>, replaceExisting: Boolean) {
        if (uris.isEmpty()) return

        val currentState = _uiState.value
        val currentPhotos = if (replaceExisting) emptyList() else currentState.selectedPhotos
        val existingUris = currentPhotos.map { it.uriString }.toHashSet()
        val uniqueIncoming = uris
            .map { it.toString() }
            .distinct()
            .filterNot { existingUris.contains(it) }

        val availableSlots = PHOTO_LIMIT - currentPhotos.size
        if (availableSlots <= 0) {
            _uiState.update { it.copy(errorMessage = "Можно выбрать максимум $PHOTO_LIMIT фото") }
            return
        }

        val selectedToAdd = uniqueIncoming.take(availableSlots)
        if (selectedToAdd.isEmpty()) return

        val wasTrimmed = uniqueIncoming.size > selectedToAdd.size

        viewModelScope.launch {
            val newPhotos = withContext(Dispatchers.IO) {
                selectedToAdd.map { uriString ->
                    val uri = Uri.parse(uriString)
                    val metadata = mediaMetadataReader.read(uri)
                    SelectedPhoto(
                        id = uriString,
                        uriString = uriString,
                        displayName = metadata.displayName,
                        mimeType = metadata.mimeType,
                        sizeBytes = metadata.sizeBytes
                    )
                }
            }

            _uiState.update { state ->
                val combined = currentPhotos + newPhotos
                state.copy(
                    selectedPhotos = combined,
                    canContinue = combined.isNotEmpty(),
                    errorMessage = if (wasTrimmed) {
                        "Лимит: максимум $PHOTO_LIMIT фото"
                    } else {
                        null
                    }
                )
            }
        }
    }

    companion object {
        const val PHOTO_LIMIT = 50
    }

    class Factory(
        private val booksRepository: BooksRepository,
        private val mediaMetadataReader: MediaMetadataReader
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookCreationViewModel::class.java)) {
                return BookCreationViewModel(booksRepository, mediaMetadataReader) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}