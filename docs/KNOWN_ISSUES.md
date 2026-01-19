# KNOWN_ISSUES.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Purpose

Comprehensive inventory of broken logic, dead code, unwired configs, tech debt, and security concerns. Each issue includes evidence, impact analysis, risk level, and recommended fix.

---

## Critical Issues (High User Impact)

### 1. Destructive Database Migration

**Evidence:**
- [AppDatabase.kt:28](../app/src/main/java/com/movierecommender/app/data/local/AppDatabase.kt#L28)
- `fallbackToDestructiveMigration()` configured
- Database version = 2

**Problem:** When schema version bumps (2 → 3), Room drops all tables and recreates from scratch. **All user favorites lost permanently.**

**User Impact:**
- **Android:** App update → lose all favorites
- **Fire TV:** App update → lose all favorites
- No warning, no backup, no recovery

**Risk Level:** 🔴 **CRITICAL**

**Recommended Fix:**
1. Implement proper Room migrations:
   ```kotlin
   val MIGRATION_2_3 = object : Migration(2, 3) {
       override fun migrate(database: SupportSQLiteDatabase) {
           // Add new column while preserving data
           database.execSQL("ALTER TABLE movies ADD COLUMN new_field TEXT DEFAULT ''")
       }
   }
   ```
2. Remove `fallbackToDestructiveMigration()`
3. Add automated migration tests

**Test Plan:**
1. Install app with DB version 2, add favorites
2. Upgrade to version 3 → verify favorites preserved
3. Test migration v2→v3, v3→v4, v2→v4 (skip)

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

**User Impact:**
- **Android:** HTTP 429 → recommendations fail → user sees error message
- **Fire TV:** Same issue

**Risk Level:** 🔴 **HIGH**

**Frequency:** Rare with default settings (bounded mode disabled), but if enabled → affects every recommendation session.

**Recommended Fix:**
1. Add OkHttp interceptor to detect 429 status:
   ```kotlin
   class RateLimitInterceptor : Interceptor {
       override fun intercept(chain: Interceptor.Chain): Response {
           val response = chain.proceed(chain.request())
           if (response.code == 429) {
               val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 2
               Thread.sleep(retryAfter * 1000L)
               return chain.proceed(chain.request())
           }
           return response
       }
   }
   ```
2. Implement request batching (delay 100ms between genre searches)
3. Cache TMDB search results (avoid repeat requests)

**Test Plan:**
1. Enable bounded mode
2. Select 5 movies, request recommendations
3. Monitor network traffic → verify no 429 errors
4. Simulate rate limit (mock server) → verify retry logic

---

### 3. Debug SSL Insecurity

**Evidence:**
- [TmdbApiService.kt:60](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt#L60)
- `buildInsecureClientBuilder()` disables SSL certificate validation

**Code:**
```kotlin
private fun buildInsecureClientBuilder(): OkHttpClient.Builder {
    val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )
    // ...
}
```

**Problem:** **Man-in-the-middle (MITM) attack vulnerability.** Accepts any SSL certificate, including attacker-controlled certs.

**User Impact:**
- **Android:** Network traffic interceptable (TMDB API key, movie data)
- **Fire TV:** Same vulnerability

**Risk Level:** 🔴 **CRITICAL** (security)

**Scope:** Debug builds only (comment says "for emulator compatibility")

**Recommended Fix:**
1. Remove `buildInsecureClientBuilder()` entirely
2. Configure emulator to trust system CA certs (proper solution)
3. Use Android Network Security Config for debug-only trust:
   ```xml
   <!-- network_security_config.xml -->
   <network-security-config>
       <debug-overrides>
           <trust-anchors>
               <certificates src="system" />
               <certificates src="user" />
           </trust-anchors>
       </debug-overrides>
   </network-security-config>
   ```
4. **Never ship insecure code in release builds**

**Test Plan:**
1. Remove insecure builder
2. Run on emulator → verify HTTPS works
3. If fails, configure emulator properly (don't weaken security)
4. Verify release build uses secure SSL

---

## High Priority Issues

### 4. Dead Code: OmdbApiService

**Evidence:**
- [OmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/OmdbApiService.kt) exists (45 lines)
- [MovieRepository.kt:975](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L975)
  ```kotlin
  private suspend fun fetchMovieDetailsFromOmdb(title: String): Movie? {
      return null // OMDB integration disabled for now
  }
  ```

**Problem:** Service interface defined, wired in Hilt, but **never called**. Returns `null` unconditionally.

**User Impact:**
- **Android:** None (dead code)
- **Fire TV:** None

**Risk Level:** 🟡 **MEDIUM** (code bloat, confusion)

**Recommended Fix:**
1. **Option A:** Delete `OmdbApiService.kt` + remove Hilt module
2. **Option B:** Implement integration (why was it added?)
3. Document decision in commit message

**Test Plan:**
1. Delete service
2. Run Gradle sync → verify no compile errors
3. Run app → verify no crashes
4. Search codebase for `OmdbApiService` → should be zero hits

---

### 5. Dead Code: ImdbScraperService

**Evidence:**
- [ImdbScraperService.kt](../app/src/main/java/com/movierecommender/app/data/remote/ImdbScraperService.kt) exists (30 lines)
- Not called from any UI flow
- No repository method uses it

**Problem:** Service defined, but **unreachable from UI**. Likely legacy from earlier iteration.

**User Impact:**
- **Android:** None (dead code)
- **Fire TV:** None

**Risk Level:** 🟡 **MEDIUM** (code bloat)

**Recommended Fix:**
1. Delete `ImdbScraperService.kt`
2. Remove from Hilt module
3. Document why it was removed (was it ever used?)

**Test Plan:**
1. Delete service
2. Run Gradle sync → verify no compile errors
3. Grep for `ImdbScraperService` → should be zero hits

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

**Evidence:**
- [TmdbApiService.kt](../app/src/main/java/com/movierecommender/app/data/remote/TmdbApiService.kt)
- No OkHttp cache configured
- Every request hits network

**Problem:** User navigates Genre Selection → Movie Selection → **back to Genre Selection** → same genre query hits TMDB again. Wastes bandwidth, increases latency.

**User Impact:**
- **Android:** Slower UI, unnecessary network usage
- **Fire TV:** Same issue (worse on slow networks)

**Risk Level:** 🟡 **MEDIUM** (UX degradation)

**Recommended Fix:**
1. Add OkHttp cache:
   ```kotlin
   val cache = Cache(
       directory = File(context.cacheDir, "http_cache"),
       maxSize = 10L * 1024 * 1024 // 10 MB
   )
   OkHttpClient.Builder()
       .cache(cache)
       .build()
   ```
2. Set cache headers (TMDB responses already have `Cache-Control`)
3. Cache expires after 24 hours (TMDB data rarely changes)

**Test Plan:**
1. Enable cache
2. Navigate Genre → Movie → Genre
3. Monitor network → second Genre request should be cache hit (no network call)

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

**Evidence:**
- [SettingsRepository.kt:51](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L51)
- `setUserName(name: String)` accepts any string

**Problem:** User can enter:
- Empty string → no validation
- Very long string (> 100 chars) → no limit
- Special characters → no sanitization

**User Impact:**
- **Android:** UI might overflow if name too long
- **Fire TV:** Same issue

**Risk Level:** 🟢 **LOW** (cosmetic)

**Recommended Fix:**
1. Add validation:
   ```kotlin
   suspend fun setUserName(name: String) {
       val sanitized = name.trim().take(50)
       if (sanitized.isNotBlank()) {
           context.dataStore.edit { it[Keys.USER_NAME] = sanitized }
       }
   }
   ```
2. Show error in UI if invalid

**Test Plan:**
1. Enter 100-char name → verify truncated to 50
2. Enter blank name → verify rejected
3. Enter valid name → verify saved

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

### 17. No Leanback Launcher Banner

**Evidence:**
- [AndroidManifest.xml (firestick)](../app/src/firestick/AndroidManifest.xml)
- `android:banner` attribute missing

**Problem:** Fire TV home screen shows default app icon instead of horizontal banner. **Required for Fire TV certification.**

**User Impact:**
- **Fire TV:** Unprofessional appearance on home screen

**Risk Level:** 🔴 **HIGH** (Fire TV certification blocker)

**Recommended Fix:**
1. Design 320×180px banner image
2. Add to `app/src/firestick/res/drawable/tv_banner.png`
3. Update manifest:
   ```xml
   <application android:banner="@drawable/tv_banner">
   ```

**Test Plan:**
1. Install on Fire TV
2. Return to home screen
3. Verify banner appears (not icon)

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

**Evidence:**
- App sends movie titles + user preferences to OpenAI
- No privacy policy link in app
- No user consent prompt

**Problem:** GDPR/CCPA compliance risk. Users don't know their data is sent to third-party AI.

**User Impact:**
- **All platforms:** Legal/privacy risk

**Risk Level:** 🔴 **HIGH** (legal/compliance)

**Recommended Fix:**
1. Add first-run consent screen:
   - "We use AI to generate recommendations. Your movie selections are sent to OpenAI. [Privacy Policy] [Accept] [Decline]"
2. If declined → disable LLM, use fallback recommendations only
3. Add privacy policy link in Settings

**Test Plan:**
1. Fresh install → verify consent prompt appears
2. Decline → verify fallback recommendations work
3. Accept → verify LLM recommendations work

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

**Total Issues:** 20

**By Risk Level:**
- 🔴 Critical/High: 6
- 🟡 Medium: 9
- 🟢 Low: 5

**By Platform:**
- Both (Android + Fire TV): 18
- Fire TV only: 2

**By Category:**
- Data integrity: 2
- Performance: 4
- Dead code: 2
- Security: 4
- UX: 5
- Compliance: 1
- Tech debt: 2

**Next Steps:**
1. Fix critical issues first (#1, #3, #17, #19)
2. Address high-priority issues (#2, #4, #5)
3. Schedule medium/low issues for future sprints

---

**Note:** This document is SOURCE-OF-TRUTH for project issues. Update as issues are fixed or new ones discovered.
