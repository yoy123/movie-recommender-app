# TESTING.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Current Testing Status

**Unit Tests:** 0% coverage (none implemented)  
**Instrumentation Tests:** Minimal (basic setup only)  
**Manual Testing:** Ad-hoc (no documented test plans)

**Risk Level:** 🔴 **HIGH** - Changes may introduce regressions without detection

---

## Testing Strategy

### Test Pyramid

```
            /\
           /  \     E2E Tests (5%)
          /____\    
         /      \   Integration Tests (15%)
        /________\  
       /          \ Unit Tests (80%)
      /____________\
```

**Target Distribution:**
- **80% Unit Tests** - Fast, isolated, test business logic
- **15% Integration Tests** - Test component interactions
- **5% E2E Tests** - Test complete user flows

---

## Unit Testing (Priority 1)

### Required Test Coverage

#### 1. MovieRepository Tests

**File:** `app/src/test/.../data/repository/MovieRepositoryTest.kt`

**Test Cases:**

```kotlin
class MovieRepositoryTest {
    
    @Test
    fun `getRecommendations with 5 selected movies returns 15 recommendations`()
    
    @Test
    fun `getRecommendations excludes favorites from results`()
    
    @Test
    fun `getRecommendations falls back to TMDB when LLM fails`()
    
    @Test
    fun `buildFallbackRecommendations uses TMDB similar movies API`()
    
    @Test
    fun `getRecommendations with empty exclusion list succeeds`()
    
    @Test
    fun `retry recommendations excludes previous recommendations`()
}
```

**Mocking Strategy:**
- Mock `TmdbApiService` (Mockito/MockK)
- Mock `LlmRecommendationService`
- Mock `MovieDao`
- Use in-memory Room database for integration tests

---

#### 2. LlmRecommendationService Tests

**File:** `app/src/test/.../data/remote/LlmRecommendationServiceTest.kt`

**Test Cases:**

```kotlin
class LlmRecommendationServiceTest {
    
    @Test
    fun `parseRecommendationsText with valid format returns movie list`()
    
    @Test
    fun `parseRecommendationsText with numbered list returns movies`()
    
    @Test
    fun `parseRecommendationsText with malformed input returns empty list`()
    
    @Test
    fun `buildPrompt includes user preferences when enabled`()
    
    @Test
    fun `buildPrompt excludes preferences when toggles disabled`()
    
    @Test
    fun `getRecommendations retries with lower temperature on failure`()
    
    @Test
    fun `validateRecommendationStructure rejects invalid responses`()
}
```

**Test Data:** Use sample LLM responses (valid + edge cases)

---

#### 3. MovieViewModel Tests

**File:** `app/src/test/.../ui/viewmodel/MovieViewModelTest.kt`

**Test Cases:**

```kotlin
class MovieViewModelTest {
    
    @Test
    fun `toggleMovieSelection adds movie when under 5 selected`()
    
    @Test
    fun `toggleMovieSelection does nothing when 5 already selected`()
    
    @Test
    fun `addToFavorites updates isFavorite flag`()
    
    @Test
    fun `retryRecommendations includes previous titles in exclusion list`()
    
    @Test
    fun `getRecommendations emits Loading then Success states`()
    
    @Test
    fun `getRecommendations emits Error state on failure`()
}
```

**Setup:**
- Use `TestCoroutineDispatcher` for coroutine testing
- Mock `MovieRepository`
- Observe `StateFlow<MovieUiState>` changes

---

#### 4. SettingsRepository Tests

**File:** `app/src/test/.../data/settings/SettingsRepositoryTest.kt`

**Test Cases:**

```kotlin
class SettingsRepositoryTest {
    
    @Test
    fun `setUserName stores value in DataStore`()
    
    @Test
    fun `getUserName returns default empty string when not set`()
    
    @Test
    fun `setDarkMode persists preference`()
    
    @Test
    fun `preference sliders default to 0_5`()
    
    @Test
    fun `toggle switches default to true`()
}
```

**Setup:** Use `TestDataStore` (in-memory)

---

#### 5. TorrentStreamService Tests

**File:** `app/src/test/.../torrent/TorrentStreamServiceTest.kt`

**Test Cases:**

```kotlin
class TorrentStreamServiceTest {
    
    @Test
    fun `getCacheSize returns correct size in MB`()
    
    @Test
    fun `clearCacheDirectory deletes all cache files`()
    
    @Test
    fun `cleanup deletes old chunks behind playback position`()
    
    @Test
    fun `pauseDownload called when cache exceeds 500MB`()
    
    @Test
    fun `resumeDownload called when cache drops below threshold`()
}
```

**Setup:** Use temporary test directory

---

### Test Dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0") // Flow testing
    testImplementation("com.google.truth:truth:1.1.5") // Assertions
    
    // Room Testing
    testImplementation("androidx.room:room-testing:2.6.1")
    
    // DataStore Testing
    testImplementation("androidx.datastore:datastore-preferences-core:1.0.0")
}
```

---

## Integration Testing (Priority 2)

### Room Database Tests

**File:** `app/src/androidTest/.../data/local/MovieDaoTest.kt`

**Setup:**
```kotlin
@RunWith(AndroidJUnit4::class)
class MovieDaoTest {
    
    private lateinit var database: AppDatabase
    private lateinit var movieDao: MovieDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        movieDao = database.movieDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
}
```

**Test Cases:**

```kotlin
@Test
fun insertAndRetrieveMovie() = runBlocking {
    val movie = Movie(id = 1, title = "Test", ...)
    movieDao.insertMovie(movie)
    val retrieved = movieDao.getMovieById(1)
    assertEquals(movie, retrieved)
}

@Test
fun getSelectedMoviesReturnsOnlySelected() = runBlocking {
    movieDao.insertMovie(Movie(id = 1, isSelected = true, ...))
    movieDao.insertMovie(Movie(id = 2, isSelected = false, ...))
    
    val selected = movieDao.getSelectedMovies().first()
    assertEquals(1, selected.size)
    assertEquals(1, selected[0].id)
}

@Test
fun clearSelectedMoviesSetsAllToFalse() = runBlocking {
    movieDao.insertMovie(Movie(id = 1, isSelected = true, ...))
    movieDao.clearSelectedMovies()
    
    val selected = movieDao.getSelectedMovies().first()
    assertTrue(selected.isEmpty())
}
```

---

### Navigation Tests

**File:** `app/src/androidTest/.../ui/navigation/AppNavigationTest.kt`

**Test Cases:**

```kotlin
@Test
fun genreSelectionNavigatesToMovieSelection() {
    composeTestRule.onNodeWithText("Action").performClick()
    composeTestRule.onNodeWithTag("movie_selection_screen").assertExists()
}

@Test
fun backButtonFromMovieSelectionReturnsToGenres() {
    // Navigate to movie selection
    // Press back
    composeTestRule.onNodeWithTag("genre_selection_screen").assertExists()
}

@Test
fun selectingFiveMov iesShowsGetRecommendationsButton() {
    repeat(5) { index ->
        composeTestRule.onNodeWithTag("movie_card_$index").performClick()
    }
    composeTestRule.onNodeWithText("Get Recommendations").assertExists()
}
```

**Setup:** Use Compose UI testing framework

---

## End-to-End Testing (Priority 3)

### Critical User Flows

**Test:** Complete Recommendation Flow
```
1. Launch app
2. Enter name "Test User"
3. Select "Action" genre
4. Select 5 movies
5. Click "Get Recommendations"
6. Verify 15 recommendations displayed
7. Click "Watch Trailer" on first movie
8. Verify YouTube launches
```

**Test:** Favorites Flow
```
1. Launch app
2. Navigate to movie
3. Click heart icon (add to favorites)
4. Navigate to "[Name]'s Favorites"
5. Verify movie appears
6. Click heart icon (remove from favorites)
7. Verify movie disappears
```

**Test:** Retry Recommendations
```
1. Get initial recommendations
2. Note movie titles
3. Click "Retry"
4. Verify new recommendations don't include previous titles
```

---

## Manual Testing Checklist

### Pre-Release Testing

**Android (Mobile):**
- [ ] Install fresh on device
- [ ] Complete onboarding
- [ ] Select 5 movies
- [ ] Get recommendations
- [ ] Add to favorites
- [ ] Watch trailer
- [ ] Stream movie
- [ ] Adjust preferences
- [ ] Retry recommendations
- [ ] Toggle dark mode
- [ ] Verify no crashes

**Fire TV (Firestick):**
- [ ] Install fresh on Fire TV Stick 4K
- [ ] Navigate with DPAD only
- [ ] Complete full flow
- [ ] Verify focus indicators
- [ ] Test back button
- [ ] Test home button (background behavior)
- [ ] Verify notification controls
- [ ] Test streaming playback controls

---

## Regression Testing

### After Each Change

**Must Test:**
1. Feature directly modified
2. Features that depend on modified code
3. Related user flows

**Example:** If changing `MovieRepository.getRecommendations()`:
- Test recommendation generation
- Test retry functionality
- Test exclusion lists
- Test fallback algorithm

---

## Performance Testing

### Metrics to Monitor

**Startup Time:**
- Cold start: < 2 seconds (target)
- Warm start: < 1 second

**Recommendation Generation:**
- With LLM: 5-15 seconds (acceptable)
- With fallback: < 3 seconds

**Database Queries:**
- Get selected movies: < 10ms
- Get favorites: < 50ms
- Get recommendations: < 50ms

**Memory Usage:**
- Idle: < 100 MB
- During streaming: < 200 MB
- Peak: < 300 MB

**Cache Management:**
- Verify never exceeds 500 MB
- Verify cleanup runs every 10 seconds during playback

---

## Test Data

### Sample Movies for Testing

```kotlin
val testMovies = listOf(
    Movie(id = 1, title = "The Shawshank Redemption", genreIds = listOf(18, 80), ...),
    Movie(id = 2, title = "The Dark Knight", genreIds = listOf(28, 80, 18), ...),
    Movie(id = 3, title = "Inception", genreIds = listOf(28, 878, 53), ...),
    Movie(id = 4, title = "Pulp Fiction", genreIds = listOf(80, 53), ...),
    Movie(id = 5, title = "Forrest Gump", genreIds = listOf(35, 18, 10749), ...)
)
```

### Sample LLM Responses

**Valid Response:**
```
Based on your selections, here are 15 recommendations:

1. The Godfather
2. Fight Club
3. The Matrix
4. Goodfellas
5. Seven
...
```

**Invalid Response (for error testing):**
```
Here are some movies: The Godfather, Fight Club, The Matrix...
```

---

## CI/CD Integration

### GitHub Actions Workflow

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run unit tests
        run: ./gradlew test
      - name: Run instrumentation tests
        run: ./gradlew connectedAndroidTest
      - name: Upload test reports
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: app/build/reports/tests/
```

---

## Test Execution Commands

**Run All Unit Tests:**
```bash
./gradlew test
```

**Run Specific Test Class:**
```bash
./gradlew test --tests MovieRepositoryTest
```

**Run Instrumentation Tests:**
```bash
./gradlew connectedAndroidTest
```

**Run with Coverage:**
```bash
./gradlew testDebugUnitTestCoverage
```

**View Coverage Report:**
```
open app/build/reports/coverage/test/debug/index.html
```

---

## Testing Best Practices

### 1. Test Naming Convention

```kotlin
// Pattern: `methodName_stateUnderTest_expectedBehavior`
@Test
fun `getRecommendations_withEmptyFavorites_returns15Movies`()

@Test
fun `toggleMovieSelection_whenFifthMovieSelected_showsRecommendationsButton`()
```

### 2. AAA Pattern

```kotlin
@Test
fun testExample() {
    // Arrange
    val repository = MovieRepository(mockDao, mockTmdb, mockLlm)
    val selectedMovies = listOf(movie1, movie2, movie3, movie4, movie5)
    
    // Act
    val result = repository.getRecommendations(selectedMovies)
    
    // Assert
    assertEquals(15, result.size)
}
```

### 3. Test Independence

- Each test should be independent (no shared state)
- Use `@Before` for setup, `@After` for cleanup
- Don't rely on test execution order

### 4. Mock External Dependencies

- Always mock: APIs, database, services
- Never mock: Entity classes, data models
- Use real implementations for pure functions

---

## Known Testing Gaps

1. **No UI tests** for Compose screens
2. **No API integration tests** (all mocked)
3. **No performance benchmarks** automated
4. **No Fire TV specific tests** (DPAD navigation)
5. **No torrent streaming tests** (complex setup)
6. **No migration tests** (database schema changes)

---

## Testing Roadmap

### Phase 1: Foundation (Week 1-2)
- [ ] Set up test dependencies
- [ ] Write tests for MovieRepository
- [ ] Write tests for LlmRecommendationService
- [ ] Write tests for MovieViewModel
- [ ] Target: 40% coverage

### Phase 2: Integration (Week 3-4)
- [ ] Write Room database tests
- [ ] Write DataStore tests
- [ ] Write navigation tests
- [ ] Target: 60% coverage

### Phase 3: E2E (Week 5-6)
- [ ] Set up Compose UI testing
- [ ] Write critical user flow tests
- [ ] Manual testing checklist
- [ ] Target: 80% coverage

### Phase 4: Automation (Week 7-8)
- [ ] CI/CD integration
- [ ] Automated regression testing
- [ ] Performance benchmarks
- [ ] Target: 90% coverage

---

**Next Steps:** Start with Phase 1, focus on critical business logic (MovieRepository, LlmRecommendationService).
