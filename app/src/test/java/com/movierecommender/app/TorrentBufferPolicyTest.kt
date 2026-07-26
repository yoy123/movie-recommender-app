package com.movierecommender.app

import com.movierecommender.app.torrent.TorrentBufferPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentBufferPolicyTest {

    @Test
    fun cacheLimit_preservesDeviceReserveAndCountsExistingCache() {
        assertEquals(4_108, TorrentBufferPolicy.calculateCacheLimitMb(4_096, 512))
        assertEquals(80, TorrentBufferPolicy.calculateCacheLimitMb(300, 80))
        assertEquals(0, TorrentBufferPolicy.calculateCacheLimitMb(300, 0))
    }

    @Test
    fun slowDownload_requiresWholeRemainingMovieBeforePlayback() {
        val twoGb = 2L * 1024L * 1024L * 1024L
        val twoHoursMs = 2L * 60L * 60L * 1000L

        assertEquals(
            twoGb,
            TorrentBufferPolicy.requiredContiguousBufferBytes(
                fileSizeBytes = twoGb,
                durationMs = twoHoursMs,
                startPositionMs = 0L,
                measuredDownloadBytesPerSecond = 0L
            )
        )
    }

    @Test
    fun resumeGate_neverRequiresBytesBehindResumePosition() {
        val twoGb = 2L * 1024L * 1024L * 1024L
        val twoHoursMs = 2L * 60L * 60L * 1000L

        assertEquals(
            twoGb / 2L,
            TorrentBufferPolicy.requiredContiguousBufferBytes(
                fileSizeBytes = twoGb,
                durationMs = twoHoursMs,
                startPositionMs = twoHoursMs / 2L,
                measuredDownloadBytesPerSecond = 0L
            )
        )
    }

    @Test
    fun fastDownload_usesLargePeakHeadroomWithoutWaitingForWholeMovie() {
        val twoGb = 2L * 1024L * 1024L * 1024L
        val twoHoursMs = 2L * 60L * 60L * 1000L

        val required = TorrentBufferPolicy.requiredContiguousBufferBytes(
            fileSizeBytes = twoGb,
            durationMs = twoHoursMs,
            startPositionMs = 0L,
            measuredDownloadBytesPerSecond = 4L * 1024L * 1024L
        )

        assertTrue(required >= 256L * 1024L * 1024L)
        assertTrue(required < twoGb)
    }

    @Test
    fun requiredStorage_roundsUpPartialMegabyte() {
        assertEquals(2, TorrentBufferPolicy.requiredStorageMb(1L * 1024L * 1024L + 1L))
    }
}
