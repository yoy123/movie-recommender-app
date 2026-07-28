package com.movierecommender.app

import com.movierecommender.app.data.remote.TorrentioBehaviorHints
import com.movierecommender.app.data.remote.TorrentioResponseParser
import com.movierecommender.app.data.remote.TorrentioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentioServiceTest {

    @Test
    fun parser_buildsFileSelectedMagnetAndReadsMetadata() {
        val parsed = TorrentioResponseParser.parse(
            TorrentioStream(
                name = "Torrentio\n1080p",
                title = "Example Show S01E01 1080p\n👤 75 💾 1.25 GB ⚙️ TorrentGalaxy",
                infoHash = "44c66ba1650661cb337cbac4d850e02d34ded802",
                fileIdx = 3,
                behaviorHints = TorrentioBehaviorHints("Example Show S01E01.mkv")
            )
        )

        requireNotNull(parsed)
        assertEquals("1080p", parsed.quality)
        assertEquals(75, parsed.seeds)
        assertEquals("1.25 GB", parsed.size)
        assertEquals("Torrentio (TorrentGalaxy)", parsed.provider)
        assertTrue(parsed.magnetUrl.contains("xt=urn:btih:44c66ba1650661cb337cbac4d850e02d34ded802"))
        assertTrue(parsed.magnetUrl.contains("so=3"))
    }

    @Test
    fun selector_prefersRequestedQualityWithHealthySwarm() {
        val streams = listOf(
            TorrentioStream(
                name = "Torrentio\n4k",
                title = "Example Movie 2160p\n👤 1 💾 18 GB ⚙️ IndexA",
                infoHash = "1111111111111111111111111111111111111111",
                fileIdx = 0,
                behaviorHints = TorrentioBehaviorHints("Example.Movie.2160p.mkv")
            ),
            TorrentioStream(
                name = "Torrentio\n1080p",
                title = "Example Movie 1080p\n👤 40 💾 2.1 GB ⚙️ IndexB",
                infoHash = "2222222222222222222222222222222222222222",
                fileIdx = 0,
                behaviorHints = TorrentioBehaviorHints("Example.Movie.1080p.mkv")
            )
        )

        val best = TorrentioResponseParser.pickBest(streams, "1080p", episodeMode = false)

        requireNotNull(best)
        assertEquals("1080p", best.quality)
        assertEquals("Torrentio (IndexB)", best.provider)
    }
}
