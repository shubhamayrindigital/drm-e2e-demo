package com.ayrindigital.drme2edemo.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _content = MutableStateFlow<ContentDetail?>(null)
    val content: StateFlow<ContentDetail?> = _content

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadContent(contentId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val detail = catalogRepository.getContent(contentId)
                _content.value = detail
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }
}
