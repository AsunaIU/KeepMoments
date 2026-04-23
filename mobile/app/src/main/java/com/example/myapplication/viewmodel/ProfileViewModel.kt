package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.profile.ProfileStore
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
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileStore: ProfileStore
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isSaving = MutableStateFlow(false)

    private val sessionFlow = authRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val storedProfileFlow = sessionFlow.flatMapLatest { session ->
        profileStore.observeProfile(session?.userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.example.myapplication.data.profile.StoredProfile(null, null)
    )

    val uiState: StateFlow<ProfileUiState> = combine(
        sessionFlow,
        storedProfileFlow,
        _isSaving,
        _errorMessage
    ) { session, storedProfile, isSaving, errorMessage ->
        val defaultName = when {
            !session?.email.isNullOrBlank() -> session!!.email.substringBefore('@').replaceFirstChar { char -> char.titlecase() }
            else -> "Гость"
        }
        val displayName = storedProfile.displayName?.takeIf { it.isNotBlank() } ?: defaultName
        ProfileUiState(
            displayName = displayName,
            editableDisplayName = storedProfile.displayName ?: if (session == null) "" else defaultName,
            avatarUriString = storedProfile.avatarUriString,
            email = session?.email,
            isAuthenticated = session != null,
            isSaving = isSaving,
            errorMessage = errorMessage,
            initials = buildInitials(displayName)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    fun saveProfile(displayName: String, avatarUriString: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            runCatching {
                val userId = authRepository.currentSession()?.userId
                profileStore.saveProfile(
                    userId = userId,
                    displayName = displayName,
                    avatarUriString = avatarUriString
                )
            }.onSuccess {
                onComplete()
            }.onFailure { throwable ->
                _errorMessage.value = throwable.localizedMessage ?: "Не удалось сохранить профиль"
            }
            _isSaving.value = false
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val profileStore: ProfileStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                return ProfileViewModel(authRepository, profileStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private fun buildInitials(name: String): String {
        val parts = name.split(' ').filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> "KM"
        }
    }
}

data class ProfileUiState(
    val displayName: String = "Гость",
    val editableDisplayName: String = "",
    val avatarUriString: String? = null,
    val email: String? = null,
    val isAuthenticated: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val initials: String = "KM"
) {
    val emailLabel: String get() = email ?: "Без аккаунта"
}
