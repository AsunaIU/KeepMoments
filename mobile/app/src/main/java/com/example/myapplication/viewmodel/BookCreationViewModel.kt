package com.example.myapplication.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.books.BooksRepository
import com.example.myapplication.data.media.MediaMetadata
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
                canContinue = updatedPhotos.any { it.isValid },
                generationError = null,
                generatedBook = null
            )
        }
    }

    fun onStoryPromptChanged(value: String) {
        _uiState.update {
            it.copy(
                storyPrompt = value.take(STORY_PROMPT_LIMIT),
                generationError = null,
                generatedBook = null
            )
        }
    }

    fun generateBook() {
        val currentState = _uiState.value
        val validPhotos = currentState.validPhotos
        if (validPhotos.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Добавьте хотя бы одно валидное фото") }
            return
        }

        if (currentState.isGenerating) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generationError = null,
                    generatedBook = null
                )
            }

            val result = booksRepository.generateBook(
                photos = validPhotos,
                storyPrompt = currentState.storyPrompt.trim()
            )

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isGenerating = false,
                        generationError = null,
                        generatedBook = result.getOrNull()
                    )
                } else {
                    it.copy(
                        isGenerating = false,
                        generationError = "Произошла ошибка, попробуйте ещё раз"
                    )
                }
            }
        }
    }

    fun retryGeneration() {
        generateBook()
    }

    fun clearGenerationError() {
        _uiState.update { it.copy(generationError = null) }
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
                    val validationMessage = validatePhoto(metadata = metadata)
                    SelectedPhoto(
                        id = uriString,
                        uriString = uriString,
                        displayName = metadata.displayName,
                        mimeType = metadata.mimeType,
                        sizeBytes = metadata.sizeBytes,
                        width = metadata.width,
                        height = metadata.height,
                        isValid = validationMessage == null,
                        validationMessage = validationMessage
                    )
                }
            }

            _uiState.update { state ->
                val combined = currentPhotos + newPhotos
                state.copy(
                    selectedPhotos = combined,
                    storyPrompt = if (replaceExisting) "" else state.storyPrompt,
                    canContinue = combined.any { it.isValid },
                    errorMessage = if (wasTrimmed) {
                        "Лимит: максимум $PHOTO_LIMIT фото"
                    } else {
                        null
                    },
                    generationError = null,
                    generatedBook = null
                )
            }
        }
    }

    private fun validatePhoto(metadata: MediaMetadata): String? {
        if (!isSupportedFormat(metadata.mimeType, metadata.displayName)) {
            return "Поддерживаются только JPG и PNG"
        }

        val sizeBytes = metadata.sizeBytes
        if (sizeBytes != null && sizeBytes > MAX_PHOTO_SIZE_BYTES) {
            return "Размер файла больше 10 МБ"
        }

        return null
    }

    private fun isSupportedFormat(mimeType: String?, displayName: String?): Boolean {
        val normalizedMimeType = mimeType?.lowercase()
        if (normalizedMimeType in supportedMimeTypes) {
            return true
        }

        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?: return false

        return extension in supportedExtensions
    }

    companion object {
        const val PHOTO_LIMIT = 50
        const val STORY_PROMPT_LIMIT = 500
        private const val MAX_PHOTO_SIZE_BYTES = 10L * 1024L * 1024L

        private val supportedMimeTypes = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png"
        )

        private val supportedExtensions = setOf(
            "jpg",
            "jpeg",
            "png"
        )
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
