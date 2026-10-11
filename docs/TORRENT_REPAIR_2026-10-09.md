# Torrent streaming repair log — October 9, 2026

This change set intentionally preserves existing uncommitted project work.

## Corrected

- Same-magnet reconnect no longer invalidates all existing watchdogs without recreating them. A connection with no active Torrent object is restarted rather than silently treated as a resume.
- Resuming cancels both the in-memory and persisted one-hour cache-expiry mechanisms. The expiry receiver rechecks the deadline under the same lock used for cache session writes.
- Failed sources report a new generation and the player releases the old media source. Stale player errors cannot terminate the replacement source.
- Mobile and Fire TV players use the same Media3 piece-aware DataSource: reads wait for verified libtorrent pieces, including requested seek and container-index positions. Missing pieces are prioritized by actual global torrent offset (important for multi-file packs).
- Read waits have a 90-second upper bound; the existing rebuffer path has a 60-second upper bound before source failover. Codec and container errors fail over rather than indefinitely retrying the same file.
- Successful service binds are unbound on disposal even when the connection callback never fired.
- Intentional user pause survives buffering and foreground/background transitions.
- A source with invalid or non-video selected-file index is rejected rather than playing another episode. Cache-fit preflight uses metadata file length, not the incomplete local file length.
- Disk capacity is checked while downloading; reserve falls under 500 MiB trigger an actionable storage failure.
- File read limits stop at torrent piece boundaries; piece verification maps video-relative positions to correct global piece indices.
- Cache storage root remains pinned to the root supplied to the native torrent library, avoiding checks on a different drive.
- Native torrent initialization and application startup cache cleanup run on IO dispatchers instead of blocking the Android main thread.

## Verification

- Gradle Kotlin compilation succeeded for mobileDebug and firestickDebug.
- Added 5 pure JVM tests in TorrentFileAndReadPolicyTest for episode selection and per-piece read boundaries.
- Unit-test tasks for both distributions passed.
- The full assemble and lint tasks were run during this repair session. See `app/build/reports` and Gradle build output for exact results.

## Limitations / next validation

- No Android/Fire TV device appeared in `adb devices`, so real network swarm/seek/playback scenarios were not instrumented. Verify with authorized/legal test torrents on actual target hardware before releasing.
- The legacy third-party TorrentStream-Android library can still have native library or swarm-specific failure modes. The new Media3 reader handles downloaded-piece gating but does not replace the native torrent engine.
- Upgrading old AndroidX Media3 and other build dependencies requires a separate controlled toolchain migration. This repair keeps the tested dependency versions to avoid introducing unrelated regressions.
- Privileged API credentials remain supplied through Android BuildConfig. A secure backend is required to eliminate secret exposure without disabling the existing AI/network features. Do not distribute production secrets in a public APK.

## Follow-up — October 9: startup buffering and timeouts

- Removed the second 64–256 MiB prebuffer gate and its independent progress dialog.
- Start Media3 immediately after the TorrentStream-Android library's initial 15 MiB preparation.
- Removed the fixed four-minute startup deadline; as long as pieces continue to download,
  the torrent remains eligible even when total startup exceeds four minutes.
- The remaining three-minute preparation watchdog measures **idle time since last activity**,
  using a monotonic clock; metadata discovery separately retains a 60-second watchdog.
- Added `TorrentProgressWatchdogTest` to cover more than 12 minutes of continuous progress,
  real stalls, resets from new progress, and inactive startup state.
