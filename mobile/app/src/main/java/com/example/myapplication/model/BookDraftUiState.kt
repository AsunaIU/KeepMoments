package com.example.myapplication.model

data class BookDraftUiState(
    val selectedPhotos: List<SelectedPhoto> = emptyList(),
    val storyPrompt: String = "",
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val generationError: String? = null,
    val generatedBook: RenderedBook? = null,
    val canContinue: Boolean = false
) {
    val validPhotos: List<SelectedPhoto>
        get() = selectedPhotos.filter { it.isValid }

    val invalidPhotos: List<SelectedPhoto>
        get() = selectedPhotos.filterNot { it.isValid }
}
