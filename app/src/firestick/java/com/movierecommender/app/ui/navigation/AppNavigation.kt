package com.movierecommender.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.movierecommender.app.data.repository.MovieRepository
import com.movierecommender.app.ui.screens.GenreSelectionScreen
import com.movierecommender.app.ui.screens.MovieSelectionScreen
import com.movierecommender.app.ui.screens.RecommendationsScreen
import com.movierecommender.app.ui.screens.FavoritesScreen
import com.movierecommender.app.ui.screens.TrailerScreen
import com.movierecommender.app.ui.viewmodel.MovieViewModel
import com.movierecommender.app.ui.viewmodel.MovieViewModelFactory
import com.movierecommender.app.data.settings.SettingsRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import android.net.Uri

sealed class Screen(val route: String) {
    object GenreSelection : Screen("genre_selection")
    object MovieSelection : Screen("movie_selection")
    object Recommendations : Screen("recommendations")
    object Favorites : Screen("favorites")
    object Trailer : Screen("trailer/{title}/{videoUrl}")
}

@Composable
fun AppNavigation(
    repository: MovieRepository,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val settings = remember(context) { SettingsRepository(context.applicationContext) }
    val viewModel: MovieViewModel = viewModel(
        factory = MovieViewModelFactory(repository, settings)
    )
    
    NavHost(
        navController = navController,
        startDestination = Screen.GenreSelection.route
    ) {
        composable(Screen.GenreSelection.route) {
            GenreSelectionScreen(
                viewModel = viewModel,
                onGenreSelected = {
                    // Check if favorites mode was selected
                    if (viewModel.uiState.value.isFavoritesMode) {
                        navController.navigate(Screen.Favorites.route)
                    } else {
                        navController.navigate(Screen.MovieSelection.route)
                    }
                }
            )
        }
        
        composable(Screen.MovieSelection.route) {
            MovieSelectionScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onGenerateRecommendations = {
                    navController.navigate(Screen.Recommendations.route)
                }
            )
        }
        
        composable(Screen.Recommendations.route) {
            RecommendationsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onStartOver = {
                    navController.popBackStack(Screen.GenreSelection.route, false)
                },
                onOpenTrailer = { title: String, videoUrl: String ->
                    android.util.Log.d("AppNavigation", "onOpenTrailer called: title=$title")
                    android.util.Log.d("AppNavigation", "Original URL length: ${videoUrl.length}")
                    android.util.Log.d("AppNavigation", "Original URL: $videoUrl")
                    val encodedTitle = Uri.encode(title)
                    android.util.Log.d("AppNavigation", "Encoded title: $encodedTitle")
                    // Use Base64 to avoid issues with special characters and long URLs
                    val encodedUrl = android.util.Base64.encodeToString(
                        videoUrl.toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    android.util.Log.d("AppNavigation", "Base64 encoded URL length: ${encodedUrl.length}")
                    android.util.Log.d("AppNavigation", "Base64 encoded URL: $encodedUrl")
                    val route = "trailer/$encodedTitle/$encodedUrl"
                    android.util.Log.d("AppNavigation", "Navigating to route: $route")
                    try {
                        navController.navigate(route)
                        android.util.Log.d("AppNavigation", "Navigation successful")
                    } catch (e: Exception) {
                        android.util.Log.e("AppNavigation", "Navigation failed", e)
                    }
                }
            )
        }
        
        composable(Screen.Favorites.route) {
            FavoritesScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onGenerateRecommendations = {
                    navController.navigate(Screen.Recommendations.route)
                }
            )
        }

        composable(Screen.Trailer.route) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: "Trailer"
            val encodedUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
            // Decode Base64 URL
            val videoUrl = try {
                String(android.util.Base64.decode(encodedUrl, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (e: Exception) {
                encodedUrl // Fallback to original if decoding fails
            }
            TrailerScreen(
                title = Uri.decode(title),
                videoUrl = videoUrl,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
