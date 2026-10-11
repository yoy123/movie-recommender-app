package com.movierecommender.app

import com.movierecommender.app.data.remote.PopcornApiService
import com.movierecommender.app.data.remote.PopcornMovie
import com.movierecommender.app.data.remote.TorrentInfo
import com.movierecommender.app.data.remote.YtsApiService
import com.movierecommender.app.data.remote.YtsMovie
import com.movierecommender.app.data.remote.YtsTorrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentProviderVariantsTest {
    @Test
    fun ytsRetainsEveryEncodeWithExactReportedByteSize() {
        val movie = YtsMovie(
            id = 1, imdbCode = "tt1234567", title = "Example", year = 2025,
            rating = null, runtime = null, genres = null, synopsis = null,
            torrents = listOf(
                YtsTorrent(null, "0123456789012345678901234567890123456789", "720p", null, 50, 5, "800 MB", 838860800),
                YtsTorrent(null, "abcdefabcdefabcdefabcdefabcdefabcdefabcd", "1080p", null, 90, 8, "1.5 GB", 1610612736)
            )
        )

        val found = YtsApiService().getAvailableTorrents(movie)

        assertEquals(2, found.size)
        assertEquals(setOf("720p", "1080p"), found.map { it.quality }.toSet())
        assertEquals("1610612736", found.last().size)
        assertTrue(found.all { it.magnetUrl.startsWith("magnet:?xt=urn:btih:") })
    }

    @Test
    fun popcornRetainsAllEnglishEncodesAndFillsMissingQuality() {
        val source = TorrentInfo(
            magnetUrl = "magnet:?xt=urn:btih:0123456789012345678901234567890123456789",
            quality = null, seeds = 15, peers = 4,
            size = "850 MB", filesize = "850 MB", provider = null
        )
        val movie = PopcornMovie(
            id = "123", imdbId = "tt1234567", tmdbId = null,
            title = "Example", year = "2025", synopsis = null, runtime = null,
            genres = null, images = null, rating = null,
            torrents = mapOf("en" to mapOf(
                "720p" to source,
                "1080p" to source.copy(
                    magnetUrl = "magnet:?xt=urn:btih:abcdefabcdefabcdefabcdefabcdefabcdefabcd",
                    size = "1.5 GB", filesize = "1.5 GB"
                )
            )),
            trailer = null
        )

        val found = PopcornApiService().getAvailableTorrents(movie)

        assertEquals(2, found.size)
        assertEquals(setOf("720p", "1080p"), found.map { it.quality }.toSet())
        assertTrue(found.all { it.provider == "Popcorn" })
    }
}
