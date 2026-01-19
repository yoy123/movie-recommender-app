# Movie Recommender App - AI Agent Instructions

## Architecture Overview

Android app (Kotlin + Jetpack Compose) using MVVM with OpenAI GPT-4o-mini for movie recommendations.

**Package**: `com.movierecommender.app`

### Layer Structure
```
UI (Compose Screens) → ViewModel (StateFlow) → Repository → Data Sources (Room + Retrofit)
```

### Product Flavors
Two build variants share core logic but have **distinct UI implementations**:
- **mobile** (`app/src/mobile/`) - Standard Android phone/tablet UI
- **firestick** (`app/src/firestick/`) - TV/D-pad navigation with `androidx.tv` libraries

Shared code lives in `app/src/main/` (data layer, models, repository). Each flavor has its own `MainActivity.kt`, `ui/viewmodel/`, `ui/screens/`, and `ui/navigation/`.

## Key Patterns

### State Management
All UI state flows through `MovieUiState` data class in `MovieViewModel`. Never mutate state directly:
```kotlin
_uiState.value = _uiState.value.copy(isLoading = true)
```

### Resource Wrapper
API/DB operations return `Flow<Resource<T>>` with `Success`, `Error`, `Loading` states (defined in `MovieRepository.kt`). Always handle all three in UI collectors.

### Favorites System
"[Name]'s Favorites" uses `genreId = -1` pseudo-genre. Check `isFavoritesMode` in ViewModel before navigation—it determines whether to route to `FavoritesScreen` or `MovieSelectionScreen`.

### LLM Integration
`LlmRecommendationService` makes two API attempts (creative temp=0.6 → strict temp=0.3). Response must pass `isValidRecommendationStructure()` validation or falls back to TMDB-based `buildFallbackRecommendations()` algorithm in `MovieRepository`.

Session retries track `sessionRecommendedTitles` in ViewModel to prevent duplicate recommendations across retries.

## Build & Run

```bash
# API keys required in local.properties (NOT committed):
TMDB_API_KEY=your_key
OPENAI_API_KEY=sk-proj-...

# Build both flavors
./gradlew :app:assembleDebug

# Build specific flavor
./gradlew :app:assembleMobileDebug
./gradlew :app:assembleFirestickDebug

# Install to connected device
./gradlew installMobileDebug
./gradlew installFirestickDebug
```

## Code Conventions

- **Compose**: Use `remember` + `LaunchedEffect` for side effects; collect StateFlow with `collectAsState()`
- **Navigation**: Routes in `Screen` sealed class; URL params encoded with **Base64** to handle special chars (see `AppNavigation.kt`)
- **Settings**: User preferences via `DataStore` in `SettingsRepository`; each pref has a `Flow<T>` getter and `suspend fun set*()` setter
- **Database**: Room with `fallbackToDestructiveMigration()` - bump `version` in `AppDatabase.kt` for schema changes (currently v2)
- **Movie flags**: `isSelected`, `isRecommended`, `isFavorite` are Boolean flags on `Movie` entity—update via DAO, don't recreate objects

## Critical Files

| Purpose | Location |
|---------|----------|
| Data models | `app/src/main/.../data/model/Movie.kt` |
| TMDB API | `app/src/main/.../data/remote/TmdbApiService.kt` |
| LLM logic | `app/src/main/.../data/remote/LlmRecommendationService.kt` |
| Business logic | `app/src/main/.../data/repository/MovieRepository.kt` |
| Room DAO | `app/src/main/.../data/local/MovieDao.kt` |
| Database | `app/src/main/.../data/local/AppDatabase.kt` |
| Settings | `app/src/main/.../data/settings/SettingsRepository.kt` |
| **Mobile** ViewModel | `app/src/mobile/.../ui/viewmodel/MovieViewModel.kt` |
| **Mobile** Navigation | `app/src/mobile/.../ui/navigation/AppNavigation.kt` |
| **Firestick** ViewModel | `app/src/firestick/.../ui/viewmodel/MovieViewModel.kt` |

## Recommendation Preferences

Six user-configurable sliders passed to LLM (each has `use*` boolean toggle in UI and settings):
| Preference | Range | Meaning |
|------------|-------|---------|
| `indiePreference` | 0–1 | 0=blockbusters, 1=indie |
| `popularityPreference` | 0–1 | 0=cult classics, 1=mainstream |
| `releaseYearStart/End` | 1950–current | Year range filter |
| `tonePreference` | 0–1 | 0=light/uplifting, 1=dark/serious |
| `internationalPreference` | 0–1 | 0=domestic, 1=international |
| `experimentalPreference` | 0–1 | 0=traditional, 1=experimental |

## Testing & Debugging

- Debug builds use insecure SSL for emulator compatibility (see `TmdbApiService.buildInsecureClientBuilder()`)
- Verbose logging in `LlmRecommendationService` (TAG: `LlmRecommendation`) and `AppNavigation` for navigation debugging
- `excludedMovies` list passed to LLM includes favorites + selected + already-recommended to prevent repeats
