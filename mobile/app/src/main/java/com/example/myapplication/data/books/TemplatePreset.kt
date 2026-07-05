package com.example.myapplication.data.books

import com.example.myapplication.model.AlbumLayouts

object MobileLayoutsTemplatePreset {
    const val TEMPLATE_ID = "mobile-layouts-v1"

    fun buildTemplate(): ProcessTemplateDto {
        return ProcessTemplateDto(
            id = TEMPLATE_ID,
            pages = listOf(
                ProcessPageDto(
                    id = AlbumLayouts.THREE_PORTRAIT,
                    slots = listOf(
                        ProcessSlotDto(id = "left", requiredOrientation = "portrait"),
                        ProcessSlotDto(id = "center", requiredOrientation = "portrait"),
                        ProcessSlotDto(id = "right", requiredOrientation = "portrait")
                    )
                ),
                ProcessPageDto(
                    id = AlbumLayouts.TWO_LANDSCAPE,
                    slots = listOf(
                        ProcessSlotDto(id = "top", requiredOrientation = "landscape"),
                        ProcessSlotDto(id = "bottom", requiredOrientation = "landscape")
                    )
                ),
                ProcessPageDto(
                    id = AlbumLayouts.SINGLE_PORTRAIT_FULL,
                    slots = listOf(
                        ProcessSlotDto(id = "main", requiredOrientation = "portrait")
                    )
                ),
                ProcessPageDto(
                    id = AlbumLayouts.SINGLE_LANDSCAPE_FULL,
                    slots = listOf(
                        ProcessSlotDto(id = "main", requiredOrientation = "landscape")
                    )
                ),
                ProcessPageDto(
                    id = AlbumLayouts.PORTRAIT_LEFT_TWO_LANDSCAPE_RIGHT,
                    slots = listOf(
                        ProcessSlotDto(id = "left", requiredOrientation = "portrait"),
                        ProcessSlotDto(id = "top_right", requiredOrientation = "landscape"),
                        ProcessSlotDto(id = "bottom_right", requiredOrientation = "landscape")
                    )
                )
            ),
            frontCover = CoverConfigDto(text = ""),
            backCover = CoverConfigDto(text = "")
        )
    }
}
