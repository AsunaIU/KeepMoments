package com.example.myapplication.data.books

import com.example.myapplication.model.BookPage
import com.example.myapplication.model.BookSlot
import com.example.myapplication.model.FilledTemplate
import com.example.myapplication.model.RenderedBook
import com.example.myapplication.model.SelectedPhoto
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class FakeBooksRepository : BooksRepository {
    override suspend fun generateBook(
        photos: List<SelectedPhoto>,
        storyPrompt: String
    ): Result<RenderedBook> {
        delay(1_500L)

        if (photos.isEmpty()) {
            return Result.failure(IllegalStateException("Сначала добавьте хотя бы одно фото"))
        }

        if (storyPrompt.contains("error", ignoreCase = true) || storyPrompt.contains("ошибка", ignoreCase = true)) {
            return Result.failure(IllegalStateException("Произошла ошибка, попробуйте ещё раз"))
        }

        return runCatching {
            parseRenderedBook(
                json = buildMockJson(
                    photos = photos,
                    storyPrompt = storyPrompt
                )
            )
        }
    }

    private fun buildMockJson(
        photos: List<SelectedPhoto>,
        storyPrompt: String
    ): String {
        val pagesJson = JSONArray()
        photos.forEachIndexed { index, photo ->
            val slotJson = JSONObject()
                .put("id", "slot_${index + 1}")
                .put("photo_id", photo.id)
                .put("caption", captionFor(index = index, storyPrompt = storyPrompt))
                .put("orientation", "portrait")

            val pageJson = JSONObject()
                .put("id", "page_${index + 1}")
                .put("slots", JSONArray().put(slotJson))

            pagesJson.put(pageJson)
        }

        return JSONObject()
            .put(
                "filled_template",
                JSONObject()
                    .put("id", "test_book_1")
                    .put("pages", pagesJson)
            )
            .toString()
    }

    private fun parseRenderedBook(json: String): RenderedBook {
        val root = JSONObject(json)
        val filledTemplateJson = root.getJSONObject("filled_template")
        val pagesJson = filledTemplateJson.getJSONArray("pages")

        val pages = buildList {
            for (index in 0 until pagesJson.length()) {
                val pageJson = pagesJson.getJSONObject(index)
                val slotsJson = pageJson.getJSONArray("slots")

                val slots = buildList {
                    for (slotIndex in 0 until slotsJson.length()) {
                        val slotJson = slotsJson.getJSONObject(slotIndex)
                        add(
                            BookSlot(
                                id = slotJson.getString("id"),
                                photoId = slotJson.getString("photo_id"),
                                caption = slotJson.optString("caption"),
                                orientation = slotJson.optString("orientation", "portrait")
                            )
                        )
                    }
                }

                add(
                    BookPage(
                        id = pageJson.getString("id"),
                        slots = slots
                    )
                )
            }
        }

        return RenderedBook(
            filledTemplate = FilledTemplate(
                id = filledTemplateJson.getString("id"),
                pages = pages
            )
        )
    }

    private fun captionFor(index: Int, storyPrompt: String): String {
        if (index == 0 && storyPrompt.isNotBlank()) {
            return storyPrompt.trim().take(80)
        }

        return defaultCaptions[index % defaultCaptions.size]
    }

    private companion object {
        val defaultCaptions = listOf(
            "Первый день путешествия",
            "Момент, который хочется сохранить",
            "Прекрасный день с близкими людьми",
            "Прогулка в солнечный день",
            "Новая страница нашей истории"
        )
    }
}
