@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.movierecommender.app.ui.screens.firestick

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel
import com.movierecommender.app.ui.dialogs.firestick.LlmConsentDialog
import com.movierecommender.app.data.remote.PopcornEpisode
import com.movierecommender.app.ui.leanback.LeanbackActionButton
import com.movierecommender.app.ui.leanback.LeanbackTopBar

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
    var isSearchActive by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    
    // Content mode handling
    val isTvMode = uiState.contentMode == ContentMode.TV_SHOWS
    val contentLabel = if (isTvMode) "TV Shows" else "Movies"
    val itemCount = if (isTvMode) uiState.tvShows.size else uiState.movies.size
    val selectedCount = if (isTvMode) uiState.selectedTvShows.size else uiState.selectedMovies.size

    val lastVisibleIndex by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
    }

    LaunchedEffect(lastVisibleIndex, itemCount, uiState.canLoadMoreGenreMovies, uiState.isLoading, uiState.isLoadingMore, uiState.searchQuery, isTvMode) {
        val threshold = 9
        val nearEnd = itemCount > 0 && lastVisibleIndex >= (itemCount - threshold).coerceAtLeast(0)
        if (nearEnd && uiState.canLoadMoreGenreMovies && !uiState.isLoading && !uiState.isLoadingMore && uiState.searchQuery.isBlank()) {
            viewModel.loadNextGenreMoviesPage()
        }
    }

    Scaffold(
        topBar = {
            LeanbackTopBar(
                title = "Select $contentLabel",
                subtitle = "$selectedCount selected - choose 1-5",
                onBackClick = onBackClick,
                actions = {
                    if (selectedCount > 0) {
                        LeanbackActionButton(
                            icon = Icons.Default.DeleteSweep,
                            label = "Clear",
                            onClick = {
                                if (isTvMode) viewModel.clearTvShowSelections()
                                else viewModel.clearSelections()
                            }
                        )
                    }

                    LeanbackActionButton(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = { showSettingsDialog = true }
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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

                    // Display field (non-focusable until Search is pressed)
                    TvSearchField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchContent(it)
                        },
                        isActive = isSearchActive,
                        onActiveChange = { isSearchActive = it },
                        onSearch = {
                            viewModel.searchContent(searchQuery)
                            isSearchActive = false
                            keyboardController?.hide()
                        },
                        focusableWhenInactive = false,
                        modifier = Modifier.weight(1f)
                    )
                }

                LinearProgressIndicator(
                    progress = (selectedCount / 5f).coerceAtMost(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val itemLabel = if (isTvMode) "TV show" else "movie"
                    Text(
                        text = "$selectedCount $itemLabel${if (selectedCount != 1) "s" else ""} selected (up to 5)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (selectedCount > 0) {
                        // Get Recommendations button with focus indicator
                        val getRecsInteraction = remember { MutableInteractionSource() }
                        val getRecsFocused by getRecsInteraction.collectIsFocusedAsState()
                        
                        Button(
                            onClick = {
                                if (isTvMode) {
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
                            interactionSource = getRecsInteraction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (getRecsFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            ),
                            border = if (getRecsFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null,
                            modifier = Modifier.focusable(interactionSource = getRecsInteraction)
                        ) {
                            Icon(Icons.Default.Check, "Generate", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get Recommendations")
                        }
                    }
                }
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
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 48.dp,
                            top = 16.dp,
                            end = 48.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (isTvMode) {
                            // TV Shows grid
                            items(uiState.tvShows) { tvShow ->
                                TvShowCard(
                                    tvShow = tvShow,
                                    isSelected = uiState.selectedTvShows.any { it.id == tvShow.id },
                                    onToggleSelection = { viewModel.toggleTvShowSelection(tvShow) },
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
                                    onToggleSelection = { viewModel.toggleMovieSelection(movie) },
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
    onToggleSelection: () -> Unit,
    viewModel: MovieViewModel? = null,
    onWatchNow: (title: String, magnetUrl: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isLoadingMagnet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Watch options dialog state
    var showWatchOptions by remember { mutableStateOf(false) }
    var watchOptions by remember { mutableStateOf<List<com.movierecommender.app.data.model.WatchOption>>(emptyList()) }
    var isLoadingWatchOptions by remember { mutableStateOf(false) }
    
    // Two-level focus: card level vs button level
    var isCardActive by remember { mutableStateOf(false) }
    // Flag to consume the KeyUp that follows an activation/deactivation KeyDown
    var consumeNextUp by remember { mutableStateOf(false) }
    
    // Focus requesters
    val cardFocusRequester = remember { FocusRequester() }
    val heartFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val checkFocusRequester = remember { FocusRequester() }
    
    // Focus tracking
    val cardInteraction = remember { MutableInteractionSource() }
    val cardFocused by cardInteraction.collectIsFocusedAsState()
    val heartInteraction = remember { MutableInteractionSource() }
    val heartFocused by heartInteraction.collectIsFocusedAsState()
    val playInteraction = remember { MutableInteractionSource() }
    val playFocused by playInteraction.collectIsFocusedAsState()
    val checkInteraction = remember { MutableInteractionSource() }
    val checkFocused by checkInteraction.collectIsFocusedAsState()
    
    val anyIconFocused = heartFocused || playFocused || checkFocused
    
    // When isCardActive becomes true, focus the play button AFTER recomposition
    LaunchedEffect(isCardActive) {
        if (isCardActive) {
            try { playFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    // Deactivate if focus leaves the card entirely
    LaunchedEffect(cardFocused, anyIconFocused) {
        if (!cardFocused && !anyIconFocused) {
            isCardActive = false
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .focusRequester(cardFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (keyEvent.type == KeyEventType.KeyDown && cardFocused && !isCardActive) {
                            // First press: activate card (LaunchedEffect will focus the play button)
                            isCardActive = true
                            consumeNextUp = true
                            true
                        } else if (keyEvent.type == KeyEventType.KeyUp && consumeNextUp) {
                            // Consume the matching KeyUp so the button doesn't trigger onClick
                            consumeNextUp = false
                            true
                        } else {
                            false
                        }
                    }
                    Key.Back, Key.Escape -> {
                        if (keyEvent.type == KeyEventType.KeyDown && isCardActive) {
                            isCardActive = false
                            consumeNextUp = true
                            try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        } else if (keyEvent.type == KeyEventType.KeyUp && consumeNextUp) {
                            consumeNextUp = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = cardInteraction),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else if (cardFocused || anyIconFocused)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (cardFocused || anyIconFocused) 
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary) 
        else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (cardFocused || anyIconFocused) 12.dp else 4.dp
        )
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

            // Heart icon - top left corner
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (heartFocused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                border = if (heartFocused) BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White) else null
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    interactionSource = heartInteraction,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(heartFocusRequester)
                        .focusProperties { canFocus = isCardActive }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (heartFocused) androidx.compose.ui.graphics.Color.White 
                               else if (isFavorite) androidx.compose.ui.graphics.Color(0xFFE53935) 
                               else androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(if (heartFocused) 36.dp else 32.dp)
                    )
                }
            }

            // Watch Now button - top center
            if (viewModel != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .size(48.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (playFocused) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                    border = if (playFocused) BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White) else null
                ) {
                    IconButton(
                        onClick = {
                            if (!isLoadingWatchOptions) {
                                isLoadingWatchOptions = true
                                showWatchOptions = true
                                coroutineScope.launch {
                                    try {
                                        val year = movie.releaseDate?.take(4)
                                        val options = viewModel.getMovieWatchOptions(movie.id, movie.title, year)
                                        watchOptions = options
                                    } catch (e: Exception) {
                                        watchOptions = emptyList()
                                        Toast.makeText(context, "Error finding options", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoadingWatchOptions = false
                                    }
                                }
                            }
                        },
                        interactionSource = playInteraction,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(playFocusRequester)
                            .focusProperties { canFocus = isCardActive },
                        enabled = !isLoadingWatchOptions
                    ) {
                        if (isLoadingWatchOptions) {
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
                                modifier = Modifier.size(if (playFocused) 36.dp else 32.dp)
                            )
                        }
                    }
                }
            }

            // Checkbox - top right corner
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (checkFocused) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                border = if (checkFocused) BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White) else null
            ) {
                IconButton(
                    onClick = onToggleSelection,
                    interactionSource = checkInteraction,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(checkFocusRequester)
                        .focusProperties { canFocus = isCardActive }
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (checkFocused) androidx.compose.ui.graphics.Color.White
                               else if (isSelected) MaterialTheme.colorScheme.primary 
                               else androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(if (checkFocused) 36.dp else 32.dp)
                    )
                }
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
    
    // Watch Options Dialog
    if (showWatchOptions) {
        WatchOptionsDialog(
            title = movie.title,
            options = watchOptions,
            isLoading = isLoadingWatchOptions,
            onDismiss = {
                showWatchOptions = false
                watchOptions = emptyList()
            },
            onTorrentSelected = { magnetUrl ->
                showWatchOptions = false
                watchOptions = emptyList()
                onWatchNow(movie.title, magnetUrl)
            }
        )
    }
}

@Composable
fun TvShowCard(
    tvShow: TvShow,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
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
    
    // Watch options dialog state
    var showWatchOptions by remember { mutableStateOf(false) }
    var watchOptions by remember { mutableStateOf<List<com.movierecommender.app.data.model.WatchOption>>(emptyList()) }
    var isLoadingWatchOptions by remember { mutableStateOf(false) }
    
    // Two-level focus: card level vs button level
    var isCardActive by remember { mutableStateOf(false) }
    // Flag to consume the KeyUp that follows an activation/deactivation KeyDown
    var consumeNextUp by remember { mutableStateOf(false) }
    
    // Focus requesters
    val cardFocusRequester = remember { FocusRequester() }
    val checkFocusRequester = remember { FocusRequester() }
    val watchFocusRequester = remember { FocusRequester() }
    
    // Focus tracking
    val cardInteraction = remember { MutableInteractionSource() }
    val cardFocused by cardInteraction.collectIsFocusedAsState()
    val checkInteraction = remember { MutableInteractionSource() }
    val checkFocused by checkInteraction.collectIsFocusedAsState()
    val watchInteraction = remember { MutableInteractionSource() }
    val watchFocused by watchInteraction.collectIsFocusedAsState()
    
    val anyIconFocused = checkFocused || watchFocused
    
    // When isCardActive becomes true, focus the watch button AFTER recomposition
    LaunchedEffect(isCardActive) {
        if (isCardActive) {
            try { watchFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    // Deactivate if focus leaves the card entirely
    LaunchedEffect(cardFocused, anyIconFocused) {
        if (!cardFocused && !anyIconFocused) {
            isCardActive = false
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .focusRequester(cardFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (keyEvent.type == KeyEventType.KeyDown && cardFocused && !isCardActive) {
                            isCardActive = true
                            consumeNextUp = true
                            true
                        } else if (keyEvent.type == KeyEventType.KeyUp && consumeNextUp) {
                            consumeNextUp = false
                            true
                        } else {
                            false
                        }
                    }
                    Key.Back, Key.Escape -> {
                        if (keyEvent.type == KeyEventType.KeyDown && isCardActive) {
                            isCardActive = false
                            consumeNextUp = true
                            try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        } else if (keyEvent.type == KeyEventType.KeyUp && consumeNextUp) {
                            consumeNextUp = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = cardInteraction),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else if (cardFocused || anyIconFocused)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (cardFocused || anyIconFocused) 
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary) 
        else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (cardFocused || anyIconFocused) 12.dp else 4.dp
        )
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
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .size(48.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (watchFocused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                    border = if (watchFocused) BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White) else null
                ) {
                    IconButton(
                        onClick = {
                            if (!isLoadingWatchOptions) {
                                isLoadingWatchOptions = true
                                showWatchOptions = true
                                coroutineScope.launch {
                                    try {
                                        val year = tvShow.firstAirDate?.take(4)
                                        val options = viewModel.getTvShowWatchOptions(tvShow.id, tvShow.name, year)
                                        watchOptions = options
                                    } catch (e: Exception) {
                                        watchOptions = emptyList()
                                    } finally {
                                        isLoadingWatchOptions = false
                                    }
                                }
                            }
                        },
                        interactionSource = watchInteraction,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(watchFocusRequester)
                            .focusProperties { canFocus = isCardActive },
                        enabled = !isLoadingWatchOptions
                    ) {
                        if (isLoadingWatchOptions) {
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
                                modifier = Modifier.size(if (watchFocused) 36.dp else 32.dp)
                            )
                        }
                    }
                }
            }

            // Checkbox - top right corner
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (checkFocused) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                border = if (checkFocused) BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White) else null
            ) {
                IconButton(
                    onClick = onToggleSelection,
                    interactionSource = checkInteraction,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(checkFocusRequester)
                        .focusProperties { canFocus = isCardActive }
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (checkFocused) androidx.compose.ui.graphics.Color.White
                               else if (isSelected) MaterialTheme.colorScheme.primary 
                               else androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(if (checkFocused) 36.dp else 32.dp)
                    )
                }
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
    
    // Watch Options Dialog (streaming providers + torrent browse episodes)
    if (showWatchOptions && viewModel != null) {
        WatchOptionsDialog(
            title = tvShow.name,
            options = watchOptions,
            isLoading = isLoadingWatchOptions,
            onDismiss = {
                showWatchOptions = false
                watchOptions = emptyList()
            },
            onTorrentSelected = { magnetUrl ->
                showWatchOptions = false
                watchOptions = emptyList()
                onWatchNow(tvShow.name, magnetUrl)
            },
            onBrowseEpisodes = {
                // Close watch options and open episode picker for torrent
                showWatchOptions = false
                watchOptions = emptyList()
                isLoadingSeasons = true
                coroutineScope.launch {
                    try {
                        val imdbId = viewModel.getTvShowImdbId(tvShow.id)
                        if (imdbId != null) {
                            val seasons = viewModel.getTvShowSeasons(imdbId)
                            if (seasons.isNotEmpty()) {
                                availableSeasons = seasons
                                selectedSeason = seasons.first()
                                showEpisodePicker = true
                            } else {
                                Toast.makeText(context, "No episodes available", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Show not found", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error finding show", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoadingSeasons = false
                    }
                }
            }
        )
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
                            Toast.makeText(context, "No streaming source found", Toast.LENGTH_SHORT).show()
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
 * TV-optimized with focus indicators for DPAD navigation.
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
    var episodes by remember { mutableStateOf<List<PopcornEpisode>>(emptyList()) }
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
 * Single episode row in the episode picker with TV focus support.
 */
@Composable
fun EpisodeItem(
    episodeNumber: Int,
    title: String?,
    hasStreaming: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusable(interactionSource = interactionSource),
        onClick = onClick,
        enabled = hasStreaming,
        color = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer
            hasStreaming -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
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

// TV-friendly search field that only opens keyboard on Enter press
@Composable
fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    focusableWhenInactive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    if (isActive) {
        // Active mode - show actual text field for input
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                onSearch()
                                true
                            }
                            Key.Escape, Key.Back -> {
                                onActiveChange(false)
                                keyboardController?.hide()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            placeholder = { Text("Type to search...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true
        )
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    } else {
        // Inactive mode
        val baseBorder = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline)
        if (focusableWhenInactive) {
            // Focusable surface that opens keyboard on Enter
            Surface(
                modifier = modifier
                    .focusable(interactionSource = interactionSource)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
                        ) {
                            onActiveChange(true)
                            true
                        } else false
                    },
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (value.isEmpty()) "Press Enter to search movies..." else value,
                        color = if (value.isEmpty()) {
                            if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            // Non-focusable display: activation happens via an external Search button.
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.small,
                border = baseBorder,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (value.isEmpty()) "Use Search to enter text…" else value,
                        color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
