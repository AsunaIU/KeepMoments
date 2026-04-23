package com.example.myapplication.data.media

class PhotoValidator {

    fun validate(metadata: MediaMetadata): PhotoValidationResult {
        if (!isSupportedFormat(metadata)) {
            return PhotoValidationResult(
                isValid = false,
                message = "Поддерживаются только JPG и PNG"
            )
        }

        if ((metadata.sizeBytes ?: 0L) > MAX_SIZE_BYTES) {
            return PhotoValidationResult(
                isValid = false,
                message = "Размер файла больше 10 МБ"
            )
        }

        val width = metadata.width ?: 0
        val height = metadata.height ?: 0
        val shortestSide = minOf(width, height)
        if (shortestSide < MIN_SIDE_PX || width * height < MIN_TOTAL_PIXELS) {
            return PhotoValidationResult(
                isValid = false,
                message = "Фото слишком маленькое для хорошего качества"
            )
        }

        return PhotoValidationResult(isValid = true, message = null)
    }

    private fun isSupportedFormat(metadata: MediaMetadata): Boolean {
        val mimeType = metadata.mimeType?.lowercase()
        if (mimeType in SUPPORTED_MIME_TYPES) {
            return true
        }

        val extension = metadata.displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()

        return extension in SUPPORTED_EXTENSIONS
    }

    companion object {
        private const val MIN_SIDE_PX = 400
        private const val MIN_TOTAL_PIXELS = 240_000
        private const val MAX_SIZE_BYTES = 10 * 1024 * 1024L
        private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png")
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }
}

data class PhotoValidationResult(
    val isValid: Boolean,
    val message: String?
)
