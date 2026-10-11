package com.movierecommender.app

import com.movierecommender.app.torrent.TorrentProgressWatchdog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentProgressWatchdogTest {
    private val idleTimeout = 3L * 60L * 1000L

    @Test
    fun activelyDownloadingSourceSurvivesLongerThanFourMinutes() {
        val watchdog = TorrentProgressWatchdog(idleTimeout)
        watchdog.onActivity(0L)
        for (minute in 1L..12L) {
            val now = minute * 60_000L
            assertFalse("Timed out at minute $minute despite active download", watchdog.isStalled(now))
            watchdog.onActivity(now)
        }
        assertFalse(watchdog.isStalled(12L * 60_000L + 20_000L))
    }

    @Test
    fun noProgressForThreeMinutesTriggersFallback() {
        val watchdog = TorrentProgressWatchdog(idleTimeout)
        watchdog.onActivity(0L)
        assertFalse(watchdog.isStalled(idleTimeout - 1L))
        assertTrue(watchdog.isStalled(idleTimeout))
    }

    @Test
    fun newProgressResetsIdleCountdown() {
        val watchdog = TorrentProgressWatchdog(idleTimeout)
        watchdog.onActivity(0L)
        watchdog.onActivity(idleTimeout - 1_000L)
        assertFalse(watchdog.isStalled(idleTimeout + 1_000L))
        assertTrue(watchdog.isStalled(2L * idleTimeout - 1_000L))
    }

    @Test
    fun watchdogIsInactiveBeforeStartupBegins() {
        assertFalse(TorrentProgressWatchdog(idleTimeout).isStalled(10L * idleTimeout))
    }
}
