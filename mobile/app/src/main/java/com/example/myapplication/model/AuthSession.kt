package com.example.myapplication.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessExpiresAt: String?,
    val refreshExpiresAt: String?,
    val email: String,
    val userId: Long
)
