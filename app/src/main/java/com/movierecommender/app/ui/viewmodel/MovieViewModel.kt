package com.movierecommender.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.movierecommender.app.data.model.Genre
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.repository.MovieRepository
import com.movierecommender.app.data.settings.SettingsRepository
import com.movierecommender.app.data.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MovieUiState(
    val genres: List<Genre> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val selectedMovies: List<Movie> = emptyList(),
    val recommendedMovies: List<Movie> = emptyList(),
    val favoriteMovies: List<Movie> = emptyList(),
    val recommendationText: String? = null, // New: LLM text response
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedGenreId: Int? = null,
    val selectedGenreName: String? = null,
    val searchQuery: String = "",
    val isFavoritesMode: Boolean = false,
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
    val isDarkMode: Boolean = true // Dark mode preference (default: dark)
)

class MovieViewModel(
    private val repository: MovieRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MovieUiState())
    val uiState: StateFlow<MovieUiState> = _uiState.asStateFlow()
    
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
    
    fun selectGenre(genreId: Int, genreName: String) {
        // Check if this is the special Dee's Favorites genre
        val isFavorites = genreId == -1
        
        _uiState.value = _uiState.value.copy(
            selectedGenreId = genreId,
            selectedGenreName = genreName,
            isFavoritesMode = isFavorites
        )
        
        if (!isFavorites) {
            loadMoviesByGenre(genreId)
        } else {
            // For favorites, we'll show search and favorites list
            _uiState.value = _uiState.value.copy(movies = emptyList())
        }
    }
    
    fun loadMoviesByGenre(genreId: Int) {
        viewModelScope.launch {
            repository.getMoviesByGenre(genreId).collect { resource ->
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
    
    fun searchMovies(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value.selectedGenreId?.let { loadMoviesByGenre(it) }
            return
        }
        
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
    
    fun generateRecommendations() {
        val selectedMovies = _uiState.value.selectedMovies
        val genreName = _uiState.value.selectedGenreName ?: "Movies"
        val state = _uiState.value
        
        // Allow 1-5 movies
        if (selectedMovies.isEmpty() || selectedMovies.size > 5) return
        
        viewModelScope.launch {
            repository.getRecommendations(
                selectedMovies, 
                genreName, 
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
                state.useExperimentalPreference
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
            _uiState.value = _uiState.value.copy(recommendationText = null)
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

    suspend fun getTmdbRatingByTitleYear(title: String, year: String?): String? {
        return repository.getTmdbRatingByTitleYear(title, year)
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
