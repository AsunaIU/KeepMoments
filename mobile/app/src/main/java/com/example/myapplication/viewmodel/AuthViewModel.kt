package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.model.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AuthUiState> = combine(
        authRepository.session,
        _isSubmitting,
        _errorMessage
    ) { session, isSubmitting, errorMessage ->
        AuthUiState(
            session = session,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthUiState()
    )

    fun submit(mode: AuthMode, email: String, password: String) {
        val normalizedEmail = email.trim()
        val normalizedPassword = password.trim()
        if (normalizedEmail.isBlank()) {
            _errorMessage.value = "Введите email"
            return
        }
        if (normalizedPassword.length < 6) {
            _errorMessage.value = "Пароль должен быть не короче 6 символов"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            val result = when (mode) {
                AuthMode.LOGIN -> authRepository.login(normalizedEmail, normalizedPassword)
                AuthMode.REGISTER -> authRepository.register(normalizedEmail, normalizedPassword)
            }
            _isSubmitting.value = false
            result.exceptionOrNull()?.localizedMessage?.let { message ->
                _errorMessage.value = message
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

enum class AuthMode {
    LOGIN,
    REGISTER
}

data class AuthUiState(
    val session: AuthSession? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val isAuthenticated: Boolean get() = session != null
}
