package com.ayrindigital.drme2edemo.data.catalog

import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.ContentDetail
import com.ayrindigital.drme2edemo.data.api.ContentItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val apiService: ApiService,
    private val catalogCache: CatalogCache,
) {
    suspend fun listContent(): List<ContentItem> {
        val items = apiService.listContent().items
        catalogCache.save(items)
        return items
    }

    suspend fun listCachedContent(): List<ContentItem> = catalogCache.read()

    suspend fun getContent(id: String): ContentDetail = apiService.getContent(id)

    suspend fun grantEntitlement(contentId: String) {
        apiService.grantEntitlement(contentId)
    }
}
