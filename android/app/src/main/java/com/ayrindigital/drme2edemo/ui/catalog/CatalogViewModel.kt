package com.ayrindigital.drme2edemo.ui.catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.ayrindigital.drme2edemo.data.api.ContentItem
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
import com.ayrindigital.drme2edemo.data.downloads.DownloadRepository
import com.ayrindigital.drme2edemo.data.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val tag = "CatalogViewModel"

    private val _contentList = MutableStateFlow<List<ContentItem>>(emptyList())
    val contentList: StateFlow<List<ContentItem>> = _contentList

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    init {
        viewModelScope.launch {
            combine(
                networkMonitor.isOnline,
                downloadRepository.downloads,
            ) { online, downloads ->
                online to downloads.filter { it.state == Download.STATE_COMPLETED }.map { it.id }.toSet()
            }
                .distinctUntilChanged()
                .collect { (online, downloadedIds) ->
                    refresh(online, downloadedIds)
                }
        }
    }

    fun loadContent() {
        viewModelScope.launch {
            refresh(networkMonitor.isOnline.value, downloadedIdsFromRepo())
        }
    }

    fun grantEntitlement(contentId: String) {
        viewModelScope.launch {
            try {
                catalogRepository.grantEntitlement(contentId)
                refresh(networkMonitor.isOnline.value, downloadedIdsFromRepo())
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private suspend fun refresh(online: Boolean, downloadedIds: Set<String>) {
        _loading.value = true
        _error.value = null
        if (online) {
            try {
                _contentList.value = catalogRepository.listContent()
                _offline.value = false
            } catch (e: Exception) {
                Log.w(tag, "Catalog fetch failed despite being online; falling back", e)
                showOffline(downloadedIds)
            }
        } else {
            showOffline(downloadedIds)
        }
        _loading.value = false
    }

    private suspend fun showOffline(downloadedIds: Set<String>) {
        val cached = catalogRepository.listCachedContent()
        _contentList.value = cached.filter { it.id in downloadedIds }
        _offline.value = true
    }

    private fun downloadedIdsFromRepo(): Set<String> =
        downloadRepository.downloads.value
            .filter { it.state == Download.STATE_COMPLETED }
            .map { it.id }
            .toSet()
}
