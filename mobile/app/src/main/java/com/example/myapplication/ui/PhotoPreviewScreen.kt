package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.model.SelectedPhoto
import com.example.myapplication.ui.theme.Blue40
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.viewmodel.DraftEditorUiState
import com.example.myapplication.viewmodel.DraftEditorViewModel

private val InvalidPhotoBorder = Color(0xFFD84C4C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    uiState: DraftEditorUiState,
    onBackClick: () -> Unit,
    onAddMoreClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    onContinueClick: () -> Unit,
    onOpenDraftsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val validPhotosCount = uiState.selectedPhotos.count { it.isValid }

    Scaffold(
        modifier = modifier,
        containerColor = ScreenBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Выбор фото",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                uiState.isLoading -> "Загружаем черновик"
                                uiState.isMissing -> "Черновик недоступен"
                                else -> "${uiState.selectedPhotos.size}/${DraftEditorViewModel.PHOTO_LIMIT} выбрано"
                            },
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!uiState.isMissing) {
                Surface(
                    color = ScreenBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Валидных фото: $validPhotosCount",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = onContinueClick,
                            enabled = uiState.canContinue && !uiState.isGeneratingBook && !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue40,
                                disabledContainerColor = Blue40.copy(alpha = 0.45f)
                            )
                        ) {
                            if (uiState.isGeneratingBook) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Далее",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.isMissing -> {
                MissingDraftState(
                    modifier = Modifier.padding(innerPadding),
                    onOpenDraftsClick = onOpenDraftsClick
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    OwnerHint(ownerType = uiState.ownerType)

                    Spacer(Modifier.height(12.dp))

                    if (uiState.selectedPhotos.isEmpty()) {
                        EmptyDraftPhotosState(onAddMoreClick = onAddMoreClick)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.selectedPhotos, key = { it.id }) { photo ->
                                SelectedPhotoTile(
                                    photo = photo,
                                    onRemoveClick = { onRemoveClick(photo.id) }
                                )
                            }

                            item {
                                AddMoreTile(
                                    enabled = uiState.selectedPhotos.size < DraftEditorViewModel.PHOTO_LIMIT,
                                    onClick = onAddMoreClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerHint(ownerType: DraftOwnerType?) {
    val text = when (ownerType) {
        DraftOwnerType.USER -> "Этот черновик привязан к аккаунту и скроется после выхода."
        DraftOwnerType.GUEST -> "Гостевой черновик останется доступным на устройстве и без входа."
        null -> ""
    }
    if (text.isNotBlank()) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MissingDraftState(
    modifier: Modifier = Modifier,
    onOpenDraftsClick: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Черновик недоступен",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Возможно, он принадлежит другому аккаунту или был удалён.",
                color = TextSecondary
            )
            Button(onClick = onOpenDraftsClick) {
                Text("К списку черновиков")
            }
        }
    }
}

@Composable
private fun EmptyDraftPhotosState(
    onAddMoreClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Черновик пуст",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Добавьте ещё фото, чтобы продолжить.",
                color = TextSecondary
            )
            Button(onClick = onAddMoreClick) {
                Text("Добавить фото")
            }
        }
    }
}

@Composable
private fun SelectedPhotoTile(
    photo: SelectedPhoto,
    onRemoveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (!photo.isValid) {
                    Modifier.border(2.dp, InvalidPhotoBorder, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
    ) {
        AsyncImage(
            model = Uri.parse(photo.uriString),
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemoveClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        if (!photo.isValid) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(InvalidPhotoBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                color = InvalidPhotoBorder.copy(alpha = 0.94f),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = photo.validationMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddMoreTile(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFFD7D3F5), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Добавить фото",
                tint = TextSecondary
            )
            Text(
                text = "Добавить",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F8FC)
@Composable
private fun PhotoPreviewScreenPreview() {
    KeepMomentsTheme {
        PhotoPreviewScreen(
            uiState = DraftEditorUiState(
                draftId = "draft-1",
                ownerType = DraftOwnerType.GUEST,
                selectedPhotos = listOf(
                    SelectedPhoto(
                        id = "1",
                        uriString = "",
                        displayName = "preview.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 1024,
                        width = 1600,
                        height = 1200,
                        isValid = true,
                        validationMessage = null
                    ),
                    SelectedPhoto(
                        id = "2",
                        uriString = "",
                        displayName = "small.png",
                        mimeType = "image/png",
                        sizeBytes = 1024,
                        width = 800,
                        height = 800,
                        isValid = false,
                        validationMessage = "Фото слишком маленькое для хорошего качества"
                    )
                )
            ),
            onBackClick = {},
            onAddMoreClick = {},
            onRemoveClick = {},
            onContinueClick = {},
            onOpenDraftsClick = {}
        )
    }
}
