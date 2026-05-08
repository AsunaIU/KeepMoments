package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.Blue40
import com.example.myapplication.ui.theme.appBackground
import com.example.myapplication.ui.theme.appSurface
import com.example.myapplication.ui.theme.appTextSecondary
import com.example.myapplication.viewmodel.DraftEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPromptScreen(
    storyPrompt: String,
    generateCaptions: Boolean,
    isCreatingDraft: Boolean,
    onBackClick: () -> Unit,
    onStoryPromptChange: (String) -> Unit,
    onGenerateCaptionsChange: (Boolean) -> Unit,
    onPickPhotosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = appBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Описание альбома", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = appBackground(),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = onPickPhotosClick,
                    enabled = !isCreatingDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Выбрать фото", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Опишите события, настроение и людей, которых важно отразить в фотокниге.",
                color = appTextSecondary(),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = storyPrompt,
                onValueChange = { onStoryPromptChange(it.take(DraftEditorViewModel.STORY_PROMPT_LIMIT)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                label = { Text("Описание") },
                placeholder = { Text("Например: семейное путешествие к морю, много солнца, спокойное настроение") },
                supportingText = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text("До ${DraftEditorViewModel.STORY_PROMPT_LIMIT} символов", color = appTextSecondary())
                        Text(
                            text = "${storyPrompt.length}/${DraftEditorViewModel.STORY_PROMPT_LIMIT}",
                            color = appTextSecondary(),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
                maxLines = 8
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = appSurface()),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Генерировать надписи", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Добавим короткие локальные подписи поверх фото.",
                            color = appTextSecondary(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = generateCaptions,
                        onCheckedChange = onGenerateCaptionsChange,
                        modifier = Modifier.size(width = 52.dp, height = 32.dp)
                    )
                }
            }
        }
    }
}
