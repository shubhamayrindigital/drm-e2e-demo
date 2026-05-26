package com.ayrindigital.drme2edemo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ayrindigital.drme2edemo.ui.auth.AuthViewModel
import com.ayrindigital.drme2edemo.ui.auth.LoginScreen
import com.ayrindigital.drme2edemo.ui.catalog.CatalogScreen
import com.ayrindigital.drme2edemo.ui.catalog.CatalogViewModel
import com.ayrindigital.drme2edemo.ui.player.PlayerScreen
import com.ayrindigital.drme2edemo.ui.player.PlayerViewModel
import com.ayrindigital.drme2edemo.ui.theme.DRME2EDemoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force dark-style system bars regardless of OS theme so icons stay light over our dark UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DRME2EDemoTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val userEmail by authViewModel.userEmail.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (userEmail != null) "catalog" else "login",
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("catalog") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                viewModel = authViewModel,
            )
        }

        composable("catalog") {
            val catalogViewModel: CatalogViewModel = hiltViewModel()
            val downloadViewModel: com.ayrindigital.drme2edemo.ui.downloads.DownloadViewModel = hiltViewModel()
            CatalogScreen(
                viewModel = catalogViewModel,
                downloadViewModel = downloadViewModel,
                onContentSelected = { contentId ->
                    navController.navigate("player/$contentId")
                },
            )
        }

        composable("player/{contentId}") { backStackEntry ->
            val contentId = backStackEntry.arguments?.getString("contentId") ?: return@composable
            val playerViewModel: PlayerViewModel = hiltViewModel()
            PlayerScreen(
                contentId = contentId,
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
