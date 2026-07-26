package com.movierecommender.app

import com.movierecommender.app.data.remote.TorznabFeedParser
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
              </channel>
            </rss>
        """.trimIndent()

        val results = TorznabFeedParser.parse(xml)

        assertEquals(2, results.size)
        assertEquals(42, results[0].seeders)
        assertEquals("1080p", results[0].quality)
        assertEquals(2147483648L, results[0].sizeBytes)
        assertTrue(results[1].magnetUrl.startsWith("magnet:?xt=urn:btih:0123456789abcdef"))
    }
}