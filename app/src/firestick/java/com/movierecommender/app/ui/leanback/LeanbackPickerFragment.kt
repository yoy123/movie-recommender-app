package com.movierecommender.app.ui.leanback

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
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
import com.movierecommender.app.data.model.TvShow
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
        private const val ACTION_ANALYZE = "analyze"
        private const val ACTION_CLEAR = "clear"

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
        isHeadersTransitionOnBackEnabled = true
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
            Toast.makeText(requireContext(), "Search is not wired into the Leanback picker yet", Toast.LENGTH_SHORT).show()
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
            cachedContentIds = ids
            contentAdapter.clear()
            if (contentMode == ContentMode.TV_SHOWS) {
                state.tvShows.forEach(contentAdapter::add)
            } else {
                state.movies.forEach(contentAdapter::add)
            }
            rebuildRows()
        } else if (contentAdapter.size() > 0) {
            contentAdapter.notifyArrayItemRangeChanged(0, contentAdapter.size())
        }
    }

    private fun rebuildRows() {
        rowsAdapter.clear()
        if (actionAdapter.size() > 0) {
            rowsAdapter.add(ListRow(HeaderItem(HEADER_ACTIONS, "Actions"), actionAdapter))
        }
        if (contentAdapter.size() > 0) {
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
        }

        currentTvShow?.let { show ->
            val isSelected = state.selectedTvShows.any { it.id == show.id }
            rows += PickerActionItem(
                id = ACTION_TOGGLE_PICK,
                title = if (isSelected) "Remove Pick" else "Add Pick",
                description = show.name,
                accentColor = 0xFF0E7490.toInt()
            )
        }

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

            ACTION_ANALYZE -> launchRecommendationsIfReady()

            ACTION_CLEAR -> {
                if (contentMode == ContentMode.TV_SHOWS) viewModel.clearTvShowSelections()
                else viewModel.clearSelections()
            }
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
}