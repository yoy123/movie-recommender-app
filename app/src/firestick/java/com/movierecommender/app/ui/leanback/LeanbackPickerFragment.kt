package com.movierecommender.app.ui.leanback

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.movierecommender.app.MovieRecommenderApplication
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.remote.PopcornEpisode
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.WatchOption
import com.movierecommender.app.ui.screens.firestick.WatchOptionsDialog
import com.movierecommender.app.ui.screens.firestick.EpisodePickerDialog
import com.movierecommender.app.ui.theme.firestick.MovieRecommenderTheme
import com.movierecommender.app.firestick.LeanbackPickerActivity
import com.movierecommender.app.ui.viewmodel.firestick.MovieUiState
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModelFactory
import kotlinx.coroutines.launch

class LeanbackPickerFragment : BrowseSupportFragment() {

    companion object {
        private const val ARG_GENRE_ID = "arg_genre_id"
        private const val ARG_GENRE_NAME = "arg_genre_name"
        private const val ARG_CONTENT_MODE = "arg_content_mode"

        private const val HEADER_ACTIONS = 0L
        private const val HEADER_CONTENT = 1L

        private const val ACTION_TOGGLE_PICK = "toggle_pick"
        private const val ACTION_TOGGLE_FAVORITE = "toggle_favorite"
        private const val ACTION_WATCH_OPTIONS = "watch_options"
        private const val ACTION_ANALYZE = "analyze"
        private const val ACTION_CLEAR = "clear"
        private const val ACTION_SEARCH = "search"

        fun newInstance(
            genreId: Int,
            genreName: String,
            contentModeName: String
        ): LeanbackPickerFragment = LeanbackPickerFragment().apply {
            arguments = bundleOf(
                ARG_GENRE_ID to genreId,
                ARG_GENRE_NAME to genreName,
                ARG_CONTENT_MODE to contentModeName
            )
        }
    }

    private lateinit var viewModel: MovieViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var actionAdapter: ArrayObjectAdapter
    private lateinit var contentAdapter: ArrayObjectAdapter
    private lateinit var contentPresenter: PickerMediaCardPresenter

    private var genreId: Int = -1
    private var genreName: String = ""
    private var contentMode: ContentMode = ContentMode.MOVIES
    private var currentMediaId: Int? = null
    private var cachedContentIds: List<Int> = emptyList()
    private var latestState: MovieUiState = MovieUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = requireActivity().application as MovieRecommenderApplication
        viewModel = ViewModelProvider(
            requireActivity(),
            MovieViewModelFactory(app.repository, app.settings)
        )[MovieViewModel::class.java]

        genreId = requireArguments().getInt(ARG_GENRE_ID)
        genreName = requireArguments().getString(ARG_GENRE_NAME).orEmpty()
        contentMode = runCatching {
            ContentMode.valueOf(requireArguments().getString(ARG_CONTENT_MODE).orEmpty())
        }.getOrDefault(ContentMode.MOVIES)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()
        setupEventListeners()
        observeViewModel()

        viewModel.setContentMode(contentMode)
        viewModel.selectGenre(genreId, genreName)
    }

    private fun setupUi() {
        title = "Select ${if (contentMode == ContentMode.TV_SHOWS) "TV Shows" else "Movies"}"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = 0xFF1B1B2F.toInt()
        searchAffordanceColor = 0xFF00BCD4.toInt()

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        actionAdapter = ArrayObjectAdapter(PickerActionCardPresenter())
        contentPresenter = PickerMediaCardPresenter(
            isMovieSelected = { movie -> latestState.selectedMovies.any { it.id == movie.id } },
            isTvSelected = { show -> latestState.selectedTvShows.any { it.id == show.id } },
            isMovieFavorite = { movie -> latestState.favoriteMovies.any { it.id == movie.id } || movie.isFavorite }
        )
        contentAdapter = ArrayObjectAdapter(contentPresenter)

        // Don't add rows yet - they'll be added in rebuildRows() once content is available.
        adapter = rowsAdapter

        setOnSearchClickedListener {
            showSearchDialog()
        }
    }

    private fun setupEventListeners() {
        onItemViewSelectedListener = OnItemViewSelectedListener {
                _: Presenter.ViewHolder?,
                item: Any?,
                _: RowPresenter.ViewHolder?,
                _: Row? ->

            when (item) {
                is Movie -> {
                    currentMediaId = item.id
                    updateActionRow(latestState)
                    maybeLoadMore(item.id)
                }

                is TvShow -> {
                    currentMediaId = item.id
                    updateActionRow(latestState)
                    maybeLoadMore(item.id)
                }
            }
        }

        onItemViewClickedListener = OnItemViewClickedListener {
                _: Presenter.ViewHolder?,
                item: Any?,
                _: RowPresenter.ViewHolder?,
                _: Row? ->

            when (item) {
                is PickerActionItem -> handleAction(item.id)
                is Movie -> viewModel.toggleMovieSelection(item)
                is TvShow -> viewModel.toggleTvShowSelection(item)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->

                    latestState = state
                    syncCurrentMedia(state)
                    updateActionRow(state)
                    syncContentRow(state)
                    updateTitle(state)
                }
            }
        }
    }

    private fun syncCurrentMedia(state: MovieUiState) {
        val currentContentIds = currentContentIds(state)
        if (currentContentIds.isEmpty()) {
            currentMediaId = null
            return
        }

        if (currentMediaId !in currentContentIds) {
            currentMediaId = currentContentIds.first()
        }
    }

    private fun syncContentRow(state: MovieUiState) {
        val ids = currentContentIds(state)
        if (ids != cachedContentIds) {
            val previousIds = cachedContentIds
            cachedContentIds = ids
            val isAppendUpdate =
                previousIds.isNotEmpty() &&
                    ids.size > previousIds.size &&
                    ids.subList(0, previousIds.size) == previousIds

            if (isAppendUpdate) {
                val alreadyBound = contentAdapter.size()
                if (contentMode == ContentMode.TV_SHOWS) {
                    state.tvShows.drop(alreadyBound).forEach(contentAdapter::add)
                } else {
                    state.movies.drop(alreadyBound).forEach(contentAdapter::add)
                }
            } else {
                contentAdapter.clear()
                if (contentMode == ContentMode.TV_SHOWS) {
                    state.tvShows.forEach(contentAdapter::add)
                } else {
                    state.movies.forEach(contentAdapter::add)
                }
            }
            rebuildRows()
        } else if (contentAdapter.size() > 0) {
            contentAdapter.notifyArrayItemRangeChanged(0, contentAdapter.size())
        }
    }

    private fun rebuildRows() {
        // Only rebuild (and reset focus to row 0) when the row structure changes for the first
        // time. Subsequent content updates (new pages loading) should not clear rowsAdapter or
        // call setSelectedPosition — that yanks Leanback focus mid-scroll and causes accidental
        // back navigation.
        val needsActions = actionAdapter.size() > 0
        val needsContent = contentAdapter.size() > 0
        val expectedSize = (if (needsActions) 1 else 0) + (if (needsContent) 1 else 0)
        if (rowsAdapter.size() == expectedSize) {
            // Row structure unchanged — just notify so presenters refresh without touching focus
            return
        }
        rowsAdapter.clear()
        if (needsActions) {
            rowsAdapter.add(ListRow(HeaderItem(HEADER_ACTIONS, "Actions"), actionAdapter))
        }
        if (needsContent) {
            rowsAdapter.add(ListRow(HeaderItem(HEADER_CONTENT, genreName.ifBlank { "Titles" }), contentAdapter))
        }
        if (rowsAdapter.size() > 0) {
            setSelectedPosition(0)
        }
    }

    private fun updateActionRow(state: MovieUiState) {
        val currentMovie = if (contentMode == ContentMode.MOVIES) {
            state.movies.firstOrNull { it.id == currentMediaId }
        } else {
            null
        }
        val currentTvShow = if (contentMode == ContentMode.TV_SHOWS) {
            state.tvShows.firstOrNull { it.id == currentMediaId }
        } else {
            null
        }

        val selectedCount = if (contentMode == ContentMode.TV_SHOWS) state.selectedTvShows.size else state.selectedMovies.size
        val rows = mutableListOf<PickerActionItem>()

        currentMovie?.let { movie ->
            val isSelected = state.selectedMovies.any { it.id == movie.id }
            rows += PickerActionItem(
                id = ACTION_TOGGLE_PICK,
                title = if (isSelected) "Remove Pick" else "Add Pick",
                description = movie.title,
                accentColor = 0xFF0E7490.toInt()
            )
            rows += PickerActionItem(
                id = ACTION_TOGGLE_FAVORITE,
                title = if (state.favoriteMovies.any { it.id == movie.id } || movie.isFavorite) "Remove Favorite" else "Save Favorite",
                description = "Library shortcut",
                accentColor = 0xFF8B1E3F.toInt()
            )
            rows += PickerActionItem(
                id = ACTION_WATCH_OPTIONS,
                title = "Watch Options",
                description = movie.title,
                accentColor = 0xFF6D28D9.toInt()
            )
        }

        currentTvShow?.let { show ->
            val isSelected = state.selectedTvShows.any { it.id == show.id }
            rows += PickerActionItem(
                id = ACTION_TOGGLE_PICK,
                title = if (isSelected) "Remove Pick" else "Add Pick",
                description = show.name,
                accentColor = 0xFF0E7490.toInt()
            )
            rows += PickerActionItem(
                id = ACTION_WATCH_OPTIONS,
                title = "Watch Options",
                description = show.name,
                accentColor = 0xFF6D28D9.toInt()
            )
        }

        rows += PickerActionItem(
            id = ACTION_SEARCH,
            title = if (latestState.searchQuery.isBlank()) "Search Titles" else "Clear Search",
            description = if (latestState.searchQuery.isBlank()) "Find by name" else "\"${latestState.searchQuery}\"",
            accentColor = 0xFF0369A1.toInt()
        )
        rows += PickerActionItem(
            id = ACTION_ANALYZE,
            title = "Analyze Picks",
            description = if (selectedCount == 0) "Select 1-5 titles first" else "$selectedCount titles ready",
            accentColor = 0xFF0F766E.toInt()
        )
        rows += PickerActionItem(
            id = ACTION_CLEAR,
            title = "Clear Picks",
            description = if (selectedCount == 0) "Nothing selected" else "Reset current picks",
            accentColor = 0xFF7C2D12.toInt()
        )

        actionAdapter.clear()
        rows.forEach(actionAdapter::add)
    }

    private fun updateTitle(state: MovieUiState) {
        val count = if (contentMode == ContentMode.TV_SHOWS) state.selectedTvShows.size else state.selectedMovies.size
        val base = if (contentMode == ContentMode.TV_SHOWS) "Select TV Shows" else "Select Movies"
        title = if (genreName.isBlank()) "$base • $count selected" else "$base • $count selected • $genreName"
    }

    private fun handleAction(actionId: String) {
        when (actionId) {
            ACTION_TOGGLE_PICK -> {
                latestState.movies.firstOrNull { it.id == currentMediaId }?.let(viewModel::toggleMovieSelection)
                latestState.tvShows.firstOrNull { it.id == currentMediaId }?.let(viewModel::toggleTvShowSelection)
            }

            ACTION_TOGGLE_FAVORITE -> {
                val movie = latestState.movies.firstOrNull { it.id == currentMediaId }
                if (movie == null) {
                    Toast.makeText(requireContext(), "Favorites only apply to movies here", Toast.LENGTH_SHORT).show()
                } else if (latestState.favoriteMovies.any { it.id == movie.id } || movie.isFavorite) {
                    viewModel.removeFromFavorites(movie.id)
                } else {
                    viewModel.addToFavorites(movie)
                }
            }

            ACTION_WATCH_OPTIONS -> openWatchOptions()

            ACTION_ANALYZE -> launchRecommendationsIfReady()

            ACTION_CLEAR -> {
                if (contentMode == ContentMode.TV_SHOWS) viewModel.clearTvShowSelections()
                else viewModel.clearSelections()
            }

            ACTION_SEARCH -> {
                if (latestState.searchQuery.isNotBlank()) {
                    viewModel.searchContent("")
                } else {
                    showSearchDialog()
                }
            }
        }
    }

    private fun showSearchDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "Enter title..."
            setSingleLine()
            setText(latestState.searchQuery)
            selectAll()
        }
        val padding = (resources.displayMetrics.density * 16).toInt()
        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(padding, 0, padding, 0)
            addView(editText)
        }
        val label = if (contentMode == ContentMode.TV_SHOWS) "TV Shows" else "Movies"
        val builder = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Search $label")
            .setView(container)
            .setPositiveButton("Search") { _, _ ->
                val query = editText.text.toString().trim()
                viewModel.searchContent(query)
            }
            .setNegativeButton("Cancel", null)
        if (latestState.searchQuery.isNotBlank()) {
            builder.setNeutralButton("Clear") { _, _ -> viewModel.searchContent("") }
        }
        builder.show().also { dialog ->
            editText.requestFocus()
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
        }
    }

    private fun launchRecommendationsIfReady() {
        val selectedCount = if (contentMode == ContentMode.TV_SHOWS) latestState.selectedTvShows.size else latestState.selectedMovies.size
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), "Pick at least one title first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), ComposeActivity::class.java).apply {
            putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_RECOMMENDATIONS)
            putExtra(ComposeActivity.EXTRA_GENRE_ID, genreId)
            putExtra(ComposeActivity.EXTRA_GENRE_NAME, genreName)
            putExtra(ComposeActivity.EXTRA_CONTENT_MODE, contentMode.name)
            putExtra(ComposeActivity.EXTRA_LLM_CONSENT_GIVEN, latestState.llmConsentGiven)
            if (contentMode == ContentMode.TV_SHOWS) {
                putExtra(ComposeActivity.EXTRA_SELECTED_TV_SHOWS_JSON, com.google.gson.Gson().toJson(latestState.selectedTvShows))
            }
        }
        startActivity(intent)
    }

    private fun maybeLoadMore(selectedId: Int) {
        if (!latestState.canLoadMoreGenreMovies || latestState.isLoading || latestState.isLoadingMore || latestState.searchQuery.isNotBlank()) {
            return
        }

        val ids = currentContentIds(latestState)
        val index = ids.indexOf(selectedId)
        if (index >= 0 && index >= ids.lastIndex - 4) {
            viewModel.loadNextGenreMoviesPage()
        }
    }

    private fun currentContentIds(state: MovieUiState): List<Int> {
        return if (contentMode == ContentMode.TV_SHOWS) {
            state.tvShows.map { it.id }
        } else {
            state.movies.map { it.id }
        }
    }

    private fun openWatchOptions() {
        val movie = latestState.movies.firstOrNull { it.id == currentMediaId }
        val tvShow = latestState.tvShows.firstOrNull { it.id == currentMediaId }
        val mediaTitle = movie?.title ?: tvShow?.name
        if (mediaTitle == null) {
            Toast.makeText(requireContext(), "Focus a title first", Toast.LENGTH_SHORT).show()
            return
        }

        val isTvMode = contentMode == ContentMode.TV_SHOWS
        val dialog = WatchOptionsDialogFragment().apply {
            configure(
                viewModel = this@LeanbackPickerFragment.viewModel,
                movieId = movie?.id,
                tvShowId = tvShow?.id,
                title = mediaTitle,
                year = movie?.releaseDate?.take(4) ?: tvShow?.firstAirDate?.take(4),
                isTvMode = isTvMode,
                onTorrentSelected = { title, magnetUrl ->
                    val intent = Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_STREAMING)
                        putExtra(ComposeActivity.EXTRA_STREAMING_TITLE, title)
                        putExtra(ComposeActivity.EXTRA_STREAMING_MAGNET, magnetUrl)
                    }
                    startActivity(intent)
                },
                onTrailerSelected = { title, videoUrl ->
                    val intent = Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_TRAILER)
                        putExtra(ComposeActivity.EXTRA_TRAILER_TITLE, title)
                        putExtra(ComposeActivity.EXTRA_TRAILER_URL, videoUrl)
                    }
                    startActivity(intent)
                }
            )
        }
        dialog.show(childFragmentManager, "watch_options")
    }

    class WatchOptionsDialogFragment : DialogFragment() {

        private lateinit var vm: MovieViewModel
        private var mediaTitle: String = ""
        private var mediaYear: String? = null
        private var movieId: Int? = null
        private var tvShowId: Int? = null
        private var isTvMode: Boolean = false
        private var onTorrent: (String, String) -> Unit = { _, _ -> }
        private var onTrailer: (String, String) -> Unit = { _, _ -> }

        fun configure(
            viewModel: MovieViewModel,
            movieId: Int?,
            tvShowId: Int?,
            title: String,
            year: String?,
            isTvMode: Boolean = false,
            onTorrentSelected: (title: String, magnetUrl: String) -> Unit,
            onTrailerSelected: (title: String, videoUrl: String) -> Unit = { _, _ -> }
        ) {
            vm = viewModel
            this.movieId = movieId
            this.tvShowId = tvShowId
            mediaTitle = title
            mediaYear = year
            this.isTvMode = isTvMode
            onTorrent = onTorrentSelected
            onTrailer = onTrailerSelected
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return ComposeView(requireContext()).apply {
                setContent {
                    var options by mutableStateOf<List<WatchOption>>(emptyList())
                    var isLoading by mutableStateOf(true)
                    var trailerUrl by mutableStateOf<String?>(null)
                    var showEpisodePicker by mutableStateOf(false)
                    var availableSeasons by mutableStateOf<List<Int>>(emptyList())
                    var selectedSeason by mutableStateOf(1)
                    var resolvedImdbId by mutableStateOf<String?>(null)
                    var isLoadingSeasons by mutableStateOf(false)
                    val coroutineScope = rememberCoroutineScope()

                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        // Fetch trailer in parallel with watch options
                        launch {
                            trailerUrl = try {
                                vm.getTrailerUrlByTitle(mediaTitle, mediaYear, isTvMode)
                            } catch (_: Exception) { null }
                        }
                        options = try {
                            if (movieId != null) {
                                vm.getMovieWatchOptions(movieId!!, mediaTitle, mediaYear)
                            } else if (tvShowId != null) {
                                vm.getTvShowWatchOptions(tvShowId!!, mediaTitle, mediaYear)
                            } else {
                                emptyList()
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                        isLoading = false
                    }

                    MovieRecommenderTheme(darkTheme = true) {
                        WatchOptionsDialog(
                            title = mediaTitle,
                            options = options,
                            isLoading = isLoading || isLoadingSeasons,
                            onImportProviderLink = if (movieId != null || tvShowId != null) {
                                { option, rawContentIdOrUrl ->
                                    val providerId = option.providerId
                                    if (providerId == null) {
                                        null
                                    } else if (movieId != null) {
                                        val updated = vm.importMovieProviderLink(
                                            tmdbId = movieId!!,
                                            title = mediaTitle,
                                            year = mediaYear,
                                            providerId = providerId,
                                            rawContentIdOrUrl = rawContentIdOrUrl
                                        )
                                        if (updated != null) {
                                            options = updated
                                        }
                                        updated
                                    } else if (tvShowId != null) {
                                        val updated = vm.importTvShowProviderLink(
                                            tmdbId = tvShowId!!,
                                            title = mediaTitle,
                                            year = mediaYear,
                                            providerId = providerId,
                                            rawContentIdOrUrl = rawContentIdOrUrl
                                        )
                                        if (updated != null) {
                                            options = updated
                                        }
                                        updated
                                    } else {
                                        null
                                    }
                                }
                            } else null,
                            onDismiss = { dismissAllowingStateLoss() },
                            onWatchTrailer = trailerUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                {
                                    dismissAllowingStateLoss()
                                    onTrailer(mediaTitle, url)
                                }
                            },
                            onTorrentSelected = { magnetUrl ->
                                dismissAllowingStateLoss()
                                onTorrent(mediaTitle, magnetUrl)
                            },
                            onBrowseEpisodes = if (tvShowId != null) {
                                {
                                    coroutineScope.launch {
                                        isLoadingSeasons = true
                                        try {
                                            val imdbId = vm.getTvShowImdbId(tvShowId!!)
                                                ?: vm.resolveImdbIdByTitle(mediaTitle, mediaYear)
                                            if (imdbId != null) {
                                                val seasons = vm.getTvShowSeasons(imdbId)
                                                if (seasons.isNotEmpty()) {
                                                    resolvedImdbId = imdbId
                                                    availableSeasons = seasons
                                                    selectedSeason = seasons.first()
                                                    showEpisodePicker = true
                                                } else {
                                                    Toast.makeText(requireContext(), "No episodes available", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(requireContext(), "Show not found", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(requireContext(), "Error finding show", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoadingSeasons = false
                                        }
                                    }
                                }
                            } else null
                        )

                        if (showEpisodePicker && resolvedImdbId != null) {
                            EpisodePickerDialog(
                                showName = mediaTitle,
                                seasons = availableSeasons,
                                selectedSeason = selectedSeason,
                                onSeasonSelected = { selectedSeason = it },
                                onEpisodeSelected = { episode: Int ->
                                    showEpisodePicker = false
                                    coroutineScope.launch {
                                        try {
                                            val magnetUrl = vm.getTvEpisodeMagnetUrl(
                                                showTitle = mediaTitle,
                                                imdbId = resolvedImdbId,
                                                season = selectedSeason,
                                                episode = episode
                                            )
                                            if (magnetUrl != null) {
                                                dismissAllowingStateLoss()
                                                onTorrent("$mediaTitle S${selectedSeason}E${episode}", magnetUrl)
                                            } else {
                                                Toast.makeText(requireContext(), "No streaming source found", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(requireContext(), "Error finding source", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDismiss = { showEpisodePicker = false },
                                viewModel = vm,
                                preResolvedImdbId = resolvedImdbId
                            )
                        }
                    }
                }
            }
        }

        override fun onStart() {
            super.onStart()
            dialog?.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
    }
}