package com.ayrindigital.drme2edemo.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_tokens")

class TokenStore(private val context: Context) {
    private object PreferenceKeys {
        val JWT_TOKEN = stringPreferencesKey("jwt_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
    }

    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.JWT_TOKEN]
    }

    val userId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.USER_ID]
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.USER_EMAIL]
    }

    suspend fun saveToken(token: String, userId: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.JWT_TOKEN] = token
            prefs[PreferenceKeys.USER_ID] = userId
            prefs[PreferenceKeys.USER_EMAIL] = email
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.JWT_TOKEN)
            prefs.remove(PreferenceKeys.USER_ID)
            prefs.remove(PreferenceKeys.USER_EMAIL)
        }
    }
}
