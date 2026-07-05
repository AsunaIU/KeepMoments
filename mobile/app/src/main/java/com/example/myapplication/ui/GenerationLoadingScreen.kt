package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.KeepMomentsTheme

@Composable
fun GenerationLoadingScreen(
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) {
        // Экран заблокирован во время генерации
    }

    val isDarkTheme = isSystemInDarkTheme()

    Box(modifier = modifier.fillMaxSize()) {

        Image(
            painter = if (isDarkTheme) {
                painterResource(R.drawable.loading_screen_dark) // для тёмной темы
            } else {
                painterResource(R.drawable.loading_screen) // для светлой
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = if (isDarkTheme) {
                Alignment.Center // без смещения
            } else {
                BiasAlignment(horizontalBias = -1f, verticalBias = 0f) // смещение только в светлой
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.32f),
                            Color.Black.copy(alpha = 0.58f)
                        )
                    )
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp),
            color = Color.White.copy(alpha = 0.65f),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "Идет загрузка",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E2A2F),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Создаем альбом из ваших фото",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4B5A60),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color(0xFF1F1919),
                    trackColor = Color(0xFFF8F6F6)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GenerationLoadingScreenLightPreview() {
    KeepMomentsTheme {
        GenerationLoadingScreen()
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun GenerationLoadingScreenDarkPreview() {
    KeepMomentsTheme {
        GenerationLoadingScreen()
    }
}