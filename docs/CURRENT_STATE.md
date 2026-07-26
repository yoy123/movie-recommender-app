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

AI mode is enabled only after the user grants consent. The current OpenAI request uses `gpt-5` through the Chat Completions endpoint.

The two-attempt strategy now varies reasoning effort rather than unsupported legacy sampling fields:

1. `minimal`
2. `low` with stricter retry instructions

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
3. configured Torznab endpoints
4. Internet Archive
5. Public Domain Torrents
6. Pirate Bay API
7. TorrentGalaxy
8. 1337x adapter

Swarm API results must report live peers. The public-domain catalogs provide verified HTTPS `.torrent` downloads without peer-count metadata.

### TV torrent discovery

- Popcorn TV
- EZTV
- configured Torznab endpoints
- Pirate Bay TV categories

Episode lookups query all available sources concurrently and select the result with the highest seed count. Torznab and EZTV inventories paginate up to bounded 2,000-release safety limits and use short-lived caches for season/episode browsing.

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
- Playback begins only after a contiguous no-stall buffer is available from the start or saved resume position.
- Buffer sizing uses file size, duration, conservative observed speed, peak-rate headroom, and the projected remaining download deficit.
- Media3 keeps 60 seconds minimum and up to five minutes of local read-ahead after playback starts.
- Torrent pieces are prioritized from the first missing piece ahead of the player.
- The screen is kept awake during playback.

Torrent cache behavior:

- preserves at least 500 MB of actually available device storage
- measures allocated filesystem blocks so sparse-file length does not overstate disk use
- rejects a source that cannot safely fit before starting playback
- retains the active torrent, position, and duration for 60 minutes after accidental player backout
- continues downloading during the resume window
- deletes on 60-minute inactivity, root app/task exit, previous-process termination detected at next launch, device reboot, explicit clear, or source switch
- does not delete merely because the player activity is recreated, backgrounded, or navigated away from

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

Each flavor currently reports 15 tests: 14 executed successfully and one skipped `LlmSmokeTest`. The shared deterministic suite covers torrent buffer policy, Torznab parsing/caching, Internet Archive results, and Public Domain Torrents parsing. UI, Room migration, repository orchestration, and real-device lifecycle behavior still lack automated coverage.

## Current Engineering Risks

1. Automated coverage remains narrow and does not exercise UI, Room migrations, repository fallback orchestration, or Android lifecycle receivers.
2. Several network integrations depend on unofficial third-party services and may break without notice.
3. `MovieRepository` is very large and owns too many responsibilities.
4. Plex configuration/service code is currently unwired.
5. Torrent playback cannot guarantee uninterrupted operation after total peer loss, network failure, process termination during playback, or malformed media.
6. Build targets Java 8 while the local toolchain is much newer, producing obsolete source/target warnings.
7. API keys are compiled into the application package through `BuildConfig`; they should be treated as extractable client secrets.

## Documentation Authority

Use this order when claims conflict:

1. Current Kotlin/Gradle/manifest code
2. `docs/CURRENT_STATE.md`
3. Topic-specific documents in `docs/`
4. Historical Git commits and old audit text
