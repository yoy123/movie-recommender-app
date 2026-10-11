package com.movierecommender.app.data.repository

import com.movierecommender.app.data.remote.TorrentInfo

/**
 * Chooses among gathered torrent candidates with awareness of the cache the device can safely
 * dedicate. Because the whole selected file must currently fit on disk before playback, a source
 * that fits the budget is always preferred over one that does not.
 */
internal object MovieTorrentSelectionPolicy {
    private const val BYTES_PER_MB = 1024L * 1024L
    private val SIZE_REGEX =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*(tib|gib|mib|kib|tb|gb|mb|kb|bytes?|b)\b""", RegexOption.IGNORE_CASE)

    fun parseSizeBytes(size: String?): Long? {
        val text = size?.replace(",", "")?.trim().orEmpty()
        if (text.isEmpty()) return null
        // A bare byte count (e.g. a raw "filesize") is accepted directly.
        text.toLongOrNull()?.let { return if (it > 0) it else null }
        val match = SIZE_REGEX.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "tb", "tib" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            "gb", "gib" -> 1024.0 * 1024.0 * 1024.0
            "mb", "mib" -> 1024.0 * 1024.0
            "kb", "kib" -> 1024.0
            else -> 1.0
        }
        val bytes = (value * multiplier).toLong()
        return if (bytes > 0) bytes else null
    }

    private fun sourceSizeBytes(torrent: TorrentInfo): Long? =
        parseSizeBytes(torrent.size) ?: parseSizeBytes(torrent.filesize)

    /** A known budget with an unknown source size counts as "does not fit" so known-fitting wins. */
    fun fitsBudget(torrent: TorrentInfo, safeCacheBudgetMb: Int?): Boolean {
        if (safeCacheBudgetMb == null) return true
        if (safeCacheBudgetMb <= 0) return false
        val bytes = sourceSizeBytes(torrent) ?: return false
        return bytes <= safeCacheBudgetMb.toLong() * BYTES_PER_MB
    }

    /** Returns the better of [current] and [candidate] under storage-aware rules. */
    fun preferred(
        current: TorrentInfo?,
        candidate: TorrentInfo,
        safeCacheBudgetMb: Int?,
        score: (TorrentInfo) -> Int
    ): TorrentInfo {
        if (current == null) return candidate
        val currentFits = fitsBudget(current, safeCacheBudgetMb)
        val candidateFits = fitsBudget(candidate, safeCacheBudgetMb)
        if (currentFits != candidateFits) return if (candidateFits) candidate else current
        if (currentFits) {
            return if (score(candidate) > score(current)) candidate else current
        }
        // Neither fits: prefer the smallest known size to minimise the overage, else higher score.
        val currentSize = sourceSizeBytes(current)
        val candidateSize = sourceSizeBytes(candidate)
        return when {
            currentSize != null && candidateSize != null ->
                if (candidateSize < currentSize) candidate else current
            candidateSize != null -> candidate
            currentSize != null -> current
            else -> if (score(candidate) > score(current)) candidate else current
        }
    }

    fun selectBest(
        candidates: List<TorrentInfo>,
        safeCacheBudgetMb: Int?,
        score: (TorrentInfo) -> Int
    ): TorrentInfo? = rank(candidates, safeCacheBudgetMb, score).firstOrNull()

    /** Returns every candidate in the same order [selectBest] would choose them successively. */
    fun rank(
        candidates: List<TorrentInfo>,
        safeCacheBudgetMb: Int?,
        score: (TorrentInfo) -> Int
    ): List<TorrentInfo> {
        val remaining = candidates.distinctBy { it.magnetUrl }.toMutableList()
        return buildList {
            while (remaining.isNotEmpty()) {
                var best: TorrentInfo? = null
                for (candidate in remaining) {
                    best = preferred(best, candidate, safeCacheBudgetMb, score)
                }
                val selected = best ?: break
                add(selected)
                remaining.remove(selected)
            }
        }
    }
}
