package com.example.myapplication.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.model.BookDraftSummary
import com.example.myapplication.model.DraftOwnerType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DraftsViewModel(
    private val draftRepository: DraftRepository,
    private val authRepository: AuthRepository,
    private val photoImportService: PhotoImportService
) : ViewModel() {

    private val _isCreating = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val sessionFlow = authRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val draftsFlow = sessionFlow.flatMapLatest { session ->
        draftRepository.observeVisibleDrafts(session?.userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<DraftsUiState> = combine(
        sessionFlow,
        draftsFlow,
        _isCreating,
        _errorMessage
    ) { session, drafts, isCreating, errorMessage ->
        DraftsUiState(
            drafts = drafts,
            isCreating = isCreating,
            errorMessage = errorMessage,
            isAuthenticated = session != null,
            userEmail = session?.email
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DraftsUiState()
    )

    suspend fun createDraftFromUris(uris: List<Uri>): String? {
        if (uris.isEmpty()) return null

        return runCatching {
            _isCreating.value = true
            _errorMessage.value = null

            val session = authRepository.currentSession()
            val importedPhotos = photoImportService.createSelectedPhotos(uris.take(PHOTO_LIMIT))
            if (importedPhotos.isEmpty()) {
                null
            } else {
                draftRepository.createDraft(
                    ownerType = if (session == null) DraftOwnerType.GUEST else DraftOwnerType.USER,
                    ownerUserId = session?.userId,
                    photos = importedPhotos
                )
            }
        }.onFailure { throwable ->
            _errorMessage.value = throwable.localizedMessage ?: "Не удалось создать черновик"
        }.also {
            _isCreating.value = false
        }.getOrNull()
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            draftRepository.deleteDraft(draftId)
        }
    }

    fun renameDraft(draftId: String, title: String) {
        viewModelScope.launch {
            draftRepository.updateDraftTitle(draftId = draftId, title = title)
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    class Factory(
        private val draftRepository: DraftRepository,
        private val authRepository: AuthRepository,
        private val photoImportService: PhotoImportService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DraftsViewModel::class.java)) {
                return DraftsViewModel(draftRepository, authRepository, photoImportService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        const val PHOTO_LIMIT = 50
    }
}

data class DraftsUiState(
    val drafts: List<BookDraftSummary> = emptyList(),
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val userEmail: String? = null
)
