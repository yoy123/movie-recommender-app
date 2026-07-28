package com.movierecommender.app

import com.movierecommender.app.torrent.TorrentMagnetUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentMagnetUtilsTest {

    @Test
    fun selectedFileIndex_readsSingleAndRangeSelections() {
        assertEquals(
            6,
            TorrentMagnetUtils.selectedFileIndex(
                "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&so=6"
            )
        )
        assertEquals(
            3,
            TorrentMagnetUtils.selectedFileIndex(
                "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&so=3-5,8"
            )
        )
    }

    @Test
    fun selectedFileIndex_rejectsMissingOrInvalidSelections() {
        assertNull(TorrentMagnetUtils.selectedFileIndex("https://example.com/file.torrent"))
        assertNull(
            TorrentMagnetUtils.selectedFileIndex(
                "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&so=-1"
            )
        )
    }
}
