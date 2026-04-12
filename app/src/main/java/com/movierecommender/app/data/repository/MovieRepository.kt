package com.movierecommender.app.data.repository

import com.movierecommender.app.BuildConfig
import com.movierecommender.app.data.local.MovieDao
import com.movierecommender.app.data.model.Genre
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.MovieDetails
import com.movierecommender.app.data.model.MovieResponse
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.TvShowResponse
import com.movierecommender.app.data.remote.TmdbApiService
import com.movierecommender.app.data.remote.ImdbScraperService
import com.movierecommender.app.data.remote.PopcornApiService
import com.movierecommender.app.data.remote.PopcornTvApiService
import com.movierecommender.app.data.remote.YtsApiService
import com.movierecommender.app.data.remote.EztvApiService
import com.movierecommender.app.data.remote.PirateBayApiService
import com.movierecommender.app.data.remote.TorrentGalaxyService
import com.movierecommender.app.data.remote.LeetxService
import com.movierecommender.app.data.remote.TorrentInfo
import com.movierecommender.app.data.remote.EpisodeTorrentInfo
import com.movierecommender.app.data.remote.StreamingAppRegistry
import com.movierecommender.app.data.model.WatchOption
import com.movierecommender.app.data.model.WatchOptionType
import com.movierecommender.app.data.model.WatchProviderEntry
import com.movierecommender.app.data.remote.PopcornTvShowDetails
import com.movierecommender.app.data.remote.PopcornEpisode
import com.movierecommender.app.data.remote.PopcornEpisodeTorrent
import com.movierecommender.app.data.model.Video
import com.movierecommender.app.data.remote.LlmRecommendationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import kotlin.math.abs

sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String) : Resource<T>()
    class Loading<T> : Resource<T>()
}

class MovieRepository(
    private val movieDao: MovieDao,
    private val apiService: TmdbApiService,
    private val llmService: LlmRecommendationService = LlmRecommendationService(),
    private val imdbScraper: ImdbScraperService = ImdbScraperService(),
    private val popcornApi: PopcornApiService = PopcornApiService(),
    private val popcornTvApi: PopcornTvApiService = PopcornTvApiService(),
    private val ytsApi: YtsApiService = YtsApiService(),
    private val eztvApi: EztvApiService = EztvApiService(),
    private val pirateBayApi: PirateBayApiService = PirateBayApiService(),
    private val torrentGalaxyApi: TorrentGalaxyService = TorrentGalaxyService(),
    private val leetxApi: LeetxService = LeetxService()
) {
    
    // OpenAI API key from BuildConfig
    private val openAiApiKey = BuildConfig.OPENAI_API_KEY

    private val tmdbGenreIdToName: Map<Int, String> = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Science Fiction",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western"
    )

    // TV genre IDs (TMDB uses different sets for movies vs TV)
    private val tmdbTvGenreIdToName: Map<Int, String> = mapOf(
        10759 to "Action & Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        10762 to "Kids",
        9648 to "Mystery",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics",
        37 to "Western"
    )
    
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

    /**
     * Paged genre discovery. Returns paging metadata (page/totalPages) so UI can implement infinite scroll.
     * Kept separate from [getMoviesByGenre] to avoid changing existing call sites.
     */
    suspend fun getMoviesByGenreResponse(genreId: Int, page: Int = 1): Flow<Resource<MovieResponse>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getMoviesByGenre(genreId = genreId, page = page)
            emit(Resource.Success(response))
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
    
    // ─────────────────────────────────────────────────────────────────────────────
    // TV Shows API
    // ─────────────────────────────────────────────────────────────────────────────
    
    suspend fun getTvGenres(): Flow<Resource<List<Genre>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getTvGenres()
            emit(Resource.Success(response.genres))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    suspend fun getTvShowsByGenre(genreId: Int, page: Int = 1): Flow<Resource<List<TvShow>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getTvShowsByGenre(genreId = genreId, page = page)
            emit(Resource.Success(response.results))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    /**
     * Paged TV genre discovery. Returns paging metadata (page/totalPages) so UI can implement infinite scroll.
     */
    suspend fun getTvShowsByGenreResponse(genreId: Int, page: Int = 1): Flow<Resource<TvShowResponse>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getTvShowsByGenre(genreId = genreId, page = page)
            emit(Resource.Success(response))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    suspend fun searchTvShows(query: String): Flow<Resource<List<TvShow>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.searchTvShows(query = query)
            emit(Resource.Success(response.results))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    /**
     * Get similar TV shows for a given series.
     * Used for TV show recommendations.
     */
    suspend fun getSimilarTvShows(seriesId: Int): Flow<Resource<List<TvShow>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getSimilarTvShows(seriesId = seriesId)
            emit(Resource.Success(response.results))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }
    
    /**
     * Get TMDB recommendations for a TV show.
     */
    suspend fun getTvShowRecommendations(seriesId: Int): Flow<Resource<List<TvShow>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getTvShowRecommendations(seriesId = seriesId)
            emit(Resource.Success(response.results))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }

    /**
     * LLM-powered TV show recommendations, mirroring the movie recommendation pipeline.
     * Uses TMDB similar/recommendations endpoints to generate candidates, then the LLM
     * to rerank or generate recommendations with user preference settings.
     */
    suspend fun getTvShowRecommendationsLlm(
        selectedShows: List<TvShow>,
        genreName: String,
        genreId: Int? = null,
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
        useExperimentalPreference: Boolean,
        additionalExcludedTitles: List<String> = emptyList(),
        useLlm: Boolean = true
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            val effectiveGenreName = if (genreId != null && genreId > 0) {
                tmdbTvGenreIdToName[genreId] ?: tmdbGenreIdToName[genreId] ?: genreName
            } else {
                genreName
            }

            android.util.Log.d(
                "MovieRepository",
                "TV LLM recs: genreName='$genreName' genreId=$genreId effectiveGenreName='$effectiveGenreName'"
            )

            // Format show titles for LLM
            val showTitles = selectedShows.map { "${it.name} (${it.firstAirDate?.take(4) ?: ""})" }

            // Build exclusion list from additional titles
            val allExcluded = (showTitles + additionalExcludedTitles)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            // Build TMDB candidate pool from similar + recommended shows
            val candidateTitles = buildTmdbTvCandidateTitlesForRerank(
                selectedShows = selectedShows,
                excludedTitles = allExcluded,
                genreId = genreId,
                releaseYearStart = releaseYearStart,
                releaseYearEnd = releaseYearEnd,
                useReleaseYearPreference = useReleaseYearPreference,
                indiePreference = indiePreference,
                useIndiePreference = useIndiePreference,
                popularityPreference = popularityPreference,
                usePopularityPreference = usePopularityPreference,
                tonePreference = tonePreference,
                useTonePreference = useTonePreference
            )

            android.util.Log.d(
                "MovieRepository",
                "TV TMDB candidate pool: size=${candidateTitles.size} (first 10)=${candidateTitles.take(10)}"
            )

            val shouldUseCandidateRerank = candidateTitles.size >= 25

            if (!useLlm) {
                android.util.Log.d("MovieRepository", "TV LLM consent not given – using TMDB-only fallback")
                val fallbackResponse = buildTvFallbackRecommendations(
                    selectedShows = selectedShows,
                    excludedTitles = allExcluded,
                    genreId = genreId,
                    releaseYearStart = releaseYearStart,
                    releaseYearEnd = releaseYearEnd,
                    useReleaseYearPreference = useReleaseYearPreference
                )
                emit(Resource.Success(fallbackResponse))
                return@flow
            }

            // Use LLM with the TV show genre context
            val llmGenre = "$effectiveGenreName TV Shows"
            val llmResult = withContext(Dispatchers.IO) {
                if (shouldUseCandidateRerank) {
                    llmService.getRecommendationsFromLlmCandidates(
                        selectedMovies = showTitles,
                        candidates = candidateTitles,
                        genre = llmGenre,
                        apiKey = openAiApiKey,
                        indiePreference = indiePreference,
                        useIndiePreference = useIndiePreference,
                        popularityPreference = popularityPreference,
                        usePopularityPreference = usePopularityPreference,
                        releaseYearStart = releaseYearStart,
                        releaseYearEnd = releaseYearEnd,
                        useReleaseYearPreference = useReleaseYearPreference,
                        tonePreference = tonePreference,
                        useTonePreference = useTonePreference,
                        internationalPreference = internationalPreference,
                        useInternationalPreference = useInternationalPreference,
                        experimentalPreference = experimentalPreference,
                        useExperimentalPreference = useExperimentalPreference,
                        excludedMovies = allExcluded
                    )
                } else {
                    llmService.getRecommendationsFromLlm(
                        selectedMovies = showTitles,
                        genre = llmGenre,
                        apiKey = openAiApiKey,
                        indiePreference = indiePreference,
                        useIndiePreference = useIndiePreference,
                        popularityPreference = popularityPreference,
                        usePopularityPreference = usePopularityPreference,
                        releaseYearStart = releaseYearStart,
                        releaseYearEnd = releaseYearEnd,
                        useReleaseYearPreference = useReleaseYearPreference,
                        tonePreference = tonePreference,
                        useTonePreference = useTonePreference,
                        internationalPreference = internationalPreference,
                        useInternationalPreference = useInternationalPreference,
                        experimentalPreference = experimentalPreference,
                        useExperimentalPreference = useExperimentalPreference,
                        excludedMovies = allExcluded
                    )
                }
            }

            val llmResponse = llmResult.getOrNull()
            android.util.Log.d("MovieRepository", "TV LLM Response received: ${llmResponse?.take(200)}")

            val parsed = llmResponse?.let { parseNumberedRecommendations(it) }.orEmpty()
            val structureOk = llmResponse?.isNotBlank() == true && isValidRecommendationStructure(llmResponse)
            val candidateOk = if (shouldUseCandidateRerank && parsed.isNotEmpty()) {
                passesCandidateConstraint(recs = parsed, allowedTitles = candidateTitles)
            } else {
                true
            }

            val isValid = structureOk && candidateOk
            android.util.Log.d("MovieRepository", "TV LLM Response valid: $isValid")

            val recommendationText = if (isValid) {
                llmResponse!!
            } else {
                android.util.Log.d("MovieRepository", "TV using fallback - LLM failed validation")
                buildTvFallbackRecommendations(
                    selectedShows = selectedShows,
                    excludedTitles = allExcluded,
                    genreId = genreId,
                    releaseYearStart = releaseYearStart,
                    releaseYearEnd = releaseYearEnd,
                    useReleaseYearPreference = useReleaseYearPreference
                )
            }

            if (recommendationText.isNotBlank()) {
                emit(Resource.Success(recommendationText))
            } else {
                emit(Resource.Error("No TV show recommendations found"))
            }
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "TV recommendation error: ${e.message}", e)
            emit(Resource.Error(e.localizedMessage ?: "Failed to get TV recommendations"))
        }
    }
    
    suspend fun getRecommendations(
        selectedMovies: List<Movie>, 
        genreName: String,
        /**
         * TMDB genre id selected in the UI. If provided (and not favorites), we can validate
         * that LLM recommendations actually belong to this genre.
         */
        genreId: Int? = null,
        /**
         * Favorites mode is a pseudo-genre (id = -1) and may be mixed-genre.
         * In this mode we do NOT hard-enforce a single genre constraint.
         */
        isFavoritesMode: Boolean = false,
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
        useExperimentalPreference: Boolean,
        /**
         * Additional titles the model must NOT recommend.
         * Used for in-session retries so the next result cannot repeat the same 15 titles.
         */
        additionalExcludedTitles: List<String> = emptyList(),
        /**
         * Whether to use LLM for recommendations. If false, falls back to TMDB-only algorithm.
         * Set to false when user has declined LLM consent for GDPR/CCPA compliance.
         */
        useLlm: Boolean = true
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            // If the user is in Favorites mode, infer the dominant genre from the selected movies.
            // This prevents passing a pseudo-genre like "Dee's Favorites" to the LLM.
            val inferredGenreId = if (isFavoritesMode) inferDominantGenreId(selectedMovies) else null
            val effectiveGenreId = if (!isFavoritesMode) genreId else inferredGenreId
            val effectiveGenreName = if (!isFavoritesMode) {
                genreName
            } else {
                inferredGenreId?.let { tmdbGenreIdToName[it] } ?: genreName
            }
            val effectiveFavoritesMode = isFavoritesMode && inferredGenreId == null

            android.util.Log.d(
                "MovieRepository",
                "LLM genre context: uiGenreName='$genreName' uiGenreId=$genreId isFavoritesMode=$isFavoritesMode inferredGenreId=$inferredGenreId effectiveGenreName='$effectiveGenreName' effectiveGenreId=$effectiveGenreId"
            )

            // Fetch all movies that should NEVER be recommended
            val favoriteMovies = movieDao.getFavoriteMovies().first()
            val alreadyRecommendedMovies = movieDao.getRecommendedMovies().first()

            // Format movie titles for LLM with year
            val movieTitles = selectedMovies.map { "${it.title} (${it.releaseDate?.take(4) ?: ""})" }

            // Build comprehensive exclusion list
            val formattedFavorites = formatExcludedMovies(favoriteMovies)
            val formattedRecommended = formatExcludedMovies(alreadyRecommendedMovies)
            val formattedSelected = formatExcludedMovies(selectedMovies)

            val allExcluded = (formattedFavorites + formattedRecommended + formattedSelected + additionalExcludedTitles)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            
            // Log exclusion details for debugging
            android.util.Log.d("MovieRepository", "Exclusion Stats:")
            android.util.Log.d("MovieRepository", "  - Favorites: ${formattedFavorites.size} movies")
            android.util.Log.d("MovieRepository", "  - Already Recommended: ${formattedRecommended.size} movies")
            android.util.Log.d("MovieRepository", "  - Currently Selected: ${formattedSelected.size} movies")
            android.util.Log.d("MovieRepository", "  - Session Excluded: ${additionalExcludedTitles.size} movies")
            android.util.Log.d("MovieRepository", "  - Total Excluded: ${allExcluded.size} movies")
            if (formattedFavorites.isNotEmpty()) {
                android.util.Log.d("MovieRepository", "Favorites being excluded: ${formattedFavorites.take(5).joinToString(", ")}${if (formattedFavorites.size > 5) "..." else ""}")
            }

            // Candidate generation (TMDB) -> LLM rerank from bounded candidate list.
            // This prevents hallucinated titles and improves genre adherence.
            val candidateTitles = buildTmdbCandidateTitlesForRerank(
                selectedMovies = selectedMovies,
                favoriteMovies = favoriteMovies,
                alreadyRecommendedMovies = alreadyRecommendedMovies,
                genreId = effectiveGenreId,
                enforceGenre = !effectiveFavoritesMode,
                releaseYearStart = releaseYearStart,
                releaseYearEnd = releaseYearEnd,
                useReleaseYearPreference = useReleaseYearPreference,
                indiePreference = indiePreference,
                useIndiePreference = useIndiePreference,
                popularityPreference = popularityPreference,
                usePopularityPreference = usePopularityPreference,
                tonePreference = tonePreference,
                useTonePreference = useTonePreference
            )

            android.util.Log.d(
                "MovieRepository",
                "TMDB candidate pool for LLM rerank: size=${candidateTitles.size} (first 10)=${candidateTitles.take(10)}"
            )

            val shouldUseCandidateRerank = candidateTitles.size >= 25

            // If user has not given LLM consent, skip LLM entirely and use fallback
            if (!useLlm) {
                android.util.Log.d("MovieRepository", "LLM consent not given – using TMDB-only fallback")
                val fallbackResponse = buildFallbackRecommendations(
                    selectedMovies = selectedMovies,
                    favoriteMovies = favoriteMovies,
                    alreadyRecommendedMovies = alreadyRecommendedMovies,
                    releaseYearStart = releaseYearStart,
                    releaseYearEnd = releaseYearEnd,
                    useReleaseYearPreference = useReleaseYearPreference,
                    genreId = effectiveGenreId
                )
                emit(Resource.Success(fallbackResponse))
                return@flow
            }

            // Get recommendations from LLM with comprehensive exclusion list.
            // If we have enough candidates, force the model to choose only from them.
            val llmResult = withContext(Dispatchers.IO) {
                if (shouldUseCandidateRerank) {
                    llmService.getRecommendationsFromLlmCandidates(
                        selectedMovies = movieTitles,
                        candidates = candidateTitles,
                        genre = effectiveGenreName,
                        apiKey = openAiApiKey,
                        indiePreference = indiePreference,
                        useIndiePreference = useIndiePreference,
                        popularityPreference = popularityPreference,
                        usePopularityPreference = usePopularityPreference,
                        releaseYearStart = releaseYearStart,
                        releaseYearEnd = releaseYearEnd,
                        useReleaseYearPreference = useReleaseYearPreference,
                        tonePreference = tonePreference,
                        useTonePreference = useTonePreference,
                        internationalPreference = internationalPreference,
                        useInternationalPreference = useInternationalPreference,
                        experimentalPreference = experimentalPreference,
                        useExperimentalPreference = useExperimentalPreference,
                        excludedMovies = allExcluded
                    )
                } else {
                    llmService.getRecommendationsFromLlm(
                        selectedMovies = movieTitles,
                        genre = effectiveGenreName,
                        apiKey = openAiApiKey,
                        indiePreference = indiePreference,
                        useIndiePreference = useIndiePreference,
                        popularityPreference = popularityPreference,
                        usePopularityPreference = usePopularityPreference,
                        releaseYearStart = releaseYearStart,
                        releaseYearEnd = releaseYearEnd,
                        useReleaseYearPreference = useReleaseYearPreference,
                        tonePreference = tonePreference,
                        useTonePreference = useTonePreference,
                        internationalPreference = internationalPreference,
                        useInternationalPreference = useInternationalPreference,
                        experimentalPreference = experimentalPreference,
                        useExperimentalPreference = useExperimentalPreference,
                        excludedMovies = allExcluded
                    )
                }
            }
            
            val llmResponse = llmResult.getOrNull()
            android.util.Log.d("MovieRepository", "LLM Response received: ${llmResponse?.take(200)}")

            val parsed = llmResponse?.let { parseNumberedRecommendations(it) }.orEmpty()
            if (parsed.isNotEmpty()) {
                android.util.Log.d(
                    "MovieRepository",
                    "Parsed ${parsed.size} numbered recs (first 5): ${parsed.take(5).joinToString { r -> "${r.number}. ${r.title}${r.year?.let { y -> " ($y)" } ?: ""}" }}"
                )
            }

            val structureOk = llmResponse?.isNotBlank() == true && isValidRecommendationStructure(llmResponse)

            val candidateOk = if (shouldUseCandidateRerank && parsed.isNotEmpty()) {
                passesCandidateConstraint(recs = parsed, allowedTitles = candidateTitles)
            } else {
                true
            }

            val genreOk = if (!effectiveFavoritesMode && effectiveGenreId != null && effectiveGenreId > 0 && parsed.isNotEmpty()) {
                // If we used candidate rerank and the candidate pool itself was genre-filtered,
                // skip the expensive TMDB search-based genre check.
                if (shouldUseCandidateRerank) {
                    true
                } else {
                    passesGenreConstraint(
                        recs = parsed,
                        requiredGenreId = effectiveGenreId
                    )
                }
            } else {
                true
            }

            val isValid = structureOk && candidateOk && genreOk
            android.util.Log.d("MovieRepository", "LLM Response valid: $isValid")
            
            val recommendationText = if (isValid) {
                android.util.Log.d("MovieRepository", "Using LLM recommendations")
                llmResponse!!
            } else {
                android.util.Log.d("MovieRepository", "Using fallback recommendations - LLM failed validation")
                if (llmResult.isFailure) {
                    android.util.Log.e("MovieRepository", "LLM Error: ${llmResult.exceptionOrNull()?.message}")
                }
                buildFallbackRecommendations(
                    selectedMovies = selectedMovies,
                    favoriteMovies = favoriteMovies,
                    alreadyRecommendedMovies = alreadyRecommendedMovies,
                    releaseYearStart = releaseYearStart,
                    releaseYearEnd = releaseYearEnd,
                    useReleaseYearPreference = useReleaseYearPreference,
                    genreId = if (!effectiveFavoritesMode) effectiveGenreId else null
                )
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

    /**
     * TMDB-only baseline recommendations for A/B comparison against the LLM.
     *
     * Uses TMDB similar/recommendations for the selected movies to build a candidate pool,
     * then ranks candidates using available metadata and the user's enabled settings.
     *
     * Note: TMDB does not expose reliable metadata for tone/experimental in a strict sense.
     * We use weak proxies from genre IDs for tone and omit hard enforcement beyond year range.
     */
    suspend fun getTmdbBaselineRecommendationsText(
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
        experimentalPreference: Float,
        useExperimentalPreference: Boolean
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            val favoriteMovies = movieDao.getFavoriteMovies().first()
            val alreadyRecommendedMovies = movieDao.getRecommendedMovies().first()

            val disallowedIds = (selectedMovies + favoriteMovies + alreadyRecommendedMovies).map { it.id }.toSet()
            val minYear = releaseYearStart.toInt()
            val maxYear = releaseYearEnd.toInt()

            // 1) Build candidate pool from TMDB recs/similar
            val pool = mutableMapOf<Int, Movie>()
            for (m in selectedMovies) {
                runCatching { apiService.getMovieRecommendations(m.id) }.onSuccess { resp ->
                    resp.results.forEach { pool[it.id] = it }
                }
                runCatching { apiService.getSimilarMovies(m.id) }.onSuccess { resp ->
                    resp.results.forEach { pool[it.id] = it }
                }
            }

            val filtered = pool.values
                .asSequence()
                .filter { it.id !in disallowedIds }
                .filter { movie ->
                    if (!useReleaseYearPreference) return@filter true
                    val year = movie.releaseDate?.take(4)?.toIntOrNull() ?: return@filter false
                    year in minYear..maxYear
                }
                .distinctBy { it.id }
                .toList()

            if (filtered.isEmpty()) {
                emit(Resource.Error("No baseline candidates available"))
                return@flow
            }

            // 2) Compute normalization stats
            fun minMax(values: List<Double>): Pair<Double, Double> {
                val min = values.minOrNull() ?: 0.0
                val max = values.maxOrNull() ?: 1.0
                return min to max
            }
            fun norm(x: Double, min: Double, max: Double): Double {
                if (max <= min) return 0.5
                return ((x - min) / (max - min)).coerceIn(0.0, 1.0)
            }

            val (popMin, popMax) = minMax(filtered.map { it.popularity })
            val (vcMin, vcMax) = minMax(filtered.map { it.voteCount.toDouble() })

            // Weak proxies based on genre IDs for tone.
            val darkGenreIds = setOf(27, 53, 80, 18, 9648, 10752)
            val lightGenreIds = setOf(35, 16, 10751, 10402, 10749, 12, 14)
            fun toneProxy(movie: Movie): Double {
                val ids = movie.genreIds
                if (ids.isEmpty()) return 0.5
                val darkHits = ids.count { it in darkGenreIds }
                val lightHits = ids.count { it in lightGenreIds }
                val total = (darkHits + lightHits).coerceAtLeast(1)
                // 0 = light leaning, 1 = dark leaning
                return (darkHits.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            }

            // 3) Score candidates
            data class Scored(val movie: Movie, val score: Double, val whyBits: List<String>)

            val scored = filtered.map { movie ->
                val nPop = norm(movie.popularity, popMin, popMax)
                val nVc = norm(movie.voteCount.toDouble(), vcMin, vcMax)

                // Base quality signal.
                var score = (movie.voteAverage * 2.0) + (nVc * 0.6) + (nPop * 0.4)
                val why = mutableListOf<String>()

                if (usePopularityPreference) {
                    val match = 1.0 - kotlin.math.abs(nPop - popularityPreference.toDouble())
                    score += match * 1.8
                    why.add(if (popularityPreference < 0.45f) "cult-leaning" else if (popularityPreference > 0.55f) "mainstream-leaning" else "balanced popularity")
                }

                if (useIndiePreference) {
                    // Indie proxy: low popularity and lower vote-count tend to correlate with "indie".
                    val indieProxy = ((1.0 - nPop) * 0.7) + ((1.0 - nVc) * 0.3)
                    val match = 1.0 - kotlin.math.abs(indieProxy - indiePreference.toDouble())
                    score += match * 1.6
                    why.add(if (indiePreference > 0.6f) "more indie" else if (indiePreference < 0.4f) "more blockbuster" else "mixed scale")
                }

                if (useTonePreference) {
                    val tProxy = toneProxy(movie)
                    val match = 1.0 - kotlin.math.abs(tProxy - tonePreference.toDouble())
                    score += match * 0.9
                    why.add(if (tonePreference > 0.6f) "darker tone" else if (tonePreference < 0.4f) "lighter tone" else "balanced tone")
                }

                if (useExperimentalPreference) {
                    // TMDB doesn't provide a reliable experimentalness signal; keep this low-weight.
                    score += (experimentalPreference.toDouble() * 0.05)
                }

                Scored(movie, score, why)
            }

            val top = scored
                .sortedWith(compareByDescending<Scored> { it.score }
                    .thenByDescending { it.movie.voteAverage }
                    .thenByDescending { it.movie.popularity })
                .take(15)

            if (top.size < 15) {
                emit(Resource.Error("Not enough baseline recommendations found"))
                return@flow
            }

            // 4) Format comparable text (same structure as LLM output)
            val analysis = buildString {
                append("TMDB baseline (no LLM): built from TMDB Similar/Recommendations for your selections and ranked using available metadata. ")
                append("Genre context: $genreName. ")
                append("Hard-enforced: exclusions + year range (if enabled). ")
                append("Scored with proxies for indie/popularity and a light tone proxy from genre IDs.")
            }

            val sb = StringBuilder()
            sb.append("Analysis:\n")
            sb.append(analysis).append("\n\n")
            sb.append("RECOMMENDATIONS:\n\n")
            top.forEachIndexed { idx, s ->
                val year = s.movie.releaseDate?.take(4)?.takeIf { it.length == 4 } ?: ""
                val title = if (year.isNotBlank()) "${s.movie.title} ($year)" else s.movie.title
                sb.append("${idx + 1}. $title\n")
                val bits = s.whyBits.distinct().take(3)
                val whyLine = if (bits.isNotEmpty()) {
                    "Why this matches: ranked by metadata (${bits.joinToString(", ")}) from TMDB similar/recommended results."
                } else {
                    "Why this matches: ranked by TMDB metadata from similar/recommended results."
                }
                sb.append(whyLine).append("\n\n")
            }

            emit(Resource.Success(sb.toString().trim()))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
        }
    }

    private fun isValidRecommendationStructure(text: String): Boolean {
        // Require an analysis section (the UI depends on it for the top paragraph).
        val hasAnalysis = text.lines().any { it.trim().equals("Analysis:", ignoreCase = true) }
        if (!hasAnalysis) return false

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

    private data class ParsedRec(
        val number: Int,
        val title: String,
        val year: Int?
    )

    /**
     * Extract numbered recommendation items (e.g. "1. Movie Title (2020)") from the LLM output.
     * We only parse the numbered title line (not the Why lines).
     */
    private fun parseNumberedRecommendations(text: String): List<ParsedRec> {
        val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*(.+?)\\s*$")
        val yearSuffix = Regex("^(.*?)(?:\\s*\\((\\d{4})\\))\\s*$")

        return text
            .lines()
            .mapNotNull { line ->
                val match = numbered.find(line) ?: return@mapNotNull null
                val number = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val raw = match.groupValues.getOrNull(2)?.trim().orEmpty()
                if (raw.isBlank()) return@mapNotNull null

                val y = yearSuffix.find(raw)
                val title = (y?.groupValues?.getOrNull(1) ?: raw).trim()
                val year = y?.groupValues?.getOrNull(2)?.toIntOrNull()

                if (title.isBlank()) return@mapNotNull null
                ParsedRec(number = number, title = title, year = year)
            }
            .distinctBy { it.number }
            .sortedBy { it.number }
    }

    /**
     * Validate that the LLM's recommended titles belong to the selected TMDB genre.
     *
     * We use TMDB search results (which include genre_ids). If a year is present, we prefer
     * a match with the same release year.
     */
    private suspend fun passesGenreConstraint(
        recs: List<ParsedRec>,
        requiredGenreId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        // Be strict but allow a couple of misses due to search ambiguity.
        val maxAllowedMisses = 2
        var misses = 0
        val missTitles = mutableListOf<String>()

        for (rec in recs.take(15)) {
            val candidate = runCatching { apiService.searchMovies(query = rec.title) }
                .getOrNull()
                ?.results
                .orEmpty()
                .let { results ->
                    if (rec.year != null) {
                        results.firstOrNull { it.releaseDate?.take(4)?.toIntOrNull() == rec.year }
                            ?: results.firstOrNull()
                    } else {
                        results.firstOrNull()
                    }
                }

            val inGenre = if (candidate == null) {
                false
            } else if (candidate.genreIds.isNotEmpty()) {
                candidate.genreIds.contains(requiredGenreId)
            } else {
                // Fallback to details if genre_ids are absent
                runCatching { apiService.getMovieDetails(candidate.id) }
                    .getOrNull()
                    ?.genres
                    ?.any { it.id == requiredGenreId }
                    ?: false
            }

            if (!inGenre) {
                misses += 1
                missTitles.add("${rec.title}${rec.year?.let { y -> " ($y)" } ?: ""}")
                if (misses > maxAllowedMisses) break
            }
        }

        android.util.Log.d(
            "MovieRepository",
            "Genre check requiredGenreId=$requiredGenreId misses=$misses (allowed=$maxAllowedMisses) missSamples=${missTitles.take(5)}"
        )

        misses <= maxAllowedMisses
    }

    /**
     * Validate that every recommended title appears in the TMDB candidate list we provided to the LLM.
     * This blocks hallucinations and prevents drift.
     */
    private fun passesCandidateConstraint(
        recs: List<ParsedRec>,
        allowedTitles: List<String>
    ): Boolean {
        if (allowedTitles.isEmpty()) return true

        val allowedKeys: Set<String> = allowedTitles.mapNotNull { t ->
            val y = Regex("\\((\\d{4})\\)").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (y == null) return@mapNotNull null
            normalizeCandidateTitle(t) + y.toString()
        }.toSet()

        val missing = mutableListOf<String>()
        for (rec in recs.take(15)) {
            val y = rec.year
            if (y == null) {
                missing.add(rec.title)
                continue
            }
            val key = normalizeCandidateTitle(rec.title) + y.toString()
            if (key !in allowedKeys) {
                missing.add("${rec.title} ($y)")
            }
        }

        android.util.Log.d(
            "MovieRepository",
            "Candidate check: allowed=${allowedTitles.size} missing=${missing.size} missingSamples=${missing.take(5)}"
        )

        return missing.isEmpty()
    }

    /**
     * Normalize title text for robust matching across sources.
     *
     * - lowercases
     * - removes year in parentheses if present
     * - removes leading articles
     * - strips non-alphanumerics
     */
    private fun normalizeCandidateTitle(title: String): String {
        return title
            .lowercase()
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("^(the|a|an)\\s+"), "")
            .replace(Regex("[^a-z0-9]+"), "")
            .trim()
    }

    /**
     * Infer the dominant genre id from the selected movies' TMDB genre_ids.
     * Returns null if there is no clear dominant genre.
     */
    private fun inferDominantGenreId(selectedMovies: List<Movie>): Int? {
        if (selectedMovies.isEmpty()) return null

        val counts = mutableMapOf<Int, Int>()
        selectedMovies.forEach { m ->
            m.genreIds.forEach { gid ->
                counts[gid] = (counts[gid] ?: 0) + 1
            }
        }

        val best = counts.maxByOrNull { it.value } ?: return null

        // Require the dominant genre to appear in at least 60% of selected movies.
        val threshold = kotlin.math.ceil(selectedMovies.size * 0.6).toInt().coerceAtLeast(1)
        return if (best.value >= threshold) best.key else null
    }

    private suspend fun buildFallbackRecommendations(
        selectedMovies: List<Movie>,
        favoriteMovies: List<Movie>,
        alreadyRecommendedMovies: List<Movie>,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        genreId: Int? = null
    ): String {
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
                // Remove any disallowed movies (selected, favorites, already recommended)
                val disallowedIds = (selectedMovies + favoriteMovies + alreadyRecommendedMovies).map { it.id }.toSet()
                val minYear = releaseYearStart.toInt()
                val maxYear = releaseYearEnd.toInt()
                val candidates = pool.values
                    .filter { it.id !in disallowedIds }
                    .filter { movie ->
                        if (genreId == null || genreId <= 0) return@filter true
                        movie.genreIds.contains(genreId)
                    }
                    .filter { movie ->
                        if (!useReleaseYearPreference) return@filter true
                        val year = movie.releaseDate?.take(4)?.toIntOrNull() ?: return@filter false
                        year in minYear..maxYear
                    }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<Movie> { it.voteAverage }
                            .thenByDescending { it.popularity }
                    )
                    .take(15)

                if (candidates.isEmpty()) return@withContext ""

                val sb = StringBuilder()
                sb.append("Analysis:\n")
                sb.append("Based on your selected films, here are 15 picks with a similar vibe and quality.")
                sb.append("\n\n")
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

    /**
     * Build a high-quality, bounded list of candidate titles from TMDB to give the LLM a constrained
     * selection set. Titles are formatted as "Title (YYYY)".
     */
    private suspend fun buildTmdbCandidateTitlesForRerank(
        selectedMovies: List<Movie>,
        favoriteMovies: List<Movie>,
        alreadyRecommendedMovies: List<Movie>,
        genreId: Int?,
        enforceGenre: Boolean,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        indiePreference: Float,
        useIndiePreference: Boolean,
        popularityPreference: Float,
        usePopularityPreference: Boolean,
        tonePreference: Float,
        useTonePreference: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val disallowedIds = (selectedMovies + favoriteMovies + alreadyRecommendedMovies).map { it.id }.toSet()
            val minYear = releaseYearStart.toInt()
            val maxYear = releaseYearEnd.toInt()

            val pool = mutableMapOf<Int, Movie>()

            // 1) Similar + Recommendations from selected movies
            for (m in selectedMovies) {
                runCatching { apiService.getMovieRecommendations(m.id, page = 1) }.onSuccess { resp ->
                    resp.results.forEach { pool[it.id] = it }
                }
                runCatching { apiService.getSimilarMovies(m.id, page = 1) }.onSuccess { resp ->
                    resp.results.forEach { pool[it.id] = it }
                }
            }

            // 2) Genre discovery if applicable (helps when selected movies are niche)
            if (enforceGenre && genreId != null && genreId > 0) {
                runCatching { apiService.getMoviesByGenre(genreId = genreId, sortBy = "vote_average.desc", page = 1) }
                    .onSuccess { resp -> resp.results.forEach { pool[it.id] = it } }
                runCatching { apiService.getMoviesByGenre(genreId = genreId, sortBy = "popularity.desc", page = 1) }
                    .onSuccess { resp -> resp.results.forEach { pool[it.id] = it } }
            }

            val filtered = pool.values
                .asSequence()
                .filter { it.id !in disallowedIds }
                .filter { m ->
                    // Require a year for stable "Title (YYYY)" formatting.
                    m.releaseDate?.take(4)?.toIntOrNull() != null
                }
                .filter { m ->
                    if (!useReleaseYearPreference) return@filter true
                    val y = m.releaseDate?.take(4)?.toIntOrNull() ?: return@filter false
                    y in minYear..maxYear
                }
                .filter { m ->
                    if (!enforceGenre || genreId == null || genreId <= 0) return@filter true
                    m.genreIds.contains(genreId)
                }
                .distinctBy { it.id }
                .toList()

            if (filtered.isEmpty()) return@withContext emptyList()

            val ranked = rankCandidatesForRerank(
                candidates = filtered,
                indiePreference = indiePreference,
                useIndiePreference = useIndiePreference,
                popularityPreference = popularityPreference,
                usePopularityPreference = usePopularityPreference,
                tonePreference = tonePreference,
                useTonePreference = useTonePreference
            )

            // Keep prompt size under control.
            ranked
                .take(80)
                .mapNotNull { m ->
                    val year = m.releaseDate?.take(4)?.toIntOrNull() ?: return@mapNotNull null
                    "${m.title} ($year)"
                }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun rankCandidatesForRerank(
        candidates: List<Movie>,
        indiePreference: Float,
        useIndiePreference: Boolean,
        popularityPreference: Float,
        usePopularityPreference: Boolean,
        tonePreference: Float,
        useTonePreference: Boolean
    ): List<Movie> {
        if (candidates.isEmpty()) return emptyList()

        fun minMax(values: List<Double>): Pair<Double, Double> {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            return min to max
        }
        fun norm(x: Double, min: Double, max: Double): Double {
            if (max <= min) return 0.5
            return ((x - min) / (max - min)).coerceIn(0.0, 1.0)
        }

        val (popMin, popMax) = minMax(candidates.map { it.popularity })
        val (vcMin, vcMax) = minMax(candidates.map { it.voteCount.toDouble() })

        val darkGenreIds = setOf(27, 53, 80, 18, 9648, 10752)
        val lightGenreIds = setOf(35, 16, 10751, 10402, 10749, 12, 14)
        fun toneProxy(movie: Movie): Double {
            val ids = movie.genreIds
            if (ids.isEmpty()) return 0.5
            val darkHits = ids.count { it in darkGenreIds }
            val lightHits = ids.count { it in lightGenreIds }
            val total = (darkHits + lightHits).coerceAtLeast(1)
            return (darkHits.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        }

        data class Scored(val movie: Movie, val score: Double)
        val scored = candidates.map { movie ->
            val nPop = norm(movie.popularity, popMin, popMax)
            val nVc = norm(movie.voteCount.toDouble(), vcMin, vcMax)

            var score = (movie.voteAverage * 2.0) + (nVc * 0.7) + (nPop * 0.3)

            if (usePopularityPreference) {
                val match = 1.0 - abs(nPop - popularityPreference.toDouble())
                score += match * 1.2
            }

            if (useIndiePreference) {
                val indieProxy = ((1.0 - nPop) * 0.7) + ((1.0 - nVc) * 0.3)
                val match = 1.0 - abs(indieProxy - indiePreference.toDouble())
                score += match * 1.0
            }

            if (useTonePreference) {
                val tProxy = toneProxy(movie)
                val match = 1.0 - abs(tProxy - tonePreference.toDouble())
                score += match * 0.4
            }

            Scored(movie, score)
        }

        return scored
            .sortedWith(compareByDescending<Scored> { it.score }
                .thenByDescending { it.movie.voteAverage }
                .thenByDescending { it.movie.voteCount }
                .thenByDescending { it.movie.popularity })
            .map { it.movie }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TV Show Candidate Generation & Fallback
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Build a candidate list of TV show titles from TMDB for LLM reranking.
     * Mirrors [buildTmdbCandidateTitlesForRerank] but uses TV-specific endpoints.
     */
    private suspend fun buildTmdbTvCandidateTitlesForRerank(
        selectedShows: List<TvShow>,
        excludedTitles: List<String>,
        genreId: Int?,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        indiePreference: Float,
        useIndiePreference: Boolean,
        popularityPreference: Float,
        usePopularityPreference: Boolean,
        tonePreference: Float,
        useTonePreference: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val selectedIds = selectedShows.map { it.id }.toSet()
            val excludedNormalized = excludedTitles.map { it.lowercase().replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim() }.toSet()
            val minYear = releaseYearStart.toInt()
            val maxYear = releaseYearEnd.toInt()

            val pool = mutableMapOf<Int, TvShow>()

            // 1) Similar + Recommendations from each selected show
            for (show in selectedShows) {
                runCatching { apiService.getSimilarTvShows(show.id, page = 1) }.onSuccess { resp ->
                    resp.results.forEach { pool[it.id] = it }
                }
                runCatching { apiService.getTvShowRecommendations(show.id, page = 1) }.onSuccess { resp ->
                    resp.results.forEach { pool[it.id] = it }
                }
            }

            // 2) Genre discovery for broader pool
            if (genreId != null && genreId > 0) {
                runCatching { apiService.getTvShowsByGenre(genreId = genreId, sortBy = "vote_average.desc", page = 1) }
                    .onSuccess { resp -> resp.results.forEach { pool[it.id] = it } }
                runCatching { apiService.getTvShowsByGenre(genreId = genreId, sortBy = "popularity.desc", page = 1) }
                    .onSuccess { resp -> resp.results.forEach { pool[it.id] = it } }
                // Page 2 for more variety
                runCatching { apiService.getTvShowsByGenre(genreId = genreId, sortBy = "popularity.desc", page = 2) }
                    .onSuccess { resp -> resp.results.forEach { pool[it.id] = it } }
            }

            val filtered = pool.values
                .asSequence()
                .filter { it.id !in selectedIds }
                .filter { show ->
                    show.firstAirDate?.take(4)?.toIntOrNull() != null
                }
                .filter { show ->
                    val normalized = show.name.lowercase().trim()
                    normalized !in excludedNormalized
                }
                .filter { show ->
                    if (!useReleaseYearPreference) return@filter true
                    val y = show.firstAirDate?.take(4)?.toIntOrNull() ?: return@filter false
                    y in minYear..maxYear
                }
                .filter { show ->
                    if (genreId == null || genreId <= 0) return@filter true
                    show.genreIds.contains(genreId)
                }
                .distinctBy { it.id }
                .toList()

            if (filtered.isEmpty()) return@withContext emptyList()

            // Rank candidates using TV-appropriate scoring
            val ranked = rankTvCandidatesForRerank(
                candidates = filtered,
                indiePreference = indiePreference,
                useIndiePreference = useIndiePreference,
                popularityPreference = popularityPreference,
                usePopularityPreference = usePopularityPreference,
                tonePreference = tonePreference,
                useTonePreference = useTonePreference
            )

            ranked
                .take(80)
                .mapNotNull { show ->
                    val year = show.firstAirDate?.take(4)?.toIntOrNull() ?: return@mapNotNull null
                    "${show.name} ($year)"
                }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun rankTvCandidatesForRerank(
        candidates: List<TvShow>,
        indiePreference: Float,
        useIndiePreference: Boolean,
        popularityPreference: Float,
        usePopularityPreference: Boolean,
        tonePreference: Float,
        useTonePreference: Boolean
    ): List<TvShow> {
        if (candidates.isEmpty()) return emptyList()

        fun minMax(values: List<Double>): Pair<Double, Double> {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            return min to max
        }
        fun norm(x: Double, min: Double, max: Double): Double {
            if (max <= min) return 0.5
            return ((x - min) / (max - min)).coerceIn(0.0, 1.0)
        }

        val (popMin, popMax) = minMax(candidates.map { it.popularity })
        val (vcMin, vcMax) = minMax(candidates.map { it.voteCount.toDouble() })

        // TV genre tone mapping (dark vs light)
        val darkTvGenreIds = setOf(80, 18, 9648, 10768) // Crime, Drama, Mystery, War & Politics
        val lightTvGenreIds = setOf(35, 16, 10751, 10762, 10764) // Comedy, Animation, Family, Kids, Reality

        fun toneProxy(show: TvShow): Double {
            val ids = show.genreIds
            if (ids.isEmpty()) return 0.5
            val darkHits = ids.count { it in darkTvGenreIds }
            val lightHits = ids.count { it in lightTvGenreIds }
            val total = (darkHits + lightHits).coerceAtLeast(1)
            return (darkHits.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        }

        data class ScoredTv(val show: TvShow, val score: Double)
        val scored = candidates.map { show ->
            val nPop = norm(show.popularity, popMin, popMax)
            val nVc = norm(show.voteCount.toDouble(), vcMin, vcMax)

            var score = (show.voteAverage * 2.0) + (nVc * 0.7) + (nPop * 0.3)

            if (usePopularityPreference) {
                val match = 1.0 - abs(nPop - popularityPreference.toDouble())
                score += match * 1.2
            }

            if (useIndiePreference) {
                val indieProxy = ((1.0 - nPop) * 0.7) + ((1.0 - nVc) * 0.3)
                val match = 1.0 - abs(indieProxy - indiePreference.toDouble())
                score += match * 1.0
            }

            if (useTonePreference) {
                val tProxy = toneProxy(show)
                val match = 1.0 - abs(tProxy - tonePreference.toDouble())
                score += match * 0.4
            }

            ScoredTv(show, score)
        }

        return scored
            .sortedWith(compareByDescending<ScoredTv> { it.score }
                .thenByDescending { it.show.voteAverage }
                .thenByDescending { it.show.voteCount }
                .thenByDescending { it.show.popularity })
            .map { it.show }
    }

    /**
     * TMDB-only fallback for TV show recommendations (no LLM).
     */
    private suspend fun buildTvFallbackRecommendations(
        selectedShows: List<TvShow>,
        excludedTitles: List<String>,
        genreId: Int?,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val selectedIds = selectedShows.map { it.id }.toSet()
                val minYear = releaseYearStart.toInt()
                val maxYear = releaseYearEnd.toInt()

                val pool = mutableMapOf<Int, TvShow>()
                for (show in selectedShows) {
                    runCatching { apiService.getSimilarTvShows(show.id) }.onSuccess { resp ->
                        resp.results.forEach { pool[it.id] = it }
                    }
                    runCatching { apiService.getTvShowRecommendations(show.id) }.onSuccess { resp ->
                        resp.results.forEach { pool[it.id] = it }
                    }
                }

                val candidates = pool.values
                    .filter { it.id !in selectedIds }
                    .filter { show ->
                        if (genreId == null || genreId <= 0) return@filter true
                        show.genreIds.contains(genreId)
                    }
                    .filter { show ->
                        if (!useReleaseYearPreference) return@filter true
                        val year = show.firstAirDate?.take(4)?.toIntOrNull() ?: return@filter false
                        year in minYear..maxYear
                    }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<TvShow> { it.voteAverage }
                            .thenByDescending { it.popularity }
                    )
                    .take(15)

                if (candidates.isEmpty()) return@withContext ""

                val sb = StringBuilder()
                sb.append("Analysis:\n")
                sb.append("Based on your selected TV shows, here are 15 similar shows you might enjoy.")
                sb.append("\n\n")
                sb.append("RECOMMENDATIONS:\n\n")
                candidates.forEachIndexed { idx, show ->
                    val year = show.firstAirDate?.take(4)?.let { " ($it)" } ?: ""
                    sb.append("${idx + 1}. ${show.name}$year\n")
                    val desc = (show.overview.takeIf { it.isNotBlank() }
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
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Database Cleanup (prevents unbounded growth)
    // ─────────────────────────────────────────────────────────────────────────────
    
    companion object {
        /** Cleanup orphaned movies older than 30 days */
        const val ORPHAN_AGE_DAYS = 30L
        /** Minimum interval between cleanups (7 days) */
        const val CLEANUP_INTERVAL_DAYS = 7L
        
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Perform database cleanup if needed (>7 days since last cleanup).
     * Deletes orphaned movies (not selected, not recommended, not favorite) older than 30 days.
     * 
     * @param lastCleanupTime Unix timestamp of last cleanup (from SettingsRepository)
     * @return Pair of (shouldUpdateTimestamp, deletedCount) - first is true if cleanup was performed
     */
    suspend fun cleanupOrphanedMoviesIfNeeded(lastCleanupTime: Long): Pair<Boolean, Int> {
        val now = System.currentTimeMillis()
        val daysSinceLastCleanup = (now - lastCleanupTime) / MILLIS_PER_DAY
        
        if (daysSinceLastCleanup < CLEANUP_INTERVAL_DAYS) {
            android.util.Log.d("MovieRepository", "Cleanup skipped: only $daysSinceLastCleanup days since last cleanup")
            return Pair(false, 0)
        }
        
        val cutoffTime = now - (ORPHAN_AGE_DAYS * MILLIS_PER_DAY)
        val orphanCountBefore = movieDao.countOrphanedMovies()
        val deletedCount = movieDao.deleteOldOrphanedMovies(cutoffTime)
        
        android.util.Log.d(
            "MovieRepository",
            "Database cleanup: deleted $deletedCount orphaned movies (had $orphanCountBefore orphans, cutoff=${ORPHAN_AGE_DAYS}d)"
        )
        
        return Pair(true, deletedCount)
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

    /**
     * Get direct video URL for a movie trailer.
     * Uses IMDB scraping to get direct MP4 URLs that can be played in WebView or ExoPlayer.
     */
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
        android.util.Log.d("MovieRepository", "=== getImdbTrailerUrlByTitle called: $title ($year) ===")
        try {
            // Search without year first for better results
            val search = apiService.searchMovies(query = title)
            android.util.Log.d("MovieRepository", "TMDB search for '$title' returned ${search.results.size} results")
            val best = search.results.firstOrNull { m ->
                val y = m.releaseDate?.take(4)
                year == null || y == year
            } ?: search.results.firstOrNull()
            if (best != null) {
                android.util.Log.d("MovieRepository", "Best match: ${best.title} (id=${best.id})")
            } else {
                android.util.Log.w("MovieRepository", "No TMDB match found for '$title'")
            }
            best?.id?.let { id -> getImdbTrailerUrl(id) }
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "Error in getImdbTrailerUrlByTitle: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Get trailer URL for a TV show by title and year.
     * Uses TMDB TV search to find the show, then fetches YouTube trailer from TMDB Videos API.
     * Returns a "youtube:<key>" prefixed URL that TrailerScreen can render in WebView.
     * Falls back to IMDB scraping if no YouTube trailer is found on TMDB.
     */
    suspend fun getTvShowTrailerUrlByTitle(title: String, year: String?): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("MovieRepository", "=== getTvShowTrailerUrlByTitle called: $title ($year) ===")
        try {
            // Search TMDB TV shows endpoint
            val search = apiService.searchTvShows(query = title)
            android.util.Log.d("MovieRepository", "TMDB TV search for '$title' returned ${search.results.size} results")
            val best = search.results.firstOrNull { show ->
                val y = show.firstAirDate?.take(4)
                year == null || y == year
            } ?: search.results.firstOrNull()

            if (best == null) {
                android.util.Log.w("MovieRepository", "No TMDB TV match found for '$title'")
                return@withContext null
            }
            android.util.Log.d("MovieRepository", "Best TV match: ${best.name} (id=${best.id})")

            // Try TMDB Videos API for YouTube trailers first
            try {
                val videos = apiService.getTvShowVideos(best.id)
                val trailer = videos.results.firstOrNull { v ->
                    v.site.equals("YouTube", ignoreCase = true) &&
                    v.type.equals("Trailer", ignoreCase = true)
                } ?: videos.results.firstOrNull { v ->
                    v.site.equals("YouTube", ignoreCase = true) &&
                    (v.type.equals("Teaser", ignoreCase = true) || v.type.equals("Opening Credits", ignoreCase = true))
                } ?: videos.results.firstOrNull { v ->
                    v.site.equals("YouTube", ignoreCase = true)
                }

                if (trailer != null) {
                    val youtubeUrl = "youtube:${trailer.key}"
                    android.util.Log.d("MovieRepository", "Found YouTube trailer for TV show: $youtubeUrl")
                    return@withContext youtubeUrl
                }
            } catch (e: Exception) {
                android.util.Log.w("MovieRepository", "Failed to get TV show videos from TMDB: ${e.message}")
            }

            // Fallback: Try IMDB scraping via external IDs
            try {
                val externalIds = apiService.getTvShowExternalIds(best.id)
                val imdbId = externalIds.imdbId
                if (!imdbId.isNullOrBlank()) {
                    val trailerUrl = imdbScraper.getTrailerUrl(imdbId)
                    if (trailerUrl != null) {
                        android.util.Log.d("MovieRepository", "Found IMDB trailer for TV show: $trailerUrl")
                        return@withContext trailerUrl
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MovieRepository", "IMDB fallback failed for TV show: ${e.message}")
            }

            android.util.Log.w("MovieRepository", "No trailer found for TV show: $title")
            null
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "Error in getTvShowTrailerUrlByTitle: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Get torrent info for a TV show's first episode (S01E01) for quick "Watch Now" from recommendations.
     * Searches Popcorn TV API for the show, gets details, and returns best torrent for S01E01.
     * Falls back to EZTV API if Popcorn TV doesn't have the show.
     */
    suspend fun getTvShowFirstEpisodeTorrent(title: String, year: String?): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("MovieRepository", "Searching TV show torrent for quick play: $title ($year)")
        var resolvedImdbId: String? = null
        try {
            // Search for the show on Popcorn TV
            val show = popcornTvApi.searchShow(title, year)
            if (show == null) {
                android.util.Log.w("MovieRepository", "TV show not found on Popcorn API: $title")
                // Fallback: try keyword search
                val keywordResults = popcornTvApi.searchShowsByKeywords(title)
                val keywordMatch = keywordResults?.firstOrNull()
                if (keywordMatch?.imdbId != null) {
                    resolvedImdbId = keywordMatch.imdbId
                    val details = popcornTvApi.getShowDetails(keywordMatch.imdbId)
                    if (details != null) {
                        val result = findBestFirstEpisode(details)
                        if (result != null) return@withContext result
                    }
                }
                // Popcorn failed — fall through to EZTV
            } else {
                val imdbId = show.imdbId
                if (imdbId != null) {
                    resolvedImdbId = imdbId
                    val details = popcornTvApi.getShowDetails(imdbId)
                    if (details != null) {
                        val result = findBestFirstEpisode(details)
                        if (result != null) return@withContext result
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "Popcorn TV search failed: ${e.message}")
        }

        // EZTV fallback
        android.util.Log.d("MovieRepository", "Trying EZTV fallback for: $title")
        try {
            // Resolve IMDB ID via TMDB if we don't have one yet
            val imdbId = resolvedImdbId ?: resolveImdbIdForTvShow(title, year)
            if (imdbId != null) {
                val result = eztvApi.getFirstEpisodeTorrent(imdbId, showTitle = title)
                if (result != null) {
                    android.util.Log.d("MovieRepository", "EZTV found torrent for $title: S${result.season}E${result.episode}")
                    return@withContext result
                }
            }
            android.util.Log.w("MovieRepository", "EZTV also failed for: $title")
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "EZTV fallback failed: ${e.message}")
        }

        null
    }

    /**
     * Find the best available first episode torrent from show details.
     * Tries S01E01 first, then falls back to any available episode.
     */
    private fun findBestFirstEpisode(details: PopcornTvShowDetails): EpisodeTorrentInfo? {
        // Try S01E01 first
        var torrent = popcornTvApi.getEpisodeTorrent(details, 1, 1, "720p")
        if (torrent != null) {
            android.util.Log.d("MovieRepository", "Found S01E01 torrent: ${torrent.quality} with ${torrent.seeds} seeds")
            return torrent
        }

        // Fallback: find any available episode with torrents
        val seasons = popcornTvApi.getSeasons(details)
        for (season in seasons) {
            val episodes = popcornTvApi.getEpisodesForSeason(details, season)
            for (episode in episodes) {
                val epNum = episode.episode ?: continue
                torrent = popcornTvApi.getEpisodeTorrent(details, season, epNum, "720p")
                if (torrent != null) {
                    android.util.Log.d("MovieRepository", "Found S${season}E${epNum} torrent: ${torrent.quality} with ${torrent.seeds} seeds")
                    return torrent
                }
            }
        }

        android.util.Log.w("MovieRepository", "No episodes with torrents found for: ${details.title}")
        return null
    }

    /**
     * Resolve IMDB ID for a TV show via TMDB search + external IDs.
     * Used when Popcorn TV API doesn't return an IMDB ID.
     * Also tries Popcorn TV API search first for faster resolution.
     */
    suspend fun resolveImdbIdForTvShow(title: String, year: String? = null): String? = withContext(Dispatchers.IO) {
        // Try Popcorn TV API first (faster, no extra API call)
        try {
            val popcornResult = popcornTvApi.searchShow(title, year)
            if (popcornResult?.imdbId != null) {
                android.util.Log.d("MovieRepository", "Resolved IMDB ID for '$title' via Popcorn: ${popcornResult.imdbId}")
                return@withContext popcornResult.imdbId
            }
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "Popcorn IMDB resolve failed for '$title': ${e.message}")
        }

        // Fallback: TMDB search + external IDs
        try {
            val search = apiService.searchTvShows(query = title)
            val best = search.results.firstOrNull { show ->
                val y = show.firstAirDate?.take(4)
                year == null || y == year
            } ?: search.results.firstOrNull()

            if (best != null) {
                val externalIds = apiService.getTvShowExternalIds(best.id)
                externalIds.imdbId.also { id ->
                    android.util.Log.d("MovieRepository", "Resolved IMDB ID for '$title' via TMDB: $id")
                }
            } else {
                android.util.Log.w("MovieRepository", "No TMDB match for TV show: $title")
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "Failed to resolve IMDB ID for '$title': ${e.message}")
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Watch Options (Streaming Providers + Torrent)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Get all watch options for a movie: streaming providers from TMDB + torrent sources.
     * Streaming providers are fetched from TMDB's watch/providers endpoint (powered by JustWatch).
     * Torrent source is fetched in parallel via the existing getTorrentInfo chain.
     *
     * @param tmdbId TMDB movie ID (for watch provider lookup)
     * @param title Movie title (for torrent search)
     * @param year Release year (for torrent search)
     * @param country ISO 3166-1 country code (default "US")
     * @return List of WatchOption sorted: FREE → SUBSCRIPTION → ADS → RENT → BUY → TORRENT
     */
    suspend fun getMovieWatchOptions(
        tmdbId: Int,
        title: String,
        year: String?,
        country: String = "US"
    ): List<WatchOption> = withContext(Dispatchers.IO) {
        val options = mutableListOf<WatchOption>()

        // Fetch streaming providers and torrent in parallel
        coroutineScope {
            val providersDeferred = async {
                try {
                    val response = apiService.getMovieWatchProviders(tmdbId)
                    response.results?.get(country)
                } catch (e: Exception) {
                    android.util.Log.e("MovieRepository", "Failed to fetch watch providers for movie $tmdbId", e)
                    null
                }
            }

            val torrentDeferred = async {
                try {
                    getTorrentInfo(title, year)
                } catch (e: Exception) {
                    android.util.Log.e("MovieRepository", "Failed to fetch torrent for $title", e)
                    null
                }
            }

            val countryProviders = providersDeferred.await()
            val torrentInfo = torrentDeferred.await()

            // Convert streaming providers to WatchOptions
            if (countryProviders != null) {
                fun addProviders(entries: List<WatchProviderEntry>?, type: WatchOptionType) {
                    entries?.forEach { entry ->
                        val deepLink = StreamingAppRegistry.buildDeepLink(entry.providerId, title, tmdbId, true)
                        options.add(
                            WatchOption(
                                name = entry.providerName,
                                type = type,
                                logoPath = entry.logoPath,
                                packageName = StreamingAppRegistry.getPackageName(entry.providerId),
                                deepLinkUrl = deepLink,
                                justWatchLink = countryProviders.link,
                                magnetUrl = null,
                                quality = null,
                                seeds = null,
                                provider = null,
                                displayPriority = entry.displayPriority
                            )
                        )
                    }
                }

                addProviders(countryProviders.free, WatchOptionType.FREE)
                addProviders(countryProviders.flatrate, WatchOptionType.SUBSCRIPTION)
                addProviders(countryProviders.ads, WatchOptionType.ADS)
                addProviders(countryProviders.rent, WatchOptionType.RENT)
                addProviders(countryProviders.buy, WatchOptionType.BUY)
            }

            // Add torrent option
            if (torrentInfo != null) {
                options.add(
                    WatchOption(
                        name = "Torrent${torrentInfo.quality?.let { " ($it)" } ?: ""}",
                        type = WatchOptionType.TORRENT,
                        logoPath = null,
                        packageName = null,
                        deepLinkUrl = null,
                        justWatchLink = null,
                        magnetUrl = torrentInfo.magnetUrl,
                        quality = torrentInfo.quality,
                        seeds = torrentInfo.seeds,
                        provider = torrentInfo.provider,
                        displayPriority = 999
                    )
                )
            }
        }

        // Sort: FREE first, then SUBSCRIPTION, ADS, RENT, BUY, TORRENT last
        // Within each type, sort by display priority
        options.sortedWith(
            compareBy<WatchOption> { option ->
                when (option.type) {
                    WatchOptionType.FREE -> 0
                    WatchOptionType.SUBSCRIPTION -> 1
                    WatchOptionType.ADS -> 2
                    WatchOptionType.RENT -> 3
                    WatchOptionType.BUY -> 4
                    WatchOptionType.TORRENT -> 5
                }
            }.thenBy { it.displayPriority }
        )
    }

    /**
     * Get all watch options for a TV show.
     * @param tmdbId TMDB TV series ID
     * @param title Show title
     * @param year First air date year
     * @param country ISO 3166-1 country code
     */
    suspend fun getTvShowWatchOptions(
        tmdbId: Int,
        title: String,
        year: String?,
        country: String = "US"
    ): List<WatchOption> = withContext(Dispatchers.IO) {
        val options = mutableListOf<WatchOption>()

        // Fetch streaming providers (no torrent for the show itself — that's per-episode)
        try {
            val response = apiService.getTvWatchProviders(tmdbId)
            val countryProviders = response.results?.get(country)

            if (countryProviders != null) {
                fun addProviders(entries: List<WatchProviderEntry>?, type: WatchOptionType) {
                    entries?.forEach { entry ->
                        val deepLink = StreamingAppRegistry.buildDeepLink(entry.providerId, title, tmdbId, false)
                        options.add(
                            WatchOption(
                                name = entry.providerName,
                                type = type,
                                logoPath = entry.logoPath,
                                packageName = StreamingAppRegistry.getPackageName(entry.providerId),
                                deepLinkUrl = deepLink,
                                justWatchLink = countryProviders.link,
                                magnetUrl = null,
                                quality = null,
                                seeds = null,
                                provider = null,
                                displayPriority = entry.displayPriority
                            )
                        )
                    }
                }

                addProviders(countryProviders.free, WatchOptionType.FREE)
                addProviders(countryProviders.flatrate, WatchOptionType.SUBSCRIPTION)
                addProviders(countryProviders.ads, WatchOptionType.ADS)
                addProviders(countryProviders.rent, WatchOptionType.RENT)
                addProviders(countryProviders.buy, WatchOptionType.BUY)
            }
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "Failed to fetch watch providers for TV show $tmdbId", e)
        }

        // Sort same as movies
        options.sortedWith(
            compareBy<WatchOption> { option ->
                when (option.type) {
                    WatchOptionType.FREE -> 0
                    WatchOptionType.SUBSCRIPTION -> 1
                    WatchOptionType.ADS -> 2
                    WatchOptionType.RENT -> 3
                    WatchOptionType.BUY -> 4
                    WatchOptionType.TORRENT -> 5
                }
            }.thenBy { it.displayPriority }
        )
    }

    /**
     * Search TMDB to resolve a title+year to a TMDB ID.
     * Tries exact match by title+year, falls back to first result.
     */
    suspend fun searchTmdbIdByTitle(title: String, year: String?, isTvMode: Boolean): Int? = withContext(Dispatchers.IO) {
        try {
            if (isTvMode) {
                val response = apiService.searchTvShows(query = title)
                val match = if (year != null) {
                    response.results.firstOrNull { it.name.equals(title, ignoreCase = true) && it.firstAirDate?.startsWith(year) == true }
                        ?: response.results.firstOrNull()
                } else {
                    response.results.firstOrNull()
                }
                match?.id
            } else {
                val response = apiService.searchMovies(query = title)
                val match = if (year != null) {
                    response.results.firstOrNull { it.title.equals(title, ignoreCase = true) && it.releaseDate?.startsWith(year) == true }
                        ?: response.results.firstOrNull()
                } else {
                    response.results.firstOrNull()
                }
                match?.id
            }
        } catch (e: Exception) {
            android.util.Log.e("MovieRepository", "Failed to search TMDB for: $title", e)
            null
        }
    }

    /**
     * Get torrent information for a movie by title and year.
     * Tries multiple sources (YTS, Popcorn API) with fallback.
     * Prefers smallest file size for faster streaming.
     */
    suspend fun getTorrentInfo(title: String, year: String?): TorrentInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("MovieRepository", "Searching torrent for: $title ($year)")
        
        // Try YTS first (generally smaller files, better for streaming)
        try {
            val ytsMovie = ytsApi.searchMovie(title, year)
            if (ytsMovie != null) {
                val torrent = ytsApi.getSmallestTorrent(ytsMovie)
                if (torrent != null && (torrent.seeds ?: 0) > 0) {
                    android.util.Log.d("MovieRepository", "Found YTS torrent: ${torrent.quality} (${torrent.size}) with ${torrent.seeds} seeds")
                    return@withContext torrent
                }
            }
            android.util.Log.d("MovieRepository", "No YTS torrent found, trying Popcorn API...")
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "YTS search failed: ${e.message}")
        }
        
        // Fallback to Popcorn API
        try {
            val popcornMovie = popcornApi.searchMovie(title, year)
            if (popcornMovie != null) {
                val torrent = popcornApi.getSmallestTorrent(popcornMovie)
                if (torrent != null) {
                    android.util.Log.d("MovieRepository", "Found Popcorn torrent: ${torrent.quality} (${torrent.size}) with ${torrent.seeds} seeds")
                    return@withContext torrent
                }
            }
            android.util.Log.d("MovieRepository", "No Popcorn API torrent found")
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "Popcorn API search failed: ${e.message}")
        }

        // Fallback to PirateBay
        try {
            val pirateBayTorrent = pirateBayApi.searchMovie(title, year)
            if (pirateBayTorrent != null) {
                android.util.Log.d("MovieRepository", "Found PirateBay torrent: ${pirateBayTorrent.quality} (${pirateBayTorrent.size}) with ${pirateBayTorrent.seeds} seeds")
                return@withContext pirateBayTorrent
            }
            android.util.Log.d("MovieRepository", "No PirateBay torrent found, trying TorrentGalaxy...")
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "PirateBay search failed: ${e.message}")
        }

        // Fallback to TorrentGalaxy
        try {
            val tgxTorrent = torrentGalaxyApi.searchMovie(title, year)
            if (tgxTorrent != null) {
                android.util.Log.d("MovieRepository", "Found TorrentGalaxy torrent: ${tgxTorrent.quality} (${tgxTorrent.size}) with ${tgxTorrent.seeds} seeds")
                return@withContext tgxTorrent
            }
            android.util.Log.d("MovieRepository", "No TorrentGalaxy torrent found, trying 1337x...")
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "TorrentGalaxy search failed: ${e.message}")
        }

        // Fallback to 1337x
        try {
            val leetxTorrent = leetxApi.searchMovie(title, year)
            if (leetxTorrent != null) {
                android.util.Log.d("MovieRepository", "Found 1337x torrent: ${leetxTorrent.quality} (${leetxTorrent.size}) with ${leetxTorrent.seeds} seeds")
                return@withContext leetxTorrent
            }
            android.util.Log.d("MovieRepository", "No 1337x torrent found")
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "1337x search failed: ${e.message}")
        }

        android.util.Log.w("MovieRepository", "No torrent source found for: $title")
        null
    }
    
    /**
     * Get TV show details from Popcorn Time API by IMDB ID.
     * Returns show with all episodes and their torrent links.
     */
    suspend fun getTvShowTorrentDetails(imdbId: String) = popcornTvApi.getShowDetails(imdbId)
    
    /**
     * Search for a TV show on Popcorn Time API by title.
     * Returns basic show info that can be used to get full details.
     */
    suspend fun searchTvShowTorrent(title: String, year: String? = null) = popcornTvApi.searchShow(title, year)
    
    /**
     * Get torrent info for a specific TV show episode.
     * Tries Popcorn TV API first, falls back to EZTV.
     * @param showTitle The title of the TV show (used to search if imdbId not provided)
     * @param imdbId Optional IMDB ID for direct lookup
     * @param season Season number
     * @param episode Episode number
     * @param preferredQuality Preferred quality (720p, 1080p, 480p)
     */
    suspend fun getTvEpisodeTorrentInfo(
        showTitle: String,
        imdbId: String? = null,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p"
    ): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("MovieRepository", "Searching TV torrent for: $showTitle S${season}E${episode}")

        // Resolve IMDB ID upfront (needed by both APIs)
        val resolvedImdbId = imdbId
            ?: popcornTvApi.searchShow(showTitle)?.imdbId
            ?: resolveImdbIdForTvShow(showTitle, null)

        // Query both APIs in parallel and pick the best result
        coroutineScope {
            val popcornJob = async {
                try {
                    val showDetails = if (resolvedImdbId != null) {
                        popcornTvApi.getShowDetails(resolvedImdbId)
                    } else null

                    if (showDetails != null) {
                        val torrent = popcornTvApi.getEpisodeTorrent(showDetails, season, episode, preferredQuality)
                        if (torrent != null) {
                            android.util.Log.d("MovieRepository", "Found Popcorn TV torrent: ${torrent.quality} with ${torrent.seeds} seeds")
                        }
                        torrent
                    } else null
                } catch (e: Exception) {
                    android.util.Log.w("MovieRepository", "Popcorn TV episode lookup failed: ${e.message}")
                    null
                }
            }

            val eztvJob = async {
                try {
                    val eztvImdbId = resolvedImdbId
                    if (eztvImdbId != null) {
                        val torrent = eztvApi.getEpisodeTorrent(eztvImdbId, season, episode, preferredQuality, showTitle)
                        if (torrent != null) {
                            android.util.Log.d("MovieRepository", "Found EZTV torrent: ${torrent.quality} with ${torrent.seeds} seeds")
                        }
                        torrent
                    } else null
                } catch (e: Exception) {
                    android.util.Log.w("MovieRepository", "EZTV episode lookup failed: ${e.message}")
                    null
                }
            }

            val popcornResult = popcornJob.await()
            val eztvResult = eztvJob.await()

            // Pick the best torrent (most seeds)
            val best = listOfNotNull(popcornResult, eztvResult).maxByOrNull { it.seeds }
            if (best != null) {
                android.util.Log.d("MovieRepository", "Best torrent for $showTitle S${season}E${episode}: ${best.provider} ${best.quality} (${best.seeds} seeds)")
            } else {
                android.util.Log.w("MovieRepository", "No torrent found for $showTitle S${season}E${episode}")
            }
            best
        }
    }
    
    /**
     * Get available seasons for a TV show.
     * Aggregates from ALL torrent APIs (Popcorn TV + EZTV) and merges/deduplicates.
     */
    suspend fun getTvShowSeasons(imdbId: String): List<Int> = withContext(Dispatchers.IO) {
        val allSeasons = mutableSetOf<Int>()

        // Query both APIs in parallel
        coroutineScope {
            val popcornJob = async {
                try {
                    val showDetails = popcornTvApi.getShowDetails(imdbId)
                    if (showDetails != null) {
                        val seasons = popcornTvApi.getSeasons(showDetails)
                        android.util.Log.d("MovieRepository", "Popcorn TV seasons for $imdbId: $seasons")
                        seasons
                    } else emptyList()
                } catch (e: Exception) {
                    android.util.Log.w("MovieRepository", "Popcorn TV seasons lookup failed: ${e.message}")
                    emptyList()
                }
            }

            val eztvJob = async {
                try {
                    val seasons = eztvApi.getSeasons(imdbId)
                    android.util.Log.d("MovieRepository", "EZTV seasons for $imdbId: $seasons")
                    seasons
                } catch (e: Exception) {
                    android.util.Log.w("MovieRepository", "EZTV seasons lookup failed: ${e.message}")
                    emptyList()
                }
            }

            allSeasons.addAll(popcornJob.await())
            allSeasons.addAll(eztvJob.await())
        }

        android.util.Log.d("MovieRepository", "Aggregated seasons for $imdbId: ${allSeasons.sorted()}")
        allSeasons.sorted()
    }
    
    /**
     * Get episodes for a specific season of a TV show.
     * Aggregates from ALL torrent APIs (Popcorn TV + EZTV) and merges by episode number.
     * Episodes found in multiple sources get combined torrent info.
     */
    suspend fun getTvShowEpisodes(imdbId: String, season: Int): List<PopcornEpisode> = withContext(Dispatchers.IO) {
        // Query both APIs in parallel
        val (popcornEpisodes, eztvEpisodes) = coroutineScope {
            val popcornJob = async {
            try {
                val showDetails = popcornTvApi.getShowDetails(imdbId)
                if (showDetails != null) {
                    val episodes = popcornTvApi.getEpisodesForSeason(showDetails, season)
                    android.util.Log.d("MovieRepository", "Popcorn TV found ${episodes.size} episodes for S$season")
                    episodes
                } else emptyList()
            } catch (e: Exception) {
                android.util.Log.w("MovieRepository", "Popcorn TV episodes lookup failed: ${e.message}")
                emptyList()
            }
        }

        val eztvJob = async {
            try {
                val eztvEpisodes = eztvApi.getEpisodesForSeason(imdbId, season)
                android.util.Log.d("MovieRepository", "EZTV found ${eztvEpisodes.size} episodes for S$season")
                eztvEpisodes.map { eztvEp ->
                    PopcornEpisode(
                        tvdbId = null,
                        season = season,
                        episode = eztvEp.episode,
                        title = eztvEp.title?.let { t ->
                            t.substringAfterLast("]").substringBefore("[").trim()
                                .ifEmpty { "Episode ${eztvEp.episode}" }
                        } ?: "Episode ${eztvEp.episode}",
                        overview = null,
                        firstAired = null,
                        torrents = mapOf(
                            "0" to PopcornEpisodeTorrent(
                                provider = "EZTV",
                                seeds = eztvEp.bestSeeds,
                                peers = 0,
                                url = ""
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("MovieRepository", "EZTV episodes lookup failed: ${e.message}")
                emptyList()
            }
        }

            Pair(popcornJob.await(), eztvJob.await())
        }

        // Merge: use a map keyed by episode number, Popcorn data takes priority for metadata
        val mergedMap = mutableMapOf<Int, PopcornEpisode>()

        // Add Popcorn episodes first (they have better metadata: titles, overviews, etc.)
        for (ep in popcornEpisodes) {
            val epNum = ep.episode ?: continue
            mergedMap[epNum] = ep
        }

        // Merge EZTV episodes: add new ones, enrich existing with EZTV torrents
        for (ep in eztvEpisodes) {
            val epNum = ep.episode ?: continue
            val existing = mergedMap[epNum]
            if (existing == null) {
                // Episode only in EZTV — add it
                mergedMap[epNum] = ep
            } else {
                // Episode in both — merge torrents (add EZTV torrents under "eztv_*" keys)
                val mergedTorrents = (existing.torrents ?: emptyMap()).toMutableMap()
                ep.torrents?.entries?.forEach { (quality, torrent) ->
                    val eztvKey = "eztv_$quality"
                    mergedTorrents[eztvKey] = torrent
                }
                mergedMap[epNum] = existing.copy(torrents = mergedTorrents)
            }
        }

        val result = mergedMap.values.sortedBy { it.episode }
        android.util.Log.d("MovieRepository", "Aggregated ${result.size} episodes for S$season (Popcorn: ${popcornEpisodes.size}, EZTV: ${eztvEpisodes.size})")
        result
    }
    
    /**
     * Get IMDB ID for a TV show from TMDB.
     * @param tmdbId The TMDB series ID
     * @return IMDB ID if available (e.g., "tt1234567")
     */
    suspend fun getTvShowImdbId(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val externalIds = apiService.getTvShowExternalIds(tmdbId)
            externalIds.imdbId.also { imdbId ->
                android.util.Log.d("MovieRepository", "Got IMDB ID for TMDB $tmdbId: $imdbId")
            }
        } catch (e: Exception) {
            android.util.Log.w("MovieRepository", "Failed to get IMDB ID for TMDB $tmdbId: ${e.message}")
            null
        }
    }
    
    /**
     * Format a list of movies for LLM exclusion.
     * Includes title and year for accurate matching.
     */
    private fun formatExcludedMovies(movies: List<Movie>): List<String> {
        return movies.map { movie ->
            val year = movie.releaseDate?.take(4) ?: ""
            "${movie.title}${if (year.isNotBlank()) " ($year)" else ""}".trim()
        }.filter { it.isNotBlank() }
    }
}
