package com.movierecommender.app

import com.movierecommender.app.torrent.TorrentPlaybackSource
import com.movierecommender.app.torrent.TorrentSourceRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TorrentSourceRegistryTest {

    @After
    fun clearRegistry() {
        TorrentSourceRegistry.clearForTests()
    }

    @Test
    fun sourcesFor_startsWithSelectedSourceThenKeepsRankedAlternatives() {
        val first = source("a", "YTS")
        val second = source("b", "Torrentio")
        val third = source("c", "Knaben")
        TorrentSourceRegistry.register(listOf(first, second, third))

        assertEquals(listOf(second, first, third), TorrentSourceRegistry.sourcesFor(second.url))
    }

    @Test
    fun register_deduplicatesTrackerVariantsOfSameInfoHash() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val withoutTracker = TorrentPlaybackSource("magnet:?xt=urn:btih:$hash", "A")
        val withTracker = TorrentPlaybackSource(
            "magnet:?xt=urn:btih:$hash&tr=udp%3A%2F%2Ftracker.example%3A80",
            "B"
        )

        assertEquals(1, TorrentSourceRegistry.register(listOf(withoutTracker, withTracker)).size)
    }

    @Test
    fun sourceKey_keepsDifferentSelectedFilesDistinct() {
        val hash = "0123456789abcdef0123456789abcdef01234567"

        assertEquals(
            2,
            TorrentSourceRegistry.register(
                listOf(
                    TorrentPlaybackSource("magnet:?xt=urn:btih:$hash&so=1"),
                    TorrentPlaybackSource("magnet:?xt=urn:btih:$hash&so=2")
                )
            ).size
        )
    }

    @Test
    fun sourcesFor_unregisteredUrlReturnsSingleSource() {
        val url = "https://example.com/movie.torrent"

        assertEquals(listOf(TorrentPlaybackSource(url)), TorrentSourceRegistry.sourcesFor(url))
    }

    private fun source(hashSuffix: String, provider: String) = TorrentPlaybackSource(
        url = "magnet:?xt=urn:btih:${hashSuffix.repeat(40)}",
        provider = provider,
        quality = "1080p",
        seeds = 10
    )
}