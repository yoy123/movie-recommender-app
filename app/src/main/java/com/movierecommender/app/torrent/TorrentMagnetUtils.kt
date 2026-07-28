package com.movierecommender.app.torrent

import android.net.Uri

/**
 * Normalizes magnet links before playback.
 *
 * Many upstream APIs return magnets with no trackers or a stale tracker set.
 * Appending a small set of reliable public trackers gives the torrent engine a
 * better chance of finding peers without having to special-case every source.
 */
object TorrentMagnetUtils {

    private val DEFAULT_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker.tryhackx.org:6969/announce",
        "udp://tracker.openbittorrent.com:80/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://tracker.leechers-paradise.org:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce"
    )

    fun enrichMagnetUrl(magnetUrl: String): String {
        if (!magnetUrl.startsWith("magnet:?", ignoreCase = true)) {
            return magnetUrl
        }

        val existingTrackers = runCatching {
            Uri.parse(magnetUrl)
                .getQueryParameters("tr")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrDefault(emptySet())

        val missingTrackers = DEFAULT_TRACKERS.filterNot(existingTrackers::contains)
        if (missingTrackers.isEmpty()) {
            return magnetUrl
        }

        val trackerSuffix = missingTrackers.joinToString(separator = "") {
            "&tr=${Uri.encode(it)}"
        }
        return magnetUrl + trackerSuffix
    }

    /**
     * Reads the first BEP-53 selected-file index from a magnet's `so` parameter.
     * Torrentio supplies this for exact episodes contained inside season packs.
     */
    fun selectedFileIndex(magnetUrl: String): Int? {
        if (!magnetUrl.startsWith("magnet:?", ignoreCase = true)) return null
        val selection = Regex("(?:[?&])so=([^&]+)", RegexOption.IGNORE_CASE)
            .find(magnetUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return selection.substringBefore(',')
            .substringBefore('-')
            .trim()
            .toIntOrNull()
            ?.takeIf { it >= 0 }
    }
}