package com.movierecommender.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * EZTV API service for fetching TV show torrent information.
 * Used as a fallback source when Popcorn Time TV API doesn't find a show.
 *
 * API: https://eztvx.to/api/
 * - No API key required (beta mode)
 * - Lookup by IMDB ID: GET /api/get-torrents?imdb_id={id}
 * - Pagination: limit (1-100), page (1-100)
 * - Returns magnet URLs, season/episode numbers, seeds/peers, file sizes
 *
 * NOTE: Requires a browser-like User-Agent header to avoid Cloudflare blocking.
 */
class EztvApiService {

    companion object {
        private val MIRROR_URLS = listOf(
            "https://eztvx.to",
            "https://eztv.re",
            "https://eztv.wf"
        )

        private const val TIMEOUT_SECONDS = 15L
        private const val MAX_RESULTS_PER_PAGE = 100
        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    /**
     * Get all torrents for a TV show by IMDB ID.
     * Fetches up to [maxPages] pages (100 torrents each).
     *
     * @param imdbId IMDB ID with or without "tt" prefix (e.g., "tt0944947" or "0944947")
     * @param maxPages Maximum number of pages to fetch (default 3 = up to 300 torrents)
     * @return List of EZTV torrents, or empty list if not found
     */
    suspend fun getTorrentsByImdbId(
        imdbId: String,
        maxPages: Int = 3
    ): List<EztvTorrent> = withContext(Dispatchers.IO) {
        // EZTV expects IMDB ID without "tt" prefix
        val cleanId = imdbId.removePrefix("tt")

        android.util.Log.d("EztvApi", "Fetching torrents for IMDB ID: $cleanId")

        val allTorrents = mutableListOf<EztvTorrent>()
        var page = 1

        while (page <= maxPages) {
            val response = fetchPage(cleanId, page) ?: break
            val torrents = response.torrents
            if (torrents.isNullOrEmpty()) break

            allTorrents.addAll(torrents)

            // If we got fewer than limit, there are no more pages
            if (torrents.size < MAX_RESULTS_PER_PAGE || allTorrents.size >= response.torrentsCount) {
                break
            }
            page++
        }

        android.util.Log.d("EztvApi", "Found ${allTorrents.size} torrents for IMDB $cleanId")
        allTorrents
    }

    /**
     * Get torrents for a specific episode.
     *
     * @param imdbId IMDB ID of the show
     * @param season Season number
     * @param episode Episode number
     * @param preferredQuality Quality preference for sorting (e.g., "720p", "1080p")
     * @return [EpisodeTorrentInfo] for the best matching torrent, or null
     */
    suspend fun getEpisodeTorrent(
        imdbId: String,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p",
        showTitle: String = ""
    ): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        val torrents = getTorrentsByImdbId(imdbId)

        // Filter to the specific episode
        val episodeTorrents = torrents.filter { t ->
            t.season?.toIntOrNull() == season && t.episode?.toIntOrNull() == episode
        }

        if (episodeTorrents.isEmpty()) {
            android.util.Log.d("EztvApi", "No torrents found for S${season}E${episode}")
            return@withContext null
        }

        // Score and sort torrents: prefer matching quality, more seeds, smaller size
        val scored = episodeTorrents.mapNotNull { t ->
            t.magnetUrl ?: return@mapNotNull null
            val quality = detectQuality(t.title ?: t.filename ?: "")
            val qualityScore = when {
                quality == preferredQuality -> 100
                quality == "1080p" -> 80
                quality == "720p" -> 70
                quality == "480p" -> 50
                else -> 30
            }
            val seedScore = (t.seeds ?: 0).coerceAtMost(100)
            // Prefer smaller files for faster streaming (cap penalty at 50)
            val sizeBytes = t.sizeBytes?.toLongOrNull() ?: Long.MAX_VALUE
            val sizeGb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
            val sizePenalty = (sizeGb * 5).toInt().coerceAtMost(50)

            val totalScore = qualityScore + seedScore - sizePenalty

            Triple(t, quality, totalScore)
        }.sortedByDescending { it.third }

        val (best, quality, _) = scored.firstOrNull() ?: return@withContext null

        android.util.Log.d("EztvApi", "Best torrent for S${season}E${episode}: $quality, ${best.seeds} seeds")

        EpisodeTorrentInfo(
            magnetUrl = best.magnetUrl!!,
            quality = quality,
            seeds = best.seeds ?: 0,
            peers = best.peers ?: 0,
            provider = "EZTV",
            season = season,
            episode = episode,
            episodeTitle = best.title,
            showTitle = showTitle
        )
    }

    /**
     * Get available seasons from EZTV torrents.
     */
    suspend fun getSeasons(imdbId: String): List<Int> = withContext(Dispatchers.IO) {
        val torrents = getTorrentsByImdbId(imdbId)
        torrents.mapNotNull { it.season?.toIntOrNull() }
            .distinct()
            .sorted()
    }

    /**
     * Get available episodes for a season from EZTV torrents.
     * Returns a list of episode numbers that have at least one torrent.
     */
    suspend fun getEpisodesForSeason(imdbId: String, season: Int): List<EztvEpisodeInfo> = withContext(Dispatchers.IO) {
        val torrents = getTorrentsByImdbId(imdbId)
        torrents.filter { it.season?.toIntOrNull() == season }
            .groupBy { it.episode?.toIntOrNull() }
            .filterKeys { it != null }
            .map { (episodeNum, episodeTorrents) ->
                EztvEpisodeInfo(
                    episode = episodeNum!!,
                    title = episodeTorrents.firstOrNull()?.title,
                    torrentCount = episodeTorrents.size,
                    bestSeeds = episodeTorrents.maxOfOrNull { it.seeds ?: 0 } ?: 0
                )
            }
            .sortedBy { it.episode }
    }

    /**
     * Find the best first-episode torrent for quick play from recommendations.
     * Prefers S01E01, falls back to any available episode.
     */
    suspend fun getFirstEpisodeTorrent(
        imdbId: String,
        preferredQuality: String = "720p",
        showTitle: String = ""
    ): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        // Try S01E01 first
        val s01e01 = getEpisodeTorrent(imdbId, 1, 1, preferredQuality, showTitle)
        if (s01e01 != null) return@withContext s01e01

        // Fallback: find any episode with torrents, preferring earliest
        val torrents = getTorrentsByImdbId(imdbId)
        val earliest = torrents.filter { t ->
            t.magnetUrl != null && (t.seeds ?: 0) > 0
        }.minByOrNull { t ->
            val s = t.season?.toIntOrNull() ?: 999
            val e = t.episode?.toIntOrNull() ?: 999
            s * 1000 + e
        } ?: return@withContext null

        val season = earliest.season?.toIntOrNull() ?: 1
        val episode = earliest.episode?.toIntOrNull() ?: 1
        val quality = detectQuality(earliest.title ?: earliest.filename ?: "")

        EpisodeTorrentInfo(
            magnetUrl = earliest.magnetUrl!!,
            quality = quality,
            seeds = earliest.seeds ?: 0,
            peers = earliest.peers ?: 0,
            provider = "EZTV",
            season = season,
            episode = episode,
            episodeTitle = earliest.title,
            showTitle = showTitle
        )
    }

    // ── Private helpers ──

    private suspend fun fetchPage(imdbId: String, page: Int): EztvResponse? = withContext(Dispatchers.IO) {
        for (baseUrl in MIRROR_URLS) {
            try {
                val url = "$baseUrl/api/get-torrents?imdb_id=$imdbId&limit=$MAX_RESULTS_PER_PAGE&page=$page"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.isNotBlank() && body.startsWith("{")) {
                            return@withContext gson.fromJson(body, EztvResponse::class.java)
                        }
                    } else {
                        android.util.Log.w("EztvApi", "HTTP ${response.code} from $baseUrl")
                    }
                    Unit
                }
            } catch (e: Exception) {
                android.util.Log.w("EztvApi", "Mirror $baseUrl failed: ${e.message}")
                continue
            }
        }
        null
    }

    /**
     * Detect video quality from torrent title/filename.
     */
    private fun detectQuality(title: String): String {
        val upper = title.uppercase()
        return when {
            "2160P" in upper || "4K" in upper || "UHD" in upper -> "2160p"
            "1080P" in upper -> "1080p"
            "720P" in upper -> "720p"
            "480P" in upper -> "480p"
            "HDTV" in upper -> "720p" // HDTV is typically 720p
            else -> "SD"
        }
    }
}

// ── Data models ──

/**
 * Top-level EZTV API response.
 */
data class EztvResponse(
    @SerializedName("imdb_id")
    val imdbId: String?,

    @SerializedName("torrents_count")
    val torrentsCount: Int = 0,

    @SerializedName("limit")
    val limit: Int = 100,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("torrents")
    val torrents: List<EztvTorrent>?
)

/**
 * Individual torrent entry from EZTV API.
 */
data class EztvTorrent(
    @SerializedName("id")
    val id: Long?,

    @SerializedName("hash")
    val hash: String?,

    @SerializedName("filename")
    val filename: String?,

    @SerializedName("magnet_url")
    val magnetUrl: String?,

    @SerializedName("title")
    val title: String?,

    @SerializedName("imdb_id")
    val imdbId: String?,

    @SerializedName("season")
    val season: String?,

    @SerializedName("episode")
    val episode: String?,

    @SerializedName("seeds")
    val seeds: Int?,

    @SerializedName("peers")
    val peers: Int?,

    @SerializedName("date_released_unix")
    val dateReleasedUnix: Long?,

    @SerializedName("size_bytes")
    val sizeBytes: String?,

    @SerializedName("small_screenshot")
    val smallScreenshot: String?,

    @SerializedName("large_screenshot")
    val largeScreenshot: String?
)

/**
 * Simplified episode info from EZTV (for episode listing in EpisodePickerDialog).
 */
data class EztvEpisodeInfo(
    val episode: Int,
    val title: String?,
    val torrentCount: Int,
    val bestSeeds: Int
)
