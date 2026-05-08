package com.example.myapplication.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.AlbumPage
import com.example.myapplication.model.AlbumSlot
import com.example.myapplication.model.AlbumSticker
import com.example.myapplication.model.SelectedPhoto
import com.example.myapplication.viewmodel.AlbumEditorUiState
import com.example.myapplication.viewmodel.EditorTab
import com.example.myapplication.ui.theme.Blue40
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumEditorScreen(
    uiState: AlbumEditorUiState,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onDoneClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSavePromptSave: () -> Unit,
    onSavePromptDiscard: () -> Unit,
    onTabClick: (EditorTab) -> Unit,
    onPageChange: (Int) -> Unit,
    onSlotClick: (AlbumSlot) -> Unit,
    onEmptySlotClick: (AlbumSlot) -> Unit,
    onDismissSlotMenu: () -> Unit,
    onReplaceSlotClick: () -> Unit,
    onDeleteSlotClick: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onPickFromDeviceClick: () -> Unit,
    onLayoutClick: (String) -> Unit,
    onOrientationReplaceClick: () -> Unit,
    onOrientationCancelClick: () -> Unit,
    onSlotTransform: (String, Float, Float, Float) -> Unit,
    onStickerClick: (String) -> Unit,
    onStickerSelected: (AlbumSticker) -> Unit,
    onStickerTransform: (String, Float, Float, Float) -> Unit,
    onDeleteStickerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val album = uiState.album
    val pages = album?.pages.orEmpty()
    val photosById = album?.draft?.selectedPhotos.orEmpty().associateBy { it.id }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    BackHandler { onBackClick() }

    LaunchedEffect(uiState.currentPageIndex) {
        if (pages.isNotEmpty() && pagerState.currentPage != uiState.currentPageIndex) {
            pagerState.animateScrollToPage(uiState.currentPageIndex)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentPageIndex) {
            onPageChange(pagerState.currentPage)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF202020),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (pages.isEmpty()) "Редактор" else "Страница ${uiState.currentPageIndex + 1} / ${pages.size}",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Мой профиль")
                    }
                    TextButton(onClick = onDoneClick) {
                        Text("Сохранить")
                    }
                }
            )
        },
        bottomBar = {
            EditorBottomPanel(
                uiState = uiState,
                photos = album?.draft?.selectedPhotos.orEmpty(),
                onTabClick = onTabClick,
                onSaveClick = onSaveClick,
                onPhotoClick = onPhotoClick,
                onPickFromDeviceClick = onPickFromDeviceClick,
                onLayoutClick = onLayoutClick,
                onStickerClick = onStickerClick,
                onDeleteStickerClick = onDeleteStickerClick
            )
        }
    ) { innerPadding ->
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Альбом не найден", color = Color.White)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                userScrollEnabled = uiState.selectedSlotId == null && uiState.selectedStickerId == null
            ) { index ->
                val page = if (index == uiState.currentPageIndex) uiState.editablePage else pages[index]
                EditorPage(
                    page = page,
                    photosById = photosById,
                    selectedSlotId = uiState.selectedSlotId,
                    selectedStickerId = uiState.selectedStickerId,
                    onSlotClick = onSlotClick,
                    onEmptySlotClick = onEmptySlotClick,
                    onSlotTransform = onSlotTransform,
                    onStickerSelected = onStickerSelected,
                    onStickerTransform = onStickerTransform
                )
            }
        }
    }

    if (uiState.savePrompt != null) {
        ModalBottomSheet(onDismissRequest = {}) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Сохранить изменения на странице ${uiState.currentPageIndex + 1}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(onClick = onSavePromptSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить")
                }
                OutlinedButton(onClick = onSavePromptDiscard, modifier = Modifier.fillMaxWidth()) {
                    Text("Не сохранять")
                }
            }
        }
    }

    uiState.slotMenu?.let {
        ModalBottomSheet(onDismissRequest = onDismissSlotMenu) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Фото в слоте", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = onReplaceSlotClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Заменить")
                }
                OutlinedButton(onClick = onDeleteSlotClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Удалить")
                }
            }
        }
    }

    if (uiState.orientationPromptLayoutId != null) {
        ModalBottomSheet(onDismissRequest = onOrientationCancelClick) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Некоторые фото не подходят под ориентацию этого макета. Заменить автоматически?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(onClick = onOrientationReplaceClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Заменить")
                }
                OutlinedButton(onClick = onOrientationCancelClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Отмена")
                }
            }
        }
    }
}

@Composable
private fun EditorPage(
    page: AlbumPage?,
    photosById: Map<String, SelectedPhoto>,
    selectedSlotId: String?,
    selectedStickerId: String?,
    onSlotClick: (AlbumSlot) -> Unit,
    onEmptySlotClick: (AlbumSlot) -> Unit,
    onSlotTransform: (String, Float, Float, Float) -> Unit,
    onStickerSelected: (AlbumSticker) -> Unit,
    onStickerTransform: (String, Float, Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            if (page != null) {
                Box(modifier = Modifier.padding(16.dp)) {
                    AlbumPageRenderer(
                        page = page,
                        photosById = photosById,
                        selectedSlotId = selectedSlotId,
                        selectedStickerId = selectedStickerId,
                        onSlotClick = onSlotClick,
                        onEmptySlotClick = onEmptySlotClick,
                        onStickerClick = onStickerSelected,
                        modifier = Modifier.fillMaxSize(),
                        slotModifier = { slot ->
                            if (slot.id == selectedSlotId) {
                                Modifier.pointerInput(slot.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        onSlotTransform(slot.id, zoom, pan.x / size.width, pan.y / size.height)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        },
                        stickerModifier = { sticker ->
                            if (sticker.id == selectedStickerId) {
                                Modifier.pointerInput(sticker.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        onStickerTransform(sticker.id, pan.x / size.width, pan.y / size.height, zoom)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorBottomPanel(
    uiState: AlbumEditorUiState,
    photos: List<SelectedPhoto>,
    onTabClick: (EditorTab) -> Unit,
    onSaveClick: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onPickFromDeviceClick: () -> Unit,
    onLayoutClick: (String) -> Unit,
    onStickerClick: (String) -> Unit,
    onDeleteStickerClick: () -> Unit
) {
    Surface(
        color = Color(0xFF242424),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorTab.entries.forEach { tab ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTabClick(tab) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(tab.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .fillMaxWidth()
                                .background(if (uiState.activeTab == tab) Color.White else Color.Transparent)
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(0.7f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить страницу", tint = Color.White)
                    }
                    if (uiState.hasUnsavedChanges) {
                        Box(
                            modifier = Modifier
                                .padding(top = 9.dp, end = 4.dp)
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE85D75))
                        )
                    }
                }
            }

            when (uiState.activeTab) {
                EditorTab.Gallery -> GalleryStrip(
                    photos = photos,
                    onPhotoClick = onPhotoClick,
                    onPickFromDeviceClick = onPickFromDeviceClick
                )
                EditorTab.Layout -> LayoutStrip(uiState = uiState, onLayoutClick = onLayoutClick)
                EditorTab.Sticker -> StickerStrip(
                    onStickerClick = onStickerClick,
                    selectedStickerId = uiState.selectedStickerId,
                    onDeleteStickerClick = onDeleteStickerClick
                )
            }
        }
    }
}

@Composable
private fun GalleryStrip(
    photos: List<SelectedPhoto>,
    onPhotoClick: (String) -> Unit,
    onPickFromDeviceClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onPickFromDeviceClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Выбрать из галереи телефона",
                        tint = Color.White
                    )
                    Text("Телефон", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = Uri.parse(photo.uriString),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPhotoClick(photo.id) }
            )
        }
    }
}

@Composable
private fun LayoutStrip(
    uiState: AlbumEditorUiState,
    onLayoutClick: (String) -> Unit
) {
    val page = uiState.editablePage
    val photoCount = page?.slots?.count { it.photoId != null } ?: 0
    val layouts = AlbumLayouts.forPhotoCount(photoCount)
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(layouts, key = { it.id }) { layout ->
            LayoutThumbnail(
                layoutId = layout.id,
                active = page?.layoutId == layout.id,
                onClick = { onLayoutClick(layout.id) }
            )
        }
    }
}

@Composable
private fun LayoutThumbnail(
    layoutId: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val layout = AlbumLayouts.require(layoutId)
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(if (active) 2.dp else 1.dp, if (active) Color.White else Color(0xFF777777), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        layout.slots.forEach { slot ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = (slot.rect.left * 64).dp,
                        top = (slot.rect.top * 64).dp,
                        end = ((1f - slot.rect.right) * 64).dp,
                        bottom = ((1f - slot.rect.bottom) * 64).dp
                    )
                    .border(1.dp, if (active) Color.White else Color(0xFF777777))
            )
        }
    }
}

@Composable
private fun StickerStrip(
    selectedStickerId: String?,
    onStickerClick: (String) -> Unit,
    onDeleteStickerClick: () -> Unit
) {
    val stickers = listOf("❤️", "⭐", "🌸", "🎂", "✈️", "📍", "✨", "😊")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(stickers) { sticker ->
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onStickerClick(sticker) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(sticker, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        if (selectedStickerId != null) {
            IconButton(onClick = onDeleteStickerClick) {
                Icon(Icons.Default.Close, contentDescription = "Удалить стикер", tint = Color.White)
            }
        }
    }
}
