# Movie Recommender Android App

An Android application that uses **AI (OpenAI GPT)** to recommend movies based on your taste! Select 5 movies you've already seen from a specific genre, and get intelligent, personalized movie recommendations with explanations.

## Features

### � Personalization
- **Custom Name**: Personalize your experience with your own name
- **Recommendation Preferences**: Customize how recommendations are generated
  - Release Date Preference: Choose between Latest Releases, Mix of Old & New, or Classic Films
  - Recommendation Style: Pick from Mainstream Popular, Mix of Popular & Indie, or Hidden Indie Gems

### 🎬 Movie Discovery
- **Genre Selection**: Browse and choose from 20+ movie genres including Action, Drama, Sci-Fi, Horror, and more
- **Smart Search**: Search for any movie across all genres with instant results
- **Movie Details**: View comprehensive movie information including ratings, release year, and descriptions
- **TMDB Integration**: Powered by The Movie Database (TMDB) API with extensive movie data

### ❤️ Favorites Management
- **Custom Favorites Collection**: Build your personal "Dan's Favorites" (or your custom name) collection
- **Cross-Genre Favorites**: Add any movie from any genre to your favorites
- **Easy Management**: Add or remove movies from favorites with a single tap
- **Persistent Storage**: Your favorites are saved locally and always accessible

### 🤖 AI-Powered Recommendations
- **GPT-4o-mini Integration**: Advanced AI analyzes your movie preferences
- **Personalized Analysis**: Get insights into your taste based on your selected movies
- **15 Custom Recommendations**: Receive tailored movie suggestions with explanations
- **Smart Matching**: AI understands themes, mood, storytelling style, and pacing - not just popularity
- **Hidden Gems**: Discover lesser-known films that match your preferences
- **TMDB Ratings**: Each recommendation includes community ratings

### 🎥 In-App Experience
- **Trailer Viewing**: Watch movie trailers directly within the app
- **Beautiful UI**: Modern Material Design 3 interface with smooth animations
- **Dark Theme Support**: Optimized viewing experience
- **Offline First**: All your data stored locally using Room database

### 🔒 Privacy & Data
- **No Account Required**: Start using immediately without registration
- **Local Storage**: All data stays on your device
- **Secure APIs**: HTTPS encrypted communications
- **No Tracking**: Your privacy is respected

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

### 🎬 First Time Setup
1. **Customize Your Experience**: Enter your name on the welcome screen to personalize the app
2. **Set Preferences**: Tap the settings icon to configure:
   - **Release Date Preference**: Choose Latest Releases, Mix of Old & New, or Classic Films
   - **Recommendation Style**: Pick Mainstream Popular, Mix of Popular & Indie, or Hidden Indie Gems
3. These preferences will be remembered and used for all future recommendations!

### 🎯 Getting AI-Powered Recommendations

#### Step 1: Choose Your Genre
- Browse the genre selection screen
- Tap on any genre to explore movies (Action, Drama, Sci-Fi, Horror, Comedy, etc.)

#### Step 2: Select 5 Movies
- Browse through the movie grid or use the search bar
- Tap on movies you've watched and enjoyed
- Selected movies show a checkmark
- You must select exactly 5 movies to get recommendations

#### Step 3: Get Recommendations
- Once 5 movies are selected, tap the "Get Recommendations" button
- The AI will analyze your taste based on:
  - Themes and storytelling styles
  - Mood and pacing
  - Genre conventions and your preferences
  - Your configured recommendation settings

#### Step 4: Explore Recommendations
- View your personalized analysis explaining your taste
- Browse 15 custom movie recommendations
- Each recommendation includes:
  - Movie title and release year
  - TMDB community rating
  - Detailed explanation of why it matches your taste
- Tap on any movie to:
  - Add it to your favorites
  - Watch the trailer
  - Read more details

### ❤️ Managing Your Favorites Collection

#### Accessing Favorites
1. From the genre screen, tap on "Your Custom Name's Favorites" (with heart icon)
2. This is your personal collection of movies from all genres

#### Adding to Favorites
- **From any genre**: Tap the heart icon on any movie poster
- **From search**: Search for any movie and tap the heart icon
- **From recommendations**: Tap a recommended movie to view details, then add to favorites

#### Organizing Favorites
- View all your favorite movies in one place
- Movies show with posters, titles, and ratings
- Remove any movie by tapping the ✕ button
- Search within your favorites

#### Using Favorites for Recommendations
- Select 5 movies from your favorites across different genres
- Get recommendations based on your diverse taste
- Perfect for when you want AI suggestions based on your all-time favorites

### 🎥 Watching Trailers
1. Tap on any movie (from search, recommendations, or favorites)
2. Movie details appear with trailer option
3. Tap "Watch Trailer" to view within the app
4. Trailers play using YouTube integration

### 🔄 Starting Fresh
- Tap the refresh icon in any selection screen to clear your choices
- Your preferences and favorites are always preserved
- Only your current movie selections are cleared

---

**See also:**
- [DEES_FAVORITES.md](DEES_FAVORITES.md) - Detailed favorites feature documentation
- [LLM_INTEGRATION.md](LLM_INTEGRATION.md) - How the AI recommendation engine works
- [PLAY_STORE_GUIDE.md](PLAY_STORE_GUIDE.md) - Complete Play Store submission guide

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
