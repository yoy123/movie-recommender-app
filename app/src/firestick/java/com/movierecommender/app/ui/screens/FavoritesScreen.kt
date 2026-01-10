@file:JvmName("FirestickFavoritesScreenKt")
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package com.movierecommender.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.ui.viewmodel.MovieViewModel


@JvmName("FirestickFavoritesScreenComposable")
@Composable
fun FavoritesScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onGenerateRecommendations: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val hasSelections = uiState.selectedMovies.isNotEmpty()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${uiState.userName}'s Favorites") },
                navigationIcon = {
                    val backInteraction = remember { MutableInteractionSource() }
                    val backFocused by backInteraction.collectIsFocusedAsState()

                    Surface(
                        modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (backFocused) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        border = if (backFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            interactionSource = backInteraction
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (backFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (backFocused) 32.dp else 24.dp)
                            )
                        }
                    }
                },
                actions = {
                    val settingsInteraction = remember { MutableInteractionSource() }
                    val settingsFocused by settingsInteraction.collectIsFocusedAsState()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 32.dp)
                    ) {
                        Surface(
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            shape = MaterialTheme.shapes.small,
                            color = if (settingsFocused) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            border = if (settingsFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                        ) {
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                interactionSource = settingsInteraction
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = if (settingsFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(if (settingsFocused) 32.dp else 24.dp)
                                )
                            }
                        }
                        Text(
                            text = "settings",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 8.sp,
                            modifier = Modifier.offset(y = (-8).dp)
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
            if (hasSelections) {
                val fabInteraction = remember { MutableInteractionSource() }
                val fabFocused by fabInteraction.collectIsFocusedAsState()

                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.generateRecommendations()
                        onGenerateRecommendations()
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = "Generate") },
                    text = { Text("Get Recommendations") },
                    containerColor = if (fabFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.focusable(interactionSource = fabInteraction),
                    interactionSource = fabInteraction
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // TV-friendly Search Bar (no auto-IME on focus)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search button
                    run {
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                interactionSource = interaction,
                                modifier = Modifier.focusable(interactionSource = interaction)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Activate search",
                                    tint = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    TvSearchField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            // Live search as text changes.
                            viewModel.searchMovies(it)
                        },
                        isActive = isSearchActive,
                        onActiveChange = { isSearchActive = it },
                        onSearch = {
                            viewModel.searchMovies(searchQuery)
                            isSearchActive = false
                            keyboardController?.hide()
                        },
                        focusableWhenInactive = false,
                        modifier = Modifier.weight(1f)
                    )

                    // Clear button
                    run {
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.searchMovies("")
                                    isSearchActive = false
                                    keyboardController?.hide()
                                },
                                enabled = searchQuery.isNotBlank(),
                                interactionSource = interaction,
                                modifier = Modifier.focusable(interactionSource = interaction)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                // Selection counter
                if (hasSelections) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${uiState.selectedMovies.size} selected for recommendations",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Favorites Section
                if (uiState.favoriteMovies.size > 0) {
                    Text(
                        text = "Your Favorites (${uiState.favoriteMovies.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 48.dp,
                            top = 16.dp,
                            end = 48.dp,
                            bottom = if (hasSelections) 120.dp else 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
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
                                    contentPadding = PaddingValues(
                                        start = 48.dp,
                                        top = 16.dp,
                                        end = 48.dp,
                                        bottom = if (hasSelections) 120.dp else 24.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
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

@JvmName("FirestickFavoriteMovieCard")
@Composable
fun FavoriteMovieCard(
    movie: Movie,
    isSelected: Boolean,
    onRemove: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Card(
        onClick = onToggleSelection,
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

            // Heart icon (favorites) - top left - ALWAYS VISIBLE
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Remove from favorites",
                    tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Checkbox - top right - ALWAYS VISIBLE
            IconButton(
                onClick = onToggleSelection,
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
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
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

@JvmName("FirestickSearchResultMovieCard")
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
            .height(280.dp),
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
                model = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                contentDescription = movie.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )

            // Heart icon - top left - ALWAYS VISIBLE
            IconButton(
                onClick = { if (!isFavorite) onAdd() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Already in favorites" else "Add to favorites",
                    tint = if (isFavorite) androidx.compose.ui.graphics.Color(0xFFE53935) else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Info section
            Surface(
                color = androidx.compose.ui.graphics.Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isFavorite) {
                        Text(
                            text = "✓ Added",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            text = "Press to add",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

