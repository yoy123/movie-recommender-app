# OpenStream+ Fire TV Support

**Last verified:** 2026-07-25

## Flavor

The Fire TV product flavor is `firestick`.

Application ID:

```text
com.movierecommender.app.firestick
```

The manifest requires Leanback support and explicitly does not require a touchscreen or faketouch device.

## Launcher

The Firestick manifest:

- removes the inherited mobile launcher activity
- declares `com.movierecommender.app.firestick.MainActivity`
- exports a launcher activity alias with `LEANBACK_LAUNCHER`
- supplies `ic_tv_banner`

The launcher activity hosts `BrowseGenreFragment`.

## Browse Shell

Current Leanback rows:

1. Movie Genres
2. TV Show Genres
3. Favorites
4. Settings

The previous Live TV row was removed on 2026-07-25.

Genre cards launch `LeanbackPickerActivity`. Favorites and settings launch `ComposeActivity` directly.

## UI Technologies

The Firestick flavor intentionally mixes:

- AndroidX Leanback for the home browse shell and picker
- Jetpack Compose for detailed screens and playback flows
- AndroidX TV dependencies for TV-oriented Compose behavior

This hybrid structure should be preserved unless a full migration is planned and tested.

## Compose Screens

Current Firestick Compose screens include:

- movie/TV selection
- recommendations
- favorites
- settings
- watch options
- trailer
- streaming player
- consent dialog

`ComposeActivity` accepts intent extras to choose a start screen and restore content-mode/selection context.

## DPAD Focus Contract

Every interactive control must be reachable and visibly focused with a Fire TV remote.

### Card focus

Movie and TV cards can contain multiple actions. The intended behavior is:

1. DPAD focuses the card.
2. DPAD center enters the card and focuses the primary action.
3. Left/right navigates among inner actions.
4. DPAD center triggers the focused action.
5. Back exits inner-action mode and returns focus to the card.

### KeyDown/KeyUp rule

A DPAD-center press produces both `KeyDown` and `KeyUp`. When `KeyDown` changes focus to a child control, the following `KeyUp` must also be consumed. Otherwise the child may receive the release event and trigger unintentionally.

The same rule applies to back/escape handlers that move focus.

### Visual requirements

Focused items should change more than color alone where practical:

- border thickness
- scale
- elevation
- text weight/size
- background contrast

## External Application Launch

The Firestick manifest declares package visibility for many streaming applications. `WatchOptionsDialog` tries multiple launch strategies:

- search intent
- native/deep link
- package launch intent
- Leanback launcher query
- generic main intent
- browser fallback

Failure to open a provider must remain non-fatal.

## Removed Live TV Feature

The integrated Firestick Live TV guide depended on an externally hosted M3U playlist and XMLTV EPG. It was removed because that source class is not stable enough for a maintained application.

Removed:

- browse row
- navigation constant and route
- `LiveTvScreen.kt`
- playlist and EPG parsers
- direct HLS playback dependency

Official services such as Plex, Pluto TV, Sling Freestream, and Tubi should be used through their own Fire TV applications rather than embedded feeds.

## Fire TV Test Matrix

Minimum manual checks:

- cold launch from Fire TV home
- banner and icon display
- movie-genre row navigation
- TV-genre row navigation
- loading placeholders replaced without focus loss
- favorites row
- settings row
- picker selection of 1–5 titles
- card entry and nested-action navigation
- recommendation generation in AI and TMDB modes
- consent dialog navigation
- trailer playback
- watch-option dialog
- external provider launch failure/success
- movie torrent playback
- TV episode picker and playback
- back-stack behavior from every screen
- screensaver suppression during playback

## Current Risks

1. No automated DPAD/focus regression tests.
2. Leanback and Compose navigation coexist, increasing state-transfer complexity.
3. Provider package names and Fire OS intent behavior vary by device generation.
4. TV show selection is passed between some activities as JSON.
5. The Firestick version code is overridden independently from the base application version.
