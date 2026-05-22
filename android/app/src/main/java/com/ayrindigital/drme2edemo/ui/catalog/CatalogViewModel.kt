package com.ayrindigital.drme2edemo.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {
    private val _contentList = MutableStateFlow<List<ContentItem>>(emptyList())
    val contentList: StateFlow<List<ContentItem>> = _contentList

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val content = catalogRepository.listContent()
                _contentList.value = content
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun grantEntitlement(contentId: String) {
        viewModelScope.launch {
            try {
                catalogRepository.grantEntitlement(contentId)
                loadContent() // refresh
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
