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
import androidx.compose.ui.platform.LocalContext
import com.movierecommender.app.data.model.WatchOption
import kotlinx.coroutines.CancellationException
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
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.ui.viewmodel.MovieViewModel

@Composable
fun FavoritesScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onGenerateRecommendations: () -> Unit = {},
    onWatchNow: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeFavorite by remember { mutableStateOf<Movie?>(null) }
    var activeTvFavorite by remember { mutableStateOf<TvShow?>(null) }
    var watchTvFavorite by remember { mutableStateOf<TvShow?>(null) }
    var tvWatchOptions by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    var isLoadingTvOptions by remember { mutableStateOf(false) }
    var watchFavorite by remember { mutableStateOf<Movie?>(null) }
    var watchOptions by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    var isLoadingWatchOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(watchFavorite) {
        val movie = watchFavorite ?: return@LaunchedEffect
        watchOptions = emptyList()
        isLoadingWatchOptions = true
        try {
            val budget = viewModel.safeCacheBudgetMb(context)
            watchOptions = viewModel.getMovieWatchOptions(
                movie.id, movie.title, movie.releaseDate?.take(4), budget
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("FavoritesScreen", "Watch options lookup failed", error)
        } finally {
            isLoadingWatchOptions = false
        }
    }
    
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.selectedMovies.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            viewModel.setContentMode(ContentMode.MOVIES)
                            viewModel.generateRecommendations()
                            onGenerateRecommendations()
                        },
                        icon = { Icon(Icons.Default.Check, "Generate movie recommendations") },
                        text = { Text("Recommend Movies") }
                    )
                }
                if (uiState.selectedTvShows.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            viewModel.setContentMode(ContentMode.TV_SHOWS)
                            viewModel.generateTvRecommendations()
                            onGenerateRecommendations()
                        },
                        icon = { Icon(Icons.Default.Check, "Generate TV recommendations") },
                        text = { Text("Recommend TV Shows") }
                    )
                }
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
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${uiState.selectedMovies.size} movies selected for recommendations",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    viewModel.generateRecommendations()
                                    onGenerateRecommendations()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Generate")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Get Recommendations")
                            }
                        }
                    }
                }
                
                // Favorites Section
                if (uiState.favoriteMovies.isNotEmpty() || uiState.favoriteTvShows.isNotEmpty()) {
                    Text(
                        text = "Your Favorites (${uiState.favoriteMovies.size + uiState.favoriteTvShows.size})",
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
                                onOpenActions = { activeFavorite = movie },
                                onToggleSelection = { viewModel.toggleMovieSelection(movie) }
                            )
                        }
                        items(uiState.favoriteTvShows, key = { "tv_" + it.id }) { show ->
                            Card(
                                onClick = { activeTvFavorite = show },
                                modifier = Modifier.fillMaxWidth().height(280.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = show.posterPath?.let { "https://image.tmdb.org/t/p/w500" + it },
                                        contentDescription = show.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { viewModel.toggleTvShowSelection(show) },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Select TV show for recommendations",
                                            tint = androidx.compose.ui.graphics.Color.White
                                        )
                                    }
                                    Surface(
                                        color = androidx.compose.ui.graphics.Color(0xD0000000),
                                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    ) {
                                        Text(show.name, modifier = Modifier.padding(8.dp), color = androidx.compose.ui.graphics.Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No favorites yet. Search and add movies and TV shows!",
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
    
    activeFavorite?.let { movie ->
        AlertDialog(
            onDismissRequest = { activeFavorite = null },
            title = { Text(movie.title) },
            text = {
                Column {
                    TextButton(onClick = {
                        activeFavorite = null
                        watchFavorite = movie
                    }) { Text("Watch Options") }
                    TextButton(onClick = {
                        viewModel.removeFromFavorites(movie.id)
                        activeFavorite = null
                    }) { Text("Delete from Favorites") }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeFavorite = null }) { Text("Cancel") }
            }
        )
    }

    watchFavorite?.let { movie ->
        WatchOptionsDialog(
            title = movie.title,
            options = watchOptions,
            isLoading = isLoadingWatchOptions,
            onDismiss = { watchFavorite = null },
            onTorrentSelected = { magnet ->
                watchFavorite = null
                onWatchNow(movie.title, magnet)
            }
        )
    }

    activeTvFavorite?.let { show ->
        AlertDialog(
            onDismissRequest = { activeTvFavorite = null },
            title = { Text(show.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        activeTvFavorite = null
                        watchTvFavorite = show
                    }) { Text("Watch Options") }
                    TextButton(onClick = {
                        viewModel.removeTvShowFromFavorites(show.id)
                        activeTvFavorite = null
                    }) { Text("Delete from Favorites") }
                }
            },
            confirmButton = { TextButton(onClick = { activeTvFavorite = null }) { Text("Cancel") } }
        )
    }

    LaunchedEffect(watchTvFavorite) {
        val show = watchTvFavorite ?: return@LaunchedEffect
        tvWatchOptions = emptyList()
        isLoadingTvOptions = true
        try {
            tvWatchOptions = viewModel.getTvShowWatchOptions(
                show.id, show.name, show.firstAirDate?.take(4)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("FavoritesScreen", "TV watch options lookup failed", error)
        } finally {
            isLoadingTvOptions = false
        }
    }

    watchTvFavorite?.let { show ->
        WatchOptionsDialog(
            title = show.name,
            options = tvWatchOptions,
            isLoading = isLoadingTvOptions,
            onDismiss = { watchTvFavorite = null },
            onTorrentSelected = { magnet ->
                watchTvFavorite = null
                onWatchNow(show.name, magnet)
            }
        )
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
    onOpenActions: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Card(
        onClick = onOpenActions,
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
            modifier = Modifier.fillMaxSize()
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
            
            // Keep recommendation selection available independently of the card action menu.
            IconButton(
                onClick = onToggleSelection,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (isSelected) "Deselect for recommendations" else "Select for recommendations",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White
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

