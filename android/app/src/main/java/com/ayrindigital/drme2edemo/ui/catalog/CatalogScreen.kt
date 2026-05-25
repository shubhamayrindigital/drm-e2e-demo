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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import com.ayrindigital.drme2edemo.data.api.ContentItem
import com.ayrindigital.drme2edemo.ui.downloads.DownloadViewModel

@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    downloadViewModel: DownloadViewModel,
    onContentSelected: (contentId: String) -> Unit,
) {
    val content by viewModel.contentList.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val offline by viewModel.offline.collectAsState()
    val downloads by downloadViewModel.downloads.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            if (offline) "Available Content (Offline)" else "Available Content",
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (error != null) {
            Text("Error: $error")
        } else if (offline && content.isEmpty()) {
            Text("You're offline. Download videos while online to watch them here.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(content) { item ->
                    ContentItemCard(
                        item = item,
                        downloadState = downloads.find { it.id == item.id },
                        onSelect = { onContentSelected(item.id) },
                        onDownload = {
                            val manifestUrl = "http://10.0.2.2:3000/catalog/${item.id}/manifest.mpd"
                            downloadViewModel.startDownload(item.id, manifestUrl)
                        },
                        onPause = { downloadViewModel.pauseDownload(item.id) },
                        onResume = { downloadViewModel.resumeDownload(item.id) },
                        onRemove = { downloadViewModel.removeDownload(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ContentItemCard(
    item: ContentItem,
    downloadState: com.ayrindigital.drme2edemo.data.downloads.DownloadState?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.entitled) { onSelect() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title)
                    item.description?.let {
                        Text(it, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp)
                    }
                }
                Text(if (item.drm) "🔒 DRM" else "📺 Clear", fontSize = 12.sp)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (item.entitled) "✓ Entitled" else "❌ Not Entitled",
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (downloadState?.state) {
                        Download.STATE_COMPLETED -> {
                            Text("✓ Downloaded", fontSize = 12.sp)
                            Button(onClick = onRemove) { Text("Remove") }
                        }
                        Download.STATE_DOWNLOADING -> {
                            Button(onClick = onPause) { Text("Pause") }
                            Button(onClick = onRemove) { Text("Cancel") }
                        }
                        Download.STATE_QUEUED, Download.STATE_RESTARTING -> {
                            Text("Queued…", fontSize = 12.sp)
                            Button(onClick = onRemove) { Text("Cancel") }
                        }
                        Download.STATE_STOPPED -> {
                            Button(onClick = onResume) { Text("Resume") }
                            Button(onClick = onRemove) { Text("Cancel") }
                        }
                        Download.STATE_FAILED -> {
                            Text("Failed", fontSize = 12.sp)
                            Button(onClick = onRemove) { Text("Clear") }
                        }
                        else -> if (item.entitled) {
                            Button(onClick = onDownload) { Text("Download") }
                        }
                    }
                }
            }

            if (downloadState != null && downloadState.state != Download.STATE_COMPLETED) {
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    "${(downloadState.progress * 100).toInt()}%",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
