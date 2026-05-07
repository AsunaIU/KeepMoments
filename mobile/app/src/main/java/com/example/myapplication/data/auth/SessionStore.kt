package com.example.myapplication.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.model.AuthSession
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

class SessionStore(
    context: Context
) {

    private val dataStore = context.sessionDataStore

    val session: Flow<AuthSession?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val accessToken = preferences[Keys.ACCESS_TOKEN]
            val refreshToken = preferences[Keys.REFRESH_TOKEN]
            val email = preferences[Keys.EMAIL]
            val userId = preferences[Keys.USER_ID]
            if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || email.isNullOrBlank() || userId == null) {
                null
            } else {
                AuthSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = preferences[Keys.TOKEN_TYPE] ?: "Bearer",
                    accessExpiresAt = preferences[Keys.ACCESS_EXPIRES_AT],
                    refreshExpiresAt = preferences[Keys.REFRESH_EXPIRES_AT],
                    email = email,
                    userId = userId
                )
            }
        }

    suspend fun save(response: AuthResponse) {
        dataStore.edit { preferences ->
            preferences[Keys.ACCESS_TOKEN] = response.accessToken
            preferences[Keys.REFRESH_TOKEN] = response.refreshToken
            preferences[Keys.TOKEN_TYPE] = response.tokenType
            response.accessExpiresAt?.let { preferences[Keys.ACCESS_EXPIRES_AT] = it }
            response.refreshExpiresAt?.let { preferences[Keys.REFRESH_EXPIRES_AT] = it }
            preferences[Keys.EMAIL] = response.email
            preferences[Keys.USER_ID] = response.userId
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun getSession(): AuthSession? = session.firstOrNull()

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TOKEN_TYPE = stringPreferencesKey("token_type")
        val ACCESS_EXPIRES_AT = stringPreferencesKey("access_expires_at")
        val REFRESH_EXPIRES_AT = stringPreferencesKey("refresh_expires_at")
        val EMAIL = stringPreferencesKey("email")
        val USER_ID = longPreferencesKey("user_id")
    }
}
