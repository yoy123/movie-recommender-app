package com.movierecommender.app.torrent

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Media3 source for an actively downloaded torrent video.
 *
 * A normal file:// source can read sparse/unwritten regions as zeroes or premature EOF.
 * This source reads only completed libtorrent pieces and prioritizes the piece Media3
 * actually needs (including metadata at the end of MP4 files and seek destinations).
 */
@OptIn(UnstableApi::class)
class TorrentPieceAwareDataSource(
    private val service: TorrentStreamService,
    private val sourceId: Long
) : BaseDataSource(false) {
    class Factory(
        private val service: TorrentStreamService,
        private val sourceId: Long
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            TorrentPieceAwareDataSource(service, sourceId)
    }

    private var input: RandomAccessFile? = null
    private var sourceUri: Uri? = null
    private var videoPath: String? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val path = dataSpec.uri.path ?: throw IOException("Missing torrent video path")
        val active = service.getVideoFilePath()
        if (active == null ||
            File(path).canonicalPath != File(active).canonicalPath ||
            service.getActiveSourceId() != sourceId
        ) {
            throw IOException("The torrent source changed during playback")
        }
        val size = service.getVideoFileSize()
        if (size <= 0 || dataSpec.position > size) {
            throw IOException("Requested position exceeds torrent video size")
        }
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            size - position
        } else {
            minOf(dataSpec.length, size - position)
        }
        input = RandomAccessFile(File(path), "r")
        sourceUri = dataSpec.uri
        videoPath = active
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0L) return C.RESULT_END_OF_INPUT
        val path = videoPath ?: throw IOException("Torrent source is closed")
        val file = input ?: throw IOException("Torrent file is closed")
        val pieceLength = service.getTorrentPieceLength(path)
        if (pieceLength <= 0L) throw IOException("Torrent piece metadata is unavailable")
        val requested = TorrentPieceReadPolicy.permittedBytes(
            position, pieceLength, length, remaining
        )
        if (requested <= 0) return C.RESULT_END_OF_INPUT

        val start = System.nanoTime()
        var lastPriorityRequest = 0L
        while (!service.hasDownloadedByte(path, position)) {
            if (service.getActiveSourceId() != sourceId ||
                service.getVideoFilePath() != path
            ) {
                throw IOException("Torrent changed while waiting for a piece")
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            if (elapsedMs >= MAX_WAIT_PER_PIECE_MS) {
                throw IOException("Torrent piece was unavailable for 90 seconds")
            }
            if (elapsedMs - lastPriorityRequest >= 800L || lastPriorityRequest == 0L) {
                service.requestTorrentByte(path, position)
                lastPriorityRequest = elapsedMs
            }
            try {
                Thread.sleep(200L) // Runs on Media3's loader thread, never Android's UI thread.
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Torrent read interrupted")
            }
        }

        file.seek(position)
        val bytesRead = file.read(buffer, offset, requested)
        if (bytesRead <= 0) {
            throw IOException("Torrent reported a complete piece that is not readable on disk")
        }
        position += bytesRead
        remaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = sourceUri

    override fun close() {
        val wasOpened = opened
        opened = false
        try {
            input?.close()
        } finally {
            input = null
            sourceUri = null
            videoPath = null
            remaining = 0L
            if (wasOpened) transferEnded()
        }
    }

    private companion object {
        const val MAX_WAIT_PER_PIECE_MS = 90_000L
    }
}
