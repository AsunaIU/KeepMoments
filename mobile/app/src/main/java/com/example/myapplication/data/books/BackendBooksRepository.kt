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
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
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
                val processResponse = processPhotoOrder(template.id, uploadedPhotos.keys.toList())

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
        val templateId = SinglePhotoPerPageTemplatePreset.templateId(photoCount)
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

        val templateRequest = SinglePhotoPerPageTemplatePreset.buildTemplate(photoCount)
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
        val uploadedPhotos = linkedMapOf<String, UploadedPhoto>()

        photos.forEachIndexed { index, photo ->
            Log.d(
                TAG,
                "uploadPhoto start index=$index name=${photo.displayName} mime=${photo.mimeType} size=${photo.sizeBytes} uri=${photo.uriString}"
            )
            Log.d(TAG, "uploadPhoto description_json omitted")
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
            uploadedPhotos[backendPhotoId] = UploadedPhoto(
                localPhotoId = photo.id,
                backendPhotoId = backendPhotoId
            )
            Log.d(
                TAG,
                "uploadPhoto success index=$index " +
                    "backendPhotoId=${uploadedPhoto.id} " +
                    "objectKey=${uploadedPhoto.objectKey} " +
                    "fileName=${uploadedPhoto.fileName} " +
                    "contentType=${uploadedPhoto.contentType} " +
                    "templateId=${uploadedPhoto.templateId}"
            )
        }

        Log.d(TAG, "uploadPhotos success uploadedIds=${uploadedPhotos.keys}")
        return uploadedPhotos
    }

    private suspend fun processPhotoOrder(
        templateId: String,
        uploadedPhotoIds: List<String>
    ): ProcessResponseDto {
        Log.d(TAG, "processPhotoOrder start templateId=$templateId backendPhotoIds=$uploadedPhotoIds")
        val response = processApi.process(
            ProcessRequestDto(
                templateId = templateId,
                photoIds = uploadedPhotoIds,
                minPhotos = uploadedPhotoIds.size,
                maxPhotos = uploadedPhotoIds.size,
                userDescription = "Фотоальбом"
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
        val pages = response.filledTemplate.pages.map { page ->
            Log.d(TAG, "buildRenderedBook page=${page.id} slots=${page.slots.size}")
            BookPage(
                id = page.id,
                slots = page.slots.map { slot ->
                    val backendPhotoId = slot.photoId
                        ?: error("Сервер не вернул photo_id для одной из страниц")
                    Log.d(TAG, "buildRenderedBook map backendPhotoId=$backendPhotoId localPhotoId=${uploadedPhotos[backendPhotoId]?.localPhotoId}")
                    val uploadedPhoto = uploadedPhotos[backendPhotoId]
                        ?: run {
                            Log.e(TAG, "buildRenderedBook missing local mapping for backendPhotoId=$backendPhotoId")
                            error("Не найдено локальное фото для backend photo_id=$backendPhotoId")
                        }

                    BookSlot(
                        id = slot.id,
                        photoId = uploadedPhoto.localPhotoId,
                        remotePhotoId = uploadedPhoto.backendPhotoId,
                        caption = ""
                    )
                }
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
            contentResolver.openInputStream(uri)?.use { inputStream ->
                sink.writeAll(inputStream.source())
            } ?: throw IOException("Не удалось открыть выбранное фото")
        }
    }

    private data class UploadedPhoto(
        val localPhotoId: String,
        val backendPhotoId: String
    )

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
