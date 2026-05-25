package com.ayrindigital.drme2edemo.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import com.ayrindigital.drme2edemo.data.downloads.OfflineLicenseStore
import okhttp3.OkHttpClient

class PlayerManager(
    private val context: Context,
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
    private val downloadCache: SimpleCache,
    private val offlineLicenseStore: OfflineLicenseStore,
) {
    private var exoPlayer: ExoPlayer? = null

    suspend fun getOrCreatePlayer(content: ContentDetail, contentId: String, manifestUrl: String, licenseUrl: String): ExoPlayer {
        if (exoPlayer != null) {
            return exoPlayer!!
        }

        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaItem = MediaItem.fromUri(manifestUrl)

        val dashFactory = DashMediaSource.Factory(cacheFactory)
        if (content.drm) {
            val cachedLicense = offlineLicenseStore.get(contentId)
            val drmSessionManager = createDrmSessionManager(licenseUrl, contentId, cachedLicense)
            dashFactory.setDrmSessionManagerProvider { drmSessionManager }
        }
        val mediaSource = dashFactory.createMediaSource(mediaItem)

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
        }
        return exoPlayer!!
    }

    private fun createDrmSessionManager(
        licenseUrl: String,
        contentId: String,
        cachedLicense: ByteArray?,
    ): DefaultDrmSessionManager {
        val uuid = C.CLEARKEY_UUID
        val httpCallback = HttpMediaDrmCallback(licenseUrl, OkHttpDataSource.Factory(okHttpClient)).apply {
            setKeyRequestProperty("x-content-id", contentId)
            setKeyRequestProperty("Content-Type", "application/json")
        }
        val callback = CachedClearKeyDrmCallback(cachedLicense, httpCallback)

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid) { FrameworkMediaDrm.newInstance(uuid) }
            .build(callback)
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
