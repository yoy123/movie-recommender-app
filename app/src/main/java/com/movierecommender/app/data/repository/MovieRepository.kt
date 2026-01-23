package com.movierecommender.app.data.repository

import com.movierecommender.app.BuildConfig
import com.movierecommender.app.data.local.MovieDao
import com.movierecommender.app.data.model.Genre
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.MovieDetails
import com.movierecommender.app.data.model.MovieResponse
import com.movierecommender.app.data.remote.TmdbApiService
import com.movierecommender.app.data.remote.ImdbScraperService
import com.movierecommender.app.data.remote.PopcornApiService
import com.movierecommender.app.data.remote.YtsApiService
import com.movierecommender.app.data.remote.TorrentInfo
import com.movierecommender.app.data.model.Video
import com.movierecommender.app.data.remote.LlmRecommendationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
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
    private val imdbScraper: ImdbScraperService = ImdbScraperService.create(),
    private val popcornApi: PopcornApiService = PopcornApiService(),
    private val ytsApi: YtsApiService = YtsApiService()
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
        
        android.util.Log.w("MovieRepository", "No torrent source found for: $title")
        null
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
