# Movie Recommender App - AI Agent Instructions

## Architecture Overview

Android app (Kotlin + Jetpack Compose) using MVVM with OpenAI GPT-4o-mini for movie recommendations.

### Layer Structure
```
UI (Compose Screens) → ViewModel (StateFlow) → Repository → Data Sources (Room + Retrofit)
```

### Product Flavors
Two build variants share core logic but have distinct UI implementations:
- **mobile** (`app/src/mobile/`) - Standard Android phone/tablet UI
- **firestick** (`app/src/firestick/`) - TV/D-pad navigation with `androidx.tv` libraries

Shared code lives in `app/src/main/` (data layer, models, repository).

## Key Patterns

### State Management
All UI state flows through `MovieUiState` data class in `MovieViewModel`. Never mutate state directly:
```kotlin
_uiState.value = _uiState.value.copy(isLoading = true)
```

### Resource Wrapper
API/DB operations return `Flow<Resource<T>>` with `Success`, `Error`, `Loading` states. Always handle all three in UI.

### Favorites System
"[Name]'s Favorites" uses `genreId = -1` pseudo-genre. Check `isFavoritesMode` in ViewModel before navigation.

### LLM Integration
`LlmRecommendationService` makes two API attempts (creative → strict) with different temperatures. Response must pass `isValidRecommendationStructure()` or falls back to TMDB-based `buildFallbackRecommendations()` algorithm.

## Build & Run

```bash
# API keys required in local.properties (NOT committed):
TMDB_API_KEY=your_key
OPENAI_API_KEY=sk-proj-...

# Build
./gradlew :app:assembleDebug          # Both flavors
./gradlew :app:assembleMobileDebug    # Mobile only
./gradlew :app:assembleFirestickDebug # Firestick only

# Install
./gradlew installMobileDebug
```

## Code Conventions

- **Compose**: Use `remember` + `LaunchedEffect` for side effects; collect StateFlow with `collectAsState()`
- **Navigation**: Routes defined in `Screen` sealed class; URL params encoded with Base64 (see `AppNavigation.kt`)
- **Settings**: User preferences persisted via `DataStore` in `SettingsRepository`
- **Database**: Room with `fallbackToDestructiveMigration()` - bump version in `AppDatabase` for schema changes

## Critical Files

| Purpose | File |
|---------|------|
| Data models | `data/model/Movie.kt` |
| API interface | `data/remote/TmdbApiService.kt` |
| LLM logic | `data/remote/LlmRecommendationService.kt` |
| Business logic | `data/repository/MovieRepository.kt` |
| State container | `ui/viewmodel/MovieViewModel.kt` |
| Navigation | `ui/navigation/AppNavigation.kt` |
| User prefs | `data/settings/SettingsRepository.kt` |

## Recommendation Preferences

Six user-configurable sliders passed to LLM (each has `use*` boolean toggle):
- `indiePreference` (0=blockbuster, 1=indie)
- `popularityPreference` (0=cult, 1=mainstream)
- `releaseYearStart/End` (1950-current)
- `tonePreference` (0=light, 1=dark)
- `internationalPreference` (0=domestic, 1=international)
- `experimentalPreference` (0=traditional, 1=experimental)

## Testing Notes

- Debug builds use insecure SSL for emulator compatibility (see `TmdbApiService.buildInsecureClientBuilder()`)
- Session retries track `sessionRecommendedTitles` to prevent duplicate recommendations
- `excludedMovies` list passed to LLM includes favorites + selected + already-recommended
