package com.ayrindigital.drme2edemo.data.downloads

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashUtil
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.OfflineLicenseHelper
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.downloads.DemoDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val id: String,
    val state: Int,
    val progress: Float,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val stopReason: Int,
)

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
    private val offlineLicenseStore: OfflineLicenseStore,
    private val downloadManager: DownloadManager,
) {
    private val tag = "DownloadRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<DownloadState>>(emptyList())
    val downloads: StateFlow<List<DownloadState>> = _downloads.asStateFlow()

    init {
        attach()
    }

    private fun attach() {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                updateDownloadState(download)
                if (download.state == Download.STATE_COMPLETED) {
                    scope.launch { maybeFetchOfflineLicense(download.request.id) }
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                _downloads.update { list -> list.filter { it.id != download.request.id } }
                scope.launch { offlineLicenseStore.remove(download.request.id) }
            }
        })
        downloadManager.currentDownloads.forEach(::updateDownloadState)
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) updateDownloadState(cursor.download)
        }
    }

    fun startDownload(contentId: String, manifestUrl: String) {
        DemoDownloadService.startDownload(context, contentId, manifestUrl)
    }

    fun pauseDownload(contentId: String) {
        DemoDownloadService.pauseDownload(context, contentId)
    }

    fun resumeDownload(contentId: String) {
        DemoDownloadService.resumeDownload(context, contentId)
    }

    fun removeDownload(contentId: String) {
        DemoDownloadService.removeDownload(context, contentId)
    }

    suspend fun getOfflineKeySetId(contentId: String): ByteArray? = offlineLicenseStore.get(contentId)

    private suspend fun maybeFetchOfflineLicense(contentId: String) {
        val playManifest = runCatching { apiService.getPlayManifest(contentId) }.getOrNull() ?: return
        if (playManifest.drmConfig == null) return
        if (offlineLicenseStore.get(contentId) != null) return

        val licenseUrl = playManifest.licenseUrl
        Log.d(tag, "Fetching offline license for $contentId")
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val drmCallback = HttpMediaDrmCallback(licenseUrl, httpFactory).apply {
            setKeyRequestProperty("x-content-id", contentId)
            setKeyRequestProperty("Content-Type", "application/json")
        }
        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID) { FrameworkMediaDrm.newInstance(C.CLEARKEY_UUID) }
            .build(drmCallback)
        val helper = OfflineLicenseHelper(drmSessionManager, DrmSessionEventListener.EventDispatcher())
        try {
            val dataSource = httpFactory.createDataSource()
            val dashManifest = DashUtil.loadManifest(dataSource, android.net.Uri.parse(playManifest.manifestUrl))
            val format = DashUtil.loadFormatWithDrmInitData(dataSource, dashManifest.getPeriod(0))
                ?: error("No DRM init data in manifest")
            val keySetId = helper.downloadLicense(format)
            offlineLicenseStore.put(contentId, keySetId)
            Log.d(tag, "Offline license stored for $contentId (${keySetId.size} bytes)")
        } catch (e: Exception) {
            Log.e(tag, "Offline license fetch failed for $contentId", e)
        } finally {
            helper.release()
        }
    }

    private fun updateDownloadState(download: Download) {
        _downloads.update { currentList ->
            val state = DownloadState(
                id = download.request.id,
                state = download.state,
                progress = download.percentDownloaded / 100f,
                totalBytes = download.contentLength,
                downloadedBytes = download.bytesDownloaded,
                stopReason = download.stopReason,
            )
            val idx = currentList.indexOfFirst { it.id == state.id }
            if (idx >= 0) currentList.toMutableList().also { it[idx] = state }
            else currentList + state
        }
    }
}
