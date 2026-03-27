package com.example.myapplication.ui

import androidx.compose.ui.geometry.CornerRadius
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.R
import com.example.myapplication.model.BookDraftUiState
import com.example.myapplication.model.SelectedPhoto
import com.example.myapplication.ui.theme.Blue40
import com.example.myapplication.ui.theme.Border
import com.example.myapplication.ui.theme.KeepMomentsTheme
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
    onAutoSelectClick: () -> Unit = onAddMoreClick,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Все", "Люди", "Места", "Лучшие")
    val selectedTab = remember { mutableStateOf(0) }

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
                            text = "${uiState.selectedPhotos.size}/${BookCreationViewModel.PHOTO_LIMIT} выбрано",
                            fontSize = 13.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
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
                Button(
                    onClick = onContinueClick,
                    enabled = uiState.canContinue && !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue40,
                        disabledContainerColor = Blue40.copy(alpha = 0.45f)
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Продолжить",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab.value == index,
                        onClick = { selectedTab.value = index },
                        label = { Text(text = label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue40,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = TextSecondary
                        ),
                        border = if (selectedTab.value == index) null else BorderStroke(1.dp, Border),
                        shape = RoundedCornerShape(999.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.selectedPhotos, key = { it.id }) { photo ->
                    SelectedPhotoTile(
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

            Spacer(Modifier.weight(1f))
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
            .size(92.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = Uri.parse(photo.uriString),
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .padding(6.dp)
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(Blue40)
                .clickable(onClick = onRemoveClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
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
            .size(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .dashedRoundedBorder(
                color = Border,
                strokeWidth = 1.5.dp
            )
            .background(Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = "Добавить фото",
            tint = TextSecondary
        )
    }
}

private fun Modifier.dashedRoundedBorder(
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
) = drawBehind {
    val strokePx = strokeWidth.toPx()
    val dash = floatArrayOf(10f, 8f)
    val effect = PathEffect.dashPathEffect(dash, 0f)

    val left = strokePx / 2
    val top = strokePx / 2
    val right = size.width - strokePx / 2
    val bottom = size.height - strokePx / 2
    val radius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
        cornerRadius = radius,
        style = Stroke(width = strokePx, pathEffect = effect)
    )
}

@Composable
private fun SelectedPhotoTilePreview(
    resId: Int
) {
    KeepMomentsTheme {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(resId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Blue40),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFFF8F8FC)
@Composable
fun PhotoPreviewScreenPreview() {
    KeepMomentsTheme {
        Scaffold(
            containerColor = ScreenBg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Выбор фото",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "3/50 выбрано",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    }
                )
            },
            bottomBar = {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Продолжить")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Все", "Люди", "Места", "Лучшие").forEachIndexed { index, label ->
                        FilterChip(
                            selected = index == 0,
                            onClick = {},
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SelectedPhotoTilePreview(
                            resId = R.drawable.photo1
                        )
                    }
                    item {
                        SelectedPhotoTilePreview(
                            resId = R.drawable.photo2
                        )
                    }
                    item {
                        SelectedPhotoTilePreview(
                            resId = R.drawable.photo3
                        )
                    }
                    item {
                        AddMoreTile(enabled = true, onClick = {})
                    }
                }
            }
        }
    }
}