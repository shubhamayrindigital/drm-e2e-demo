package com.ayrindigital.drme2edemo.data.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.RenewOfflineLicenseRequest
import com.ayrindigital.drme2edemo.downloads.DemoDownloadService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DownloadState(
    val id: String,
    val state: Int,
    val progress: Float,
    val totalBytes: Long,
    val downloadedBytes: Long,
)

class DownloadRepository @Inject constructor(
    private val context: Context,
    private val apiService: ApiService,
) {
    private val _downloads = MutableStateFlow<List<DownloadState>>(emptyList())
    val downloads: StateFlow<List<DownloadState>> = _downloads.asStateFlow()

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            updateDownloadState(download)
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            _downloads.update { list -> list.filter { it.id != download.request.id } }
        }
    }

    fun initializeListener(downloadManager: DownloadManager) {
        downloadManager.addListener(downloadListener)
    }

    suspend fun startDownload(
        contentId: String,
        manifestUrl: String,
        isDrm: Boolean = false,
    ) {
        if (isDrm) {
            try {
                apiService.renewOfflineLicense(RenewOfflineLicenseRequest(contentId))
            } catch (e: Exception) {
                throw Exception("Failed to acquire offline license: ${e.message}")
            }
        }

        DemoDownloadService.startDownload(context, contentId, manifestUrl)
    }

    suspend fun pauseDownload(contentId: String) {
        DemoDownloadService.pauseDownload(context, contentId)
    }

    suspend fun removeDownload(contentId: String) {
        DemoDownloadService.removeDownload(context, contentId)
    }

    fun getDownloadState(contentId: String): Flow<DownloadState?> = flow {
        downloads.collect { downloadList ->
            emit(downloadList.find { it.id == contentId })
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
            )

            currentList.map { if (it.id == state.id) state else it }
                .takeIf { it.any { s -> s.id == state.id } }
                ?: (currentList + state)
        }
    }
}
