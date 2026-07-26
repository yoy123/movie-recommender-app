package com.movierecommender.app

import com.movierecommender.app.data.remote.TorznabFeedParser
import com.movierecommender.app.data.remote.TorznabEpisodeInventory
import com.movierecommender.app.data.remote.TorznabMatch
import com.movierecommender.app.data.remote.TorznabResult
import com.movierecommender.app.data.remote.TorznabSource
import com.movierecommender.app.data.remote.TorznabSourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorznabServiceTest {

    @Test
    fun sourceConfig_parsesMultipleEndpointsAndSkipsInvalidEntries() {
        val sources = TorznabSourceConfig.parse(
            "Primary|https://example.com/torznab|secret;invalid;Backup|https://backup.example/api|"
        )

        assertEquals(2, sources.size)
        assertEquals("Primary", sources[0].name)
        assertEquals("secret", sources[0].apiKey)
        assertEquals("Backup", sources[1].name)
    }

    @Test
    fun feedParser_readsMagnetAndBuildsMagnetFromInfoHash() {
        val xml = """
            <rss xmlns:torznab="http://torznab.com/schemas/2015/feed">
              <channel>
                <item>
                  <title>Example Movie 2026 1080p</title>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:ABC123&amp;dn=Example" />
                  <torznab:attr name="seeders" value="42" />
                  <torznab:attr name="peers" value="7" />
                  <torznab:attr name="size" value="2147483648" />
                </item>
                <item>
                  <title>Example Movie 2026 720p</title>
                  <torznab:attr name="infohash" value="0123456789abcdef0123456789abcdef01234567" />
                  <torznab:attr name="seeders" value="12" />
                </item>
                <item>
                  <title>Example Show S02E03E04 1080p</title>
                  <torznab:attr name="infohash" value="abcdef0123456789abcdef0123456789abcdef01" />
                  <torznab:attr name="seeders" value="20" />
                </item>
                <item>
                  <title>Example Show 3x05 720p</title>
                  <torznab:attr name="infohash" value="fedcba9876543210fedcba9876543210fedcba98" />
                  <torznab:attr name="seeders" value="8" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val results = TorznabFeedParser.parse(xml)

        assertEquals(5, results.size)
        assertEquals(42, results[0].seeders)
        assertEquals("1080p", results[0].quality)
        assertEquals(2147483648L, results[0].sizeBytes)
        assertTrue(results[1].magnetUrl.startsWith("magnet:?xt=urn:btih:0123456789abcdef"))
        assertEquals(listOf(2 to 3, 2 to 4, 3 to 5), results.drop(2).map { it.season to it.episode })
    }

      @Test
      fun episodeInventory_mergesEndpointsAndDeduplicatesInfoHashes() {
        val primary = TorznabSource("Primary", "https://primary.example/api", "key")
        val backup = TorznabSource("Backup", "https://backup.example/api", "key")
        val duplicateHash = "0123456789abcdef0123456789abcdef01234567"
        val matches = listOf(
          TorznabMatch(primary, episodeResult(1, 1, duplicateHash, "720p", 10)),
          TorznabMatch(backup, episodeResult(1, 1, duplicateHash.uppercase(), "720p", 5)),
          TorznabMatch(backup, episodeResult(1, 1, "abcdef0123456789abcdef0123456789abcdef01", "1080p", 20)),
          TorznabMatch(primary, episodeResult(1, 2, "fedcba9876543210fedcba9876543210fedcba98", "720p", 8))
        )

        val episodes = TorznabEpisodeInventory.aggregate(matches)

        assertEquals(listOf(1 to 1, 1 to 2), episodes.map { it.season to it.episode })
        assertEquals(2, episodes.first().torrents.size)
        assertEquals("1080p", episodes.first().torrents.first().quality)
        assertEquals("Torznab (Backup)", episodes.first().torrents.first().provider)
      }

      private fun episodeResult(
        season: Int,
        episode: Int,
        hash: String,
        quality: String,
        seeds: Int
      ) = TorznabResult(
        title = "Example Show S%02dE%02d %s".format(season, episode, quality),
        magnetUrl = "magnet:?xt=urn:btih:$hash",
        seeders = seeds,
        peers = 1,
        sizeBytes = null,
        quality = quality,
        season = season,
        episode = episode
      )
}