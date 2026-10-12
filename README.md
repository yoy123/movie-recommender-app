# OpenStream+

OpenStream+ is a Kotlin Android and Fire TV application for discovering movies and TV shows, generating personalized recommendations, finding watch options, and playing supported media.

## Platforms

| Flavor | Interface | Application ID |
| --- | --- | --- |
| `mobile` | Touch-oriented Jetpack Compose | `com.movierecommender.app` |
| `firestick` | Leanback browse shell plus DPAD-oriented Compose screens | `com.movierecommender.app.firestick` |

The embedded Firestick Live TV guide was removed on 2026-07-25 because it depended on an unstable third-party playlist and EPG source.

## Main Features

- Browse and search movies and TV shows through TMDB.
- Select 1–5 titles to seed recommendations.
- Always request OpenAI recommendations with verified TMDB candidates; no AI on/off switch, TMDB fallback only for AI errors or unavailable inputs.
- Tune recommendation preferences for popularity, indie content, year range, tone, international content, and experimental content.
- Maintain movie favorites in Room and TV-show favorites in DataStore. Favorites support Watch Options and Delete actions.
- View trailers and TMDB watch-provider availability.
- Launch supported streaming applications through mapped package names and deep links.
- Store exact provider-content links in a Room-backed crosswalk.
- Find movie torrents through a multi-source fallback chain, with device-storage-aware source selection and a quality/swarm-health balance.
- Browse TV seasons and episodes across Popcorn TV, EZTV, configured Torznab endpoints, and Pirate Bay TV.
- Play torrent-backed media through Media3 ExoPlayer and `TorrentStreamService`.

## Technology

- Kotlin
- Jetpack Compose
- AndroidX Leanback and AndroidX TV
- MVVM with `StateFlow`
- Room database version 3
- Preferences DataStore
- Retrofit and OkHttp
- OpenAI Chat Completions (`gpt-6-astra` default with automatic GPT-6 and GPT-4.1 model fallback; configurable with `OPENAI_MODEL`)
- TMDB
- Media3 ExoPlayer
- TorrentStream-Android

## Project Layout

```text
app/src/main/       shared data, persistence, APIs, repository, playback service
app/src/mobile/     phone/tablet UI and ViewModel
app/src/firestick/  Fire TV Leanback/Compose UI and ViewModel
docs/               current architecture and engineering documentation
```

`MovieRepository` is the main business-logic and integration layer. The mobile and Firestick flavors have separate UI and ViewModel implementations over the shared repository.

## Configuration

Create `local.properties` in the project root:

```properties
TMDB_API_KEY=your_tmdb_key
OPENAI_API_KEY=your_openai_key
```

The file is ignored by Git. Values are compiled into `BuildConfig`, so client APKs must not be treated as a secure place for long-lived privileged secrets.

## Build

```bash
./gradlew :app:assembleMobileDebug
./gradlew :app:assembleFirestickDebug
```

Release builds require the signing properties documented in [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

## Tests

```bash
./gradlew :app:testMobileDebugUnitTest
./gradlew :app:testFirestickDebugUnitTest
```

Both flavor unit-test suites passed on 2026-10-12 after the always-on AI change, including storage-budget, provider-variant, torrent-read, and recommendation tests. The opt-in `LlmSmokeTest` remains skipped; Android lifecycle and device playback still require instrumentation testing.

## Documentation

Start with:

1. [`docs/CURRENT_STATE.md`](docs/CURRENT_STATE.md)
2. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
3. [`docs/FEATURES.md`](docs/FEATURES.md)
4. [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md)

Current code is authoritative whenever an older document or Git history conflicts with the implementation.

## Current Verification

On 2026-10-12, both debug flavors compiled and both flavor unit-test tasks passed after removing the AI switch and consent gate. Playback was reported working on-device; no new automated ADB playback test was performed.
