package com.example.myapplication.data.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.AlbumPage
import com.example.myapplication.model.AlbumSlot
import com.example.myapplication.model.EditableAlbum
import com.example.myapplication.model.SelectedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class AndroidPdfExporter(
    private val contentResolver: ContentResolver
) : PdfExporter {

    override suspend fun export(album: EditableAlbum, destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val photosById = album.draft.selectedPhotos.associateBy { it.id }

            val document = PdfDocument()
            try {
                album.pages.forEachIndexed { index, page ->
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                    val pdfPage = document.startPage(pageInfo)
                    drawPage(
                        canvas = pdfPage.canvas,
                        page = page,
                        pageNumber = index + 1,
                        totalPages = album.pages.size,
                        photosById = photosById
                    )
                    document.finishPage(pdfPage)
                }

                contentResolver.openOutputStream(destination)?.use { stream ->
                    document.writeTo(stream)
                } ?: error("Не удалось открыть файл для записи")
            } finally {
                document.close()
            }
        }
    }

    private fun drawPage(
        canvas: Canvas,
        page: AlbumPage,
        pageNumber: Int,
        totalPages: Int,
        photosById: Map<String, SelectedPhoto>
    ) {
        canvas.drawColor(PAGE_BACKGROUND_COLOR)

        val frameRect = RectF(
            PAGE_SIDE_PADDING.toFloat(),
            PAGE_TOP_PADDING.toFloat(),
            (PAGE_WIDTH - PAGE_SIDE_PADDING).toFloat(),
            (PAGE_HEIGHT - PAGE_BOTTOM_PADDING).toFloat()
        )
        drawPhotoFrame(canvas = canvas, frameRect = frameRect)

        val contentRect = frameRect.inset(PHOTO_FRAME_INSET)
        val layout = AlbumLayouts.require(page.layoutId)
        val slotsByKey = page.slots.associateBy { it.slotKey }
        layout.slots.forEach { slotSpec ->
            val slot = slotsByKey[slotSpec.key]
            val photoRect = RectF(
                contentRect.left + contentRect.width() * slotSpec.rect.left,
                contentRect.top + contentRect.height() * slotSpec.rect.top,
                contentRect.left + contentRect.width() * slotSpec.rect.right,
                contentRect.top + contentRect.height() * slotSpec.rect.bottom
            )
            drawPhoto(
                canvas = canvas,
                slot = slot,
                photo = slot?.photoId?.let(photosById::get),
                destination = photoRect
            )
            if (slot?.caption?.isNotBlank() == true) {
                drawCaption(canvas = canvas, slot = slot, photoRect = photoRect)
            }
        }
        page.stickers.sortedBy { it.zIndex }.forEach { sticker ->
            drawSticker(
                canvas = canvas,
                sticker = sticker.sticker,
                x = contentRect.left + contentRect.width() * sticker.x,
                y = contentRect.top + contentRect.height() * sticker.y,
                scale = sticker.scale,
                rotation = sticker.rotation
            )
        }
        drawPageCounter(canvas = canvas, pageNumber = pageNumber, totalPages = totalPages)
    }

    private fun drawPhotoFrame(
        canvas: Canvas,
        frameRect: RectF
    ) {
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        canvas.drawRoundRect(frameRect, FRAME_RADIUS, FRAME_RADIUS, framePaint)
    }

    private fun drawPhoto(
        canvas: Canvas,
        slot: AlbumSlot?,
        photo: SelectedPhoto?,
        destination: RectF
    ) {
        val bitmap = photo?.let {
            decodeScaledBitmap(
                uri = Uri.parse(it.uriString),
                requestedWidth = destination.width().toInt(),
                requestedHeight = destination.height().toInt()
            )
        }

        val photoPath = Path().apply {
            addRoundRect(destination, PHOTO_RADIUS, PHOTO_RADIUS, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(photoPath)

        if (bitmap == null) {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E5E7EB")
            }
            canvas.drawRect(destination, placeholderPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6B7280")
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                if (slot?.photoId == null) "+" else "Фото недоступно",
                destination.centerX(),
                destination.centerY(),
                textPaint
            )
            canvas.restore()
            return
        }

        val srcRect = transformedCenterCropSourceRect(
            srcWidth = bitmap.width,
            srcHeight = bitmap.height,
            dstWidth = destination.width().toInt(),
            dstHeight = destination.height().toInt(),
            scale = slot?.cropScale ?: 1f,
            offsetX = slot?.cropOffsetX ?: 0f,
            offsetY = slot?.cropOffsetY ?: 0f
        )
        canvas.drawBitmap(bitmap, srcRect, destination, null)
        bitmap.recycle()
        canvas.restore()
    }

    private fun drawCaption(
        canvas: Canvas,
        slot: AlbumSlot,
        photoRect: RectF
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        val caption = slot.caption.ifBlank { return }
        val availableWidth = min(
            photoRect.width() - CAPTION_HORIZONTAL_PADDING * 2,
            MAX_CAPTION_BOX_WIDTH
        ).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(caption, 0, caption.length, textPaint, availableWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.04f)
            .build()

        val captionPath = Path().apply {
            addRoundRect(photoRect, PHOTO_RADIUS, PHOTO_RADIUS, Path.Direction.CW)
        }
        val captionBoxTop = max(
            photoRect.top + CAPTION_MIN_TOP_PADDING,
            photoRect.bottom - CAPTION_BOTTOM_PADDING - layout.height - CAPTION_BOX_VERTICAL_PADDING * 2
        )
        val captionBoxRect = RectF(
            photoRect.centerX() - layout.width / 2f - CAPTION_BOX_HORIZONTAL_PADDING,
            captionBoxTop,
            photoRect.centerX() + layout.width / 2f + CAPTION_BOX_HORIZONTAL_PADDING,
            captionBoxTop + layout.height + CAPTION_BOX_VERTICAL_PADDING * 2
        )
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
        }

        canvas.save()
        canvas.clipPath(captionPath)
        canvas.drawRoundRect(captionBoxRect, CAPTION_BOX_RADIUS, CAPTION_BOX_RADIUS, backgroundPaint)
        canvas.translate(
            photoRect.centerX() - layout.width / 2f,
            captionBoxTop + CAPTION_BOX_VERTICAL_PADDING
        )
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawPageCounter(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 28f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "$pageNumber / $totalPages",
            (PAGE_WIDTH - PAGE_SIDE_PADDING).toFloat(),
            (PAGE_HEIGHT - 36).toFloat(),
            textPaint
        )
    }

    private fun drawSticker(canvas: Canvas, sticker: String, x: Float, y: Float, scale: Float, rotation: Float) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f * scale.coerceIn(0.4f, 4f)
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(rotation, x, y)
        val centeredBaseline = y - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(sticker, x, centeredBaseline, textPaint)
        canvas.restore()
    }

    private fun decodeScaledBitmap(
        uri: Uri,
        requestedWidth: Int,
        requestedHeight: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = calculateInSampleSize(
                srcWidth = bounds.outWidth,
                srcHeight = bounds.outHeight,
                requestedWidth = requestedWidth,
                requestedHeight = requestedHeight
            )
        }

        return contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
    }

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): Int {
        var sampleSize = 1
        if (srcHeight > requestedHeight || srcWidth > requestedWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2

            while (halfHeight / sampleSize >= requestedHeight && halfWidth / sampleSize >= requestedWidth) {
                sampleSize *= 2
                halfHeight = max(halfHeight, 1)
                halfWidth = max(halfWidth, 1)
            }
        }
        return max(sampleSize, 1)
    }

    private fun transformedCenterCropSourceRect(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Rect {
        val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
        val dstAspect = dstWidth.toFloat() / dstHeight.toFloat()

        val base = if (srcAspect > dstAspect) {
            val targetWidth = (srcHeight * dstAspect).toInt()
            val left = (srcWidth - targetWidth) / 2
            Rect(left, 0, left + targetWidth, srcHeight)
        } else {
            val targetHeight = (srcWidth / dstAspect).toInt()
            val top = (srcHeight - targetHeight) / 2
            Rect(0, top, srcWidth, top + targetHeight)
        }
        val safeScale = scale.coerceIn(1f, 4f)
        val cropWidth = (base.width() / safeScale).toInt().coerceAtLeast(1)
        val cropHeight = (base.height() / safeScale).toInt().coerceAtLeast(1)
        val maxLeft = srcWidth - cropWidth
        val maxTop = srcHeight - cropHeight
        val centerX = (base.centerX() + offsetX.coerceIn(-1f, 1f) * base.width() * 0.35f)
            .coerceIn(cropWidth / 2f, srcWidth - cropWidth / 2f)
        val centerY = (base.centerY() + offsetY.coerceIn(-1f, 1f) * base.height() * 0.35f)
            .coerceIn(cropHeight / 2f, srcHeight - cropHeight / 2f)
        val left = (centerX - cropWidth / 2f).toInt().coerceIn(0, maxLeft)
        val top = (centerY - cropHeight / 2f).toInt().coerceIn(0, maxTop)
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }

    private companion object {
        const val PAGE_WIDTH = 1240
        const val PAGE_HEIGHT = 1754
        val PAGE_BACKGROUND_COLOR: Int = Color.parseColor("#F6F1EB")
        const val PAGE_SIDE_PADDING = 72
        const val PAGE_TOP_PADDING = 90
        const val PAGE_BOTTOM_PADDING = 160
        const val PHOTO_FRAME_INSET = 18f
        const val CAPTION_HORIZONTAL_PADDING = 56f
        const val CAPTION_BOTTOM_PADDING = 44f
        const val CAPTION_MIN_TOP_PADDING = 40f
        const val CAPTION_BOX_HORIZONTAL_PADDING = 28f
        const val CAPTION_BOX_VERTICAL_PADDING = 16f
        const val CAPTION_BOX_RADIUS = 18f
        const val MAX_CAPTION_BOX_WIDTH = 760f
        const val FRAME_RADIUS = 28f
        const val PHOTO_RADIUS = 24f
    }
}

private fun RectF.inset(amount: Float): RectF = RectF(
    left + amount,
    top + amount,
    right - amount,
    bottom - amount
)
