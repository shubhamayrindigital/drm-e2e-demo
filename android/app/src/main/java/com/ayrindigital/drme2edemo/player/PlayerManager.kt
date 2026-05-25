package com.ayrindigital.drme2edemo.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import okhttp3.OkHttpClient

class PlayerManager(
    private val context: Context,
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
) {
    private var exoPlayer: ExoPlayer? = null

    suspend fun getOrCreatePlayer(content: ContentDetail, contentId: String): ExoPlayer {
        if (exoPlayer != null) {
            return exoPlayer!!
        }

        val manifest = apiService.getPlayManifest(contentId)
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaItem = MediaItem.fromUri(manifest.manifestUrl)

        val dashFactory = DashMediaSource.Factory(httpFactory)
        if (content.drm) {
            val drmSessionManager = createDrmSessionManager(manifest.licenseUrl, contentId)
            dashFactory.setDrmSessionManagerProvider { drmSessionManager }
        }
        val mediaSource = dashFactory.createMediaSource(mediaItem)

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
        }

        return exoPlayer!!
    }

    private fun createDrmSessionManager(licenseUrl: String, contentId: String): DefaultDrmSessionManager {
        val uuid = C.CLEARKEY_UUID
        val drmCallback = HttpMediaDrmCallback(licenseUrl, OkHttpDataSource.Factory(okHttpClient)).apply {
            setKeyRequestProperty("x-content-id", contentId)
            setKeyRequestProperty("Content-Type", "application/json")
        }

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid) { FrameworkMediaDrm.newInstance(uuid) }
            .build(drmCallback)
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
