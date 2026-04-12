package com.movierecommender.app.ui.leanback

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.movierecommender.app.MovieRecommenderApplication
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.torrent.TorrentStreamService
import com.movierecommender.app.ui.screens.firestick.FavoritesScreen
import com.movierecommender.app.ui.screens.firestick.MovieSelectionScreen
import com.movierecommender.app.ui.screens.firestick.RecommendationsScreen
import com.movierecommender.app.ui.screens.firestick.StreamingPlayerScreen
import com.movierecommender.app.ui.screens.firestick.TrailerScreen
import com.movierecommender.app.ui.theme.firestick.MovieRecommenderTheme
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModelFactory

class ComposeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCREEN = "extra_screen"
        const val EXTRA_GENRE_ID = "extra_genre_id"
        const val EXTRA_GENRE_NAME = "extra_genre_name"
        const val EXTRA_CONTENT_MODE = "extra_content_mode"

        const val SCREEN_MOVIE_SELECTION = "movie_selection"
        const val SCREEN_FAVORITES = "favorites"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MovieRecommenderApplication
        val startScreen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MOVIE_SELECTION
        val genreId = intent.getIntExtra(EXTRA_GENRE_ID, -1)
        val genreName = intent.getStringExtra(EXTRA_GENRE_NAME) ?: ""
        val contentModeName = intent.getStringExtra(EXTRA_CONTENT_MODE) ?: ContentMode.MOVIES.name

        setContent {
            val viewModel: MovieViewModel = viewModel(
                factory = MovieViewModelFactory(app.repository, app.settings)
            )
            val uiState by viewModel.uiState.collectAsState()

            // Initialize ViewModel state from Intent extras
            LaunchedEffect(Unit) {
                val mode = try {
                    ContentMode.valueOf(contentModeName)
                } catch (_: Exception) {
                    ContentMode.MOVIES
                }
                viewModel.setContentMode(mode)
                if (genreId != -1 || startScreen == SCREEN_FAVORITES) {
                    viewModel.selectGenre(genreId, genreName)
                }
            }

            MovieRecommenderTheme(darkTheme = uiState.isDarkMode) {
                LeanbackBackdrop(modifier = Modifier.fillMaxSize()) {
                    ComposeNavHost(
                        viewModel = viewModel,
                        startDestination = startScreen
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        startService(TorrentStreamService.getClearCacheIntent(this))
        super.onDestroy()
    }
}

@Composable
private fun ComposeNavHost(
    viewModel: MovieViewModel,
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(ComposeActivity.SCREEN_MOVIE_SELECTION) {
            MovieSelectionScreen(
                viewModel = viewModel,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        (navController.context as? ComponentActivity)?.finish()
                    }
                },
                onGenerateRecommendations = {
                    navController.navigate("recommendations")
                },
                onWatchNow = { title: String, magnetUrl: String ->
                    val encodedTitle = Uri.encode(title)
                    val encodedMagnet = android.util.Base64.encodeToString(
                        magnetUrl.toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    navController.navigate("streaming/$encodedTitle/$encodedMagnet")
                }
            )
        }

        composable("recommendations") {
            RecommendationsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onStartOver = {
                    // Finish this activity to go back to the Leanback browse
                    (navController.context as? ComponentActivity)?.finish()
                },
                onOpenTrailer = { title: String, videoUrl: String ->
                    val encodedTitle = Uri.encode(title)
                    val encodedUrl = android.util.Base64.encodeToString(
                        videoUrl.toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    navController.navigate("trailer/$encodedTitle/$encodedUrl")
                },
                onWatchNow = { title: String, magnetUrl: String ->
                    val encodedTitle = Uri.encode(title)
                    val encodedMagnet = android.util.Base64.encodeToString(
                        magnetUrl.toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    navController.navigate("streaming/$encodedTitle/$encodedMagnet")
                }
            )
        }

        composable(ComposeActivity.SCREEN_FAVORITES) {
            FavoritesScreen(
                viewModel = viewModel,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        (navController.context as? ComponentActivity)?.finish()
                    }
                },
                onGenerateRecommendations = {
                    navController.navigate("recommendations")
                }
            )
        }

        composable("trailer/{title}/{videoUrl}") { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: "Trailer"
            val encodedUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
            val videoUrl = try {
                String(android.util.Base64.decode(encodedUrl, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (_: Exception) {
                encodedUrl
            }
            TrailerScreen(
                title = Uri.decode(title),
                videoUrl = videoUrl,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("streaming/{title}/{magnetUrl}") { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: "Movie"
            val encodedMagnet = backStackEntry.arguments?.getString("magnetUrl") ?: ""
            val magnetUrl = try {
                String(android.util.Base64.decode(encodedMagnet, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (_: Exception) {
                encodedMagnet
            }
            StreamingPlayerScreen(
                movieTitle = Uri.decode(title),
                magnetUrl = magnetUrl,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
