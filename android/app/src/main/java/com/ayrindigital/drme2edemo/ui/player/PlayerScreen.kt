package com.ayrindigital.drme2edemo.ui.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ayrindigital.drme2edemo.player.PlayerManager
import com.ayrindigital.drme2edemo.ui.common.LoadingDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    contentId: String,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val content by viewModel.content.collectAsState()
    val player by viewModel.player.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val context = LocalContext.current

    DisposableEffect(contentId) {
        viewModel.loadContent(contentId)
        onDispose {}
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(content) {
        if (content != null && player == null) {
            val playerManager = PlayerManager(
                context = context,
                apiService = viewModel.apiService,
                okHttpClient = viewModel.okHttpClient,
                downloadCache = viewModel.downloadCache,
                offlineLicenseStore = viewModel.offlineLicenseStore,
            )
            viewModel.createPlayer(playerManager, contentId)
        }
    }

    LaunchedEffect(player) {
        player?.playWhenReady = true
    }

    LoadingDialog(
        visible = loading,
        title = "Preparing playback…",
    )

    // Release ExoPlayer before navigating so the SurfaceView tears down ahead of the nav crossfade,
    // avoiding the ugly snap where the legacy surface lingers past the fade-out.
    val dismiss: () -> Unit = {
        viewModel.releasePlayer()
        onBack()
    }
    BackHandler(enabled = true, onBack = dismiss)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        content?.title ?: "Player",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = dismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> Text("Error: $error", color = Color.White)
                content != null && player != null -> AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
