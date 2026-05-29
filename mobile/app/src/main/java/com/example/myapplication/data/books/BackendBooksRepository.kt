package com.example.myapplication.data.books

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.BookPage
import com.example.myapplication.model.BookSlot
import com.example.myapplication.model.FilledTemplate
import com.example.myapplication.model.RenderedBook
import com.example.myapplication.model.SelectedPhoto
import com.google.gson.JsonElement
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import retrofit2.Response

class BackendBooksRepository(
    private val authRepository: AuthRepository,
    private val templatesApi: TemplatesApi,
    private val photosApi: PhotosApi,
    private val processApi: ProcessApi,
    private val contentResolver: ContentResolver
) : BooksRepository {

    companion object {
        private const val TAG = "BookGeneration"
        private val DESCRIPTION_JSON_TEXT_KEYS = listOf(
            "caption",
            "description",
            "text",
            "summary",
            "title",
            "photo_caption",
            "photoDescription",
            "generated_caption",
            "generatedCaption"
        )
    }

    override suspend fun generateRenderedBook(draft: BookDraft): Result<RenderedBook> {
        return withContext(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "generateRenderedBook start draftId=${draft.id} totalPhotos=${draft.selectedPhotos.size}")
                authRepository.currentSession()
                    ?: run {
                        Log.e(TAG, "generateRenderedBook blocked: no auth session")
                        error("Для создания книги нужен вход в аккаунт")
                    }

                val validPhotos = draft.selectedPhotos.filter(SelectedPhoto::isValid)
                Log.d(TAG, "generateRenderedBook validPhotos=${validPhotos.size}")
                if (validPhotos.isEmpty()) {
                    Log.e(TAG, "generateRenderedBook blocked: no valid photos")
                    error("Добавьте хотя бы одно валидное фото")
                }

                val template = resolveTemplate(validPhotos.size)
                val uploadedPhotos = uploadPhotos(template.id, validPhotos)
                val processResponse = processPhotoOrder(
                    templateId = template.id,
                    uploadedPhotoIds = uploadedPhotos.keys.toList(),
                    userDescription = draft.storyPrompt.orEmpty().trim().ifBlank { "Фотоальбом" }
                )

                buildRenderedBook(
                    draftId = draft.id,
                    templateId = template.id,
                    response = processResponse,
                    uploadedPhotos = uploadedPhotos
                ).also { book ->
                    Log.d(TAG, "generateRenderedBook success draftId=${draft.id} pages=${book.filledTemplate.pages.size}")
                }
            }.onFailure { throwable ->
                Log.e(TAG, "generateRenderedBook failed draftId=${draft.id}: ${throwable.message}", throwable)
            }
        }
    }

    private suspend fun resolveTemplate(photoCount: Int): ProcessTemplateDto {
        val templateId = MobileLayoutsTemplatePreset.TEMPLATE_ID
        Log.d(TAG, "resolveTemplate start photoCount=$photoCount templateId=$templateId")
        val listResponse = templatesApi.listTemplates()
        Log.d(TAG, "resolveTemplate listTemplates code=${listResponse.code()} successful=${listResponse.isSuccessful}")
        if (!listResponse.isSuccessful) {
            val errorMessage = extractErrorMessage(listResponse)
            Log.e(TAG, "resolveTemplate list failed code=${listResponse.code()} error=$errorMessage")
            error("Не удалось получить шаблоны книги: $errorMessage")
        }

        listResponse.body()
            ?.firstOrNull { it.id == templateId }
            ?.let {
                Log.d(TAG, "resolveTemplate found existing templateId=$templateId")
                return it
            }

        val templateRequest = MobileLayoutsTemplatePreset.buildTemplate()
        Log.d(
            TAG,
            "resolveTemplate creating templateId=$templateId pages=${templateRequest.pages.size} firstPageSlots=${templateRequest.pages.firstOrNull()?.slots?.size ?: 0} firstSlotPhotoId=${templateRequest.pages.firstOrNull()?.slots?.firstOrNull()?.photoId}"
        )
        val createResponse = templatesApi.createTemplate(templateRequest)
        if (!createResponse.isSuccessful) {
            val errorMessage = extractErrorMessage(createResponse)
            Log.e(TAG, "resolveTemplate create failed code=${createResponse.code()} error=$errorMessage")
            error("Не удалось создать шаблон книги: $errorMessage")
        }

        return (createResponse.body() ?: templateRequest).also {
            Log.d(TAG, "resolveTemplate created templateId=${it.id}")
        }
    }

    private suspend fun uploadPhotos(
        templateId: String,
        photos: List<SelectedPhoto>
    ): LinkedHashMap<String, UploadedPhoto> {
        Log.d(TAG, "uploadPhotos start templateId=$templateId count=${photos.size}")

        val results = coroutineScope {
            photos.mapIndexed { index, photo ->
                async {
                    Log.d(
                        TAG,
                        "uploadPhoto start index=$index name=${photo.displayName} mime=${photo.mimeType} size=${photo.sizeBytes} uri=${photo.uriString}"
                    )
                    val response = photosApi.uploadPhoto(
                        templateId = templateId.toPlainTextRequestBody(),
                        file = createPhotoPart(photo)
                    )
                    Log.d(TAG, "uploadPhoto response index=$index code=${response.code()} successful=${response.isSuccessful}")
                    if (!response.isSuccessful) {
                        val errorMessage = extractErrorMessage(response)
                        Log.e(TAG, "uploadPhoto failed index=$index error=$errorMessage")
                        error("Не удалось загрузить фото на сервер: $errorMessage")
                    }

                    val uploadedPhoto = response.body()
                        ?: error("Сервер не вернул идентификатор фото")

                    val backendPhotoId = uploadedPhoto.id.toString()
                    val description = extractDescriptionText(uploadedPhoto.descriptionJson)
                    Log.d(
                        TAG,
                        "uploadPhoto success index=$index " +
                            "backendPhotoId=${uploadedPhoto.id} " +
                            "objectKey=${uploadedPhoto.objectKey} " +
                            "fileName=${uploadedPhoto.fileName} " +
                            "contentType=${uploadedPhoto.contentType} " +
                            "templateId=${uploadedPhoto.templateId} " +
                            "hasDescription=${description != null}"
                    )
                    index to Pair(backendPhotoId, UploadedPhoto(
                        localPhotoId = photo.id,
                        backendPhotoId = backendPhotoId,
                        description = description
                    ))
                }
            }.awaitAll()
        }

        val uploadedPhotos = linkedMapOf<String, UploadedPhoto>()
        results.sortedBy { it.first }.forEach { (_, entry) ->
            uploadedPhotos[entry.first] = entry.second
        }

        Log.d(TAG, "uploadPhotos success uploadedIds=${uploadedPhotos.keys}")
        return uploadedPhotos
    }

    private suspend fun processPhotoOrder(
        templateId: String,
        uploadedPhotoIds: List<String>,
        userDescription: String
    ): ProcessResponseDto {
        Log.d(TAG, "processPhotoOrder start templateId=$templateId backendPhotoIds=$uploadedPhotoIds")
        val response = processApi.process(
            ProcessRequestDto(
                templateId = templateId,
                photoIds = uploadedPhotoIds,
                minPhotos = uploadedPhotoIds.size,
                maxPhotos = uploadedPhotoIds.size,
                userDescription = userDescription
            )
        )
        Log.d(TAG, "processPhotoOrder response code=${response.code()} successful=${response.isSuccessful}")
        if (!response.isSuccessful) {
            val errorMessage = extractErrorMessage(response)
            Log.e(TAG, "processPhotoOrder failed error=$errorMessage")
            error("Не удалось собрать книгу: $errorMessage")
        }

        return (response.body() ?: error("Сервер вернул пустой ответ при сборке книги")).also { body ->
            val orderedPhotoIds = body.filledTemplate.pages.flatMap { page -> page.slots.mapNotNull { it.photoId } }
            Log.d(TAG, "processPhotoOrder success pages=${body.filledTemplate.pages.size} orderedPhotoIds=$orderedPhotoIds")
        }
    }

    private fun buildRenderedBook(
        draftId: String,
        templateId: String,
        response: ProcessResponseDto,
        uploadedPhotos: Map<String, UploadedPhoto>
    ): RenderedBook {
        Log.d(TAG, "buildRenderedBook start draftId=$draftId templateId=$templateId")
        val pages = response.filledTemplate.pages.mapIndexedNotNull { index, page ->
            Log.d(TAG, "buildRenderedBook page=${page.id} slots=${page.slots.size}")
            val slots = page.slots.mapNotNull { slot ->
                val backendPhotoId = slot.photoId ?: return@mapNotNull null
                Log.d(TAG, "buildRenderedBook map backendPhotoId=$backendPhotoId localPhotoId=${uploadedPhotos[backendPhotoId]?.localPhotoId}")
                val uploadedPhoto = uploadedPhotos[backendPhotoId]
                    ?: run {
                        Log.e(TAG, "buildRenderedBook missing local mapping for backendPhotoId=$backendPhotoId")
                        error("Одно из фото недоступно. Попробуйте выбрать его заново.")
                    }

                BookSlot(
                    id = slot.id,
                    photoId = uploadedPhoto.localPhotoId,
                    remotePhotoId = uploadedPhoto.backendPhotoId,
                    caption = listOf(
                        slot.caption,
                        slot.description,
                        slot.text,
                        slot.summary,
                        slot.photoCaption,
                        extractDescriptionText(slot.descriptionJson),
                        uploadedPhoto.description
                    ).firstNotNullOfOrNull { it.normalizedCaption() }.orEmpty()
                )
            }

            if (slots.isEmpty()) {
                Log.d(TAG, "buildRenderedBook skip empty page=${page.id}")
                return@mapIndexedNotNull null
            }

            val pageCaption = page.caption.normalizedCaption()
                ?: slots.mapNotNull { it.caption.normalizedCaption() }
                    .distinct()
                    .joinToString(separator = "\n")
                    .normalizedCaption()
                ?: ""
            Log.d(
                TAG,
                "buildRenderedBook page=${page.id} captionSource=" +
                    when {
                        page.caption.normalizedCaption() != null -> "page"
                        pageCaption.isNotBlank() -> "slot_or_photo"
                        else -> "none"
                    } +
                    " slotCaptions=${slots.count { it.caption.isNotBlank() }}"
            )

            BookPage(
                id = "page-${index + 1}",
                layoutId = page.id,
                caption = pageCaption,
                slots = slots
            )
        }

        return RenderedBook(
            draftId = draftId,
            templateId = templateId,
            filledTemplate = FilledTemplate(
                id = response.filledTemplate.id,
                pages = pages
            )
        ).also {
            Log.d(TAG, "buildRenderedBook success pages=${pages.size}")
        }
    }

    private fun createPhotoPart(photo: SelectedPhoto): MultipartBody.Part {
        val uri = Uri.parse(photo.uriString)
        val requestBody = ContentUriRequestBody(
            contentResolver = contentResolver,
            uri = uri,
            mediaType = photo.mimeType?.toMediaTypeOrNull(),
            contentLength = contentLength(uri = uri, fallback = photo.sizeBytes)
        )
        val fileName = photo.displayName ?: "photo-${photo.id}.jpg"
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = requestBody
        )
    }

    private fun contentLength(uri: Uri, fallback: Long?): Long? {
        fallback?.takeIf { it >= 0L }?.let { return it }
        if (uri.scheme != "file") return null

        return uri.path
            ?.let(::File)
            ?.length()
            ?.takeIf { it >= 0L }
    }

    private fun String.toPlainTextRequestBody() = toRequestBody("text/plain".toMediaTypeOrNull())

    private class ContentUriRequestBody(
        private val contentResolver: ContentResolver,
        private val uri: Uri,
        private val mediaType: MediaType?,
        private val contentLength: Long?
    ) : RequestBody() {
        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = contentLength ?: -1L

        override fun writeTo(sink: BufferedSink) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    sink.writeAll(inputStream.source())
                } ?: throw IOException("Одно из фото недоступно. Попробуйте выбрать его заново.")
            } catch (exception: IOException) {
                throw IOException("Одно из фото недоступно. Попробуйте выбрать его заново.", exception)
            }
        }
    }

    private data class UploadedPhoto(
        val localPhotoId: String,
        val backendPhotoId: String,
        val description: String?
    )

    private fun String?.normalizedCaption(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractDescriptionText(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        return when {
            element.isJsonPrimitive -> element.asJsonPrimitive
                .takeIf { it.isString }
                ?.asString
                .normalizedCaption()

            element.isJsonArray -> element.asJsonArray
                .mapNotNull(::extractDescriptionText)
                .distinct()
                .joinToString(separator = "\n")
                .normalizedCaption()

            element.isJsonObject -> extractDescriptionTextFromObject(element.asJsonObject)
            else -> null
        }
    }

    private fun extractDescriptionTextFromObject(element: JsonElement): String? {
        val jsonObject = element.asJsonObject
        return DESCRIPTION_JSON_TEXT_KEYS
            .asSequence()
            .mapNotNull { key -> extractDescriptionText(jsonObject.get(key)) }
            .firstOrNull()
    }

    private fun extractErrorMessage(response: Response<*>): String {
        val rawBody = response.errorBody()?.string().orEmpty()
        if (rawBody.isBlank()) {
            return response.message().takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
        }

        return runCatching {
            val jsonObject = JSONObject(rawBody)
            jsonObject.optString("error")
                .takeIf { it.isNotBlank() }
                ?: jsonObject.optJSONArray("detail")
                    ?.optJSONObject(0)
                    ?.optString("msg")
                    ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: rawBody
    }
}
