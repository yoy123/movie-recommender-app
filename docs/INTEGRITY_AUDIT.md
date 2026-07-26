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
- model: `gpt-4.1-mini`
- open-mode temperatures: 0.6, 0.3
- bounded-mode temperatures: 0.4, 0.2
- TMDB fallback: active
- explicit AI consent: active

### Torrent cache

The cache is dynamic, not fixed at 500 MB:

- reserve 500 MB
- use 75% of remaining free space
- minimum 100 MB
- no explicit maximum

### Firestick

- Leanback launcher alias and banner: present
- touchscreen requirement: disabled
- home rows: movie genres, TV genres, favorites, settings
- embedded Live TV row/route/screen: removed

### Testing

Both debug flavors build. Both flavor unit-test tasks complete, but the only test is skipped, leaving zero executing automated tests.

## Documentation Corrections Made

The July 2026 refresh corrected these stale claims:

- Room v2 -> Room v3
- one `Movie` entity -> two Room entities
- 16/17 preferences -> 26 DataStore keys
- GPT-4o-mini -> `gpt-4.1-mini`
- fixed 500 MB torrent cache -> dynamic allowance
- six integrations -> expanded movie/TV/provider integration set
- Plex described as complete -> implemented but unwired
- Live TV described as active -> removed
- test tasks mistaken for coverage -> tests pass but execute zero test methods
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
