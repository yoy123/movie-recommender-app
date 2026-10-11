package com.movierecommender.app.torrent

import kotlin.math.ceil

/**
 * Pure calculations used by [TorrentStreamService]. Keeping these calculations free of
 * Android dependencies makes the cache/storage guards directly unit-testable.
 */
object TorrentBufferPolicy {
    const val RESERVED_DEVICE_SPACE_MB = 500L
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

    fun requiredStorageMb(fileSizeBytes: Long): Int {
        if (fileSizeBytes <= 0L) return 0
        return ceil(fileSizeBytes.toDouble() / BYTES_PER_MB.toDouble()).toInt()
    }

    private const val BYTES_PER_MB = 1024L * 1024L
}
