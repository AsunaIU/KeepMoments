package com.example.myapplication.data.auth

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("access_expires_at")
    val accessExpiresAt: String?,
    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: String?,
    val email: String,
    @SerializedName("user_id")
    val userId: Long
)

data class ErrorResponse(
    val error: String?
)
