package com.example.myapplication.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.books.RenderedBookStore
import com.example.myapplication.data.pdf.PdfExporter
import com.example.myapplication.model.RenderedBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RenderedBookViewModel(
    private val draftId: String,
    private val renderedBookStore: RenderedBookStore,
    private val pdfExporter: PdfExporter
) : ViewModel() {

    companion object {
        private const val TAG = "RenderedBook"
    }

    private val _uiState = MutableStateFlow(
        RenderedBookUiState(
            book = renderedBookStore.get(draftId)
        )
    )
    val uiState: StateFlow<RenderedBookUiState> = _uiState.asStateFlow()

    fun exportToPdf(destination: Uri) {
        val book = _uiState.value.book
        if (book == null) {
            Log.e(TAG, "exportToPdf failed: book not found for draftId=$draftId")
            _uiState.update { it.copy(message = "Книга не готова, попробуйте ещё раз") }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "exportToPdf start draftId=$draftId destination=$destination")
            _uiState.update { it.copy(isExporting = true, message = null) }
            val result = pdfExporter.export(book, destination)
            result.onSuccess {
                Log.d(TAG, "exportToPdf success draftId=$draftId")
            }.onFailure { throwable ->
                Log.e(TAG, "exportToPdf failed draftId=$draftId: ${throwable.message}", throwable)
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    message = result.exceptionOrNull()?.localizedMessage ?: "PDF сохранён"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    class Factory(
        private val draftId: String,
        private val renderedBookStore: RenderedBookStore,
        private val pdfExporter: PdfExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RenderedBookViewModel::class.java)) {
                return RenderedBookViewModel(draftId, renderedBookStore, pdfExporter) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class RenderedBookUiState(
    val book: RenderedBook? = null,
    val isExporting: Boolean = false,
    val message: String? = null
)
