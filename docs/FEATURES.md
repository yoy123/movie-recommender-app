# FEATURES.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

Complete inventory of user-facing features with activation conditions, underlying code paths, and platform-specific differences (Android vs Fire TV).

---

## 1. Feature Matrix

| Feature | Android | Fire TV | Code Path |
|---------|---------|---------|-----------|
| User name onboarding | ✅ | ✅ | OnboardingScreen.kt |
| Genre selection | ✅ | ✅ | GenreSelectionScreen.kt |
| Movie selection (1-5) | ✅ | ✅ | MovieSelectionScreen.kt |
| Favorites management | ✅ | ✅ | FavoritesScreen.kt |
| 6 preference sliders | ✅ | ✅ | SettingsRepository.kt |
| LLM recommendations | ✅ | ✅ | MovieRepository.kt |
| Retry recommendations | ✅ | ✅ | MovieViewModel.kt |
| Trailer playback (YouTube) | ✅ | ✅ | TrailerScreen.kt |
| Torrent streaming | ✅ | ✅ | StreamingScreen.kt |
| Dark mode toggle | ✅ | ✅ | SettingsRepository.kt |
| DPAD navigation | ❌ | ✅ | Firestick UI layer |

---

## 2. Core Features

### Feature 1: User Name Onboarding

**Purpose:** Personalize experience ("Dee's Favorites" vs "Your Favorites").

**Activation:**
- First app launch → `is_first_run = true` in DataStore
- User prompted: "What's your name?"

**Flow:**
1. App checks `SettingsRepository.isFirstRun.first()` on launch
2. If `true` → navigate to OnboardingScreen
3. User enters name → `SettingsRepository.setUserName(name)`
4. Set `is_first_run = false`
5. Navigate to GenreSelectionScreen

**Code:**
- [MainActivity.kt:34](../app/src/mobile/java/com/movierecommender/app/MainActivity.kt#L34)
- [OnboardingScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/OnboardingScreen.kt)
- [SettingsRepository.kt:51](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L51)

**UI:**
- Text field (centered)
- "Continue" button (enabled when name.length > 0)

**Validation:**
- No empty names (button disabled)
- No max length enforced (see [KNOWN_ISSUES.md #12](KNOWN_ISSUES.md#12))

**Platform Differences:**
- **Android:** Touch keyboard
- **Fire TV:** On-screen keyboard via DPAD

---

### Feature 2: Genre Selection

**Purpose:** Browse movies by genre, access favorites.

**Activation:** Always available (home screen).

**Flow:**
1. App launches → fetch genres from TMDB:
   ```kotlin
   val response = tmdbApi.getGenres()
   ```
2. Add pseudo-genre: `Genre(id = -1, name = "${userName}'s Favorites")`
3. Display grid of genre cards

**Code:**
- [GenreSelectionScreen.kt:67](../app/src/mobile/java/com/movierecommender/app/ui/screens/GenreSelectionScreen.kt#L67)
- [TmdbApiService.kt:28](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L28)

**UI:**
- **Mobile:** 2-column grid (vertical scroll)
- **Fire TV:** Single column (DPAD up/down)

**User Actions:**
- Tap/click genre → navigate to MovieSelectionScreen
- Tap/click "[Name]'s Favorites" → navigate to FavoritesScreen

**Genres Available:**
- Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery, Romance, Science Fiction, TV Movie, Thriller, War, Western
- **Total:** 19 genres + 1 favorites pseudo-genre = 20 options

**Caching:** No (fetches every launch).

---

### Feature 3: Movie Selection

**Purpose:** Select 1–5 movies to generate recommendations.

**Activation:** After selecting genre.

**Flow:**
1. Fetch movies for genre:
   ```kotlin
   val movies = tmdbApi.getMoviesByGenre(genreId, page = 1)
   ```
2. Display movie posters in grid
3. User taps poster → toggle selection
4. When 5 selected → "Get Recommendations" button appears
5. User clicks button → navigate to RecommendationsScreen

**Code:**
- [MovieSelectionScreen.kt:89](../app/src/mobile/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt#L89)
- [MovieViewModel.kt:187](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L187)

**UI:**
- **Mobile:** 3-column grid (vertical scroll)
- **Fire TV:** 3-column grid (DPAD navigation)
- Selected movies: Checkmark overlay (green, top-right corner)

**Constraints:**
- Minimum: 1 movie (button disabled)
- Maximum: 5 movies (selection disabled after 5)
- Can deselect to select different movie

**Visual Feedback:**
- Selected: Checkmark + border highlight
- Unselected: Normal poster
- Selection count: "X/5 movies selected" at top

**Platform Differences:**
- **Android:** Touch to select
- **Fire TV:** DPAD center to select

---

### Feature 4: Favorites Management

**Purpose:** Save movies for quick access, exclude from recommendations.

**Activation:** Available from any movie card (heart icon).

**Flow:**

**Adding Favorite:**
1. User clicks heart icon (outline) on movie card
2. `MovieViewModel.addToFavorites(movie)` called
3. Room inserts movie with `isFavorite = true`
4. Heart icon becomes filled (red)
5. Movie excluded from future recommendations

**Removing Favorite:**
1. User clicks heart icon (filled) on movie card
2. `MovieViewModel.removeFromFavorites(movie)` called
3. Room updates movie with `isFavorite = false`
4. Heart icon becomes outline (gray)

**Viewing Favorites:**
1. User selects "[Name]'s Favorites" from Genre Selection
2. Navigate to FavoritesScreen
3. Display all movies with `isFavorite = true`
4. Empty state: "No favorites yet. Add movies by clicking the heart icon."

**Code:**
- [FavoritesScreen.kt:45](../app/src/mobile/java/com/movierecommender/app/ui/screens/FavoritesScreen.kt#L45)
- [MovieRepository.kt:234](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L234)
- [MovieDao.kt:28](../app/src/main/java/com/movierecommender/app/data/local/MovieDao.kt#L28)

**UI:**
- Heart icon on movie cards (all screens except onboarding)
- Favorites screen: Same grid layout as Movie Selection

**Persistence:** Survives app restart (stored in Room).

**Platform Differences:**
- **Android:** Touch heart icon
- **Fire TV:** DPAD navigate to card, long-press center to favorite

---

### Feature 5: Recommendation Preferences

**Purpose:** Fine-tune LLM recommendations with 6 slider preferences.

**Activation:** Settings screen (gear icon).

**Preferences:**

**1. Indie vs Blockbuster**
- Range: 0 (Blockbuster) → 1 (Indie)
- Default: 0.5 (balanced)
- Toggle: `use_indie` (default: true)
- LLM Instruction: "Prefer indie films with budget < $10M" (if > 0.7)

**2. Popularity**
- Range: 0 (Cult classics) → 1 (Mainstream)
- Default: 0.5
- Toggle: `use_popularity` (default: true)
- LLM Instruction: "Prefer mainstream popular films" (if > 0.7)

**3. Release Year Range**
- Start: 1950 → 2026 (default: 1950)
- End: 1950 → 2026 (default: 2026)
- Toggle: `use_release_year` (default: true)
- LLM Instruction: "Only recommend films from {start} to {end}"

**4. Tone**
- Range: 0 (Light/uplifting) → 1 (Dark/serious)
- Default: 0.5
- Toggle: `use_tone` (default: true)
- LLM Instruction: "Prefer dark, serious films" (if > 0.7)

**5. International**
- Range: 0 (Domestic) → 1 (International)
- Default: 0.5
- Toggle: `use_international` (default: true)
- LLM Instruction: "Prefer international/foreign films" (if > 0.7)

**6. Experimental**
- Range: 0 (Traditional) → 1 (Experimental)
- Default: 0.5
- Toggle: `use_experimental` (default: true)
- LLM Instruction: "Prefer experimental/avant-garde films" (if > 0.7)

**Code:**
- [SettingsRepository.kt:62](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L62)
- [LlmRecommendationService.kt:289](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L289)

**UI:**
- Sliders for each preference (Material3 Slider)
- Toggle switches to enable/disable
- Reset button (restore defaults)

**Persistence:** Stored in DataStore (survives app restart).

**Platform Differences:**
- **Android:** Touch sliders
- **Fire TV:** DPAD left/right to adjust (5% increments)

---

### Feature 6: LLM Recommendations

**Purpose:** Generate personalized movie recommendations using AI.

**Activation:** After selecting 5 movies.

**Flow:**

1. User clicks "Get Recommendations"
2. `MovieRepository.getRecommendations(selectedMovies)` called
3. Extract themes from selected movies (TMDB data)
4. Load user preferences (sliders)
5. Build exclusion list (favorites + previous recommendations)
6. Construct LLM prompt:
   ```
   Based on these movies: [titles]
   Themes: [extracted themes]
   Preferences: [slider values]
   Exclude: [exclusion list]
   Recommend 15 movies.
   ```
7. Call OpenAI API (GPT-4o-mini)
8. Parse response → extract movie titles
9. Search TMDB for each title → get movie details
10. Validate (genre overlap, release year constraints)
11. Save to Room with `isRecommended = true`
12. Display on RecommendationsScreen

**Code:**
- [MovieRepository.kt:456](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L456)
- [LlmRecommendationService.kt:134](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L134)

**Modes:**

**Open Mode** (default):
- LLM has full freedom
- Generates any 15 movies
- Faster (one API call)

**Bounded Mode** (if enabled):
- Fetch 25+ TMDB candidates
- LLM reranks candidates
- Slower but more constrained

**Code:** [MovieRepository.kt:489](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L489)

**Retry Logic:**
- Attempt 1: Creative (temp=0.6)
- Attempt 2: Strict (temp=0.3) if validation fails
- Fallback: TMDB-based recommendations if LLM fails

**Loading State:**
- Show spinner + "Generating recommendations..."
- Duration: 5–15 seconds (network dependent)

**Platform Differences:** None (same logic).

---

### Feature 7: Retry Recommendations

**Purpose:** Get new recommendations without re-selecting movies.

**Activation:** "Retry" button on RecommendationsScreen.

**Flow:**
1. User clicks "Retry"
2. `MovieViewModel.retryRecommendations()` called
3. Extract previously recommended titles:
   ```kotlin
   val previousTitles = sessionRecommendedTitles.value
   ```
4. Add to exclusion list
5. Call LLM again with updated exclusions
6. Display new recommendations

**Code:**
- [MovieViewModel.kt:412](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L412)
- [MovieRepository.kt:456](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L456)

**Deduplication:**
- Session-level tracking (`sessionRecommendedTitles`)
- Prevents repeats within same session
- Resets on "Start Over"

**UI:**
- Button at top of RecommendationsScreen
- Disabled during loading

**Limit:** None (can retry indefinitely).

**Platform Differences:** None.

---

### Feature 8: Trailer Playback

**Purpose:** Preview movie with YouTube trailer.

**Activation:** "Watch Trailer" button on recommendation card.

**Flow:**
1. User clicks "Watch Trailer"
2. Fetch trailer key from TMDB:
   ```kotlin
   val videos = tmdbApi.getMovieVideos(movieId)
   val trailer = videos.results.find { it.type == "Trailer" && it.site == "YouTube" }
   ```
3. Construct YouTube URL: `https://www.youtube.com/watch?v={key}`
4. Launch intent:
   ```kotlin
   val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
   context.startActivity(intent)
   ```
5. YouTube app opens (or browser if app not installed)

**Code:**
- [TrailerScreen.kt:89](../app/src/mobile/java/com/movierecommender/app/ui/screens/TrailerScreen.kt#L89)
- [TmdbApiService.kt:48](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L48)

**UI:**
- No embedded player (delegates to YouTube)
- Returns to app when user exits YouTube

**Fallback:** If no trailer found → button disabled + "No trailer available".

**Platform Differences:**
- **Android:** YouTube app (seamless)
- **Fire TV:** YouTube TV app (slower transition)

---

### Feature 9: Torrent Streaming

**Purpose:** Watch full movie via torrent streaming.

**Activation:** "Watch Now" button on recommendation card.

**Flow:**
1. User clicks "Watch Now"
2. Search YTS API for torrent:
   ```kotlin
   val torrent = ytsApi.searchMovies(title, year)
   ```
3. If not found → search Popcorn API
4. Construct magnet URL
5. Start TorrentStreamService (foreground service)
6. Show notification: "Downloading movie..."
7. Wait for buffer (2.5 seconds of video)
8. Navigate to StreamingScreen
9. ExoPlayer plays from local cache
10. Continue downloading while playing
11. User exits → service continues in background
12. User closes notification → stop service, clear cache

**Code:**
- [StreamingScreen.kt:78](../app/src/mobile/java/com/movierecommender/app/ui/screens/StreamingScreen.kt#L78)
- [TorrentStreamService.kt:67](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt#L67)
- [YtsApiService.kt:28](../app/src/main/java/com/movierecommender/app/data/remote/YtsApiService.kt#L28)

**UI:**
- ExoPlayer fullscreen
- Standard controls (play/pause, seek, progress)
- Notification (persistent, dismissible)

**Cache:**
- Max 500 MB (aggressive cleanup)
- Cleared on exit

**Error Handling:**
- No seeders → "Movie not available for streaming"
- Slow network → "Buffering..." indicator
- Timeout (30s) → "Unable to connect"

**Platform Differences:**
- **Android:** Touch controls
- **Fire TV:** DPAD controls (center = play/pause, left/right = seek)

**Legal Warning:** See [MEDIA_PLAYBACK.md §11](MEDIA_PLAYBACK.md#11).

---

### Feature 10: Dark Mode

**Purpose:** Reduce eye strain, save battery (OLED).

**Activation:** Settings toggle.

**Flow:**
1. User opens Settings → toggles "Dark Mode"
2. `SettingsRepository.setDarkMode(enabled)` called
3. DataStore saves preference
4. App recomposes with new theme

**Code:**
- [SettingsRepository.kt:77](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L77)
- [MainActivity.kt:56](../app/src/mobile/java/com/movierecommender/app/MainActivity.kt#L56)

**Themes:**
- Light mode: White background, dark text
- Dark mode: Black background, white text

**Default:** Dark mode enabled.

**Persistence:** Stored in DataStore (survives app restart).

**Platform Differences:** None (same theme system).

---

## 3. Navigation Flows

### Flow 1: First-Time User (Full Journey)

```
Launch App
    ↓
Onboarding (enter name)
    ↓
Genre Selection
    ↓
Movie Selection (pick 5)
    ↓
Recommendations (15 suggestions)
    ↓
[Option A] Watch Trailer → YouTube
[Option B] Watch Now → Torrent Streaming
```

**Duration:** 2–5 minutes (depending on streaming).

---

### Flow 2: Returning User (Favorites Mode)

```
Launch App
    ↓
Genre Selection
    ↓
Select "[Name]'s Favorites"
    ↓
Favorites Screen (view saved movies)
    ↓
Click movie → Watch Now or Watch Trailer
```

**Duration:** < 1 minute.

---

### Flow 3: Retry Recommendations

```
Recommendations Screen
    ↓
Click "Retry"
    ↓
New recommendations generated (excludes previous)
    ↓
Review new suggestions
```

**Duration:** 5–10 seconds (LLM call).

---

### Flow 4: Adjust Preferences

```
Any Screen
    ↓
Click Settings (gear icon)
    ↓
Adjust sliders (indie, tone, etc.)
    ↓
Save changes
    ↓
Next recommendations use new preferences
```

**Persistence:** Preferences apply to all future recommendation sessions.

---

## 4. Feature Activation Matrix

| Feature | Required Setup | Optional Setup | Disabled If... |
|---------|----------------|----------------|----------------|
| Genre Selection | TMDB_API_KEY | - | No internet |
| Movie Selection | TMDB_API_KEY | - | No internet |
| Recommendations | TMDB_API_KEY, OPENAI_API_KEY | Sliders configured | No internet, no API keys |
| Favorites | - | - | Never |
| Trailer Playback | YouTube app installed | - | No trailer available |
| Torrent Streaming | - | - | No seeders, no internet |
| Dark Mode | - | - | Never |

---

## 5. Feature Flags (None)

**Current State:** No feature flags implemented.

**Recommendation:** Add feature flags for:
- Bounded mode (TMDB candidate pool)
- Torrent streaming (disable for store submission)
- Experimental features

**Implementation:**
```kotlin
object FeatureFlags {
    const val ENABLE_BOUNDED_MODE = false
    const val ENABLE_TORRENT_STREAMING = true
    const val ENABLE_VOICE_SEARCH = false
}
```

---

## 6. A/B Testing (None)

**Current State:** No A/B testing framework.

**Potential Tests:**
- LLM temperature (0.6 vs 0.8)
- Recommendation count (15 vs 10)
- Slider defaults (0.5 vs 0.7)

---

## 7. Analytics Events (None)

**Current State:** No analytics SDK integrated.

**Recommended Events:**
- `onboarding_complete`
- `genre_selected`
- `movie_selected`
- `recommendations_requested`
- `recommendations_success`
- `recommendations_failed`
- `favorite_added`
- `favorite_removed`
- `trailer_watched`
- `movie_streamed`
- `retry_clicked`

**See:** [KNOWN_ISSUES.md #13](KNOWN_ISSUES.md#13).

---

## 8. Accessibility Features

### Current Support

**Screen Readers:** Partial (Compose provides defaults).

**Content Descriptions:**
- Movie posters: `contentDescription = movie.title`
- Buttons: `contentDescription = "Get Recommendations"`

**Status:** Not comprehensively tested.

**Recommendation:** Add accessibility audit:
1. Enable TalkBack (Android) / VoiceView (Fire TV)
2. Navigate app without looking at screen
3. Verify all elements have descriptions
4. Add semantic labels where missing

---

### Keyboard Navigation (TV)

**Fire TV:** DPAD navigation fully supported (spatial focus).

**Android TV:** Same DPAD support.

**Desktop (ChromeOS, Samsung DeX):** Not tested.

---

## 9. Localization (None)

**Current State:** English only.

**Hardcoded Strings:**
- UI text (buttons, labels)
- Error messages
- LLM prompts

**Recommendation:** Extract to `strings.xml`:
```xml
<string name="get_recommendations">Get Recommendations</string>
<string name="watch_now">Watch Now</string>
<string name="watch_trailer">Watch Trailer</string>
```

**Supported Languages (Future):**
- Spanish
- French
- German
- Japanese
- Portuguese

**LLM Localization:** Translate prompts, receive responses in target language.

---

## 10. Offline Mode (None)

**Current State:** Requires internet for all features.

**Recommendation:** Cache genre list + movie posters for offline browsing.

**See:** [KNOWN_ISSUES.md #11](KNOWN_ISSUES.md#11).

---

## 11. Code References

### Feature Implementation Files

**Onboarding:**
- [OnboardingScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/OnboardingScreen.kt)

**Genre Selection:**
- [GenreSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/GenreSelectionScreen.kt)

**Movie Selection:**
- [MovieSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt)

**Favorites:**
- [FavoritesScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/FavoritesScreen.kt)

**Recommendations:**
- [RecommendationsScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt)
- [MovieRepository.kt:456](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L456)

**Trailer:**
- [TrailerScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/TrailerScreen.kt)

**Streaming:**
- [StreamingScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/StreamingScreen.kt)
- [TorrentStreamService.kt](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt)

**Settings:**
- [SettingsRepository.kt](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt)

---

**Next Review:** When new features added, existing features modified, or platform-specific differences emerge.
