package com.example.myapplication.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.R
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.ui.theme.ScreenBg

@Composable
fun HomeScreen(
    photos: List<Painter> = emptyList(),
    isAuthenticated: Boolean,
    userEmail: String?,
    isCreatingDraft: Boolean,
    onCreateBookClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAuthClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPhotos = photos.ifEmpty {
        listOf(
            painterResource(R.drawable.photo1),
            painterResource(R.drawable.photo2),
            painterResource(R.drawable.photo3)
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                displayPhotos.take(3).forEachIndexed { index, painter ->
                    val offsetX = when (index) {
                        0 -> (-40).dp
                        1 -> 0.dp
                        else -> 40.dp
                    }
                    val offsetY = when (index) {
                        0 -> (-10).dp
                        1 -> 0.dp
                        else -> 10.dp
                    }
                    val rotation = when (index) {
                        0 -> -10f
                        1 -> 0f
                        else -> 10f
                    }
                    val width = 140.dp
                    val height = 200.dp

                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(height)
                            .offset(x = offsetX, y = offsetY)
                            .graphicsLayer { rotationZ = rotation }
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(4.dp)
                            .zIndex(index.toFloat())
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Соберите идеальную фотокнигу за пару минут",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF1F1F1F)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isAuthenticated) {
                            "Выполнен вход в аккаунт"
                        } else {
                            "Начать можно без регистрации"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F1F1F)
                    )
                    Text(
                        text = if (isAuthenticated) {
                            userEmail ?: "Вы вошли в аккаунт"
                        } else {
                            "Гостевые черновики останутся на устройстве, а для оплаты попросим войти позже."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6E6E6E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCreateBookClick,
                enabled = !isCreatingDraft,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(18.dp),

            ) {
                if (isCreatingDraft) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.width(18.dp).height(18.dp)
                    )
                } else {
                    Text(
                        text = "Создать фотокнигу",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "Мой профиль",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }

        TextButton(
            onClick = if (isAuthenticated) onLogoutClick else onAuthClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (isAuthenticated) {
                    "Выйти из аккаунта"
                } else {
                    "Войти в аккаунт"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewHomeScreen() {
    val photos = listOf(
        painterResource(R.drawable.photo1),
        painterResource(R.drawable.photo2),
        painterResource(R.drawable.photo3)
    )
    KeepMomentsTheme {
        HomeScreen(
            photos = photos,
            isAuthenticated = false,
            userEmail = null,
            isCreatingDraft = false,
            onCreateBookClick = {},
            onProfileClick = {},
            onAuthClick = {},
            onLogoutClick = {}
        )
    }
}
