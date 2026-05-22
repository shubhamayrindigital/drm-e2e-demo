package com.ayrindigital.drme2edemo.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import okhttp3.OkHttpClient

class PlayerManager(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun getOrCreatePlayer(content: ContentDetail, contentId: String): ExoPlayer {
        if (exoPlayer != null) {
            return exoPlayer!!
        }

        val httpFactory = OkHttpDataSource.Factory(okHttpClient)

        val mediaSource = if (content.drm) {
            // TODO: Widevine DRM setup
            createClearMediaSource(content, httpFactory)
        } else {
            createClearMediaSource(content, httpFactory)
        }

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
            }

        return exoPlayer!!
    }

    private fun createClearMediaSource(
        content: ContentDetail,
        httpFactory: OkHttpDataSource.Factory,
    ): androidx.media3.exoplayer.source.MediaSource {
        val uri = "http://localhost:3000/..." // TODO: get signed manifest URL from backend
        val mediaItem = MediaItem.Builder().setUri(uri).build()

        return if (content.manifestPath.contains(".mpd")) {
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
