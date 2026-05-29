package com.example.myapplication.data.books

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ProcessTemplateDto(
    val id: String,
    val pages: List<ProcessPageDto>,
    @SerializedName("front_cover")
    val frontCover: CoverConfigDto? = null,
    @SerializedName("back_cover")
    val backCover: CoverConfigDto? = null
)

data class CoverConfigDto(
    val mode: String = "caption",
    @SerializedName("photo_id")
    val photoId: String? = null,
    val text: String? = null
)

data class ProcessPageDto(
    val id: String,
    val slots: List<ProcessSlotDto>
)

data class ProcessSlotDto(
    val id: String,
    @SerializedName("photo_id")
    val photoId: String? = null,
    @SerializedName("required_orientation")
    val requiredOrientation: String? = null
)

data class PhotoDetailsDto(
    val id: Long,
    @SerializedName("file_name")
    val fileName: String?,
    @SerializedName("template_id")
    val templateId: String,
    @SerializedName("content_type")
    val contentType: String?,
    @SerializedName("description_json")
    val descriptionJson: JsonElement?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("object_key")
    val objectKey: String?
)

data class ProcessRequestDto(
    @SerializedName("template_id")
    val templateId: String,
    @SerializedName("photo_ids")
    val photoIds: List<String>,
    @SerializedName("min_photos")
    val minPhotos: Int,
    @SerializedName("max_photos")
    val maxPhotos: Int,
    @SerializedName("user_description")
    val userDescription: String = "Фотоальбом"
)

data class ProcessResponseDto(
    @SerializedName("filled_template")
    val filledTemplate: FilledTemplateDto
)

data class FilledTemplateDto(
    val id: String,
    val pages: List<FilledPageDto>,
    @SerializedName("front_cover")
    val frontCover: FilledCoverDto? = null,
    @SerializedName("back_cover")
    val backCover: FilledCoverDto? = null
)

data class FilledCoverDto(
    val mode: String,
    @SerializedName("photo_id")
    val photoId: String? = null,
    val text: String? = null
)

data class FilledPageDto(
    val id: String,
    val slots: List<FilledSlotDto>,
    val caption: String? = null
)

data class FilledSlotDto(
    val id: String,
    @SerializedName("photo_id")
    val photoId: String?,
    val caption: String? = null,
    val description: String? = null,
    val text: String? = null,
    val summary: String? = null,
    @SerializedName("photo_caption")
    val photoCaption: String? = null,
    @SerializedName("description_json")
    val descriptionJson: JsonElement? = null
)
