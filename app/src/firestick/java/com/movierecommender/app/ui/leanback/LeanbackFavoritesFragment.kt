package com.movierecommender.app.ui.leanback

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.Gson
import com.movierecommender.app.MovieRecommenderApplication
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.firestick.LeanbackPickerActivity
import com.movierecommender.app.ui.viewmodel.firestick.MovieUiState
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModelFactory
import kotlinx.coroutines.launch

/**
 * Favorites with the SAME native Leanback poster/action cards, horizontal ListRows,
 * and animated DPAD-focus border as the front-menu picker. No Compose dashboard.
 */
class LeanbackFavoritesFragment : BrowseSupportFragment() {

    companion object {
        private const val ROW_ACTIONS = 0L
        private const val ROW_SELECTED = 1L
        private const val ROW_MOVIES = 2L
        private const val ROW_TV = 3L
        private const val ROW_SEARCH = 4L

        private const val ACTION_CLEAR = "clear"
        private const val ACTION_RECOMMEND_MOVIES = "recommend_movies"
        private const val ACTION_RECOMMEND_TV = "recommend_tv"
        private const val ACTION_SEARCH_MOVIES = "search_movies"
        private const val ACTION_SEARCH_TV = "search_tv"
        private const val ACTION_SETTINGS = "settings"
        private const val ACTION_BACK = "back"
    }

    private lateinit var viewModel: MovieViewModel
    private lateinit var actionAdapter: ArrayObjectAdapter
    private lateinit var selectedAdapter: ArrayObjectAdapter
    private lateinit var movieAdapter: ArrayObjectAdapter
    private lateinit var tvAdapter: ArrayObjectAdapter
    private lateinit var searchAdapter: ArrayObjectAdapter
    private lateinit var rows: ArrayObjectAdapter

    private var latestState = MovieUiState()
    private var lastMovieIds: Set<Int> = emptySet()
    private var lastTvIds: Set<Int> = emptySet()
    private var lastFavoriteIds: Set<Int> = emptySet()
    private var searchMode = ContentMode.MOVIES
    private var activeSearch = ""
    private var renderedRowIds: List<Long> = emptyList()

    private val mediaDiff = object : DiffCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean = when {
            oldItem is Movie && newItem is Movie -> oldItem.id == newItem.id
            oldItem is TvShow && newItem is TvShow -> oldItem.id == newItem.id
            else -> false
        }
        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean = oldItem == newItem
    }
    private val actionDiff = object : DiffCallback<PickerActionItem>() {
        override fun areItemsTheSame(oldItem: PickerActionItem, newItem: PickerActionItem) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PickerActionItem, newItem: PickerActionItem) =
            oldItem == newItem
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = requireActivity().application as MovieRecommenderApplication
        viewModel = ViewModelProvider(
            requireActivity(), MovieViewModelFactory(app.repository, app.settings)
        )[MovieViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = "Favorites"
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = 0xFF1B1B2F.toInt()
        searchAffordanceColor = 0xFF00BCD4.toInt()

        val presenter = PickerMediaCardPresenter(
            isMovieSelected = { movie -> latestState.selectedMovies.any { it.id == movie.id } },
            isTvSelected = { show -> latestState.selectedTvShows.any { it.id == show.id } },
            isMovieFavorite = { movie -> latestState.favoriteMovies.any { it.id == movie.id } }
        )
        actionAdapter = ArrayObjectAdapter(PickerActionCardPresenter())
        // Leanback's RowsSupportFragment needs a non-empty initial row at the
        // moment it binds. If every child adapter is initially empty it can
        // render only the Browse header and never lay out the updated rows.
        actionAdapter.add(PickerActionItem(ACTION_BACK, "Back", "Return to front menu", 0xFF39495B.toInt()))
        selectedAdapter = ArrayObjectAdapter(presenter)
        movieAdapter = ArrayObjectAdapter(presenter)
        tvAdapter = ArrayObjectAdapter(presenter)
        searchAdapter = ArrayObjectAdapter(presenter)

        // Leanback does not lay out empty ListRows reliably when all card adapters
        // are empty during the first attach. Add rows after their content is ready,
        // and retain the existing row objects whenever the structure is unchanged.
        rows = ArrayObjectAdapter(ListRowPresenter())
        adapter = rows

        onItemViewClickedListener = OnItemViewClickedListener {
                _: Presenter.ViewHolder?, item: Any?, _: RowPresenter.ViewHolder?, row: Row? ->
            when (item) {
                is PickerActionItem -> handleAction(item.id)
                is Movie -> showMovieActions(item)
                is TvShow -> showTvActions(item)
            }
        }
        setOnSearchClickedListener { promptSearch(ContentMode.MOVIES) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestState = state
                    updateRows(state)
                }
            }
        }
    }

    private fun updateRows(state: MovieUiState) {
        val movieIds = state.selectedMovies.map { it.id }.toSet()
        val tvIds = state.selectedTvShows.map { it.id }.toSet()
        val favoriteIds = state.favoriteMovies.map { it.id }.toSet()

        title = "Favorites • " + (movieIds.size + tvIds.size) + " selected"
        val actions = listOf(
            PickerActionItem(ACTION_RECOMMEND_MOVIES, "Recommend Movies",
                if (movieIds.isEmpty()) "Select 1-5 movies" else movieIds.size.toString() + " movies ready",
                0xFF0F766E.toInt()),
            PickerActionItem(ACTION_RECOMMEND_TV, "Recommend TV",
                if (tvIds.isEmpty()) "Select 1-5 shows" else tvIds.size.toString() + " shows ready",
                0xFF0F766E.toInt()),
            PickerActionItem(ACTION_CLEAR, "Clear Selections",
                if (movieIds.isEmpty() && tvIds.isEmpty()) "Nothing selected" else "Remove picks; keep favorites",
                0xFF7C2D12.toInt()),
            PickerActionItem(ACTION_SEARCH_MOVIES, "Search Movies", "Find movie titles", 0xFF0369A1.toInt()),
            PickerActionItem(ACTION_SEARCH_TV, "Search TV Shows", "Find series", 0xFF0369A1.toInt()),
            PickerActionItem(ACTION_SETTINGS, "Settings", "Recommendation preferences", 0xFF6D28D9.toInt()),
            PickerActionItem(ACTION_BACK, "Back", "Return to front menu", 0xFF39495B.toInt())
        )
        actionAdapter.setItems(actions, actionDiff)

        val selectedItems: List<Any> = state.selectedMovies + state.selectedTvShows
        selectedAdapter.setItems(selectedItems, mediaDiff)
        movieAdapter.setItems(state.favoriteMovies, mediaDiff)
        tvAdapter.setItems(state.favoriteTvShows, mediaDiff)

        val searchItems: List<Any> = if (activeSearch.isBlank()) emptyList() else {
            if (searchMode == ContentMode.MOVIES) state.movies else state.tvShows
        }
        searchAdapter.setItems(searchItems, mediaDiff)
        syncRowStructure()

        if (movieIds != lastMovieIds || tvIds != lastTvIds || favoriteIds != lastFavoriteIds) {
            for (cards in listOf(selectedAdapter, movieAdapter, tvAdapter, searchAdapter)) {
                if (cards.size() > 0) cards.notifyArrayItemRangeChanged(0, cards.size())
            }
            lastMovieIds = movieIds
            lastTvIds = tvIds
            lastFavoriteIds = favoriteIds
        }
    }

    private fun syncRowStructure() {
        val wanted = buildList {
            if (actionAdapter.size() > 0) add(ROW_ACTIONS)
            if (selectedAdapter.size() > 0) add(ROW_SELECTED)
            if (movieAdapter.size() > 0) add(ROW_MOVIES)
            if (tvAdapter.size() > 0) add(ROW_TV)
            if (searchAdapter.size() > 0) add(ROW_SEARCH)
        }
        if (wanted == renderedRowIds) return
        val oldSelection = getSelectedPosition()
        val previouslyFocusedId = renderedRowIds.getOrNull(oldSelection)
        rows.clear()
        for (id in wanted) {
            val (label, cards) = when (id) {
                ROW_ACTIONS -> "Actions" to actionAdapter
                ROW_SELECTED -> "Selected for Recommendations" to selectedAdapter
                ROW_MOVIES -> "Movie Favorites" to movieAdapter
                ROW_TV -> "TV Show Favorites" to tvAdapter
                else -> "Search Results" to searchAdapter
            }
            rows.add(ListRow(HeaderItem(id, label), cards))
        }
        renderedRowIds = wanted
        if (wanted.isNotEmpty()) {
            val nextPosition = wanted.indexOf(previouslyFocusedId).takeIf { it >= 0 }
                ?: oldSelection.coerceIn(0, wanted.lastIndex)
            setSelectedPosition(nextPosition)
        }
    }

    private fun handleAction(id: String) {
        when (id) {
            ACTION_RECOMMEND_MOVIES -> launchRecommendations(ContentMode.MOVIES)
            ACTION_RECOMMEND_TV -> launchRecommendations(ContentMode.TV_SHOWS)
            ACTION_CLEAR -> viewModel.clearSelections()
            ACTION_SEARCH_MOVIES -> promptSearch(ContentMode.MOVIES)
            ACTION_SEARCH_TV -> promptSearch(ContentMode.TV_SHOWS)
            ACTION_SETTINGS -> startActivity(Intent(requireContext(), ComposeActivity::class.java).apply {
                putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_SETTINGS)
            })
            ACTION_BACK -> requireActivity().finish()
        }
    }

    private fun launchRecommendations(mode: ContentMode) {
        val amount = if (mode == ContentMode.MOVIES) latestState.selectedMovies.size else latestState.selectedTvShows.size
        if (amount == 0) {
            Toast.makeText(requireContext(), "Select at least one title first", Toast.LENGTH_SHORT).show()
            return
        }
        val destination = Intent(requireContext(), ComposeActivity::class.java).apply {
            putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_RECOMMENDATIONS)
            putExtra(ComposeActivity.EXTRA_GENRE_ID, -1)
            putExtra(ComposeActivity.EXTRA_GENRE_NAME, "Favorites")
            putExtra(ComposeActivity.EXTRA_CONTENT_MODE, mode.name)
            if (mode == ContentMode.TV_SHOWS) {
                putExtra(ComposeActivity.EXTRA_SELECTED_TV_SHOWS_JSON, Gson().toJson(latestState.selectedTvShows))
            }
        }
        startActivity(destination)
    }

    private fun showMovieActions(movie: Movie) {
        val favorite = latestState.favoriteMovies.any { it.id == movie.id }
        val selected = latestState.selectedMovies.any { it.id == movie.id }
        val choices = mutableListOf<String>()
        choices.add("Watch Options")
        choices.add(if (selected) "Remove from Recommendations" else "Select for Recommendations")
        choices.add(if (favorite) "Delete from Favorites" else "Add to Favorites")
        AlertDialog.Builder(requireContext())
            .setTitle(movie.title)
            .setItems(choices.toTypedArray()) { _, index ->
                when (index) {
                    0 -> openWatchOptions(movie, null)
                    1 -> {
                        if (!selected && latestState.selectedMovies.size >= 5) {
                            Toast.makeText(requireContext(), "Maximum of 5 movie picks", Toast.LENGTH_SHORT).show()
                        } else viewModel.toggleMovieSelection(movie)
                    }
                    2 -> if (favorite) viewModel.removeFromFavorites(movie.id) else viewModel.addToFavorites(movie)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTvActions(show: TvShow) {
        val favorite = latestState.favoriteTvShows.any { it.id == show.id }
        val selected = latestState.selectedTvShows.any { it.id == show.id }
        val choices = arrayOf(
            "Watch Options",
            if (selected) "Remove from Recommendations" else "Select for Recommendations",
            if (favorite) "Delete from Favorites" else "Add to Favorites"
        )
        AlertDialog.Builder(requireContext())
            .setTitle(show.name)
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> openWatchOptions(null, show)
                    1 -> {
                        if (!selected && latestState.selectedTvShows.size >= 5) {
                            Toast.makeText(requireContext(), "Maximum of 5 TV picks", Toast.LENGTH_SHORT).show()
                        } else viewModel.toggleTvShowSelection(show)
                    }
                    2 -> if (favorite) viewModel.removeTvShowFromFavorites(show.id) else viewModel.addTvShowToFavorites(show)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openWatchOptions(movie: Movie?, show: TvShow?) {
        val name = movie?.title ?: show?.name ?: return
        val dialog = LeanbackPickerFragment.WatchOptionsDialogFragment().apply {
            configure(
                viewModel = this@LeanbackFavoritesFragment.viewModel,
                movieId = movie?.id,
                tvShowId = show?.id,
                title = name,
                year = movie?.releaseDate?.take(4) ?: show?.firstAirDate?.take(4),
                isTvMode = show != null,
                onTorrentSelected = { title, magnet ->
                    startActivity(Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_STREAMING)
                        putExtra(ComposeActivity.EXTRA_STREAMING_TITLE, title)
                        putExtra(ComposeActivity.EXTRA_STREAMING_MAGNET, magnet)
                    })
                },
                onTrailerSelected = { title, url ->
                    startActivity(Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_TRAILER)
                        putExtra(ComposeActivity.EXTRA_TRAILER_TITLE, title)
                        putExtra(ComposeActivity.EXTRA_TRAILER_URL, url)
                    })
                }
            )
        }
        dialog.show(childFragmentManager, "favorite_watch_options")
    }

    private fun promptSearch(mode: ContentMode) {
        val field = EditText(requireContext()).apply {
            setSingleLine()
            hint = if (mode == ContentMode.TV_SHOWS) "Search TV shows" else "Search movies"
            setText(if (mode == searchMode) activeSearch else "")
            selectAll()
        }
        val padding = (resources.displayMetrics.density * 16).toInt()
        val wrapper = FrameLayout(requireContext()).apply {
            setPadding(padding, 0, padding, 0)
            addView(field)
        }
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (mode == ContentMode.TV_SHOWS) "Search TV Shows" else "Search Movies")
            .setView(wrapper)
            .setPositiveButton("Search") { _, _ ->
                activeSearch = field.text.toString().trim()
                searchMode = mode
                viewModel.setContentMode(mode)
                viewModel.searchContent(activeSearch)
            }
            .setNegativeButton("Cancel", null)
        if (activeSearch.isNotBlank()) {
            builder.setNeutralButton("Clear") { _, _ ->
                activeSearch = ""
                viewModel.setContentMode(mode)
                viewModel.searchContent("")
                searchAdapter.clear()
            }
        }
        builder.show().also { dialog ->
            field.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }
}
