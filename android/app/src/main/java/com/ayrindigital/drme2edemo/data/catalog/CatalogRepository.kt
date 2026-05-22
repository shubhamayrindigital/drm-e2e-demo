package com.ayrindigital.drme2edemo.data.catalog

import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import com.ayrindigital.drme2edemo.data.api.ContentItem

class CatalogRepository(private val apiService: ApiService) {
    suspend fun listContent(): List<ContentItem> {
        return apiService.listContent()
    }

    suspend fun getContent(id: String): ContentDetail {
        return apiService.getContent(id)
    }

    suspend fun grantEntitlement(contentId: String) {
        apiService.grantEntitlement(contentId)
    }
}
