package com.ayrindigital.drme2edemo.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import com.ayrindigital.drme2edemo.data.api.PlayManifestResponse
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
import com.ayrindigital.drme2edemo.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val apiService: ApiService,
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
            try {
                val detail = catalogRepository.getContent(contentId)
                _content.value = detail

                val manifestData = apiService.getPlayManifest(contentId)
                _manifest.value = manifestData

                // Note: ExoPlayer creation needs Android context from UI layer
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun createPlayer(playerManager: PlayerManager, contentId: String) {
        viewModelScope.launch {
            try {
                val content = _content.value ?: return@launch
                val player = playerManager.getOrCreatePlayer(content, contentId)
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
