package com.example.myapplication.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.album.AlbumRepository
import com.example.myapplication.data.pdf.PdfExporter
import com.example.myapplication.model.EditableAlbum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RenderedBookViewModel(
    private val draftId: String,
    private val albumRepository: AlbumRepository,
    private val pdfExporter: PdfExporter
) : ViewModel() {

    companion object {
        private const val TAG = "RenderedBook"
    }

    private val workState = MutableStateFlow(RenderedBookWorkState())
    val uiState: StateFlow<RenderedBookUiState> = combine(
        albumRepository.observeAlbum(draftId),
        workState
    ) { album, work ->
        RenderedBookUiState(
            album = album,
            isExporting = work.isExporting,
            message = work.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RenderedBookUiState()
    )

    fun exportToPdf(destination: Uri) {
        val album = uiState.value.album
        if (album == null) {
            Log.e(TAG, "exportToPdf failed: book not found for draftId=$draftId")
            workState.update { it.copy(message = "Книга не готова, попробуйте ещё раз") }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "exportToPdf start draftId=$draftId destination=$destination")
            workState.update { it.copy(isExporting = true, message = null) }
            val result = pdfExporter.export(album, destination)
            result.onSuccess {
                Log.d(TAG, "exportToPdf success draftId=$draftId")
            }.onFailure { throwable ->
                Log.e(TAG, "exportToPdf failed draftId=$draftId: ${throwable.message}", throwable)
            }
            workState.update {
                it.copy(
                    isExporting = false,
                    message = result.exceptionOrNull()?.localizedMessage ?: "PDF сохранён"
                )
            }
        }
    }

    fun clearMessage() {
        workState.update { it.copy(message = null) }
    }

    class Factory(
        private val draftId: String,
        private val albumRepository: AlbumRepository,
        private val pdfExporter: PdfExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RenderedBookViewModel::class.java)) {
                return RenderedBookViewModel(draftId, albumRepository, pdfExporter) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class RenderedBookUiState(
    val album: EditableAlbum? = null,
    val isExporting: Boolean = false,
    val message: String? = null
)

private data class RenderedBookWorkState(
    val isExporting: Boolean = false,
    val message: String? = null
)
