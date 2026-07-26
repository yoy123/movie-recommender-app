# OpenStream+ Media Playback

**Last verified:** 2026-07-25

## Playback Paths

OpenStream+ currently supports:

1. Trailer playback from TMDB YouTube results or IMDb fallback URLs.
2. Torrent-backed movie and TV-episode playback through `TorrentStreamService` and Media3 ExoPlayer.
3. External streaming-provider application launch through watch options.

The former embedded Live TV HLS player was removed on 2026-07-25.

## Trailer Playback

Repository lookup order:

1. Search TMDB for the movie or TV show.
2. Request the TMDB videos endpoint.
3. Prefer a YouTube trailer.
4. Fall back to teaser/opening-credit/other YouTube video where applicable.
5. Resolve IMDb ID and try `ImdbScraperService`.

Trailer routes encode URL data before passing it through Compose Navigation.

## Torrent Discovery to Playback

```text
Title/episode
   -> MovieRepository torrent lookup
   -> TorrentInfo / EpisodeTorrentInfo
   -> magnet URL
   -> StreamingPlayerScreen
   -> TorrentStreamService
   -> selected local video file
   -> Media3 ExoPlayer
```

## `TorrentStreamService`

The service is declared as:

- unexported
- foreground service
- `mediaPlayback` service type

Responsibilities:

- initialize TorrentStream
- manage one active torrent stream
- expose stream status to the UI
- select the most appropriate video file
- estimate bitrate and buffer requirements
- prioritize pieces around seek positions
- pause/resume downloads where needed
- clear cache on request

## Adaptive Prebuffering

The service no longer relies on one fixed prebuffer byte count. It estimates startup requirements using available information such as:

- file size
- known or estimated duration
- observed download speed
- peak bitrate estimate
- requested playback/seek position

The goal is to reduce startup delay on fast torrents while avoiding immediate rebuffering on slow torrents.

## Media3 Player

Both flavors use Media3 ExoPlayer for torrent-backed playback.

Player screens configure buffering controls and keep the screen awake during playback.

The Firestick player is designed for remote input. The mobile player uses touch controls.

## Cache Management

The torrent cache allowance is calculated when TorrentStream initializes:

- reserve 500 MB of device space
- calculate 75% of the remaining free space
- enforce a minimum of 100 MB
- no explicit maximum cap

This is not the fixed 500 MB policy described in older documentation.

`TorrentStreamService.getClearCacheIntent()` is called when relevant activities are destroyed. This reduces retained data but does not guarantee immediate cleanup after a process crash or forced termination.

## Seeking

When the player seeks, the service estimates the corresponding byte offset and can reprioritize pieces around the target position.

This calculation depends on a known playback duration. Before duration is available, seeking behavior is less precise.

## Watch Options

Movie watch options can include:

- free streaming providers
- subscription providers
- ad-supported providers
- rent
- buy
- torrent

TV show watch options include provider availability. Torrent playback is selected at the episode level through the episode picker.

External provider launch uses a series of best-effort intent strategies because Fire TV applications differ in supported deep links and launcher categories.

## Removed Embedded Live TV

Removed components:

- Firestick `LiveTvScreen`
- M3U playlist parser/fetcher
- XMLTV EPG parser/fetcher
- `live_tv` Compose route
- Leanback Live TV row
- HLS-only Media3 dependency

The removal avoids coupling the application to an unstable third-party channel feed. Official FAST/live-TV services can still be opened through their own installed applications where mapped as providers.

## Error Handling

Expected non-fatal failures include:

- no torrent match
- zero live peers
- source timeout or schema change
- torrent metadata failure
- unsupported media container
- local file unavailable
- player decode failure
- external provider application missing
- trailer URL unavailable

The UI should surface an actionable message and allow navigation back without crashing.

## Known Limitations

- No subtitle-selection system is documented as complete.
- No picture-in-picture implementation is present.
- No playback-speed control is exposed.
- Cache cleanup after process death is not guaranteed.
- Torrent source quality and legality vary by result and jurisdiction.
- Media3 versions are pinned at 1.2.1 and should be evaluated before future platform-target upgrades.

## Manual Playback Checklist

1. Start a movie torrent with live peers.
2. Verify foreground notification appears.
3. Verify playback starts after prebuffering.
4. Seek forward and backward.
5. Pause/resume.
6. Background and return to the app.
7. Exit playback and verify cache cleanup request.
8. Repeat with a TV episode.
9. Test a missing/dead torrent and confirm graceful failure.
10. Confirm Fire TV screensaver does not activate during playback.
