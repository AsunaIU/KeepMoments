package com.example.myapplication.data.books

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TemplatesApi {

    @GET("api/v1/templates")
    suspend fun listTemplates(): Response<List<ProcessTemplateDto>>

    @POST("api/v1/templates")
    suspend fun createTemplate(@Body request: ProcessTemplateDto): Response<ProcessTemplateDto>
}

interface PhotosApi {

    @Multipart
    @POST("api/v1/photos")
    suspend fun uploadPhoto(
        @Part("template_id") templateId: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<PhotoDetailsDto>
}

interface ProcessApi {

    @POST("process")
    suspend fun process(@Body request: ProcessRequestDto): Response<ProcessResponseDto>
}
