package com.ayrindigital.drme2edemo.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayrindigital.drme2edemo.data.api.ContentItem

@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onContentSelected: (contentId: String) -> Unit,
) {
    val content by viewModel.contentList.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Available Content", modifier = Modifier.padding(bottom = 16.dp))

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (error != null) {
            Text("Error: $error")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(content) { item ->
                    ContentItemCard(
                        item = item,
                        onSelect = { onContentSelected(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ContentItemCard(
    item: ContentItem,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.entitled) { onSelect() },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title)
                    item.description?.let {
                        Text(it, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Text(if (item.drm) "🔒 DRM" else "📺 Clear")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(if (item.entitled) "✓ Entitled" else "❌ Not Entitled")
        }
    }
}
