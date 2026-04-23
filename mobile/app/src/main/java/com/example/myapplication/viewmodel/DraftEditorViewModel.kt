package com.example.myapplication.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.model.SelectedPhoto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DraftEditorViewModel(
    private val draftId: String,
    private val draftRepository: DraftRepository,
    private val authRepository: AuthRepository,
    private val photoImportService: PhotoImportService
) : ViewModel() {

    private val _hasLoadedDraft = MutableStateFlow(false)
    private val _isProcessing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val sessionFlow = authRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val draftFlow = sessionFlow.flatMapLatest { session ->
        draftRepository.observeVisibleDraft(draftId = draftId, userId = session?.userId)
    }.onEach {
        _hasLoadedDraft.value = true
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val uiState: StateFlow<DraftEditorUiState> = combine(
        draftFlow,
        _hasLoadedDraft,
        _isProcessing,
        _errorMessage
    ) { draft, hasLoadedDraft, isProcessing, errorMessage ->
        DraftEditorUiState(
            draftId = draftId,
            ownerType = draft?.ownerType,
            selectedPhotos = draft?.selectedPhotos.orEmpty(),
            isLoading = !hasLoadedDraft,
            isProcessing = isProcessing,
            errorMessage = errorMessage,
            isMissing = hasLoadedDraft && draft == null,
            canContinue = draft?.selectedPhotos?.any(SelectedPhoto::isValid) == true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DraftEditorUiState(draftId = draftId, isLoading = true)
    )

    fun onAddMorePhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val currentDraft = draftFlow.value ?: return@launch
            val existingUris = currentDraft.selectedPhotos.map { it.uriString }.toHashSet()
            val uniqueIncoming = uris
                .distinctBy(Uri::toString)
                .filterNot { existingUris.contains(it.toString()) }

            val availableSlots = PHOTO_LIMIT - currentDraft.selectedPhotos.size
            if (availableSlots <= 0) {
                _errorMessage.value = "Можно выбрать максимум $PHOTO_LIMIT фото"
                return@launch
            }

            val selectedToAdd = uniqueIncoming.take(availableSlots)
            if (selectedToAdd.isEmpty()) return@launch

            _isProcessing.value = true
            _errorMessage.value = null
            runCatching {
                val newPhotos = photoImportService.createSelectedPhotos(selectedToAdd)
                draftRepository.addPhotos(draftId = draftId, photos = newPhotos)
                if (uniqueIncoming.size > selectedToAdd.size) {
                    _errorMessage.value = "Можно выбрать максимум $PHOTO_LIMIT фото"
                }
            }.onFailure { throwable ->
                _errorMessage.value = throwable.localizedMessage ?: "Не удалось добавить фотографии"
            }
            _isProcessing.value = false
        }
    }

    fun onRemovePhoto(photoId: String) {
        viewModelScope.launch {
            draftRepository.removePhoto(draftId = draftId, photoId = photoId)
        }
    }

    fun onContinueClicked() {
        val currentDraft = draftFlow.value
        _errorMessage.value = if (currentDraft?.selectedPhotos?.any(SelectedPhoto::isValid) == true) {
            "Следующий шаг MVP пока в разработке"
        } else {
            "Добавьте хотя бы одно валидное фото"
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    class Factory(
        private val draftId: String,
        private val draftRepository: DraftRepository,
        private val authRepository: AuthRepository,
        private val photoImportService: PhotoImportService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DraftEditorViewModel::class.java)) {
                return DraftEditorViewModel(draftId, draftRepository, authRepository, photoImportService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        const val PHOTO_LIMIT = 50
    }
}

data class DraftEditorUiState(
    val draftId: String,
    val ownerType: DraftOwnerType? = null,
    val selectedPhotos: List<SelectedPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val isMissing: Boolean = false,
    val canContinue: Boolean = false
)
