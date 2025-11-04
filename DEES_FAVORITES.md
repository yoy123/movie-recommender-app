# Dee's Favorites Feature

## Overview
"Dee's Favorites" is a special custom genre that allows you to save any movies from any genre into a personal favorites collection. Unlike regular genres, this collection is not limited to specific movie genres and can include any movies you want to save.

## Features

### 1. **Custom Favorites Genre**
- Appears as the first item in the genre selection screen
- Distinguished by a heart icon (♥) instead of the regular movie icon
- Always available regardless of TMDB genre list

### 2. **Search Any Movie**
- Search across all movies in the TMDB database
- No genre restrictions
- Real-time search results as you type

### 3. **Add to Favorites**
- Tap any movie from search results to add it to favorites
- Movies already in favorites show a "✓ Added" badge
- Added movies persist across app sessions

### 4. **Remove from Favorites**
- Each favorite movie displays a remove button (✕)
- Tap the remove button to instantly remove from favorites
- Changes are saved immediately

### 5. **Favorites Counter**
- Display shows "Your Favorites (X)" where X is the total count
- Updates in real-time as you add or remove movies

## How to Use

### Adding Movies to Favorites:

1. **Select Dee's Favorites**
   - From the genre selection screen, tap "Dee's Favorites" (the one with the heart icon)
   
2. **Search for Movies**
   - Type any movie name in the search bar
   - Press the search icon or hit Enter
   - Browse the search results

3. **Add Movies**
   - Tap any movie poster to add it to your favorites
   - The movie will show a "✓ Added" badge once added
   - You cannot add the same movie twice

### Removing Movies from Favorites:

1. **View Your Favorites**
   - Open Dee's Favorites from the genre selection
   - Scroll through your favorites collection at the top

2. **Remove a Movie**
   - Tap the ✕ button in the top-right corner of any favorite movie
   - The movie is instantly removed from your collection

## Technical Implementation

### Database Schema
```kotlin
@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey val id: Int,
    // ... other fields ...
    val isFavorite: Boolean = false,  // New field for favorites
    val timestamp: Long = System.currentTimeMillis()
)
```

### New DAO Queries
```kotlin
@Query("SELECT * FROM movies WHERE isFavorite = 1 ORDER BY timestamp DESC")
fun getFavoriteMovies(): Flow<List<Movie>>

@Query("UPDATE movies SET isFavorite = 1 WHERE id = :movieId")
suspend fun addToFavorites(movieId: Int)

@Query("UPDATE movies SET isFavorite = 0 WHERE id = :movieId")
suspend fun removeFromFavorites(movieId: Int)
```

### UI Components

**FavoritesScreen.kt**
- Handles the favorites management UI
- Two sections:
  1. Your Favorites (top) - Shows saved favorites with remove buttons
  2. Search Results (bottom) - Shows search results with add buttons
- Real-time search with keyboard handling
- Grid layout (3 columns) for optimal viewing

**GenreSelectionScreen.kt**
- Modified to inject "Dee's Favorites" as first item
- Special handling for genreId = -1 (favorites pseudo-genre)
- Heart icon for visual distinction

### Navigation Flow
```
Genre Selection Screen
    ↓ (Select Dee's Favorites)
Favorites Screen
    ↓ (Back button)
Genre Selection Screen
```

## Data Persistence

- Favorites are stored in the local Room database
- The `isFavorite` flag marks movies as favorites
- Movies persist across app sessions
- Database version upgraded to version 2 to support new field

## Benefits

1. **Cross-Genre Collection**: Save movies from any genre in one place
2. **Personal Curation**: Build your own watchlist
3. **Quick Access**: Easily find your saved movies
4. **Flexible Management**: Add or remove movies anytime
5. **No Limits**: Add as many movies as you want

## Future Enhancements (Potential)

- Export favorites list
- Share favorites with friends
- Sync favorites across devices
- Add notes or ratings to favorites
- Create multiple custom collections
- Sort favorites by different criteria (date added, rating, title)

## Screenshots Layout

```
[Genre Selection]     [Dee's Favorites]     [Search & Add]
┌─────────────┐      ┌─────────────┐       ┌─────────────┐
│ ♥ Dee's     │      │ Your Faves  │       │ Search Bar  │
│   Favorites │  →   │ [■] [■] [■] │   →   │ Results:    │
│             │      │ [■] [■] [■] │       │ [+] [+] [+] │
│ Action      │      │             │       │ [+] [+] [+] │
│ Comedy      │      │ Search:     │       └─────────────┘
│ Drama       │      │ [Search]    │
└─────────────┘      └─────────────┘
```

## Notes

- The favorites feature is completely independent of the recommendation system
- You can have movies in favorites while also selecting them for recommendations
- Clearing selections does not affect favorites
- The database uses `fallbackToDestructiveMigration()` so upgrading will clear existing data (only relevant for development)

---

For more information about the project, see:
- [README.md](README.md) - Main project documentation
- [GETTING_STARTED.md](GETTING_STARTED.md) - Setup instructions
- [LLM_INTEGRATION.md](LLM_INTEGRATION.md) - AI recommendation system details
