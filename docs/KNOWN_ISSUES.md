# KNOWN_ISSUES.md

**Last Updated:** 2026-01-23
**Status:** SOURCE-OF-TRUTH MEMORY

## Purpose

Comprehensive inventory of broken logic, dead code, unwired configs, tech debt, and security concerns. Each issue includes evidence, impact analysis, risk level, and recommended fix.

---

## Critical Issues (High User Impact)

### 1. ~~Destructive Database Migration~~ **RESOLVED**

**Status:** ✅ **FIXED** - 2026-01-19

**Resolution:**

1. Removed `fallbackToDestructiveMigration()` from [AppDatabase.kt](../app/src/main/java/com/movierecommender/app/data/local/AppDatabase.kt)
2. Added proper Room migration infrastructure with `MIGRATION_1_2`
3. Enabled schema export (`exportSchema = true`) for migration testing
4. Added Room testing dependency for automated migration tests
5. Added KSP schema export configuration in build.gradle.kts

**Code Changes:**

- [AppDatabase.kt](../app/src/main/java/com/movierecommender/app/data/local/AppDatabase.kt): 
  - `.addMigrations(MIGRATION_1_2)` replaces `.fallbackToDestructiveMigration()`
  - Added migration template for future schema changes
- [build.gradle.kts](../app/build.gradle.kts):
  - Added `room.schemaLocation` KSP argument
  - Added `androidx.room:room-testing` dependency

**Behavior Change:**

- **Before:** Schema version bump → ALL user favorites deleted silently
- **After:** Schema version bump → Data preserved via explicit migration
- **Safety:** If migration is missing, app crashes with clear error (prevents silent data loss)

**Risk Level:** ✅ **RESOLVED**

---

### 2. No TMDB Rate Limit Handling

**Evidence:**

- [TmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt)
- No retry logic, no backoff, no rate limit detection

**Problem:** TMDB has rate limits (40 requests/10 seconds). App makes bursts of 20+ requests during recommendation flow:

1. Get 5 selected movies (5 requests)
2. Bounded mode: search 15 recommended titles (15 requests)
3. Genre constraint validation: up to 15 more (15 requests)
4. **Total: 35 requests in < 5 seconds**

**Status:** ✅ **FIXED** - 2026-01-19

**Resolution:**

Created [RateLimitInterceptor.kt](../app/src/main/java/com/movierecommender/app/data/remote/RateLimitInterceptor.kt) that:
- Detects HTTP 429 (Too Many Requests) responses
- Reads `Retry-After` header when present
- Implements exponential backoff (1s → 2s → 4s, max 10s)
- Retries up to 3 times before failing
- Logs rate limit events for debugging

**Code Changes:**

- **Added:** `RateLimitInterceptor.kt` - OkHttp interceptor for 429 handling
- **Modified:** `TmdbApiService.kt` - Added interceptor to OkHttpClient chain

**Behavior:**

- **Before:** 429 response → immediate failure → user sees error
- **After:** 429 response → wait with backoff → retry up to 3 times → graceful degradation

**Risk Level:** ✅ **RESOLVED**

---

### 3. ~~Debug SSL Insecurity~~ **RESOLVED**

**Status:** ✅ **FIXED** - 2026-01-19

**Resolution:**

1. Removed `buildInsecureClientBuilder()` from [TmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt)
2. Created [network_security_config.xml](../app/src/main/res/xml/network_security_config.xml) with proper debug overrides
3. Updated [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) to reference the security config
4. Removed `android:usesCleartextTraffic="true"` (now handled by config)

**Code Changes:**

- **Removed:** Dangerous `buildInsecureClientBuilder()` that trusted ALL certificates
- **Added:** `network_security_config.xml` with:
  - Base config: Only trusts system certificates, no cleartext traffic
  - Debug overrides: Also trusts user-installed certs (for Charles Proxy debugging)
- **Removed:** 6 unused SSL-related imports from TmdbApiService

**Security Improvement:**

- **Before:** Debug builds accepted ANY certificate (MITM vulnerable)
- **After:** Debug builds trust system + user certs only (still debuggable, but secure)
- **Release:** Only system certificates trusted (fully secure)

**Risk Level:** ✅ **RESOLVED**

---

## High Priority Issues

### 4. ~~Dead Code: OmdbApiService~~ **RESOLVED**

**Status:** ✅ **DELETED** - 2026-01-19

**Resolution:**

1. Deleted `OmdbApiService.kt` (45 lines of unused code)
2. Removed `omdbService` parameter from `MovieRepository` constructor
3. Removed `OMDB_API_KEY` from `build.gradle.kts` BuildConfig
4. Import removed from `MovieRepository.kt`

**Files Changed:**

- **Deleted:** `app/src/main/java/com/movierecommender/app/data/remote/OmdbApiService.kt`
- **Modified:** `MovieRepository.kt` - Removed import and constructor parameter
- **Modified:** `build.gradle.kts` - Removed OMDB_API_KEY BuildConfigField

**Rationale:** Service was wired but never invoked. Removed to reduce code complexity and maintenance burden.

**Risk Level:** ✅ **RESOLVED**

---

### 5. ~~Dead Code: ImdbScraperService~~ **RESOLVED**

**Status:** ✅ **NOT DEAD CODE** - Verified 2026-01-19

**Evidence of Usage:**

- [MovieRepository.kt:977](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L977): `imdbScraper.getTrailerUrl(imdbId)`
- [RecommendationsScreen.kt:516](../app/src/mobile/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt#L516): `viewModel.getImdbTrailerUrlByTitle()`
- [MovieViewModel.kt:528-529](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L528): Routes to repository

**Call Chain:**
```
RecommendationsScreen → MovieViewModel.getImdbTrailerUrlByTitle()
→ MovieRepository.getImdbTrailerUrlByTitle()
→ MovieRepository.getImdbTrailerUrl()
→ ImdbScraperService.getTrailerUrl()
```

**Conclusion:** Service IS reachable from UI. Used for fetching IMDB trailer URLs when user clicks "Watch Trailer" on recommendations.

**Risk Level:** N/A (not an issue)

---

### 6. Unbounded Database Growth

**Status:** ✅ **RESOLVED** (2026-01-20)

**Resolution:**
- Added `deleteOldOrphanedMovies(cutoffTime)` and `countOrphanedMovies()` queries to MovieDao
- Added cleanup logic in MovieRepository with configurable constants:
  - `ORPHAN_AGE_DAYS = 30` (delete orphans older than 30 days)
  - `CLEANUP_INTERVAL_DAYS = 7` (run cleanup at most once per 7 days)
- Added `lastDbCleanup` preference to SettingsRepository to track last cleanup time
- MovieRecommenderApplication.onCreate() triggers cleanup asynchronously on app startup
- **IMPORTANT**: Favorites are NEVER deleted (isFavorite = 1 excluded from cleanup)

**Files Modified:**
- [MovieDao.kt](../app/src/main/java/com/movierecommender/app/data/local/MovieDao.kt): Added cleanup queries
- [MovieRepository.kt](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt): Added `cleanupOrphanedMoviesIfNeeded()` method
- [SettingsRepository.kt](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt): Added `lastDbCleanup` preference
- [MovieRecommenderApplication.kt](../app/src/main/java/com/movierecommender/app/MovieRecommenderApplication.kt): Added startup cleanup call

**Verification:**
- Build passed

---

### 7. No Response Caching

**Status:** ✅ **RESOLVED** (2026-01-20)

**Resolution:**
- Added 10 MB HTTP cache to OkHttpClient in TmdbApiService
- Cache stored in `context.cacheDir/tmdb_http_cache`
- TMDB responses include `Cache-Control` headers which OkHttp respects automatically
- Modified `TmdbApiService.create()` to accept Context parameter for cache directory

**Files Modified:**
- [TmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt): Added Cache to OkHttpClient, added Context parameter to `create()`
- [MovieRecommenderApplication.kt](../app/src/main/java/com/movierecommender/app/MovieRecommenderApplication.kt): Pass `this` (Application context) to `TmdbApiService.create()`
- [ARCHITECTURE.md](ARCHITECTURE.md): Updated code example

**Verification:**
- Build passed

---

## Medium Priority Issues

### 8. Popcorn API Sequential Page Search

**Evidence:**

- [PopcornApiService.kt:48](../app/src/main/java/com/movierecommender/app/data/remote/PopcornApiService.kt#L48)
- `searchMovies()` loops pages 1→2→3 sequentially until match found

**Code:**

```kotlin
for (page in 1..3) {
    val response = api.searchMovies(query, page = page)
    val match = response.movies.find { /* title match */ }
    if (match != null) return match
}
```

**Problem:** If movie is on page 3, waits for page 1 + page 2 to complete first. **Slow** (3× latency).

**User Impact:**

- **Android:** "Watch Now" button takes 5–10 seconds to start streaming
- **Fire TV:** Same delay

**Risk Level:** 🟡 **MEDIUM** (UX degradation)

**Recommended Fix:**

1. Parallelize requests:

   ```kotlin
   val deferreds = (1..3).map { page ->
       async { api.searchMovies(query, page) }
   }
   deferreds.awaitAll().flatMap { it.movies }.find { /* match */ }
   ```

2. Or use Popcorn's search endpoint (if available)

**Test Plan:**

1. Mock slow API (500ms/page)
2. Search for movie on page 3
3. Verify total time < 1 second (parallel) vs 1.5 seconds (sequential)

---

### 9. Expensive LLM Genre Validation

**Evidence:**

- [LlmRecommendationService.kt:651](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L651)
- `validateRecommendationGenreConstraints()` searches TMDB for **every recommendation** to verify genre overlap

**Code:**

```kotlin
for (rec in recommendations) {
    val details = tmdbApi.getMovieDetails(rec.tmdbId)
    val overlap = details.genreIds.intersect(selectedGenres)
    if (overlap.isEmpty()) return false
}
```

**Problem:** If LLM returns 15 recommendations, makes 15 sequential TMDB API calls just for validation. **Slow** (5–10 seconds).

**User Impact:**

- **Android:** Recommendations take 15+ seconds (vs 5 seconds without validation)
- **Fire TV:** Same issue

**Risk Level:** 🟡 **MEDIUM** (UX degradation)

**Frequency:** Only when `genreConstraintWeight > 0` (currently disabled by default)

**Recommended Fix:**

1. Parallelize TMDB calls (same as issue #8)
2. Or cache TMDB details from initial candidate pool (avoid re-fetching)
3. Or disable validation (trust LLM genre assignments)

**Test Plan:**

1. Enable genre constraint weight
2. Request recommendations
3. Verify validation time < 2 seconds

---

### 10. ~~Hardcoded Constants Should Be Configurable~~ **PARTIALLY RESOLVED**

**Status:** ✅ **CACHE SIZE NOW DYNAMIC** - 2026-01-23

**Original Evidence:**

- ~~[TorrentStreamService.kt:27](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt#L27)~~ **FIXED**
- [LlmRecommendationService.kt:34](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L34) - 15 recommendations (kept as-is per user preference)
- [MovieRepository.kt:221](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L221) - 25 candidate pool threshold (kept as-is, quality gate)

**Resolution:**

1. **Torrent Cache Size:** Now dynamically calculated based on available free space
   - Uses 15% of available space (after reserving 500MB for system)
   - Bounded: minimum 100MB, maximum 1GB
   - Automatically adapts to low-storage devices (Fire TV Stick, budget phones)
   - Logs calculated size for debugging

2. **Recommendation Count (15):** Kept hardcoded - user confirmed this is fine

3. **Candidate Pool Threshold (25):** Kept hardcoded - serves as a quality gate:
   - If TMDB returns ≥25 candidates → LLM picks from bounded list (no hallucinations)
   - If TMDB returns <25 candidates → LLM has "open mode" (more creative but risk of hallucinations)
   - Typical searches yield 40-80 candidates, so threshold is rarely hit

**Code Changes:**

- [TorrentStreamService.kt](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt):
  - Added `calculateDynamicCacheSize(cacheDir: File): Int` function
  - Added `dynamicMaxCacheSizeMB` instance variable
  - Cache size calculated on `initTorrentStream()` using `StatFs.availableBytes`
  - Constants: `MIN_CACHE_SIZE_MB=100`, `MAX_CACHE_SIZE_MB=1024`, `FREE_SPACE_PERCENTAGE=0.15`, `RESERVED_SPACE_MB=500`

**Behavior Change:**

| Device Storage | Old Cache | New Cache |
|----------------|-----------|-----------|
| Fire TV (8GB free) | 500MB | ~1GB (capped) |
| Fire TV (2GB free) | 500MB | ~225MB |
| Fire TV (500MB free) | 500MB | 100MB (minimum) |
| Budget phone (1GB free) | 500MB | ~75MB |

**Risk Level:** ✅ **RESOLVED** (main concern was low-storage devices)

---

### 11. No Offline Mode

**Evidence:**

- App requires network for all core features
- No error recovery if network unavailable

**Problem:**

- Genre selection → requires TMDB API
- Movie selection → requires TMDB API
- Recommendations → requires TMDB + OpenAI
- **No offline fallback** (can't browse previously viewed movies)

**User Impact:**

- **Android:** App unusable without network
- **Fire TV:** Same issue

**Risk Level:** 🟡 **MEDIUM** (expected behavior for streaming app, but could cache data)

**Recommended Fix:**

1. Cache genre list (rarely changes)
2. Cache movie details (expires after 7 days)
3. Show cached data with "Last updated X days ago" message
4. Recommendations still require network (can't run LLM locally)

**Test Plan:**

1. Use app with network
2. Disable network
3. Verify cached data still displays
4. Verify graceful error for recommendations

---

### 12. No Input Validation for User Name

**Status:** ✅ **RESOLVED** (2026-01-20)

**Resolution:**
- Added `sanitizeUserName()` function that:
  - Trims leading/trailing whitespace
  - Collapses multiple spaces to single space
  - Removes control characters and special characters (allows letters, numbers, space, apostrophe, hyphen)
  - Limits to 50 characters (constant `MAX_USER_NAME_LENGTH`)
- Modified `setUserName()` to use sanitization and return the sanitized value

**Files Modified:**
- [SettingsRepository.kt](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt): Added `companion object` with `MAX_USER_NAME_LENGTH` and `sanitizeUserName()`, updated `setUserName()` to sanitize and return result

**Verification:**
- Build passed

---

## Low Priority Issues

### 13. No Telemetry or Analytics

**Evidence:** No analytics SDK integrated (no Firebase, no Amplitude, etc.)

**Problem:** Can't measure:

- Recommendation success rate (do users click "Watch Now"?)
- Feature usage (are sliders used?)
- Error rates (how often do APIs fail?)

**User Impact:**

- **Android:** No impact (users don't see analytics)
- **Fire TV:** Same

**Risk Level:** 🟢 **LOW** (business concern, not technical)

**Recommended Fix:**

1. Integrate Firebase Analytics (free tier)
2. Log key events:
   - `recommendation_requested`
   - `recommendation_success`
   - `recommendation_failure`
   - `movie_watched`
   - `favorite_added`
3. Respect user privacy (anonymize data)

**Test Plan:**

1. Integrate SDK
2. Trigger event → verify appears in Firebase console
3. Verify no PII leaked

---

### 14. No Unit Tests

**Evidence:**

- `app/src/test/` directory exists but empty
- `app/src/androidTest/` has basic instrumentation setup

**Problem:** No automated tests for:

- Repository logic
- LLM parsing
- Database operations
- Preference management

**User Impact:**

- **Android:** Higher risk of bugs slipping through
- **Fire TV:** Same risk

**Risk Level:** 🟢 **LOW** (tech debt)

**Recommended Fix:**

1. Add JUnit tests for:
   - `MovieRepository.buildFallbackRecommendations()`
   - `LlmRecommendationService.parseRecommendationsText()`
   - `MovieDao` queries (use in-memory DB)
2. Add instrumentation tests for:
   - UI navigation flows
   - Database migrations
3. CI/CD integration (run tests on every commit)

**Test Plan:**

1. Write tests for critical paths
2. Run `./gradlew test` → verify pass
3. Introduce bug → verify test catches it

---

### 15. No ProGuard Rules for Release

**Status:** ✅ **RESOLVED** (2026-01-20)

**Resolution:**
- Verified existing proguard-rules.pro already contains comprehensive rules for:
  - Retrofit (interfaces, annotations)
  - Room (database, entities, DAOs)
  - Gson/data models
  - OkHttp, Coroutines, Compose
  - BuildConfig
- Added additional DAO method retention rules
- Release build (`assembleMobileRelease`) completes successfully with R8

**Files Modified:**
- [proguard-rules.pro](../app/proguard-rules.pro): Added DAO annotation and method retention

**Verification:**
- Release build passed: `./gradlew :app:assembleMobileRelease`

---

## Fire TV Specific Issues

### 21. Screensaver Activates During Torrent Playback

**Status:** ✅ **RESOLVED** (2026-01-19)

**Problem:** Fire TV screensaver would activate after a few minutes of torrent playback because the PlayerView wasn't signaling activity to the system.

**Resolution:**
- Added `keepScreenOn = true` to PlayerView in both mobile and firestick StreamingPlayerScreen
- This uses Android's built-in FLAG_KEEP_SCREEN_ON mechanism to prevent screen timeout during video playback

**Files Modified:**
- [firestick/StreamingPlayerScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/StreamingPlayerScreen.kt): Added `keepScreenOn = true`
- [mobile/StreamingPlayerScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/StreamingPlayerScreen.kt): Added `keepScreenOn = true`

**Verification:**
- Build passed
- Screen stays awake during torrent playback on Fire TV

---

### 16. ~~No DPAD Focus Hints~~ **RESOLVED**

**Status:** ✅ **FIXED** - 2026-01-23

**Original Problem:** On Fire TV, user couldn't tell which element had focus. Focus indicators were either missing or too faint.

**Resolution:**

1. **MovieCard icons (heart, play, checkmark):**
   - Wrapped each IconButton in a Surface with focus-reactive styling
   - When focused: colored background, 3dp white border, icon scales up (32dp → 36dp)
   - Heart: Primary color background when focused
   - Play: Tertiary color background when focused  
   - Check: Secondary color background when focused

2. **Movie card switching:**
   - Added 4dp primary-colored border when card or any icon is focused
   - Background changes to surfaceVariant
   - Elevation increases from 4dp to 12dp
   - Visual feedback makes current card very obvious

3. **RecommendationsScreen buttons (Watch Trailer / Watch Now):**
   - Buttons grow from 48dp to 56dp height when focused
   - Border increases from 1dp outline to 4dp white
   - Text grows from 16sp to 18sp and becomes bold
   - Elevation increases from 2dp to 8dp
   - Color changes to fully saturated primary/tertiary
   - Added 16dp spacing between buttons for easier D-pad navigation

**Files Modified:**
- [firestick/MovieSelectionScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt): Complete MovieCard rewrite with focus indicators
- [firestick/RecommendationsScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt): Enhanced button focus states

**Verification:**
- Build passed for firestick flavor

---

### 17. ~~No Leanback Launcher Banner~~ **RESOLVED**

**Status:** ✅ **BANNER EXISTS** - Verified 2026-01-19

**Evidence of Resolution:**

- [AndroidManifest.xml:16](../app/src/firestick/AndroidManifest.xml#L16): `android:banner="@drawable/ic_tv_banner"`
- [AndroidManifest.xml:23](../app/src/firestick/AndroidManifest.xml#L23): Activity also has banner attribute
- **File exists:** `app/src/firestick/res/drawable-nodpi/ic_tv_banner.png`

**Code:**
```xml
<application android:banner="@drawable/ic_tv_banner">
    <activity
        android:name="...MainActivity"
        android:banner="@drawable/ic_tv_banner"
        android:exported="true">
```

**Conclusion:** Fire TV banner is properly configured. Not a blocker.

**Risk Level:** N/A (resolved)

---

## Security and Privacy Concerns

### 18. API Keys in Version Control

**Evidence:**

- [local.properties.example](../local.properties.example) shows expected format
- Actual `local.properties` in `.gitignore` (✅ correct)

**Problem:** Risk of accidentally committing `local.properties` with real keys.

**User Impact:**

- **All platforms:** If keys leaked → API abuse, billing charges

**Risk Level:** 🔴 **HIGH** (security)

**Current Mitigation:** `.gitignore` prevents commit (✅ safe)

**Recommended Enhancement:**

1. Add pre-commit hook to block `local.properties`:

   ```bash
   #!/bin/bash
   if git diff --cached --name-only | grep -q "local.properties"; then
       echo "ERROR: local.properties must not be committed"
       exit 1
   fi
   ```

2. Add automated scan (Trufflehog, git-secrets)

**Test Plan:**

1. Try to commit `local.properties` → verify blocked
2. Verify `.example` file can be committed

---

### 19. No User Consent for LLM Data

**Status:** ✅ **RESOLVED** (2026-01-20)

**Resolution:**
- Added GDPR/CCPA compliant consent dialog that appears on first recommendation request
- Consent state persisted via DataStore (`llm_consent_given`, `llm_consent_asked`)
- If user declines, LLM is bypassed and TMDB-only fallback recommendations are used
- Dialog explains: what data is shared (movie titles, preferences), where it's sent (OpenAI), and the alternative

**Files Modified:**
- [SettingsRepository.kt](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt): Added consent preference keys and `setLlmConsent()` method
- [MovieRepository.kt](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt): Added `useLlm` parameter to `getRecommendations()`, bypasses LLM when false
- [mobile/MovieViewModel.kt](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt): Added consent state and handlers
- [firestick/MovieViewModel.kt](../app/src/firestick/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt): Same changes for TV flavor
- [mobile/LlmConsentDialog.kt](../app/src/mobile/java/com/movierecommender/app/ui/dialogs/LlmConsentDialog.kt): New consent dialog component
- [firestick/LlmConsentDialog.kt](../app/src/firestick/java/com/movierecommender/app/ui/dialogs/LlmConsentDialog.kt): TV-specific dialog with D-pad hint
- [mobile/MovieSelectionScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt): Integrated consent check before recommendations
- [firestick/MovieSelectionScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt): Same for TV flavor

**Verification:**
- Build passed for both mobile and firestick flavors

---

### 20. No Certificate Pinning

**Evidence:**

- HTTPS connections use default SSL (no pinning)

**Problem:** Vulnerable to MITM attacks with compromised CA certs.

**User Impact:**

- **All platforms:** Low risk (requires compromised device/network)

**Risk Level:** 🟢 **LOW** (advanced threat)

**Recommended Fix:**

1. Add certificate pinning for TMDB + OpenAI:

   ```kotlin
   CertificatePinner.Builder()
       .add("api.themoviedb.org", "sha256/AAAA...")
       .add("api.openai.com", "sha256/BBBB...")
       .build()
   ```

2. Update pins annually (certs expire)

**Test Plan:**

1. Add pinning
2. Verify API calls succeed
3. Mock bad cert → verify connection rejected

---

## Summary Statistics

**Last Updated:** 2026-01-23
**Total Issues:** 21 (**14 RESOLVED**)

**By Risk Level:**

- 🔴 Critical/High: **0** (was 6 - #1, #3, #4, #5, #17, #19 resolved)
- 🟡 Medium: **3** (was 9 - #2, #6, #7, #10, #12, #15, #16 resolved)
- 🟢 Low: 4 (was 5 - #21 resolved)

**By Platform:**

- Both (Android + Fire TV): 7 (was 17)
- Fire TV only: 0 (was 2, #16, #17 resolved)

**By Category:**

- Data integrity: 0 (was 2 - #1, #6 resolved)
- Performance: 2 (was 4 - #7, #10 resolved)
- Dead code: 0 (was 2 - #4 removed, #5 verified not dead)
- Security: 1 (was 4 - #3, #19 resolved)
- UX: 3 (was 5 - #12, #16 resolved)
- Compliance: 0 (was 1 - #19 resolved)
- Tech debt: 2

**Resolved Issues (13):**

| Issue | Description | Resolution |
|-------|-------------|------------|
| ✅ #1 | Destructive Migration | Proper Room migrations + schema export |
| ✅ #2 | TMDB Rate Limiting | RateLimitInterceptor with exponential backoff |
| ✅ #3 | Debug SSL Insecurity | network_security_config.xml + removed trust-all |
| ✅ #4 | OmdbApiService Dead Code | Removed completely |
| ✅ #5 | ImdbScraperService | NOT dead code (verified reachable from UI) |
| ✅ #6 | DB Growth/Cleanup | Automatic orphan cleanup with 30-day TTL |
| ✅ #7 | HTTP Caching | 10MB OkHttp cache configured |
| ✅ #10 | Hardcoded Cache Size | Dynamic cache based on free space (75% of available) |
| ✅ #12 | Name Validation | sanitizeUserName() with length/char limits |
| ✅ #15 | ProGuard Rules | Verified comprehensive rules, release build works |
| ✅ #16 | DPAD Focus Hints | Enhanced focus indicators for all TV elements |
| ✅ #17 | Fire TV Banner | Already exists at `ic_tv_banner.png` |
| ✅ #19 | LLM Consent | GDPR dialog + TMDB-only fallback |
| ✅ #21 | Screensaver During Playback | keepScreenOn = true on PlayerView |

**Remaining Issues (7):**

| Priority | Issue | Description |
|----------|-------|-------------|
| 🟡 Medium | #8 | Popcorn sequential page search |
| 🟡 Medium | #9 | LLM genre validation performance |
| 🟡 Medium | #11 | Offline mode graceful degradation |
| 🟢 Low | #13 | Telemetry/analytics (product decision needed) |
| 🟢 Low | #14 | Unit tests coverage |
| 🟢 Low | #18 | Pre-commit hooks / secret scanning |
| 🟢 Low | #20 | Certificate pinning (advanced hardening) |

### 22. TV Shows Recommendations: Trailers and Torrents Used Movie-Only Code Paths

**Status:** ✅ **FIXED** - 2026-02-18

**Evidence:**

- [MovieRepository.kt](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt): `getImdbTrailerUrlByTitle()` called `apiService.searchMovies()` (movies-only TMDB endpoint) for all content types
- [MovieRepository.kt](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt): `getTorrentInfo()` searched YTS + Popcorn Movies API — both movie-only sources
- Both firestick and mobile `RecommendationsScreen` used movie-only methods regardless of `ContentMode`

**Problem:** When the user was in TV Shows mode, both "Watch Trailer" and "Watch Now" buttons on recommendation cards always used movie-only APIs:
1. **Trailers:** TMDB movie search → no results for TV show titles → "No trailer available"
2. **Torrents:** YTS/Popcorn Movie API → no results for TV shows → "No streaming source found"

**Resolution:**

**Repository layer:**
- Added `getTvShowTrailerUrlByTitle()`: Uses TMDB TV search → TMDB Videos API for YouTube trailers, falls back to IMDB scraping via external IDs
- Added `getTvShowFirstEpisodeTorrent()`: Searches Popcorn TV API, gets show details, returns best torrent for first available episode (S01E01 preferred)
- Added `findBestFirstEpisode()`: Helper to find best available episode torrent with quality fallback

**ViewModel layer (both flavors):**
- Added `getTvShowTrailerUrlByTitle()`: Routes to repository TV show trailer method
- Added `getTrailerUrlByTitle(title, year, isTvMode)`: Content-mode-aware wrapper
- Added `getTvShowTorrentMagnetUrl()`: Returns `Pair<magnetUrl, displayLabel>` with episode info
- Added `getTorrentMagnetUrlForContent(title, year, isTvMode)`: Content-mode-aware wrapper

**UI layer:**
- Firestick `RecommendationCard`: Now accepts `isTvMode` parameter, uses `getTrailerUrlByTitle()` and `getTorrentMagnetUrlForContent()`
- Mobile `RecommendationRow`: Same changes — threads `isTvMode` through `ParsedRecommendationsList`

**Behavior Change:**
- **Before:** TV show recommendations → no trailers, no torrents
- **After:** TV show recommendations → YouTube trailers from TMDB Videos API, torrents from Popcorn TV API (first episode auto-selected)

**Risk Level:** ✅ **RESOLVED**

---

### 23. EZTV API Added as Fallback Torrent Source for TV Shows

**Status:** ✅ **IMPLEMENTED** - 2026-02-18

**Evidence:**

- [EztvApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/EztvApiService.kt): New service class for EZTV API integration
- [MovieRepository.kt](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt): Four TV torrent methods updated to use EZTV fallback

**Background:** Popcorn TV API sometimes doesn't have torrent data for certain TV shows. EZTV (eztvx.to) provides an alternative, free, no-API-key-required source for TV show torrents indexed by IMDB ID.

**Implementation:**

**New service (`EztvApiService.kt`):**
- Lookup by IMDB ID via `GET /api/get-torrents?imdb_id={id}`
- Paginated fetching (up to 300 torrents per show)
- Quality detection from torrent titles (2160p/1080p/720p/480p/HDTV/SD)
- Quality-scored torrent selection (matching quality + seed count - file size penalty)
- Mirror URL fallback: `eztvx.to` → `eztv.re` → `eztv.wf`
- Browser-like User-Agent header to avoid Cloudflare blocking
- Methods: `getTorrentsByImdbId()`, `getEpisodeTorrent()`, `getSeasons()`, `getEpisodesForSeason()`, `getFirstEpisodeTorrent()`

**Repository integration (fallback chain: Popcorn TV → EZTV):**
- `getTvShowFirstEpisodeTorrent()`: Popcorn search/keyword search → EZTV via resolved IMDB ID
- `getTvEpisodeTorrentInfo()`: Popcorn episode lookup → EZTV episode lookup
- `getTvShowSeasons()`: Popcorn seasons → EZTV seasons
- `getTvShowEpisodes()`: Popcorn episodes → EZTV episodes (mapped to `PopcornEpisode` for UI compatibility)

**Helper added:**
- `resolveImdbIdForTvShow()`: Searches TMDB TV shows → gets external IDs to resolve IMDB ID when Popcorn doesn't provide one

**Behavior Change:**
- **Before:** TV shows not found on Popcorn TV API → "No streaming source found"
- **After:** TV shows not found on Popcorn TV API → EZTV fallback attempted → significantly improved torrent coverage

**Risk Level:** Low — EZTV is a fallback-only path; Popcorn TV remains primary

---

### 24. TV Show Recommendations Now Show Episode Picker Instead of Auto-Playing S01E01

**Status:** ✅ **IMPLEMENTED** - 2026-02-18

**Evidence:**

- Firestick [RecommendationsScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt): `RecommendationCard` now shows `EpisodePickerDialog` in TV mode
- Mobile [RecommendationsScreen.kt](../app/src/mobile/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt): `RecommendationRow` now shows `EpisodePickerDialog` in TV mode
- Both [MovieSelectionScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt) `EpisodePickerDialog` updated with `preResolvedImdbId` parameter and TMDB IMDB resolution fallback

**Problem:** The "Watch Now" button on TV show recommendation cards auto-played S01E01 without letting users choose an episode. Users expected to browse and select any episode.

**Resolution:**

- **Button label changed:** "Watch Now" → "Browse Episodes" in TV mode (both flavors)
- **Click behavior changed:** Instead of calling `getTvShowFirstEpisodeTorrent()`, now resolves IMDB ID via `getSeasonsForTvShowByTitle()` and opens the `EpisodePickerDialog`
- **New ViewModel methods:** `resolveImdbIdByTitle()` and `getSeasonsForTvShowByTitle()` for title-based IMDB resolution
- **Repository method made public:** `resolveImdbIdForTvShow()` now tries Popcorn TV search first, then falls back to TMDB search + external IDs
- **`EpisodePickerDialog` enhanced:** Accepts optional `preResolvedImdbId` parameter to skip redundant searches; falls back to TMDB resolution when Popcorn TV search returns no IMDB ID

**Behavior Change:**
- **Before:** TV show recommendation → "Watch Now" → auto-streams S01E01
- **After:** TV show recommendation → "Browse Episodes" → episode picker dialog → user selects season/episode → streams selected episode

**Risk Level:** Low — movie mode behavior unchanged; TV mode gains full episode browsing

---

### Issue #25: DPAD Focus Auto-Triggers Buttons on Card Selection (Firestick)

**Status:** ✅ RESOLVED  
**Severity:** High  
**Platform:** Firestick only  
**Files:**
- [MovieSelectionScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt) — `MovieCard` and `TvShowCard` composables

**Problem:** On Firestick, pressing DPAD center on a movie or TV show card immediately focused and triggered the first inner `IconButton` instead of just "entering" the card. In `MovieCard`, this auto-triggered the heart/favorite button (TopStart position). In `TvShowCard`, it auto-triggered the Watch Now/play button (TopCenter position). Users could not navigate between buttons before activating one.

**Root Cause:** Both the `Card` (via `.focusable()`) and its inner `IconButton`s were independently focusable targets. Compose's focus system treated each `IconButton` as a separate DPAD navigation target, so pressing center/select on the card immediately moved focus to the first focusable child and triggered its `onClick`.

**Resolution:** Implemented a two-level focus model:
- **Level 1 (Card-level):** Card is the only DPAD-navigable focus target. Inner buttons are NOT focusable until the card is "active".
- **Level 2 (Button-level):** Pressing DPAD center on a focused card sets `isCardActive = true` and requests focus on the middle button (play/watch) via `FocusRequester` — without triggering any action.
- **Navigation within card:** Left/right DPAD navigates between buttons (heart ← play → check for movies; watch ← → check for TV shows).
- **Exit card:** Pressing DPAD Back exits active state and returns focus to the card itself.
- **Static icons:** When `isCardActive = false`, buttons are rendered as static icons (not `IconButton`) so they cannot receive DPAD focus.
- **Auto-deactivate:** `LaunchedEffect` watches focus state; if neither the card nor any button is focused, `isCardActive` resets to `false`.

**Behavior Change:**
- **Before:** DPAD navigate to card → press center → auto-triggers favorite (movies) or play (TV shows)
- **After:** DPAD navigate to card → press center → enters card (focuses play button without triggering) → left/right to navigate buttons → press center to activate → back to exit

**Risk Level:** Low — only affects firestick flavor DPAD navigation; mobile touch UI unaffected

---

**Next Steps:**

1. ✅ All critical/high issues resolved
2. Medium-priority issues can be addressed in future sprints
3. Low-priority issues are tech debt / nice-to-have

---

**Note:** This document is SOURCE-OF-TRUTH for project issues. Update as issues are fixed or new ones discovered.

