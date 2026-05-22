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
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import okhttp3.OkHttpClient

class PlayerManager(
    private val context: Context,
    private val apiService: ApiService,
) {
    private var exoPlayer: ExoPlayer? = null
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun getOrCreatePlayer(content: ContentDetail, contentId: String): ExoPlayer {
        if (exoPlayer != null) {
            return exoPlayer!!
        }

        val manifest = apiService.getPlayManifest(contentId)
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)

        val mediaSource = if (content.drm) {
            createDrmMediaSource(manifest, httpFactory)
        } else {
            createClearMediaSource(manifest, httpFactory)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        if (content.drm) {
            val drmSessionManager = createDrmSessionManager(manifest.licenseUrl)
            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
        }

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
            }

        return exoPlayer!!
    }

    private fun createDrmSessionManager(licenseUrl: String): DefaultDrmSessionManager {
        val uuid = C.WIDEVINE_UUID
        val drmCallback = HttpMediaDrmCallback(licenseUrl, OkHttpDataSource.Factory(okHttpClient))

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid) { FrameworkMediaDrm.newInstance(uuid) }
            .build(drmCallback)
    }

    @Suppress("DEPRECATION")
    private fun createDrmMediaSource(
        manifest: com.ayrindigital.drme2edemo.data.api.PlayManifestResponse,
        httpFactory: OkHttpDataSource.Factory,
    ): androidx.media3.exoplayer.source.MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(manifest.manifestUrl)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(manifest.licenseUrl)
            .build()

        return if (manifest.manifestUrl.contains(".mpd")) {
            DashMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        }
    }

    private fun createClearMediaSource(
        manifest: com.ayrindigital.drme2edemo.data.api.PlayManifestResponse,
        httpFactory: OkHttpDataSource.Factory,
    ): androidx.media3.exoplayer.source.MediaSource {
        val mediaItem = MediaItem.Builder().setUri(manifest.manifestUrl).build()

        return if (manifest.manifestUrl.contains(".mpd")) {
            DashMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
