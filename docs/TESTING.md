# OpenStream+ Testing

**Last verified:** 2026-07-25

## Current Automated Status

Commands:

```bash
./gradlew :app:testMobileDebugUnitTest
./gradlew :app:testFirestickDebugUnitTest
```

Both tasks complete successfully.

Actual test inventory:

- `app/src/test/java/com/movierecommender/app/LlmSmokeTest.kt`
- one test per flavor variant
- test result: skipped

Therefore, the project currently has no executing automated unit-test coverage.

## Current Build Verification

```bash
./gradlew \
  :app:assembleFirestickDebug \
  :app:assembleMobileDebug \
  :app:testFirestickDebugUnitTest \
  :app:testMobileDebugUnitTest
```

Verified result on 2026-07-25: `BUILD SUCCESSFUL`.

The local JDK reports Java 8 source/target obsolescence warnings.

## Required Test Layers

### Unit tests

Highest-priority targets:

1. LLM response parsing
2. exclusion and deduplication rules
3. TMDB preference scoring
4. title/year normalization
5. torrent live-peer filtering
6. provider deep-link construction
7. exact provider-content resolver parsing
8. user-name sanitization
9. torrent cache allowance calculation

### Repository integration tests

Use fake services/DAOs to verify:

- AI success path
- AI retry path
- AI validation failure -> TMDB fallback
- AI disabled -> no LLM call
- movie torrent provider fallback order
- one provider failure does not stop the chain
- TV episode result chooses highest seeds
- watch options combine providers and torrent correctly

### Room migration tests

Required paths:

- schema 1 -> 2
- schema 2 -> 3
- schema 1 -> 3

Assertions:

- existing movie/favorite rows survive
- provider crosswalk table exists at version 3
- composite primary key works

### UI tests

Mobile:

- genre -> selection -> recommendation
- consent accept/decline
- favorites
- trailer navigation
- watch options
- playback error recovery

Firestick:

- initial focus
- row traversal
- selection count/action cards
- card entry without accidental action
- consume center `KeyDown` and matching `KeyUp`
- back exits nested action focus
- settings and favorites shortcuts
- episode picker

## Manual Regression Checklist

### Both flavors

- [ ] cold launch
- [ ] first-run name flow
- [ ] movie genres load
- [ ] TV genres load
- [ ] search
- [ ] select 1 title
- [ ] select 5 titles
- [ ] reject sixth title
- [ ] AI consent accepted
- [ ] AI consent declined
- [ ] AI disabled uses TMDB mode
- [ ] retry does not repeat current-session results
- [ ] add/remove favorite
- [ ] movie trailer
- [ ] TV trailer
- [ ] watch providers
- [ ] missing provider app
- [ ] movie torrent found
- [ ] no-live-peer torrent rejected
- [ ] TV season/episode picker
- [ ] playback start, pause, seek, exit
- [ ] network-off error handling

### Firestick-specific

- [ ] launcher banner and icon
- [ ] movie row
- [ ] TV row
- [ ] favorites row
- [ ] settings row
- [ ] no Live TV row
- [ ] visible focus on every control
- [ ] nested card-action navigation
- [ ] remote back behavior
- [ ] screensaver blocked during playback

## Removed Live TV Regression

After the 2026-07-25 removal, verify:

- no Live TV row appears
- no `live_tv` route exists
- no `tvpass` or `thetvapp` URL remains
- Firestick build does not require `media3-exoplayer-hls`

## CI Recommendation

A minimal CI workflow should run:

```bash
./gradlew \
  :app:assembleMobileDebug \
  :app:assembleFirestickDebug \
  :app:testMobileDebugUnitTest \
  :app:testFirestickDebugUnitTest \
  --no-daemon
```

Once real tests exist, CI should also publish JUnit reports and fail when the suite contains zero executed tests.

## Definition of Done for New Features

A change is complete when:

1. both affected flavors compile
2. automated tests cover deterministic logic
3. Firestick changes are manually DPAD-tested
4. docs are updated
5. no secrets or generated APKs are added to Git
6. unrelated working-tree changes are preserved
