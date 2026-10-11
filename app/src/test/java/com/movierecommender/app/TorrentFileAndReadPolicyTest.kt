package com.movierecommender.app

import com.movierecommender.app.torrent.TorrentFilePolicy
import com.movierecommender.app.torrent.TorrentPieceReadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentFileAndReadPolicyTest {
    @Test fun largestVideoWinsOverNonVideoEntries() {
        assertEquals(2, TorrentFilePolicy.select(
            listOf("readme.nfo" to 1000L, "small.mp4" to 200L, "large.MKV" to 800L), null
        ))
    }

    @Test fun explicitSeasonPackIndexIsRespected() {
        assertEquals(1, TorrentFilePolicy.select(
            listOf("Episode01.mkv" to 100L, "Episode02.mkv" to 200L), 1
        ))
    }

    @Test fun invalidOrNonVideoIndexNeverSilentlyFallsBackToWrongEpisode() {
        val files = listOf("Episode01.mkv" to 100L, "poster.jpg" to 200L)
        assertNull(TorrentFilePolicy.select(files, 7))
        assertNull(TorrentFilePolicy.select(files, 1))
        assertNull(TorrentFilePolicy.select(listOf("poster.jpg" to 10L), null))
    }

    @Test fun zeroSizedFilesAreNotPlayable() {
        assertNull(TorrentFilePolicy.select(listOf("sample.mp4" to 0L), null))
    }

    @Test fun mediaReadsStayWithinVerifiedPiece() {
        assertEquals(2, TorrentPieceReadPolicy.permittedBytes(6L, 8L, 100, 100L))
        assertEquals(8, TorrentPieceReadPolicy.permittedBytes(8L, 8L, 100, 100L))
        assertEquals(1, TorrentPieceReadPolicy.permittedBytes(8L, 8L, 100, 1L))
        assertEquals(0, TorrentPieceReadPolicy.permittedBytes(8L, 0L, 100, 100L))
    }
}
