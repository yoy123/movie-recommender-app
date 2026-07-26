# OpenStream+ Features

**Last verified:** 2026-07-25

## Feature Matrix

| Feature | Mobile | Firestick | Notes |
|---|---:|---:|---|
| Movie genres and search | Yes | Yes | TMDB |
| TV genres and search | Yes | Yes | TMDB |
| Select 1–5 titles | Yes | Yes | Recommendation input |
| Favorites | Yes | Yes | Movies persisted in Room |
| AI recommendations | Yes | Yes | Opt-in OpenAI path |
| TMDB-only recommendations | Yes | Yes | No OpenAI data transfer |
| Preference controls | Yes | Yes | Six preference groups |
| Movie trailers | Yes | Yes | TMDB, IMDb fallback |
| TV trailers | Yes | Yes | TMDB, IMDb fallback |
| Watch-provider options | Yes | Yes | TMDB availability |
| External streaming-app launch | Yes | Yes | Best-effort intents |
| Movie torrent playback | Yes | Yes | Multi-source fallback |
| TV episode picker | Yes | Yes | Popcorn TV + EZTV |
| TV episode torrent playback | Yes | Yes | Per-episode |
| Exact provider-link crosswalk | Yes | Yes | Room v3 |
| Fire TV Leanback home | No | Yes | DPAD browse shell |
| Embedded Live TV guide | No | No | Removed 2026-07-25 |
| Plex server workflow | No | No | Service/settings exist but unwired |

## Onboarding and Appearance

The app stores a sanitized user name, first-run state, and dark-mode setting in DataStore.

The name is used to personalize labels such as the favorites collection. The internal routing convention for favorites remains genre ID `-1`; it is not a stored TMDB genre.

## Movie and TV Browsing

Users can:

- select movie or TV content mode
- browse TMDB genres
- load paginated content
- search titles
- select up to five titles
- clear current selections

Firestick uses a Leanback browse screen and picker. Mobile uses Compose screens.

## Recommendations

### AI mode

AI mode sends selected titles and active preference values to OpenAI after explicit consent.

The service produces personalized analysis plus 15 recommendations. Output is validated and deduplicated.

### TMDB-only mode

TMDB-only mode ranks TMDB candidate titles locally using active preferences. It is used when:

- AI is disabled
- consent is declined
- the OpenAI request fails
- output validation fails

### Preferences

Each preference has a value and enable toggle:

- indie
- popularity
- release-year range
- tone
- international
- experimental

## Favorites

Movie favorites are persisted in Room through the `isFavorite` flag.

Favorites can be selected as recommendation inputs. The favorites route uses pseudo-genre ID `-1`.

TV-show favorites are not represented by a dedicated persisted TV entity in the current schema.

## Trailers

Trailer lookup is content-mode aware.

Preferred path:

1. TMDB search
2. TMDB videos
3. YouTube trailer/teaser
4. IMDb scraping fallback

## Watch Options

Watch options combine:

- TMDB provider availability
- application/package mappings
- generic or exact provider links
- movie torrent option where available

Options are ordered by access type: free, subscription, ad-supported, rent, buy, torrent.

## Provider Crosswalks

Users or import workflows can associate a TMDB title/provider pair with an exact provider content ID or URL.

Resolved values are persisted and preferred over generic search links on future watch-option requests.

## Movie Torrent Streaming

Movie torrent search tries:

1. YTS
2. Popcorn movie API
3. Pirate Bay adapter
4. TorrentGalaxy
5. 1337x adapter

Only results reporting live peers are accepted.

## TV Episode Browsing

TV recommendation and selection cards open an episode picker instead of blindly starting S01E01.

The picker:

- resolves an IMDb ID
- merges seasons from Popcorn TV and EZTV
- merges episode lists
- searches the selected episode in both sources
- selects the highest-seed result

## Playback

Media3 ExoPlayer renders the local file managed by `TorrentStreamService`.

Playback includes:

- adaptive prebuffering
- seek-piece prioritization
- foreground-service notification
- screen-awake behavior
- cache cleanup request on exit

## Firestick Experience

The Firestick flavor provides:

- Leanback launcher/banner
- movie genres
- TV genres
- favorites shortcut
- settings shortcut
- DPAD focus states
- nested card-action navigation

The embedded Live TV row and guide were removed on 2026-07-25.

## Not Implemented or Incomplete

- active automated test suite
- offline recommendation generation
- encrypted Plex-token storage
- completed Plex server UI/workflow
- multi-profile support
- localization
- analytics/telemetry
- picture-in-picture
- subtitle-management UI
- playback-speed control
