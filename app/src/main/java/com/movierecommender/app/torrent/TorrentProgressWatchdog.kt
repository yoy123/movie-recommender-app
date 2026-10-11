package com.movierecommender.app.torrent

import java.util.concurrent.atomic.AtomicLong

/**
 * Startup failures are based only on time without new download activity.
 * Elapsed time since the magnet was selected must not trigger a fallback.
 */
internal class TorrentProgressWatchdog(private val idleLimitMs: Long) {
    private val lastProgressAtMs = AtomicLong(-1L)

    fun onActivity(nowElapsedMs: Long) {
        lastProgressAtMs.set(nowElapsedMs)
    }

    fun isStalled(nowElapsedMs: Long): Boolean {
        val lastProgress = lastProgressAtMs.get()
        return lastProgress >= 0L &&
            nowElapsedMs >= lastProgress &&
            nowElapsedMs - lastProgress >= idleLimitMs
    }
}
