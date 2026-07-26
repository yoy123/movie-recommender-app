package com.movierecommender.app

import com.movierecommender.app.data.remote.InternetArchiveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetArchiveServiceTest {

    @Test
    fun searchParser_scoresExactTitleAndYearAboveDerivativeUploads() {
        val response = InternetArchiveParser.parseSearch(
            """
            {
              "response": {
                "docs": [
                  {"identifier":"double-feature","title":"Double Feature Charade","downloads":90000},
                  {"identifier":"Charade_1953","title":"Charade","year":"1953","downloads":1000}
                ]
              }
            }
            """.trimIndent()
        )

        val ranked = response.sortedByDescending {
            InternetArchiveParser.score(it, "Charade", "1953")
        }

        assertEquals("Charade_1953", ranked.first().identifier)
        assertTrue(InternetArchiveParser.isLikelyTitleMatch("Charade (1953)", "Charade"))
        assertFalse(InternetArchiveParser.isLikelyTitleMatch("Double Feature Charade", "Charade"))
    }

    @Test
    fun itemParser_returnsTorrentUrlAndLargestVideoSize() {
        val candidate = InternetArchiveParser.parseSearch(
            """{"response":{"docs":[{"identifier":"Charade_1953","title":"Charade","year":"1953"}]}}"""
        ).single()
        val item = InternetArchiveParser.parseItem(
            """
            {
              "is_dark": false,
              "metadata": {"mediatype":"movies"},
              "files": [
                {"name":"Charade_1953_archive.torrent","size":"36924"},
                {"name":"CHARADE_1953.mp4","size":"1013924173"},
                {"name":"CHARADE_1953_512kb.mp4","size":"355508845"}
              ]
            }
            """.trimIndent()
        )!!

        val torrent = InternetArchiveParser.toTorrentInfo(candidate, item)!!

        assertEquals(
            "https://archive.org/download/Charade_1953/Charade_1953_archive.torrent",
            torrent.magnetUrl
        )
        assertEquals("967.0 MB", torrent.size)
        assertEquals("Internet Archive", torrent.provider)
        assertNull(torrent.seeds)
    }

    @Test
    fun darkItem_isRejected() {
        val candidate = InternetArchiveParser.parseSearch(
            """{"response":{"docs":[{"identifier":"dark","title":"Dark"}]}}"""
        ).single()
        val item = InternetArchiveParser.parseItem(
            """{"is_dark":true,"metadata":{"mediatype":"movies"},"files":[{"name":"dark.torrent"}]}"""
        )!!

        assertNull(InternetArchiveParser.toTorrentInfo(candidate, item))
    }
}