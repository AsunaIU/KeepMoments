package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.model.BookDraftSummary
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.viewmodel.DraftsUiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    uiState: DraftsUiState,
    onBackClick: () -> Unit,
    onOpenDraftClick: (String) -> Unit,
    onDeleteDraftClick: (String) -> Unit,
    onCreateDraftClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Все фотокниги") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (uiState.isAuthenticated) {
                    "Видны гостевые фотокниги и черновики аккаунта ${uiState.userEmail.orEmpty()}"
                } else {
                    "Сейчас видны только гостевые фотокниги. Альбомы аккаунта появятся после входа."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.drafts.isEmpty()) {
                EmptyDraftsState(onCreateDraftClick = onCreateDraftClick)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.drafts, key = { it.id }) { draft ->
                        DraftCard(
                            draft = draft,
                            onOpenClick = { onOpenDraftClick(draft.id) },
                            onDeleteClick = { onDeleteDraftClick(draft.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDraftsState(
    onCreateDraftClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Фотокниг пока нет",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Создайте новую фотокнигу в профиле или на главном экране. После выбора фото она появится здесь автоматически.",
                color = TextSecondary
            )
            TextButton(onClick = onCreateDraftClick) {
                Text("На главный экран")
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: BookDraftSummary,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateText = remember(draft.updatedAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ru"))
            .format(Date(draft.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF0F1F5)),
                contentAlignment = Alignment.Center
            ) {
                if (draft.coverUriString != null) {
                    AsyncImage(
                        model = Uri.parse(draft.coverUriString),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = draft.title
                            ?: draft.coverDisplayName
                            ?.substringBeforeLast('.')
                            ?.replace('_', ' ')
                            ?.replace('-', ' ')
                            ?.takeIf { it.isNotBlank() }
                            ?: "Моя фотокнига",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "${draft.photoCount} фото, валидных ${draft.validPhotoCount}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Обновлён $dateText",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Удалить черновик",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewDraftsScreen() {
    KeepMomentsTheme {
        DraftsScreen(
            uiState = DraftsUiState(
                drafts = listOf(
                    BookDraftSummary(
                        id = "1",
                        ownerType = DraftOwnerType.GUEST,
                        ownerUserId = null,
                        title = "Семейный альбом",
                        updatedAt = System.currentTimeMillis(),
                        photoCount = 12,
                        validPhotoCount = 10,
                        coverUriString = null,
                        coverDisplayName = null
                    )
                )
            ),
            onBackClick = {},
            onOpenDraftClick = {},
            onDeleteDraftClick = {},
            onCreateDraftClick = {}
        )
    }
}
