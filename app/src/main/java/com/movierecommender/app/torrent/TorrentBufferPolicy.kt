package com.movierecommender.app.torrent

import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure calculations used by [TorrentStreamService]. Keeping these calculations free of
 * Android dependencies makes the storage and no-stall gates directly unit-testable.
 */
object TorrentBufferPolicy {
    const val RESERVED_DEVICE_SPACE_MB = 500L
    const val MINIMUM_START_BUFFER_MB = 256L
    const val STARTUP_HEADROOM_SECONDS = 10L * 60L
    const val PEAK_BITRATE_FACTOR = 1.5
    const val DOWNLOAD_SPEED_SAFETY_FACTOR = 0.65

    /**
     * Maximum total size the active torrent cache may reach while preserving the device reserve.
     * Existing cache bytes are included because StatFs available space already excludes them.
     */
    fun calculateCacheLimitMb(freeSpaceMb: Long, existingCacheMb: Long): Int {
        val additionalUsableMb = (freeSpaceMb - RESERVED_DEVICE_SPACE_MB).coerceAtLeast(0L)
        return (existingCacheMb.coerceAtLeast(0L) + additionalUsableMb)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    /**
     * Computes the contiguous bytes that should be present ahead of the resume position before
     * playback begins. The target covers a ten-minute peak-rate cushion plus the projected
     * whole-session deficit when the conservatively discounted download speed is slower than
     * playback consumption.
     */
    fun requiredContiguousBufferBytes(
        fileSizeBytes: Long,
        durationMs: Long,
        startPositionMs: Long,
        measuredDownloadBytesPerSecond: Long
    ): Long {
        if (fileSizeBytes <= 0L) return 0L

        val normalizedStartMs = startPositionMs.coerceAtLeast(0L)
        if (durationMs <= 0L) {
            return minOf(fileSizeBytes, MINIMUM_START_BUFFER_MB * BYTES_PER_MB)
        }

        val boundedStartMs = normalizedStartMs.coerceAtMost(durationMs)
        val remainingFraction = ((durationMs - boundedStartMs).toDouble() / durationMs.toDouble())
            .coerceIn(0.0, 1.0)
        val remainingBytes = (fileSizeBytes * remainingFraction).toLong().coerceAtLeast(0L)
        if (remainingBytes == 0L) return 0L

        val durationSeconds = (durationMs / 1000.0).coerceAtLeast(1.0)
        val remainingSeconds = ((durationMs - boundedStartMs) / 1000.0).coerceAtLeast(0.0)
        val averagePlaybackBytesPerSecond = fileSizeBytes / durationSeconds
        val protectedPlaybackRate = averagePlaybackBytesPerSecond * PEAK_BITRATE_FACTOR
        val conservativeDownloadRate = measuredDownloadBytesPerSecond.coerceAtLeast(0L) *
            DOWNLOAD_SPEED_SAFETY_FACTOR

        val peakHeadroomBytes = protectedPlaybackRate * STARTUP_HEADROOM_SECONDS
        val projectedDeficitBytes = max(
            0.0,
            (protectedPlaybackRate - conservativeDownloadRate) * remainingSeconds
        )
        val minimumBytes = MINIMUM_START_BUFFER_MB * BYTES_PER_MB

        return max(minimumBytes.toDouble(), peakHeadroomBytes + projectedDeficitBytes)
            .toLong()
            .coerceAtMost(remainingBytes)
    }

    fun requiredStorageMb(fileSizeBytes: Long): Int {
        if (fileSizeBytes <= 0L) return 0
        return ceil(fileSizeBytes.toDouble() / BYTES_PER_MB.toDouble()).toInt()
    }

    private const val BYTES_PER_MB = 1024L * 1024L
}
