# KNOWN_ISSUES.md

**Last Updated:** 2026-01-19
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

**Evidence:**

- [MovieDao.kt](../app/src/main/java/com/movierecommender/app/data/local/MovieDao.kt)
- No cleanup queries for orphaned movies
- Movies persist forever (no TTL, no auto-delete)

**Problem:**

1. User selects 5 movies → 5 rows inserted with `isSelected = 1`
2. User gets recommendations → 15 rows inserted with `isRecommended = 1`
3. User clicks "Start Over" → flags cleared (`isSelected = 0`, `isRecommended = 0`)
4. **Movies remain in DB** (20 rows with all flags = 0)
5. Repeat 100 times → 2000 orphaned rows

**User Impact:**

- **Android:** DB grows indefinitely (~500 bytes/movie)
- **Fire TV:** Same issue
- After 1 year heavy use: ~10K movies = 5 MB (tolerable, but wasteful)

**Risk Level:** 🟢 **LOW** (performance impact minimal)

**Recommended Fix:**

1. Add periodic cleanup job:

   ```kotlin
   @Query("""
       DELETE FROM movies 
       WHERE isSelected = 0 
         AND isRecommended = 0 
         AND isFavorite = 0 
         AND timestamp < :cutoffTime
   """)
   suspend fun deleteOldOrphanedMovies(cutoffTime: Long)
   ```

2. Call on app startup if last cleanup > 7 days
3. Preserve favorites (never delete `isFavorite = 1`)

**Test Plan:**

1. Insert 100 orphaned movies
2. Run cleanup with cutoffTime = 30 days ago
3. Verify orphans deleted, favorites preserved

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

### 10. Hardcoded Constants Should Be Configurable

**Evidence:**

- [TorrentStreamService.kt:27](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt#L27)

  ```kotlin
  private const val MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB
  ```

- [LlmRecommendationService.kt:34](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L34)

  ```kotlin
  private const val RECOMMENDATION_COUNT = 15
  ```

- [MovieRepository.kt:56](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L56)

  ```kotlin
  private const val MIN_CANDIDATE_POOL_SIZE = 25
  ```

**Problem:** These values are hardcoded. Users can't adjust:

- Torrent cache size (500 MB might be too much for low-storage devices)
- Recommendation count (15 might be too many)
- Candidate pool size (25 affects LLM quality)

**User Impact:**

- **Android:** No customization
- **Fire TV:** Same limitation

**Risk Level:** 🟢 **LOW** (nice-to-have)

**Recommended Fix:**

1. Move to `SettingsRepository` as configurable preferences
2. Add UI sliders in Settings screen
3. Default values remain same (backward compatible)

**Test Plan:**

1. Add preferences with defaults
2. Change value in UI → verify behavior changes
3. Verify defaults work if preference not set

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

**Evidence:**

- [proguard-rules.pro](../app/proguard-rules.pro) exists but minimal rules

**Problem:** Release builds might strip classes needed by:

- Retrofit (API interfaces)
- Room (DAO methods)
- Gson (JSON parsing)

**User Impact:**

- **Android:** Release build might crash (reflection failures)
- **Fire TV:** Same risk

**Risk Level:** 🟡 **MEDIUM** (only affects release builds)

**Recommended Fix:**

1. Add comprehensive rules:

   ```proguard
   # Retrofit
   -keep interface com.movierecommender.app.data.remote.** { *; }
   
   # Room
   -keep class com.movierecommender.app.data.local.** { *; }
   
   # Gson
   -keep class com.movierecommender.app.data.model.** { *; }
   ```

2. Test release build thoroughly

**Test Plan:**

1. Build release APK with ProGuard
2. Install on device
3. Verify all features work (especially API calls)

---

## Fire TV Specific Issues

### 16. No DPAD Focus Hints

**Evidence:**

- Compose UI uses default focus behavior
- No visual indicator which element has focus

**Problem:** On Fire TV, user can't tell which movie card is focused. Must press D-pad to see focus change.

**User Impact:**

- **Android:** None (touch UI)
- **Fire TV:** Confusing navigation

**Risk Level:** 🟡 **MEDIUM** (UX)

**Recommended Fix:**

1. Add focus border to all focusable elements:

   ```kotlin
   Modifier.onFocusChanged { state ->
       if (state.isFocused) {
           // Highlight with border
       }
   }
   ```

2. Use `FocusRequester` to auto-focus first element

**Test Plan:**

1. Launch on Fire TV
2. Navigate with D-pad
3. Verify focused element has visible highlight

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

**Total Issues:** 20 (8 RESOLVED)

**By Risk Level:**

- 🔴 Critical/High: 0 (was 6, #1, #3, #4, #5, #17, #19 resolved)
- 🟡 Medium: 7 (was 9, #2, #7 resolved)
- 🟢 Low: 5 (was 5, #12 resolved but same count)

**By Platform:**

- Both (Android + Fire TV): 12 (was 17)
- Fire TV only: 0 (was 2, #17 resolved)

**By Category:**

- Data integrity: 2
- Performance: 4
- Dead code: 1 (was 2, #5 ImdbScraperService NOT dead)
- Security: 4
- UX: 5
- Compliance: 1
- Tech debt: 2

**Resolved Issues:**

- ✅ #5: ImdbScraperService - NOT dead code (verified reachable from UI)
- ✅ #17: Fire TV Banner - already exists at `ic_tv_banner.png`

**Next Steps:**

1. Fix critical issues first (#1, #3, #19)
2. Address high-priority issues (#2, #4)
3. Schedule medium/low issues for future sprints

---

**Note:** This document is SOURCE-OF-TRUTH for project issues. Update as issues are fixed or new ones discovered.
