# OpenStream+ Architecture

**Last verified:** 2026-07-25  
**Status:** Current architecture reference

## Overview

OpenStream+ is a single-module Android application with two product flavors over a shared data and playback layer.

```text
┌──────────────────────────────┐     ┌──────────────────────────────┐
│ Mobile flavor               │     │ Firestick flavor            │
│ Compose touch UI            │     │ Leanback + Compose DPAD UI  │
│ Mobile MovieViewModel       │     │ Firestick MovieViewModel    │
└──────────────┬───────────────┘     └──────────────┬───────────────┘
               └──────────────────┬─────────────────┘
                                  │
                         ┌────────▼────────┐
                         │ MovieRepository │
                         └────────┬────────┘
          ┌───────────────────────┼────────────────────────┐
          │                       │                        │
   ┌──────▼──────┐        ┌───────▼────────┐      ┌────────▼────────┐
   │ Network/API │        │ Persistence    │      │ Playback        │
   │ integrations│        │ Room/DataStore │      │ Torrent service │
   └─────────────┘        └────────────────┘      └─────────────────┘
```

## Source Sets

### Shared: `app/src/main/`

Responsibilities:

- data models
- Room database and DAOs
- DataStore settings
- TMDB and OpenAI access
- trailer lookup
- watch-provider mapping and exact provider crosswalks
- movie and TV torrent discovery
- repository business logic
- torrent streaming foreground service

Key files:

- `MovieRecommenderApplication.kt`
- `data/repository/MovieRepository.kt`
- `data/remote/TmdbApiService.kt`
- `data/remote/LlmRecommendationService.kt`
- `data/local/AppDatabase.kt`
- `data/settings/SettingsRepository.kt`
- `torrent/TorrentStreamService.kt`

### Mobile: `app/src/mobile/`

Responsibilities:

- touch-oriented Compose screens
- Compose Navigation routes
- mobile `MovieViewModel`
- mobile theme and dialogs

The mobile entry point is `com.movierecommender.app.MainActivity`.

### Firestick: `app/src/firestick/`

Responsibilities:

- Leanback browse shell
- DPAD selection picker
- Compose sub-screens
- Firestick `MovieViewModel`
- Fire TV focus rules and visual focus indicators
- Leanback launcher manifest overlay

The Firestick launcher activity is `com.movierecommender.app.firestick.MainActivity`. The launcher is exported through an activity alias with `LEANBACK_LAUNCHER`.

The embedded Live TV screen and its route were removed on 2026-07-25.

## Application Startup

`MovieRecommenderApplication` creates lazy application-scoped dependencies:

```text
AppDatabase
SettingsRepository
MovieRepository
```

`MovieRepository` receives:

- `MovieDao`
- `ProviderContentCrosswalkDao`
- `TmdbApiService`

Its remaining services use constructor defaults.

Startup also launches periodic database cleanup in an application-scoped IO coroutine.

## UI State Flow

Each flavor owns a separate `MovieViewModel` and `MovieUiState` implementation.

```text
Screen action
   -> MovieViewModel method
   -> MovieRepository suspend function / Flow<Resource<T>>
   -> ViewModel copies new state into StateFlow
   -> Compose recomposes
```

Repository network operations generally emit:

1. `Resource.Loading`
2. `Resource.Success`
3. `Resource.Error`

## Content Modes

`ContentMode` distinguishes:

- `MOVIES`
- `TV_SHOWS`

The selected content mode controls:

- TMDB endpoints
- movie or TV candidate models
- trailer lookup path
- watch-provider endpoint
- torrent and episode behavior
- recommendation generation path

TV shows are not stored in the `movies` Room table as a second entity. TV selection state is currently held in the ViewModel and transferred between some Firestick activities through JSON extras.

## Recommendation Flow

```text
1-5 selected titles
    |
TMDB candidate collection + exclusions + Bayesian ranking
    |
AI data sharing allowed and at least 15 safe candidates?
    ├─ yes -> bounded OpenAI candidate rerank -> strict validation
    └─ no  -> deterministic TMDB fallback
    |
typed RecommendationResult -> flavor ViewModel -> result/fallback dialog
```

Production code has no open-generation branch and no recommendation-engine selector. The consent state controls whether recommendation data may be sent externally; it is not a product-mode choice.

The current OpenAI model is `gpt-5` through `/v1/chat/completions`, using bounded reasoning effort and `max_completion_tokens`.

AI failure falls back to TMDB-based recommendation text.

## Watch Options

For movies, `getMovieWatchOptions()` fetches in parallel:

- TMDB watch providers
- the movie torrent fallback chain

For TV shows, `getTvShowWatchOptions()` returns TMDB provider options. Episode torrent selection is handled separately.

`StreamingAppRegistry` maps provider IDs to Android packages and generic links. `ProviderContentResolverRegistry` converts imported provider IDs or URLs into exact canonical/app links. Exact mappings are persisted in `provider_content_crosswalk`.

## Torrent Discovery

### Movies

YTS, Popcorn, Torrentio, and Knaben form the primary concurrent search group. `MovieRepository` compares quality and swarm health across the completed results rather than returning the first adequate provider response.

When no primary result is usable, fallback continues through configured Torznab endpoints, Internet Archive, Public Domain Torrents, Pirate Bay, TorrentGalaxy, and 1337x. Swarm API results must report at least one seed or peer. The two public-domain catalogs return verified HTTPS `.torrent` files without swarm-count metadata.

### TV episodes

Popcorn TV, EZTV, Torrentio, Knaben, configured Torznab endpoints, and Pirate Bay TV are queried concurrently when possible. The repository ranks preferred quality, seeders, and peers. Season and episode lists merge the inventory-capable sources while preserving Popcorn metadata.

Torrentio may identify an exact file within a multi-file torrent. That `so` selection is preserved in the magnet and applied through `Torrent.setSelectedFileIndex()` during `onStreamPrepared`, before the download begins.

## Playback

`TorrentStreamService` is an unexported foreground service with `mediaPlayback` type.

Responsibilities:

- initialize TorrentStream
- select a video file
- monitor download speed and progress
- calculate adaptive prebuffer thresholds
- prioritize pieces around seek positions
- provide a local file path for Media3 ExoPlayer
- clean torrent cache on request

Flavor-specific `StreamingPlayerScreen` implementations own Media3 player UI.

## Persistence

### Room version 3

Entities:

- `Movie`
- `ProviderContentCrosswalk`

DAOs:

- `MovieDao`
- `ProviderContentCrosswalkDao`

Migrations:

- `MIGRATION_1_2`
- `MIGRATION_2_3`

No destructive migration fallback is configured.

### DataStore

`SettingsRepository` exposes Flow-backed preferences for onboarding, appearance, AI consent, recommendation controls, cleanup timing, and Plex configuration.

## Firestick Focus Contract

Firestick screens must remain usable without touch input.

Important behavior:

- focused elements require an obvious visual state
- cards with nested actions use a two-level focus model
- pressing DPAD center to enter a card must not trigger the newly focused child
- when focus moves on `KeyDown`, the corresponding `KeyUp` must also be consumed
- back exits nested card focus before leaving the screen

## Security Boundaries

- API keys originate in untracked `local.properties`.
- Keys are compiled into `BuildConfig` and are extractable from an APK.
- Release traffic uses normal platform TLS validation.
- OpenAI use requires explicit consent.
- Provider/torrent services must be treated as untrusted network inputs.

## Architectural Debt

- `MovieRepository` is over 2,500 lines and combines recommendation, provider, trailer, persistence, and torrent responsibilities.
- Mobile and Firestick ViewModels contain substantial duplicated logic.
- Plex service/settings are not connected to a current user flow.
- TV selection persistence differs from movie persistence.
- External torrent integrations do not share a formal provider interface.
