package com.movierecommender.app

import com.movierecommender.app.data.remote.TorrentInfo
import com.movierecommender.app.data.repository.MovieTorrentSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieTorrentSelectionPolicyTest {

    private fun score(torrent: TorrentInfo): Int {
        val qualityScore = when (torrent.quality?.lowercase()) {
            "1080p" -> 1_000
            "720p" -> 850
            "2160p", "4k" -> 800
            "480p" -> 550
            "cam" -> -1_000
            else -> 350
        }
        return qualityScore + ((torrent.seeds ?: 0).coerceAtMost(500) * 4) +
            ((torrent.peers ?: 0).coerceAtMost(200) * 2)
    }

    private fun torrent(provider: String, quality: String?, size: String?, seeds: Int, peers: Int = 0) =
        TorrentInfo(
            magnetUrl = "magnet:?xt=urn:btih:$provider",
            quality = quality,
            seeds = seeds,
            peers = peers,
            size = size,
            filesize = size,
            provider = provider
        )

    @Test
    fun parseSizeBytes_readsUnitsAndBareBytes() {
        assertEquals(700L * 1024 * 1024, MovieTorrentSelectionPolicy.parseSizeBytes("700 MB"))
        assertEquals(2L * 1024 * 1024 * 1024, MovieTorrentSelectionPolicy.parseSizeBytes("2 GB"))
        assertEquals(3_808_117_223L, MovieTorrentSelectionPolicy.parseSizeBytes("3808117223"))
        assertNull(MovieTorrentSelectionPolicy.parseSizeBytes(null))
        assertNull(MovieTorrentSelectionPolicy.parseSizeBytes(""))
    }

    @Test
    fun selectBest_prefersFittingSourceOverHigherScoringOversized() {
        val oversized = torrent("Big", "1080p", "7.9 GB", seeds = 6000, peers = 200)
        val fitting = torrent("Fits", "720p", "1.5 GB", seeds = 40)

        val best = MovieTorrentSelectionPolicy.selectBest(
            listOf(oversized, fitting),
            safeCacheBudgetMb = 1800,
            ::score
        )

        assertEquals(fitting, best)
    }

    @Test
    fun selectBest_amongFittingPicksHighestScore() {
        val fittingLow = torrent("A", "720p", "1.2 GB", seeds = 10)
        val fittingHigh = torrent("B", "1080p", "1.6 GB", seeds = 500, peers = 200)

        val best = MovieTorrentSelectionPolicy.selectBest(
            listOf(fittingLow, fittingHigh),
            safeCacheBudgetMb = 1800,
            ::score
        )

        assertEquals(fittingHigh, best)
    }

    @Test
    fun selectBest_whenNoneFitPrefersSmallest() {
        val huge = torrent("Huge", "1080p", "9 GB", seeds = 6000, peers = 200)
        val lessHuge = torrent("Less", "720p", "6 GB", seeds = 3000)

        val best = MovieTorrentSelectionPolicy.selectBest(
            listOf(huge, lessHuge),
            safeCacheBudgetMb = 1800,
            ::score
        )

        assertEquals(lessHuge, best)
    }

    @Test
    fun selectBest_prefersKnownFittingOverUnknownSize() {
        val unknownSize = torrent("Unknown", "1080p", null, seeds = 6000, peers = 200)
        val knownFitting = torrent("Known", "720p", "1.4 GB", seeds = 20)

        val best = MovieTorrentSelectionPolicy.selectBest(
            listOf(unknownSize, knownFitting),
            safeCacheBudgetMb = 1800,
            ::score
        )

        assertEquals(knownFitting, best)
    }

    @Test
    fun selectBest_withoutBudgetKeepsHighestScore() {
        val small = torrent("Small", "720p", "1.2 GB", seeds = 10)
        val strong = torrent("Strong", "1080p", "8 GB", seeds = 6000, peers = 200)

        val best = MovieTorrentSelectionPolicy.selectBest(
            listOf(small, strong),
            safeCacheBudgetMb = null,
            ::score
        )

        assertEquals(strong, best)
    }

    @Test
    fun parseSizeBytes_supportsBinaryUnitsAndCommas() {
        assertEquals(1536L * 1024 * 1024, MovieTorrentSelectionPolicy.parseSizeBytes("1.5 GiB"))
        assertEquals(1024L * 1024 * 1024, MovieTorrentSelectionPolicy.parseSizeBytes("1,024 MB"))
    }

    @Test
    fun fitsBudget_whenNoSpaceRemainsRejectsEveryPositiveSize() {
        val source = torrent("NoSpace", "720p", "600 MB", seeds = 10)
        assertEquals(false, MovieTorrentSelectionPolicy.fitsBudget(source, 0))
        assertEquals(false, MovieTorrentSelectionPolicy.fitsBudget(source, -1))
        assertEquals(true, MovieTorrentSelectionPolicy.fitsBudget(source, null))
    }

    @Test
    fun fitsBudget_usesFileSizeWhenSizeIsBlank() {
        val source = torrent("FallbackField", "720p", "", seeds = 10)
            .copy(filesize = "1.4 GB")
        assertEquals(true, MovieTorrentSelectionPolicy.fitsBudget(source, 1800))
        assertEquals(false, MovieTorrentSelectionPolicy.fitsBudget(source, 1000))
    }

    @Test
    fun selectBest_returnsNullForEmpty() {
        assertNull(MovieTorrentSelectionPolicy.selectBest(emptyList(), 1800, ::score))
    }

    @Test
    fun rank_ordersEveryUniqueCandidateForAutomaticFailover() {
        val oversized = torrent("Huge", "1080p", "8 GB", seeds = 6000)
        val lowerScore = torrent("Lower", "720p", "1.2 GB", seeds = 10)
        val best = torrent("Best", "1080p", "1.5 GB", seeds = 100)
        val duplicateBest = best.copy(provider = "Duplicate")

        assertEquals(
            listOf(best, lowerScore, oversized),
            MovieTorrentSelectionPolicy.rank(
                listOf(oversized, lowerScore, best, duplicateBest),
                safeCacheBudgetMb = 1800,
                ::score
            )
        )
    }
}
