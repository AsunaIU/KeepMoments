package com.example.myapplication.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoValidatorTest {

    private val validator = PhotoValidator()

    @Test
    fun `accepts a valid jpeg photo`() {
        val result = validator.validate(metadata())

        assertTrue(result.isValid)
        assertNull(result.message)
    }

    @Test
    fun `accepts png photos too`() {
        assertTrue(validator.validate(metadata(mimeType = "image/png")).isValid)
    }

    @Test
    fun `matches the mime type regardless of case`() {
        assertTrue(validator.validate(metadata(mimeType = "IMAGE/JPEG")).isValid)
    }

    @Test
    fun `falls back to the file extension when mime type is missing`() {
        val result = validator.validate(metadata(mimeType = null, displayName = "vacation.PNG"))

        assertTrue(result.isValid)
    }

    @Test
    fun `rejects unsupported formats`() {
        val result = validator.validate(metadata(mimeType = "image/gif", displayName = "anim.gif"))

        assertFalse(result.isValid)
        assertEquals("Поддерживаются только JPG и PNG", result.message)
    }

    @Test
    fun `rejects a file we cannot recognize by mime or extension`() {
        assertFalse(validator.validate(metadata(mimeType = null, displayName = "photo")).isValid)
    }

    @Test
    fun `allows files up to 10 MB`() {
        assertTrue(validator.validate(metadata(sizeBytes = 10 * 1024 * 1024L)).isValid)
    }

    @Test
    fun `rejects files over 10 MB`() {
        val result = validator.validate(metadata(sizeBytes = 10 * 1024 * 1024L + 1))

        assertFalse(result.isValid)
        assertEquals("Размер файла больше 10 МБ", result.message)
    }

    @Test
    fun `does not reject when the size is unknown`() {
        assertTrue(validator.validate(metadata(sizeBytes = null)).isValid)
    }

    @Test
    fun `accepts a photo right on the minimum resolution`() {
        // 400px shortest side and exactly 240k total pixels
        assertTrue(validator.validate(metadata(width = 400, height = 600)).isValid)
    }

    @Test
    fun `rejects photos whose shortest side is too small`() {
        val result = validator.validate(metadata(width = 399, height = 800))

        assertFalse(result.isValid)
        assertEquals("Фото слишком маленькое для хорошего качества", result.message)
    }

    @Test
    fun `rejects photos with too few total pixels`() {
        assertFalse(validator.validate(metadata(width = 500, height = 479)).isValid)
    }

    @Test
    fun `rejects photos without known dimensions`() {
        assertFalse(validator.validate(metadata(width = null, height = null)).isValid)
    }

    private fun metadata(
        displayName: String? = "photo.jpg",
        mimeType: String? = "image/jpeg",
        sizeBytes: Long? = 2_000_000L,
        width: Int? = 1200,
        height: Int? = 900
    ) = MediaMetadata(displayName, mimeType, sizeBytes, width, height)
}
