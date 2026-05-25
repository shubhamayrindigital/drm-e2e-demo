package com.ayrindigital.drme2edemo.data.downloads

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.offlineLicenseDataStore by preferencesDataStore(name = "offline_licenses")

@Singleton
class OfflineLicenseStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun keyFor(contentId: String) = stringPreferencesKey("lic_$contentId")

    suspend fun put(contentId: String, keySetId: ByteArray) {
        context.offlineLicenseDataStore.edit { it[keyFor(contentId)] = keySetId.toBase64() }
    }

    suspend fun get(contentId: String): ByteArray? {
        val encoded = context.offlineLicenseDataStore.data.first()[keyFor(contentId)] ?: return null
        return encoded.fromBase64()
    }

    suspend fun remove(contentId: String) {
        context.offlineLicenseDataStore.edit { it.remove(keyFor(contentId)) }
    }
}

private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
private fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
