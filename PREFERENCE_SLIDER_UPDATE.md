# Preference Slider Update - January 2025

## Overview
Updated the recommendation preference system with the following improvements:
1. Reordered sliders to match user specification
2. Added checkboxes to enable/disable individual preferences
3. Replaced single release year slider with dual-slider range selector
4. Updated LLM prompt to only include active preferences

## Slider Order (as requested)
1. **Indie vs Blockbuster** - Production style preference
2. **Popularity Level** - Cult classics vs Mainstream
3. **Release Year Range** - Dual slider (earliest to latest year, 1980-2025)
4. **Tone/Mood** - Light & Uplifting vs Dark & Serious
5. **International vs Domestic** - Geographic focus
6. **Experimental vs Traditional** - Storytelling style

## New Features

### 1. Checkbox Toggles
- Each preference now has a checkbox to enable/disable it
- When disabled, the slider is hidden and that preference is not sent to the LLM
- All preferences default to **enabled** (checked)

### 2. Dual Release Year Slider
- **Left slider (Earliest)**: Sets the minimum release year
- **Right slider (Latest)**: Sets the maximum release year
- Range: 1980 to 2025
- Visual feedback shows "From YYYY to YYYY"
- Sliders are constrained so earliest never goes after latest
- Different colors for each slider (secondary/tertiary theme colors)

### 3. Updated LLM Integration
- Only active (enabled) preferences are included in the prompt
- Release year is now a strict filter: "Only recommend films released between X and Y"
- More efficient prompts when users disable unnecessary preferences

## Technical Changes

### MovieUiState (MovieViewModel.kt)
```kotlin
// Removed single releaseYearPreference Float
// Added:
val releaseYearStart: Float = 1980f
val releaseYearEnd: Float = 2025f

// Added toggle flags for all preferences:
val useIndiePreference: Boolean = true
val usePopularityPreference: Boolean = true
val useReleaseYearPreference: Boolean = true
val useTonePreference: Boolean = true
val useInternationalPreference: Boolean = true
val useExperimentalPreference: Boolean = true
```

### Update Functions (MovieViewModel.kt)
Added toggle update functions for each preference:
- `updateUseIndiePreference(Boolean)`
- `updateUsePopularityPreference(Boolean)`
- `updateReleaseYearStart(Float)`
- `updateReleaseYearEnd(Float)`
- `updateUseReleaseYearPreference(Boolean)`
- `updateUseTonePreference(Boolean)`
- `updateUseInternationalPreference(Boolean)`
- `updateUseExperimentalPreference(Boolean)`

### Repository & LLM Service
Updated signatures to pass:
- All 6 preference values (Float)
- All 6 toggle flags (Boolean)
- Release year start and end values

### New Composables (GenreSelectionScreen.kt)

#### PreferenceSliderWithToggle
- Displays title with checkbox
- Only shows slider when enabled
- Clean, consistent UI pattern

#### ReleaseYearRangeSlider
- Title with checkbox
- Display of current range: "From YYYY to YYYY"
- Two independent sliders with constraints
- Left slider (earliest): Secondary theme color
- Right slider (latest): Tertiary theme color
- Labels showing 1980 and 2025 as boundaries

### LLM Prompt Builder (LlmRecommendationService.kt)
- Builds dynamic list of active preferences
- Only includes guidance for enabled preferences
- Release year is now a hard constraint with specific years
- Cleaner prompts when preferences are disabled

## Usage

### For Users
1. Open Settings (gear icon) from any screen
2. Check/uncheck preferences to enable/disable them
3. For Release Year Range:
   - Move left slider to set earliest year
   - Move right slider to set latest year
   - Visual feedback shows "From 1990 to 2020" (example)
4. Disabled preferences won't affect recommendations
5. All preferences default to enabled

### For Developers
All screens (Genre, Movie Selection, Favorites) now pass complete preference data including:
- 6 preference values
- 6 toggle states
- 2 release year values

## Testing Checklist
- [ ] Open Settings dialog from each screen
- [ ] Verify slider order matches specification
- [ ] Toggle each preference on/off
- [ ] Verify sliders hide when disabled
- [ ] Test release year dual slider:
  - [ ] Move left slider (earliest)
  - [ ] Move right slider (latest)
  - [ ] Verify range display updates
  - [ ] Verify constraint (start ≤ end)
- [ ] Generate recommendations with different combinations
- [ ] Verify LLM only receives enabled preferences
- [ ] Test with all preferences disabled (should give general recommendations)

## Benefits
1. **User Control**: Fine-grained control over which preferences matter
2. **Cleaner Prompts**: LLM receives only relevant guidance
3. **Precise Year Filtering**: Users can target specific eras (e.g., 1990-2000 for 90s films)
4. **Better UX**: Checkboxes make it obvious which preferences are active
5. **Flexibility**: Users can disable irrelevant preferences for their current search

## Example Use Cases

### Case 1: 90s Action Fan
- Enable: Release Year (1990-1999), Tone (Dark), Blockbusters
- Disable: International, Experimental, Popularity

### Case 2: Art House Explorer
- Enable: Indie, International, Experimental
- Disable: Release Year, Popularity, Tone

### Case 3: Recent Mainstream Only
- Enable: Release Year (2020-2025), Blockbusters, Mainstream
- Disable: All others

## Notes
- All preferences default to enabled to preserve existing behavior
- Release year range defaults to 1980-2025 (full range)
- When all preferences are disabled, LLM gives general recommendations
- Checkboxes persist across app sessions (when we add SharedPreferences/DataStore)
