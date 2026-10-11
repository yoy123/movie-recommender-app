package com.movierecommender.app.torrent

/** Caps a read at a torrent piece boundary, so no unverified bytes are returned to Media3. */
internal object TorrentPieceReadPolicy {
    fun permittedBytes(offset: Long, pieceLength: Long, requested: Int, remaining: Long): Int {
        if (offset < 0L || pieceLength <= 0L || requested <= 0 || remaining <= 0L) return 0
        return minOf(requested.toLong(), remaining, pieceLength - offset % pieceLength).toInt()
    }
}
