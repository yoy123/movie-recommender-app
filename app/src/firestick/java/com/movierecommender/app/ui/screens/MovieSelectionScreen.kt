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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.movierecommender.app.ui.leanback.LeanbackPanel
import com.movierecommender.app.ui.leanback.LeanbackTextButton
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
                LeanbackPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        run {
                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                                border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
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
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val itemLabel = if (isTvMode) "TV show" else "movie"
                        Text(
                            text = "$selectedCount $itemLabel${if (selectedCount != 1) "s" else ""} selected (up to 5)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (selectedCount > 0) {
                            LeanbackTextButton(
                                label = "Analyze Picks",
                                onClick = {
                                    if (isTvMode) {
                                        viewModel.generateTvRecommendations()
                                        onGenerateRecommendations()
                                    } else {
                                        if (!uiState.llmConsentAsked) {
                                            viewModel.checkAndShowLlmConsentIfNeeded()
                                        } else {
                                            viewModel.generateRecommendations()
                                            onGenerateRecommendations()
                                        }
                                    }
                                },
                                emphasized = true
                            )
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
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(
                            start = 56.dp,
                            top = 16.dp,
                            end = 56.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
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
private fun PickerInfoChip(
    text: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PickerActionButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    focused: Boolean,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester,
    canFocus: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailingLabel: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            contentColor = if (focused) Color.White else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        ),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties { this.canFocus = canFocus }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailingLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (focused) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            .height(246.dp)
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
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            else if (cardFocused || anyIconFocused)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
        ),
        border = when {
            cardFocused || anyIconFocused -> BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (cardFocused || anyIconFocused) 12.dp else 4.dp
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                PickerInfoChip(
                    text = "TMDB ${String.format("%.1f", movie.voteAverage)}",
                    containerColor = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = movie.overview.ifBlank { "No synopsis available yet." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    movie.releaseDate?.take(4)?.let {
                        PickerInfoChip(text = it, containerColor = MaterialTheme.colorScheme.tertiary)
                    }
                    if (isFavorite) {
                        PickerInfoChip(text = "Favorite", containerColor = MaterialTheme.colorScheme.error)
                    }
                    if (isSelected) {
                        PickerInfoChip(text = "Selected", containerColor = MaterialTheme.colorScheme.secondary)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (isCardActive) {
                        "Choose an action on the right. Press Back to return to browsing."
                    } else {
                        "Press center to open actions for watching, saving, or selecting this movie."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .width(252.dp)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                PickerActionButton(
                    label = if (isFavorite) "Favorited" else "Add Favorite",
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    accentColor = MaterialTheme.colorScheme.error,
                    focused = heartFocused,
                    interactionSource = heartInteraction,
                    focusRequester = heartFocusRequester,
                    canFocus = isCardActive,
                    onClick = onToggleFavorite,
                    trailingLabel = if (isFavorite) "Saved" else null
                )

                Spacer(modifier = Modifier.height(12.dp))

                PickerActionButton(
                    label = if (isLoadingWatchOptions) "Loading Watch Options" else "Open Watch Options",
                    icon = Icons.Filled.PlayArrow,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    focused = playFocused,
                    interactionSource = playInteraction,
                    focusRequester = playFocusRequester,
                    canFocus = isCardActive,
                    onClick = {
                        if (!isLoadingWatchOptions) {
                            isLoadingWatchOptions = true
                            showWatchOptions = true
                            coroutineScope.launch {
                                try {
                                    val year = movie.releaseDate?.take(4)
                                    val options = viewModel?.getMovieWatchOptions(movie.id, movie.title, year).orEmpty()
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
                    enabled = !isLoadingWatchOptions,
                    trailingLabel = if (viewModel != null) "Watch" else null
                )

                Spacer(modifier = Modifier.height(12.dp))

                PickerActionButton(
                    label = if (isSelected) "Remove Pick" else "Select Pick",
                    icon = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    focused = checkFocused,
                    interactionSource = checkInteraction,
                    focusRequester = checkFocusRequester,
                    canFocus = isCardActive,
                    onClick = onToggleSelection,
                    trailingLabel = if (isSelected) "Ready" else null
                )
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
            .height(246.dp)
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
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            else if (cardFocused || anyIconFocused)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
        ),
        border = when {
            cardFocused || anyIconFocused -> BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (cardFocused || anyIconFocused) 12.dp else 4.dp
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = tvShow.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    contentDescription = tvShow.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                PickerInfoChip(
                    text = "TMDB ${String.format("%.1f", tvShow.voteAverage)}",
                    containerColor = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tvShow.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = tvShow.overview.ifBlank { "No synopsis available yet." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tvShow.firstAirDate?.take(4)?.let {
                        PickerInfoChip(text = it, containerColor = MaterialTheme.colorScheme.tertiary)
                    }
                    if (isSelected) {
                        PickerInfoChip(text = "Selected", containerColor = MaterialTheme.colorScheme.secondary)
                    }
                    PickerInfoChip(text = "Series", containerColor = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (isCardActive) {
                        "Choose an action on the right. Back returns to the show list."
                    } else {
                        "Press center to open actions for watching or selecting this show."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .width(252.dp)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                PickerActionButton(
                    label = if (isLoadingWatchOptions) "Loading Watch Options" else "Open Watch Options",
                    icon = Icons.Filled.PlayArrow,
                    accentColor = MaterialTheme.colorScheme.primary,
                    focused = watchFocused,
                    interactionSource = watchInteraction,
                    focusRequester = watchFocusRequester,
                    canFocus = isCardActive,
                    onClick = {
                        if (!isLoadingWatchOptions) {
                            isLoadingWatchOptions = true
                            showWatchOptions = true
                            coroutineScope.launch {
                                try {
                                    val year = tvShow.firstAirDate?.take(4)
                                    val options = viewModel?.getTvShowWatchOptions(tvShow.id, tvShow.name, year).orEmpty()
                                    watchOptions = options
                                } catch (e: Exception) {
                                    watchOptions = emptyList()
                                } finally {
                                    isLoadingWatchOptions = false
                                }
                            }
                        }
                    },
                    enabled = !isLoadingWatchOptions,
                    trailingLabel = if (viewModel != null) "Watch" else null
                )

                Spacer(modifier = Modifier.height(12.dp))

                PickerActionButton(
                    label = if (isSelected) "Remove Pick" else "Select Pick",
                    icon = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    focused = checkFocused,
                    interactionSource = checkInteraction,
                    focusRequester = checkFocusRequester,
                    canFocus = isCardActive,
                    onClick = onToggleSelection,
                    trailingLabel = if (isSelected) "Ready" else null
                )
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
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        LeanbackPanel(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .wrapContentHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = showName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                LeanbackTextButton(
                    label = "Close",
                    onClick = onDismiss
                )
            }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LeanbackTextButton(
                    label = "Cancel",
                    onClick = onDismiss
                )
            }
        }
    }
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
