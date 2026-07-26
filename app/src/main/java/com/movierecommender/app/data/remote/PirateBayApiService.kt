package com.movierecommender.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ThePirateBay API service using the apibay.org JSON API.
 * Returns torrent info including info_hash for magnet URL construction.
 *
 * API Endpoints:
 *   - Search:  GET https://apibay.org/q.php?q={query}&cat={category}
 *   - Top:     GET https://apibay.org/precompiled/data_top100_{category}.json
 *
 * Category codes:
 *   - 201 = Movies
 *   - 205 = TV Shows
 *   - 207 = HD Movies
 *   - 208 = HD TV Shows
 *
 * Supports IMDB ID search: q=tt{imdbid}
 */
class PirateBayApiService {

    companion object {
        private const val BASE_URL = "https://apibay.org"
        private const val TIMEOUT_SECONDS = 15L

        // PirateBay category codes
        private const val CAT_MOVIES = "201"
        private const val CAT_TV = "205"
        private const val CAT_HD_MOVIES = "207"
        private const val CAT_HD_TV = "208"

        private val TRACKERS = listOf(
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://tracker.openbittorrent.com:80",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://p4p.arenabg.com:1337",
            "udp://tracker.internetwarriors.net:1337",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Search for a movie by title and optional year.
     * Searches HD Movies (cat 207) first, falls back to all Movies (cat 201).
     */
    suspend fun searchMovie(title: String, year: String?): TorrentInfo? = withContext(Dispatchers.IO) {
        val query = if (year != null) "$title $year" else title
        
        // Try HD movies first, then all movies
        for (cat in listOf(CAT_HD_MOVIES, CAT_MOVIES)) {
            try {
                val results = search(query, cat)
                val best = pickBestResult(results, title, year)
                if (best != null) {
                    return@withContext best
                }
            } catch (e: Exception) {
                android.util.Log.w("PirateBayApi", "Search failed (cat=$cat): ${e.message}")
            }
        }

        null
    }

    /**
     * Search for a movie by IMDB ID.
     * PirateBay supports IMDB ID search directly.
     */
    suspend fun searchByImdbId(imdbId: String): TorrentInfo? = withContext(Dispatchers.IO) {
        val normalizedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        
        try {
            val results = search(normalizedId, CAT_HD_MOVIES)
            if (results.isNotEmpty() && results[0].name != "No results returned") {
                return@withContext pickBestResult(results, "", null)
            }
            
            // Fallback to all movies category
            val allResults = search(normalizedId, CAT_MOVIES)
            if (allResults.isNotEmpty() && allResults[0].name != "No results returned") {
                return@withContext pickBestResult(allResults, "", null)
            }
        } catch (e: Exception) {
            android.util.Log.w("PirateBayApi", "IMDB search failed: ${e.message}")
        }

        null
    }

    /**
     * Enumerate episode releases from both HD and standard TV categories.
     * Title and IMDb searches are combined because mirrors vary in IMDb indexing quality.
     */
    suspend fun getShowEpisodes(title: String, imdbId: String?): List<PirateBayEpisodeInfo> =
        withContext(Dispatchers.IO) {
            val queries = buildList {
                if (!imdbId.isNullOrBlank()) {
                    add(if (imdbId.startsWith("tt")) imdbId else "tt$imdbId")
                }
                if (title.isNotBlank()) add(title)
            }.distinct()

            val torrents = mutableListOf<PirateBayTorrent>()
            for (query in queries) {
                for (category in listOf(CAT_HD_TV, CAT_TV)) {
                    try {
                        torrents += search(query, category)
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "PirateBayApi",
                            "TV inventory failed (cat=$category): ${e.message}"
                        )
                    }
                }
            }

            PirateBayEpisodeParser.aggregate(
                torrents = torrents.distinctBy { it.infoHash.lowercase() },
                provider = "PirateBay"
            ) { torrent -> torrent.toEpisodeTorrentInfo() }
        }

    suspend fun searchEpisode(
        title: String,
        imdbId: String?,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p"
    ): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        val episodeToken = "S%02dE%02d".format(season, episode)
        val targetedResults = mutableListOf<PirateBayTorrent>()
        for (category in listOf(CAT_HD_TV, CAT_TV)) {
            try {
                targetedResults += search("$title $episodeToken", category)
            } catch (e: Exception) {
                android.util.Log.w(
                    "PirateBayApi",
                    "Episode search failed (cat=$category): ${e.message}"
                )
            }
        }

        val targeted = PirateBayEpisodeParser.aggregate(
            torrents = targetedResults.distinctBy { it.infoHash.lowercase() },
            provider = "PirateBay"
        ) { torrent -> torrent.toEpisodeTorrentInfo() }
            .firstOrNull { it.season == season && it.episode == episode }

        val candidates = targeted?.torrents
            ?: getShowEpisodes(title, imdbId)
                .firstOrNull { it.season == season && it.episode == episode }
                ?.torrents
            ?: return@withContext null

        val best = candidates.maxByOrNull { torrent ->
            val qualityBonus = when (torrent.quality) {
                preferredQuality -> 300
                "1080p" -> 200
                "720p" -> 150
                else -> 0
            }
            qualityBonus + torrent.seeds
        } ?: return@withContext null

        EpisodeTorrentInfo(
            magnetUrl = best.magnetUrl,
            quality = best.quality,
            seeds = best.seeds,
            peers = best.peers,
            provider = best.provider,
            season = season,
            episode = episode,
            episodeTitle = best.releaseTitle,
            showTitle = title
        )
    }

    /**
     * Perform a raw search against the apibay.org API.
     */
    private fun search(query: String, category: String): List<PirateBayTorrent> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/q.php?q=$encodedQuery&cat=$category"

        android.util.Log.d("PirateBayApi", "Searching: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val type = object : TypeToken<List<PirateBayTorrent>>() {}.type
                    return gson.fromJson(body, type) ?: emptyList()
                }
            }
        }

        return emptyList()
    }

    /**
     * Pick the best torrent from search results.
     * Prefers: has seeders > 0, smaller file size, title match.
     */
    private fun pickBestResult(results: List<PirateBayTorrent>, title: String, year: String?): TorrentInfo? {
        if (results.isEmpty()) return null

        // Filter out "no results" sentinel
        val valid = results.filter {
            it.name != "No results returned" && (it.seeders?.toIntOrNull() ?: 0) > 0
        }
        if (valid.isEmpty()) return null

        // If we have a title to match against, prefer results that contain the title words
        val normalizedTitle = title.lowercase().trim()
        val scored = valid.map { torrent ->
            val name = torrent.name.lowercase()
            var score = (torrent.seeders?.toIntOrNull() ?: 0)
            
            // Bonus for title word matches
            if (normalizedTitle.isNotEmpty()) {
                val titleWords = normalizedTitle.split(" ").filter { it.length > 2 }
                val matchCount = titleWords.count { name.contains(it) }
                score += matchCount * 100
            }
            
            // Bonus for year match
            if (year != null && name.contains(year)) {
                score += 500
            }
            
            // Prefer 1080p/720p over cam/ts
            if (name.contains("1080p")) score += 200
            if (name.contains("720p")) score += 150
            if (name.contains("bluray") || name.contains("bdrip") || name.contains("brrip")) score += 100
            
            // Penalize cam/ts/screener
            if (name.contains("cam") || name.contains("hdcam")) score -= 500
            if (name.contains("telesync") || name.contains(".ts.")) score -= 500
            if (name.contains("screener") || name.contains("scr")) score -= 300
            
            torrent to score
        }

        val best = scored.maxByOrNull { it.second }?.first ?: return null
        return best.toTorrentInfo()
    }

    /**
     * Convert a PirateBay result to the common TorrentInfo format.
     */
    private fun PirateBayTorrent.toTorrentInfo(): TorrentInfo {
        val magnetUrl = buildMagnetUrl(infoHash, name)
        val sizeStr = formatFileSize(size?.toLongOrNull() ?: 0L)
        val qualityStr = detectQuality(name)

        return TorrentInfo(
            magnetUrl = magnetUrl,
            quality = qualityStr,
            seeds = seeders?.toIntOrNull(),
            peers = leechers?.toIntOrNull(),
            size = sizeStr,
            filesize = sizeStr,
            provider = "PirateBay"
        )
    }

    private fun PirateBayTorrent.toEpisodeTorrentInfo(): PirateBayEpisodeTorrent {
        return PirateBayEpisodeTorrent(
            magnetUrl = buildMagnetUrl(infoHash, name),
            quality = detectQuality(name),
            seeds = seeders?.toIntOrNull() ?: 0,
            peers = leechers?.toIntOrNull() ?: 0,
            provider = "PirateBay",
            releaseTitle = name
        )
    }

    /**
     * Build a magnet URL from info_hash.
     */
    private fun buildMagnetUrl(hash: String, name: String): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val trackerParams = TRACKERS.joinToString("") { "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        return "magnet:?xt=urn:btih:$hash&dn=$encodedName$trackerParams"
    }

    /**
     * Format byte count to human-readable size string.
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024L * 1024L))
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024L * 1024L))
            bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024L)
            else -> "$bytes B"
        }
    }

    /**
     * Detect quality from torrent name.
     */
    private fun detectQuality(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "2160p"
            lower.contains("1080p") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("480p") -> "480p"
            lower.contains("bluray") || lower.contains("bdrip") -> "BluRay"
            lower.contains("webrip") || lower.contains("web-dl") || lower.contains("webdl") -> "WEB"
            lower.contains("hdtv") -> "HDTV"
            lower.contains("dvdrip") -> "DVDRip"
            lower.contains("cam") || lower.contains("hdcam") -> "CAM"
            else -> "Unknown"
        }
    }
}

internal object PirateBayEpisodeParser {
    fun aggregate(
        torrents: List<PirateBayTorrent>,
        provider: String,
        convert: (PirateBayTorrent) -> PirateBayEpisodeTorrent
    ): List<PirateBayEpisodeInfo> {
        return torrents
            .flatMap { torrent ->
                extractEpisodeKeys(torrent.name).map { key -> key to torrent }
            }
            .groupBy({ it.first }, { it.second })
            .map { (key, episodeTorrents) ->
                PirateBayEpisodeInfo(
                    season = key.first,
                    episode = key.second,
                    torrents = episodeTorrents
                        .filter { (it.seeders?.toIntOrNull() ?: 0) > 0 }
                        .distinctBy { it.infoHash.lowercase() }
                        .map(convert)
                        .sortedWith(
                            compareByDescending<PirateBayEpisodeTorrent> { it.seeds }
                                .thenByDescending { it.peers }
                        )
                        .map { it.copy(provider = provider) }
                )
            }
            .filter { it.torrents.isNotEmpty() }
            .sortedWith(compareBy<PirateBayEpisodeInfo> { it.season }.thenBy { it.episode })
    }

    internal fun extractEpisodeKeys(title: String): List<Pair<Int, Int>> {
        val keys = mutableListOf<Pair<Int, Int>>()
        Regex("""\bS(\d{1,2})[ ._-]*E(\d{1,3})\b""", RegexOption.IGNORE_CASE)
            .findAll(title)
            .forEach { match ->
                val season = match.groupValues[1].toIntOrNull() ?: return@forEach
                val episode = match.groupValues[2].toIntOrNull() ?: return@forEach
                keys += season to episode
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
}

data class PirateBayEpisodeInfo(
    val season: Int,
    val episode: Int,
    val torrents: List<PirateBayEpisodeTorrent>
)

data class PirateBayEpisodeTorrent(
    val magnetUrl: String,
    val quality: String,
    val seeds: Int,
    val peers: Int,
    val provider: String,
    val releaseTitle: String
)

/**
 * Raw torrent result from apibay.org JSON API.
 */
data class PirateBayTorrent(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("info_hash")
    val infoHash: String,

    @SerializedName("leechers")
    val leechers: String?,

    @SerializedName("seeders")
    val seeders: String?,

    @SerializedName("num_files")
    val numFiles: String?,

    @SerializedName("size")
    val size: String?,

    @SerializedName("username")
    val username: String?,

    @SerializedName("added")
    val added: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("category")
    val category: String?,

    @SerializedName("imdb")
    val imdb: String?
)
