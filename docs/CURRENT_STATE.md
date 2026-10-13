# OpenStream+ Current State

**Last code review:** 2026-10-11
**Status:** Primary source-of-truth summary

## Product

OpenStream+ is a Kotlin Android application for discovering movies and TV shows, generating recommendations, viewing trailers and watch-provider options, and playing supported media.

The project has two product flavors:

- `mobile`: touch-oriented Jetpack Compose UI for phones and tablets.
- `firestick`: remote/DPAD-oriented Fire TV UI using a Leanback browse shell and Compose sub-screens.

The former embedded Live TV guide was removed on 2026-07-25 because it depended on an unstable third-party playlist and EPG source. Legitimate free-live-TV services remain available through their own Fire TV applications, but OpenStream+ does not embed or proxy their channel feeds.

## Firestick trailer navigation fix (2026-10-12)

The movie recommendation Watch Options -> Watch Trailer action now resolves to the trailer/{title}/{videoUrl} destination in ComposeActivity, decodes the Base64 trailer URL, and returns to recommendations when backing out. The earlier missing destination caused users to land on Fire TV Home. Built and installed on Firestick 192.168.1.238; user confirmed it works again. Playback continues through OpenStream+'s internal trailer player.

## Current Feature Scope

### Shared functionality

- Movie and TV-show genre browsing and search through TMDB.
- Selection of 1–5 titles as recommendation inputs.
- Movie favorites in Room and persisted TV-show favorites in DataStore, both with clickable Watch Options / Delete actions.
- Configurable recommendation preferences:
  - popularity
  - indie emphasis
  - release-year range
  - tone
  - international content
  - experimental content
- AI-first recommendations always attempt OpenAI without a setting switch; selected titles/preferences are shared with OpenAI and disclosed via static Settings text.
- Deterministic TMDB fallback with a visible debug reason when AI cannot be used safely.
- No user-selectable recommendation-engine style.
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
- Settings and TV favorites: Preferences DataStore

## Recommendation Engine

AI is always the primary engine. The old Settings AI Data Sharing switch and first-use consent dialog are removed. Generating recommendations sends selected titles, preferences and bounded candidates to OpenAI; Settings displays a static disclosure.

Production AI recommendations are bounded to verified TMDB candidates. The repository requires at least 15 safe candidates, calls the configured OpenAI model (`gpt-6-astra` by default, with GPT-6/GPT-4.1 fallback), validates exactly 15 canonical titles, and rejects generic, duplicated, excluded, malformed, or out-of-candidate output. Model-access failures automatically retry with compatible fallback models before TMDB fallback is used.

TMDB fallback occurs only if the OpenAI key is absent, safe candidate pool is too small, the API fails, or output validation rejects the result. The existing fallback debug dialog remains. There is no engine or AI permission toggle.

Candidate and fallback ranking use Bayesian-adjusted ratings so very small vote counts cannot dominate the list. Retry exclusions are applied to both AI and TMDB paths.

## Outstanding user-reported regressions / design changes (2026-10-12)

1. User confirmed OpenAI worked after manually enabling the Settings switch. The toggle and gates are now removed; both variants request AI by default. New Firestick installation and validation are pending.
2. Implemented Firestick source: favorite movie and TV cards offer Select for Recommendations / Remove from Recommendations, Watch Options and Delete; top-level Clear Selections clears both movie and TV picks without deleting favorites. Device validation pending.
3. Firestick Favorites now opens a native BrowseSupportFragment with AndroidX Leanback ListRows (selected picks, movie favorites, TV favorites and search), reusing front-menu PickerMediaCardPresenter and FocusBorderImageCardView. Debug APK built and was installed on Firestick 192.168.1.238. Live screenshot verified horizontal rails; user confirmed visual improvement. Previous InflateException did not recur, but full DPAD/menu verification remains. Mobile continues to use the Compose vertical grid and needs a separate redesign.
4. Completely different title selections reportedly produce the **same TMDB fallback list**. Test selected IDs/mode, candidate discovery, cache/session state, ranking sensitivity and exclusion logic using controlled comparisons.

All four items are OPEN. The mandatory-features list and DESIGN_FEATURES_2_PROGRESS.md specify acceptance criteria; updating documentation is not implementing or testing these fixes.

## External Services

### Metadata and recommendations

- TMDB
- OpenAI
- IMDb page scraping fallback for trailers

### Movie torrent discovery

Primary movie search queries YTS, Popcorn, Torrentio, and Knaben concurrently and ranks live results by available storage, quality, and swarm health. YTS and Popcorn now expose their available encodes for device-aware selection rather than preselecting a file without the storage budget. If no primary result fits the known device budget, lookup continues through configured Torznab endpoints, Internet Archive, Public Domain Torrents, Pirate Bay, TorrentGalaxy, and 1337x. Unknown-size sources remain subject to the playback service's metadata-based storage preflight.

Swarm API results must report live peers. The public-domain catalogs provide verified HTTPS `.torrent` downloads without peer-count metadata.

### TV torrent discovery

- Popcorn TV
- EZTV
- Torrentio
- Knaben
- configured Torznab endpoints
- Pirate Bay TV categories

Episode lookups query all available sources concurrently and rank preferred quality, seeders, and peers. Torrentio file-index metadata is retained so the player can select the requested episode from a multi-file torrent. Torznab and EZTV inventories paginate up to bounded 2,000-release safety limits and use short-lived caches for season/episode browsing.

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

DataStore currently defines 27 keys covering:

- onboarding and appearance
- legacy AI consent keys are no longer used
- recommendation preferences
- database cleanup timestamp
- Plex connection/library settings
- saved TV favorites

### Cleanup

On application startup, orphaned movie rows older than 30 days are removed when at least seven days have passed since the previous cleanup. Favorites are excluded from cleanup.

## Playback

- Media3 ExoPlayer renders local torrent-streamed files.
- `TorrentStreamService` runs as a media-playback foreground service.
- Playback begins after a bounded contiguous buffer is available from the start or saved resume position.
- Buffer sizing uses file size, duration, conservative observed speed, peak-rate headroom, and the projected remaining download deficit, capped at 256 MB.
- Media3 keeps 60 seconds minimum and up to five minutes of local read-ahead after playback starts.
- Torrent pieces are prioritized from the first missing piece ahead of the player.
- Recoverable metadata, startup-piece, and storage-admission failures advance automatically through the ranked source queue.
- The screen is kept awake during playback.

Torrent cache behavior:

- preserves at least 500 MB of actually available device storage
- measures allocated filesystem blocks so sparse-file length does not overstate disk use
- rejects a source that cannot safely fit before starting playback
- does not currently support rolling chunk-only storage when the entire video cannot fit; adding this requires engine support and device validation
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

Verified for current changes on 2026-10-11:

```bash
./gradlew \
  :app:assembleFirestickDebug \
  :app:assembleMobileDebug \
  :app:testFirestickDebugUnitTest \
  :app:testMobileDebugUnitTest
```

Result: `BUILD SUCCESSFUL`.

Both flavor unit-test suites pass; the live OpenAI `LlmSmokeTest` is skipped by default. The shared deterministic suite covers recommendation fallback/result policy, Bayesian recommendation ranking, model-specific OpenAI request construction, torrent buffer policy, Torznab parsing/caching, Torrentio/Knaben adapters, magnet file selection, Internet Archive results, and Public Domain Torrents parsing. The new GPT-6-first model chain has not been tested against a live OpenAI API account during this update; access and response quality remain to be verified. UI, Room migration, repository orchestration, and real-device lifecycle behavior still lack automated coverage.

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
