package com.ayrindigital.drme2edemo.data.downloads

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.offlineLicenseDataStore by preferencesDataStore(name = "offline_licenses")
private const val KEY_PREFIX = "lic_"

/**
 * Persists ClearKey license bytes for downloaded DRM content with a short POC TTL so expiry
 * is easy to verify by hand. Payload format is "<storedAtMillis>:<base64-bytes>".
 */
@Singleton
class OfflineLicenseStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun keyFor(contentId: String) = stringPreferencesKey("$KEY_PREFIX$contentId")

    suspend fun put(contentId: String, license: ByteArray) {
        val payload = "${System.currentTimeMillis()}:${license.toBase64()}"
        context.offlineLicenseDataStore.edit { it[keyFor(contentId)] = payload }
    }

    suspend fun get(contentId: String): ByteArray? {
        val payload = context.offlineLicenseDataStore.data.first()[keyFor(contentId)] ?: return null
        val parsed = parse(payload) ?: return null
        if (System.currentTimeMillis() - parsed.first > LICENSE_TTL_MS) {
            remove(contentId)
            return null
        }
        return parsed.second.fromBase64()
    }

    suspend fun remove(contentId: String) {
        context.offlineLicenseDataStore.edit { it.remove(keyFor(contentId)) }
    }

    /** Emits a map of contentId -> license expiry epoch millis whenever stored licenses change. */
    val expiriesFlow: Flow<Map<String, Long>> = context.offlineLicenseDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
            val storedAt = parse(value)?.first ?: return@mapNotNull null
            name.removePrefix(KEY_PREFIX) to (storedAt + LICENSE_TTL_MS)
        }.toMap()
    }

    private fun parse(payload: String): Pair<Long, String>? {
        val idx = payload.indexOf(':')
        if (idx <= 0) return null
        val storedAt = payload.substring(0, idx).toLongOrNull() ?: return null
        return storedAt to payload.substring(idx + 1)
    }

    companion object {
        const val LICENSE_TTL_MS: Long = 60_000L
    }
}

private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
