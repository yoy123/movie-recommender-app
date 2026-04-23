package com.movierecommender.app.ui.leanback

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
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
import com.movierecommender.app.MovieRecommenderApplication
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.data.model.Genre
import com.movierecommender.app.firestick.LeanbackPickerActivity
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModelFactory
import kotlinx.coroutines.launch

class BrowseGenreFragment : BrowseSupportFragment() {

    private lateinit var viewModel: MovieViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter

    // Persistent adapters for genre rows so we can update them without clearing everything
    private lateinit var movieGenreAdapter: ArrayObjectAdapter
    private lateinit var tvGenreAdapter: ArrayObjectAdapter

    // Row IDs
    companion object {
        private const val HEADER_MOVIE_GENRES = 0L
        private const val HEADER_TV_GENRES = 1L
        private const val HEADER_LIVE_TV = 2L
        private const val HEADER_FAVORITES = 3L
        private const val HEADER_SETTINGS = 4L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = requireActivity().application as MovieRecommenderApplication
        viewModel = ViewModelProvider(
            requireActivity(),
            MovieViewModelFactory(app.repository, app.settings)
        )[MovieViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()
        setupEventListeners()
        observeViewModel()
    }

    private fun setupUi() {
        title = "Movie Recommender"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = 0xFF1B1B2F.toInt() // Dark navy
        searchAffordanceColor = 0xFF00BCD4.toInt() // Cyan accent

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        // Row 1: Movie Genres (start with placeholder)
        movieGenreAdapter = ArrayObjectAdapter(GenreCardPresenter())
        movieGenreAdapter.add(Genre(id = Int.MIN_VALUE, name = "Loading…"))
        rowsAdapter.add(ListRow(HeaderItem(HEADER_MOVIE_GENRES, "Movie Genres"), movieGenreAdapter))

        // Row 2: TV Show Genres (start with placeholder)
        tvGenreAdapter = ArrayObjectAdapter(GenreCardPresenter())
        tvGenreAdapter.add(Genre(id = Int.MIN_VALUE, name = "Loading…"))
        rowsAdapter.add(ListRow(HeaderItem(HEADER_TV_GENRES, "TV Show Genres"), tvGenreAdapter))

        // Row 3: Live TV
        val liveTvAdapter = ArrayObjectAdapter(GenreCardPresenter())
        liveTvAdapter.add(Genre(id = -2, name = "Live TV"))
        rowsAdapter.add(ListRow(HeaderItem(HEADER_LIVE_TV, "Live TV"), liveTvAdapter))

        // Row 4: Favorites shortcut
        val favoritesAdapter = ArrayObjectAdapter(GenreCardPresenter())
        favoritesAdapter.add(Genre(id = -1, name = "My Favorites"))
        rowsAdapter.add(ListRow(HeaderItem(HEADER_FAVORITES, "Favorites"), favoritesAdapter))

        // Row 5: Settings
        val settingsAdapter = ArrayObjectAdapter(GenreCardPresenter())
        settingsAdapter.add(Genre(id = -3, name = "Settings"))
        rowsAdapter.add(ListRow(HeaderItem(HEADER_SETTINGS, "Settings"), settingsAdapter))

        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener {
                _: Presenter.ViewHolder?,
                item: Any?,
                _: RowPresenter.ViewHolder?,
                row: Row? ->

            if (item is Genre) {
                // Ignore placeholder "Loading…" cards
                if (item.id == Int.MIN_VALUE) return@OnItemViewClickedListener

                val headerItem = (row as? ListRow)?.headerItem

                // Live TV row → open Live TV
                if (headerItem?.id == HEADER_LIVE_TV) {
                    val intent = Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_LIVE_TV)
                    }
                    startActivity(intent)
                    return@OnItemViewClickedListener
                }

                // Settings row → open Settings
                if (headerItem?.id == HEADER_SETTINGS) {
                    val intent = Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_SETTINGS)
                    }
                    startActivity(intent)
                    return@OnItemViewClickedListener
                }

                val contentMode = when (headerItem?.id) {
                    HEADER_TV_GENRES -> ContentMode.TV_SHOWS
                    else -> ContentMode.MOVIES
                }

                // Favorites uses genreId = -1
                if (item.id == -1) {
                    val intent = Intent(requireContext(), ComposeActivity::class.java).apply {
                        putExtra(ComposeActivity.EXTRA_SCREEN, ComposeActivity.SCREEN_FAVORITES)
                        putExtra(ComposeActivity.EXTRA_GENRE_ID, -1)
                        putExtra(ComposeActivity.EXTRA_GENRE_NAME, item.name)
                        putExtra(ComposeActivity.EXTRA_CONTENT_MODE, contentMode.name)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(requireContext(), LeanbackPickerActivity::class.java).apply {
                        putExtra(LeanbackPickerActivity.EXTRA_GENRE_ID, item.id)
                        putExtra(LeanbackPickerActivity.EXTRA_GENRE_NAME, item.name)
                        putExtra(LeanbackPickerActivity.EXTRA_CONTENT_MODE, contentMode.name)
                    }
                    startActivity(intent)
                }
            }
        }

        setOnSearchClickedListener {
            // Could launch a SearchFragment or Compose search screen in the future
            android.widget.Toast.makeText(
                requireContext(), "Search coming soon", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateGenreRow(movieGenreAdapter, state.genres)
                    updateGenreRow(tvGenreAdapter, state.tvGenres)
                }
            }
        }

        // Trigger TV genre load (movie genres are loaded on ViewModel init)
        viewModel.setContentMode(ContentMode.MOVIES)
        viewModel.setContentMode(ContentMode.TV_SHOWS)
        // Reset back to movies default
        viewModel.setContentMode(ContentMode.MOVIES)
    }

    /**
     * Replace the contents of a genre row adapter only when the data actually changes.
     * Avoids clearing the whole rows adapter which resets Leanback focus state.
     */
    private fun updateGenreRow(adapter: ArrayObjectAdapter, genres: List<Genre>) {
        if (genres.isNotEmpty()) {
            // Check if already populated with the same data
            if (adapter.size() == genres.size && adapter.size() > 0) {
                val first = adapter.get(0) as? Genre
                if (first?.id == genres.firstOrNull()?.id) return
            }
            adapter.clear()
            genres.forEach { adapter.add(it) }
        }
        // If genres is empty, keep existing placeholder — don't overwrite
    }
}
