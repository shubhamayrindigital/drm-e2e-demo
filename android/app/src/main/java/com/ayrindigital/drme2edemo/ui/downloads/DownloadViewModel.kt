package com.ayrindigital.drme2edemo.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.ayrindigital.drme2edemo.data.downloads.DownloadRepository
import com.ayrindigital.drme2edemo.data.downloads.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadState>> = downloadRepository.downloads

    fun startDownload(contentId: String, manifestUrl: String) {
        downloadRepository.startDownload(contentId, manifestUrl)
    }

    fun pauseDownload(contentId: String) {
        downloadRepository.pauseDownload(contentId)
    }

    fun resumeDownload(contentId: String) {
        downloadRepository.resumeDownload(contentId)
    }

    fun removeDownload(contentId: String) {
        downloadRepository.removeDownload(contentId)
    }

    fun isDownloading(contentId: String): Boolean =
        downloads.value.any { it.id == contentId && it.state == Download.STATE_DOWNLOADING }

    fun isDownloaded(contentId: String): Boolean =
        downloads.value.any { it.id == contentId && it.state == Download.STATE_COMPLETED }

    fun getDownloadProgress(contentId: String): Float =
        downloads.value.find { it.id == contentId }?.progress ?: 0f
}
