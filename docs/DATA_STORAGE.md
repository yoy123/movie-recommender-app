# DATA_STORAGE.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

Complete documentation of all data persistence: Room database schema, DataStore preferences, retention rules, migration strategy, and versioning.

---

## 1. Room Database

### Database Configuration

**Name:** `"movie_recommender_database"`  
**Version:** `2`  
**Location:** `{app_data}/databases/movie_recommender_database`  
**Migration Strategy:** `fallbackToDestructiveMigration()`

**Code:** [AppDatabase.kt:9](../app/src/main/java/com/movierecommender/app/data/local/AppDatabase.kt#L9)
```kotlin
@Database(
    entities = [Movie::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase()
```

**Critical Warning:** `fallbackToDestructiveMigration()` means **ALL DATA IS DELETED** on schema version bump. Users lose favorites, selected movies, recommendations on app update.

---

### Entity: Movie

**Table Name:** `movies`  
**Primary Key:** `id` (TMDB movie ID)

**Schema:**

| Column | Type | Nullable | Default | Index | Description |
|--------|------|----------|---------|-------|-------------|
| id | INTEGER | NO | - | PRIMARY KEY | TMDB movie ID |
| title | TEXT | NO | - | NO | Movie title |
| overview | TEXT | NO | - | NO | Plot summary |
| posterPath | TEXT | YES | NULL | NO | TMDB poster path (/abc123.jpg) |
| backdropPath | TEXT | YES | NULL | NO | TMDB backdrop path |
| releaseDate | TEXT | YES | NULL | NO | ISO date (YYYY-MM-DD) |
| voteAverage | REAL | NO | - | NO | TMDB rating (0.0-10.0) |
| voteCount | INTEGER | NO | - | NO | Number of votes |
| popularity | REAL | NO | - | NO | TMDB popularity score |
| genreIds | TEXT | NO | "" | NO | Comma-separated genre IDs (via TypeConverter) |
| genres | TEXT | YES | NULL | NO | Comma-separated genre names |
| isSelected | INTEGER | NO | 0 | NO | Boolean: user selected for recommendations |
| isRecommended | INTEGER | NO | 0 | NO | Boolean: currently recommended |
| isFavorite | INTEGER | NO | 0 | NO | Boolean: saved to favorites |
| timestamp | INTEGER | NO | System.currentTimeMillis() | NO | Creation timestamp (ms) |

**Code:** [Movie.kt:10](../app/src/main/java/com/movierecommender/app/data/model/Movie.kt#L10)

---

### Type Converters

**Purpose:** SQLite doesn't support `List<Int>` natively. Convert to/from comma-separated string.

**Implementation:**

```kotlin
@TypeConverter
fun fromIntList(value: List<Int>): String {
    return value.joinToString(",")
}

@TypeConverter
fun toIntList(value: String): List<Int> {
    if (value.isBlank()) return emptyList()
    return value.split(",").mapNotNull { it.toIntOrNull() }
}
```

**Code:** [Converters.kt:9](../app/src/main/java/com/movierecommender/app/data/local/Converters.kt#L9)

**Example:**
- Kotlin: `listOf(28, 35, 80)`
- SQLite: `"28,35,80"`

---

### DAO Operations

**Interface:** [MovieDao.kt](../app/src/main/java/com/movierecommender/app/data/local/MovieDao.kt)

#### Queries

**Get Selected Movies:**
```kotlin
@Query("SELECT * FROM movies WHERE isSelected = 1 ORDER BY timestamp DESC")
fun getSelectedMovies(): Flow<List<Movie>>
```
Returns Flow that emits when selected movies change.

**Get Recommended Movies:**
```kotlin
@Query("SELECT * FROM movies WHERE isRecommended = 1 ORDER BY popularity DESC")
fun getRecommendedMovies(): Flow<List<Movie>>
```

**Get Favorites:**
```kotlin
@Query("SELECT * FROM movies WHERE isFavorite = 1 ORDER BY timestamp DESC")
fun getFavoriteMovies(): Flow<List<Movie>>
```

**Get by ID:**
```kotlin
@Query("SELECT * FROM movies WHERE id = :id")
suspend fun getMovieById(id: Int): Movie?
```

---

#### Insert/Update

**Insert Single:**
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertMovie(movie: Movie)
```

**Insert Multiple:**
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertMovies(movies: List<Movie>)
```

**Update:**
```kotlin
@Update
suspend fun updateMovie(movie: Movie)
```

**Strategy:** `REPLACE` means if movie with same `id` exists, overwrite all fields. No partial updates.

---

#### Flag Operations

**Clear Selected:**
```kotlin
@Query("UPDATE movies SET isSelected = 0 WHERE isSelected = 1")
suspend fun clearSelectedMovies()
```

**Clear Recommended:**
```kotlin
@Query("UPDATE movies SET isRecommended = 0 WHERE isRecommended = 1")
suspend fun clearRecommendedMovies()
```

**Add to Favorites:**
```kotlin
@Query("UPDATE movies SET isFavorite = 1 WHERE id = :movieId")
suspend fun addToFavorites(movieId: Int)
```

**Remove from Favorites:**
```kotlin
@Query("UPDATE movies SET isFavorite = 0 WHERE id = :movieId")
suspend fun removeFromFavorites(movieId: Int)
```

**Critical Pattern:** Flags are updated in-place via SQL UPDATE. Never delete and re-insert.

---

#### Delete

**Delete Single:**
```kotlin
@Delete
suspend fun deleteMovie(movie: Movie)
```

**Clear All:**
```kotlin
@Query("DELETE FROM movies")
suspend fun clearAllMovies()
```

**Warning:** `clearAllMovies()` is NEVER CALLED in current codebase. Movies persist indefinitely.

---

### Schema Version History

#### Version 1 (Original)
**Unknown.** No migration code exists. Assumed initial schema.

#### Version 2 (Current)
**Changes:** Unknown (no migration code preserved).

**Upgrade Path:**
- User updates app → database version mismatch → `fallbackToDestructiveMigration()` → DROP all tables → recreate from scratch
- **Result:** ALL favorites lost, ALL selections lost

**Code:** [AppDatabase.kt:28](../app/src/main/java/com/movierecommender/app/data/local/AppDatabase.kt#L28)
```kotlin
val instance = Room.databaseBuilder(
    context.applicationContext,
    AppDatabase::class.java,
    "movie_recommender_database"
)
    .fallbackToDestructiveMigration()
    .build()
```

---

### Retention Rules

**No Automatic Cleanup:** Movies never expire, never auto-deleted.

**Manual Cleanup Triggers:**
1. User clicks "Start Over" → `clearSelectedMovies()` + `clearRecommendedMovies()`
2. User removes favorite → `removeFromFavorites(movieId)`
3. App reinstall → all data lost

**Growth Over Time:**
- User selects 5 movies → 5 rows inserted
- User gets recommendations → 15 more rows inserted (marked `isRecommended = true`)
- User adds to favorites → flags updated (no new rows)
- **Repeat 100 times** → 2000 rows in DB
- **No cleanup** → DB grows indefinitely

**Disk Usage Estimate:**
- Average movie row: ~500 bytes (title, overview, URLs)
- 10,000 movies = ~5 MB
- Not a problem for modern devices (storage abundant)

---

## 2. DataStore Preferences

### Configuration

**Type:** `androidx.datastore.preferences`  
**Name:** `"user_settings"`  
**Location:** `{app_data}/files/datastore/user_settings.preferences_pb`  
**Format:** Protocol Buffers (binary)

**Code:** [SettingsRepository.kt:13](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L13)
```kotlin
private val Context.dataStore by preferencesDataStore(name = SETTINGS_NAME)
```

---

### Stored Preferences

#### User Identity

**user_name:** String (default: "")  
**is_first_run:** Boolean (default: true)

#### Theme

**dark_mode:** Boolean (default: true)

#### Recommendation Preferences (6 sliders + toggles)

1. **indie_preference:** Float (0–1, default: 0.5)  
   **use_indie:** Boolean (default: true)

2. **popularity_preference:** Float (0–1, default: 0.5)  
   **use_popularity:** Boolean (default: true)

3. **release_year_start:** Float (1950–2026, default: 1950)  
   **release_year_end:** Float (1950–2026, default: 2026)  
   **use_release_year:** Boolean (default: true)

4. **tone_preference:** Float (0–1, default: 0.5)  
   **use_tone:** Boolean (default: true)

5. **international_preference:** Float (0–1, default: 0.5)  
   **use_international:** Boolean (default: true)

6. **experimental_preference:** Float (0–1, default: 0.5)  
   **use_experimental:** Boolean (default: true)

**Code:** [SettingsRepository.kt:18](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L18)

---

### Access Pattern

**Read:** Flow-based (reactive)
```kotlin
val userName: Flow<String> = context.dataStore.data.map { 
    it[Keys.USER_NAME] ?: "" 
}
```

**Write:** Suspend function (transactional)
```kotlin
suspend fun setUserName(name: String) {
    context.dataStore.edit { prefs ->
        prefs[Keys.USER_NAME] = name
    }
}
```

**Code:** [SettingsRepository.kt:32](../app/src/main/java/com/movierecommender/app/data/settings/SettingsRepository.kt#L32)

---

### Persistence

**Survives:** App restart, app update  
**Lost:** App uninstall, clear data, device factory reset

**Backup:** Not backed up to cloud (no `android:allowBackup` implementation for DataStore)

---

## 3. Cache Storage

### Torrent Cache

**Location:** `{app_cache}/torrent_stream/`  
**Purpose:** Store downloaded torrent chunks during streaming  
**Max Size:** 500 MB  
**Cleanup Strategy:** Aggressive deletion of old chunks + pause download when full

**Code:** [TorrentStreamService.kt:183](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt#L183)

**Lifecycle:**
1. User clicks "Watch Now" → TorrentStreamService started
2. Torrent downloads to cache → video file + chunk files
3. ExoPlayer reads from local file
4. As playback progresses: `updatePlaybackPosition()` called
5. Old chunks (> 1 minute old + behind playback position) deleted every 10 seconds
6. If cache > 500 MB: pause download, aggressive cleanup
7. When cache < 500 MB: resume download
8. On app exit: `clearCacheDirectory()` called → entire cache deleted

**Code:** [TorrentStreamService.kt:131](../app/src/main/java/com/movierecommender/app/torrent/TorrentStreamService.kt#L131)

---

### Image Cache (Coil)

**Location:** `{app_cache}/image_cache/` (Coil default)  
**Purpose:** Cache TMDB poster/backdrop images  
**Max Size:** Coil default (~250 MB)  
**Cleanup:** LRU eviction (Coil handles automatically)

**No explicit code:** Coil library manages internally via `AsyncImage` composable.

---

## 4. Data Flow Diagrams

### Movie Selection Flow

```
User clicks movie checkbox
         ↓
MovieViewModel.toggleMovieSelection(movie)
         ↓
If not selected:
  repository.saveSelectedMovie(movie)
         ↓
  movieDao.insertMovie(movie.copy(isSelected = true))
         ↓
  Room inserts/updates row with isSelected = 1
         ↓
  movieDao.getSelectedMovies() emits updated list
         ↓
  ViewModel collects flow → updates MovieUiState.selectedMovies
         ↓
  UI recomposes → checkbox checked
```

---

### Favorites Flow

```
User clicks heart icon
         ↓
MovieViewModel.addToFavorites(movie)
         ↓
repository.addToFavorites(movie)
         ↓
movieDao.insertMovie(movie.copy(isFavorite = true))
         ↓
Room REPLACE (upsert) → sets isFavorite = 1
         ↓
movieDao.getFavoriteMovies() emits updated list
         ↓
ViewModel collects flow → updates MovieUiState.favoriteMovies
         ↓
UI recomposes → heart icon filled
```

**Critical:** If movie not in DB yet, `insertMovie()` creates new row. If exists, REPLACE updates all fields.

---

### Recommendation Storage

```
LLM returns recommendations text
         ↓
Repository parses text → extracts 15 movie titles
         ↓
For each title:
  TMDB search → get Movie object
         ↓
  repository.saveRecommendedMovies(movies)
         ↓
  movieDao.clearRecommendedMovies() (clears previous)
         ↓
  movieDao.insertMovies(movies.map { it.copy(isRecommended = true) })
         ↓
  Room inserts/replaces 15 rows with isRecommended = 1
         ↓
  movieDao.getRecommendedMovies() emits updated list
         ↓
  ViewModel updates MovieUiState.recommendedMovies
         ↓
  UI displays 15 recommendations
```

**Code:** [MovieRepository.kt:1014](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L1014)

---

## 5. Data Integrity Concerns

### Issue 1: Flag Conflicts

**Scenario:** Movie is both selected AND recommended.

**Current Handling:**
- `saveRecommendedMovies()` sets `isRecommended = true, isSelected = false`
- `saveSelectedMovie()` sets `isSelected = true, isRecommended = false`
- Flags are mutually exclusive **in code**, but schema allows `isSelected = 1 AND isRecommended = 1`

**Risk:** None (code prevents conflict, but DB schema doesn't enforce)

---

### Issue 2: Orphaned Flags

**Scenario:** User clicks "Start Over" → `clearSelectedMovies()` + `clearRecommendedMovies()` called.

**Result:**
- All `isSelected` → 0
- All `isRecommended` → 0
- **Movies remain in DB** (not deleted)
- Next session: DB contains 100+ movies with all flags = 0

**Growth:** Unbounded. DB accumulates every movie ever viewed.

**Mitigation:** None currently. Could add periodic cleanup of rows where `isSelected = 0 AND isRecommended = 0 AND isFavorite = 0`.

---

### Issue 3: Destructive Migration

**Scenario:** Schema version bumps from 2 → 3.

**Result:** `fallbackToDestructiveMigration()` drops all tables → **ALL FAVORITES LOST**.

**User Impact:** Severe. No warning, no backup, no recovery.

**Recommendation:** Implement proper migrations:
```kotlin
.addMigrations(MIGRATION_2_3)
```

Where `MIGRATION_2_3` preserves `isFavorite = 1` rows.

---

### Issue 4: No Foreign Keys

**Schema:** Single `movies` table, no relations.

**Implication:** No referential integrity constraints. Can't orphan data (no relations to break).

**Status:** ✅ Safe (no risk)

---

## 6. Backup and Restore

### Current State: NO BACKUP

**Room:** Not backed up (no `android:allowBackup="true"` for DB folder)  
**DataStore:** Not backed up  
**Cache:** Intentionally ephemeral (cleared on exit)

**User Impact:** Reinstall app → lose all favorites, preferences, history.

---

### Recommended Backup Strategy

**Option 1: Android Auto Backup**
- Add to `AndroidManifest.xml`: `android:fullBackupContent="@xml/backup_rules"`
- Create `backup_rules.xml`:
  ```xml
  <include domain="database" path="movie_recommender_database" />
  <include domain="file" path="datastore/user_settings.preferences_pb" />
  ```

**Option 2: Manual Export/Import**
- Add "Export Favorites" button → writes JSON to `Downloads/`
- Add "Import Favorites" button → reads JSON → inserts to Room

---

## 7. Performance Characteristics

### Room Query Performance

**Selected Movies Query:**
- Index: None (no index on `isSelected`)
- Scan: Full table scan
- Rows: ~5 (typically)
- Performance: **Fast** (< 1 ms even with 10k rows)

**Favorites Query:**
- Index: None
- Scan: Full table scan
- Rows: Variable (0–500 typical)
- Performance: **Acceptable** (< 10 ms)

**Recommendation:** Add index if favorites > 1000:
```kotlin
@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["isSelected"]),
        Index(value = ["isRecommended"])
    ]
)
```

---

### DataStore Performance

**Read:** ~1 ms (in-memory cache after first read)  
**Write:** ~5 ms (async write to disk)  
**Concurrency:** Thread-safe (handles concurrent edits)

**Code:** DataStore uses `Flow` → UI collects → automatic updates.

---

## 8. Testing Considerations

### Room Testing

**Strategy:** Use in-memory database for tests:
```kotlin
Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
```

**Current Tests:** None found in `app/src/test/` or `app/src/androidTest/`.

**Recommendation:** Add instrumentation tests for:
- Flag toggling (selected → not selected)
- Favorites persistence
- Clear operations

---

### DataStore Testing

**Strategy:** Use `TestContext` with temp directory.

**Current Tests:** None.

**Recommendation:** Add unit tests for:
- Preference defaults
- Concurrent edits
- Flow emissions

---

## 9. Known Issues and TODOs

### Critical

1. **Destructive Migration**
   - **Impact:** Users lose favorites on app update
   - **Fix:** Implement proper Room migrations with data preservation

### High

2. **Unbounded DB Growth**
   - **Impact:** DB accumulates orphaned movies indefinitely
   - **Fix:** Add cleanup query to delete rows where all flags = 0 and timestamp > 30 days

3. **No Backup Strategy**
   - **Impact:** Reinstall = data loss
   - **Fix:** Enable Android Auto Backup or add manual export

### Medium

4. **No Indexes**
   - **Impact:** Slow queries if favorites > 1000
   - **Fix:** Add indices on boolean flags

5. **No Tests**
   - **Impact:** Schema changes may break without detection
   - **Fix:** Add instrumentation tests for Room + DataStore

### Low

6. **Flag Conflicts Possible**
   - **Impact:** None (code prevents, but schema allows)
   - **Fix:** Add CHECK constraints in Room schema

---

**Next Review:** When schema version bumps, new entities added, or migration strategy changes.
