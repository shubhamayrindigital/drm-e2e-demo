package com.ayrindigital.drme2edemo.player

import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import java.util.UUID

/**
 * MediaDrmCallback that returns a cached license response when available, otherwise delegates
 * to a fallback (typically HttpMediaDrmCallback hitting the license server). Used so downloaded
 * DRM content can play offline without relying on the emulator's broken restoreKeys.
 */
class CachedClearKeyDrmCallback(
    private val cachedLicense: ByteArray?,
    private val fallback: MediaDrmCallback,
) : MediaDrmCallback {
    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response {
        return cachedLicense?.let { MediaDrmCallback.Response(it) }
            ?: fallback.executeKeyRequest(uuid, request)
    }

    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): MediaDrmCallback.Response {
        return fallback.executeProvisionRequest(uuid, request)
    }
}
