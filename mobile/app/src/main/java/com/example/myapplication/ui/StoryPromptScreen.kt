package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.BookDraftUiState
import com.example.myapplication.ui.theme.ScreenBg
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.viewmodel.BookCreationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPromptScreen(
    uiState: BookDraftUiState,
    onBackClick: () -> Unit,
    onStoryPromptChanged: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Пожелания к книге",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
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
                Button(
                    onClick = onGenerateClick,
                    enabled = uiState.validPhotos.isNotEmpty() && !uiState.isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(48.dp)
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Сгенерировать",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "В генерацию пойдёт ${uiState.validPhotos.size} фото",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Опишите основную линию сюжета, важные события и героев",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = uiState.storyPrompt,
                onValueChange = onStoryPromptChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("Описание сюжета") },
                placeholder = { Text("Например: семейное путешествие к морю, много солнца, спокойное настроение") },
                supportingText = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "До ${BookCreationViewModel.STORY_PROMPT_LIMIT} символов",
                            color = TextSecondary
                        )
                        Text(
                            text = "${uiState.storyPrompt.length}/${BookCreationViewModel.STORY_PROMPT_LIMIT}",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = TextSecondary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                singleLine = false,
                maxLines = 8
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Создаём книгу из выбранных фото...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (uiState.generationError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(Color(0xFFFEE4E2), shape = MaterialTheme.shapes.large)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiState.generationError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB42318),
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onRetryClick) {
                        Text("Повторить")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
