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
import com.example.myapplication.model.BookPage
import com.example.myapplication.model.BookSlot
import com.example.myapplication.model.RenderedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

interface PdfExporter {
    suspend fun export(book: RenderedBook, destination: Uri): Result<Unit>
}

class AndroidPdfExporter(
    private val contentResolver: ContentResolver
) : PdfExporter {

    override suspend fun export(book: RenderedBook, destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val outputStream = contentResolver.openOutputStream(destination)
                ?: error("Не удалось открыть файл для записи")

            val document = PdfDocument()
            try {
                book.filledTemplate.pages.forEachIndexed { index, page ->
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                    val pdfPage = document.startPage(pageInfo)
                    drawPage(
                        canvas = pdfPage.canvas,
                        page = page,
                        pageNumber = index + 1,
                        totalPages = book.filledTemplate.pages.size
                    )
                    document.finishPage(pdfPage)
                }

                outputStream.use { stream ->
                    document.writeTo(stream)
                }
            } finally {
                document.close()
            }
        }
    }

    private fun drawPage(
        canvas: Canvas,
        page: BookPage,
        pageNumber: Int,
        totalPages: Int
    ) {
        canvas.drawColor(PAGE_BACKGROUND_COLOR)

        val frameRect = RectF(
            PAGE_SIDE_PADDING.toFloat(),
            PAGE_TOP_PADDING.toFloat(),
            (PAGE_WIDTH - PAGE_SIDE_PADDING).toFloat(),
            (PAGE_HEIGHT - PAGE_BOTTOM_PADDING).toFloat()
        )
        drawPhotoFrame(canvas = canvas, frameRect = frameRect)

        if (page.slots.size != 1) {
            drawUnsupportedLayout(
                canvas = canvas,
                frameRect = frameRect,
                pageNumber = pageNumber,
                totalPages = totalPages
            )
            return
        }

        val slot = page.slots.first()
        val photoRect = frameRect.inset(PHOTO_FRAME_INSET)

        drawPhoto(canvas = canvas, slot = slot, destination = photoRect)
        drawCaption(canvas = canvas, slot = slot, photoRect = photoRect)
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
        slot: BookSlot,
        destination: RectF
    ) {
        val bitmap = decodeScaledBitmap(
            uri = Uri.parse(slot.photoId),
            requestedWidth = destination.width().toInt(),
            requestedHeight = destination.height().toInt()
        )

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
                "Фото недоступно",
                destination.centerX(),
                destination.centerY(),
                textPaint
            )
            canvas.restore()
            return
        }

        val srcRect = centerCropSourceRect(
            srcWidth = bitmap.width,
            srcHeight = bitmap.height,
            dstWidth = destination.width().toInt(),
            dstHeight = destination.height().toInt()
        )
        canvas.drawBitmap(bitmap, srcRect, destination, null)
        bitmap.recycle()
        canvas.restore()
    }

    private fun drawCaption(
        canvas: Canvas,
        slot: BookSlot,
        photoRect: RectF
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        val caption = slot.caption.ifBlank { " " }
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

    private fun drawUnsupportedLayout(
        canvas: Canvas,
        frameRect: RectF,
        pageNumber: Int,
        totalPages: Int
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Этот layout пока не поддерживается",
            frameRect.centerX(),
            frameRect.centerY(),
            textPaint
        )
        drawPageCounter(canvas = canvas, pageNumber = pageNumber, totalPages = totalPages)
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

    private fun centerCropSourceRect(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): Rect {
        val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
        val dstAspect = dstWidth.toFloat() / dstHeight.toFloat()

        return if (srcAspect > dstAspect) {
            val targetWidth = (srcHeight * dstAspect).toInt()
            val left = (srcWidth - targetWidth) / 2
            Rect(left, 0, left + targetWidth, srcHeight)
        } else {
            val targetHeight = (srcWidth / dstAspect).toInt()
            val top = (srcHeight - targetHeight) / 2
            Rect(0, top, srcWidth, top + targetHeight)
        }
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
