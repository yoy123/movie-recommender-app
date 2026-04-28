# ARCHITECTURE.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

OpenStream+ is an Android/Fire TV application that uses TMDB for movie data and OpenAI GPT-4o-mini for personalized recommendations. The architecture is MVVM-based with Jetpack Compose UI and Room persistence.

## High-Level System Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          USER INTERFACE LAYER                        │
│  ┌──────────────────┐                   ┌──────────────────┐       │
│  │  Mobile Flavor   │                   │ Firestick Flavor │       │
│  │  (Touch/Gesture) │                   │  (DPAD/Remote)   │       │
│  │                  │                   │                  │       │
│  │  MainActivity    │                   │  MainActivity    │       │
│  │  AppNavigation   │                   │  AppNavigation   │       │
│  │  Screens/*       │                   │  Screens/*       │       │
│  │  MovieViewModel  │                   │  MovieViewModel  │       │
│  └────────┬─────────┘                   └────────┬─────────┘       │
└───────────┼──────────────────────────────────────┼──────────────────┘
            │                                      │
            └──────────────────┬───────────────────┘
                               │
┌──────────────────────────────┼───────────────────────────────────────┐
│                     REPOSITORY LAYER                                 │
│                       MovieRepository                                │
│                                                                       │
│  • State Management (Resource<T>)                                   │
│  • Business Logic (recommendation algorithm)                         │
│  • LLM Integration (OpenAI requests + validation)                   │
│  • Fallback Logic (TMDB-based recommendations)                      │
│  • Storage Coordination (Room + DataStore)                          │
└───────────┬───────────────────────────────┬──────────────────────────┘
            │                               │
┌───────────┼───────────────┐   ┌───────────┼──────────────────────────┐
│    DATA SOURCES           │   │     PERSISTENCE                      │
│                           │   │                                      │
│  • TmdbApiService         │   │  • AppDatabase (Room v2)            │
│  • LlmRecommendationSvc   │   │    - Movie entity                   │
│  • OmdbApiService         │   │    - MovieDao                       │
│  • YtsApiService          │   │    - Converters (List<Int>)         │
│  • PopcornApiService      │   │                                      │
│  • ImdbScraperService     │   │  • SettingsRepository (DataStore)   │
│                           │   │    - userName                        │
│                           │   │    - isFirstRun                      │
│                           │   │    - darkMode                        │
│                           │   │    - 6 preference sliders + toggles │
└───────────────────────────┘   └──────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                       FOREGROUND SERVICES                            │
│                                                                       │
│  • TorrentStreamService (mediaPlayback)                             │
│    - Manages torrent download + streaming                           │
│    - Cache management (500 MB limit)                                │
│    - Provides local file path for ExoPlayer                         │
└─────────────────────────────────────────────────────────────────────┘
```

## Module Boundaries

### 1. Shared Data Layer (`app/src/main/`)

**Package:** `com.movierecommender.app`

**Ownership:** All flavors (mobile + firestick)

**Responsibilities:**
- Data models (Movie, Genre, etc.)
- Network clients (TMDB, OpenAI, torrent APIs)
- Repository pattern (MovieRepository)
- Local persistence (AppDatabase, MovieDao, SettingsRepository)
- Application class (MovieRecommenderApplication)
- Torrent streaming service (TorrentStreamService)

**Key Files:**
- [data/model/Movie.kt](../app/src/main/java/com/movierecommender/app/data/model/Movie.kt) - Movie entity with Room annotations
- [data/local/AppDatabase.kt](../app/src/main/java/com/movierecommender/app/data/local/AppDatabase.kt) - Room DB v2 with destructive migration
- [data/local/MovieDao.kt](../app/src/main/java/com/movierecommender/app/data/local/MovieDao.kt) - CRUD operations + flag updates
- [data/repository/MovieRepository.kt](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt) - Business logic
- [data/settings/SettingsRepository.kt](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt) - User preferences
- [MovieRecommenderApplication.kt](../app/src/main/java/com/movierecommender/app/MovieRecommenderApplication.kt) - DI container

### 2. Mobile Flavor (`app/src/mobile/`)

**Package:** `com.movierecommender.app`

**Ownership:** Mobile (phone/tablet) builds

**Responsibilities:**
- Touch-based UI screens (Compose)
- Gesture navigation
- ViewModel (StateFlow → UI state)
- Navigation graph (Compose Navigation)

**Key Files:**
- [MainActivity.kt](../app/src/mobile/java/com/movierecommender/app/MainActivity.kt) - Mobile entrypoint
- [ui/navigation/AppNavigation.kt](../app/src/mobile/java/com/movierecommender/app/ui/navigation/AppNavigation.kt) - Routes + NavHost
- [ui/viewmodel/MovieViewModel.kt](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt) - State management
- [ui/screens/GenreSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/GenreSelectionScreen.kt)
- [ui/screens/MovieSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt)
- [ui/screens/RecommendationsScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt)
- [ui/screens/FavoritesScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/FavoritesScreen.kt)
- [ui/screens/TrailerScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/TrailerScreen.kt)
- [ui/screens/StreamingPlayerScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/StreamingPlayerScreen.kt)

### 3. Firestick Flavor (`app/src/firestick/`)

**Package:** `com.movierecommender.app.firestick` (MainActivity only) + `com.movierecommender.app.ui.*`

**Ownership:** Fire TV/Android TV builds

**Responsibilities:**
- DPAD-based UI screens (Compose + androidx.tv)
- Remote control navigation
- Leanback launcher integration
- ViewModel (StateFlow → UI state)
- Navigation graph (Compose Navigation)

**Key Files:**
- [MainActivity.kt](../app/src/firestick/java/com/movierecommender/app/firestick/MainActivity.kt) - TV entrypoint (LEANBACK_LAUNCHER)
- [ui/navigation/firestick/AppNavigation.kt](../app/src/firestick/java/com/movierecommender/app/ui/navigation/firestick/AppNavigation.kt)
- [ui/viewmodel/firestick/MovieViewModel.kt](../app/src/firestick/java/com/movierecommender/app/ui/viewmodel/firestick/MovieViewModel.kt)
- [ui/screens/*.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/) - TV-optimized screens

**Special TV Handling:**
- Focus management via `Modifier.focusable()` and `androidx.tv.material3`
- 10-foot UI design (larger text, cards)
- No touch gestures; all navigation via DPAD

## Dependency Edges

```
Mobile/Firestick UI → MovieViewModel → MovieRepository → [Data Sources + Persistence]
                            ↓                    ↓
                    SettingsRepository      MovieDao / Room
                                                ↓
                                         AppDatabase (local.db)
```

### Dependency Flow

1. **UI → ViewModel (StateFlow)**
   - Screens collect `MovieUiState` via `collectAsState()`
   - UI triggers actions: `viewModel.selectGenre()`, `viewModel.toggleMovieSelection()`, etc.

2. **ViewModel → Repository (suspend funs + Flow<Resource<T>>)**
   - `repository.getGenres()` → `Flow<Resource<List<Genre>>>`
   - `repository.getRecommendations(...)` → `Flow<Resource<String>>`
   - All network/DB calls emit Loading → Success/Error

3. **Repository → Data Sources**
   - `TmdbApiService`: genre list, movie search, similar/recommendations, **watch providers**
   - `LlmRecommendationService`: OpenAI GPT-4o-mini prompt + validation
   - `YtsApiService`, `PopcornApiService`, `PirateBayApiService`, `TorrentGalaxyService`, `LeetxService`: torrent metadata
   - `StreamingAppRegistry`: maps TMDB provider IDs to Android package names and deep links (25+ streaming apps)
   - `OmdbApiService`, `ImdbScraperService`: IMDB ratings/trailers (unused in current UI)

4. **Repository → Persistence**
   - `MovieDao`: CRUD operations for Movie entity
   - `SettingsRepository`: DataStore for user preferences

## Control Flow for Core Screens

### Entrypoints

#### Mobile
**Manifest:** [app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml)
- Intent filter: `action.MAIN` + `category.LAUNCHER`
- Activity: `com.movierecommender.app.MainActivity`

#### Fire TV
**Manifest:** [app/src/firestick/AndroidManifest.xml](../app/src/firestick/AndroidManifest.xml)
- Intent filter: `action.MAIN` + `category.LEANBACK_LAUNCHER`
- Activity: `com.movierecommender.app.firestick.MainActivity`
- Removes standard LAUNCHER intent (TV-only)

### Application Startup

Both flavors instantiate `MovieRecommenderApplication`:
```kotlin
class MovieRecommenderApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { 
        MovieRepository(
            movieDao = database.movieDao(),
            apiService = TmdbApiService.create(this)  // Context for HTTP cache
        ) 
    }
}
```

### Navigation Graph (Mobile Example)

**Start:** `GenreSelectionScreen`

1. **GenreSelectionScreen**
   - Loads genres from TMDB
   - User selects a genre (or "Dee's Favorites" pseudo-genre with `genreId = -1`)
   - If favorites → navigate to `FavoritesScreen`
   - Else → navigate to `MovieSelectionScreen`

2. **MovieSelectionScreen** (or **FavoritesScreen**)
   - Displays movies from selected genre (or favorites list)
   - User selects 1-5 movies (checkboxes)
   - User clicks "Generate Recommendations" → navigate to `RecommendationsScreen`

3. **RecommendationsScreen**
   - Calls `repository.getRecommendations(...)` with selected movies + preferences
   - LLM generates 15 recommendations (or TMDB fallback)
   - User can:
     - Add to Favorites (heart icon)
     - Watch Trailer (opens `TrailerScreen` with YouTube/IMDB embed)
     - Watch Now (torrent streaming via `StreamingPlayerScreen`)
     - Retry Recommendations (excludes previous results)
     - Start Over (back to `GenreSelectionScreen`)

### Android vs Fire TV Codepaths

**Shared:**
- Data layer (models, repository, APIs, Room)
- Application class
- TorrentStreamService

**Distinct:**
- MainActivity (different packages for each flavor)
- UI screens (mobile vs firestick directories)
- Navigation (different route handling for DPAD)
- Theme files (Color.kt, Type.kt, Theme.kt)

**No conditional branching in shared code.** Flavor-specific behavior is achieved through separate source sets.

## Data Flow Example: Generating Recommendations

1. User selects 3 movies in `MovieSelectionScreen`
2. User clicks "Get Recommendations"
3. `MovieViewModel.generateRecommendations()` is called
4. ViewModel reads current state (selected movies, genre, preferences)
5. Calls `repository.getRecommendations(...)` which emits `Flow<Resource<String>>`
6. Repository:
   - Fetches favorites + already-recommended movies from Room
   - Builds exclusion list (selected + favorites + already-recommended + session retries)
   - If candidate pool ≥ 25: calls `LlmRecommendationService.getRecommendationsFromLlmCandidates()`
   - Else: calls `LlmRecommendationService.getRecommendationsFromLlm()`
   - Validates LLM response structure + genre constraint + candidate constraint
   - If validation fails: falls back to `buildFallbackRecommendations()` (TMDB-based)
7. ViewModel updates `recommendationText` in `MovieUiState`
8. UI displays analysis + 15 numbered recommendations

## Key Design Patterns

### 1. Resource Wrapper

All async operations return `Flow<Resource<T>>`:
```kotlin
sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String) : Resource<T>()
    class Loading<T> : Resource<T>()
}
```

UI always handles all three states.

### 2. StateFlow + Compose

`MovieViewModel` exposes `StateFlow<MovieUiState>`. Screens collect:
```kotlin
val uiState by viewModel.uiState.collectAsState()
```

Never mutate state directly; always use `.copy()`:
```kotlin
_uiState.value = _uiState.value.copy(isLoading = true)
```

### 3. Repository as Single Source of Truth

All business logic lives in `MovieRepository`. ViewModels delegate to repository and update UI state based on emitted resources.

### 4. Favorites Pseudo-Genre

"Dee's Favorites" is represented as `genreId = -1`. This special value triggers:
- `isFavoritesMode = true` in UI state
- Navigation to `FavoritesScreen` instead of `MovieSelectionScreen`
- No genre enforcement in LLM prompt (infers dominant genre from selected movies)

### 5. Room Flag Management

Movies have three Boolean flags:
- `isSelected`: currently selected for recommendation input
- `isRecommended`: appears in current recommendation list
- `isFavorite`: saved to favorites collection

Flags are updated via DAO operations, not by recreating Movie objects.

### 6. Navigation with Base64 Encoding

URL parameters (trailer URLs, magnet links) are Base64-encoded to avoid Compose Navigation issues with special characters:
```kotlin
val encodedUrl = android.util.Base64.encodeToString(
    videoUrl.toByteArray(),
    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
)
navController.navigate("trailer/$encodedTitle/$encodedUrl")
```

## Security and Privacy

- **API Keys:** Stored in `local.properties` (not committed). Read via `BuildConfig` at compile time.
- **SSL:** Debug builds use insecure SSL (for emulator); release builds use standard SSL with certificate validation.
- **Torrent Cache:** Cleared on app exit via `TorrentStreamService.getClearCacheIntent()`.
- **User Data:** No user data is sent to external services except:
  - Movie titles (selected movies) → OpenAI API (for recommendations)
  - Search queries → TMDB API

## Open Questions / UNKNOWNs

None at this time. All architectural flows have been traced.

---
**Next Review:** When schema changes, new flavors added, or major refactoring occurs.
