package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.AlbumPage
import com.example.myapplication.model.AlbumSlot
import com.example.myapplication.model.AlbumSticker
import com.example.myapplication.model.SelectedPhoto
import kotlin.math.roundToInt

@Composable
fun AlbumPageRenderer(
    page: AlbumPage,
    photosById: Map<String, SelectedPhoto>,
    modifier: Modifier = Modifier,
    selectedSlotId: String? = null,
    selectedStickerId: String? = null,
    onSlotClick: ((AlbumSlot) -> Unit)? = null,
    onEmptySlotClick: ((AlbumSlot) -> Unit)? = null,
    onStickerClick: ((AlbumSticker) -> Unit)? = null,
    slotModifier: (AlbumSlot) -> Modifier = { Modifier },
    stickerModifier: (AlbumSticker) -> Modifier = { Modifier }
) {
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        val layout = AlbumLayouts.require(page.layoutId)
        val slotsByKey = page.slots.associateBy { it.slotKey }

        Box(modifier = Modifier.fillMaxSize()) {
            layout.slots.forEach { slotSpec ->
                val slot = slotsByKey[slotSpec.key] ?: return@forEach
                val x = width * slotSpec.rect.left
                val y = height * slotSpec.rect.top
                val slotWidth = width * slotSpec.rect.width
                val slotHeight = height * slotSpec.rect.height
                val photo = slot.photoId?.let(photosById::get)
                AlbumSlotContent(
                    slot = slot,
                    photo = photo,
                    selected = selectedSlotId == slot.id,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                        .size(slotWidth, slotHeight)
                        .then(slotModifier(slot)),
                    onClick = {
                        if (photo == null) {
                            onEmptySlotClick?.invoke(slot)
                        } else {
                            onSlotClick?.invoke(slot)
                        }
                    }
                )
            }

            page.stickers.sortedBy { it.zIndex }.forEach { sticker ->
                Text(
                    text = sticker.sticker,
                    fontSize = (44f * sticker.scale.coerceIn(0.4f, 4f)).sp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                ((width.toPx() * sticker.x) - 28.dp.toPx() * sticker.scale).roundToInt(),
                                ((height.toPx() * sticker.y) - 28.dp.toPx() * sticker.scale).roundToInt()
                            )
                        }
                        .graphicsLayer(rotationZ = sticker.rotation)
                        .then(stickerModifier(sticker))
                        .then(
                            if (selectedStickerId == sticker.id) {
                                Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onStickerClick?.invoke(sticker) }
                )
            }
        }
    }
}

@Composable
private fun AlbumSlotContent(
    slot: AlbumSlot,
    photo: SelectedPhoto?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE7E7E7))
            .then(
                if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photo == null) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Добавить фото",
                tint = Color(0xFF777777)
            )
        } else {
            AsyncImage(
                model = Uri.parse(photo.uriString),
                contentDescription = photo.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = slot.cropScale.coerceIn(1f, 4f),
                        scaleY = slot.cropScale.coerceIn(1f, 4f),
                        translationX = slot.cropOffsetX.coerceIn(-1f, 1f) * 120f,
                        translationY = slot.cropOffsetY.coerceIn(-1f, 1f) * 120f
                    ),
                contentScale = ContentScale.Crop
            )
            if (slot.caption.isNotBlank()) {
                Text(
                    text = slot.caption,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                )
            }
        }
    }
}
