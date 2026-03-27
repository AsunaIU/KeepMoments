package com.example.myapplication.model

data class BookDraftUiState(
    val selectedPhotos: List<SelectedPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canContinue: Boolean = false
)
