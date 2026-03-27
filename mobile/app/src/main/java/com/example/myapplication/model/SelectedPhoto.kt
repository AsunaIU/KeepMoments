package com.example.myapplication.model

data class SelectedPhoto(
    val id: String,
    val uriString: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?
)
