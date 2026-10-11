package com.movierecommender.app

import com.movierecommender.app.torrent.TorrentBufferPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class TorrentBufferPolicyTest {

    @Test
    fun cacheLimit_preservesDeviceReserveAndCountsExistingCache() {
        assertEquals(4_108, TorrentBufferPolicy.calculateCacheLimitMb(4_096, 512))
        assertEquals(80, TorrentBufferPolicy.calculateCacheLimitMb(300, 80))
        assertEquals(0, TorrentBufferPolicy.calculateCacheLimitMb(300, 0))
    }

    @Test
    fun requiredStorage_roundsUpPartialMegabyte() {
        assertEquals(2, TorrentBufferPolicy.requiredStorageMb(1L * 1024L * 1024L + 1L))
    }
}
