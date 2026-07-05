package com.example.myapplication.testutil.fakes

import com.example.myapplication.data.auth.AuthResponse
import com.example.myapplication.data.auth.SessionStore
import com.example.myapplication.model.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionStore(initial: AuthSession? = null) : SessionStore {

    private val state = MutableStateFlow(initial)

    var savedResponse: AuthResponse? = null
        private set
    var clearCount = 0
        private set

    override val session = state

    override suspend fun save(response: AuthResponse) {
        savedResponse = response
        state.value = response.toSession()
    }

    override suspend fun clear() {
        clearCount++
        savedResponse = null
        state.value = null
    }

    override suspend fun getSession(): AuthSession? = state.value

    private fun AuthResponse.toSession() = AuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        accessExpiresAt = accessExpiresAt,
        refreshExpiresAt = refreshExpiresAt,
        email = email,
        userId = userId
    )
}
