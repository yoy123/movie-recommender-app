# OpenStream+ Documentation

**Last updated:** 2026-07-25

## Start Here

1. [CURRENT_STATE.md](CURRENT_STATE.md) — current verified product and engineering state
2. [ARCHITECTURE.md](ARCHITECTURE.md) — module boundaries and control flow
3. [FEATURES.md](FEATURES.md) — current user-facing behavior
4. [KNOWN_ISSUES.md](KNOWN_ISSUES.md) — active risks and resolved history
5. [TESTING.md](TESTING.md) — actual automated and manual test state

## Technical References

- [API_INTEGRATIONS.md](API_INTEGRATIONS.md) — active network and provider integrations
- [RECOMMENDATION_ENGINE.md](RECOMMENDATION_ENGINE.md) — AI and TMDB recommendation paths
- [DATA_STORAGE.md](DATA_STORAGE.md) — Room v3, DataStore, and cache behavior
- [CONFIG_REGISTRY.md](CONFIG_REGISTRY.md) — build-time and runtime configuration
- [MEDIA_PLAYBACK.md](MEDIA_PLAYBACK.md) — torrent service and Media3 playback
- [TV_SUPPORT.md](TV_SUPPORT.md) — Firestick launcher, Leanback, Compose, and DPAD rules
- [WORKFLOW.md](WORKFLOW.md) — user journeys and navigation
- [DEPLOYMENT.md](DEPLOYMENT.md) — build and release process
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — diagnostic procedures

## Documentation Authority

When documents disagree, use this order:

1. Current Kotlin, Gradle, and manifest code
2. [CURRENT_STATE.md](CURRENT_STATE.md)
3. Topic-specific documents
4. Historical audit notes and Git history

## Important Current Facts

- Product flavors: `mobile` and `firestick`
- Room database: version 3
- OpenAI model: `gpt-4.1-mini`
- AI recommendations: opt-in; TMDB-only mode remains available
- Torrent cache: one active source, 500 MB device reserve, contiguous no-stall gate, and 60-minute resume retention
- Embedded Live TV: removed on 2026-07-25
- Automated tests: 14 executed successfully per flavor; `LlmSmokeTest` remains skipped

## Maintenance Rule

Any behavior change should update at least:

- [CURRENT_STATE.md](CURRENT_STATE.md)
- the relevant topic document
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) when risk or debt changes

Line-number references should be avoided unless they are regenerated during the same change, because large Kotlin files move frequently.
