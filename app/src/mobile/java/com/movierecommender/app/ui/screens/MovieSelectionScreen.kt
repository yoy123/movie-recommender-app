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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.ui.viewmodel.MovieViewModel
import com.movierecommender.app.ui.dialogs.LlmConsentDialog
import com.movierecommender.app.data.remote.PopcornEpisode

@Composable
fun MovieSelectionScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onGenerateRecommendations: () -> Unit,
    onWatchNow: (title: String, magnetUrl: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    
    val isTvMode = uiState.contentMode == ContentMode.TV_SHOWS
    val contentLabel = if (isTvMode) "TV Shows" else "Movies"
    val contentLabelSingular = if (isTvMode) "show" else "movie"
    
    // Get the appropriate item count for infinite scroll
    val itemCount = if (isTvMode) uiState.tvShows.size else uiState.movies.size
    val selectedCount = if (isTvMode) uiState.selectedTvShows.size else uiState.selectedMovies.size

    val lastVisibleIndex by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
    }

    LaunchedEffect(
        lastVisibleIndex,
        itemCount,
        uiState.canLoadMoreGenreMovies,
        uiState.isLoading,
        uiState.isLoadingMore,
        uiState.searchQuery
    ) {
        // Infinite scroll for genre browsing (disabled while searching).
        val threshold = 6
        val nearEnd = itemCount > 0 && lastVisibleIndex >= (itemCount - threshold).coerceAtLeast(0)
        if (nearEnd && uiState.canLoadMoreGenreMovies && !uiState.isLoading && !uiState.isLoadingMore && uiState.searchQuery.isBlank()) {
            viewModel.loadNextGenreMoviesPage()
        }
    }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Select $contentLabel (1-5)") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (selectedCount > 0) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(onClick = { 
                                    if (isTvMode) viewModel.clearTvShowSelections() 
                                    else viewModel.clearSelections() 
                                }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
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
                        viewModel.searchContent(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search ${contentLabel.lowercase()}...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search")
                    },
                    singleLine = true
                )
                
                LinearProgressIndicator(
                    progress = (selectedCount / 5f).coerceAtMost(1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "$selectedCount $contentLabelSingular${if (selectedCount != 1) "s" else ""} selected (up to 5)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        floatingActionButton = {
            if (selectedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isTvMode) {
                            // TV shows don't use LLM, go straight to recommendations
                            viewModel.generateTvRecommendations()
                            onGenerateRecommendations()
                        } else {
                            // Check if consent is needed before generating recommendations
                            if (!uiState.llmConsentAsked) {
                                viewModel.checkAndShowLlmConsentIfNeeded()
                            } else {
                                viewModel.generateRecommendations()
                                onGenerateRecommendations()
                            }
                        }
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
                itemCount == 0 -> {
                    Text(
                        text = "No ${contentLabel.lowercase()} found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = if (selectedCount > 0) 100.dp else 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isTvMode) {
                            // TV Shows grid
                            items(uiState.tvShows) { tvShow ->
                                TvShowCard(
                                    tvShow = tvShow,
                                    isSelected = uiState.selectedTvShows.any { it.id == tvShow.id },
                                    onClick = { viewModel.toggleTvShowSelection(tvShow) },
                                    viewModel = viewModel,
                                    onWatchNow = onWatchNow
                                )
                            }
                        } else {
                            // Movies grid
                            items(uiState.movies) { movie ->
                                MovieCard(
                                    movie = movie,
                                    isSelected = uiState.selectedMovies.any { it.id == movie.id },
                                    isFavorite = movie.isFavorite,
                                    onToggleFavorite = {
                                        if (movie.isFavorite) viewModel.removeFromFavorites(movie.id)
                                        else viewModel.addToFavorites(movie)
                                    },
                                    onClick = { viewModel.toggleMovieSelection(movie) },
                                    viewModel = viewModel,
                                    onWatchNow = onWatchNow
                                )
                            }
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
    
    // LLM Consent Dialog (GDPR/CCPA compliance)
    if (uiState.showLlmConsentDialog) {
        LlmConsentDialog(
            onAccept = {
                viewModel.onLlmConsentResponse(consented = true)
                // After consent, proceed to generate recommendations
                viewModel.generateRecommendations()
                onGenerateRecommendations()
            },
            onDecline = {
                viewModel.onLlmConsentResponse(consented = false)
                // Still generate recommendations, but will use TMDB fallback
                viewModel.generateRecommendations()
                onGenerateRecommendations()
            },
            onDismiss = {
                viewModel.dismissLlmConsentDialog()
            }
        )
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    isSelected: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    viewModel: MovieViewModel? = null,
    onWatchNow: (title: String, magnetUrl: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isLoadingMagnet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
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

            // Watch Now button - top center - ALWAYS VISIBLE
            if (viewModel != null) {
                IconButton(
                    onClick = {
                        if (!isLoadingMagnet) {
                            isLoadingMagnet = true
                            coroutineScope.launch {
                                try {
                                    val year = movie.releaseDate?.take(4)
                                    val magnet = viewModel.getTorrentMagnetUrl(movie.title, year)
                                    if (magnet != null) {
                                        onWatchNow(movie.title, magnet)
                                    } else {
                                        Toast.makeText(context, "No streaming source found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error finding source", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoadingMagnet = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .size(48.dp),
                    enabled = !isLoadingMagnet
                ) {
                    if (isLoadingMagnet) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Watch Now",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvShowCard(
    tvShow: TvShow,
    isSelected: Boolean,
    onClick: () -> Unit,
    viewModel: MovieViewModel? = null,
    onWatchNow: (title: String, magnetUrl: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoadingMagnet by remember { mutableStateOf(false) }
    var showEpisodePicker by remember { mutableStateOf(false) }
    var availableSeasons by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf(1) }
    var isLoadingSeasons by remember { mutableStateOf(false) }
    
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
                model = tvShow.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                contentDescription = tvShow.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            
            // Watch Now button - top center
            if (viewModel != null) {
                IconButton(
                    onClick = {
                        if (!isLoadingMagnet && !isLoadingSeasons) {
                            isLoadingSeasons = true
                            coroutineScope.launch {
                                try {
                                    // Get IMDB ID from TMDB
                                    val imdbId = viewModel.getTvShowImdbId(tvShow.id)
                                    if (imdbId != null) {
                                        // Get available seasons from Popcorn Time
                                        val seasons = viewModel.getTvShowSeasons(imdbId)
                                        if (seasons.isNotEmpty()) {
                                            availableSeasons = seasons
                                            selectedSeason = seasons.first()
                                            showEpisodePicker = true
                                        } else {
                                            Toast.makeText(context, "No episodes available for streaming", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Show not found on streaming service", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error finding show", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoadingSeasons = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .size(48.dp),
                    enabled = !isLoadingMagnet && !isLoadingSeasons
                ) {
                    if (isLoadingMagnet || isLoadingSeasons) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Watch Now",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Checkbox - top right corner
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
                        text = tvShow.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tvShow.overview,
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
                            text = "★ ${String.format("%.1f", tvShow.voteAverage)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFFFFD700)
                        )
                        tvShow.firstAirDate?.let {
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
    
    // Episode Picker Dialog
    if (showEpisodePicker && viewModel != null) {
        EpisodePickerDialog(
            showName = tvShow.name,
            seasons = availableSeasons,
            selectedSeason = selectedSeason,
            onSeasonSelected = { selectedSeason = it },
            onEpisodeSelected = { episode ->
                showEpisodePicker = false
                isLoadingMagnet = true
                coroutineScope.launch {
                    try {
                        val imdbId = viewModel.getTvShowImdbId(tvShow.id)
                        val magnet = viewModel.getTvEpisodeMagnetUrl(
                            showTitle = tvShow.name,
                            imdbId = imdbId,
                            season = selectedSeason,
                            episode = episode
                        )
                        if (magnet != null) {
                            onWatchNow("${tvShow.name} S${selectedSeason}E${episode}", magnet)
                        } else {
                            Toast.makeText(context, "No streaming source found for this episode", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error finding source", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoadingMagnet = false
                    }
                }
            },
            onDismiss = { showEpisodePicker = false },
            viewModel = viewModel
        )
    }
}

/**
 * Dialog for selecting a TV show episode to stream.
 * Shows season tabs and episode list for the selected season.
 */
@Composable
fun EpisodePickerDialog(
    showName: String,
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MovieViewModel,
    preResolvedImdbId: String? = null
) {
    var episodes by remember { mutableStateOf<List<com.movierecommender.app.data.remote.PopcornEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }
    var imdbId by remember { mutableStateOf(preResolvedImdbId) }
    
    // Load episodes when season changes
    LaunchedEffect(selectedSeason) {
        isLoadingEpisodes = true
        try {
            // Use pre-resolved IMDB ID if available, otherwise search Popcorn then TMDB
            if (imdbId == null) {
                val searchResult = viewModel.searchTvShowTorrent(showName)
                imdbId = searchResult?.imdbId
            }
            if (imdbId == null) {
                imdbId = viewModel.resolveImdbIdByTitle(showName)
            }
            if (imdbId != null) {
                episodes = viewModel.getTvShowEpisodes(imdbId!!, selectedSeason)
            }
        } catch (e: Exception) {
            episodes = emptyList()
        } finally {
            isLoadingEpisodes = false
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = showName,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Season tabs
                if (seasons.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp
                    ) {
                        seasons.forEach { season ->
                            Tab(
                                selected = season == selectedSeason,
                                onClick = { onSeasonSelected(season) },
                                text = { Text("Season $season") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (seasons.isNotEmpty()) {
                    Text(
                        text = "Season ${seasons.first()}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Episode list
                if (isLoadingEpisodes) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (episodes.isEmpty()) {
                    Text(
                        text = "No episodes available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(episodes.size) { index ->
                            val episode = episodes[index]
                            val episodeNum = episode.episode ?: (index + 1)
                            EpisodeItem(
                                episodeNumber = episodeNum,
                                title = episode.title,
                                hasStreaming = episode.torrents?.isNotEmpty() == true,
                                onClick = { onEpisodeSelected(episodeNum) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Single episode row in the episode picker.
 */
@Composable
fun EpisodeItem(
    episodeNumber: Int,
    title: String?,
    hasStreaming: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        enabled = hasStreaming,
        color = if (hasStreaming) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "E$episodeNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!title.isNullOrBlank() && title != "Episode $episodeNumber") {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            if (hasStreaming) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Stream available",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
