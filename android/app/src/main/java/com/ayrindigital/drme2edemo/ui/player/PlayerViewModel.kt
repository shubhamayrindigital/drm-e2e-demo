package com.ayrindigital.drme2edemo.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import com.ayrindigital.drme2edemo.data.api.PlayManifestResponse
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
import com.ayrindigital.drme2edemo.data.downloads.OfflineLicenseStore
import com.ayrindigital.drme2edemo.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val apiService: ApiService,
    val okHttpClient: OkHttpClient,
    val downloadCache: SimpleCache,
    val offlineLicenseStore: OfflineLicenseStore,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    private val _content = MutableStateFlow<ContentDetail?>(null)
    val content: StateFlow<ContentDetail?> = _content

    private val _manifest = MutableStateFlow<PlayManifestResponse?>(null)
    val manifest: StateFlow<PlayManifestResponse?> = _manifest

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var playerManager: PlayerManager? = null

    fun loadContent(contentId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            // Cache-first: if content is already downloaded, play locally without touching the network.
            val offline = buildOfflinePlayData(contentId)
            if (offline != null) {
                Log.d("PlayerViewModel", "Playing $contentId from local download")
                _content.value = offline.first
                _manifest.value = offline.second
                _loading.value = false
                return@launch
            }

            try {
                val detail = catalogRepository.getContent(contentId)
                val manifest = apiService.getPlayManifest(contentId)
                _manifest.value = manifest
                _content.value = detail
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun buildOfflinePlayData(contentId: String): Pair<ContentDetail, PlayManifestResponse>? {
        val download = downloadManager.downloadIndex.getDownload(contentId)
        if (download == null) {
            Log.d("PlayerViewModel", "No local download for $contentId")
            return null
        }
        Log.d("PlayerViewModel", "Download for $contentId state=${download.state} bytes=${download.bytesDownloaded}/${download.contentLength}")
        if (download.state != Download.STATE_COMPLETED) return null

        val cachedLicense = offlineLicenseStore.get(contentId)
        val isDrm = cachedLicense != null
        val manifestUrl = download.request.uri.toString()
        Log.d("PlayerViewModel", "Using offline data: isDrm=$isDrm manifestUrl=$manifestUrl")

        val detail = ContentDetail(
            id = contentId,
            title = contentId,
            description = "Offline playback",
            drm = isDrm,
            manifestPath = "",
        )
        val manifest = PlayManifestResponse(
            manifestUrl = manifestUrl,
            licenseUrl = "",
            playbackToken = "",
            drmConfig = null,
        )
        return detail to manifest
    }

    fun createPlayer(playerManager: PlayerManager, contentId: String) {
        viewModelScope.launch {
            try {
                val content = _content.value ?: return@launch
                val manifest = _manifest.value ?: return@launch
                val player = playerManager.getOrCreatePlayer(
                    content = content,
                    contentId = contentId,
                    manifestUrl = manifest.manifestUrl,
                    licenseUrl = manifest.licenseUrl,
                )
                this@PlayerViewModel.playerManager = playerManager
                _player.value = player
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create player"
            }
        }
    }

    override fun onCleared() {
        playerManager?.release()
        _player.value?.release()
        super.onCleared()
    }
}
