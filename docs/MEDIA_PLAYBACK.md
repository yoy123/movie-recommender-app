# OpenStream+ Media Playback

**Last verified:** 2026-07-25

## Playback Paths

OpenStream+ supports:

1. Trailer playback from TMDB YouTube results or IMDb fallback URLs.
2. Torrent-backed movie and TV-episode playback through `TorrentStreamService` and Media3 ExoPlayer.
3. External streaming-provider application launch through watch options.

The embedded Firestick Live TV/HLS feature was removed on 2026-07-25.

## Torrent Discovery to Playback

```text
Title or episode
   -> MovieRepository torrent lookup
   -> TorrentInfo / EpisodeTorrentInfo
   -> magnet URL
   -> flavor-specific StreamingPlayerScreen
   -> TorrentStreamService
   -> contiguous no-stall startup gate
   -> local torrent video file
   -> Media3 ExoPlayer
```

## Torrent Service

`TorrentStreamService` is an unexported foreground service with the `mediaPlayback` service type. It owns one active torrent session and is responsible for:

- initializing TorrentStream-Android
- reusing cached pieces for the same magnet
- tracking download progress, speed, peers, and allocated disk usage
- reading or accepting the media duration
- prioritizing the first missing piece ahead of the playback position
- enforcing the storage reserve
- retaining an interrupted session for 60 minutes
- clearing the cache on timeout, app exit/task removal, process restart, or device reboot

The service now calls the library's real `Torrent.pause()` and `Torrent.resume()` methods. The previous reflective lookup for nonexistent `pauseStream`/`resumeStream` methods was removed.

## No-Stall Startup Gate

Playback no longer begins after a fixed byte count or arbitrary three-minute startup deadline.

Before exposing the file to Media3, the service waits for a contiguous region beginning at the requested start or resume position. The required region is calculated from:

- total video size
- known or extracted duration
- resume position
- a conservative lower-quartile download speed
- a 35% speed safety discount
- a 1.5x peak-bitrate allowance
- ten minutes of protected peak-rate headroom
- the projected download deficit over the remaining runtime

The target can increase when the swarm slows down, but a temporary speed spike cannot reduce it. On a sufficiently slow torrent, the gate may require the entire remaining video before starting. This trades startup delay for a materially lower chance of buffering during playback.

If no download progress occurs for three minutes, the service stops waiting and returns an actionable source error rather than starting a stream that is expected to stall.

No implementation can guarantee uninterrupted playback if the network fails, every peer disappears, the device loses power, or the media file is malformed. Those remain external failure conditions.

## Piece Prioritization

The player reports position and duration to the torrent service. Every two seconds the service:

1. converts the playback position into an approximate byte offset
2. scans forward through available pieces
3. finds the first missing piece in a fifteen-minute lookahead window
4. raises the priority of that missing piece and the following pieces

This avoids prioritizing an arbitrary point while leaving holes between the player and the requested data.

## Media3 Buffering

Both flavors use Media3 ExoPlayer with:

- minimum buffer: 60 seconds
- maximum read-ahead: 300 seconds
- startup requirement: 5 seconds
- post-rebuffer requirement: 30 seconds

Media3 buffering is a secondary local-file buffer. The torrent service's contiguous startup gate remains the primary protection against mid-playback stalls.

## Cache Capacity

The active source must fit while preserving at least 500 MB of actually available device storage.

The safe total session allowance is:

```text
physically allocated current cache
+ max(available device storage - 500 MB, 0)
```

Allocated filesystem blocks are measured instead of relying only on a sparse file's logical length. Before playback starts, the service compares the selected video's full size with the safe allowance. An oversized source is rejected with a storage message instead of starting and later filling the Firestick.

The cache is not trimmed behind the player because the torrent library exposes a single selected video file rather than independently deletable internal video chunks. The one-source-at-a-time policy and preflight storage check make that behavior explicit.

## Accidental Backout and Resume

Leaving the player does not stop the torrent or delete its data.

The player saves:

- current playback position
- known duration
- active magnet identity
- cache-retention deadline

The foreground torrent service continues downloading. Returning to the same source within 60 minutes reuses the existing torrent session and local pieces and resumes at the saved position.

Starting a different torrent clears the previous session first.

## Cache Deletion Rules

The current torrent cache and its saved playback position are removed when any of these conditions occurs:

1. The user has not returned to playback for 60 minutes.
2. The root application task is deliberately closed or removed.
3. The previous application process ended and the app is launched again.
4. The Firestick/device restarts.
5. A different torrent is selected.
6. An explicit clear-cache action is issued.

A persisted alarm handles the 60-minute window even while the player is closed. A boot receiver clears retained data after device restart. Application startup also checks boot count, expiry, and an unfinished previous-process marker as recovery safeguards.

Activity recreation, configuration changes, normal backgrounding, and accidental navigation out of the player do not immediately delete the session.

## Trailer Playback

Trailer lookup order:

1. Search TMDB for the movie or TV show.
2. Request the TMDB videos endpoint.
3. Prefer a YouTube trailer.
4. Fall back to another suitable YouTube result.
5. Resolve IMDb ID and try `ImdbScraperService`.

## Watch Options

Movie watch options can include free, subscription, ad-supported, rent, buy, and torrent choices. TV provider availability is shown at the series level; torrent playback is selected at the episode level.

External provider launch remains best-effort because Fire TV applications differ in supported packages, launcher categories, and deep-link formats.

## Known Limitations

- Network or peer loss can still interrupt an active stream.
- A slow source may require a long initial wait or the entire remaining file.
- Resume precision depends on reliable duration and container metadata.
- No completed subtitle-selection, picture-in-picture, or playback-speed system is documented.
- Media3 remains pinned at 1.2.1.
- Torrent availability and lawful use depend on the source and jurisdiction.

## Manual Playback Checklist

1. Start a source with live peers and verify the foreground notification.
2. Verify playback remains in the prebuffer screen until the contiguous gate is satisfied.
3. Play through a high-bitrate section without mid-playback buffering.
4. Seek forward and verify missing pieces are prioritized before recovery.
5. Back out of the player and immediately return; verify position and cache reuse.
6. Return within 60 minutes and verify resume.
7. Leave the player unused for more than 60 minutes and verify cache/position deletion.
8. Close/remove the application task and verify deletion.
9. Restart the Firestick and verify deletion.
10. Select a source larger than the safe allowance and verify it is rejected before playback.
11. Repeat the flow with a TV episode.
12. Test a dead torrent and confirm the three-minute no-progress error.
