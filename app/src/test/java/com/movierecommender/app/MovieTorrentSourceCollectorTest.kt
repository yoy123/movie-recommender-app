package com.movierecommender.app

import com.movierecommender.app.data.remote.TorrentInfo
import com.movierecommender.app.data.repository.MovieTorrentSourceCollector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieTorrentSourceCollectorTest {

    @Test
    fun collectAll_emitsFastResultWhileSlowSourceKeepsRunning() = runBlocking {
        val slowStarted = CompletableDeferred<Unit>()
        val releaseSlowSource = CompletableDeferred<Unit>()
        val firstEmission = CompletableDeferred<TorrentInfo>()
        val fast = torrent("Torrentio", seeds = 50)
        val slow = torrent("Popcorn", seeds = 5)

        val collection = async {
            MovieTorrentSourceCollector.collectAll(
                sources = listOf(
                    {
                        slowStarted.complete(Unit)
                        releaseSlowSource.await()
                        slow
                    },
                    { fast }
                ),
                onResult = { firstEmission.complete(it) }
            )
        }

        withTimeout(1_000L) { slowStarted.await() }
        assertEquals(fast, withTimeout(1_000L) { firstEmission.await() })
        assertFalse(collection.isCompleted)

        releaseSlowSource.complete(Unit)
        assertEquals(setOf(fast, slow), collection.await().toSet())
    }

    @Test
    fun collectAll_invokesEverySourceAndIsolatesFailures() = runBlocking {
        val invoked = mutableSetOf<String>()
        val healthy = torrent("Knaben", seeds = 10)

        val results = MovieTorrentSourceCollector.collectAll(
            listOf(
                {
                    invoked += "empty"
                    null
                },
                {
                    invoked += "failure"
                    error("source unavailable")
                },
                {
                    invoked += "healthy"
                    healthy
                }
            )
        )

        assertEquals(setOf("empty", "failure", "healthy"), invoked)
        assertEquals(listOf(healthy), results)
        assertTrue(results.single().seeds!! > 0)
    }

    private fun torrent(provider: String, seeds: Int) = TorrentInfo(
        magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
        quality = "1080p",
        seeds = seeds,
        peers = 0,
        size = "1 GB",
        filesize = "1 GB",
        provider = provider
    )
}