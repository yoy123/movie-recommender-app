package com.movierecommender.app.data.repository

import com.movierecommender.app.data.local.MovieDao
import com.movierecommender.app.data.model.Genre
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.MovieDetails
import com.movierecommender.app.data.remote.TmdbApiService
import com.movierecommender.app.data.remote.OmdbApiService
import com.movierecommender.app.data.remote.ImdbScraperService
import com.movierecommender.app.data.model.Video
import com.movierecommender.app.data.remote.LlmRecommendationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String) : Resource<T>()
    class Loading<T> : Resource<T>()
}

class MovieRepository(
    private val movieDao: MovieDao,
    private val apiService: TmdbApiService,
    private val llmService: LlmRecommendationService = LlmRecommendationService(),
    private val omdbService: OmdbApiService = OmdbApiService.create(),
    private val imdbScraper: ImdbScraperService = ImdbScraperService.create()
) {
    
    // OpenAI API key from BuildConfig
    private val openAiApiKey = com.movierecommender.app.BuildConfig.OPENAI_API_KEY
    
    fun getSelectedMovies(): Flow<List<Movie>> = movieDao.getSelectedMovies()
    
    fun getRecommendedMovies(): Flow<List<Movie>> = movieDao.getRecommendedMovies()
    
    fun getFavoriteMovies(): Flow<List<Movie>> = movieDao.getFavoriteMovies()
    
    suspend fun getGenres(): Flow<Resource<List<Genre>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getGenres()
            emit(Resource.Success(response.genres))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    suspend fun getMoviesByGenre(genreId: Int, page: Int = 1): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getMoviesByGenre(genreId = genreId, page = page)
            emit(Resource.Success(response.results))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    suspend fun searchMovies(query: String): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.searchMovies(query = query)
            emit(Resource.Success(response.results))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    suspend fun getRecommendations(
        selectedMovies: List<Movie>, 
        genreName: String, 
        indiePreference: Float,
        useIndiePreference: Boolean,
        popularityPreference: Float,
        usePopularityPreference: Boolean,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        tonePreference: Float,
        useTonePreference: Boolean,
        internationalPreference: Float,
        useInternationalPreference: Boolean,
        experimentalPreference: Float,
        useExperimentalPreference: Boolean
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            // Get movie titles for LLM
            val movieTitles = selectedMovies.map { "${it.title} (${it.releaseDate?.take(4) ?: ""})" }
            
            // Get recommendations from LLM
            val llmResult = withContext(Dispatchers.IO) {
                llmService.getRecommendationsFromLlm(
                    movieTitles, 
                    genreName, 
                    openAiApiKey, 
                    indiePreference,
                    useIndiePreference,
                    popularityPreference,
                    usePopularityPreference,
                    releaseYearStart,
                    releaseYearEnd,
                    useReleaseYearPreference,
                    tonePreference,
                    useTonePreference,
                    internationalPreference,
                    useInternationalPreference,
                    experimentalPreference,
                    useExperimentalPreference
                )
            }
            
            val llmResponse = llmResult.getOrNull()
            android.util.Log.d("MovieRepository", "LLM Response received: ${llmResponse?.take(200)}")
            
            val isValid = llmResponse?.isNotBlank() == true && isValidRecommendationStructure(llmResponse)
            android.util.Log.d("MovieRepository", "LLM Response valid: $isValid")
            
            val recommendationText = if (isValid) {
                android.util.Log.d("MovieRepository", "Using LLM recommendations")
                llmResponse!!
            } else {
                android.util.Log.d("MovieRepository", "Using fallback recommendations - LLM failed validation")
                if (llmResult.isFailure) {
                    android.util.Log.e("MovieRepository", "LLM Error: ${llmResult.exceptionOrNull()?.message}")
                }
                buildFallbackRecommendations(selectedMovies)
            }

            if (recommendationText.isBlank()) {
                emit(Resource.Error("No recommendations received"))
            } else {
                emit(Resource.Success(recommendationText))
            }
            
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }

    private fun isValidRecommendationStructure(text: String): Boolean {
        // Expect 15 numbered items (1-15) in the response
        // Match format: "1. Movie", "1 . Movie", "1 .Title" (LLM varies format despite instructions)
        val lines = text.lines()
        val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
        
        // Find all numbered lines using containsMatchIn (not matches, which requires full line match)
        val numberedItems = lines.mapNotNull { line ->
            numbered.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        
        android.util.Log.d("MovieRepository", "Validation found ${numberedItems.size} numbered items: $numberedItems")
        
        // Must have exactly numbers 1 through 15
        if (numberedItems.size < 15) return false
        
        // Check that we have consecutive numbers 1-15
        val hasAllNumbers = (1..15).all { it in numberedItems }
        android.util.Log.d("MovieRepository", "Has all numbers 1-15: $hasAllNumbers")
        
        return hasAllNumbers
    }

    private suspend fun buildFallbackRecommendations(selectedMovies: List<Movie>): String {
        return withContext(Dispatchers.IO) {
            try {
                // Gather TMDB recommendations/similar for each selected movie
                val pool = mutableMapOf<Int, Movie>()
                for (m in selectedMovies) {
                    runCatching { apiService.getMovieRecommendations(m.id) }.onSuccess { resp ->
                        resp.results.forEach { pool[it.id] = it }
                    }
                    runCatching { apiService.getSimilarMovies(m.id) }.onSuccess { resp ->
                        resp.results.forEach { pool[it.id] = it }
                    }
                }
                // Remove any originally selected movies
                val selectedIds = selectedMovies.map { it.id }.toSet()
                val candidates = pool.values
                    .filter { it.id !in selectedIds }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<Movie> { it.voteAverage }
                            .thenByDescending { it.popularity }
                    )
                    .take(15)

                if (candidates.isEmpty()) return@withContext ""

                val sb = StringBuilder()
                sb.append("RECOMMENDATIONS:\n\n")
                candidates.forEachIndexed { idx, movie ->
                    val year = movie.releaseDate?.take(4)?.let { " ($it)" } ?: ""
                    sb.append("${idx + 1}. ${movie.title}$year\n")
                    val desc = (movie.overview.takeIf { it.isNotBlank() }
                        ?: "A strong match based on your selections.")
                        .trim()
                        .replace("\n", " ")
                    sb.append(truncateWords(desc, 75)).append("\n\n")
                }
                sb.toString().trim()
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun truncateWords(text: String, maxWords: Int): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= maxWords) text.trim() else words.take(maxWords).joinToString(" ") + "…"
    }
    
    suspend fun saveSelectedMovie(movie: Movie) {
        movieDao.insertMovie(movie.copy(isSelected = true, isRecommended = false))
    }
    
    suspend fun removeSelectedMovie(movie: Movie) {
        // Don't delete the movie - just update it to unselected
        // This preserves favorites and other flags
        movieDao.insertMovie(movie.copy(isSelected = false, isRecommended = false))
    }
    
    suspend fun saveRecommendedMovies(movies: List<Movie>) {
        movieDao.clearRecommendedMovies()
        movieDao.insertMovies(movies.map { it.copy(isRecommended = true, isSelected = false) })
    }
    
    suspend fun clearSelectedMovies() {
        movieDao.clearSelectedMovies()
    }
    
    suspend fun clearRecommendedMovies() {
        movieDao.clearRecommendedMovies()
    }
    
    suspend fun addToFavorites(movie: Movie) {
        // First ensure the movie is in the database
        movieDao.insertMovie(movie.copy(isFavorite = true))
    }
    
    suspend fun removeFromFavorites(movieId: Int) {
        movieDao.removeFromFavorites(movieId)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun getImdbRating(title: String, year: String?): String? = withContext(Dispatchers.IO) {
        // IMDb/OMDb not used in UI; return null to avoid requiring OMDB_API_KEY
        null
    }

    suspend fun getImdbTrailerUrl(movieId: Int): String? = withContext(Dispatchers.IO) {
        try {
            // Get movie details to retrieve IMDB ID
            val details = apiService.getMovieDetails(movieId)
            val imdbId = details.imdbId
            
            android.util.Log.d("MovieRepository", "getImdbTrailerUrl: movieId=$movieId, imdbId=$imdbId")
            
            if (imdbId.isNullOrBlank()) {
                android.util.Log.w("MovieRepository", "No IMDB ID found for movie $movieId")
                return@withContext null
            }
            
            // Scrape IMDB for trailer URL
            val trailerUrl = imdbScraper.getTrailerUrl(imdbId)
            android.util.Log.d("MovieRepository", "IMDB scraper returned: $trailerUrl")
            trailerUrl
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "Error getting IMDB trailer URL", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun getImdbTrailerUrlByTitle(title: String, year: String?): String? = withContext(Dispatchers.IO) {
        try {
            // Search without year first for better results
            val search = apiService.searchMovies(query = title)
            val best = search.results.firstOrNull { m ->
                val y = m.releaseDate?.take(4)
                year == null || y == year
            } ?: search.results.firstOrNull()
            best?.id?.let { id -> getImdbTrailerUrl(id) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getTmdbRatingByTitleYear(title: String, year: String?): String? = withContext(Dispatchers.IO) {
        val candidates = buildList {
            add(title)
            val noYear = title.replace(Regex("\\(\\d{4}\\)"), "").trim()
            if (noYear.isNotEmpty()) add(noYear)
            val splitDelimiters = listOf(":", " - ", " – ", " — ")
            splitDelimiters.mapNotNull { delimiter ->
                if (noYear.contains(delimiter)) noYear.substringBefore(delimiter).trim() else null
            }.filter { it.isNotEmpty() }.forEach { add(it) }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        for (candidate in candidates) {
            try {
                val search = apiService.searchMovies(query = candidate)
                if (search.results.isEmpty()) continue

                val best = search.results.firstOrNull { m ->
                    val y = m.releaseDate?.take(4)
                    year != null && y == year
                } ?: search.results.firstOrNull()

                val rating = best?.voteAverage
                if (rating != null && rating > 0.0) {
                    return@withContext String.format("%.1f", rating)
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    }
}
