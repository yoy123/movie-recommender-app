# Design Features 2 — Implementation Audit (2026-10-11; reports added 2026-10-12)

This is the implementation tracker for the user's non-exhaustive Design Features 2 checklist. It describes the code that exists now; it is not a claim of successful end-to-end device playback or a live OpenAI account test.

| Item | Current result | Evidence / remaining work |
| --- | --- | --- |
| 1. Project audit before edits | Reviewed current docs, repository, ViewModels, torrent source/ranking/playback policy, OpenAI request code, and both flavor favorites screens. Fixed identified stale Room row-overwrite paths. | Large repository/duplicated flavor ViewModels, unofficial providers, client-side API keys, untested UI and lifecycle transitions remain in `KNOWN_ISSUES.md`. |
| 2. Storage-aware torrent | Already in place: reads safe device free-space budget, reserves 500 MB, prefers complete sources that fit, and rejects unsafe source metadata at playback. | Rolling chunk-only download is **not implemented**. The library cannot yet demonstrate safe old-piece eviction / rewind / resume. No automatic partial-download fallback was added. |
| 3. Quality vs swarm speed | Already in place: ranks sources using fit, quality, seeders, and peers, keeping alternatives for failover. | Real torrent speeds and stability still require device measurement. |
| 4. Check recommendations | Reviewed candidate constraint / validation, AI-first orchestration, preference toggles, and fallback alert paths. Movie repository now avoids overwriting saved favorite flags. | Recommend adding controlled integration tests for TMDB and OpenAI response failures and output validation. |
| 5. OpenAI model | Switched default to `gpt-6-astra` with `gpt-6.1-sol`, `gpt-6-luna`, GPT-4.1 fallbacks. Explicit GPT-6 Chat Completions reasoning support, token limit, and JVM request tests added. `OPENAI_MODEL` in `local.properties` still overrides default. | Requires live API entitlement/latency/cost validation; normal unit tests cannot prove live recommendation quality. |
| 6. Exclude favorites | Movie favorites already excluded by repository by TMDB ID/title in both AI and fallback. Added TV favorites persistence via DataStore and TV recommendation exclusions. | TV exclusions are by normalized title rather than stable TMDB ID; TV favorites DataStore migration/performance tests should be added. |
| 7. AI primary / TMDB fallback alert | Existing: AI-first request with automatic TMDB fallback, explicit debug dialog and reason. No recommendation-style choice. | Verify both flavor dialogs under live failure cases. |
| 8. Favorites actions | Movie and TV cards now open an action dialog on mobile and Fire TV with Watch Options and Delete from Favorites. Separate icons retain selection for recommendations. Existing watch-provider/torrent movie player navigation reused. | Favorites TV watch dialog lists streaming providers; direct TV torrent episode-picker launch remains on the selection screen. Test Fire TV DPAD focus and content selection on a device. |

## Open Firestick issues and requirements — reported October 12, 2026

Items 9-11 have implementations; the native Favorites layout was built, installed and visually verified on Firestick, with user confirmation. Full menu/navigation acceptance checks remain. Item 12 remains OPEN.

| Feature | User report | Required verification |
| --- | --- | --- |
| Always-on OpenAI | User confirmed AI actually worked after enabling the old Settings switch. The application-side gate caused the fallback; API credentials were not proven bad. | Removed Settings switch, legacy consent gate and dialog; movies/TV always request OpenAI; TMDB is fallback for real failures. Both flavor tests passed; verify updated APK on Firestick. |
| Favorite selection | User requires the card action menu to select a favorite for recommendations, remove a previously selected favorite, and clear all selections without deleting favorites. | **Implemented in Firestick source:** menu shows Select for Recommendations or Remove from Recommendations, plus Watch Options and Delete. Clear Selections action clears movie/TV picks through ViewModel. Build passed; DPAD/device verification pending. |
| Favorites layout | Favorites must match the front-menu native Leanback poster rails, as pictured in user screenshots. | **Installed and visually verified on Firestick:** native BrowseSupportFragment/ListRow screen uses PickerMediaCardPresenter, FocusBorderImageCardView, poster dimensions and metadata. The 2026-10-12 live screenshot showed horizontal Actions and Selected for Recommendations rails; the user confirmed visual improvement. The prior InflateException no longer occurred. Full DPAD/menu/search checks remain pending. Mobile still uses its original Compose grid. |
| TMDB relevance | Completely different input selections returned exactly the same fallback recommendations. | Check selected IDs/mode propagation, TMDB candidate discovery, cache/state, similarity scoring and exclusions; add distinct-seed A/B tests and device verification. |

Items 10 and 11 require on-device UI/focus validation; item 12 (TMDB candidate sensitivity) remains open.

## Build verification

After these changes run:

```bash
./gradlew :app:assembleMobileDebug :app:assembleFirestickDebug :app:testMobileDebugUnitTest :app:testFirestickDebugUnitTest --no-daemon
```

A successful build verifies compilation and deterministic JVM tests, but not device-specific DPAD focus, low-storage playback behavior, live provider lookups, or live OpenAI response quality.
