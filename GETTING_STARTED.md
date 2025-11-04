# Getting Started with Movie Recommender App

## Quick Start Guide

### Step 1: Get Your TMDB API Key

1. Visit https://www.themoviedb.org/
2. Sign up for a free account
3. Navigate to Settings → API
4. Request an API key (select "Developer")
5. Copy your API key

### Step 2: Configure the API Key

Open `app/build.gradle.kts` and find this line:
```kotlin
buildConfigField("String", "TMDB_API_KEY", "\"YOUR_API_KEY_HERE\"")
```

Replace `YOUR_API_KEY_HERE` with your actual API key:
```kotlin
buildConfigField("String", "TMDB_API_KEY", "\"abc123def456ghi789\"")
```

### Step 3: Build and Run

#### Using Android Studio:
1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to this folder and select it
4. Wait for Gradle sync to complete
5. Click the green "Run" button (▶️) or press Shift+F10

#### Using Command Line:
```bash
# Build the project
./gradlew build

# Install on connected device/emulator
./gradlew installDebug

# Run on connected device/emulator
./gradlew installDebug
adb shell am start -n com.movierecommender.app/.MainActivity
```

### Step 4: Set Up an Emulator (if needed)

1. In Android Studio: Tools → Device Manager
2. Click "Create Device"
3. Select a phone (e.g., Pixel 6)
4. Select a system image (API 34 or higher recommended)
5. Click "Finish" and launch the emulator

## Project Architecture

```
┌─────────────────────────────────────────┐
│           UI Layer (Compose)            │
│  • GenreSelectionScreen                 │
│  • MovieSelectionScreen                 │
│  • RecommendationsScreen                │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         ViewModel Layer                 │
│  • MovieViewModel                       │
│  • State Management (StateFlow)         │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Repository Layer                │
│  • MovieRepository                      │
│  • Business Logic                       │
└──────┬────────────────────┬─────────────┘
       │                    │
┌──────▼─────────┐  ┌──────▼──────────────┐
│  Local (Room)  │  │  Remote (Retrofit)  │
│  • MovieDao    │  │  • TmdbApiService   │
│  • Database    │  │  • API Endpoints    │
└────────────────┘  └─────────────────────┘
```

## How the Recommendation Works

1. **User selects 5 movies** they've already watched from a genre
2. **For each selected movie**, the app queries TMDB for:
   - Similar movies (based on metadata like genres, keywords, cast)
   - Recommended movies (TMDB's own recommendation engine)
3. **Scoring algorithm**:
   - Each recommended movie gets +1 popularity point per occurrence
   - Movies are sorted by: (recommendation count × popularity) + vote_average
4. **Top 20 unique recommendations** are displayed (excluding the 5 selected)

## Key Features Implemented

✅ **Genre Selection**: Browse and select from 19 movie genres  
✅ **Movie Discovery**: Search and browse popular movies by genre  
✅ **Smart Selection**: Visual feedback and 5-movie limit enforcement  
✅ **Recommendations**: Algorithm combining similarity and popularity  
✅ **Offline Storage**: Room database for caching selections  
✅ **Material Design 3**: Modern UI with dark theme  
✅ **Image Loading**: Poster and backdrop images via Coil  
✅ **Error Handling**: Graceful error states with retry options  

## Customization Ideas

### Change Theme Colors
Edit `ui/theme/Color.kt`:
```kotlin
val MoviePrimary = Color(0xFFE50914) // Netflix red
val MovieSecondary = Color(0xFF831010)
```

### Adjust Recommendation Count
Edit `MovieRepository.kt`, line with `.take(20)`:
```kotlin
.take(30) // Show 30 recommendations instead of 20
```

### Add More Genres Support
Currently supports single genre. To add multi-genre:
1. Update `MovieUiState` to store `List<Int>` for genres
2. Modify `getMoviesByGenre()` to accept multiple genre IDs
3. Update UI to allow multiple genre selection

### Add Movie Details Screen
1. Create `MovieDetailsScreen.kt`
2. Add navigation route in `AppNavigation.kt`
3. Pass movie ID as navigation argument
4. Fetch movie details from TMDB API

## Troubleshooting

### Gradle Sync Issues
```bash
# Clean and rebuild
./gradlew clean
./gradlew build --refresh-dependencies
```

### API Key Not Working
- Ensure no extra spaces in the API key
- Verify the key is active on TMDB website
- Check LogCat for network errors: `adb logcat | grep "OkHttp"`

### Room Database Issues
```bash
# Clear app data to reset database
adb shell pm clear com.movierecommender.app
```

### Compose Preview Not Showing
- Ensure you're using Android Studio Hedgehog or later
- Try Build → Clean Project → Rebuild Project
- Invalidate Caches: File → Invalidate Caches and Restart

## Testing the App

### Manual Testing Checklist
- [ ] Genre selection displays correctly
- [ ] Movies load for selected genre
- [ ] Search functionality works
- [ ] Can select exactly 5 movies
- [ ] Selected movies show checkmark
- [ ] "Get Recommendations" button appears after 5 selections
- [ ] Recommendations screen loads
- [ ] Can navigate back and forth
- [ ] "Start Over" clears selections

### Recommended Test Flow
1. Select "Action" genre
2. Search for "Mission Impossible"
3. Select 5 action movies
4. Generate recommendations
5. Verify recommended movies are relevant
6. Click "Start Over"
7. Try different genre

## Next Steps

### Immediate Tasks
1. ✅ Get TMDB API key
2. ✅ Configure API key in build.gradle.kts
3. ⏳ Build and run the app
4. ⏳ Test the recommendation flow

### Future Enhancements
- Add movie details screen with trailers
- Implement favorites/watchlist
- Add user authentication
- Support for TV shows
- Streaming availability information
- Social features (share recommendations)
- Advanced filters (year, rating, runtime)
- Multiple recommendation algorithms
- Offline mode with cached data

## Resources

- [TMDB API Documentation](https://developers.themoviedb.org/3)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [Retrofit Documentation](https://square.github.io/retrofit/)

## Support

For issues with:
- **TMDB API**: Check [TMDB Forums](https://www.themoviedb.org/talk)
- **Android Development**: Visit [Stack Overflow](https://stackoverflow.com/questions/tagged/android)
- **Jetpack Compose**: See [Compose Pathway](https://developer.android.com/courses/pathways/compose)

---

**Happy Coding! 🎬🍿**
