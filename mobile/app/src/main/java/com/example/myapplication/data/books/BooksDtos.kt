package com.example.myapplication.data.books

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ProcessTemplateDto(
    val id: String,
    val pages: List<ProcessPageDto>
)

data class ProcessPageDto(
    val id: String,
    val slots: List<ProcessSlotDto>
)

data class ProcessSlotDto(
    val id: String,
    @SerializedName("photo_id")
    val photoId: String? = null
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
    val pages: List<FilledPageDto>
)

data class FilledPageDto(
    val id: String,
    val slots: List<FilledSlotDto>
)

data class FilledSlotDto(
    val id: String,
    @SerializedName("photo_id")
    val photoId: String?
)
