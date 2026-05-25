package com.ayrindigital.drme2edemo.data.catalog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ayrindigital.drme2edemo.data.api.ContentItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.catalogCacheDataStore by preferencesDataStore(name = "catalog_cache")
private val LIST_KEY = stringPreferencesKey("content_list")

@Singleton
class CatalogCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(items: List<ContentItem>) {
        val encoded = json.encodeToString(ListSerializer(ContentItem.serializer()), items)
        context.catalogCacheDataStore.edit { it[LIST_KEY] = encoded }
    }

    suspend fun read(): List<ContentItem> {
        val encoded = context.catalogCacheDataStore.data.first()[LIST_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ContentItem.serializer()), encoded)
        }.getOrElse { emptyList() }
    }
}
