@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.movierecommender.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.ui.viewmodel.MovieViewModel

@Composable
fun MovieSelectionScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onGenerateRecommendations: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Select Movies (1-5)") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (uiState.selectedMovies.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(onClick = { viewModel.clearSelections() }) {
                                    Icon(
                                        Icons.Default.Autorenew,
                                        contentDescription = "Clear selections",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Text(
                                    text = "clear selections",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 8.sp,
                                    modifier = Modifier.offset(y = (-8).dp)
                                )
                            }
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchMovies(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search movies...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search")
                    },
                    singleLine = true
                )
                
                LinearProgressIndicator(
                    progress = (uiState.selectedMovies.size / 5f).coerceAtMost(1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${uiState.selectedMovies.size} movie${if (uiState.selectedMovies.size != 1) "s" else ""} selected (up to 5)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        floatingActionButton = {
            if (uiState.selectedMovies.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.generateRecommendations()
                        onGenerateRecommendations()
                    },
                    icon = { Icon(Icons.Default.Check, "Generate") },
                    text = { Text("Get Recommendations") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
                uiState.movies.isEmpty() -> {
                    Text(
                        text = "No movies found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = if (uiState.selectedMovies.isNotEmpty()) 100.dp else 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.movies) { movie ->
                            MovieCard(
                                movie = movie,
                                isSelected = uiState.selectedMovies.any { it.id == movie.id },
                                isFavorite = movie.isFavorite,
                                onToggleFavorite = {
                                    if (movie.isFavorite) viewModel.removeFromFavorites(movie.id)
                                    else viewModel.addToFavorites(movie)
                                },
                                onClick = { viewModel.toggleMovieSelection(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showSettingsDialog) {
        PreferenceSettingsDialog(
            currentPreference = uiState.indiePreference,
            currentUserName = uiState.userName,
            onPreferenceChange = { viewModel.updateIndiePreference(it) },
            onUserNameChange = { viewModel.updateUserName(it) },
            useIndiePreference = uiState.useIndiePreference,
            usePopularityPreference = uiState.usePopularityPreference,
            releaseYearStart = uiState.releaseYearStart,
            releaseYearEnd = uiState.releaseYearEnd,
            useReleaseYearPreference = uiState.useReleaseYearPreference,
            tonePreference = uiState.tonePreference,
            useTonePreference = uiState.useTonePreference,
            internationalPreference = uiState.internationalPreference,
            useInternationalPreference = uiState.useInternationalPreference,
            experimentalPreference = uiState.experimentalPreference,
            useExperimentalPreference = uiState.useExperimentalPreference,
            popularityPreference = uiState.popularityPreference,
            onUseIndieChange = { viewModel.updateUseIndiePreference(it) },
            onUsePopularityChange = { viewModel.updateUsePopularityPreference(it) },
            onReleaseYearStartChange = { viewModel.updateReleaseYearStart(it) },
            onReleaseYearEndChange = { viewModel.updateReleaseYearEnd(it) },
            onUseReleaseYearChange = { viewModel.updateUseReleaseYearPreference(it) },
            onToneChange = { viewModel.updateTonePreference(it) },
            onUseToneChange = { viewModel.updateUseTonePreference(it) },
            onInternationalChange = { viewModel.updateInternationalPreference(it) },
            onUseInternationalChange = { viewModel.updateUseInternationalPreference(it) },
            onExperimentalChange = { viewModel.updateExperimentalPreference(it) },
            onUseExperimentalChange = { viewModel.updateUseExperimentalPreference(it) },
            onPopularityChange = { viewModel.updatePopularityPreference(it) },
            isDarkMode = uiState.isDarkMode,
            onDarkModeChange = { viewModel.updateDarkMode(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    isSelected: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            // Heart icon - top left corner - ALWAYS VISIBLE
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) androidx.compose.ui.graphics.Color(0xFFE53935) else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Checkbox - top right corner - ALWAYS VISIBLE
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Info section with semi-transparent background for readability
            Surface(
                color = androidx.compose.ui.graphics.Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = movie.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "★ ${String.format("%.1f", movie.voteAverage)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFFFFD700)
                        )
                        movie.releaseDate?.let {
                            Text(
                                text = it.take(4),
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
