# WORKFLOW.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

This document describes the complete user journeys through OpenStream+ for both mobile and Fire TV platforms. All flows are traced through code to navigation routes and screen interactions.

## Primary User Journeys

### Journey 1: First-Time User Onboarding

**Code Evidence:**
- `SettingsRepository.isFirstRun: Flow<Boolean>` (defaults to `true`)
- `MovieViewModel` observes this setting and exposes in `MovieUiState.isFirstRun`

**Flow:**
1. App launches → `MainActivity` (mobile or firestick)
2. First time: `isFirstRun = true` triggers welcome dialog (implementation in screens)
3. User enters name → `SettingsRepository.setUserName(name)` + `setFirstRunDone()`
4. Name stored in DataStore → `userName` flow emits to ViewModel
5. Welcome dialog dismissed → normal flow continues

**Navigation:** No route change; modal dialog within GenreSelectionScreen.

**Files:**
- [SettingsRepository.kt:32](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L32) - `isFirstRun` flow
- [SettingsRepository.kt:58](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L58) - `setFirstRunDone()`
- [MovieViewModel.kt:74](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L74) - observes `isFirstRun`

---

### Journey 2: Genre Selection → Movie Selection → Recommendations

**Start Route:** `genre_selection`

**Flow:**

#### Step 1: Genre Selection
**Screen:** `GenreSelectionScreen`  
**Code:** [GenreSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/GenreSelectionScreen.kt)

**Actions:**
1. Screen loads → `viewModel.loadGenres()` called in `init` block
2. ViewModel → `repository.getGenres()` → `TmdbApiService.getGenres()`
3. TMDB returns genre list → stored in `MovieUiState.genres`
4. UI displays genre grid with cards (Comedy, Drama, Horror, etc.)
5. Special card: "[UserName]'s Favorites" with `genreId = -1`

**User Selects Genre:**
- Click genre → `viewModel.selectGenre(genreId, genreName)`
- ViewModel sets `selectedGenreId`, `selectedGenreName` in state
- If `genreId == -1` → `isFavoritesMode = true` → navigate to `favorites`
- Else → `isFavoritesMode = false` → `loadMoviesByGenre(genreId)` → navigate to `movie_selection`

**Navigation Code:** [AppNavigation.kt:46](../app/src/mobile/java/com/movierecommender/app/ui/navigation/AppNavigation.kt#L46)
```kotlin
composable(Screen.GenreSelection.route) {
    GenreSelectionScreen(
        viewModel = viewModel,
        onGenreSelected = {
            if (viewModel.uiState.value.isFavoritesMode) {
                navController.navigate(Screen.Favorites.route)
            } else {
                navController.navigate(Screen.MovieSelection.route)
            }
        }
    )
}
```

**Error Handling:**
- Network failure → `Resource.Error` → `MovieUiState.error` set → UI shows error message
- User can retry via pull-to-refresh (if implemented) or restart app

---

#### Step 2: Movie Selection (Standard Genre)
**Screen:** `MovieSelectionScreen`  
**Route:** `movie_selection`  
**Code:** [MovieSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt)

**Initial Load:**
1. Screen loads → ViewModel already called `loadMoviesByGenre(genreId)` in `selectGenre()`
2. Repository → `TmdbApiService.getMoviesByGenre(genreId, page=1)`
3. Movies returned → enriched with `isFavorite` flag from local DB
4. Stored in `MovieUiState.movies`
5. UI displays movie grid with poster + title

**User Interactions:**

**A) Search:**
- User types in search bar → `viewModel.searchMovies(query)`
- Debounced input → `repository.searchMovies(query)` → TMDB search API
- Search results replace genre list
- Clear search → `searchMovies("")` → reloads genre movies

**B) Toggle Movie Selection:**
- User clicks checkbox on movie card → `viewModel.toggleMovieSelection(movie)`
- If not selected AND `selectedMovies.size < 5` → `repository.saveSelectedMovie(movie.copy(isSelected = true))`
- If already selected → `repository.removeSelectedMovie(movie)` (sets `isSelected = false`)
- Selected movies appear in bottom sheet / chip row

**C) Infinite Scroll (Paging):**
- User scrolls to bottom → `viewModel.loadNextGenreMoviesPage()`
- Loads next page from TMDB → appends to `movies` list
- Stops when `genrePage >= genreTotalPages`

**D) Add to Favorites:**
- User clicks heart icon → `viewModel.addToFavorites(movie)`
- Repository → `MovieDao.insertMovie(movie.copy(isFavorite = true))`
- UI updates heart icon state

**Navigation to Next Step:**
- "Generate Recommendations" button enabled when 1–5 movies selected
- Click → `onGenerateRecommendations()` → navigate to `recommendations` route
- Before navigation, button triggers `viewModel.generateRecommendations()`

**Navigation Code:** [AppNavigation.kt:58](../app/src/mobile/java/com/movierecommender/app/ui/navigation/AppNavigation.kt#L58)
```kotlin
composable(Screen.MovieSelection.route) {
    MovieSelectionScreen(
        viewModel = viewModel,
        onBackClick = { navController.popBackStack() },
        onGenerateRecommendations = {
            navController.navigate(Screen.Recommendations.route)
        },
        // ... trailer and streaming handlers
    )
}
```

---

#### Step 2-ALT: Favorites Mode
**Screen:** `FavoritesScreen`  
**Route:** `favorites`  
**Code:** [FavoritesScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/FavoritesScreen.kt)

**Flow:**
1. User selected "Dee's Favorites" genre → `isFavoritesMode = true`
2. Navigated to `FavoritesScreen`
3. Screen observes `repository.getFavoriteMovies()` → emits from Room
4. UI displays favorites + search bar (search uses TMDB, not local favorites)
5. User selects 1–5 favorites → same `toggleMovieSelection()` logic
6. Click "Generate Recommendations" → navigate to `recommendations`

**Difference from MovieSelection:**
- Movies come from local DB (favorites collection)
- No genre paging (favorites are always local)
- Search still uses TMDB API (user can add new favorites from search)

---

#### Step 3: Recommendations Generation
**Screen:** `RecommendationsScreen`  
**Route:** `recommendations`  
**Code:** [RecommendationsScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt)

**Initial Load:**
1. Screen loaded → `viewModel.generateRecommendations()` already triggered before navigation
2. ViewModel reads state:
   - `selectedMovies` (1–5 movies)
   - `selectedGenreId`, `selectedGenreName`
   - `isFavoritesMode`
   - All 6 preference sliders + toggles
3. Calls `repository.getRecommendations(...)` with all parameters
4. Repository flow:
   - Fetches favorites + already-recommended from Room
   - Builds exclusion list (selected + favorites + recommended + session retries)
   - If TMDB candidate pool ≥ 25 → calls LLM with bounded candidates
   - Else → calls LLM with open prompt
   - LLM returns text → validates structure (has "Analysis:" section + 15 numbered items)
   - Validates genre constraint (TMDB search for each title → check genre_ids)
   - Validates candidate constraint (if bounded mode)
   - If validation fails → fallback to TMDB-only recommendations
5. Success → `recommendationText` stored in `MovieUiState`
6. UI parses text:
   - Analysis paragraph (top)
   - 15 numbered recommendations with "Why" lines

**UI Display:**
- Scrollable list with analysis at top
- Each recommendation: Movie poster (fetched from TMDB via title search) + title + year + explanation
- Icons: Heart (add to favorites), Play (trailer), Download (torrent stream)

**User Actions:**

**A) Add to Favorites:**
- Click heart → `viewModel.addToFavorites(movie)`
- Same flow as in MovieSelectionScreen

**B) Watch Trailer:**
- Click play icon → fetches trailer URL from TMDB videos endpoint
- Encodes URL in Base64 → navigates to `trailer/{title}/{encodedUrl}`

**C) Watch Now (Torrent Streaming):**
- Click download icon → searches YTS/Popcorn API for torrent
- If found → encodes magnet URL in Base64 → navigates to `streaming/{title}/{encodedMagnet}`

**D) Retry Recommendations:**
- Button visible after recommendations loaded
- Click → `viewModel.retryRecommendations()`
- Extracts titles from current `recommendationText` → adds to exclusion list
- Calls `generateRecommendations(additionalExcludedTitles)` again
- New LLM request excludes previous 15 titles
- Session-level tracking prevents returning same titles across multiple retries

**E) Start Over:**
- Click "Start Over" → `onStartOver()` → `navController.popBackStack(Screen.GenreSelection.route, false)`
- Clears all selections via `viewModel.clearSelections()`

**Navigation Code:** [AppNavigation.kt:95](../app/src/mobile/java/com/movierecommender/app/ui/navigation/AppNavigation.kt#L95)

---

### Journey 3: Trailer Playback

**Route:** `trailer/{title}/{videoUrl}`  
**Screen:** `TrailerScreen`  
**Code:** [TrailerScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/TrailerScreen.kt)

**Flow:**
1. User clicks trailer icon in RecommendationsScreen or MovieSelectionScreen
2. App fetches trailer URL:
   - `TmdbApiService.getMovieVideos(movieId)` → list of videos
   - Filters for `type == "Trailer"` and `site == "YouTube"`
   - Constructs YouTube URL: `https://www.youtube.com/watch?v={key}`
3. URL Base64-encoded → navigation params
4. `TrailerScreen` loaded:
   - Decodes Base64 URL
   - Embeds YouTube player in WebView (or uses intent to open YouTube app)
5. User watches trailer or clicks back

**Navigation Code:** [AppNavigation.kt:119](../app/src/mobile/java/com/movierecommender/app/ui/navigation/AppNavigation.kt#L119)

**Error Handling:**
- No trailer found → icon disabled in UI (gray out)
- Network failure → error message in TrailerScreen

---

### Journey 4: Torrent Streaming

**Route:** `streaming/{title}/{magnetUrl}`  
**Screen:** `StreamingPlayerScreen`  
**Code:** [StreamingPlayerScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/StreamingPlayerScreen.kt)

**Flow:**
1. User clicks "Watch Now" in RecommendationsScreen or MovieSelectionScreen
2. App searches torrent APIs:
   - Tries YTS first (smaller files) → `YtsApiService.searchMovie(title, year)`
   - Fallback to Popcorn API → `PopcornApiService.searchMovie(title, year)`
   - Selects smallest torrent (for fastest streaming)
3. Magnet URL Base64-encoded → navigation params
4. `StreamingPlayerScreen` loaded:
   - Decodes magnet URL
   - Starts `TorrentStreamService` (foreground service)
   - Service downloads torrent → provides local file path
   - ExoPlayer initialized with local file
5. User watches movie
6. Playback position tracked → `service.updatePlaybackPosition(positionMs)`
7. Cache managed (500 MB limit, deletes old chunks)
8. On exit → service stopped → cache cleared

**Service Details:**
- **File:** [TorrentStreamService.kt](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt)
- **Type:** Foreground service with `mediaPlayback` type
- **Notification:** Shows download progress (seeds, speed, cache usage)
- **Cache Management:** 
  - Max 500 MB
  - Deletes chunks older than 1 minute + passed playback position
  - Pauses download if cache full
  - Resumes when space available

**Navigation Code:** [AppNavigation.kt:155](../app/src/mobile/java/com/movierecommender/app/ui/navigation/AppNavigation.kt#L155)

**Error Handling:**
- No torrent found → "Watch Now" button disabled
- Download fails → error shown in StreamingPlayerScreen
- No seeds → error message

---

## Navigation Routes Summary

| Route | Screen | Entry Condition |
|-------|--------|-----------------|
| `genre_selection` | GenreSelectionScreen | App start (default) |
| `movie_selection` | MovieSelectionScreen | Genre selected (not favorites) |
| `favorites` | FavoritesScreen | "Dee's Favorites" selected |
| `recommendations` | RecommendationsScreen | 1–5 movies selected + "Generate" clicked |
| `trailer/{title}/{videoUrl}` | TrailerScreen | Trailer icon clicked |
| `streaming/{title}/{magnetUrl}` | StreamingPlayerScreen | "Watch Now" clicked |

**Deep Links:** None currently configured. All navigation is internal.

---

## Error Handling and Empty States

### Network Failures
**Symptom:** TMDB API call fails  
**Handling:**
- `Resource.Error` emitted from repository
- `MovieUiState.error` set
- UI displays error snackbar or alert dialog
- User can retry via button or pull-to-refresh

**Code:** [MovieViewModel.kt:176](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L176)

### No Movies Found
**Symptom:** Genre has no movies or search returns empty  
**Handling:**
- `movies` list empty
- UI shows "No movies found" message
- User can change genre or search term

### LLM Failure
**Symptom:** OpenAI API error or validation failure  
**Handling:**
- LLM service returns empty string or invalid format
- Repository automatically falls back to TMDB-based recommendations
- `buildFallbackRecommendations()` generates 15 movies using similar/recommended endpoints
- User sees recommendations but from TMDB algorithm instead of LLM

**Code:** [MovieRepository.kt:362](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L362)

### No Torrent Found
**Symptom:** YTS + Popcorn API both return no results  
**Handling:**
- "Watch Now" button disabled (grayed out)
- User can only watch trailer (if available)

### Cache Full
**Symptom:** Torrent cache exceeds 500 MB  
**Handling:**
- `TorrentStreamService.manageCacheSize()` pauses download
- Aggressive cleanup of old chunks
- Resumes when space available
- User continues playback from buffer

**Code:** [TorrentStreamService.kt:131](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt#L131)

---

## Offline Behavior

**Not Supported.** App requires internet for:
- TMDB API (genres, movies, search)
- OpenAI API (recommendations)
- Torrent APIs (streaming metadata)

**Cached Data:**
- Favorites stored locally in Room → accessible offline
- Selected movies stored locally → accessible offline
- User preferences stored in DataStore → accessible offline

**Offline UX:**
- If network unavailable → error shown immediately
- No background sync or queue

---

## Fire TV Specific Flow Differences

### DPAD Navigation
- All clickable elements must be focusable via `Modifier.focusable()`
- Focus order controlled by Compose layout order
- Remote control buttons mapped:
  - D-Pad → navigation
  - Center/OK → click
  - Back → back navigation
  - Play/Pause → media controls (in streaming screen)

### Screen Layouts
- Larger text sizes (10-foot UI)
- Card-based grids with focus highlights
- No swipe gestures (all DPAD)

### Leanback Launcher
- TV-specific launcher intent filter
- Banner image instead of app icon
- No standard LAUNCHER intent (removed in firestick manifest)

**Manifest:** [app/src/firestick/AndroidManifest.xml:19](../app/src/firestick/AndroidManifest.xml#L19)

---

## User Preference Settings

**Access:** Settings icon in GenreSelectionScreen (top-right)

**Preferences Managed:**
1. **Indie Preference** (0–1): Blockbusters ↔ Indie films
2. **Popularity Preference** (0–1): Cult classics ↔ Mainstream
3. **Release Year Range** (1950–2026): Year filter
4. **Tone Preference** (0–1): Light/uplifting ↔ Dark/serious
5. **International Preference** (0–1): Domestic ↔ International
6. **Experimental Preference** (0–1): Traditional ↔ Experimental

**Each has:**
- Slider to adjust value
- Toggle to enable/disable

**Storage:** DataStore → `SettingsRepository`  
**Scope:** Global (persists across app restarts)  
**Effect:** Passed to LLM prompt + TMDB candidate ranking

**Code:** [SettingsRepository.kt:37](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L37)

---

## Known Edge Cases

### 1. Retry Recommendations Multiple Times
**Behavior:** Session tracking (`sessionRecommendedTitles`) prevents repeating titles across retries.  
**Code:** [MovieViewModel.kt:358](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L358)

### 2. Favorites Mode with Mixed Genres
**Behavior:** LLM infers dominant genre from selected movies. If no clear dominant genre (< 60% overlap), LLM treats it as mixed-genre and matches themes instead of strict genre.  
**Code:** [MovieRepository.kt:665](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L665)

### 3. Search While in Favorites Mode
**Behavior:** Search uses TMDB API, not local favorites. User can select searched movies alongside favorites for recommendation input.

### 4. Torrent Streaming on Slow Network
**Behavior:** Buffering occurs. Service shows download speed + seeds in notification. User can pause/resume via service controls (if implemented).

---

**Next Review:** When new screens added, navigation routes change, or user journeys modified.
