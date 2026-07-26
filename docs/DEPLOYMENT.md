# OpenStream+ Deployment

**Last verified:** 2026-07-25

## Prerequisites

- Android SDK with API 34
- JDK supported by the current Android Gradle Plugin/Gradle wrapper
- project Gradle wrapper
- TMDB API key
- optional OpenAI API key for AI recommendations

Create untracked `local.properties`:

```properties
TMDB_API_KEY=your_tmdb_key
OPENAI_API_KEY=your_openai_key
```

OpenAI is optional at runtime because the app supports TMDB-only recommendations. OMDb configuration is not used.

## Build Variants

```bash
./gradlew :app:assembleMobileDebug
./gradlew :app:assembleFirestickDebug
./gradlew :app:assembleMobileRelease
./gradlew :app:assembleFirestickRelease
```

Application IDs:

- mobile: `com.movierecommender.app`
- Firestick: `com.movierecommender.app.firestick`

The Firestick flavor overrides version code to 4 and appends `-firestick` to the version name.

## Release Signing

Gradle properties:

```properties
RELEASE_STORE_FILE=app/signing/release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Keystores and passwords must not be committed.

## Current Release Build Behavior

The release type currently has:

- minification disabled
- resource shrinking disabled
- release signing configuration enabled

Older documentation stated that release builds were minified. That is not the current Gradle configuration.

## Pre-Release Verification

```bash
./gradlew clean
./gradlew \
  :app:assembleMobileRelease \
  :app:assembleFirestickRelease \
  :app:testMobileDebugUnitTest \
  :app:testFirestickDebugUnitTest
```

The shared suite currently executes 14 successful tests per flavor and skips the opt-in `LlmSmokeTest`. Manual regression remains mandatory for UI, Firestick DPAD behavior, Room migrations, and Android cache-lifecycle behavior.

## Mobile Distribution

Potential channels:

- Google Play
- direct APK distribution
- managed/private distribution

Before Play submission:

- review `PLAY_STORE_GUIDE.md`
- review and publish `PRIVACY_POLICY.md`
- complete current Data Safety declarations from actual runtime behavior
- verify whether torrent-related functionality is permitted by the selected store and jurisdiction
- remove unused permissions, including AD_ID if still unused

## Fire TV Distribution

Potential channels:

- Amazon Appstore
- direct/sideloaded APK
- managed device deployment

Required checks:

- launcher banner and icon
- `LEANBACK_LAUNCHER` visibility
- no touchscreen requirement
- complete DPAD navigation
- focus visibility
- back-stack behavior
- playback foreground-service behavior

The embedded Live TV feature was removed and should not appear in screenshots or listing text.

## Versioning

Before release, update in `app/build.gradle.kts`:

```kotlin
versionCode = ...
versionName = "..."
```

Review the Firestick-specific version override separately.

## APK and AAB Outputs

Typical APK locations:

```text
app/build/outputs/apk/mobile/debug/
app/build/outputs/apk/mobile/release/
app/build/outputs/apk/firestick/debug/
app/build/outputs/apk/firestick/release/
```

Build Android App Bundles with:

```bash
./gradlew :app:bundleMobileRelease
./gradlew :app:bundleFirestickRelease
```

## Security Checklist

- [ ] no `local.properties` in Git
- [ ] no keystore in Git
- [ ] no API key printed in logs
- [ ] production OpenAI key has limits/monitoring
- [ ] network security config reviewed
- [ ] privacy policy matches actual services
- [ ] user consent works before OpenAI calls
- [ ] Plex token handling reviewed if Plex is exposed
- [ ] third-party provider/torrent URLs validated

## Release Risks

1. API keys are extractable from APKs.
2. No executing automated regression tests.
3. Unofficial torrent integrations may violate store policy or fail review.
4. Fire OS provider intents vary by device and app version.
5. The project targets Java 8 and emits obsolescence warnings on the current JDK.

## Rollback

Keep the previous signed artifact and version metadata. A rollback build must use a higher store version code even when restoring older source behavior.
