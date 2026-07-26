# OpenStream+ Troubleshooting

**Last verified:** 2026-07-25

## Quick Diagnosis

| Symptom | Likely cause |
| --- | --- |
| App fails at launch or content is empty | missing/invalid TMDB key or network failure |
| AI recommendations fail | missing OpenAI key, consent disabled, API/network error, invalid model output |
| Recommendations still appear without AI | expected TMDB-only fallback behavior |
| Favorites missing after reinstall/clear data | local application data was removed |
| Movie torrent unavailable | no source match or no live peers |
| TV episode unavailable | Popcorn TV/EZTV failure or no seeded release |
| Streaming provider does not open | app missing, package mismatch, unsupported deep link |
| Firestick action triggers unexpectedly | DPAD KeyUp not consumed after focus change |
| Playback stalls | insufficient peers/download rate or unsupported media |
| Live TV row missing | expected; feature removed 2026-07-25 |

## Logging

Useful tags include:

```text
MovieRepository
LlmRecommendationService
TmdbApiService
RateLimitInterceptor
TorrentStreamService
WatchOptions
AppNavigation
```

Example:

```bash
adb logcat | grep -E 'MovieRepository|LlmRecommendationService|TmdbApiService|TorrentStreamService|WatchOptions'
```

## TMDB Problems

### Symptoms

- genres do not load
- search returns errors
- posters/details missing
- HTTP 401 or 429

### Checks

1. Confirm `TMDB_API_KEY` exists in `local.properties` before building.
2. Rebuild after changing the key.
3. Confirm network connectivity.
4. Inspect HTTP status in Logcat.

### HTTP 429

The app has a `RateLimitInterceptor` that retries up to three times with backoff. Persistent 429 responses can still fail after retries.

Reduce repeated requests and retry later.

## OpenAI Problems

### AI disabled or consent declined

This is not an error. The repository should use TMDB-only recommendations.

### Missing API key

Set:

```properties
OPENAI_API_KEY=...
```

Rebuild the app. The key is compiled into `BuildConfig`.

### Invalid or incomplete response

The service performs a stricter second attempt. If validation still fails, the repository falls back to TMDB.

Current model:

```text
gpt-4.1-mini
```

## Database Problems

### Favorites missing after an update

The current database uses explicit migrations and does not use destructive fallback.

Investigate:

- whether app data was cleared
- whether a different application ID/flavor was installed
- whether the app was uninstalled
- migration exceptions in Logcat

Mobile and Firestick use different application IDs and therefore separate application sandboxes.

### Migration crash

A missing future migration should fail loudly rather than delete data.

Check `AppDatabase` version and `.addMigrations()` registrations.

## Torrent Lookup Problems

### Movie

Fallback order:

1. YTS
2. Popcorn movie API
3. Pirate Bay adapter
4. TorrentGalaxy
5. 1337x adapter

A result with zero seeds and zero peers is rejected.

### TV episode

Popcorn TV and EZTV are queried when possible. No result may mean:

- IMDb ID resolution failed
- source unavailable
- episode numbering mismatch
- no live peers

## Playback Problems

### Never starts

Check:

- foreground-service permission
- torrent metadata callback
- selected video file
- download speed
- available cache space
- live peers

### Stalls after starting

The source may be slower than the media bitrate. Adaptive prebuffering reduces but cannot eliminate this condition.

### Seek fails

Accurate piece prioritization depends on known duration and file size. Wait until metadata/player duration is available.

### Cache remains after exit

The app requests cleanup on activity destruction. A process crash or force stop can bypass normal cleanup. Clear application cache manually when needed.

## Firestick Focus Problems

### Center press triggers an inner button immediately

When card activation moves focus on `KeyDown`, consume the matching `KeyUp`. Otherwise the newly focused child can interpret the release as a click.

### Focus disappears

Verify:

- one initial `FocusRequester`
- target is composed before requesting focus
- focused styling has sufficient contrast
- focus is restored when leaving nested action mode

### External streaming app does not open

The watch-options launcher tries several intent strategies. Confirm the provider app is installed and visible under Android 11+ package-query rules.

## Removed Live TV Feature

The following no longer exist:

- Live TV home row
- M3U playlist fetch
- XMLTV EPG fetch
- HLS guide/player screen

Do not troubleshoot `tvpass.org` or `thetvapp.to` inside OpenStream+; there is no current code path using them.

## Build Problems

### Clean build

```bash
./gradlew clean
./gradlew :app:assembleMobileDebug :app:assembleFirestickDebug
```

### Java 8 warning

The current JDK reports that Java 8 source/target is obsolete. This is a warning, not the cause of a failed build.

### Release signing failure

Confirm all four release signing properties are present and the keystore path is correct.

## Before Reporting a Bug

Include:

- flavor: mobile or Firestick
- device/Fire OS or Android version
- exact navigation steps
- relevant Logcat section
- whether AI mode was enabled
- title/year/episode involved
- provider/torrent source if shown
- whether the problem reproduces after a clean launch
