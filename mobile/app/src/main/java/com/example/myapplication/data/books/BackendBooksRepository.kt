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
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
                val uploadMapping = uploadPhotos(template.id, validPhotos)
                val processResponse = processPhotoOrder(template.id, uploadMapping.keys.toList())

                buildRenderedBook(
                    draftId = draft.id,
                    templateId = template.id,
                    response = processResponse,
                    localUriMapping = uploadMapping
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
            Log.e(TAG, "resolveTemplate list failed code=${listResponse.code()} error=${extractErrorMessage(listResponse)}")
            error("Не удалось получить шаблоны книги: ${extractErrorMessage(listResponse)}")
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
            Log.e(TAG, "resolveTemplate create failed code=${createResponse.code()} error=${extractErrorMessage(createResponse)}")
            error("Не удалось создать шаблон книги: ${extractErrorMessage(createResponse)}")
        }

        return (createResponse.body() ?: templateRequest).also {
            Log.d(TAG, "resolveTemplate created templateId=${it.id}")
        }
    }

    private suspend fun uploadPhotos(
        templateId: String,
        photos: List<SelectedPhoto>
    ): LinkedHashMap<String, String> {
        Log.d(TAG, "uploadPhotos start templateId=$templateId count=${photos.size}")
        val uploadedPhotoMapping = linkedMapOf<String, String>()

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
                Log.e(TAG, "uploadPhoto failed index=$index error=${extractErrorMessage(response)}")
                error("Не удалось загрузить фото на сервер: ${extractErrorMessage(response)}")
            }

            val uploadedPhoto = response.body()
                ?: error("Сервер не вернул идентификатор фото")

            uploadedPhotoMapping[uploadedPhoto.id.toString()] = photo.uriString
            Log.d(TAG, "uploadPhoto success index=$index backendPhotoId=${uploadedPhoto.id}")
        }

        Log.d(TAG, "uploadPhotos success uploadedIds=${uploadedPhotoMapping.keys}")
        return uploadedPhotoMapping
    }

    private suspend fun processPhotoOrder(
        templateId: String,
        uploadedPhotoIds: List<String>
    ): ProcessResponseDto {
        Log.d(TAG, "processPhotoOrder start templateId=$templateId photoIds=$uploadedPhotoIds")
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
            Log.e(TAG, "processPhotoOrder failed error=${extractErrorMessage(response)}")
            error("Не удалось собрать книгу: ${extractErrorMessage(response)}")
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
        localUriMapping: Map<String, String>
    ): RenderedBook {
        Log.d(TAG, "buildRenderedBook start draftId=$draftId templateId=$templateId")
        val pages = response.filledTemplate.pages.map { page ->
            Log.d(TAG, "buildRenderedBook page=${page.id} slots=${page.slots.size}")
            BookPage(
                id = page.id,
                slots = page.slots.map { slot ->
                    val backendPhotoId = slot.photoId
                        ?: error("Сервер не вернул photo_id для одной из страниц")
                    Log.d(TAG, "buildRenderedBook map backendPhotoId=$backendPhotoId localUri=${localUriMapping[backendPhotoId]}")
                    val localUriString = localUriMapping[backendPhotoId]
                        ?: run {
                            Log.e(TAG, "buildRenderedBook missing local mapping for backendPhotoId=$backendPhotoId")
                            error("Не найдено локальное фото для backend photo_id=$backendPhotoId")
                        }

                    BookSlot(
                        id = slot.id,
                        photoId = localUriString,
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
        val bytes = contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: throw IOException("Не удалось открыть выбранное фото")

        val requestBody = bytes.toRequestBody(photo.mimeType?.toMediaTypeOrNull())
        val fileName = photo.displayName ?: "photo-${photo.id}.jpg"
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = requestBody
        )
    }

    private fun String.toPlainTextRequestBody() = toRequestBody("text/plain".toMediaTypeOrNull())

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
