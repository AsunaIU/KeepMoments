package com.example.myapplication.ui

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.AlbumPage
import com.example.myapplication.model.AlbumSlot
import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.model.EditableAlbum
import com.example.myapplication.model.SelectedPhoto
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderedBookScreen(
    album: EditableAlbum?,
    isExporting: Boolean,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEditClick: () -> Unit,
    onDownloadPdfClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = album?.pages.orEmpty()
    val photosById = album?.draft?.selectedPhotos.orEmpty().associateBy { it.id }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Scaffold(
        modifier = modifier,
        containerColor = ScreenBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (pages.isEmpty()) "Книга" else "${pagerState.currentPage + 1} / ${pages.size}",
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
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditClick,
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Редактировать", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onDownloadPdfClick,
                        enabled = pages.isNotEmpty() && !isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(18.dp)
                            )
                        } else {
                            Text("Скачать PDF", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Страницы пока не готовы",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { pageIndex ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            AlbumPageRenderer(
                                page = pages[pageIndex],
                                photosById = photosById,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Свайпайте влево и вправо, чтобы перелистывать страницы",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewRenderedBookScreen() {
    val photo = SelectedPhoto("1", "", "preview.jpg", null, null, 1200, 1800, true, null)
    KeepMomentsTheme {
        RenderedBookScreen(
            album = EditableAlbum(
                draft = BookDraft("draft-1", DraftOwnerType.GUEST, null, null, 0L, 0L, listOf(photo)),
                pages = listOf(
                    AlbumPage(
                        id = "page-1",
                        draftId = "draft-1",
                        position = 0,
                        layoutId = AlbumLayouts.SINGLE_PORTRAIT_FULL,
                        slots = listOf(AlbumSlot("slot-1", "page-1", "main", photo.id)),
                        stickers = emptyList()
                    )
                )
            ),
            isExporting = false,
            onBackClick = {},
            onProfileClick = {},
            onEditClick = {},
            onDownloadPdfClick = {}
        )
    }
}
