package com.example.myapplication.data.media

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

class PhotoLocalStorage(
    private val contentResolver: ContentResolver,
    private val storageDir: File
) {

    fun copyToLocalStorage(
        sourceUri: Uri,
        displayName: String?,
        mimeType: String?
    ): Uri {
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw IOException("Не удалось подготовить локальное хранилище фото")
        }

        val target = File(storageDir, "${UUID.randomUUID()}${extensionFor(displayName, mimeType)}")
        runCatching {
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                target.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Не удалось открыть выбранное фото")
        }.onFailure {
            target.delete()
        }.getOrThrow()

        return Uri.fromFile(target)
    }

    fun deleteLocalCopy(uri: Uri): Boolean {
        return localFileFor(uri)?.delete() ?: false
    }

    fun deleteUnusedLocalCopies(activeUriStrings: Collection<String>) {
        val activePaths = activeUriStrings
            .mapNotNull { uriString -> localFileFor(Uri.parse(uriString))?.path }
            .toHashSet()

        storageDir.listFiles()?.forEach { file ->
            runCatching {
                val localFile = file.canonicalFile
                if (localFile.isFile && localFile.path !in activePaths) {
                    localFile.delete()
                }
            }
        }
    }

    fun deleteAll() {
        storageDir.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
    }

    private fun extensionFor(displayName: String?, mimeType: String?): String {
        val nameExtension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in SUPPORTED_EXTENSIONS }

        val extension = nameExtension ?: when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return ".$extension"
    }

    private fun localFileFor(uri: Uri): File? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null

        return runCatching {
            val storageRoot = storageDir.canonicalFile
            val file = File(path).canonicalFile
            file.takeIf { it.path.startsWith(storageRoot.path + File.separator) }
        }.getOrNull()
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
