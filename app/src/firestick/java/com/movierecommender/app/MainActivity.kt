package com.movierecommender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movierecommender.app.ui.navigation.AppNavigation
import com.movierecommender.app.ui.theme.MovieRecommenderTheme
import com.movierecommender.app.ui.viewmodel.MovieViewModel
import com.movierecommender.app.ui.viewmodel.MovieViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val application = application as MovieRecommenderApplication
        val repository = application.repository
        
        setContent {
            val viewModel: MovieViewModel = viewModel(
                factory = MovieViewModelFactory(repository, application.settings)
            )
            val uiState by viewModel.uiState.collectAsState()
            
            MovieRecommenderTheme(darkTheme = uiState.isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(repository = repository)
                }
            }
        }
    }
}
