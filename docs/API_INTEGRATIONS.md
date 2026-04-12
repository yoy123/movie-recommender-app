# API_INTEGRATIONS.md

**Last Updated:** 2026-01-19
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

This document comprehensively maps all external API integrations: endpoints, authentication, throttling, caching, error handling, and code locations.

---

## 1. TMDB (The Movie Database)

### Purpose (TMDB)

Primary source for movie metadata (genres, titles, posters, trailers, ratings, similar movies).

### TMDB Base URL

`https://api.themoviedb.org/3/`

### Authentication (TMDB)

**Method:** API key via query parameter
**Key Location:** `local.properties` → `TMDB_API_KEY`
**Read Location:** `BuildConfig.TMDB_API_KEY` (compile-time constant)
**Failure Mode:** If key missing/invalid → all TMDB calls fail → app unusable

**Code Evidence:**

- [build.gradle.kts:34](../app/build.gradle.kts#L34) - Loads from `local.properties`
- [TmdbApiService.kt:19](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L19) - Uses `BuildConfig.TMDB_API_KEY` as default param

### Endpoints Used

#### 1.1 Get Genre List

**Endpoint:** `GET /genre/movie/list`
**Params:**

- `api_key`: TMDB API key
- `language`: `en-US` (hardcoded)

**Response:** `GenreResponse` → `List<Genre>`
**Usage:** Populate genre selection screen
**Code:** [TmdbApiService.kt:23](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L23)

---

#### 1.2 Discover Movies by Genre

**Endpoint:** `GET /discover/movie`
**Params:**

- `api_key`: TMDB API key
- `with_genres`: Genre ID (e.g., 28 for Action)
- `sort_by`: `popularity.desc` or `vote_average.desc` (default: popularity)
- `page`: Page number (1-indexed, max 500 pages per TMDB limit)
- `language`: `en-US`

**Response:** `MovieResponse` → `List<Movie>` + paging metadata (`page`, `totalPages`, `totalResults`)
**Usage:**

- Load movies in MovieSelectionScreen
- Build TMDB candidate pool for LLM rerank

**Code:** [TmdbApiService.kt:30](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L30)

**Paging Strategy:**

- `MovieViewModel.loadMoviesByGenre(page)` → tracks `genrePage`, `genreTotalPages`, `canLoadMoreGenreMovies`
- Infinite scroll → `loadNextGenreMoviesPage()` increments page
- Results merged into `movies` list (deduped by `id`)

**Code:** [MovieViewModel.kt:193](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L193)

---

#### 1.3 Search Movies

**Endpoint:** `GET /search/movie`
**Params:**

- `api_key`: TMDB API key
- `query`: User search term
- `page`: Page number (default: 1)
- `language`: `en-US`

**Response:** `MovieResponse` → `List<Movie>`
**Usage:**

- Search bar in MovieSelectionScreen and FavoritesScreen
- LLM validation (genre constraint check)
- Fetch movie details for recommendations

**Code:** [TmdbApiService.kt:57](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L57)

---

#### 1.4 Get Movie Recommendations

**Endpoint:** `GET /movie/{movie_id}/recommendations`
**Params:**

- `api_key`: TMDB API key
- `movie_id`: TMDB movie ID
- `page`: Page number (default: 1)
- `language`: `en-US`

**Response:** `MovieResponse` → `List<Movie>`
**Usage:**

- Build TMDB candidate pool for LLM
- Fallback recommendations when LLM fails

**Code:** [TmdbApiService.kt:41](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L41)

---

#### 1.5 Get Similar Movies

**Endpoint:** `GET /movie/{movie_id}/similar`
**Params:**

- `api_key`: TMDB API key
- `movie_id`: TMDB movie ID
- `page`: Page number (default: 1)
- `language`: `en-US`

**Response:** `MovieResponse` → `List<Movie>`
**Usage:** Same as recommendations endpoint (candidate pool + fallback)

**Code:** [TmdbApiService.kt:49](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L49)

---

#### 1.6 Get Movie Details

**Endpoint:** `GET /movie/{movie_id}`
**Params:**

- `api_key`: TMDB API key
- `movie_id`: TMDB movie ID
- `append_to_response`: `keywords` (fetch keywords alongside details)
- `language`: `en-US`

**Response:** `MovieDetails` → includes `genres`, `runtime`, `imdbId`, `keywords`
**Usage:**

- Fetch IMDB ID for trailer/rating lookups
- Genre validation (fallback if `genre_ids` empty in search results)

**Code:** [TmdbApiService.kt:66](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L66)

---

#### 1.7 Get Movie Videos (Trailers)

**Endpoint:** `GET /movie/{movie_id}/videos`
**Params:**

- `api_key`: TMDB API key
- `movie_id`: TMDB movie ID
- `language`: `en-US`

**Response:** `VideoResponse` → `List<Video>` with YouTube keys
**Usage:** Fetch trailer URL for `TrailerScreen`

**Code:** [TmdbApiService.kt:75](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L75)

**Video Filtering:**

- Prefers `type == "Trailer"` and `site == "YouTube"`
- Constructs URL: `https://www.youtube.com/watch?v={key}`

---

#### 1.8 Get Movie Watch Providers

**Endpoint:** `GET /movie/{movie_id}/watch/providers`
**Params:**

- `api_key`: TMDB API key
- `movie_id`: TMDB movie ID

**Response:** `WatchProviderResponse` → `Map<String, CountryWatchProviders>` keyed by country code (e.g., "US")
**Usage:**

- Fetch streaming/rent/buy options for a movie
- Populate WatchOptionsDialog with streaming app options alongside torrent
- Data sourced from JustWatch (no extra API required)

**Code:** [TmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt) `getMovieWatchProviders()`

---

#### 1.9 Get TV Show Watch Providers

**Endpoint:** `GET /tv/{series_id}/watch/providers`
**Params:**

- `api_key`: TMDB API key
- `series_id`: TMDB TV show ID

**Response:** `WatchProviderResponse` → `Map<String, CountryWatchProviders>` keyed by country code
**Usage:**

- Same as 1.8 but for TV shows
- Combined with "Browse Episodes (Torrent)" option in WatchOptionsDialog

**Code:** [TmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt) `getTvWatchProviders()`

---

### Rate Limiting (TMDB)

**TMDB Limit:** 40 requests per 10 seconds (free tier)
**Current Handling:** None (no retry logic or exponential backoff)
**Risk:** Burst requests (e.g., rapid genre switching) may hit rate limit → HTTP 429 → error shown to user

**TODO** Implement request queue or debouncing for rapid calls.

---

### Caching

**In-Memory:** None
**Disk:** None
**Strategy:** Every request is a fresh network call

**Room DB Caching:**

- Selected movies cached locally (`isSelected = true`)
- Favorites cached locally (`isFavorite = true`)
- Recommended movies cached locally (`isRecommended = true`)

No TMDB response caching; all movie lists are ephemeral.

---

### SSL/TLS

**Production:** Standard SSL with certificate validation
**Debug:** Insecure SSL (accepts all certificates) to avoid emulator issues

**Code:** [TmdbApiService.kt:91](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L91)

```kotlin
val clientBuilder = if (BuildConfig.DEBUG) {
    buildInsecureClientBuilder()
} else {
    OkHttpClient.Builder()

### Image Base URLs

**TMDB Image CDN:** `https://image.tmdb.org/t/p/`
**Sizes:** `w500` (posters), `original` (backdrops)
**Construction:** `baseUrl + size + posterPath`

**Code:** UI screens use Coil library to load:

```kotlin
AsyncImage(
    model = "https://image.tmdb.org/t/p/w500${movie.posterPath}",
    contentDescription = movie.title
### Error Handling (TMDB)

**Network Failure:** Retrofit throws exception → caught in `repository.getMoviesByGenre()` → emits `Resource.Error`
**Invalid API Key:** TMDB returns HTTP 401 → same flow as network failure
**Empty Results:** Empty list returned → UI shows "No movies found"

**Code:** [MovieRepository.kt:98](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L98)

```kotlin
suspend fun getMoviesByGenre(genreId: Int, page: Int = 1): Flow<Resource<List<Movie>>> = flow {
    emit(Resource.Loading())
    try {
        val response = apiService.getMoviesByGenre(genreId = genreId, page = page)
        emit(Resource.Success(response.results))
    } catch (e: Exception) {
        emit(Resource.Error(e.localizedMessage ?: "An error occurred"))
```

---

## 2. OpenAI (GPT-4o-mini)

### Purpose (OpenAI)

Generate personalized movie recommendations based on user's selected movies and preferences.

### OpenAI Base URL

`https://api.openai.com/v1/chat/completions`

### Authentication (OpenAI)

**Method:** Bearer token in `Authorization` header
**Key Location:** `local.properties` → `OPENAI_API_KEY`
**Read Location:** `BuildConfig.OPENAI_API_KEY` (compile-time constant)
**Failure Mode:** If key missing/invalid → LLM calls fail → falls back to TMDB recommendations

**Code Evidence:**

- [build.gradle.kts:36](../app/build.gradle.kts#L36) - Loads from `local.properties`
- [MovieRepository.kt:34](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L34) - Reads `BuildConfig.OPENAI_API_KEY`

---

### Model

**Model:** `gpt-4o-mini`
**Max Tokens:** 4096 (default, configurable in LlmRecommendationService)
**Temperature:** 0.6 (creative) → 0.3 (strict) on retry

**Code:** [LlmRecommendationService.kt:70](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L70)

---

### Request Structure

**Method:** POST
**Headers:**

- `Authorization: Bearer {OPENAI_API_KEY}`
- `Content-Type: application/json`

**Body:**

```json
  "messages": [
      "role": "system",

      "content": "You are a film curator..."
      "content": "[Prompt with selected movies + preferences + exclusions]"
  "temperature": 0.6,
  "frequency_penalty": 1.0,
  "presence_penalty": 0.6,

}
```

**Code:** [LlmRecommendationService.kt:230](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L230)

---

### Prompt Construction

**Two Modes:**

#### Mode 1: Open Prompt (Free-form LLM selection)

**When:** TMDB candidate pool < 25 titles
**Prompt Structure:**

1. Genre constraint (or favorites mode detection)
2. Selected movie titles (1–5) with years
3. Active preferences (6 sliders + toggles)
4. Exclusion list (selected + favorites + recommended + session retries)
5. Output format (analysis + 15 numbered recommendations)

**Code:** [LlmRecommendationService.kt:233](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L233)

---

#### Mode 2: Candidate Rerank (Bounded LLM selection)

**When:** TMDB candidate pool ≥ 25 titles
**Prompt Structure:**

1. Genre constraint
2. Selected movie titles
3. Active preferences
4. **Candidate list** (80 titles from TMDB, formatted as "Title (YYYY)")
5. Hard constraint: **"ONLY select from the candidate list"**

**Code:** [LlmRecommendationService.kt:460](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L460)

**Why Bounded Mode:**

- Prevents hallucinations (LLM inventing fake titles)
- Improves genre adherence (candidates pre-filtered by TMDB)
- Enables strict validation (every recommended title must exist in candidate list)

---

### Retry Strategy

**Attempts:** 2

1. **Attempt 1:** Creative (temp=0.6, frequency_penalty=1.0)
2. **Attempt 2:** Strict (temp=0.3 + extra instructions)
**Extra Instructions on Retry:**

```text
STRICT RETRY (FINAL):
The previous response failed validation. Follow the rules exactly:
- Start with a 3-sentence analysis...
- Output EXACTLY 15 recommendations, numbered 1..15
- ...
```

---

### Response Validation

**Three Checks:**

#### 1. Structure Validation

**Required:**

- "Analysis:" section exists
- Exactly 15 numbered items (1–15)
- Regex: `^\s*(\d{1,2})\.\s*`

**Code:** [MovieRepository.kt:519](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L519)

---

#### 2. Genre Constraint Validation

**When:** Not in favorites mode AND `genreId` provided
**Process:**

- Parse numbered recommendations → extract titles + years
- For each title: `TmdbApiService.searchMovies(title)` → get `genre_ids`
- Check if `requiredGenreId` in `genre_ids`
- Allow max 2 misses (for search ambiguity)

**Code:** [MovieRepository.kt:601](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L601)

**Skip if:** Bounded mode used (candidates already genre-filtered)

---

#### 3. Candidate Constraint Validation

**When:** Bounded mode (candidate list provided to LLM)
**Process:**

- Normalize all candidate titles → remove articles, punctuation, lowercase
- For each recommended title: normalize + check if in candidate set
- Must have 0 misses (strict)

**Code:** [MovieRepository.kt:651](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L651)

---

### Fallback to TMDB

**When:** LLM fails (network error, validation failure, empty response)
**Process:**

- `buildFallbackRecommendations()` called
- Uses TMDB similar/recommendations endpoints
- Ranks by `voteAverage` + `popularity`
- Applies year range filter (if enabled)
- Applies genre filter (if not favorites mode)
- Formats as same structure (analysis + 15 numbered items)

**Code:** [MovieRepository.kt:689](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L689)

---

### Rate Limiting (OpenAI)

**OpenAI Limit:** Varies by tier (free tier: 3 RPM, 40k TPM)
**Current Handling:** None (single request per recommendation generation)
**Risk:** Low (recommendation button has ~5-10 second delay between clicks due to loading state)

---

### Timeout (OpenAI)

**Connect Timeout:** 60 seconds
**Read Timeout:** 60 seconds

**Code:** [LlmRecommendationService.kt:16](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L16)

---

### Privacy

**Data Sent to OpenAI:**

- Selected movie titles (1–5) with years
- Genre name (e.g., "Comedy")
- Preference slider values (indie, popularity, tone, etc.)
- Exclusion list (favorites + recommended titles)

**NOT Sent:**

- User name (stored locally only)
- Viewing history
- Device info
- Location

**Mitigation:** All movie data is public (TMDB). No PII transmitted.

---

### Error Handling (YTS)

**Network Failure:** Exception caught → returns `Result.failure(e)`
**Invalid API Key:** OpenAI returns HTTP 401 → same as network failure
**Rate Limit Hit:** HTTP 429 → treated as error → fallback to TMDB
**Invalid JSON Response:** Parsing fails → returns empty string → fallback to TMDB

**Code:** [LlmRecommendationService.kt:130](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L130)

---

## 3. OMDb (Open Movie Database)

### Purpose (OMDb - Deprecated)

**Originally:** Fetch IMDB ratings
**Current Status:** **UNUSED IN UI**

### OMDb Base URL

`https://www.omdbapi.com`

### Authentication (OMDb)

**Method:** API key via query parameter
**Key Location:** `local.properties` → `OMDB_API_KEY`
**Read Location:** `BuildConfig.OMDB_API_KEY`
**Current Value:** Empty string in `local.properties`

**Code:** [build.gradle.kts:37](../app/build.gradle.kts#L37)

---

### Endpoint

`GET /?t={title}&y={year}&apikey={key}`

**Response:** `OmdbResponse` → `imdbRating` (string, e.g., "7.5")

**Code:** [OmdbApiService.kt:15](../app/src/main/java/com/movierecommender/app/data/remote/OmdbApiService.kt#L15)

---

---

### Integration Status

**Service Created:** ✅ Yes
**Repository Method:** ✅ Yes (disabled)
**UI Integration:** ❌ No
**Config Value:** ❌ Empty (not required)

**Recommendation:** Remove OMDb integration entirely OR document as optional feature for future use.

---

## 4. YTS (YIFY Torrents)

### Purpose (YTS)

Fetch torrent metadata for movie streaming (magnet URLs, seeds, quality).

### YTS Base URL

`https://yts.lt/api/v2`

### Authentication (YTS)

None (public API)

---

### Endpoint: Search Movie

`GET /list_movies.json?query_term={title}&year={year}&limit=1`

**Response:** `YtsResponse` → `List<YtsMovie>`
**Usage:** Find torrent for "Watch Now" feature

**Code:** [YtsApiService.kt:53](../app/src/main/java/com/movierecommender/app/data/remote/YtsApiService.kt#L53)

---

### Torrent Selection

**Strategy:** Select smallest torrent (fastest streaming)
**Process:**

- Parse `size` field (e.g., "1.2 GB") → convert to bytes
- Sort by bytes → pick smallest
- Construct magnet URL from hash + trackers

**Code:** [YtsApiService.kt:130](../app/src/main/java/com/movierecommender/app/data/remote/YtsApiService.kt#L130)

---

### Magnet URL Construction

**Format:** `magnet:?xt=urn:btih:{hash}&dn={movieName}&tr={tracker1}&tr={tracker2}...`

**Trackers:** Hardcoded list of 8 trackers
**Code:** [YtsApiService.kt:22](../app/src/main/java/com/movierecommender/app/data/remote/YtsApiService.kt#L22)

---

### Timeout (YTS)

**Connect:** 15 seconds
**Read:** 15 seconds

**Code:** [YtsApiService.kt:28](../app/src/main/java/com/movierecommender/app/data/remote/YtsApiService.kt#L28)

---

### Error Handling (OpenAI)

**No Results:** Returns `null` → fallback to Popcorn API
**Network Failure:** Exception logged → fallback to Popcorn API

---

## 5. Popcorn Time API

### Purpose (Popcorn Time)

Fallback torrent source if YTS fails.

### Popcorn Base URL

**Mirrors:** 4 mirrors tried in order (fusme.link, jfper.link, uxert.link, yrkde.link)

### Authentication (Popcorn)

None (public API)

---

### Endpoint: List Movies

`GET /movies/{page}`

**Response:** `List<PopcornMovie>`
**Usage:** Search through paginated movie list for match

**Code:** [PopcornApiService.kt:45](../app/src/main/java/com/movierecommender/app/data/remote/PopcornApiService.kt#L45)

---

### Search Strategy

**Process:**

- Fetch pages 1–10 (500 movies total)
- For each page: try exact title + year match
- If not found: try partial title match
- Return first match or `null`

**Code:** [PopcornApiService.kt:47](../app/src/main/java/com/movierecommender/app/data/remote/PopcornApiService.kt#L47)

**Limitation:** Slow (multiple sequential API calls). May take 5–10 seconds.

---

### Popcorn Torrent Selection

**Strategy:** Same as YTS (smallest torrent)
**Field:** `torrents["en"][quality].size` or `.filesize`

**Code:** [PopcornApiService.kt:118](../app/src/main/java/com/movierecommender/app/data/remote/PopcornApiService.kt#L118)

---

### Timeout (Popcorn)

**Connect:** 15 seconds
**Read:** 15 seconds

**Code:** [PopcornApiService.kt:26](../app/src/main/java/com/movierecommender/app/data/remote/PopcornApiService.kt#L26)

---

### Mirror Fallback

**Process:** If mirror 1 fails → try mirror 2 → try mirror 3 → try mirror 4
**Failure:** All 4 mirrors fail → return `null` → "Watch Now" disabled

**Code:** [PopcornApiService.kt:97](../app/src/main/java/com/movierecommender/app/data/remote/PopcornApiService.kt#L97)

---

## 6. IMDB Scraper

### Purpose (IMDB Scraper)

**Originally:** Scrape IMDB for trailer URLs
**Current Status:** **UNUSED IN UI**

### Implementation

**Method:** HTTP scraping of IMDB HTML
**Target:** `https://www.imdb.com/title/{imdbId}/`

**Code:** [ImdbScraperService.kt](../app/src/main/java/com/movierecommender/app/data/remote/ImdbScraperService.kt)

---

### Usage

**Repository Method:** `MovieRepository.getImdbTrailerUrl(movieId)`
**Status:** Defined but not called from UI

**Reason:** TMDB provides trailer URLs via `/movie/{id}/videos` endpoint (more reliable).

---

### Wiring Status

**Service Created:** ✅ Yes
**Repository Method:** ✅ Yes
**UI Integration:** ❌ No

**Recommendation:** Remove IMDB scraper OR mark as legacy/unused.

---

## API Dependency Graph

```text
UI (Screens)
    ↓
ViewModel
    ↓
Repository
    ↓
├─ TmdbApiService (primary, always used)
├─ LlmRecommendationService (recommendations only)
├─ YtsApiService (torrent streaming)
├─ PopcornApiService (torrent fallback)
├─ OmdbApiService (UNUSED)
└─ ImdbScraperService (UNUSED)
```

---

## Configuration Summary

| API | Key Location | Build Config Key | Required | Used in UI |
| ----- | ------------- | ------------------ | ---------- | ------------ |
 | TMDB | `local.properties` | `TMDB_API_KEY` | ✅ Yes | ✅ Yes |
 | OpenAI | `local.properties` | `OPENAI_API_KEY` | ✅ Yes | ✅ Yes |
 | OMDb | `local.properties` | `OMDB_API_KEY` | ❌ No | ❌ No |
 | YTS | None (public) | N/A | ❌ No | ✅ Yes |
 | Popcorn | None (public) | N/A | ❌ No | ✅ Yes |
 | IMDB Scraper | None | N/A | ❌ No | ❌ No |

---

## Known Issues and TODOs

### 1. TMDB Rate Limiting

**Issue:** No retry or backoff logic
**Impact:** Burst requests may hit rate limit → HTTP 429 → error
**Fix:** Implement exponential backoff or request queue

### 2. OMDb and IMDB Scraper Unused

**Issue:** Dead code (services defined but not used)
**Impact:** Confusing codebase, unnecessary dependencies
**Fix:** Remove or document as optional

### 3. Popcorn API Slow Search

**Issue:** Sequential page fetching (10 pages) is slow
**Impact:** "Watch Now" button takes 5–10 seconds to appear
**Fix:** Parallel page fetches OR switch to YTS-only

### 4. LLM Validation Expensive

**Issue:** Genre constraint validation requires TMDB search for each recommended title (15 requests)
**Impact:** Adds 2–3 seconds to recommendation generation
**Fix:** Skip validation when using bounded mode (candidates pre-filtered)

### 5. No Caching

**Issue:** Every TMDB request is fresh network call
**Impact:** Slow UI, redundant network usage
**Fix:** Implement in-memory cache for genres, popular movies

---

**Next Review:** When new APIs added, endpoints change, or rate limits encountered.
