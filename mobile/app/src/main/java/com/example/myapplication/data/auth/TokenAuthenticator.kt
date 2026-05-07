package com.example.myapplication.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val authRepository: AuthRepository
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.startsWith("/api/v1/auth/")) {
            return null
        }
        if (response.request.header("Authorization").isNullOrBlank()) {
            return null
        }
        if (responseCount(response) >= 2) {
            return null
        }

        val updatedSession = runBlocking {
            authRepository.refreshTokens().getOrNull()
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "${updatedSession.tokenType} ${updatedSession.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var current = response.priorResponse
        while (current != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }
}
