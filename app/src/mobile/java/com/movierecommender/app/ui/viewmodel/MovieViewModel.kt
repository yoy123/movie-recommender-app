package com.movierecommender.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.movierecommender.app.data.model.Genre
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.data.model.WatchOption
import com.movierecommender.app.data.repository.MovieRepository
import com.movierecommender.app.data.settings.SettingsRepository
import com.movierecommender.app.data.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MovieUiState(
    val genres: List<Genre> = emptyList(),
    val tvGenres: List<Genre> = emptyList(), // TV show genres
    val movies: List<Movie> = emptyList(),
    val tvShows: List<TvShow> = emptyList(), // TV shows for current genre
    val selectedMovies: List<Movie> = emptyList(),
    val selectedTvShows: List<TvShow> = emptyList(), // Selected TV shows for recommendations
    val recommendedMovies: List<Movie> = emptyList(),
    val recommendedTvShows: List<TvShow> = emptyList(), // Recommended TV shows
    val favoriteMovies: List<Movie> = emptyList(),
    val recommendationText: String? = null, // LLM text response (TMDB fallback on failure)
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedGenreId: Int? = null,
    val selectedGenreName: String? = null,
    val searchQuery: String = "",
    val isFavoritesMode: Boolean = false,
    val contentMode: ContentMode = ContentMode.MOVIES, // Movies or TV Shows mode
    // Paging for genre discovery (infinite scroll)
    val genrePage: Int = 1,
    val genreTotalPages: Int = 1,
    val canLoadMoreGenreMovies: Boolean = false,
    val isLoadingMore: Boolean = false,
    // Recommendation preferences (ordered as per user spec)
    val indiePreference: Float = 0.5f, // 0.0 = blockbusters, 1.0 = indie films
    val useIndiePreference: Boolean = true,
    val popularityPreference: Float = 0.5f, // 0.0 = cult classics, 1.0 = mainstream
    val usePopularityPreference: Boolean = true,
    val releaseYearStart: Float = 1950f, // Earliest year (1950-current)
    val releaseYearEnd: Float = 2025f, // Latest year (1950-current)
    val useReleaseYearPreference: Boolean = true,
    val tonePreference: Float = 0.5f, // 0.0 = light/uplifting, 1.0 = dark/serious
    val useTonePreference: Boolean = true,
    val internationalPreference: Float = 0.5f, // 0.0 = domestic, 1.0 = international
    val useInternationalPreference: Boolean = true,
    val experimentalPreference: Float = 0.5f, // 0.0 = traditional, 1.0 = experimental
    val useExperimentalPreference: Boolean = true,
    val userName: String = "", // User's name for personalized favorites
    val isFirstRun: Boolean = true, // Show welcome dialog on first run
    val isDarkMode: Boolean = true, // Dark mode preference (default: dark)
    // LLM consent for GDPR/CCPA compliance
    val llmConsentGiven: Boolean = false, // User consented to send data to OpenAI
    val llmConsentAsked: Boolean = false, // Consent dialog has been shown
    val showLlmConsentDialog: Boolean = false // Show consent dialog now
)

class MovieViewModel(
    private val repository: MovieRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MovieUiState())
    val uiState: StateFlow<MovieUiState> = _uiState.asStateFlow()

    // Tracks titles recommended during this ViewModel session so user-triggered retries never return the same list again.
    private val sessionRecommendedTitles = mutableSetOf<String>()
    
    init {
        loadGenres()
        observeSelectedMovies()
        observeRecommendedMovies()
        observeFavoriteMovies()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.userName.collect { _uiState.value = _uiState.value.copy(userName = it) }
        }
        viewModelScope.launch {
            settings.isFirstRun.collect { _uiState.value = _uiState.value.copy(isFirstRun = it) }
        }
        viewModelScope.launch {
            settings.darkMode.collect { _uiState.value = _uiState.value.copy(isDarkMode = it) }
        }
        viewModelScope.launch {
            settings.indiePreference.collect { _uiState.value = _uiState.value.copy(indiePreference = it) }
        }
        viewModelScope.launch {
            settings.useIndie.collect { _uiState.value = _uiState.value.copy(useIndiePreference = it) }
        }
        viewModelScope.launch {
            settings.popularityPreference.collect { _uiState.value = _uiState.value.copy(popularityPreference = it) }
        }
        viewModelScope.launch {
            settings.usePopularity.collect { _uiState.value = _uiState.value.copy(usePopularityPreference = it) }
        }
        viewModelScope.launch {
            settings.releaseYearStart.collect { _uiState.value = _uiState.value.copy(releaseYearStart = it) }
        }
        viewModelScope.launch {
            settings.releaseYearEnd.collect { _uiState.value = _uiState.value.copy(releaseYearEnd = it) }
        }
        viewModelScope.launch {
            settings.useReleaseYear.collect { _uiState.value = _uiState.value.copy(useReleaseYearPreference = it) }
        }
        viewModelScope.launch {
            settings.tonePreference.collect { _uiState.value = _uiState.value.copy(tonePreference = it) }
        }
        viewModelScope.launch {
            settings.useTone.collect { _uiState.value = _uiState.value.copy(useTonePreference = it) }
        }
        viewModelScope.launch {
            settings.internationalPreference.collect { _uiState.value = _uiState.value.copy(internationalPreference = it) }
        }
        viewModelScope.launch {
            settings.useInternational.collect { _uiState.value = _uiState.value.copy(useInternationalPreference = it) }
        }
        viewModelScope.launch {
            settings.experimentalPreference.collect { _uiState.value = _uiState.value.copy(experimentalPreference = it) }
        }
        viewModelScope.launch {
            settings.useExperimental.collect { _uiState.value = _uiState.value.copy(useExperimentalPreference = it) }
        }
        // LLM consent observers
        viewModelScope.launch {
            settings.llmConsentGiven.collect { _uiState.value = _uiState.value.copy(llmConsentGiven = it) }
        }
        viewModelScope.launch {
            settings.llmConsentAsked.collect { _uiState.value = _uiState.value.copy(llmConsentAsked = it) }
        }
    }
    
    private fun observeSelectedMovies() {
        viewModelScope.launch {
            repository.getSelectedMovies().collect { movies ->
                _uiState.value = _uiState.value.copy(selectedMovies = movies)
            }
        }
    }
    
    private fun observeRecommendedMovies() {
        viewModelScope.launch {
            repository.getRecommendedMovies().collect { movies ->
                _uiState.value = _uiState.value.copy(recommendedMovies = movies)
            }
        }
    }
    
    private fun observeFavoriteMovies() {
        viewModelScope.launch {
            repository.getFavoriteMovies().collect { favoriteMovies ->
                // Update favorites list and sync with current movies
                val favoriteIds = favoriteMovies.map { it.id }.toSet()
                val updatedMovies = _uiState.value.movies.map { movie ->
                    movie.copy(isFavorite = favoriteIds.contains(movie.id))
                }
                _uiState.value = _uiState.value.copy(
                    favoriteMovies = favoriteMovies,
                    movies = updatedMovies
                )
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // LLM Consent (GDPR/CCPA compliance)
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Show the LLM consent dialog if user hasn't been asked yet.
     * Call this before first LLM recommendation generation.
     */
    fun checkAndShowLlmConsentIfNeeded() {
        val state = _uiState.value
        if (!state.llmConsentAsked) {
            _uiState.value = state.copy(showLlmConsentDialog = true)
        }
    }
    
    /**
     * User responded to LLM consent dialog.
     * @param consented true if user accepts AI recommendations, false if declined
     */
    fun onLlmConsentResponse(consented: Boolean) {
        _uiState.value = _uiState.value.copy(showLlmConsentDialog = false)
        viewModelScope.launch {
            settings.setLlmConsent(consented)
        }
    }
    
    /**
     * Dismiss consent dialog without recording a decision (user tapped outside).
     * They will be asked again next time.
     */
    fun dismissLlmConsentDialog() {
        _uiState.value = _uiState.value.copy(showLlmConsentDialog = false)
    }
    
    fun loadGenres() {
        viewModelScope.launch {
            repository.getGenres().collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            genres = resource.data,
                            isLoading = false,
                            error = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }
    
    fun loadTvGenres() {
        viewModelScope.launch {
            repository.getTvGenres().collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            tvGenres = resource.data,
                            isLoading = false,
                            error = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Switch between Movies and TV Shows content modes.
     */
    fun setContentMode(mode: ContentMode) {
        _uiState.value = _uiState.value.copy(contentMode = mode)
        when (mode) {
            ContentMode.MOVIES -> {
                if (_uiState.value.genres.isEmpty()) loadGenres()
            }
            ContentMode.TV_SHOWS -> {
                if (_uiState.value.tvGenres.isEmpty()) loadTvGenres()
            }
        }
    }
    
    fun selectGenre(genreId: Int, genreName: String) {
        // Check if this is the special Dee's Favorites genre
        val isFavorites = genreId == -1
        
        _uiState.value = _uiState.value.copy(
            selectedGenreId = genreId,
            selectedGenreName = genreName,
            isFavoritesMode = isFavorites,
            searchQuery = ""
        )
        
        if (!isFavorites) {
            // Load content based on current mode
            when (_uiState.value.contentMode) {
                ContentMode.MOVIES -> loadMoviesByGenre(genreId = genreId, reset = true)
                ContentMode.TV_SHOWS -> loadTvShowsByGenre(genreId = genreId, reset = true)
            }
        } else {
            // For favorites, we'll show search and favorites list
            _uiState.value = _uiState.value.copy(movies = emptyList(), tvShows = emptyList())
        }
    }
    
    fun loadMoviesByGenre(genreId: Int) {
        loadMoviesByGenre(genreId = genreId, reset = true)
    }

    fun loadMoviesByGenre(genreId: Int, reset: Boolean = false, pageOverride: Int? = null) {
        viewModelScope.launch {
            val page = pageOverride ?: if (reset) 1 else _uiState.value.genrePage
            if (reset) {
                _uiState.value = _uiState.value.copy(
                    movies = emptyList(),
                    error = null,
                    isLoadingMore = false,
                    genrePage = 1,
                    genreTotalPages = 1,
                    canLoadMoreGenreMovies = false
                )
            }

            repository.getMoviesByGenreResponse(genreId = genreId, page = page).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = if (page == 1) {
                            _uiState.value.copy(isLoading = true, error = null)
                        } else {
                            _uiState.value.copy(isLoadingMore = true, error = null)
                        }
                    }
                    is Resource.Success -> {
                        // Sync isFavorite flag with favorites list
                        val favoriteIds = _uiState.value.favoriteMovies.map { it.id }.toSet()
                        val moviesWithFavorites = resource.data.results.map { movie ->
                            movie.copy(isFavorite = favoriteIds.contains(movie.id))
                        }

                        val merged = if (page == 1) {
                            moviesWithFavorites
                        } else {
                            (_uiState.value.movies + moviesWithFavorites)
                                .distinctBy { it.id }
                        }

                        val totalPages = resource.data.totalPages.coerceAtLeast(1)
                        val canLoadMore = page < totalPages

                        _uiState.value = _uiState.value.copy(
                            movies = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                            genrePage = page,
                            genreTotalPages = totalPages,
                            canLoadMoreGenreMovies = canLoadMore
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }

    fun loadNextGenreMoviesPage() {
        val genreId = _uiState.value.selectedGenreId ?: return
        if (_uiState.value.isFavoritesMode) return
        if (_uiState.value.searchQuery.isNotBlank()) return
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) return
        if (!_uiState.value.canLoadMoreGenreMovies) return

        val nextPage = (_uiState.value.genrePage + 1).coerceAtMost(_uiState.value.genreTotalPages)
        // Avoid accidental re-entrancy.
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        when (_uiState.value.contentMode) {
            ContentMode.MOVIES -> loadMoviesByGenre(genreId = genreId, reset = false, pageOverride = nextPage)
            ContentMode.TV_SHOWS -> loadTvShowsByGenre(genreId = genreId, reset = false, pageOverride = nextPage)
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // TV Shows Loading & Selection
    // ─────────────────────────────────────────────────────────────────────────────
    
    fun loadTvShowsByGenre(genreId: Int, reset: Boolean = false, pageOverride: Int? = null) {
        viewModelScope.launch {
            val page = pageOverride ?: if (reset) 1 else _uiState.value.genrePage
            if (reset) {
                _uiState.value = _uiState.value.copy(
                    tvShows = emptyList(),
                    error = null,
                    isLoadingMore = false,
                    genrePage = 1,
                    genreTotalPages = 1,
                    canLoadMoreGenreMovies = false
                )
            }

            repository.getTvShowsByGenreResponse(genreId = genreId, page = page).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = if (page == 1) {
                            _uiState.value.copy(isLoading = true, error = null)
                        } else {
                            _uiState.value.copy(isLoadingMore = true, error = null)
                        }
                    }
                    is Resource.Success -> {
                        val merged = if (page == 1) {
                            resource.data.results
                        } else {
                            (_uiState.value.tvShows + resource.data.results).distinctBy { it.id }
                        }

                        val totalPages = resource.data.totalPages.coerceAtLeast(1)
                        val canLoadMore = page < totalPages

                        _uiState.value = _uiState.value.copy(
                            tvShows = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                            genrePage = page,
                            genreTotalPages = totalPages,
                            canLoadMoreGenreMovies = canLoadMore
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }
    
    fun searchTvShows(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value.selectedGenreId?.let { loadTvShowsByGenre(genreId = it, reset = true) }
            return
        }

        _uiState.value = _uiState.value.copy(canLoadMoreGenreMovies = false, isLoadingMore = false)
        
        viewModelScope.launch {
            repository.searchTvShows(query).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            tvShows = resource.data,
                            isLoading = false,
                            error = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }
    
    fun toggleTvShowSelection(tvShow: TvShow) {
        val isCurrentlySelected = _uiState.value.selectedTvShows.any { it.id == tvShow.id }
        if (isCurrentlySelected) {
            _uiState.value = _uiState.value.copy(
                selectedTvShows = _uiState.value.selectedTvShows.filter { it.id != tvShow.id }
            )
        } else {
            // Allow up to 5 TV shows
            if (_uiState.value.selectedTvShows.size < 5) {
                _uiState.value = _uiState.value.copy(
                    selectedTvShows = _uiState.value.selectedTvShows + tvShow
                )
            }
        }
    }
    
    fun clearTvShowSelections() {
        _uiState.value = _uiState.value.copy(selectedTvShows = emptyList())
    }
    
    /**
     * Generate TV show recommendations using LLM + user preference settings.
     * Mirrors the movie recommendation pipeline: TMDB candidates → LLM rerank → validated output.
     */
    fun generateTvRecommendations(additionalExcludedTitles: List<String> = emptyList()) {
        val selectedShows = _uiState.value.selectedTvShows
        if (selectedShows.isEmpty() || selectedShows.size > 5) return
        
        val genreName = _uiState.value.selectedGenreName ?: "TV Shows"
        val genreId = _uiState.value.selectedGenreId
        val state = _uiState.value
        
        viewModelScope.launch {
            repository.getTvShowRecommendationsLlm(
                selectedShows = selectedShows,
                genreName = genreName,
                genreId = genreId,
                indiePreference = state.indiePreference,
                useIndiePreference = state.useIndiePreference,
                popularityPreference = state.popularityPreference,
                usePopularityPreference = state.usePopularityPreference,
                releaseYearStart = state.releaseYearStart,
                releaseYearEnd = state.releaseYearEnd,
                useReleaseYearPreference = state.useReleaseYearPreference,
                tonePreference = state.tonePreference,
                useTonePreference = state.useTonePreference,
                internationalPreference = state.internationalPreference,
                useInternationalPreference = state.useInternationalPreference,
                experimentalPreference = state.experimentalPreference,
                useExperimentalPreference = state.useExperimentalPreference,
                additionalExcludedTitles = (additionalExcludedTitles + sessionRecommendedTitles.toList()).distinct(),
                useLlm = state.llmConsentGiven
            ).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            error = null,
                            recommendedTvShows = emptyList(),
                            recommendationText = null
                        )
                    }
                    is Resource.Success -> {
                        sessionRecommendedTitles.addAll(extractRecommendedTitlesFromText(resource.data))
                        _uiState.value = _uiState.value.copy(
                            recommendationText = resource.data,
                            isLoading = false,
                            error = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }
    
    fun searchContent(query: String) {
        when (_uiState.value.contentMode) {
            ContentMode.MOVIES -> searchMovies(query)
            ContentMode.TV_SHOWS -> searchTvShows(query)
        }
    }
    
    fun searchMovies(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value.selectedGenreId?.let { loadMoviesByGenre(genreId = it, reset = true) }
            return
        }

        // While searching, disable genre paging.
        _uiState.value = _uiState.value.copy(canLoadMoreGenreMovies = false, isLoadingMore = false)
        
        viewModelScope.launch {
            repository.searchMovies(query).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is Resource.Success -> {
                        // Sync isFavorite flag with favorites list
                        val favoriteIds = _uiState.value.favoriteMovies.map { it.id }.toSet()
                        val moviesWithFavorites = resource.data.map { movie ->
                            movie.copy(isFavorite = favoriteIds.contains(movie.id))
                        }
                        _uiState.value = _uiState.value.copy(
                            movies = moviesWithFavorites,
                            isLoading = false,
                            error = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }
    
    fun toggleMovieSelection(movie: Movie) {
        viewModelScope.launch {
            val isCurrentlySelected = _uiState.value.selectedMovies.any { it.id == movie.id }
            if (isCurrentlySelected) {
                repository.removeSelectedMovie(movie)
            } else {
                // Allow up to 5 movies
                if (_uiState.value.selectedMovies.size < 5) {
                    repository.saveSelectedMovie(movie)
                }
            }
        }
    }
    
    private fun extractRecommendedTitlesFromText(recommendationText: String): List<String> {
        // Extract numbered titles like: "1. Movie Title (Year)". Keep the year if present.
        val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
        return recommendationText
            .replace("```json", "")
            .replace("```", "")
            .replace("**", "")
            .lines()
            .mapNotNull { line ->
                if (!numbered.containsMatchIn(line)) return@mapNotNull null
                line.replace(numbered, "").trim().takeIf { it.isNotBlank() }
            }
            .distinct()
    }

    fun retryRecommendations() {
        val current = _uiState.value.recommendationText ?: return
        val additionalExcluded = extractRecommendedTitlesFromText(current)
        generateRecommendations(additionalExcludedTitles = additionalExcluded)
    }
    
    fun retryTvRecommendations() {
        val current = _uiState.value.recommendationText ?: return
        val additionalExcluded = extractRecommendedTitlesFromText(current)
        _uiState.value = _uiState.value.copy(
            recommendedTvShows = emptyList(),
            recommendationText = null
        )
        generateTvRecommendations(additionalExcludedTitles = additionalExcluded)
    }

    fun generateRecommendations(additionalExcludedTitles: List<String> = emptyList()) {
        val selectedMovies = _uiState.value.selectedMovies
        val genreName = _uiState.value.selectedGenreName ?: "Movies"
        val genreId = _uiState.value.selectedGenreId
        val isFavoritesMode = _uiState.value.isFavoritesMode
        val state = _uiState.value
        
        // Allow 1-5 movies
        if (selectedMovies.isEmpty() || selectedMovies.size > 5) return
        
        viewModelScope.launch {
            repository.getRecommendations(
                selectedMovies, 
                genreName, 
                genreId = genreId,
                isFavoritesMode = isFavoritesMode,
                state.indiePreference,
                state.useIndiePreference,
                state.popularityPreference,
                state.usePopularityPreference,
                state.releaseYearStart,
                state.releaseYearEnd,
                state.useReleaseYearPreference,
                state.tonePreference,
                state.useTonePreference,
                state.internationalPreference,
                state.useInternationalPreference,
                state.experimentalPreference,
                state.useExperimentalPreference,
                additionalExcludedTitles = (additionalExcludedTitles + sessionRecommendedTitles.toList()).distinct(),
                useLlm = state.llmConsentGiven // Only use LLM if user has consented
            ).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true, 
                            error = null,
                            recommendationText = null
                        )
                    }
                    is Resource.Success -> {
                        // Remember these titles so a subsequent retry can't return the same list.
                        sessionRecommendedTitles.addAll(extractRecommendedTitlesFromText(resource.data))
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            recommendationText = resource.data
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message,
                            recommendationText = null
                        )
                    }
                }
            }
        }
    }
    
    fun clearSelections() {
        viewModelScope.launch {
            repository.clearSelectedMovies()
            repository.clearRecommendedMovies()
            sessionRecommendedTitles.clear()
            _uiState.value = _uiState.value.copy(
                recommendationText = null,
                selectedTvShows = emptyList(),
                recommendedTvShows = emptyList()
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun addToFavorites(movie: Movie) {
        viewModelScope.launch {
            repository.addToFavorites(movie)
        }
    }
    
    fun removeFromFavorites(movieId: Int) {
        viewModelScope.launch {
            repository.removeFromFavorites(movieId)
        }
    }
    
    fun updateIndiePreference(preference: Float) {
        _uiState.value = _uiState.value.copy(indiePreference = preference)
        viewModelScope.launch { settings.setIndiePreference(preference) }
    }
    
    fun updateUseIndiePreference(use: Boolean) {
        _uiState.value = _uiState.value.copy(useIndiePreference = use)
        viewModelScope.launch { settings.setUseIndie(use) }
    }
    
    fun updatePopularityPreference(preference: Float) {
        _uiState.value = _uiState.value.copy(popularityPreference = preference)
        viewModelScope.launch { settings.setPopularityPreference(preference) }
    }
    
    fun updateUsePopularityPreference(use: Boolean) {
        _uiState.value = _uiState.value.copy(usePopularityPreference = use)
        viewModelScope.launch { settings.setUsePopularity(use) }
    }
    
    fun updateReleaseYearStart(year: Float) {
        _uiState.value = _uiState.value.copy(releaseYearStart = year)
        viewModelScope.launch { settings.setReleaseYearStart(year) }
    }
    
    fun updateReleaseYearEnd(year: Float) {
        _uiState.value = _uiState.value.copy(releaseYearEnd = year)
        viewModelScope.launch { settings.setReleaseYearEnd(year) }
    }
    
    fun updateUseReleaseYearPreference(use: Boolean) {
        _uiState.value = _uiState.value.copy(useReleaseYearPreference = use)
        viewModelScope.launch { settings.setUseReleaseYear(use) }
    }
    
    fun updateTonePreference(preference: Float) {
        _uiState.value = _uiState.value.copy(tonePreference = preference)
        viewModelScope.launch { settings.setTonePreference(preference) }
    }
    
    fun updateUseTonePreference(use: Boolean) {
        _uiState.value = _uiState.value.copy(useTonePreference = use)
        viewModelScope.launch { settings.setUseTone(use) }
    }
    
    fun updateInternationalPreference(preference: Float) {
        _uiState.value = _uiState.value.copy(internationalPreference = preference)
        viewModelScope.launch { settings.setInternationalPreference(preference) }
    }
    
    fun updateUseInternationalPreference(use: Boolean) {
        _uiState.value = _uiState.value.copy(useInternationalPreference = use)
        viewModelScope.launch { settings.setUseInternational(use) }
    }
    
    fun updateExperimentalPreference(preference: Float) {
        _uiState.value = _uiState.value.copy(experimentalPreference = preference)
        viewModelScope.launch { settings.setExperimentalPreference(preference) }
    }
    
    fun updateUseExperimentalPreference(use: Boolean) {
        _uiState.value = _uiState.value.copy(useExperimentalPreference = use)
        viewModelScope.launch { settings.setUseExperimental(use) }
    }
    
    fun updateUserName(name: String) {
        val trimmed = name.trim()
        _uiState.value = _uiState.value.copy(userName = trimmed)
        viewModelScope.launch { settings.setUserName(trimmed) }
    }
    
    fun completeFirstRun() {
        _uiState.value = _uiState.value.copy(isFirstRun = false)
        viewModelScope.launch { settings.setFirstRunDone() }
    }
    
    fun updateDarkMode(isDark: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = isDark)
        viewModelScope.launch { settings.setDarkMode(isDark) }
    }

    // IMDb rating helper
    suspend fun getImdbRating(title: String, year: String?): String? {
        return repository.getImdbRating(title, year)
    }

    suspend fun getImdbTrailerUrl(movieId: Int): String? {
        return repository.getImdbTrailerUrl(movieId)
    }

    suspend fun getImdbTrailerUrlByTitle(title: String, year: String?): String? {
        return repository.getImdbTrailerUrlByTitle(title, year)
    }

    /**
     * Get trailer URL for a TV show by title.
     * Uses TMDB TV search + Videos API for YouTube trailers, with IMDB scraping fallback.
     */
    suspend fun getTvShowTrailerUrlByTitle(title: String, year: String?): String? {
        return repository.getTvShowTrailerUrlByTitle(title, year)
    }

    /**
     * Get trailer URL based on current content mode.
     * Routes to movie or TV show trailer fetching as appropriate.
     */
    suspend fun getTrailerUrlByTitle(title: String, year: String?, isTvMode: Boolean): String? {
        return if (isTvMode) {
            getTvShowTrailerUrlByTitle(title, year)
        } else {
            getImdbTrailerUrlByTitle(title, year)
        }
    }

    suspend fun getTmdbRatingByTitleYear(title: String, year: String?): String? {
        return repository.getTmdbRatingByTitleYear(title, year)
    }

    /**
     * Get all watch options (streaming providers + torrent) for a movie.
     */
    suspend fun getMovieWatchOptions(tmdbId: Int, title: String, year: String?): List<WatchOption> {
        return repository.getMovieWatchOptions(tmdbId, title, year)
    }

    /**
     * Get all watch options (streaming providers) for a TV show.
     */
    suspend fun getTvShowWatchOptions(tmdbId: Int, title: String, year: String?): List<WatchOption> {
        return repository.getTvShowWatchOptions(tmdbId, title, year)
    }

    /**
     * Search TMDB to resolve a title+year to a TMDB ID.
     */
    suspend fun searchTmdbIdByTitle(title: String, year: String?, isTvMode: Boolean): Int? {
        return repository.searchTmdbIdByTitle(title, year, isTvMode)
    }

    /**
     * Get torrent magnet URL for a movie to enable streaming playback.
     */
    suspend fun getTorrentMagnetUrl(title: String, year: String?): String? {
        return repository.getTorrentInfo(title, year)?.magnetUrl
    }

    /**
     * Get torrent magnet URL for a TV show (first available episode) for quick play from recommendations.
     * Returns the magnet URL and a display label like "ShowName S01E01".
     */
    suspend fun getTvShowTorrentMagnetUrl(title: String, year: String?): Pair<String, String>? {
        val info = repository.getTvShowFirstEpisodeTorrent(title, year) ?: return null
        val label = "${info.showTitle ?: title} S${info.season}E${info.episode}"
        return Pair(info.magnetUrl, label)
    }

    /**
     * Get torrent magnet URL based on current content mode.
     * For movies: returns magnet URL directly.
     * For TV shows: finds first available episode and returns (magnetUrl, displayLabel).
     */
    suspend fun getTorrentMagnetUrlForContent(title: String, year: String?, isTvMode: Boolean): Pair<String, String>? {
        return if (isTvMode) {
            getTvShowTorrentMagnetUrl(title, year)
        } else {
            val magnet = getTorrentMagnetUrl(title, year) ?: return null
            Pair(magnet, title)
        }
    }
    
    /**
     * Get torrent magnet URL for a TV show episode.
     * @param showTitle The title of the TV show
     * @param imdbId Optional IMDB ID for direct lookup
     * @param season Season number
     * @param episode Episode number  
     * @param preferredQuality Preferred quality (720p, 1080p, 480p)
     */
    suspend fun getTvEpisodeMagnetUrl(
        showTitle: String,
        imdbId: String? = null,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p"
    ): String? {
        return repository.getTvEpisodeTorrentInfo(showTitle, imdbId, season, episode, preferredQuality)?.magnetUrl
    }
    
    /**
     * Get available seasons for a TV show.
     */
    suspend fun getTvShowSeasons(imdbId: String): List<Int> {
        return repository.getTvShowSeasons(imdbId)
    }
    
    /**
     * Get episodes for a specific season.
     */
    suspend fun getTvShowEpisodes(imdbId: String, season: Int) = repository.getTvShowEpisodes(imdbId, season)
    
    /**
     * Get IMDB ID for a TV show from TMDB.
     * @param tmdbId The TMDB series ID
     */
    suspend fun getTvShowImdbId(tmdbId: Int): String? = repository.getTvShowImdbId(tmdbId)
    
    /**
     * Search for a TV show on Popcorn Time API to get its IMDB ID.
     */
    suspend fun searchTvShowTorrent(title: String, year: String? = null) = repository.searchTvShowTorrent(title, year)

    /**
     * Resolve IMDB ID for a TV show by title (Popcorn TV then TMDB fallback).
     * Used when we only have a title/year from recommendations and need the IMDB ID for episode lookups.
     */
    suspend fun resolveImdbIdByTitle(title: String, year: String? = null): String? {
        return repository.resolveImdbIdForTvShow(title, year)
    }

    /**
     * Get seasons for a TV show by title.
     * Resolves IMDB ID first, then fetches seasons.
     * Returns (imdbId, seasons) or null if show not found.
     */
    suspend fun getSeasonsForTvShowByTitle(title: String, year: String? = null): Pair<String, List<Int>>? {
        val imdbId = resolveImdbIdByTitle(title, year) ?: return null
        val seasons = repository.getTvShowSeasons(imdbId)
        return if (seasons.isNotEmpty()) Pair(imdbId, seasons) else null
    }
}

class MovieViewModelFactory(
    private val repository: MovieRepository,
    private val settings: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MovieViewModel(repository, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
