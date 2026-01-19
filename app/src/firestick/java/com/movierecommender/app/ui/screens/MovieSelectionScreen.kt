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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel

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

    val lastVisibleIndex by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
    }

    LaunchedEffect(lastVisibleIndex, uiState.movies.size, uiState.canLoadMoreGenreMovies, uiState.isLoading, uiState.isLoadingMore, uiState.searchQuery) {
        // Infinite scroll for genre browsing (disabled while searching).
        val threshold = 9
        val nearEnd = uiState.movies.isNotEmpty() && lastVisibleIndex >= (uiState.movies.size - threshold).coerceAtLeast(0)
        if (nearEnd && uiState.canLoadMoreGenreMovies && !uiState.isLoading && !uiState.isLoadingMore && uiState.searchQuery.isBlank()) {
            viewModel.loadNextGenreMoviesPage()
        }
    }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Select Movies (1-5)") },
                    navigationIcon = {
                        // TV-friendly back button with focus indicator
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
                                    Icons.Default.ArrowBack, 
                                    "Back",
                                    tint = if (backFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(if (backFocused) 32.dp else 24.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.selectedMovies.isNotEmpty()) {
                            // Clear selections button with focus indicator
                            val clearInteraction = remember { MutableInteractionSource() }
                            val clearFocused by clearInteraction.collectIsFocusedAsState()
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (clearFocused) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    border = if (clearFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                                ) {
                                    IconButton(
                                        onClick = { viewModel.clearSelections() },
                                        interactionSource = clearInteraction
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "Clear selections",
                                            tint = if (clearFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(if (clearFocused) 32.dp else 24.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "clear selections",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 8.sp,
                                    modifier = Modifier.offset(y = (-8).dp)
                                )
                            }
                        }
                        
                        // Settings button with focus indicator
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
                
                // TV-friendly search (button-driven so D-pad can pass without auto-entering)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 8.dp),
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
                }
                
                LinearProgressIndicator(
                    progress = (uiState.selectedMovies.size / 5f).coerceAtMost(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.selectedMovies.size} movie${if (uiState.selectedMovies.size != 1) "s" else ""} selected (up to 5)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (uiState.selectedMovies.isNotEmpty()) {
                        // Get Recommendations button with focus indicator
                        val getRecsInteraction = remember { MutableInteractionSource() }
                        val getRecsFocused by getRecsInteraction.collectIsFocusedAsState()
                        
                        Button(
                            onClick = {
                                viewModel.generateRecommendations()
                                onGenerateRecommendations()
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
                uiState.movies.isEmpty() -> {
                    Text(
                        text = "No movies found",
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
    onToggleSelection: () -> Unit,
    viewModel: MovieViewModel? = null,
    onWatchNow: (title: String, magnetUrl: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isLoadingMagnet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val selectFocusRequester = remember { FocusRequester() }
    
    Card(
        onClick = { selectFocusRequester.requestFocus() },
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
                onClick = onToggleSelection,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .focusRequester(selectFocusRequester)
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
