package com.example.myapplication.data.auth

import com.example.myapplication.model.AuthSession
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore
) {

    val session: Flow<AuthSession?> = sessionStore.session

    suspend fun register(email: String, password: String): Result<AuthSession> {
        return runCatching {
            authApi.register(AuthRequest(email = email.trim(), password = password.trim()))
        }.fold(
            onSuccess = { response -> handleAuthResponse(response) },
            onFailure = { Result.failure(mapThrowable(it)) }
        )
    }

    suspend fun login(email: String, password: String): Result<AuthSession> {
        return runCatching {
            authApi.login(AuthRequest(email = email.trim(), password = password.trim()))
        }.fold(
            onSuccess = { response -> handleAuthResponse(response) },
            onFailure = { Result.failure(mapThrowable(it)) }
        )
    }

    suspend fun refreshTokens(): Result<AuthSession> {
        val currentSession = sessionStore.getSession()
            ?: return Result.failure(IllegalStateException("Сессия отсутствует"))

        return runCatching {
            authApi.refresh(RefreshRequest(refreshToken = currentSession.refreshToken))
        }.fold(
            onSuccess = { response -> handleAuthResponse(response) },
            onFailure = { Result.failure(mapThrowable(it)) }
        )
    }

    suspend fun logout() {
        sessionStore.clear()
    }

    suspend fun currentSession(): AuthSession? = sessionStore.getSession()

    private suspend fun handleAuthResponse(response: Response<AuthResponse>): Result<AuthSession> {
        if (!response.isSuccessful) {
            val message = parseErrorMessage(response.errorBody())
            return Result.failure(IllegalStateException(message))
        }

        val body = response.body()
            ?: return Result.failure(IllegalStateException("Пустой ответ сервера"))

        sessionStore.save(body)
        return Result.success(body.toSession())
    }

    private fun AuthResponse.toSession(): AuthSession {
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            accessExpiresAt = accessExpiresAt,
            refreshExpiresAt = refreshExpiresAt,
            email = email,
            userId = userId
        )
    }

    private fun parseErrorMessage(errorBody: ResponseBody?): String {
        val rawBody = errorBody?.string().orEmpty()
        if (rawBody.isBlank()) {
            return "Не удалось выполнить запрос"
        }

        return runCatching {
            JSONObject(rawBody).optString("error")
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: rawBody
    }

    private fun mapThrowable(throwable: Throwable): Throwable {
        return when (throwable) {
            is HttpException -> IllegalStateException("Сервер временно недоступен")
            else -> throwable
        }
    }
}
