package com.example.myapplication.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.album.AlbumRepository
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.AlbumPage
import com.example.myapplication.model.AlbumSlot
import com.example.myapplication.model.AlbumSticker
import com.example.myapplication.model.EditableAlbum
import com.example.myapplication.model.SelectedPhoto
import com.example.myapplication.model.matches
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumEditorViewModel(
    private val draftId: String,
    private val albumRepository: AlbumRepository,
    private val draftRepository: DraftRepository,
    private val photoImportService: PhotoImportService
) : ViewModel() {
    private companion object {
        const val PHOTO_LIMIT = 50
    }

    private val _uiState = MutableStateFlow(AlbumEditorUiState())
    val uiState: StateFlow<AlbumEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AlbumEditorEvent>(replay = 0)
    val events: SharedFlow<AlbumEditorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            albumRepository.observeAlbum(draftId).collect { album ->
                val currentIndex = _uiState.value.currentPageIndex.coerceAtMost((album?.pages?.lastIndex ?: 0).coerceAtLeast(0))
                val currentPage = album?.pages?.getOrNull(currentIndex)
                _uiState.update { state ->
                    if (state.hasUnsavedChanges) {
                        state.copy(album = album)
                    } else {
                        state.copy(
                            album = album,
                            currentPageIndex = currentIndex,
                            savedPage = currentPage,
                            editablePage = currentPage,
                            selectedSlotId = null,
                            selectedStickerId = null
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: EditorTab) {
        val page = _uiState.value.editablePage
        if (tab == EditorTab.Layout && page?.slots?.any { it.photoId == null } == true) {
            emitMessage("Заполните все слоты")
            return
        }
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun requestPageChange(index: Int) {
        val state = _uiState.value
        if (index == state.currentPageIndex) return
        if (state.hasUnsavedChanges) {
            _uiState.update { it.copy(savePrompt = SavePrompt.Page(index)) }
        } else {
            moveToPage(index)
        }
    }

    fun requestBack() = requestNavigation(SavePrompt.Back)

    fun requestProfile() = requestNavigation(SavePrompt.Profile)

    fun requestDone() = requestNavigation(SavePrompt.Done)

    fun dismissSavePrompt() {
        _uiState.update { it.copy(savePrompt = null) }
    }

    fun saveAndContinue() {
        val prompt = _uiState.value.savePrompt ?: return
        saveCurrentPage(onSaved = { continueAfter(prompt) })
    }

    fun discardAndContinue() {
        val prompt = _uiState.value.savePrompt ?: return
        _uiState.update { it.copy(editablePage = it.savedPage, hasUnsavedChanges = false, savePrompt = null) }
        continueAfter(prompt)
    }

    fun saveCurrentPage(onSaved: (() -> Unit)? = null) {
        val page = _uiState.value.editablePage ?: return
        if (!page.isComplete) {
            emitMessage("Заполните все слоты фотографиями")
            return
        }
        viewModelScope.launch {
            albumRepository.savePage(page)
            _uiState.update { state ->
                val pageChangedDuringSave = state.editablePage != page
                state.copy(
                    savedPage = page,
                    hasUnsavedChanges = pageChangedDuringSave,
                    savePrompt = null
                )
            }
            onSaved?.invoke()
        }
    }

    fun selectSlot(slot: AlbumSlot) {
        _uiState.update { it.copy(selectedSlotId = slot.id, selectedStickerId = null, selectedCaptionSlotId = null, slotMenu = slot) }
    }

    fun selectEmptySlot(slot: AlbumSlot) {
        _uiState.update { it.copy(selectedSlotId = slot.id, selectedStickerId = null, selectedCaptionSlotId = null, slotMenu = null, activeTab = EditorTab.Gallery) }
        emitMessage("Выберите фото из галереи")
    }

    fun selectCaption(slot: AlbumSlot) {
        _uiState.update {
            it.copy(
                selectedCaptionSlotId = slot.id,
                selectedSlotId = null,
                selectedStickerId = null,
                slotMenu = null
            )
        }
    }

    fun updateCaptionText(slotId: String, caption: String) {
        updateEditablePage { page ->
            page.copy(slots = page.slots.map { slot ->
                if (slot.id == slotId) slot.copy(caption = caption.take(CAPTION_LIMIT)) else slot
            })
        }
        _uiState.update { it.copy(selectedCaptionSlotId = slotId) }
    }

    fun deleteCaption(slotId: String? = _uiState.value.selectedCaptionSlotId) {
        val id = slotId ?: return
        updateEditablePage { page ->
            page.copy(slots = page.slots.map { slot ->
                if (slot.id == id) slot.copy(caption = "") else slot
            })
        }
        _uiState.update { it.copy(selectedCaptionSlotId = null) }
    }

    fun dismissSlotMenu() {
        _uiState.update { it.copy(slotMenu = null) }
    }

    fun startReplaceSelectedSlot() {
        _uiState.update { it.copy(slotMenu = null, activeTab = EditorTab.Gallery) }
        emitMessage("Выберите новое фото")
    }

    fun deleteSelectedSlotPhoto() {
        val selectedSlotId = _uiState.value.slotMenu?.id ?: _uiState.value.selectedSlotId ?: return
        updateEditablePage { page ->
            page.copy(slots = page.slots.map { slot -> if (slot.id == selectedSlotId) slot.copy(photoId = null) else slot })
        }
        _uiState.update { it.copy(slotMenu = null) }
    }

    fun addPhotoToCurrentSlot(photoId: String) {
        val state = _uiState.value
        val page = state.editablePage ?: return
        val targetSlotId = state.selectedSlotId ?: page.slots.firstOrNull { it.photoId == null }?.id
        if (targetSlotId == null) {
            emitMessage("Удалите фото со страницы, чтобы добавить новое")
            return
        }
        updateEditablePage { current ->
            current.copy(slots = current.slots.map { slot ->
                if (slot.id == targetSlotId) slot.copy(photoId = photoId, cropScale = 1f, cropOffsetX = 0f, cropOffsetY = 0f) else slot
            })
        }
    }

    fun importPhotosFromDevice(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val album = _uiState.value.album ?: return@launch
            val existingUris = album.draft.selectedPhotos.map { it.uriString }.toHashSet()
            val uniqueIncoming = uris
                .distinctBy(Uri::toString)
                .filterNot { existingUris.contains(it.toString()) }

            val availableSlots = PHOTO_LIMIT - album.draft.selectedPhotos.size
            if (availableSlots <= 0) {
                emitMessage("Можно выбрать максимум $PHOTO_LIMIT фото")
                return@launch
            }

            val selectedToAdd = uniqueIncoming.take(availableSlots)
            if (selectedToAdd.isEmpty()) {
                emitMessage("Это фото уже есть в альбоме")
                return@launch
            }

            runCatching {
                photoImportService.createSelectedPhotos(selectedToAdd)
            }.onSuccess { newPhotos ->
                draftRepository.addPhotos(draftId = draftId, photos = newPhotos)
                newPhotos.firstOrNull()?.let { photo ->
                    addPhotoToCurrentSlot(photo.id)
                }
                if (uniqueIncoming.size > selectedToAdd.size) {
                    emitMessage("Можно выбрать максимум $PHOTO_LIMIT фото")
                }
            }.onFailure { throwable ->
                emitMessage(throwable.localizedMessage ?: "Не удалось добавить фотографии")
            }
        }
    }

    fun requestLayout(layoutId: String) {
        val state = _uiState.value
        val page = state.editablePage ?: return
        val photosById = state.album?.draft?.selectedPhotos.orEmpty().associateBy { it.id }
        val layout = AlbumLayouts.require(layoutId)
        val photos = page.slots.mapNotNull { it.photoId?.let(photosById::get) }
        val hasMismatch = photos.zip(layout.slots).any { (photo, slot) -> !photo.matches(slot.orientation) }
        if (hasMismatch) {
            _uiState.update { it.copy(orientationPromptLayoutId = layoutId) }
        } else {
            applyLayout(layoutId)
        }
    }

    fun cancelOrientationPrompt() {
        _uiState.update { it.copy(orientationPromptLayoutId = null) }
    }

    fun replaceForOrientationAndApply() {
        val layoutId = _uiState.value.orientationPromptLayoutId ?: return
        val state = _uiState.value
        val page = state.editablePage ?: return
        val photos = state.album?.draft?.selectedPhotos.orEmpty()
        val layout = AlbumLayouts.require(layoutId)
        val usedIds = mutableSetOf<String>()
        val chosen = layout.slots.map { slot ->
            photos.firstOrNull { it.id !in usedIds && it.matches(slot.orientation) }?.also { usedIds += it.id }
        }
        if (chosen.any { it == null }) {
            emitMessage("Недостаточно подходящих фото для этого макета")
            return
        }
        _uiState.update { it.copy(orientationPromptLayoutId = null) }
        val rebuilt = albumRepository.buildPageWithLayout(page, layoutId).copy(
            slots = albumRepository.buildPageWithLayout(page, layoutId).slots.mapIndexed { index, slot ->
                slot.copy(photoId = chosen[index]?.id, cropScale = 1f, cropOffsetX = 0f, cropOffsetY = 0f)
            }
        )
        setEditablePage(rebuilt)
    }

    fun updateSlotTransform(slotId: String, scaleChange: Float, offsetXChange: Float, offsetYChange: Float) {
        updateEditablePage { page ->
            page.copy(slots = page.slots.map { slot ->
                if (slot.id == slotId) {
                    slot.copy(
                        cropScale = (slot.cropScale * scaleChange).coerceIn(1f, 4f),
                        cropOffsetX = (slot.cropOffsetX + offsetXChange).coerceIn(-1f, 1f),
                        cropOffsetY = (slot.cropOffsetY + offsetYChange).coerceIn(-1f, 1f)
                    )
                } else {
                    slot
                }
            })
        }
    }

    fun addSticker(sticker: String) {
        val page = _uiState.value.editablePage ?: return
        val nextZ = (page.stickers.maxOfOrNull { it.zIndex } ?: 0) + 1
        setEditablePage(page.copy(stickers = page.stickers + albumRepository.newSticker(page.id, sticker, nextZ)))
    }

    fun selectSticker(sticker: AlbumSticker) {
        _uiState.update { it.copy(selectedStickerId = sticker.id, selectedSlotId = null, selectedCaptionSlotId = null, slotMenu = null) }
    }

    fun updateSticker(stickerId: String, dx: Float, dy: Float, scaleChange: Float) {
        updateEditablePage { page ->
            page.copy(stickers = page.stickers.map { sticker ->
                if (sticker.id == stickerId) {
                    sticker.copy(
                        x = (sticker.x + dx).coerceIn(0f, 1f),
                        y = (sticker.y + dy).coerceIn(0f, 1f),
                        scale = (sticker.scale * scaleChange).coerceIn(0.4f, 4f)
                    )
                } else {
                    sticker
                }
            })
        }
    }

    fun deleteSelectedSticker() {
        val stickerId = _uiState.value.selectedStickerId ?: return
        updateEditablePage { page -> page.copy(stickers = page.stickers.filterNot { it.id == stickerId }) }
        _uiState.update { it.copy(selectedStickerId = null) }
    }

    private fun requestNavigation(prompt: SavePrompt) {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(savePrompt = prompt) }
        } else {
            continueAfter(prompt)
        }
    }

    private fun continueAfter(prompt: SavePrompt) {
        when (prompt) {
            SavePrompt.Back -> emitNavigation(AlbumEditorNavigation.Back)
            SavePrompt.Profile -> emitNavigation(AlbumEditorNavigation.Profile)
            SavePrompt.Done -> emitNavigation(AlbumEditorNavigation.Done)
            is SavePrompt.Page -> moveToPage(prompt.index)
        }
    }

    private fun moveToPage(index: Int) {
        val album = _uiState.value.album ?: return
        val page = album.pages.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                currentPageIndex = index,
                savedPage = page,
                editablePage = page,
                hasUnsavedChanges = false,
                selectedSlotId = null,
                selectedStickerId = null,
                selectedCaptionSlotId = null,
                slotMenu = null,
                savePrompt = null
            )
        }
    }

    private fun applyLayout(layoutId: String) {
        val page = _uiState.value.editablePage ?: return
        setEditablePage(albumRepository.buildPageWithLayout(page, layoutId))
    }

    private fun updateEditablePage(transform: (AlbumPage) -> AlbumPage) {
        val page = _uiState.value.editablePage ?: return
        setEditablePage(transform(page))
    }

    private fun setEditablePage(page: AlbumPage) {
        _uiState.update { it.copy(editablePage = page, hasUnsavedChanges = page != it.savedPage) }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _events.emit(AlbumEditorEvent.Message(message)) }
    }

    private fun emitNavigation(navigation: AlbumEditorNavigation) {
        viewModelScope.launch { _events.emit(AlbumEditorEvent.Navigate(navigation)) }
    }

    class Factory(
        private val draftId: String,
        private val albumRepository: AlbumRepository,
        private val draftRepository: DraftRepository,
        private val photoImportService: PhotoImportService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlbumEditorViewModel::class.java)) {
                return AlbumEditorViewModel(
                    draftId = draftId,
                    albumRepository = albumRepository,
                    draftRepository = draftRepository,
                    photoImportService = photoImportService
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

private const val CAPTION_LIMIT = 120

data class AlbumEditorUiState(
    val album: EditableAlbum? = null,
    val currentPageIndex: Int = 0,
    val savedPage: AlbumPage? = null,
    val editablePage: AlbumPage? = null,
    val activeTab: EditorTab = EditorTab.Gallery,
    val selectedSlotId: String? = null,
    val selectedStickerId: String? = null,
    val selectedCaptionSlotId: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val savePrompt: SavePrompt? = null,
    val orientationPromptLayoutId: String? = null,
    val slotMenu: AlbumSlot? = null
)

enum class EditorTab(val title: String) {
    Gallery("ГАЛЕРЕЯ"),
    Layout("МАКЕТ"),
    Sticker("СТИКЕР")
}

sealed interface SavePrompt {
    data object Back : SavePrompt
    data object Profile : SavePrompt
    data object Done : SavePrompt
    data class Page(val index: Int) : SavePrompt
}

sealed interface AlbumEditorEvent {
    data class Message(val text: String) : AlbumEditorEvent
    data class Navigate(val destination: AlbumEditorNavigation) : AlbumEditorEvent
}

enum class AlbumEditorNavigation {
    Back,
    Profile,
    Done
}
