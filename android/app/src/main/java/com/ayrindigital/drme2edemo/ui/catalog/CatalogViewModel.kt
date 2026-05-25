package com.ayrindigital.drme2edemo.ui.catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.ayrindigital.drme2edemo.data.api.ContentItem
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadManager: DownloadManager,
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
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _contentList.value = catalogRepository.listContent()
                _offline.value = false
            } catch (e: Exception) {
                Log.w(tag, "Network catalog fetch failed, falling back to local", e)
                val downloadedIds = downloadedContentIds()
                val cached = catalogRepository.listCachedContent()
                _contentList.value = cached.filter { it.id in downloadedIds }
                _offline.value = true
                if (downloadedIds.isEmpty()) {
                    _error.value = null // show empty list, not error
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun grantEntitlement(contentId: String) {
        viewModelScope.launch {
            try {
                catalogRepository.grantEntitlement(contentId)
                loadContent()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun downloadedContentIds(): Set<String> {
        val ids = mutableSetOf<String>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val d = cursor.download
                if (d.state == Download.STATE_COMPLETED) ids += d.request.id
            }
        }
        return ids
    }
}
