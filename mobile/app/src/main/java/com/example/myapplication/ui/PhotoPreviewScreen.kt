package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.model.BookDraftUiState
import com.example.myapplication.model.SelectedPhoto
import com.example.myapplication.ui.theme.Border
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.viewmodel.BookCreationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    uiState: BookDraftUiState,
    onBackClick: () -> Unit,
    onAddMoreClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Выбор фото",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${uiState.selectedPhotos.size}/${BookCreationViewModel.PHOTO_LIMIT} выбрано",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = ScreenBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (uiState.invalidPhotos.isNotEmpty()) {
                        Text(
                            text = "Фото с предупреждением останутся в сетке, но не попадут в генерацию",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB42318)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = onContinueClick,
                        enabled = uiState.canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Далее",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (uiState.selectedPhotos.isEmpty()) {
            EmptyPhotosState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onAddMoreClick = onAddMoreClick
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Подходящий формат: ${uiState.validPhotos.size} · С ошибкой: ${uiState.invalidPhotos.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(uiState.selectedPhotos, key = { it.id }) { photo ->
                        SelectedPhotoGridItem(
                            photo = photo,
                            onRemoveClick = { onRemoveClick(photo.id) }
                        )
                    }

                    item {
                        AddMoreTile(
                            enabled = uiState.selectedPhotos.size < BookCreationViewModel.PHOTO_LIMIT,
                            onClick = onAddMoreClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPhotosState(
    modifier: Modifier = Modifier,
    onAddMoreClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Пока нет выбранных фото",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Добавьте хотя бы одно фото, чтобы перейти дальше",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onAddMoreClick) {
            Text("Выбрать фото")
        }
    }
}

@Composable
private fun SelectedPhotoGridItem(
    photo: SelectedPhoto,
    onRemoveClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .then(
                    if (photo.isValid) {
                        Modifier
                    } else {
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = Color(0xFFD92D20),
                                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                )
        ) {
            AsyncImage(
                model = Uri.parse(photo.uriString),
                contentDescription = photo.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            RemoveButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                onClick = onRemoveClick
            )

            if (!photo.isValid) {
                WarningBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
        }

        Text(
            text = photo.displayName ?: "Без названия",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (photo.isValid) {
                buildMetaText(photo = photo)
            } else {
                photo.validationMessage ?: "Фото не прошло проверку"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (photo.isValid) TextSecondary else Color(0xFFD92D20),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RemoveButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Удалить фото",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun WarningBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFD92D20))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⚠",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AddMoreTile(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(18.dp))
                .dashedRoundedBorder(
                    color = Border,
                    strokeWidth = 1.5.dp
                )
                .background(Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Добавить фото",
                    tint = TextSecondary
                )
                Text(
                    text = "Добавить",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        Text(
            text = if (enabled) "Открыть галерею" else "Лимит достигнут",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

private fun buildMetaText(photo: SelectedPhoto): String {
    val width = photo.width ?: 0
    val height = photo.height ?: 0
    val sizeMb = photo.sizeBytes?.div(1024f * 1024f)

    return if (sizeMb != null) {
        "${width}x${height} · ${String.format("%.1f", sizeMb)} МБ"
    } else {
        "${width}x${height}"
    }
}

private fun Modifier.dashedRoundedBorder(
    color: Color,
    strokeWidth: Dp,
) = drawBehind {
    val strokePx = strokeWidth.toPx()
    val dash = floatArrayOf(10f, 8f)
    val effect = PathEffect.dashPathEffect(dash, 0f)

    val left = strokePx / 2
    val top = strokePx / 2
    val right = size.width - strokePx / 2
    val bottom = size.height - strokePx / 2
    val radius = CornerRadius(18.dp.toPx(), 18.dp.toPx())

    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
        cornerRadius = radius,
        style = Stroke(width = strokePx, pathEffect = effect)
    )
}
