@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package com.movierecommender.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.ui.viewmodel.MovieViewModel

@Composable
fun FavoritesScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onGenerateRecommendations: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${uiState.userName}'s Favorites") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
        },
        floatingActionButton = {
            if (uiState.selectedMovies.size > 0) {
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search any movie...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchMovies("")
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.length > 0) {
                                viewModel.searchMovies(searchQuery)
                            }
                            keyboardController?.hide()
                        }
                    )
                )
                
                // Selection counter
                if (uiState.selectedMovies.size > 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "${uiState.selectedMovies.size} selected for recommendations",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Favorites Section
                if (uiState.favoriteMovies.size > 0) {
                    Text(
                        text = "Your Favorites (${uiState.favoriteMovies.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = if (uiState.selectedMovies.size > 0) 100.dp else 8.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.favoriteMovies) { movie ->
                            FavoriteMovieCard(
                                movie = movie,
                                isSelected = uiState.selectedMovies.any { selected: Movie -> selected.id == movie.id },
                                onRemove = { viewModel.removeFromFavorites(movie.id) },
                                onToggleSelection = { viewModel.toggleMovieSelection(movie) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No favorites yet. Search and add movies!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                
                Divider()
                
                // Search Results Section
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "Search Results",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            uiState.isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
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
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.movies) { movie ->
                                        SearchResultMovieCard(
                                            movie = movie,
                                            isFavorite = uiState.favoriteMovies.any { it.id == movie.id },
                                            onAdd = { viewModel.addToFavorites(movie) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        PreferenceSettingsDialog(
            currentPreference = uiState.indiePreference,
            currentUserName = uiState.userName,
            onPreferenceChange = { viewModel.updateIndiePreference(it) },
            onUserNameChange = { viewModel.updateUserName(it) },
            // Toggles and values
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
            // Callbacks
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
            // Dark mode
            isDarkMode = uiState.isDarkMode,
            onDarkModeChange = { viewModel.updateDarkMode(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun FavoriteMovieCard(
    movie: Movie,
    isSelected: Boolean,
    onRemove: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Card(
        onClick = {}, // Disable card click - we'll handle it on the Box
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onToggleSelection() } // Click on image area to select
        ) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${movie.posterPath}",
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Selection indicator
            if (isSelected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.padding(4.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            // Remove button - clicking this WON'T trigger selection
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clickable { onRemove() }, // Separate click handler
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from Favorites",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun SearchResultMovieCard(
    movie: Movie,
    isFavorite: Boolean,
    onAdd: () -> Unit
) {
    Card(
        onClick = { if (!isFavorite) onAdd() },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${movie.posterPath}",
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (isFavorite) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "✓ Added",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

