package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.model.BookDraftUiState
import com.example.myapplication.model.SelectedPhoto
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
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "${uiState.selectedPhotos.size} из ${BookCreationViewModel.PHOTO_LIMIT}")
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.selectedPhotos.isEmpty()) {
            EmptyState(
                onAddMoreClick = onAddMoreClick,
                onBackClick = onBackClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.selectedPhotos, key = { it.id }) { photo ->
                        PhotoGridItem(
                            photo = photo,
                            onRemoveClick = { onRemoveClick(photo.id) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onAddMoreClick,
                        enabled = uiState.selectedPhotos.size < BookCreationViewModel.PHOTO_LIMIT,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Добавить еще")
                    }
                    Button(
                        onClick = onContinueClick,
                        enabled = uiState.canContinue && !uiState.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = "Продолжить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: SelectedPhoto,
    onRemoveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
    ) {
        AsyncImage(
            model = Uri.parse(photo.uriString),
            contentDescription = photo.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .size(120.dp),
            contentScale = ContentScale.Crop
        )

        FilledIconButton(
            onClick = onRemoveClick,
            modifier = Modifier
                .padding(6.dp)
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Удалить"
            )
        }
    }
}

@Composable
private fun EmptyState(
    onAddMoreClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Вы еще не выбрали фото")
        Button(onClick = onAddMoreClick, modifier = Modifier.padding(top = 12.dp)) {
            Text(text = "Добавить фото")
        }
        TextButton(onClick = onBackClick) {
            Text(text = "Вернуться на главный")
        }
    }
}