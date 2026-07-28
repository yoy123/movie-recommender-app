# OpenStream+ Documentation Integrity Audit

**Date:** 2026-07-25  
**Status:** Current audit

## Scope

This audit reconciled current code against the project documentation after development continued beyond the original January 2026 documentation snapshot.

Verified areas:

- product flavors and manifests
- source-set boundaries
- Room schema and migrations
- DataStore settings
- recommendation model and retry values
- movie and TV integration paths
- provider crosswalk persistence
- torrent cache behavior
- Firestick navigation
- automated build/test status
- embedded Live TV removal

## Verified Current Claims

### Product flavors

- `mobile`
- `firestick`

Both use shared code from `app/src/main` and separate UI/ViewModel source sets.

### Room

- database version: 3
- entities: `Movie`, `ProviderContentCrosswalk`
- migrations: 1->2 and 2->3
- destructive migration fallback: absent

### DataStore

26 keys are defined across onboarding, appearance, AI consent, recommendation preferences, cleanup timing, and Plex settings.

### Recommendation service

- endpoint: OpenAI Chat Completions
- model: configurable; default `gpt-4.1`
- automatic model fallbacks: `gpt-4.1`, `gpt-4.1-mini`, `gpt-4o-mini`
- non-reasoning retry temperatures: `0.4`, then `0.2`
- reasoning-model efforts when applicable: `minimal`, then `low`
- maximum completion tokens: 4,000
- TMDB fallback: active
- explicit AI consent: active

### Torrent cache

The cache is a single active resumable session with a tested storage and buffer policy:

- preserve 500 MB of actually available device storage
- count physically allocated cache blocks
- reject sources that cannot safely fit
- require a contiguous protected region before playback
- retain interrupted playback for 60 minutes
- clear on timeout, full app/task exit, prior-process termination detected at next launch, source switch, or reboot

### Firestick

- Leanback launcher alias and banner: present
- touchscreen requirement: disabled
- home rows: movie genres, TV genres, favorites, settings
- embedded Live TV row/route/screen: removed

### Testing

Both debug flavors build. Deterministic coverage includes torrent buffer policy, torrent-source parsers, Torznab pagination metadata, Pirate Bay episode parsing, and model-specific OpenAI request construction. The opt-in `LlmSmokeTest` remains skipped by default but now performs a real candidate-bounded request and fails on API or validation errors when enabled. UI, Room migration, repository orchestration, and Android lifecycle behavior remain partially uncovered.

## Documentation Corrections Made

The July 2026 refresh corrected these stale claims:

- Room v2 -> Room v3
- one `Movie` entity -> two Room entities
- 16/17 preferences -> 26 DataStore keys
- inaccessible hard-coded `gpt-5` -> configurable `gpt-4.1` primary with compatible automatic fallbacks
- fixed/percentage torrent cache -> tested 500 MB reserve, source-fit admission, and 60-minute resumable session
- six integrations -> expanded movie/TV/provider integration set
- Plex described as complete -> implemented but unwired
- Live TV described as active -> removed
- test tasks mistaken for coverage -> current suites execute 32 deterministic tests per flavor, with substantial application gaps remaining
- old unresolved security/migration issues -> resolved history

## Remaining Documentation Caveats

- External service domains and response formats may change after this audit.
- Store listing and privacy text require review before public release in each jurisdiction.
- Line numbers are intentionally minimized because large Kotlin files change frequently.
- Historical Git commits may contain superseded statements.

## Verification Command

```bash
./gradlew \
  :app:assembleFirestickDebug \
  :app:assembleMobileDebug \
  :app:testFirestickDebugUnitTest \
  :app:testMobileDebugUnitTest
```

Result on 2026-07-25: `BUILD SUCCESSFUL`.

## Authority Order

1. Current code
2. `CURRENT_STATE.md`
3. Current topic documents
4. This audit
5. Historical commits and superseded documents

## Conclusion

The current documentation set now represents the July 2026 implementation. The primary remaining integrity risk is future code change without corresponding documentation updates, not a known mismatch in the refreshed core documents.
