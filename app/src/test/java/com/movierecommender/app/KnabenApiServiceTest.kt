package com.movierecommender.app

import com.movierecommender.app.data.remote.KnabenHit
import com.movierecommender.app.data.remote.KnabenResultSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnabenApiServiceTest {

    @Test
    fun selector_buildsMagnetFromHashAndRejectsWrongRemakeYear() {
        val hits = listOf(
            KnabenHit(
                title = "Example Movie 1998 1080p BluRay",
                magnetUrl = null,
                hash = "1111111111111111111111111111111111111111",
                seeders = 200,
                peers = 20,
                bytes = 2_000_000_000,
                category = "Movies / HD",
                tracker = "WrongIndex"
            ),
            KnabenHit(
                title = "Example Movie 2026 1080p WEB-DL",
                magnetUrl = null,
                hash = "2222222222222222222222222222222222222222",
                seeders = 30,
                peers = 5,
                bytes = 1_500_000_000,
                category = "Movies / HD",
                tracker = "RightIndex"
            )
        )

        val best = KnabenResultSelector.pickMovie(hits, "Example Movie", "2026", "1080p")

        requireNotNull(best)
        assertEquals("Knaben (RightIndex)", best.provider)
        assertTrue(best.magnetUrl.startsWith("magnet:?xt=urn:btih:2222222222222222222222222222222222222222"))
    }

    @Test
    fun episodeSelector_requiresExactEpisodeMarker() {
        val hits = listOf(
            KnabenHit(
                title = "Example Show S01E02 1080p",
                magnetUrl = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
                hash = null,
                seeders = 100,
                peers = 10,
                bytes = 1_000_000_000,
                category = "TV / HD",
                tracker = "IndexA"
            ),
            KnabenHit(
                title = "Example Show S01E01 720p",
                magnetUrl = "magnet:?xt=urn:btih:2222222222222222222222222222222222222222",
                hash = null,
                seeders = 25,
                peers = 2,
                bytes = 700_000_000,
                category = "TV / HD",
                tracker = "IndexB"
            )
        )

        val best = KnabenResultSelector.pickEpisode(hits, "Example Show", 1, 1, "720p")

        requireNotNull(best)
        assertEquals("720p", best.quality)
        assertEquals("Knaben (IndexB)", best.provider)
    }
}
