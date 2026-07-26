package com.movierecommender.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Searches one or more Torznab-compatible endpoints such as Jackett or Prowlarr.
 *
 * Configuration format:
 *   Name|https://host/torznab/api|apiKey;Second|https://host/torznab/api|apiKey
 */
class TorznabService(config: String) {

    companion object {
        private const val TIMEOUT_SECONDS = 15L
        private const val MOVIE_CATEGORY = "2000"
        private const val TV_CATEGORY = "5000"
        private const val SHOW_INVENTORY_LIMIT = 100
        private const val INVENTORY_CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private val sources = TorznabSourceConfig.parse(config)
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val episodeInventoryCache = ConcurrentHashMap<String, CachedTorznabInventory>()

    val isConfigured: Boolean
        get() = sources.isNotEmpty()

    suspend fun searchMovie(title: String, year: String?, imdbId: String?): TorrentInfo? {
        val match = search(
            TorznabQuery(
                type = "movie",
                query = listOfNotNull(title, year).joinToString(" "),
                category = MOVIE_CATEGORY,
                imdbId = imdbId
            ),
            title = title,
            year = year
        ) ?: return null

        return TorrentInfo(
            magnetUrl = match.result.magnetUrl,
            quality = match.result.quality,
            seeds = match.result.seeders,
            peers = match.result.peers,
            size = formatFileSize(match.result.sizeBytes),
            filesize = formatFileSize(match.result.sizeBytes),
            provider = "Torznab (${match.source.name})"
        )
    }

    suspend fun searchEpisode(
        showTitle: String,
        imdbId: String?,
        season: Int,
        episode: Int,
        preferredQuality: String
    ): EpisodeTorrentInfo? {
        val match = search(
            TorznabQuery(
                type = "tvsearch",
                query = showTitle,
                category = TV_CATEGORY,
                imdbId = imdbId,
                season = season,
                episode = episode
            ),
            title = showTitle,
            preferredQuality = preferredQuality
        ) ?: return null

        return EpisodeTorrentInfo(
            magnetUrl = match.result.magnetUrl,
            quality = match.result.quality,
            seeds = match.result.seeders,
            peers = match.result.peers,
            provider = "Torznab (${match.source.name})",
            season = season,
            episode = episode,
            episodeTitle = null,
            showTitle = showTitle
        )
    }

    /**
     * Enumerates episodes exposed by every configured Torznab endpoint.
     * Results are cached briefly because the season picker and episode picker
     * request the same show inventory in succession.
     */
    suspend fun getShowEpisodes(imdbId: String): List<TorznabEpisodeInfo> = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext emptyList()

        val normalizedImdbId = imdbId.lowercase().removePrefix("tt")
        val now = System.currentTimeMillis()
        episodeInventoryCache[normalizedImdbId]
            ?.takeIf { now - it.createdAtMs < INVENTORY_CACHE_TTL_MS }
            ?.let { return@withContext it.episodes }

        val matches = fetchMatches(
            TorznabQuery(
                type = "tvsearch",
                query = "",
                category = TV_CATEGORY,
                imdbId = imdbId,
                limit = SHOW_INVENTORY_LIMIT
            )
        )
        val episodes = TorznabEpisodeInventory.aggregate(matches)
        episodeInventoryCache[normalizedImdbId] = CachedTorznabInventory(now, episodes)
        episodes
    }

    private suspend fun search(
        query: TorznabQuery,
        title: String,
        year: String? = null,
        preferredQuality: String? = null
    ): TorznabMatch? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext null

        val matches = fetchMatches(query)

        matches
            .filter { it.result.seeders > 0 || it.result.peers > 0 }
            .maxByOrNull { score(it.result, title, year, preferredQuality) }
    }

    private suspend fun fetchMatches(query: TorznabQuery): List<TorznabMatch> = coroutineScope {
        sources.map { source ->
            async {
                try {
                    fetch(source, query).map { TorznabMatch(source, it) }
                } catch (e: Exception) {
                    android.util.Log.w("Torznab", "${source.name} search failed: ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private fun fetch(source: TorznabSource, query: TorznabQuery): List<TorznabResult> {
        val baseUrl = source.endpoint.toHttpUrlOrNull() ?: return emptyList()
        val url = baseUrl.newBuilder()
            .addQueryParameter("t", query.type)
            .addQueryParameter("cat", query.category)
            .apply {
                if (query.query.isNotBlank()) addQueryParameter("q", query.query)
                if (source.apiKey.isNotBlank()) addQueryParameter("apikey", source.apiKey)
                if (!query.imdbId.isNullOrBlank()) addQueryParameter("imdbid", query.imdbId)
                query.season?.let { addQueryParameter("season", it.toString()) }
                query.episode?.let { addQueryParameter("ep", it.toString()) }
                query.limit?.let { addQueryParameter("limit", it.toString()) }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .build()

        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }

        return TorznabFeedParser.parse(xml)
    }

    private fun score(
        result: TorznabResult,
        title: String,
        year: String?,
        preferredQuality: String?
    ): Int {
        val normalizedName = result.title.lowercase()
        val titleMatches = title.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .count(normalizedName::contains)

        var score = result.seeders + titleMatches * 100
        if (year != null && normalizedName.contains(year)) score += 500
        if (preferredQuality != null && result.quality.equals(preferredQuality, ignoreCase = true)) {
            score += 300
        }
        if (result.quality == "1080p") score += 200
        if (result.quality == "720p") score += 150
        if (result.quality == "CAM") score -= 500
        return score
    }

    private fun formatFileSize(bytes: Long?): String? {
        bytes ?: return null
        return when {
            bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024L * 1024L))
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024L * 1024L))
            bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024L)
            else -> "$bytes B"
        }
    }
}

internal object TorznabSourceConfig {
    fun parse(config: String): List<TorznabSource> {
        return config.split(';').mapNotNull { entry ->
            val parts = entry.split('|', limit = 3).map(String::trim)
            if (parts.size < 2 || parts[0].isBlank() || parts[1].toHttpUrlOrNull() == null) {
                null
            } else {
                TorznabSource(
                    name = parts[0],
                    endpoint = parts[1],
                    apiKey = parts.getOrNull(2).orEmpty()
                )
            }
        }
    }
}

internal object TorznabFeedParser {
    fun parse(xml: String): List<TorznabResult> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setExpandEntityReferences(false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val items = document.getElementsByTagName("item")

        return buildList {
            for (index in 0 until items.length) {
                val item = items.item(index) as? Element ?: continue
                addAll(parseItems(item))
            }
        }
    }

    private fun parseItems(item: Element): List<TorznabResult> {
        val title = item.firstText("title")?.trim().orEmpty()
        if (title.isBlank()) return emptyList()

        val attributes = mutableMapOf<String, String>()
        val descendants = item.getElementsByTagName("*")
        for (index in 0 until descendants.length) {
            val element = descendants.item(index) as? Element ?: continue
            if (element.localName == "attr" || element.tagName.endsWith(":attr")) {
                val name = element.getAttribute("name").lowercase()
                val value = element.getAttribute("value")
                if (name.isNotBlank() && value.isNotBlank()) attributes[name] = value
            }
        }

        val directMagnet = sequenceOf(
            attributes["magneturl"],
            attributes["magnet"],
            item.firstText("link"),
            item.firstText("guid"),
            item.firstEnclosureUrl()
        ).filterNotNull().firstOrNull { it.startsWith("magnet:?", ignoreCase = true) }

        val magnetUrl = directMagnet ?: attributes["infohash"]
            ?.takeIf { it.matches(Regex("[A-Fa-f0-9]{40}|[A-Za-z2-7]{32}")) }
            ?.let { hash ->
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                "magnet:?xt=urn:btih:$hash&dn=$encodedTitle"
            }
            ?: return emptyList()

        val sizeBytes = attributes["size"]?.toLongOrNull()
            ?: item.firstText("size")?.trim()?.toLongOrNull()
        val episodeKeys = extractEpisodeKeys(attributes["season"], attributes["episode"], title)
            .ifEmpty { listOf(null to null) }

        return episodeKeys.map { (season, episode) ->
            TorznabResult(
                title = title,
                magnetUrl = magnetUrl.replace("&amp;", "&"),
                seeders = attributes["seeders"]?.toIntOrNull() ?: 0,
                peers = attributes["peers"]?.toIntOrNull()
                    ?: attributes["leechers"]?.toIntOrNull()
                    ?: 0,
                sizeBytes = sizeBytes,
                quality = detectQuality(title),
                season = season,
                episode = episode
            )
        }
    }

    internal fun extractEpisodeKeys(
        seasonAttribute: String?,
        episodeAttribute: String?,
        title: String
    ): List<Pair<Int, Int>> {
        val attributeSeason = seasonAttribute?.toIntOrNull()
        val attributeEpisodes = episodeAttribute
            ?.let { Regex("\\d+").findAll(it).mapNotNull { match -> match.value.toIntOrNull() }.toList() }
            .orEmpty()
        if (attributeSeason != null && attributeEpisodes.isNotEmpty()) {
            return attributeEpisodes.map { attributeSeason to it }.distinct()
        }

        val keys = mutableListOf<Pair<Int, Int>>()
        val seasonBlocks = Regex(
            """\bS(\d{1,2})((?:[ ._-]*E\d{1,3})+)""",
            RegexOption.IGNORE_CASE
        )
        seasonBlocks.findAll(title).forEach { block ->
            val season = block.groupValues[1].toIntOrNull() ?: return@forEach
            Regex("""E(\d{1,3})""", RegexOption.IGNORE_CASE)
                .findAll(block.groupValues[2])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .forEach { episode -> keys += season to episode }
        }
        Regex("""\b(\d{1,2})x(\d{1,3})\b""", RegexOption.IGNORE_CASE)
            .findAll(title)
            .forEach { match ->
                val season = match.groupValues[1].toIntOrNull() ?: return@forEach
                val episode = match.groupValues[2].toIntOrNull() ?: return@forEach
                keys += season to episode
            }
        return keys.distinct()
    }

    private fun Element.firstText(tagName: String): String? {
        return getElementsByTagName(tagName).item(0)?.textContent
    }

    private fun Element.firstEnclosureUrl(): String? {
        return (getElementsByTagName("enclosure").item(0) as? Element)?.getAttribute("url")
    }

    private fun detectQuality(name: String): String {
        val normalizedName = name.lowercase()
        return when {
            normalizedName.contains("2160p") || normalizedName.contains("4k") -> "2160p"
            normalizedName.contains("1080p") -> "1080p"
            normalizedName.contains("720p") -> "720p"
            normalizedName.contains("480p") -> "480p"
            normalizedName.contains("bluray") || normalizedName.contains("bdrip") -> "BluRay"
            normalizedName.contains("webrip") || normalizedName.contains("web-dl") -> "WEB"
            normalizedName.contains("hdtv") -> "HDTV"
            normalizedName.contains("cam") || normalizedName.contains("hdcam") -> "CAM"
            else -> "Unknown"
        }
    }
}

internal data class TorznabSource(
    val name: String,
    val endpoint: String,
    val apiKey: String
)

internal data class TorznabResult(
    val title: String,
    val magnetUrl: String,
    val seeders: Int,
    val peers: Int,
    val sizeBytes: Long?,
    val quality: String,
    val season: Int?,
    val episode: Int?
)

private data class TorznabQuery(
    val type: String,
    val query: String,
    val category: String,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val limit: Int? = null
)

internal data class TorznabMatch(
    val source: TorznabSource,
    val result: TorznabResult
)

internal object TorznabEpisodeInventory {
    fun aggregate(matches: List<TorznabMatch>): List<TorznabEpisodeInfo> {
        return matches
            .filter { match ->
                val season = match.result.season
                val episode = match.result.episode
                season != null && season >= 0 && episode != null && episode > 0
            }
            .groupBy { requireNotNull(it.result.season) to requireNotNull(it.result.episode) }
            .map { (key, episodeMatches) ->
                val torrents = episodeMatches
                    .sortedWith(
                        compareByDescending<TorznabMatch> { it.result.seeders }
                            .thenByDescending { it.result.peers }
                    )
                    .distinctBy { magnetIdentity(it.result.magnetUrl) }
                    .map { match ->
                        TorznabEpisodeTorrent(
                            magnetUrl = match.result.magnetUrl,
                            quality = match.result.quality,
                            seeds = match.result.seeders,
                            peers = match.result.peers,
                            provider = "Torznab (${match.source.name})",
                            releaseTitle = match.result.title
                        )
                    }
                TorznabEpisodeInfo(
                    season = key.first,
                    episode = key.second,
                    torrents = torrents
                )
            }
            .sortedWith(compareBy<TorznabEpisodeInfo> { it.season }.thenBy { it.episode })
    }

    private fun magnetIdentity(magnetUrl: String): String {
        return Regex("""(?i)xt=urn:btih:([^&]+)""")
            .find(magnetUrl)
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?: magnetUrl
    }
}

data class TorznabEpisodeInfo(
    val season: Int,
    val episode: Int,
    val torrents: List<TorznabEpisodeTorrent>
)

data class TorznabEpisodeTorrent(
    val magnetUrl: String,
    val quality: String,
    val seeds: Int,
    val peers: Int,
    val provider: String,
    val releaseTitle: String
)

private data class CachedTorznabInventory(
    val createdAtMs: Long,
    val episodes: List<TorznabEpisodeInfo>
)