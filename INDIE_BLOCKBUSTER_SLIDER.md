# Indie vs Blockbuster Slider Feature

## Overview
Added a sliding preference control that allows users to adjust LLM recommendations from mainstream blockbusters to indie films.

## Implementation Details

### UI Components
**MovieSelectionScreen.kt** and **FavoritesScreen.kt**
- Added a `Surface` container with elevated styling at the bottom of the screen
- Slider appears only when movies are selected (1-5 movies)
- Labeled with "Blockbusters" on left and "Indie Films" on right
- Title: "Recommendation Style"
- Uses Material 3 `Slider` component with primary color scheme
- Adjusted `LazyVerticalGrid` bottom padding to accommodate slider (140.dp)

### State Management
**MovieViewModel.kt**
- Added `indiePreference: Float = 0.5f` to `MovieUiState`
  - 0.0 = Full blockbuster preference
  - 0.5 = Balanced (default)
  - 1.0 = Full indie preference
- Added `updateIndiePreference(preference: Float)` function
- Updated `generateRecommendations()` to pass `indiePreference` to repository

### Data Layer
**MovieRepository.kt**
- Updated `getRecommendations()` signature to accept `indiePreference: Float`
- Passes preference value to `LlmRecommendationService`

**LlmRecommendationService.kt**
- Updated `getRecommendationsFromLlm()` to accept `indiePreference: Float = 0.5f`
- Modified `buildPrompt()` to incorporate preference:
  - **< 0.33**: "Focus primarily on mainstream blockbusters, critically acclaimed hits, and popular films with wide theatrical releases."
  - **0.33-0.67**: "Balance between mainstream hits and indie films, including both popular movies and hidden gems."
  - **> 0.67**: "Focus primarily on indie films, hidden gems, lesser-known titles, and critically acclaimed art house cinema."

## User Experience
1. User selects 1-5 movies from any genre or favorites
2. Slider appears at bottom with "Recommendation Style" label
3. User adjusts slider left for blockbusters, right for indie films
4. Slider position is preserved across navigation
5. When "Get Recommendations" is tapped, LLM uses preference to tailor results

## Technical Details
- Slider range: 0.0 to 1.0 (continuous)
- Default value: 0.5 (balanced)
- State persists during session (resets on app restart)
- Prompt modification is transparent to user
- Works with both genre selection and favorites flows

## Style Guidelines
The LLM interprets the preference as follows:
- **0.0-0.33**: Mainstream blockbusters (wide releases, popular films)
- **0.33-0.67**: Mixed recommendations (balance of popular and hidden gems)
- **0.67-1.0**: Indie focus (art house, lesser-known, hidden gems)

## Future Enhancements
- Persist preference across app restarts (SharedPreferences)
- Add visual indicator showing current preference (e.g., "Balanced", "Indie Focus")
- Include preference in recommendation results display
- A/B test different threshold values for optimal categorization
