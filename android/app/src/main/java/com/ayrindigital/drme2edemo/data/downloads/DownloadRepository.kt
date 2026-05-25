package com.ayrindigital.drme2edemo.data.downloads

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.downloads.DemoDownloadService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun getCachedLicense(contentId: String): ByteArray? = offlineLicenseStore.get(contentId)

    /**
     * For ClearKey content, fetches the license JSON from the server and caches it locally.
     * On the Android emulator the ClearKey CDM does not implement restoreKeys (persistent sessions),
     * so we cannot rely on OfflineLicenseHelper's keySetId mechanism. Instead, we cache the raw
     * license bytes and replay them through a custom MediaDrmCallback during playback.
     */
    private suspend fun maybeFetchOfflineLicense(contentId: String) {
        val playManifest = runCatching { apiService.getPlayManifest(contentId) }.getOrNull() ?: return
        if (playManifest.drmConfig == null) return
        if (offlineLicenseStore.get(contentId) != null) return

        Log.d(tag, "Fetching offline license for $contentId")
        val body = """{"kids":[],"type":"temporary"}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(playManifest.licenseUrl)
            .header("x-content-id", contentId)
            .post(body)
            .build()
        try {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(tag, "License pre-fetch failed: ${resp.code}")
                    return
                }
                val bytes = resp.body?.bytes() ?: return
                offlineLicenseStore.put(contentId, bytes)
                Log.d(tag, "Offline license cached for $contentId (${bytes.size} bytes)")
            }
        } catch (e: Exception) {
            Log.e(tag, "Offline license fetch failed for $contentId", e)
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
