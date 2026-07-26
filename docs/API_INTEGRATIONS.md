# OpenStream+ API Integrations

**Last verified:** 2026-07-25  
**Status:** Current integration reference

## Integration Summary

| Integration | Purpose | Auth | Current status |
| --- | --- | --- | --- |
| TMDB | Movies, TV, genres, videos, recommendations, external IDs, watch providers | API key | Active |
| OpenAI | Personalized recommendation text/reranking | API key | Active, opt-in |
| IMDb scraper | Trailer fallback from IMDb pages | None | Active fallback |
| YTS | Movie torrent lookup | None | Active |
| Popcorn movie API | Movie torrent lookup | None | Active |
| Torznab | Configurable movie and TV torrent lookup through authorized Jackett/Prowlarr endpoints | API key | Optional |
| Internet Archive | Curated downloadable feature-film torrents | None | Active fallback |
| Public Domain Torrents | Public-domain movie torrents | None | Active fallback |
| Pirate Bay adapter | Movie torrent lookup | None | Active fallback |
| TorrentGalaxy adapter | Movie torrent lookup | None | Active fallback |
| 1337x adapter | Movie torrent lookup | None | Active fallback |
| Popcorn TV API | TV show/episode torrent metadata | None | Active |
| EZTV | TV episode torrent fallback | None | Active |
| Plex API service | Server test, libraries, refresh | Plex token | Implemented but unwired |

The previous embedded Live TV playlist and EPG integration was removed on 2026-07-25.

## 1. TMDB

### OpenAI Configuration

```properties
TMDB_API_KEY=...
```

Base URL is compiled as:

```text
https://api.themoviedb.org/3/
```

### Endpoints

Movies:

- genre list
- discover by genre
- search
- details with keywords
- similar titles
- recommendations
- videos
- external IDs
- watch providers

TV:

- genre list
- discover by genre
- search
- details
- similar shows
- recommendations
- videos
- external IDs
- watch providers

### HTTP behavior

`TmdbApiService.create(context)` configures:

- Retrofit with Gson
- OkHttp logging
- 10 MB HTTP cache in `cacheDir/tmdb_http_cache`
- `RateLimitInterceptor`

The interceptor handles HTTP 429 responses with up to three retries and exponential backoff. It uses `Retry-After` when present.

### Security

The client uses Android network security configuration. It does not install a trust-all certificate manager.

## 2. OpenAI

### Configuration

```properties
OPENAI_API_KEY=...
```

### Endpoint and model

```text
POST https://api.openai.com/v1/chat/completions
model: gpt-5
```

The request uses `max_completion_tokens`, medium verbosity, and GPT-5 reasoning effort instead of legacy sampling penalties.

### Consent

OpenAI calls are permitted only when `llm_consent_given` is true. The consent dialog explains that selected titles and recommendation preferences are sent to OpenAI.

If AI is disabled or consent is declined, the repository uses the TMDB-only ranking path.

### Retry behavior

Both recommendation modes use two attempts:

1. `minimal` reasoning effort
2. `low` reasoning effort plus strict retry instructions

The second attempt uses stricter instructions. Invalid or incomplete output returns control to the repository fallback path.

### Validation

Validation includes:

- expected recommendation count and format
- duplicate and exclusion checks
- candidate constraints in bounded mode
- genre constraints when enabled

## 3. IMDb Trailer Fallback

`ImdbScraperService` is active. It is reached after TMDB video lookup fails and an IMDb ID is available.

Movie and TV trailer lookup both prefer TMDB YouTube videos first.

Because IMDb HTML is not a stable API contract, scraper failure must remain non-fatal.

## 4. Movie Torrent Sources

`MovieRepository.getTorrentInfo()` uses this fallback order:

1. YTS
2. Popcorn movie API
3. Configured Torznab endpoints
4. Internet Archive Feature Films
5. Public Domain Torrents
6. Pirate Bay adapter
7. TorrentGalaxy adapter
8. 1337x adapter (`LeetxService`)

IMDb ID is resolved through TMDB where useful. Swarm API results must report at least one seed or peer. Internet Archive and Public Domain Torrents instead return verified HTTPS `.torrent` files; Archive-generated torrents include HTTP web seeds and neither catalog exposes live swarm counts.

### Selection objective

YTS and Popcorn generally prefer the smallest available file for faster startup. Internet Archive requires a close title match and rejects dark items. Public Domain Torrents requires an exact normalized title, cross-checks IMDb IDs when available, and prefers its largest MP4 variant for player compatibility.

### Reliability

These are unofficial third-party services. Hostnames, response formats, anti-bot behavior, and availability may change without notice.

### Torznab configuration

Torznab support is optional and accepts multiple semicolon-separated endpoints in `local.properties`:

```properties
TORZNAB_SOURCES=Jackett|https://indexer.example/api/v2.0/indexers/all/results/torznab/|api_key;Backup|https://backup.example/torznab/api|api_key
```

Each entry uses `Name|HTTPS endpoint|API key`. The app queries configured endpoints concurrently and chooses a live result using title, year, quality, and seed count. Use only endpoints and content sources you are authorized to access.

## 5. TV Torrent Sources

### Popcorn TV

Provides:

- show search
- show details
- seasons
- episodes
- episode torrent variants

### EZTV

Uses IMDb ID and provides:

- paginated show torrent lookup
- season/episode discovery
- quality detection
- first-episode and specific-episode selection

The full inventory is paginated up to a 2,000-release safety limit and cached for five minutes.

### Pirate Bay TV

Uses the existing Pirate Bay API adapter's TV and HD TV categories. Strict `SxxEyy` and `NxYY` parsing excludes season packs from the episode picker.

### Repository behavior

For a specific episode, Popcorn TV, EZTV, configured Torznab endpoints, and Pirate Bay TV are queried concurrently when possible. The result with the highest seed count is selected. All four sources participate in S01E01 quick-play fallback.

Season and episode lists merge all available sources, with Popcorn metadata preferred when several sources describe the same episode. Torznab follows advertised result offsets across every configured endpoint, up to a 2,000-release safety limit per endpoint.

## 6. Watch Providers and Deep Links

TMDB watch-provider responses are converted into `WatchOption` records grouped as:

- free
- subscription
- ad-supported
- rent
- buy
- torrent, for movies only

`StreamingAppRegistry` contains provider-ID-to-package mappings and generic link builders.

`ProviderContentResolverRegistry` creates exact provider content links from imported IDs or URLs. Exact links are stored in Room so future watch-option requests can prefer them over generic search links.

Launching external applications remains best effort because Fire OS package names and intent handling differ by provider and device version.

## 7. Plex

`PlexApiService` implements:

- server connection test
- library enumeration
- library refresh/scan requests

`SettingsRepository` stores Plex server URL, token, library paths, library IDs, and enabled state.

No current screen, ViewModel method, or repository flow calls `PlexApiService`. It should be considered dormant infrastructure, not a completed feature.

Plex is still supported as a mapped external watch provider.

## Removed Integration: Embedded Live TV

The Firestick Live TV screen previously fetched:

- an M3U playlist
- an XMLTV EPG

from a third-party domain. The row, screen, parser, playback route, and HLS-only dependency were removed on 2026-07-25.

No replacement playlist was added. Legitimate services such as Plex, Pluto TV, Sling Freestream, and Tubi should be accessed through their official applications rather than reverse-engineered or proxied feeds.

## Error Handling Rules

- Integration failures should return `null`, empty results, or `Resource.Error` without crashing the app.
- One torrent provider failure must not stop later providers in the chain.
- Trailer scraping failure must not block recommendations.
- OpenAI failure must fall back to TMDB.
- Exact provider-link resolution failure must fall back to generic provider links where available.

## Active Risks

1. Third-party torrent service contracts are unofficial and fragile.
2. API keys compiled into the APK are extractable.
3. OpenAI has no dedicated rate-limit interceptor equivalent to TMDB's.
4. IMDb scraping can break when page structure changes.
5. Provider deep links vary across Fire OS versions and installed app variants.
6. Plex code is unwired and untested through the UI.
