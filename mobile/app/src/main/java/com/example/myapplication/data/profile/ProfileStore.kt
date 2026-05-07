package com.example.myapplication.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile_store")

class ProfileStore(
    context: Context
) {

    private val dataStore = context.profileDataStore

    fun observeProfile(userId: Long?): Flow<StoredProfile> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                StoredProfile(
                    displayName = preferences[displayNameKey(userId)],
                    avatarUriString = preferences[avatarUriKey(userId)]
                )
            }
    }

    suspend fun saveProfile(userId: Long?, displayName: String?, avatarUriString: String?) {
        dataStore.edit { preferences ->
            val normalizedDisplayName = displayName?.trim().orEmpty()
            val normalizedAvatarUri = avatarUriString?.trim().orEmpty()

            if (normalizedDisplayName.isBlank()) {
                preferences.remove(displayNameKey(userId))
            } else {
                preferences[displayNameKey(userId)] = normalizedDisplayName
            }

            if (normalizedAvatarUri.isBlank()) {
                preferences.remove(avatarUriKey(userId))
            } else {
                preferences[avatarUriKey(userId)] = normalizedAvatarUri
            }
        }
    }

    private fun displayNameKey(userId: Long?) = stringPreferencesKey("profile_${scopeSuffix(userId)}_display_name")

    private fun avatarUriKey(userId: Long?) = stringPreferencesKey("profile_${scopeSuffix(userId)}_avatar_uri")

    private fun scopeSuffix(userId: Long?): String = userId?.let { "user_$it" } ?: "guest"
}

data class StoredProfile(
    val displayName: String?,
    val avatarUriString: String?
)
