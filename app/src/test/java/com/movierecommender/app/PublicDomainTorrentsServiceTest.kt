package com.movierecommender.app

import com.movierecommender.app.data.remote.PublicDomainTorrentsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicDomainTorrentsServiceTest {

    @Test
    fun catalogParser_readsUnquotedMovieLinksAndMatchesExactTitles() {
        val movies = PublicDomainTorrentsParser.parseCatalog(
            """
            <a href=nshowmovie.html?movieid=827>The Blancheville Monster</a>
            <a href="nshowmovie.html?movieid=365">Africa Screams</a>
            """.trimIndent()
        )

        assertEquals(2, movies.size)
        assertEquals("827", movies.first().id)
        assertTrue(
            PublicDomainTorrentsParser.isExactTitleMatch(
                "The Blancheville Monster",
                "The Blancheville Monster"
            )
        )
        assertFalse(PublicDomainTorrentsParser.isExactTitleMatch("Charade Double Feature", "Charade"))
    }

    @Test
    fun detailParser_upgradesHttpsAndPrefersLargestMp4() {
        val detail = PublicDomainTorrentsParser.parseDetail(
            """
            <a href=http://www.imdb.com/title/tt0057155/>IMDb</a>
            <a href=http://www.publicdomaintorrents.com/bt/btdownload.php?type=torrent&amp;file=Movie.avi.torrent>Click for Divx 716MB AVI</a>
            <a href=http://www.publicdomaintorrents.com/bt/btdownload.php?type=torrent&amp;file=Movie.mp4.torrent>Click for IPOD MP4 247 MB</a>
            <a href=http://www.publicdomaintorrents.com/bt/btdownload.php?type=torrent&amp;file=Movie_PSP.MP4.torrent>Click for PSP MP4 455 MB</a>
            """.trimIndent()
        )

        val torrent = PublicDomainTorrentsParser.toTorrentInfo(detail)!!

        assertEquals("tt0057155", detail.imdbId)
        assertTrue(torrent.magnetUrl.startsWith("https://www.publicdomaintorrents.com/"))
        assertTrue(torrent.magnetUrl.contains("Movie_PSP.MP4.torrent"))
        assertEquals("Public Domain Torrents", torrent.provider)
    }

    @Test
    fun imdbMatch_rejectsDifferentRemake() {
        assertTrue(PublicDomainTorrentsParser.matchesImdb("tt0057155", "0057155"))
        assertFalse(PublicDomainTorrentsParser.matchesImdb("tt0057155", "tt1234567"))
    }
}