package com.ayrindigital.drme2edemo.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.ayrindigital.drme2edemo.BuildConfig
import com.ayrindigital.drme2edemo.data.api.ContentItem
import com.ayrindigital.drme2edemo.data.downloads.DownloadState
import com.ayrindigital.drme2edemo.ui.common.LoadingDialog
import com.ayrindigital.drme2edemo.ui.downloads.DownloadViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val licenseExpiries by downloadViewModel.licenseExpiries.collectAsState()

    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        TerminologyDialog(onDismiss = { showInfo = false })
    }

    // Only show the modal loader while we're actually waiting on the network — not for the
    // local fallback path that just reads cached content from disk.
    LoadingDialog(
        visible = loading && !offline && content.isEmpty(),
        title = "Loading catalog…",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Catalog", fontWeight = FontWeight.SemiBold)
                        if (offline) {
                            Text(
                                "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Glossary")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        CatalogBody(
            innerPadding = innerPadding,
            loading = loading,
            error = error,
            offline = offline,
            content = content,
            downloads = downloads,
            licenseExpiries = licenseExpiries,
            onSelect = onContentSelected,
            onChipTap = { showInfo = true },
            onDownload = { item ->
                val manifestUrl = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/catalog/${item.id}/manifest.mpd"
                downloadViewModel.startDownload(item.id, manifestUrl)
            },
            onResume = { downloadViewModel.resumeDownload(it.id) },
            onRemove = { downloadViewModel.removeDownload(it.id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogBody(
    innerPadding: PaddingValues,
    loading: Boolean,
    error: String?,
    offline: Boolean,
    content: List<ContentItem>,
    downloads: List<DownloadState>,
    licenseExpiries: Map<String, Long>,
    onSelect: (String) -> Unit,
    onChipTap: () -> Unit,
    onDownload: (ContentItem) -> Unit,
    onResume: (ContentItem) -> Unit,
    onRemove: (ContentItem) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when {
            error != null -> Text(
                "Error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
            offline && content.isEmpty() -> Text(
                "You're offline. Download videos while online to watch them here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
            content.isEmpty() && loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // `key = it.id` gives LazyColumn the same identity-based diffing semantics that
                // RecyclerView+ListAdapter+DiffUtil provided — items keep their state, and only
                // genuinely-changed rows re-compose. `animateItem` adds insert/remove/move
                // transitions so license-expiry removals slide out instead of popping.
                items(content, key = { it.id }) { item ->
                    ContentItemCard(
                        modifier = Modifier.animateItem(),
                        item = item,
                        downloadState = downloads.find { it.id == item.id },
                        licenseExpiryAt = licenseExpiries[item.id],
                        onSelect = { onSelect(item.id) },
                        onChipTap = onChipTap,
                        onDownload = { onDownload(item) },
                        onResume = { onResume(item) },
                        onRemove = { onRemove(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentItemCard(
    modifier: Modifier = Modifier,
    item: ContentItem,
    downloadState: DownloadState?,
    licenseExpiryAt: Long?,
    onSelect: () -> Unit,
    onChipTap: () -> Unit,
    onDownload: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    item.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    label = if (item.drm) "DRM" else "Clear",
                    icon = if (item.drm) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    tint = if (item.drm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    onClick = onChipTap,
                )
                StatusChip(
                    label = if (item.entitled) "Entitled" else "Not entitled",
                    icon = if (item.entitled) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    tint = if (item.entitled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    onClick = onChipTap,
                )
                if (downloadState?.state == Download.STATE_COMPLETED) {
                    StatusChip(
                        label = "Downloaded",
                        icon = Icons.Filled.Cloud,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = onChipTap,
                    )
                }
            }

            if (item.drm && downloadState?.state == Download.STATE_COMPLETED) {
                Spacer(Modifier.height(10.dp))
                LicenseCountdown(expiryAt = licenseExpiryAt)
            }

            Spacer(Modifier.height(16.dp))
            ActionRow(
                item = item,
                downloadState = downloadState,
                onPlay = onSelect,
                onDownload = onDownload,
                onResume = onResume,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = tint,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun LicenseCountdown(expiryAt: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (expiryAt == null) {
            CountdownLine(
                text = "Offline license: not cached",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = Icons.Filled.Schedule,
            )
        } else {
            val remainingMs by produceState(
                initialValue = expiryAt - System.currentTimeMillis(),
                expiryAt,
            ) {
                while (true) {
                    value = expiryAt - System.currentTimeMillis()
                    if (value <= 0) break
                    delay(500)
                }
            }
            if (remainingMs <= 0) {
                CountdownLine(
                    text = "Offline license expired — re-download to refresh",
                    tint = MaterialTheme.colorScheme.error,
                    icon = Icons.Filled.WarningAmber,
                )
            } else {
                CountdownLine(
                    text = "Offline license expires in ${formatRemaining(remainingMs)}",
                    tint = MaterialTheme.colorScheme.tertiary,
                    icon = Icons.Filled.Bolt,
                )
            }
        }
        Text(
            text = "POC: timer is client-enforced. Real Widevine/FairPlay enforces this inside the CDM.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp),
        )
    }
}

@Composable
private fun CountdownLine(text: String, tint: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun ActionRow(
    item: ContentItem,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    val isDownloaded = downloadState?.state == Download.STATE_COMPLETED
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (downloadState?.state) {
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED,
            Download.STATE_RESTARTING -> {
                PlayPrimary(item, isDownloaded, onPlay)
                FilledTonalButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Downloading…")
                }
            }
            Download.STATE_COMPLETED -> {
                PlayPrimary(item, isDownloaded, onPlay)
                FilledTonalButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Remove")
                }
            }
            Download.STATE_STOPPED -> {
                PlayPrimary(item, isDownloaded, onPlay)
                FilledTonalButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Resume")
                }
                OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            Download.STATE_FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Download failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                PlayPrimary(item, isDownloaded, onPlay)
                OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Clear") }
            }
            else -> {
                PlayPrimary(item, isDownloaded, onPlay)
                if (item.entitled) {
                    FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPrimary(
    item: ContentItem,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Button(
        onClick = onClick,
        enabled = item.entitled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(6.dp))
        Text(if (isDownloaded) "Play (offline)" else "Play")
    }
}

@Composable
private fun TerminologyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        title = { Text("Glossary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlossaryEntry(
                    icon = Icons.Filled.Lock,
                    term = "DRM",
                    definition = "Stream is encrypted. Player must obtain a license to decrypt before playback.",
                )
                GlossaryEntry(
                    icon = Icons.Filled.LockOpen,
                    term = "Clear",
                    definition = "Stream is unencrypted. No license required; player downloads and plays directly.",
                )
                GlossaryEntry(
                    icon = Icons.Filled.CheckCircle,
                    term = "Entitled",
                    definition = "Your account has been granted access to this title. You can play and download it.",
                )
                GlossaryEntry(
                    icon = Icons.Filled.WarningAmber,
                    term = "Not entitled",
                    definition = "Your account has no access yet. Playback and download are disabled.",
                )
                GlossaryEntry(
                    icon = Icons.Filled.Cloud,
                    term = "Downloaded",
                    definition = "Segments are cached on this device, so the title plays without a network connection.",
                )
                GlossaryEntry(
                    icon = Icons.Filled.Bolt,
                    term = "Offline license expiry",
                    definition = "Conceptually: a DRM license lets the device decrypt offline for a limited window, after which the CDM stops releasing keys. In this POC the window is enforced by the app (60 s timer + auto-remove), not by the ClearKey CDM — real Widevine/FairPlay enforce expiry inside the CDM so it can't be bypassed by a modified client.",
                )
            }
        },
    )
}

@Composable
private fun GlossaryEntry(icon: ImageVector, term: String, definition: String) {
    Row {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                definition,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

