@file:JvmName("FirestickFavoritesScreenKt")
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package com.movierecommender.app.ui.screens.firestick

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ContextThemeWrapper
import com.movierecommender.app.R
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import com.movierecommender.app.ui.leanback.PickerMediaCardPresenter
import com.movierecommender.app.ui.leanback.PickerActionItem
import com.movierecommender.app.ui.leanback.PickerActionCardPresenter
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.ui.leanback.LeanbackActionButton
import com.movierecommender.app.ui.leanback.LeanbackTopBar
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel


@JvmName("FirestickFavoritesScreenComposable")
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
    var isSearchActive by remember { mutableStateOf(false) }
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
            viewModel.getMovieWatchOptionsUpdates(movie.id, movie.title, movie.releaseDate?.take(4), budget)
                .collect { state ->
                    watchOptions = state.options
                    isLoadingWatchOptions = state.isLoading
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("FavoritesScreen", "Watch options lookup failed", error)
        } finally {
            isLoadingWatchOptions = false
        }
    }

    val selectedMovieIds = uiState.selectedMovies.map { it.id }.toSet()
    val selectedTvIds = uiState.selectedTvShows.map { it.id }.toSet()
    val favoriteMovieIds = uiState.favoriteMovies.map { it.id }.toSet()
    val selectedFavorites: List<Any> =
        uiState.favoriteMovies.filter { it.id in selectedMovieIds } +
            uiState.favoriteTvShows.filter { it.id in selectedTvIds }
    val hasSelections = selectedMovieIds.isNotEmpty() || selectedTvIds.isNotEmpty()

    val actionItems = buildList {
        add(PickerActionItem("back", "Back", "Return to browse", 0xFF23456A.toInt()))
        add(PickerActionItem("search", "Search Titles", "Find by name", 0xFF006B99.toInt()))
        if (selectedMovieIds.isNotEmpty()) {
            add(PickerActionItem("recommend_movies", "Recommend Movies", selectedMovieIds.size.toString() + " selected", 0xFF007A75.toInt()))
        }
        if (selectedTvIds.isNotEmpty()) {
            add(PickerActionItem("recommend_tv", "Recommend TV", selectedTvIds.size.toString() + " selected", 0xFF007A75.toInt()))
        }
        if (hasSelections) {
            add(PickerActionItem("clear", "Clear Selections", "Keep your favorites", 0xFF9A512F.toInt()))
        }
        add(PickerActionItem("settings", "Settings", "Recommendation preferences", 0xFF5C3BA8.toInt()))
    }

    // Use the same dark Browse/Picker background and actual native Leanback action cards
    // as the front menu rather than a large Compose dashboard header.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF303030))
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = uiState.userName.takeIf { it.isNotBlank() }?.let { it + "'s Favorites" } ?: "Favorites",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 48.dp, top = 20.dp, bottom = 6.dp)
        )
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 48.dp, top = 12.dp, bottom = 4.dp)
        )
        FavoriteLeanbackActionRail(actionItems) { action ->
            when (action.id) {
                "back" -> onBackClick()
                "search" -> isSearchActive = true
                "recommend_movies" -> {
                    viewModel.setContentMode(ContentMode.MOVIES)
                    viewModel.generateRecommendations()
                    onGenerateRecommendations()
                }
                "recommend_tv" -> {
                    viewModel.setContentMode(ContentMode.TV_SHOWS)
                    viewModel.generateTvRecommendations()
                    onGenerateRecommendations()
                }
                "clear" -> viewModel.clearSelections()
                "settings" -> showSettingsDialog = true
            }
        }

        if (isSearchActive || searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvSearchField(
                    value = searchQuery,
                    onValueChange = { value ->
                        searchQuery = value
                        viewModel.searchMovies(value)
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
                IconButton(onClick = {
                    searchQuery = ""
                    viewModel.searchMovies("")
                    isSearchActive = false
                    keyboardController?.hide()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = Color.White)
                }
            }
        }

            if (selectedFavorites.isNotEmpty()) {
                Text(
                    text = "Selected for Recommendations (" + selectedFavorites.size + ")",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 4.dp)
                )
                FavoriteLeanbackRail(
                    content = selectedFavorites,
                    selectedMovieIds = selectedMovieIds,
                    selectedTvIds = selectedTvIds,
                    favoriteMovieIds = favoriteMovieIds,
                    onMovieClick = { activeFavorite = it },
                    onTvClick = { activeTvFavorite = it }
                )
            }

            if (uiState.favoriteMovies.isNotEmpty()) {
                Text(
                    text = "Movie Favorites (" + uiState.favoriteMovies.size + ")",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 4.dp)
                )
                FavoriteLeanbackRail(
                    content = uiState.favoriteMovies,
                    selectedMovieIds = selectedMovieIds,
                    selectedTvIds = selectedTvIds,
                    favoriteMovieIds = favoriteMovieIds,
                    onMovieClick = { activeFavorite = it },
                    onTvClick = { activeTvFavorite = it }
                )
            }
            if (uiState.favoriteTvShows.isNotEmpty()) {
                Text(
                    text = "TV Show Favorites (" + uiState.favoriteTvShows.size + ")",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 4.dp)
                )
                FavoriteLeanbackRail(
                    content = uiState.favoriteTvShows,
                    selectedMovieIds = selectedMovieIds,
                    selectedTvIds = selectedTvIds,
                    favoriteMovieIds = favoriteMovieIds,
                    onMovieClick = { activeFavorite = it },
                    onTvClick = { activeTvFavorite = it }
                )
            }
            if (uiState.favoriteMovies.isEmpty() && uiState.favoriteTvShows.isEmpty()) {
                Text(
                    text = "No favorites yet. Search for movies and add them to Favorites.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
                )
            }

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Search Results",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 48.dp, top = 12.dp, bottom = 4.dp)
                )
                when {
                    uiState.isLoading ->
                        CircularProgressIndicator(modifier = Modifier.padding(start = 48.dp, top = 12.dp))
                    uiState.movies.isEmpty() ->
                        Text("No movies found", modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp))
                    else -> FavoriteLeanbackRail(
                        content = uiState.movies,
                        selectedMovieIds = selectedMovieIds,
                        selectedTvIds = selectedTvIds,
                        favoriteMovieIds = favoriteMovieIds,
                        onMovieClick = { movie ->
                            if (movie.id in favoriteMovieIds) activeFavorite = movie
                            else viewModel.addToFavorites(movie)
                        },
                        onTvClick = { activeTvFavorite = it }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
                    val isSelected = movie.id in selectedMovieIds
                    val limitReached = !isSelected && selectedMovieIds.size >= 5
                    TextButton(
                        onClick = {
                            viewModel.toggleMovieSelection(movie)
                            activeFavorite = null
                        },
                        enabled = !limitReached
                    ) {
                        Text(if (isSelected) "Remove from Recommendations" else "Select for Recommendations")
                    }
                    if (limitReached) Text("Maximum of 5 movie picks", style = MaterialTheme.typography.bodySmall)
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
                    val isSelected = show.id in selectedTvIds
                    val limitReached = !isSelected && selectedTvIds.size >= 5
                    TextButton(
                        onClick = {
                            viewModel.toggleTvShowSelection(show)
                            activeTvFavorite = null
                        },
                        enabled = !limitReached
                    ) {
                        Text(if (isSelected) "Remove from Recommendations" else "Select for Recommendations")
                    }
                    if (limitReached) Text("Maximum of 5 TV picks", style = MaterialTheme.typography.bodySmall)
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



/**
 * Host the same AndroidX Leanback presenter and focus-bordered poster cards as
 * the main picker, rather than approximating them with Compose grid tiles.
 * RecyclerView instances are remembered by AndroidView so scrolling/focus
 * are not reset on each StateFlow emission.
 */
@Composable
private fun FavoriteLeanbackRail(
    content: List<Any>,
    selectedMovieIds: Set<Int>,
    selectedTvIds: Set<Int>,
    favoriteMovieIds: Set<Int>,
    onMovieClick: (Movie) -> Unit,
    onTvClick: (TvShow) -> Unit
) {
    val movieIdsState = rememberUpdatedState(selectedMovieIds)
    val tvIdsState = rememberUpdatedState(selectedTvIds)
    val favoritesState = rememberUpdatedState(favoriteMovieIds)
    val movieCallbackState = rememberUpdatedState(onMovieClick)
    val tvCallbackState = rememberUpdatedState(onTvClick)

    val objectAdapter = remember {
        ArrayObjectAdapter(
            PickerMediaCardPresenter(
                isMovieSelected = { it.id in movieIdsState.value },
                isTvSelected = { it.id in tvIdsState.value },
                isMovieFavorite = { it.id in favoritesState.value },
                onMovieClick = { movieCallbackState.value(it) },
                onTvClick = { tvCallbackState.value(it) }
            )
        )
    }
    val bridgeAdapter = remember { ItemBridgeAdapter(objectAdapter) }
    val diffCallback = remember {
        object : DiffCallback<Any>() {
            override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean = when {
                oldItem is Movie && newItem is Movie -> oldItem.id == newItem.id
                oldItem is TvShow && newItem is TvShow -> oldItem.id == newItem.id
                else -> false
            }

            override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean = oldItem == newItem
        }
    }
    var previousItems by remember { mutableStateOf<List<Any>>(emptyList()) }
    var previousMovieSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var previousTvSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var previousFavorites by remember { mutableStateOf<Set<Int>>(emptySet()) }

    AndroidView(
        factory = { context ->
            val dp = context.resources.displayMetrics.density
            HorizontalGridView(ContextThemeWrapper(context, R.style.Theme_MovieRecommender)).apply {
                setNumRows(1)
                setHorizontalSpacing((14 * dp).toInt())
                clipToPadding = false
                clipChildren = false
                setPadding((48 * dp).toInt(), (8 * dp).toInt(),
                    (48 * dp).toInt(), (8 * dp).toInt())
                adapter = bridgeAdapter
                descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        },
        update = {
            if (previousItems != content) {
                objectAdapter.setItems(content, diffCallback)
                previousItems = content.toList()
            }
            if (previousMovieSelection != selectedMovieIds ||
                previousTvSelection != selectedTvIds ||
                previousFavorites != favoriteMovieIds
            ) {
                if (objectAdapter.size() > 0) {
                    objectAdapter.notifyArrayItemRangeChanged(0, objectAdapter.size())
                }
                previousMovieSelection = selectedMovieIds
                previousTvSelection = selectedTvIds
                previousFavorites = favoriteMovieIds
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(274.dp)
    )
}


/** The action row is the same native Leanback ImageCardView rail used in the picker. */
@Composable
private fun FavoriteLeanbackActionRail(
    actions: List<PickerActionItem>,
    onActionClick: (PickerActionItem) -> Unit
) {
    val currentClick = rememberUpdatedState(onActionClick)
    val adapter = remember {
        ArrayObjectAdapter(PickerActionCardPresenter { currentClick.value(it) })
    }
    val bridge = remember { ItemBridgeAdapter(adapter) }
    val diff = remember {
        object : DiffCallback<PickerActionItem>() {
            override fun areItemsTheSame(oldItem: PickerActionItem, newItem: PickerActionItem): Boolean =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: PickerActionItem, newItem: PickerActionItem): Boolean =
                oldItem == newItem
        }
    }
    var previous by remember { mutableStateOf<List<PickerActionItem>>(emptyList()) }

    AndroidView(
        factory = { context ->
            val dp = context.resources.displayMetrics.density
            HorizontalGridView(ContextThemeWrapper(context, R.style.Theme_MovieRecommender)).apply {
                setNumRows(1)
                setHorizontalSpacing((14 * dp).toInt())
                clipChildren = false
                clipToPadding = false
                setPadding((48 * dp).toInt(), (8 * dp).toInt(),
                    (48 * dp).toInt(), (8 * dp).toInt())
                this.adapter = bridge
                descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        },
        update = {
            if (previous != actions) {
                adapter.setItems(actions, diff)
                previous = actions.toList()
            }
        },
        modifier = Modifier.fillMaxWidth().height(145.dp)
    )
}
