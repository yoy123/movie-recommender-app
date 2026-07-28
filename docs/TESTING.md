# OpenStream+ Testing

**Last verified:** 2026-07-28

## Verified Commands

```bash
./gradlew \
  :app:assembleMobileDebug \
  :app:assembleFirestickDebug \
  :app:testMobileDebugUnitTest \
  :app:testFirestickDebugUnitTest \
  --stacktrace
```

Result: `BUILD SUCCESSFUL`.

## Current Unit-Test Results

The shared JVM test suite runs once for each flavor.

| Test class | Tests per flavor | Result |
|---|---:|---|
| `InternetArchiveServiceTest` | 3 | pass |
| `KnabenApiServiceTest` | 2 | pass |
| `LlmSmokeTest` | 1 | skipped unless explicitly enabled |
| `OpenAiRequestFactoryTest` | 5 | pass |
| `PirateBayEpisodeParserTest` | 1 | pass |
| `PublicDomainTorrentsServiceTest` | 3 | pass |
| `RecommendationRankingPolicyTest` | 2 | pass |
| `RecommendationResultTest` | 3 | pass |
| `TorrentBufferPolicyTest` | 5 | pass |
| `TorrentMagnetUtilsTest` | 2 | pass |
| `TorrentioServiceTest` | 2 | pass |
| `TorznabServiceTest` | 4 | pass |

Per flavor:

- total discovered: 33
- executed: 32
- skipped: 1
- failures: 0
- errors: 0

Across both variants, Gradle records 64 successful deterministic test executions and two skipped smoke-test executions. Recommendation tests cover typed fallback notices, Bayesian quality ranking, and model-specific OpenAI request construction. The opt-in smoke test now performs a real bounded candidate-rerank request and fails on API or output-validation errors. Repository orchestration, consent UI, and fallback dialogs still require broader integration/UI coverage.

## Torrent Buffer Policy Coverage

`TorrentBufferPolicyTest` verifies:

1. The cache budget preserves the 500 MB device-storage reserve while counting existing allocated cache.
2. A sufficiently slow torrent requires the full remaining movie before playback.
3. Resume buffering does not require bytes behind the saved position.
4. A fast torrent uses substantial peak-rate headroom without requiring the whole movie.
5. Partial-megabyte source sizes round upward for storage admission.

The Android-specific alarm, boot receiver, foreground-service lifecycle, task-removal behavior, and real torrent-piece availability still require instrumentation or device testing.

## Remaining Automated Gaps

### Repository behavior

Add fake-service/DAO tests for:

- AI success and two-attempt retry
- AI validation failure to TMDB fallback
- blocked AI data sharing means no OpenAI request and produces TMDB fallback
- movie torrent fallback order
- one failed source does not stop the provider chain
- TV episode result selection
- provider/torrent watch-option combination

### Persistence

Add Room migration tests for:

- schema 1 to 2
- schema 2 to 3
- schema 1 to 3
- favorite preservation
- provider crosswalk composite keys

Add persistence tests for saved playback position, duration, retention deadline, and cleanup of the active magnet's state.

### Android lifecycle

Add instrumentation tests for:

- leaving the player retains the torrent session
- returning within 60 minutes resumes from the saved position
- expiry receiver clears cache and position after the deadline
- task removal clears immediately
- root app finish clears immediately
- device reboot receiver clears retained cache
- activity recreation does not clear cache
- previous-process marker clears stale cache on the next launch

### UI

Mobile:

- genre to selection to recommendation
- consent accepted/declined
- favorites
- trailer and watch-option navigation
- playback resume/error recovery

Firestick:

- initial focus and row traversal
- nested card/action focus
- center `KeyDown` plus matching `KeyUp` consumption
- remote back behavior
- episode picker
- playback backout and resume

## Manual Torrent Playback Checklist

### Buffering and storage

- [ ] Start a torrent with stable peers.
- [ ] Confirm the player remains on the prebuffer screen until the contiguous gate completes.
- [ ] Verify a high-bitrate scene plays without a mid-playback stall.
- [ ] Verify a slow swarm waits longer rather than starting prematurely.
- [ ] Verify a torrent with no progress for three minutes returns an actionable error.
- [ ] Verify an oversized source is rejected while preserving 500 MB of device storage.
- [ ] Confirm sparse-file logical size does not cause a false cache-full condition.

### Resume lifecycle

- [ ] Back out of playback and return immediately; confirm the same cache is reused.
- [ ] Confirm playback resumes near the saved position.
- [ ] Confirm the torrent continues downloading during the 60-minute window.
- [ ] Return before 60 minutes and confirm the deadline is canceled/replaced by active playback.
- [ ] Leave unused beyond 60 minutes and confirm cache plus saved position are removed.
- [ ] Remove/close the app task and confirm immediate cleanup.
- [ ] Finish the root app activity and confirm cleanup.
- [ ] Rotate/recreate the mobile activity and confirm no cleanup occurs.
- [ ] Restart the Firestick and confirm cleanup.
- [ ] Force-stop or terminate the process, reopen the app, and confirm stale cache cleanup.
- [ ] Select a different torrent and confirm the previous cache is removed.

### Playback controls

- [ ] Pause and resume.
- [ ] Seek backward.
- [ ] Seek forward into available data.
- [ ] Seek forward into missing data and verify priority/recovery behavior.
- [ ] Confirm the Fire TV screensaver remains blocked during playback.
- [ ] Repeat with a TV episode.

## General Regression Checklist

### Both flavors

- [ ] cold launch
- [ ] onboarding/name flow
- [ ] movie and TV genres
- [ ] search
- [ ] select one to five titles
- [ ] reject a sixth title
- [ ] AI consent accepted and declined
- [ ] AI-disabled TMDB path
- [ ] session recommendation deduplication
- [ ] add/remove favorites
- [ ] movie and TV trailers
- [ ] watch providers and missing-provider-app handling
- [ ] movie torrent lookup
- [ ] TV season/episode picker
- [ ] network-off error handling

### Firestick-specific

- [ ] launcher banner/icon
- [ ] movie, TV, favorites, and settings rows
- [ ] no Live TV row
- [ ] visible focus on all controls
- [ ] nested action navigation
- [ ] remote back behavior

## Current Warnings

The verified build still reports:

- unused `excludedTitles` parameter in `MovieRepository`
- unused `year` parameter in `MovieRepository`
- Java 8 source/target obsolescence warnings from the local JDK/toolchain

## CI Recommendation

Run the verified command on every change and publish the JUnit XML reports. CI should fail on test failures and should separately report skipped tests so `LlmSmokeTest` does not create a false impression of coverage.

## Definition of Done

A change is complete when:

1. both affected flavors compile
2. deterministic logic has automated tests
3. Android lifecycle behavior is tested on an emulator or device when applicable
4. Firestick changes are manually DPAD-tested
5. documentation is updated
6. secrets and generated APKs are not committed
7. unrelated working-tree changes are preserved
