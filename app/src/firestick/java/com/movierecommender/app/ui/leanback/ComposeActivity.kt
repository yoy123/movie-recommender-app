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
import com.movierecommender.app.ui.screens.firestick.LiveTvScreen
import com.movierecommender.app.ui.screens.firestick.MovieSelectionScreen
import com.movierecommender.app.ui.screens.firestick.RecommendationsScreen
import com.movierecommender.app.ui.screens.firestick.SettingsScreen
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
        const val EXTRA_TRAILER_TITLE = "extra_trailer_title"
        const val EXTRA_TRAILER_URL = "extra_trailer_url"
        const val EXTRA_SELECTED_TV_SHOWS_JSON = "extra_selected_tv_shows_json"
        const val EXTRA_LLM_CONSENT_GIVEN = "extra_llm_consent_given"
        const val EXTRA_STREAMING_TITLE = "extra_streaming_title"
        const val EXTRA_STREAMING_MAGNET = "extra_streaming_magnet"

        const val SCREEN_MOVIE_SELECTION = "movie_selection"
        const val SCREEN_FAVORITES = "favorites"
        const val SCREEN_RECOMMENDATIONS = "recommendations"
        const val SCREEN_LIVE_TV = "live_tv"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_TRAILER = "trailer"
        const val SCREEN_STREAMING = "streaming"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MovieRecommenderApplication
        val startScreen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MOVIE_SELECTION
        val genreId = intent.getIntExtra(EXTRA_GENRE_ID, -1)
        val genreName = intent.getStringExtra(EXTRA_GENRE_NAME) ?: ""
        val contentModeName = intent.getStringExtra(EXTRA_CONTENT_MODE) ?: ContentMode.MOVIES.name
        val trailerTitle = intent.getStringExtra(EXTRA_TRAILER_TITLE) ?: ""
        val trailerUrl = intent.getStringExtra(EXTRA_TRAILER_URL) ?: ""
        val streamingTitle = intent.getStringExtra(EXTRA_STREAMING_TITLE) ?: ""
        val streamingMagnet = intent.getStringExtra(EXTRA_STREAMING_MAGNET) ?: ""
        val selectedTvShowsJson = intent.getStringExtra(EXTRA_SELECTED_TV_SHOWS_JSON)
        val llmConsentGiven = intent.getBooleanExtra(EXTRA_LLM_CONSENT_GIVEN, false)

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
                // Pre-set LLM consent from the launching activity so it's available
                // before TV show selections trigger recommendation generation
                if (llmConsentGiven) {
                    viewModel.setLlmConsentGiven(true)
                }
                // Restore TV show selections passed from the picker
                if (!selectedTvShowsJson.isNullOrBlank()) {
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<List<com.movierecommender.app.data.model.TvShow>>() {}.type
                        val shows: List<com.movierecommender.app.data.model.TvShow> = com.google.gson.Gson().fromJson(selectedTvShowsJson, type)
                        viewModel.setSelectedTvShows(shows)
                    } catch (_: Exception) { }
                }
            }

            MovieRecommenderTheme(darkTheme = uiState.isDarkMode) {
                LeanbackBackdrop(modifier = Modifier.fillMaxSize()) {
                    ComposeNavHost(
                        viewModel = viewModel,
                        startDestination = startScreen,
                        trailerTitle = trailerTitle,
                        trailerUrl = trailerUrl,
                        streamingTitle = streamingTitle,
                        streamingMagnet = streamingMagnet
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
    trailerTitle: String = "",
    trailerUrl: String = "",
    streamingTitle: String = "",
    streamingMagnet: String = "",
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

        composable(ComposeActivity.SCREEN_LIVE_TV) {
            LiveTvScreen(
                onBackClick = {
                    if (!navController.popBackStack()) {
                        (navController.context as? ComponentActivity)?.finish()
                    }
                }
            )
        }

        composable(ComposeActivity.SCREEN_SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        (navController.context as? ComponentActivity)?.finish()
                    }
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

        composable(ComposeActivity.SCREEN_TRAILER) {
            TrailerScreen(
                title = trailerTitle,
                videoUrl = trailerUrl,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        (navController.context as? ComponentActivity)?.finish()
                    }
                }
            )
        }

        // Used when launched externally (e.g. from LeanbackPickerFragment) via Intent extras
        composable(ComposeActivity.SCREEN_STREAMING) {
            StreamingPlayerScreen(
                movieTitle = streamingTitle,
                magnetUrl = streamingMagnet,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        (navController.context as? ComponentActivity)?.finish()
                    }
                }
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
