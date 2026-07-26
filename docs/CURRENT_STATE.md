# OpenStream+ Current State

**Last verified:** 2026-07-25  
**Status:** Primary source-of-truth summary

## Product

OpenStream+ is a Kotlin Android application for discovering movies and TV shows, generating recommendations, viewing trailers and watch-provider options, and playing supported media.

The project has two product flavors:

- `mobile`: touch-oriented Jetpack Compose UI for phones and tablets.
- `firestick`: remote/DPAD-oriented Fire TV UI using a Leanback browse shell and Compose sub-screens.

The former embedded Live TV guide was removed on 2026-07-25 because it depended on an unstable third-party playlist and EPG source. Legitimate free-live-TV services remain available through their own Fire TV applications, but OpenStream+ does not embed or proxy their channel feeds.

## Current Feature Scope

### Shared functionality

- Movie and TV-show genre browsing and search through TMDB.
- Selection of 1–5 titles as recommendation inputs.
- Local favorites collection.
- Configurable recommendation preferences:
  - popularity
  - indie emphasis
  - release-year range
  - tone
  - international content
  - experimental content
- Opt-in AI recommendations through OpenAI.
- TMDB-only recommendation mode when AI is disabled or consent is declined.
- Movie and TV trailer lookup.
- TMDB watch-provider availability.
- Streaming-app package mapping and deep-link attempts.
- Exact provider-content crosswalk persistence for imported or resolved links.
- Movie torrent lookup and in-app playback.
- TV episode browsing and episode torrent lookup.

### Firestick-specific functionality

- Leanback launcher and banner.
- Browse rows for movie genres, TV genres, favorites, and settings.
- DPAD-first focus and navigation behavior.
- Two-level card/button focus handling where a card must be entered before an action is triggered.
- Compose screens for selection, recommendations, favorites, settings, trailers, watch options, and playback.

### Not currently wired as a user-facing feature

`PlexApiService` and Plex DataStore settings exist, but no current UI or repository flow calls the service. Plex remains mapped as a watch provider/deep-link target.

## Architecture

```text
mobile UI / firestick UI
          |
flavor-specific MovieViewModel
          |
      MovieRepository
      /      |       \
   TMDB    OpenAI   media/provider services
      \      |       /
       Room + DataStore
```

- Shared code: `app/src/main/`
- Mobile UI: `app/src/mobile/`
- Firestick UI: `app/src/firestick/`
- Primary orchestration: `MovieRepository`
- Reactive state: flavor-specific `MovieViewModel` using `StateFlow`
- Local database: Room version 3
- Settings: Preferences DataStore

## Recommendation Modes

### AI mode

AI mode is enabled only after the user grants consent. The current OpenAI request uses `gpt-4.1-mini` through the Chat Completions endpoint.

Movie open-mode retry temperatures:

1. `0.6`
2. `0.3`

Bounded candidate reranking uses a stricter pair:

1. `0.4`
2. `0.2`

The repository validates output and falls back to TMDB-derived recommendations when the LLM path fails.

### TMDB-only mode

When AI is disabled, consent is declined, or the OpenAI path fails, the repository ranks TMDB candidates using the active preference values. This mode does not send selected titles to OpenAI.

## External Services

### Metadata and recommendations

- TMDB
- OpenAI
- IMDb page scraping fallback for trailers

### Movie torrent discovery

Fallback order:

1. YTS
2. Popcorn movie API
3. Pirate Bay API
4. TorrentGalaxy
5. 1337x adapter

A result must report live peers before it is returned.

### TV torrent discovery

- Popcorn TV
- EZTV

Episode lookups query both sources concurrently when possible and select the result with the highest seed count.

### Watch-provider handling

- TMDB watch-provider endpoint
- `StreamingAppRegistry` for Android/Fire TV package and link mappings
- `ProviderContentResolverRegistry` for exact provider URLs/content IDs
- Room-backed provider crosswalks

## Persistence

### Room database version 3

Entities:

- `movies`
- `provider_content_crosswalk`

Migrations:

- `1 -> 2`: no schema change; preserves existing data
- `2 -> 3`: creates the provider-content crosswalk table

The app does not use destructive migration fallback.

### DataStore

DataStore currently defines 26 keys covering:

- onboarding and appearance
- AI consent
- recommendation preferences
- database cleanup timestamp
- Plex connection/library settings

### Cleanup

On application startup, orphaned movie rows older than 30 days are removed when at least seven days have passed since the previous cleanup. Favorites are excluded from cleanup.

## Playback

- Media3 ExoPlayer renders local torrent-streamed files.
- `TorrentStreamService` runs as a media-playback foreground service.
- Adaptive prebuffering estimates the amount of data needed before playback starts or resumes.
- The screen is kept awake during playback.
- Cache is cleared when playback activities are destroyed through the service clear-cache intent.

Torrent cache allowance is dynamic:

- reserves 500 MB of device space
- uses 75% of the remaining free space
- enforces a minimum allowance of 100 MB
- currently has no explicit upper cap

## Build Configuration

- Compile SDK: 34
- Target SDK: 34
- Minimum SDK: 24
- Java/Kotlin target: 1.8
- Mobile application ID: `com.movierecommender.app`
- Firestick application ID: `com.movierecommender.app.firestick`
- Firestick version code override: 4

API keys are loaded from untracked `local.properties` into `BuildConfig`.

## Verification

Verified on 2026-07-25:

```bash
./gradlew \
  :app:assembleFirestickDebug \
  :app:assembleMobileDebug \
  :app:testFirestickDebugUnitTest \
  :app:testMobileDebugUnitTest
```

Result: `BUILD SUCCESSFUL`.

The only unit test class is `LlmSmokeTest`; its test is skipped in both flavors. The project therefore has no executing automated unit-test coverage despite the successful test tasks.

## Current Engineering Risks

1. No active automated regression coverage.
2. Several network integrations depend on unofficial third-party services and may break without notice.
3. `MovieRepository` is very large and owns too many responsibilities.
4. Plex configuration/service code is currently unwired.
5. Torrent cache calculation has no maximum cap and its comment does not match the 75% constant.
6. Build targets Java 8 while the local toolchain is much newer, producing obsolete source/target warnings.
7. API keys are compiled into the application package through `BuildConfig`; they should be treated as extractable client secrets.

## Documentation Authority

Use this order when claims conflict:

1. Current Kotlin/Gradle/manifest code
2. `docs/CURRENT_STATE.md`
3. Topic-specific documents in `docs/`
4. Historical Git commits and old audit text
