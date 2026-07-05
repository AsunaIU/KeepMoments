package com.example.myapplication.data.books

import com.example.myapplication.model.BookDraft
import com.example.myapplication.model.BookPage
import com.example.myapplication.model.BookSlot
import com.example.myapplication.model.FilledTemplate
import com.example.myapplication.model.AlbumLayouts
import com.example.myapplication.model.RenderedBook
import com.example.myapplication.model.SelectedPhoto
import kotlinx.coroutines.delay
import kotlin.random.Random

class FakeBooksRepository : BooksRepository {
    override suspend fun generateRenderedBook(draft: BookDraft): Result<RenderedBook> {
        delay(Random.nextLong(from = 500L, until = 1_000L))
        return Result.success(
            RenderedBook(
                draftId = draft.id,
                templateId = MobileLayoutsTemplatePreset.TEMPLATE_ID,
                filledTemplate = FilledTemplate(
                    id = MobileLayoutsTemplatePreset.TEMPLATE_ID,
                    pages = draft.selectedPhotos.mapIndexed { index, photo ->
                        val layoutId = AlbumLayouts.defaultSingleLayout(photo)
                        BookPage(
                            id = "page-${index + 1}",
                            layoutId = layoutId,
                            caption = "",
                            slots = listOf(
                                BookSlot(
                                    id = "slot-1",
                                    photoId = photo.id,
                                    caption = ""
                                )
                            )
                        )
                    }
                )
            )
        )
    }
}
