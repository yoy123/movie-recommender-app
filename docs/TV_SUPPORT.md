# TV_SUPPORT.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

Complete documentation of Fire TV / Android TV specific implementation: DPAD navigation, focus management, androidx.tv usage, leanback launcher configuration, remote control handling, and 10-foot UI design patterns.

---

## 1. Product Flavor Architecture

### Flavor Configuration

**Flavors:** `mobile`, `firestick`

**Code:** [build.gradle.kts:47](../app/build.gradle.kts#L47)
```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("mobile") {
        dimension = "platform"
    }
    create("firestick") {
        dimension = "platform"
    }
}
```

**Source Sets:**
- Shared: `app/src/main/` - data layer, models, repository
- Mobile: `app/src/mobile/` - touch UI, phone/tablet layouts
- Firestick: `app/src/firestick/` - TV UI, DPAD navigation

**Build Variants:**
- `mobileDebug`, `mobileRelease`
- `firestickDebug`, `firestickRelease`

**Key Principle:** **No runtime conditionals.** Separate source sets = separate implementations. No `if (isTV)` checks.

---

## 2. Fire TV Manifest Configuration

### Application Tag

**Code:** [AndroidManifest.xml (firestick):7](../app/src/firestick/AndroidManifest.xml#L7)
```xml
<application
    android:theme="@style/Theme.MovieRecommender.TV"
    android:banner="@drawable/tv_banner">
```

**Critical Attributes:**
- `android:theme` → TV-specific theme (no action bar, immersive)
- `android:banner` → **REQUIRED** for Fire TV certification (320×180px horizontal banner)

**Issue:** `tv_banner` drawable may not exist (see [KNOWN_ISSUES.md #17](KNOWN_ISSUES.md#17))

---

### Leanback Launcher Intent

**Code:** [AndroidManifest.xml (firestick):14](../app/src/firestick/AndroidManifest.xml#L14)
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter>
```

**Purpose:** Makes app visible on Fire TV home screen (in "Apps & Games" section).

**Without `LEANBACK_LAUNCHER`:** App wouldn't appear in TV launcher → not installable on Fire TV.

---

### Required Features

**Code:** [AndroidManifest.xml (firestick):22](../app/src/firestick/AndroidManifest.xml#L22)
```xml
<uses-feature
    android:name="android.software.leanback"
    android:required="false" />
<uses-feature
    android:name="android.hardware.touchscreen"
    android:required="false" />
```

**Rationale:**
- `leanback` with `required="false"` → app works on **both** TV and mobile (universal APK)
- `touchscreen` with `required="false"` → Fire TV has no touchscreen, but app still installs

**If `required="true"`:** App would be restricted to TV-only devices.

---

## 3. androidx.tv Library Usage

### Dependencies

**Code:** [build.gradle.kts:85](../app/build.gradle.kts#L85)
```kotlin
implementation("androidx.tv:tv-material:1.0.0-rc01")
implementation("androidx.tv:tv-foundation:1.0.0-rc01")
```

**Purpose:**
- `tv-material` → TV-optimized Material Design components (cards, buttons with focus states)
- `tv-foundation` → DPAD navigation primitives (focus management, spatial navigation)

**Status:** Release Candidate (RC01) - stable enough for production.

---

### TV Composables

**Used in Firestick UI:**

1. **TvLazyColumn** (vertical list with focus)
   - Replaces `LazyColumn` for TV
   - Handles DPAD up/down navigation automatically

2. **Card** (focusable movie card)
   - Built-in focus border + scale animation
   - Optimized for 10-foot UI (large touch targets)

3. **Surface** (focusable container)
   - Handles focus state changes
   - Provides focus ripple effect

**Code Reference:** Used throughout `app/src/firestick/java/.../ui/screens/`

---

## 4. DPAD Navigation

### Input Events

**Fire TV Remote Buttons:**
| Button | Android KeyEvent | Purpose |
|--------|-----------------|---------|
| Up | `KEYCODE_DPAD_UP` | Navigate up |
| Down | `KEYCODE_DPAD_DOWN` | Navigate down |
| Left | `KEYCODE_DPAD_LEFT` | Navigate left |
| Right | `KEYCODE_DPAD_RIGHT` | Navigate right |
| Center | `KEYCODE_DPAD_CENTER` | Select/click |
| Back | `KEYCODE_BACK` | Go back |
| Home | `KEYCODE_HOME` | Exit to launcher |
| Menu | `KEYCODE_MENU` | Context menu |

**Compose Handling:** Automatic via `Modifier.focusable()`. No manual KeyEvent listeners needed.

---

### Focus System

**Compose Focus API:**

```kotlin
Modifier.focusable()
    .onFocusChanged { state ->
        if (state.isFocused) {
            // Element has focus
        }
    }
```

**Focus Traversal:** Spatial navigation (up/down/left/right) handled by Compose automatically based on layout position.

**Focus Indicator:** TV components have built-in focus border (highlight on focus).

---

### Initial Focus

**Problem:** On screen load, no element has focus → user must press DPAD to start navigation.

**Solution:** Use `FocusRequester` to auto-focus first element.

**Code Pattern:**
```kotlin
val focusRequester = remember { FocusRequester() }

LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}

FirstElement(
    modifier = Modifier.focusRequester(focusRequester)
)
```

**Status:** May not be implemented in all screens (see [KNOWN_ISSUES.md #16](KNOWN_ISSUES.md#16))

---

## 5. Fire TV UI Patterns

### 10-Foot Design

**Principles:**
1. **Large touch targets** → minimum 48dp, recommend 80dp
2. **High contrast** → visible from 10 feet away
3. **Clear focus indicator** → user knows where they are
4. **Simplified navigation** → fewer choices per screen
5. **Readable text** → 18sp minimum, 24sp recommended

**Implementation:**
- Movie cards: 150×225dp (larger than mobile)
- Button text: 24sp (vs 16sp mobile)
- Spacing: 24dp between elements (vs 16dp mobile)

---

### Focus Management

**Issue:** Compose focus can be lost if state changes (e.g., list updates).

**Solution:** Use `key()` in lazy lists to preserve focus:
```kotlin
TvLazyColumn {
    items(movies, key = { it.id }) { movie ->
        MovieCard(movie)
    }
}
```

**Without `key`:** Focus resets to first item on list update.

---

### Scrolling Behavior

**DPAD scrolling:** Discrete (one item at a time).  
**Mobile scrolling:** Continuous (smooth fling).

**TV Optimization:**
- Snap to grid (movies align perfectly)
- No overscroll (no "bounce" effect)
- Focus follows scroll (focused item stays visible)

**Handled automatically by `TvLazyColumn`.**

---

## 6. Screen-by-Screen TV Implementation

### Genre Selection Screen

**Code:** [GenreSelectionScreen.kt (firestick)](../app/src/firestick/java/com/movierecommender/app/ui/screens/GenreSelectionScreen.kt)

**Layout:**
- `TvLazyColumn` of genre cards
- Each card: 300×150dp with genre name + icon
- Focus: Vertical navigation (up/down)

**Remote Actions:**
- Up/Down → navigate genres
- Center → select genre → navigate to MovieSelectionScreen

---

### Movie Selection Screen

**Code:** [MovieSelectionScreen.kt (firestick)](../app/src/firestick/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt)

**Layout:**
- `TvLazyVerticalGrid` (3 columns)
- Each movie card: 150×225dp poster
- Checkbox overlay (top-right corner)

**Remote Actions:**
- DPAD → navigate grid
- Center → toggle selection
- Back → return to genres
- Menu → show "Get Recommendations" if 5 selected

---

### Recommendations Screen

**Code:** [RecommendationsScreen.kt (firestick)](../app/src/firestick/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt)

**Layout:**
- `TvLazyColumn` of recommendation cards
- Each card: poster + title + explanation + "Watch Now" button

**Remote Actions:**
- Up/Down → navigate recommendations
- Center on card → expand details
- Center on "Watch Now" → navigate to StreamingScreen

---

### Streaming Screen

**Code:** [StreamingScreen.kt (firestick)](../app/src/firestick/java/com/movierecommender/app/ui/screens/StreamingScreen.kt)

**Layout:**
- ExoPlayer fullscreen
- Playback controls overlay (bottom)
- Progress bar + time display

**Remote Actions:**
- Center → play/pause
- Left/Right → seek ±10 seconds
- Back → stop playback, return to recommendations

---

## 7. Differences from Mobile Version

### UI Components

| Feature | Mobile | Firestick |
|---------|--------|-----------|
| Navigation | `LazyColumn` | `TvLazyColumn` |
| Movie cards | Touch-optimized (smaller) | Focus-optimized (larger) |
| Buttons | Material3 Button | TV Surface Button |
| Input | Touch + gestures | DPAD only |
| Focus indicator | None | Border + scale animation |

---

### Navigation Flow

**Mobile:**
1. Genre Selection (vertical scroll)
2. Movie Selection (grid with swipe)
3. Get Recommendations (button)
4. Recommendations (scroll, click "Watch Now")

**Firestick:**
1. Genre Selection (DPAD up/down)
2. Movie Selection (DPAD grid navigation)
3. Get Recommendations (menu button)
4. Recommendations (DPAD up/down, center on "Watch Now")

**Same flow, different input methods.**

---

### ViewModel Layer

**Shared:** Both flavors use same `MovieViewModel.kt` in their flavor directories.

**Code:**
- Mobile: `app/src/mobile/.../ui/viewmodel/MovieViewModel.kt`
- Firestick: `app/src/firestick/.../ui/viewmodel/MovieViewModel.kt`

**Why separate?** Different navigation parameters (mobile uses `NavController`, TV uses different back stack handling).

**Data layer:** Fully shared (`MovieRepository.kt` in `main/`).

---

## 8. Performance Considerations

### Focus Traversal Performance

**Problem:** Complex layouts (100+ focusable elements) can cause slow focus traversal.

**Solution:**
- Limit items per screen (15–20 recommendations)
- Use `key()` in lazy lists (avoid recomposition)
- Lazy loading (don't load all movie posters upfront)

**Status:** Not an issue with current design (max 15 recommendations).

---

### Image Loading

**Mobile:** Aggressive caching (250 MB Coil cache).  
**Fire TV:** Same cache size, but slower network (Wi-Fi vs Ethernet).

**Optimization:**
- Preload posters while user navigates
- Use low-res placeholders (TMDB provides multiple sizes)
- Cache indefinitely (movies don't change)

**Code:** Coil's `AsyncImage` handles automatically.

---

## 9. Testing Fire TV

### Emulator Setup

**Command:** [run_tv_emulator.sh](../run_tv_emulator.sh)
```bash
emulator @tv_1080p -no-snapshot-load
```

**AVD Configuration:**
- Profile: Android TV (1080p)
- API Level: 33 (Android 13)
- RAM: 2 GB
- Storage: 8 GB

---

### DPAD Testing

**Script:** [test_dpad_navigation.sh](../test_dpad_navigation.sh)

**Manual Testing:**
1. Launch app
2. Press DPAD up/down → verify focus moves
3. Press center → verify action triggers
4. Press back → verify navigation pops
5. Navigate to all screens → verify no focus loss

---

### Focus Debugging

**Enable visual focus indicator:**
```bash
adb shell settings put global focus_debug_mode 1
```

**Result:** Red border around focused element (system-wide).

**Disable:**
```bash
adb shell settings put global focus_debug_mode 0
```

---

## 10. Fire TV Certification Requirements

### Required for Submission

✅ = Implemented  
❌ = Missing  
⚠️ = Partial

| Requirement | Status | Notes |
|-------------|--------|-------|
| Leanback launcher intent | ✅ | [AndroidManifest.xml #14](../app/src/firestick/AndroidManifest.xml#L14) |
| Banner image (320×180) | ❌ | See [KNOWN_ISSUES.md #17](KNOWN_ISSUES.md#17) |
| No touchscreen required | ✅ | `required="false"` in manifest |
| DPAD navigation | ✅ | All screens navigable |
| Focus indicators | ⚠️ | Built-in, but may need enhancement |
| Playback controls | ✅ | ExoPlayer standard controls |
| Back button handling | ✅ | Compose navigation handles |
| No crash on launch | ✅ | Tested on Fire TV Stick 4K |

**Blockers for certification:**
1. Missing banner image → must add
2. Focus indicators may need enhancement (see [KNOWN_ISSUES.md #16](KNOWN_ISSUES.md#16))

---

## 11. Known TV-Specific Issues

### Issue 1: No Visual Focus Indicator

**Evidence:** [KNOWN_ISSUES.md #16](KNOWN_ISSUES.md#16)

**Problem:** Default focus indicator may be too subtle on some TVs.

**Recommended Fix:**
```kotlin
Modifier.onFocusChanged { state ->
    if (state.isFocused) {
        // Add 4dp border with accent color
        Modifier.border(4.dp, Color.Blue, RoundedCornerShape(8.dp))
    }
}
```

---

### Issue 2: No Leanback Banner

**Evidence:** [KNOWN_ISSUES.md #17](KNOWN_ISSUES.md#17)

**Problem:** `android:banner="@drawable/tv_banner"` in manifest, but drawable may not exist.

**User Impact:** Fire TV shows default icon (unprofessional).

**Recommended Fix:**
1. Design 320×180px banner
2. Save to `app/src/firestick/res/drawable/tv_banner.png`
3. Verify manifest reference

---

### Issue 3: Long Text Truncation

**Problem:** Movie titles > 30 chars may overflow on TV (smaller screen space than mobile).

**Solution:**
```kotlin
Text(
    text = movie.title,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis
)
```

**Status:** Likely implemented, but not verified in all screens.

---

## 12. Future TV Enhancements

### Voice Search

**Amazon Alexa Integration:**
- "Alexa, search for action movies"
- App receives intent with query → navigate to search results

**Implementation:**
```xml
<intent-filter>
    <action android:name="android.intent.action.SEARCH" />
</intent-filter>
```

**Status:** Not implemented.

---

### Fire TV Channels

**Feature:** Add app row to Fire TV home screen with "Continue Watching" or "Favorites".

**API:** `TvProvider` content provider.

**Status:** Not implemented.

---

### Multi-User Profiles

**Feature:** Switch between family members' favorites (e.g., "Dad's Favorites", "Mom's Favorites").

**Implementation:** Add profile selection screen before genre selection.

**Status:** Single-user only (user name stored globally).

---

## 13. Testing Matrix

### Devices

| Device | Tested | Notes |
|--------|--------|-------|
| Fire TV Stick 4K | ✅ | Primary target |
| Fire TV Cube | ❓ | Should work (same OS) |
| Nvidia Shield | ❓ | Android TV (not Fire TV) |
| Samsung Tizen TV | ❌ | Not compatible (different OS) |

---

### Navigation Scenarios

- [x] Genre Selection → Movie Selection
- [x] Movie Selection (5 movies) → Recommendations
- [x] Recommendations → Trailer playback
- [x] Recommendations → Streaming (Watch Now)
- [x] Favorites → Remove favorite
- [x] Settings → Change preferences
- [x] Back button from each screen
- [ ] Focus retention on configuration change (screen rotation - N/A for TV)

---

## 14. Code References

### Firestick-Specific Files

**Manifest:**
- [AndroidManifest.xml](../app/src/firestick/AndroidManifest.xml)

**MainActivity:**
- [MainActivity.kt](../app/src/firestick/java/com/movierecommender/app/MainActivity.kt)

**Navigation:**
- [AppNavigation.kt](../app/src/firestick/java/com/movierecommender/app/ui/navigation/AppNavigation.kt)

**ViewModel:**
- [MovieViewModel.kt](../app/src/firestick/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt)

**Screens:**
- [GenreSelectionScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/GenreSelectionScreen.kt)
- [MovieSelectionScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/MovieSelectionScreen.kt)
- [FavoritesScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/FavoritesScreen.kt)
- [RecommendationsScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/RecommendationsScreen.kt)
- [TrailerScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/TrailerScreen.kt)
- [StreamingScreen.kt](../app/src/firestick/java/com/movierecommender/app/ui/screens/StreamingScreen.kt)

---

**Next Review:** When androidx.tv stable (1.0.0) releases, new TV features added, or Fire TV certification requirements change.
