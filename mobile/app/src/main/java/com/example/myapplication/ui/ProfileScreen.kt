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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.model.BookDraftSummary
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.ui.theme.Blue40
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.viewmodel.ProfileUiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileUiState: ProfileUiState,
    latestDraft: BookDraftSummary?,
    hasMoreDrafts: Boolean,
    onBackClick: () -> Unit,
    onCreateNewClick: () -> Unit,
    onOpenDraftClick: (String) -> Unit,
    onAllBooksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenBg,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Мой профиль",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                ProfileHeaderCard(uiState = profileUiState)
            }

            item {
                SectionHeader(
                    title = "Мои фотокниги",
                    actionLabel = "Создать новую",
                    onActionClick = onCreateNewClick
                )
            }

            item {
                if (latestDraft != null) {
                    LatestBookCard(
                        draft = latestDraft,
                        onEditClick = { onOpenDraftClick(latestDraft.id) }
                    )
                } else {
                    EmptyBooksCard(onCreateNewClick = onCreateNewClick)
                }
            }

            if (hasMoreDrafts) {
                item {
                    TextButton(onClick = onAllBooksClick) {
                        Text("Все фотокниги")
                    }
                }
            }

            item {
                SectionHeader(title = "Меню")
            }

            item {
                MenuCard(onSettingsClick = onSettingsClick)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(uiState: ProfileUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(
                avatarUriString = uiState.avatarUriString,
                initials = uiState.initials,
                modifier = Modifier.size(68.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = uiState.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = uiState.emailLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun LatestBookCard(
    draft: BookDraftSummary,
    onEditClick: () -> Unit
) {
    val savedAtLabel = remember(draft.updatedAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ru"))
            .format(Date(draft.updatedAt))
    }
    val title = draft.coverDisplayName
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.takeIf { it.isNotBlank() }
        ?: "Моя фотокнига"

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BookCoverPreview(
                        coverUriString = draft.coverUriString,
                        modifier = Modifier.size(82.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Сохранено: $savedAtLabel • ${draft.photoCount} фото • Черновик",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = onEditClick,
                            modifier = Modifier.padding(start = 0.dp)
                        ) {
                            Text("Редактировать макет")
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFFCE7A7)
                ) {
                    Text(
                        text = "ЧЕРНОВИК",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8A6110),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue40,
                    disabledContainerColor = Blue40.copy(alpha = 0.42f),
                    disabledContentColor = Color.White
                )
            ) {
                Text("Оформить печать")
            }
        }
    }
}

@Composable
private fun EmptyBooksCard(
    onCreateNewClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "У вас пока нет фотокниг",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Создайте первую фотокнигу, и она появится в профиле.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            TextButton(onClick = onCreateNewClick) {
                Text("Создать новую")
            }
        }
    }
}

@Composable
private fun MenuCard(
    onSettingsClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            MenuRow(
                icon = Icons.Default.History,
                title = "История заказов",
                enabled = false,
                onClick = null
            )
            DividerLine()
            MenuRow(
                icon = Icons.Default.CreditCard,
                title = "Способы оплаты",
                enabled = false,
                onClick = null
            )
            DividerLine()
            MenuRow(
                icon = Icons.Default.Settings,
                title = "Настройки",
                enabled = true,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f)
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodyLarge
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) TextSecondary.copy(alpha = 0.75f) else TextSecondary.copy(alpha = 0.32f)
        )
    }
}

@Composable
private fun DividerLine() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF0F1F5))
    )
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    avatarUriString: String?,
    initials: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF7A6AF6)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUriString != null) {
            AsyncImage(
                model = Uri.parse(avatarUriString),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BookCoverPreview(
    coverUriString: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF0F1F5)),
        contentAlignment = Alignment.Center
    ) {
        if (coverUriString != null) {
            AsyncImage(
                model = Uri.parse(coverUriString),
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
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F7FB)
@Composable
private fun PreviewProfileScreen() {
    KeepMomentsTheme {
        ProfileScreen(
            profileUiState = ProfileUiState(
                displayName = "Мария В.",
                email = "maria.v@example.com",
                initials = "МВ",
                isAuthenticated = true
            ),
            latestDraft = BookDraftSummary(
                id = "1",
                ownerType = DraftOwnerType.USER,
                ownerUserId = 1L,
                updatedAt = System.currentTimeMillis(),
                photoCount = 35,
                validPhotoCount = 35,
                coverUriString = null,
                coverDisplayName = "Отпуск в горах.jpg"
            ),
            hasMoreDrafts = true,
            onBackClick = {},
            onCreateNewClick = {},
            onOpenDraftClick = {},
            onAllBooksClick = {},
            onSettingsClick = {}
        )
    }
}
