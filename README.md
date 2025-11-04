# Movie Recommender Android App

An Android application that uses **AI (OpenAI GPT)** to recommend movies based on your taste! Select 5 movies you've already seen from a specific genre, and get intelligent, personalized movie recommendations with explanations.

## Features

- 🎬 **Genre Selection**: Choose from various movie genres
- ❤️ **Dee's Favorites**: Custom collection for saving any movies across all genres
- 🔍 **Movie Search**: Search for movies within your selected genre (or any genre in Favorites)
- ✅ **Movie Selection**: Select exactly 5 movies you've watched
- 🤖 **AI-Powered Recommendations**: Uses OpenAI's GPT-4o-mini to analyze your taste and suggest movies
- 💡 **Smart Analysis**: Understands themes, mood, storytelling style - not just popularity
- 🎯 **Hidden Gems**: Discovers lesser-known films that match your taste
- 💾 **Local Storage**: Your selections and favorites are saved locally using Room database
- 🌐 **TMDB Integration**: Powered by The Movie Database (TMDB) API for movie data

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **AI**: OpenAI GPT-4o-mini
- **Database**: Room
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Async Operations**: Kotlin Coroutines + Flow
- **Navigation**: Jetpack Navigation Compose

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API Level 34
- **TMDB API Key** (free to obtain)
- **OpenAI API Key** (required for AI recommendations - very affordable!)

### Getting Your TMDB API Key

1. Go to [The Movie Database (TMDB)](https://www.themoviedb.org/)
2. Create a free account or sign in
3. Go to Settings > API
4. Request an API key (choose "Developer" option)
5. Fill in the required information
6. Copy your API key

### Getting Your OpenAI API Key

1. Visit [OpenAI Platform](https://platform.openai.com/signup)
2. Create an account
3. Go to [API Keys](https://platform.openai.com/api-keys)
4. Click "Create new secret key"
5. Copy your key (starts with `sk-proj-...`)

**Cost**: ~$0.0002-$0.0005 per recommendation (less than a cent!)

### Configuration

1. Clone this repository:
   ```bash
   git clone https://github.com/yoy123/movie-recommender-app.git
   cd movie-recommender-app
   ```

2. Copy the example properties file:
   ```bash
   cp local.properties.example local.properties
   ```

3. Edit `local.properties` and add your API keys:
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   
   TMDB_API_KEY=your_tmdb_key_here
   OPENAI_API_KEY=sk-proj-your_openai_key_here
   OMDB_API_KEY=your_omdb_key_here_or_leave_empty
   ```

4. Open the project in Android Studio
5. Sync the project with Gradle files

### Building the Project

1. Wait for Gradle sync to complete
2. Build the project: `Build > Make Project` or press `Ctrl+F9` (Windows/Linux) or `Cmd+F9` (Mac)
3. Run the app: Click the "Run" button or press `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac)

## How to Use

### Getting Movie Recommendations:
1. **Select a Genre**: On the first screen, choose a movie genre you're interested in
2. **Select 5 Movies**: Browse or search for movies in that genre and select 5 movies you've already watched
3. **Get Recommendations**: Once you've selected 5 movies, tap "Get Recommendations"
4. **View AI Results**: Read personalized movie recommendations with explanations from the AI
5. **Start Over**: Tap the refresh icon to clear selections and start again

### Using Dee's Favorites:
1. **Open Favorites**: Select "Dee's Favorites" (heart icon) from the genre screen
2. **Search Any Movie**: Use the search bar to find any movie from any genre
3. **Add to Collection**: Tap a movie poster to add it to your favorites
4. **Remove Movies**: Tap the ✕ button on any favorite to remove it
5. **Build Your List**: Create your personal watchlist across all genres

See [DEES_FAVORITES.md](DEES_FAVORITES.md) for detailed information about the favorites feature.

## Project Structure

```
app/
├── src/main/
│   ├── java/com/movierecommender/app/
│   │   ├── data/
│   │   │   ├── local/          # Room database, DAOs
│   │   │   ├── model/          # Data models
│   │   │   ├── remote/         # API service
│   │   │   └── repository/     # Repository pattern
│   │   ├── ui/
│   │   │   ├── navigation/     # Navigation setup
│   │   │   ├── screens/        # Compose screens (Genre, Movie, Recommendations, Favorites)
│   │   │   ├── theme/          # App theme
│   │   │   └── viewmodel/      # ViewModels
│   │   ├── MainActivity.kt
│   │   └── MovieRecommenderApplication.kt
│   ├── res/                    # Resources (layouts, strings, etc.)
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Algorithm

The recommendation algorithm works as follows:

1. For each of the 5 selected movies, the app fetches:
   - Similar movies (based on TMDB's similarity algorithm)
   - Recommended movies (based on TMDB's recommendation engine)

2. Movies are scored based on:
   - Frequency of recommendation (how many of your 5 movies recommend it)
   - Vote average (TMDB rating)
   - Popularity score

3. Results are sorted and the top 20 unique recommendations are displayed

## Troubleshooting

### Common Issues

**Gradle Sync Failed**
- Ensure you have JDK 17 installed
- Check your internet connection
- Try `File > Invalidate Caches and Restart`

**API Key Error**
- Make sure you've replaced `YOUR_API_KEY_HERE` with your actual API key
- Ensure the API key is wrapped in quotes correctly
- Rebuild the project after changing the API key

**Build Errors**
- Update Android Studio to the latest version
- Sync Gradle files: `File > Sync Project with Gradle Files`
- Clean and rebuild: `Build > Clean Project` then `Build > Rebuild Project`

**App Crashes on Launch**
- Check LogCat for error messages
- Ensure you have internet permission (already configured in manifest)
- Verify your TMDB API key is valid

## Dependencies

- AndroidX Core KTX
- Jetpack Compose (UI, Material3, Navigation)
- Lifecycle & ViewModel
- Room Database
- Retrofit & OkHttp
- Gson
- Coil (Image Loading)
- Kotlin Coroutines

## API Attribution

This app uses the TMDB API but is not endorsed or certified by TMDB.

![TMDB Logo](https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg)

## License

This project is provided as-is for educational purposes.

## Documentation

- [GETTING_STARTED.md](GETTING_STARTED.md) - Detailed setup and troubleshooting guide
- [LLM_INTEGRATION.md](LLM_INTEGRATION.md) - Information about AI-powered recommendations
- [DEES_FAVORITES.md](DEES_FAVORITES.md) - Guide to using the custom favorites feature
- [ALGORITHM.md](ALGORITHM.md) - Original algorithmic approach (replaced by AI)

## Future Enhancements

- [ ] User accounts and cloud sync
- [ ] Share recommendations with friends
- [ ] Multiple custom collections (beyond Dee's Favorites)
- [ ] Advanced filtering options in favorites
- [ ] Movie details screen with full cast/crew
- [ ] Watch providers integration
- [ ] Export favorites to external formats
- [ ] Add notes/ratings to favorite movies
