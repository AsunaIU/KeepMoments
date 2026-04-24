package com.example.myapplication.data.books

object SinglePhotoPerPageTemplatePreset {

    fun templateId(photoCount: Int): String = "single-photo-v1-$photoCount"

    fun buildTemplate(photoCount: Int): ProcessTemplateDto {
        return ProcessTemplateDto(
            id = templateId(photoCount),
            pages = (1..photoCount).map { pageIndex ->
                ProcessPageDto(
                    id = "page-$pageIndex",
                    slots = listOf(
                        ProcessSlotDto(id = "slot-1")
                    )
                )
            }
        )
    }
}
