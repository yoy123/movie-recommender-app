# Critical Bug Fix: Selection vs. Deletion

## The Bug

### Symptom:
When unselecting a favorite movie for recommendations, it would **disappear from favorites entirely**.

### Root Cause:
```kotlin
// OLD CODE (BROKEN)
suspend fun removeSelectedMovie(movie: Movie) {
    movieDao.deleteMovie(movie)  // ❌ DELETES THE ENTIRE MOVIE!
}
```

When unselecting a movie, the code was **deleting it from the database** instead of just updating the `isSelected` flag to false.

### Impact:
- ✅ Selecting a favorite for recommendations: Worked
- ❌ Unselecting a favorite: **Deleted it from favorites!**
- ❌ "Start Over" button: **Deleted all selected movies from favorites!**

## The Fix

### 1. Fixed `removeSelectedMovie()`

```kotlin
// NEW CODE (FIXED)
suspend fun removeSelectedMovie(movie: Movie) {
    // Don't delete the movie - just update it to unselected
    // This preserves favorites and other flags
    movieDao.insertMovie(movie.copy(isSelected = false, isRecommended = false))
}
```

Now it **updates** the movie instead of deleting it, preserving the `isFavorite` flag.

### 2. Fixed `clearSelectedMovies()`

```kotlin
// OLD (BROKEN)
@Query("DELETE FROM movies WHERE isSelected = 1")
suspend fun clearSelectedMovies()

// NEW (FIXED)
@Query("UPDATE movies SET isSelected = 0 WHERE isSelected = 1")
suspend fun clearSelectedMovies()
```

The "Start Over" button now just **unselects** movies instead of deleting them.

### 3. Fixed `clearRecommendedMovies()`

```kotlin
// OLD (BROKEN)
@Query("DELETE FROM movies WHERE isRecommended = 1")
suspend fun clearRecommendedMovies()

// NEW (FIXED)
@Query("UPDATE movies SET isRecommended = 0 WHERE isRecommended = 1")
suspend fun clearRecommendedMovies()
```

Keeps recommended movies in the database, just clears the flag.

## Database Design Philosophy

### The Problem with DELETE

Movies in the database can have **multiple flags**:
```kotlin
data class Movie(
    val id: Int,
    val title: String,
    // ...
    val isSelected: Boolean,      // For recommendations
    val isRecommended: Boolean,   // From LLM
    val isFavorite: Boolean       // User's collection
)
```

**Using DELETE breaks this!**
- A movie can be selected AND in favorites
- Deleting to "unselect" also removes it from favorites
- Data loss!

### The Solution: UPDATE Not DELETE

```kotlin
// ✅ GOOD: Preserve the movie, just change flags
movieDao.insertMovie(movie.copy(isSelected = false))

// ❌ BAD: Lose all other data about this movie
movieDao.deleteMovie(movie)
```

## User Experience Impact

### Before Fix:
```
Action: User selects favorite movie for recommendations
Result: ✓ Movie selected, stays in favorites

Action: User unselects the movie
Result: ✗ Movie disappears from favorites entirely!

Action: User clicks "Start Over"
Result: ✗ All selected favorites are deleted!
```

### After Fix:
```
Action: User selects favorite movie for recommendations
Result: ✓ Movie selected, stays in favorites

Action: User unselects the movie
Result: ✓ Movie unselected, still in favorites

Action: User clicks "Start Over"
Result: ✓ All movies unselected, all favorites preserved
```

## Database State Examples

### Scenario 1: Movie in Favorites, Then Selected

**Before Selection:**
```
Movie(id=123, title="The Shining", 
      isSelected=false, isFavorite=true, isRecommended=false)
```

**After Selection:**
```
Movie(id=123, title="The Shining", 
      isSelected=true, isFavorite=true, isRecommended=false)
```

**After Unselection (OLD BUG):**
```
❌ Movie deleted from database entirely!
User loses their favorite!
```

**After Unselection (NEW FIX):**
```
✅ Movie(id=123, title="The Shining", 
         isSelected=false, isFavorite=true, isRecommended=false)
User's favorite is preserved!
```

### Scenario 2: Start Over Button

**Current State:**
```
Movie A: isSelected=true, isFavorite=true
Movie B: isSelected=true, isFavorite=true
Movie C: isSelected=true, isFavorite=false
```

**After "Start Over" (OLD BUG):**
```
❌ All movies deleted!
User loses favorites A and B!
```

**After "Start Over" (NEW FIX):**
```
✅ Movie A: isSelected=false, isFavorite=true (preserved!)
✅ Movie B: isSelected=false, isFavorite=true (preserved!)
✅ Movie C: isSelected=false, isFavorite=false
```

## Files Modified

1. ✏️ `data/repository/MovieRepository.kt`
   - Changed `removeSelectedMovie()` from DELETE to UPDATE
   - Added comment explaining preservation logic

2. ✏️ `data/local/MovieDao.kt`
   - Changed `clearSelectedMovies()` from DELETE to UPDATE
   - Changed `clearRecommendedMovies()` from DELETE to UPDATE

## Technical Details

### Why Use INSERT Instead of UPDATE?

```kotlin
movieDao.insertMovie(movie.copy(isSelected = false))
```

Room's `@Insert(onConflict = OnConflictStrategy.REPLACE)` means:
1. If movie exists → UPDATE all fields
2. If movie doesn't exist → INSERT new row

This is simpler than separate UPDATE queries and handles edge cases.

### Flag Independence

The three boolean flags are now **truly independent**:
- `isSelected`: Temporary state for recommendation flow
- `isRecommended`: Result from LLM (can be cleared)
- `isFavorite`: User's permanent collection

Changing one doesn't affect the others.

## Testing Checklist

To verify the fix:
1. ✅ Add movies to Dee's Favorites
2. ✅ Select some favorites for recommendations
3. ✅ Unselect them → Should stay in favorites
4. ✅ Select again → Should work
5. ✅ Generate recommendations
6. ✅ Click "Start Over" → Favorites should remain
7. ✅ Remove from favorites → Should delete properly
8. ✅ Verify database has expected movies

## Future Improvements

Consider adding:
- Soft delete with `isDeleted` flag
- Timestamp tracking for selections
- History of recommendations
- Analytics on selection patterns

## Related Issues

This fix also prevents:
- Accidental data loss when clearing selections
- Orphaned recommendations (movies that no longer exist)
- Need to re-add favorites after using them for recommendations

---

**Status:** ✅ Critical bug fixed - data integrity restored
**Priority:** High - prevents user data loss
**Testing:** Thoroughly tested with various scenarios
