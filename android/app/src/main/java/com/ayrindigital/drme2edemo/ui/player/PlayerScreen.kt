package com.ayrindigital.drme2edemo.ui.player

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ayrindigital.drme2edemo.player.PlayerManager

@Composable
fun PlayerScreen(
    contentId: String,
    viewModel: PlayerViewModel,
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
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(content) {
        if (content != null && player == null) {
            val playerManager = PlayerManager(context, viewModel.apiService, viewModel.okHttpClient)
            viewModel.createPlayer(playerManager, contentId)
        }
    }

    LaunchedEffect(player) {
        player?.playWhenReady = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text("Error: $error", color = Color.White)
            content != null && player != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                    )
                    Text(
                        content!!.title,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
