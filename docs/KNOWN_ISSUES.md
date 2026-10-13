# OpenStream+ Known Issues

**Last code review:** 2026-10-11

**Status:** Current active issue inventory

## Current Summary

There are no known compile-blocking issues in either debug flavor. The largest risks are test coverage, unstable third-party integrations, oversized architecture components, and client-side secret handling.

## New Firestick defects reported October 12, 2026 — open

- **AI enable switch caused fallback (code fixed, device verification pending):** User confirmed OpenAI worked when enabled. Removed the switch, gating and consent dialog from both flavors, so AI is always attempted. TMDB remains an operational fallback. Updated APK verification is needed.
- **Favorites selection actions implemented, device check pending:** Firestick menu now includes Select for Recommendations / Remove from Recommendations alongside Watch Options and Delete for movies and TV. Clear Selections clears picks without deleting favorites. Verify DPAD focus, five-input cap and state updates on a Firestick.
- **Firestick native Leanback Favorites visually verified; follow-up testing pending:** Replaced the Compose Favorites screen with a native BrowseSupportFragment/ListRow activity under the Leanback theme, using the same poster presenter and focus border as the picker. Installed APK on Firestick 192.168.1.238; live screenshot confirms horizontal rails, user confirmed visual improvement, and the former InflateException did not recur. Verify DPAD menu actions, focus restoration, search and clear-selection behavior. Mobile still needs a horizontal-rail redesign.
- **TMDB fallback identical for different seeds:** Audit input propagation, candidate pool, stale state and caching, similarity weighting and exclusions; add controlled A/B different-selection tests. Do not conceal defects with arbitrary random ranking.

These are user-reported problems, not verified root causes or implemented fixes. See DESIGN_FEATURES_2_PROGRESS.md and the project-root Mandatory-Design-Features-2.txt.

## High Priority

### 1. Automated coverage is still narrow

Both flavor unit-test suites passed on 2026-10-12 and skip the opt-in `LlmSmokeTest` by default. Coverage includes recommendation policy/request construction, torrent buffer policy, and torrent-source parsing, but still lacks broad UI and repository integration coverage.

Impact:

- recommendation parsing and repository fallback orchestration can regress silently
- DPAD focus bugs require manual discovery
- Room migrations are not actively verified
- Android alarm, reboot, task-removal, and process-lifecycle behavior are not instrumented

Recommended action:

- add deterministic tests for recommendation parsing/scoring and repository orchestration
- add Room migration tests for 1->2->3
- add UI tests for core mobile navigation
- add Firestick focus/key-event and torrent-cache lifecycle instrumentation coverage

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

### 6. Torrent playback still depends on external swarm and network stability

The service rejects sources that cannot fit while preserving 500 MB of device storage. It starts playback after the native torrent engine's initial preparation (not a second 64–256 MB prebuffer), then uses a piece-aware reader. It also prioritizes missing pieces ahead of the player and retains interrupted sessions for 60 minutes.

Residual risk: no local buffering policy can guarantee uninterrupted playback if every peer disappears, the network fails, the process is killed during active playback, or the media file is malformed.

Recommended action: continue device testing with slow and unstable swarms, record startup time and rebuffer rates, and tune the bounded safety factors from evidence.

### 7. Chunk-only torrent playback is not implemented

The current torrent engine requires that the selected video can fit on local storage while retaining 500 MB of free space. Provider selection favors fitting sources before comparing quality and swarm health. If no full file fits, the player declines to start rather than exhaust storage.

Recycling downloaded torrent pieces during playback requires explicit support from the native torrent engine and validation that old pieces are not re-requested, resume remains valid, and seeking still works. That guarantee has not been established, so rolling chunk-only playback is deferred rather than introduced unsafely.

Recommended action: verify a disk-bounded rolling-cache engine on a real low-storage Fire TV, including rewind and resume, before enabling this fallback.

### 8. Plex infrastructure is unwired

`PlexApiService` and seven DataStore settings exist, but no UI/ViewModel/repository path calls the service.

Impact:

- dead product surface
- token stored without user-visible value
- future developers may assume the feature is complete

Recommended action: either complete the flow or remove the dormant settings/service.

### 8. TV state is not persisted consistently

Movies use Room flags and saved TV favorites persist in Preferences DataStore, but TV recommendation selections remain in ViewModel state and sometimes travel through JSON extras.

Impact: saved TV favorites survive app restarts, but in-progress TV selections remain vulnerable to process death or activity recreation.

Recommended action: persist in-progress TV selections using SavedStateHandle or a dedicated session store and add TV favorites persistence tests.

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
