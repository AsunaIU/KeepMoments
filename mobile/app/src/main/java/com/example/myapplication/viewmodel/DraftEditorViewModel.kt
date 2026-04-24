package com.example.myapplication.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.books.BooksRepository
import com.example.myapplication.data.books.RenderedBookStore
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
    private val photoImportService: PhotoImportService,
    private val booksRepository: BooksRepository,
    private val renderedBookStore: RenderedBookStore
) : ViewModel() {

    companion object {
        const val PHOTO_LIMIT = 50
        private const val TAG = "DraftEditor"
    }

    private val _hasLoadedDraft = MutableStateFlow(false)
    private val _isProcessing = MutableStateFlow(false)
    private val _isGeneratingBook = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _requiresAuthToContinue = MutableStateFlow(false)
    private val _generatedBookDraftId = MutableStateFlow<String?>(null)

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

    private data class DraftEditorTransientState(
        val hasLoadedDraft: Boolean,
        val isProcessing: Boolean,
        val isGeneratingBook: Boolean,
        val errorMessage: String?,
        val requiresAuthToContinue: Boolean,
        val generatedBookDraftId: String?
    )

    private val loadingAndWorkStateFlow = combine(
        _hasLoadedDraft,
        _isProcessing,
        _isGeneratingBook
    ) { hasLoadedDraft, isProcessing, isGeneratingBook ->
        Triple(hasLoadedDraft, isProcessing, isGeneratingBook)
    }

    private val messageAndNavigationStateFlow = combine(
        _errorMessage,
        _requiresAuthToContinue,
        _generatedBookDraftId
    ) { errorMessage, requiresAuthToContinue, generatedBookDraftId ->
        Triple(errorMessage, requiresAuthToContinue, generatedBookDraftId)
    }

    private val transientStateFlow = combine(
        loadingAndWorkStateFlow,
        messageAndNavigationStateFlow
    ) { loadingAndWorkState, messageAndNavigationState ->
        val (hasLoadedDraft, isProcessing, isGeneratingBook) = loadingAndWorkState
        val (errorMessage, requiresAuthToContinue, generatedBookDraftId) = messageAndNavigationState
        DraftEditorTransientState(
            hasLoadedDraft = hasLoadedDraft,
            isProcessing = isProcessing,
            isGeneratingBook = isGeneratingBook,
            errorMessage = errorMessage,
            requiresAuthToContinue = requiresAuthToContinue,
            generatedBookDraftId = generatedBookDraftId
        )
    }

    val uiState: StateFlow<DraftEditorUiState> = combine(
        draftFlow,
        transientStateFlow
    ) { draft, transientState ->
        DraftEditorUiState(
            draftId = draftId,
            ownerType = draft?.ownerType,
            selectedPhotos = draft?.selectedPhotos.orEmpty(),
            isLoading = !transientState.hasLoadedDraft,
            isProcessing = transientState.isProcessing,
            isGeneratingBook = transientState.isGeneratingBook,
            errorMessage = transientState.errorMessage,
            isMissing = transientState.hasLoadedDraft && draft == null,
            canContinue = draft?.selectedPhotos?.any(SelectedPhoto::isValid) == true,
            requiresAuthToContinue = transientState.requiresAuthToContinue,
            generatedBookDraftId = transientState.generatedBookDraftId
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
        Log.d(TAG, "continue clicked draftId=$draftId")
        if (currentDraft?.selectedPhotos?.any(SelectedPhoto::isValid) != true) {
            Log.e(TAG, "continue blocked: no valid photos")
            _errorMessage.value = "Добавьте хотя бы одно валидное фото"
            return
        }

        if (sessionFlow.value == null) {
            // Backend upload and /process ordering require BearerAuth by current contract.
            Log.d(TAG, "continue blocked: auth required draftId=$draftId")
            _errorMessage.value = "Для создания книги нужен вход в аккаунт"
            _requiresAuthToContinue.value = true
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "continue generating book draftId=$draftId")
            _isGeneratingBook.value = true
            _errorMessage.value = null
            val result = booksRepository.generateRenderedBook(currentDraft)
            _isGeneratingBook.value = false

            result.onSuccess { book ->
                renderedBookStore.save(draftId = draftId, book = book)
                Log.d(TAG, "continue success draftId=$draftId navigate rendered")
                _generatedBookDraftId.value = draftId
            }.onFailure { throwable ->
                Log.e(TAG, "continue failed draftId=$draftId: ${throwable.message}", throwable)
                _errorMessage.value = throwable.localizedMessage ?: "Не удалось собрать книгу"
            }
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    fun consumeAuthRequirement() {
        _requiresAuthToContinue.value = false
    }

    fun consumeGeneratedBookNavigation() {
        _generatedBookDraftId.value = null
    }

    class Factory(
        private val draftId: String,
        private val draftRepository: DraftRepository,
        private val authRepository: AuthRepository,
        private val photoImportService: PhotoImportService,
        private val booksRepository: BooksRepository,
        private val renderedBookStore: RenderedBookStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DraftEditorViewModel::class.java)) {
                return DraftEditorViewModel(
                    draftId = draftId,
                    draftRepository = draftRepository,
                    authRepository = authRepository,
                    photoImportService = photoImportService,
                    booksRepository = booksRepository,
                    renderedBookStore = renderedBookStore
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class DraftEditorUiState(
    val draftId: String,
    val ownerType: DraftOwnerType? = null,
    val selectedPhotos: List<SelectedPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isGeneratingBook: Boolean = false,
    val errorMessage: String? = null,
    val isMissing: Boolean = false,
    val canContinue: Boolean = false,
    val requiresAuthToContinue: Boolean = false,
    val generatedBookDraftId: String? = null
)
