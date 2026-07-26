# OpenStream+ User Workflows

**Last verified:** 2026-07-25

## 1. First Launch

1. App initializes Room, DataStore, and `MovieRepository`.
2. Background orphan cleanup runs when due.
3. User name and appearance settings are loaded.
4. User reaches genre/content selection.

## 2. Movie Recommendation Flow

1. Select movie mode.
2. Select a genre or favorites.
3. Browse/search movies.
4. Select 1–5 movies.
5. Choose recommendation preferences.
6. Generate recommendations.
7. If AI has never been addressed, show the consent dialog.
8. Use AI mode when consented/enabled; otherwise use TMDB-only mode.
9. Display analysis and 15 recommendation entries.
10. User may retry, favorite, open a trailer, open watch options, or start over.

## 3. TV Recommendation Flow

1. Select TV mode.
2. Select a TV genre.
3. Browse/search TV shows.
4. Select 1–5 shows.
5. Generate content-mode-aware recommendations.
6. Open trailers or provider options from a result.
7. Choose Browse Episodes for torrent playback.

## 4. Favorites Flow

1. Open Favorites.
2. Select one or more persisted favorite movies.
3. Generate recommendations using favorites as inputs.
4. Favorites routing uses pseudo-genre ID `-1`.

Favorites remain stored when selection and recommendation flags are cleared.

## 5. Recommendation Decision Flow

```text
Generate
   |
AI consent given and AI enabled?
   ├─ yes -> collect TMDB candidates -> OpenAI -> validate
   │                                      |
   │                               invalid/failure
   │                                      v
   └─ no --------------------------> TMDB local ranking
                                          |
                                      display results
```

Retry excludes titles already returned during the current session.

## 6. Trailer Flow

1. Resolve title and year through the correct TMDB movie/TV endpoint.
2. Request videos.
3. Prefer YouTube trailer.
4. Fall back to IMDb scraping when possible.
5. Encode the URL for navigation.
6. Open `TrailerScreen`.

## 7. Movie Watch Flow

1. User opens watch options.
2. Repository concurrently requests TMDB providers and movie torrent info.
3. Provider options are sorted by access type.
4. Exact stored provider links override generic links.
5. User selects:
   - external streaming provider, or
   - torrent playback.
6. External provider launch tries multiple intent strategies.
7. Torrent selection opens `StreamingPlayerScreen`.

## 8. TV Episode Flow

1. User selects Browse Episodes.
2. Resolve IMDb ID through Popcorn TV or TMDB external IDs.
3. Merge available seasons from Popcorn TV and EZTV.
4. Merge episodes for the selected season.
5. User selects an episode.
6. Query Popcorn TV and EZTV concurrently.
7. Select the result with the highest seed count.
8. Start torrent playback.

## 9. Torrent Playback Flow

1. Pass magnet URL to `StreamingPlayerScreen`.
2. Bind/start `TorrentStreamService`.
3. Parse torrent metadata.
4. Select the video file.
5. Calculate adaptive prebuffer target.
6. Begin Media3 playback from the local file.
7. Reprioritize pieces after seeks.
8. Keep the screen awake.
9. Request cache cleanup when leaving playback.

## 10. Firestick Navigation Flow

### Home browse rows

1. Movie Genres
2. TV Show Genres
3. Favorites
4. Settings

The Live TV row was removed on 2026-07-25.

### Genre picker

- DPAD moves between media cards and action cards.
- Generate Recommendations requires at least one selection.
- Current selections can be cleared.

### Nested card actions

- Center on card enters action mode.
- Left/right changes action.
- Center triggers action.
- Back returns to card-level focus.
- The activation `KeyUp` must be consumed after focus moves on `KeyDown`.

## 11. Settings Flow

Current user-facing settings include AI recommendation enablement and recommendation preference values/toggles.

Plex settings exist in DataStore but are not connected to a completed UI workflow.

## 12. Error and Empty States

Expected recoverable states:

- TMDB request failure
- OpenAI failure or invalid output
- no titles found
- no trailer found
- no watch providers
- external provider app unavailable
- no torrent with live peers
- torrent metadata/download failure
- playback decode failure

The app should preserve back navigation and offer retry where practical.

## Removed Workflow: Embedded Live TV

The Firestick M3U/XMLTV guide and direct HLS playback flow were removed. There is no replacement embedded channel source. Users should use official live-TV applications installed on Fire TV.
