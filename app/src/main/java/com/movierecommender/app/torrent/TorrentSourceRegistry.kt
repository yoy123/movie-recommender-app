package com.movierecommender.app.torrent

/** A ranked torrent candidate retained for automatic playback failover. */
data class TorrentPlaybackSource(
    val url: String,
    val provider: String? = null,
    val quality: String? = null,
    val seeds: Int? = null
)

/**
 * Process-local bridge between torrent discovery and playback.
 *
 * Navigation continues to pass one URL, while the repository registers the other ranked results
 * from the same lookup. Mapping every candidate key to the complete group lets playback recover
 * regardless of which candidate was selected before all provider requests completed.
 */
object TorrentSourceRegistry {
    private const val MAX_SOURCES_PER_GROUP = 8
    private const val MAX_REGISTERED_KEYS = 256
    private val infoHashRegex = Regex("(?:[?&])xt=urn:btih:([a-z0-9]+)", RegexOption.IGNORE_CASE)
    private val fileSelectionRegex = Regex("(?:[?&])so=([^&]+)", RegexOption.IGNORE_CASE)
    private val lock = Any()
    private val groupsBySourceKey = LinkedHashMap<String, List<TorrentPlaybackSource>>()

    fun register(rankedSources: List<TorrentPlaybackSource>): List<TorrentPlaybackSource> {
        val normalized = rankedSources
            .filter { it.url.isNotBlank() }
            .distinctBy { sourceKey(it.url) }
            .take(MAX_SOURCES_PER_GROUP)
        if (normalized.isEmpty()) return emptyList()

        return synchronized(lock) {
            val existing = normalized.flatMap { source ->
                groupsBySourceKey[sourceKey(source.url)].orEmpty()
            }
            val merged = (normalized + existing)
                .distinctBy { sourceKey(it.url) }
                .take(MAX_SOURCES_PER_GROUP)

            if (groupsBySourceKey.size + merged.size > MAX_REGISTERED_KEYS) {
                groupsBySourceKey.clear()
            }
            merged.forEach { source ->
                groupsBySourceKey[sourceKey(source.url)] = merged
            }
            merged
        }
    }

    fun sourcesFor(selectedUrl: String): List<TorrentPlaybackSource> {
        val selectedKey = sourceKey(selectedUrl)
        return synchronized(lock) {
            val registered = groupsBySourceKey[selectedKey].orEmpty()
            val selected = registered.firstOrNull { sourceKey(it.url) == selectedKey }
                ?: TorrentPlaybackSource(url = selectedUrl)
            (listOf(selected) + registered)
                .distinctBy { sourceKey(it.url) }
                .take(MAX_SOURCES_PER_GROUP)
        }
    }

    fun sourceKey(url: String): String {
        val trimmed = url.trim()
        val infoHash = infoHashRegex.find(trimmed)?.groupValues?.getOrNull(1)?.lowercase()
        if (infoHash == null) return trimmed
        val selectedFile = fileSelectionRegex.find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            .orEmpty()
        return "btih:$infoHash:$selectedFile"
    }

    internal fun clearForTests() {
        synchronized(lock) { groupsBySourceKey.clear() }
    }
}