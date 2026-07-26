package com.movierecommender.app

import com.movierecommender.app.data.remote.PirateBayEpisodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PirateBayEpisodeParserTest {

    @Test
    fun extractEpisodeKeys_acceptsEpisodeReleasesAndRejectsSeasonPacks() {
        assertEquals(listOf(2 to 3), PirateBayEpisodeParser.extractEpisodeKeys("Example.Show.S02E03.1080p"))
        assertEquals(listOf(4 to 7), PirateBayEpisodeParser.extractEpisodeKeys("Example Show 4x07 WEB"))
        assertTrue(PirateBayEpisodeParser.extractEpisodeKeys("Example Show Complete Season 2").isEmpty())
    }
}