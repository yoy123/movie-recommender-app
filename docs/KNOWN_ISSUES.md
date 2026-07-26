# OpenStream+ Known Issues

**Last verified:** 2026-07-25  
**Status:** Current active issue inventory

## Current Summary

There are no known compile-blocking issues in either debug flavor. The largest risks are test coverage, unstable third-party integrations, oversized architecture components, and client-side secret handling.

## High Priority

### 1. No executing automated test coverage

Both flavor unit-test tasks pass, but the only test method in `LlmSmokeTest` is skipped.

Impact:

- recommendation parsing can regress silently
- DPAD focus bugs require manual discovery
- Room migrations are not actively verified
- provider/torrent schema changes can break runtime behavior

Recommended action:

- add deterministic unit tests for recommendation parsing/scoring
- add Room migration tests for 1->2->3
- add UI tests for core mobile navigation
- add Firestick focus/key-event instrumentation coverage

### 2. Unofficial network integrations are fragile

Movie and TV torrent services are unofficial and can change domains, schemas, or anti-bot behavior without notice.

Impact:

- Watch Now may stop finding results
- HTML/API parsing may fail
- external services may return unsafe or misleading data

Recommended action:

- introduce a common provider interface
- add health/error metrics without personal data
- validate all returned URLs and metadata
- keep failures isolated per provider

### 3. API keys are compiled into the APK

TMDB and OpenAI keys are loaded into `BuildConfig`.

Impact:

- distributed APKs can be inspected to recover keys
- OpenAI key abuse could create charges

Recommended action:

- move privileged OpenAI calls behind a controlled backend
- restrict and monitor provider keys
- rotate keys if a public artifact was built with production credentials

## Medium Priority

### 4. `MovieRepository` owns too many responsibilities

The repository exceeds 2,500 lines and contains:

- TMDB access
- AI orchestration
- preference scoring
- persistence
- trailer lookup
- watch-provider mapping
- provider crosswalk handling
- movie torrent lookup
- TV episode lookup

Impact:

- difficult testing
- high regression surface
- duplicated helper/scoring logic

Recommended action: split by domain and inject interfaces.

### 5. Mobile and Firestick ViewModels duplicate logic

Both flavors maintain large, parallel ViewModel implementations.

Impact: fixes can land in one flavor but not the other.

Recommended action: extract shared state/reducer/use-case code while preserving separate UI source sets.

### 6. Torrent cache has no explicit maximum

Current calculation uses 75% of free space after reserving 500 MB, with a 100 MB minimum.

Impact: devices with large free storage can allocate a very large torrent cache allowance.

The code comment also says 50%, which does not match the constant.

Recommended action: add a tested upper cap and correct the comment.

### 7. Plex infrastructure is unwired

`PlexApiService` and seven DataStore settings exist, but no UI/ViewModel/repository path calls the service.

Impact:

- dead product surface
- token stored without user-visible value
- future developers may assume the feature is complete

Recommended action: either complete the flow or remove the dormant settings/service.

### 8. TV state is not persisted consistently

Movies use Room flags; TV selections are held in ViewModel state and sometimes transferred through JSON intent extras.

Impact: TV state is more vulnerable to process death and activity recreation.

Recommended action: add a TV entity/session store or a shared saved-state strategy.

### 9. Provider crosswalks are never revalidated automatically

Exact provider URLs can become stale.

Recommended action: add last-verified expiration policy and lazy revalidation.

### 10. Java 8 target is obsolete for the active JDK

Builds complete, but JDK 25 reports obsolete source/target warnings for Java 8.

Recommended action: upgrade Android Gradle/Kotlin compatibility deliberately, then raise JVM target after device and dependency testing.

## Low Priority

### 11. Unused advertising ID permission

The main manifest declares `com.google.android.gms.permission.AD_ID`, but no analytics or advertising SDK usage was found.

Recommended action: remove the permission unless a documented feature requires it.

### 12. No analytics or crash reporting

There is no integrated telemetry for recommendation failures, provider failures, or playback errors.

This is a product/privacy decision, not automatically a requirement. Any implementation should be opt-in or privacy-minimized.

### 13. No encrypted storage for Plex token

The Plex token is stored in Preferences DataStore.

Recommended action: use Keystore-backed encrypted storage if the Plex feature is completed.

### 14. Missing playback features

Not currently implemented:

- subtitle-management UI
- picture-in-picture
- playback-speed control

### 15. No full offline mode

Cached HTTP/image data may help, but core discovery and recommendation flows require network access.

## Recently Resolved

### Embedded Live TV dependency removed — 2026-07-25

The Firestick Live TV guide depended on a third-party M3U playlist and XMLTV EPG source. The source class was unstable and had no suitable maintained public API contract.

Removed:

- Leanback Live TV row
- `live_tv` navigation route
- `LiveTvScreen`
- M3U and EPG fetching/parsing
- HLS-only Media3 dependency

Official free-live-TV applications remain external services rather than embedded feeds.

### Earlier resolved items

The current code also includes fixes for:

- destructive Room migration behavior
- TMDB 429 retry/backoff
- insecure trust-all debug TLS
- OMDb dead code removal
- database orphan cleanup
- TMDB HTTP caching
- user-name sanitization
- OpenAI consent flow
- Firestick visual focus indicators
- Firestick launcher banner
- screensaver prevention during playback
- TV-specific trailer/torrent paths
- EZTV fallback
- TV episode picker
- DPAD card-action auto-trigger bug

## Verification Baseline

On 2026-07-25, these completed successfully:

```bash
./gradlew \
  :app:assembleFirestickDebug \
  :app:assembleMobileDebug \
  :app:testFirestickDebugUnitTest \
  :app:testMobileDebugUnitTest
```

Successful tasks do not imply meaningful test coverage because the test itself is skipped.
