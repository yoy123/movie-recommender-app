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
 * Popcorn Time API service for fetching torrent information for TV shows.
 * Uses multiple mirror domains for redundancy.
 * 
 * API Endpoints:
 * - GET /shows - List of available pages
 * - GET /shows/{page} - List of shows (50 per page)
 * - GET /show/{imdb_id} - Show details with episodes and torrents
 */
class PopcornTvApiService {
    
    companion object {
        private val MIRROR_URLS = listOf(
            "https://tv-v2.api-fetch.website",
            "https://fusme.link",
            "https://jfper.link",
            "https://uxert.link",
            "https://yrkde.link"
        )
        
        private const val TIMEOUT_SECONDS = 15L
        private const val MAX_SEARCH_PAGES = 30  // 50 shows per page = 1500 shows
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Search for a TV show by title and optional year.
     * Searches through available pages to find matches.
     */
    suspend fun searchShow(title: String, year: String? = null): PopcornTvShow? = withContext(Dispatchers.IO) {
        val normalizedTitle = title.lowercase().trim()
        val targetYear = year?.trim()
        
        android.util.Log.d("PopcornTvApi", "Searching for TV show: $title ($year)")
        
        // Search through pages for a match
        for (page in 1..MAX_SEARCH_PAGES) {
            try {
                val shows = fetchShowsPage(page) ?: continue
                
                if (shows.isEmpty()) {
                    android.util.Log.d("PopcornTvApi", "No more shows at page $page, stopping search")
                    break
                }
                
                // Try exact match first
                val exactMatch = shows.find { show ->
                    val showTitle = show.title.lowercase().trim()
                    val yearMatch = targetYear == null || show.year == targetYear
                    showTitle == normalizedTitle && yearMatch
                }
                if (exactMatch != null) {
                    android.util.Log.d("PopcornTvApi", "Found exact match: ${exactMatch.title}")
                    return@withContext exactMatch
                }
                
                // Try contains match
                val partialMatch = shows.find { show ->
                    val showTitle = show.title.lowercase().trim()
                    val yearMatch = targetYear == null || show.year == targetYear
                    (showTitle.contains(normalizedTitle) || normalizedTitle.contains(showTitle)) && yearMatch
                }
                if (partialMatch != null) {
                    android.util.Log.d("PopcornTvApi", "Found partial match: ${partialMatch.title}")
                    return@withContext partialMatch
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PopcornTvApi", "Error fetching page $page: ${e.message}")
            }
        }
        
        android.util.Log.d("PopcornTvApi", "No show found for: $title")
        null
    }
    
    /**
     * Get detailed show information by IMDB ID, including all episodes with torrents.
     */
    suspend fun getShowDetails(imdbId: String): PopcornTvShowDetails? = withContext(Dispatchers.IO) {
        val normalizedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        
        android.util.Log.d("PopcornTvApi", "Fetching show details for: $normalizedId")
        
        for (baseUrl in MIRROR_URLS) {
            try {
                val url = "$baseUrl/show/$normalizedId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.isNotBlank() && !body.startsWith("<!")) {
                            val show = gson.fromJson(body, PopcornTvShowDetails::class.java)
                            if (show != null) {
                                android.util.Log.d("PopcornTvApi", "Got details for: ${show.title} with ${show.episodes?.size ?: 0} episodes")
                                return@withContext show
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PopcornTvApi", "Mirror $baseUrl failed: ${e.message}")
                continue
            }
        }
        
        android.util.Log.e("PopcornTvApi", "Failed to get show details for: $normalizedId")
        null
    }
    
    /**
     * Fetch a page of TV shows from the API.
     */
    suspend fun fetchShowsPage(page: Int, sort: String = "trending", genre: String? = null): List<PopcornTvShow>? = withContext(Dispatchers.IO) {
        for (baseUrl in MIRROR_URLS) {
            try {
                val urlBuilder = StringBuilder("$baseUrl/shows/$page?sort=$sort&order=-1")
                if (genre != null) {
                    urlBuilder.append("&genre=$genre")
                }
                
                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.isNotBlank() && body.startsWith("[")) {
                            val type = object : TypeToken<List<PopcornTvShow>>() {}.type
                            return@withContext gson.fromJson<List<PopcornTvShow>>(body, type)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PopcornTvApi", "Mirror $baseUrl failed for page $page: ${e.message}")
                continue
            }
        }
        null
    }
    
    /**
     * Search shows by keywords using the API's search parameter.
     */
    suspend fun searchShowsByKeywords(keywords: String): List<PopcornTvShow>? = withContext(Dispatchers.IO) {
        val encodedKeywords = java.net.URLEncoder.encode(keywords, "UTF-8")
        
        for (baseUrl in MIRROR_URLS) {
            try {
                val url = "$baseUrl/shows/1?keywords=$encodedKeywords"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.isNotBlank() && body.startsWith("[")) {
                            val type = object : TypeToken<List<PopcornTvShow>>() {}.type
                            return@withContext gson.fromJson<List<PopcornTvShow>>(body, type)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PopcornTvApi", "Search failed on $baseUrl: ${e.message}")
                continue
            }
        }
        null
    }
    
    /**
     * Get the best available torrent for a specific episode.
     * Prefers higher quality but falls back to available options.
     */
    fun getEpisodeTorrent(
        showDetails: PopcornTvShowDetails,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p"
    ): EpisodeTorrentInfo? {
        val episodeData = showDetails.episodes?.find { ep ->
            ep.season == season && ep.episode == episode
        } ?: return null
        
        val torrents = episodeData.torrents ?: return null
        
        // Try preferred quality first
        val preferredTorrent = torrents[preferredQuality]
        if (preferredTorrent != null && !preferredTorrent.url.isNullOrBlank()) {
            return EpisodeTorrentInfo(
                magnetUrl = preferredTorrent.url,
                quality = preferredQuality,
                seeds = preferredTorrent.seeds ?: 0,
                peers = preferredTorrent.peers ?: 0,
                provider = preferredTorrent.provider ?: "Unknown",
                season = season,
                episode = episode,
                episodeTitle = episodeData.title,
                showTitle = showDetails.title
            )
        }
        
        // Fallback: try other qualities in order of preference
        val qualityOrder = listOf("1080p", "720p", "480p", "0")
        for (quality in qualityOrder) {
            val torrent = torrents[quality]
            if (torrent != null && !torrent.url.isNullOrBlank()) {
                return EpisodeTorrentInfo(
                    magnetUrl = torrent.url,
                    quality = if (quality == "0") "SD" else quality,
                    seeds = torrent.seeds ?: 0,
                    peers = torrent.peers ?: 0,
                    provider = torrent.provider ?: "Unknown",
                    season = season,
                    episode = episode,
                    episodeTitle = episodeData.title,
                    showTitle = showDetails.title
                )
            }
        }
        
        return null
    }
    
    /**
     * Get all available seasons for a show.
     */
    fun getSeasons(showDetails: PopcornTvShowDetails): List<Int> {
        return showDetails.episodes
            ?.mapNotNull { it.season }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }
    
    /**
     * Get all episodes for a specific season.
     */
    fun getEpisodesForSeason(showDetails: PopcornTvShowDetails, season: Int): List<PopcornEpisode> {
        return showDetails.episodes
            ?.filter { it.season == season }
            ?.sortedBy { it.episode }
            ?: emptyList()
    }
}

/**
 * Basic TV show info from list endpoint.
 */
data class PopcornTvShow(
    @SerializedName("_id")
    val id: String,
    
    @SerializedName("imdb_id")
    val imdbId: String?,
    
    @SerializedName("tvdb_id")
    val tvdbId: String?,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("year")
    val year: String?,
    
    @SerializedName("slug")
    val slug: String?,
    
    @SerializedName("num_seasons")
    val numSeasons: Int?,
    
    @SerializedName("images")
    val images: PopcornTvImages?,
    
    @SerializedName("rating")
    val rating: PopcornTvRating?
)

/**
 * Detailed TV show info including episodes.
 */
data class PopcornTvShowDetails(
    @SerializedName("_id")
    val id: String,
    
    @SerializedName("imdb_id")
    val imdbId: String?,
    
    @SerializedName("tvdb_id")
    val tvdbId: String?,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("year")
    val year: String?,
    
    @SerializedName("slug")
    val slug: String?,
    
    @SerializedName("synopsis")
    val synopsis: String?,
    
    @SerializedName("runtime")
    val runtime: String?,
    
    @SerializedName("country")
    val country: String?,
    
    @SerializedName("network")
    val network: String?,
    
    @SerializedName("air_day")
    val airDay: String?,
    
    @SerializedName("air_time")
    val airTime: String?,
    
    @SerializedName("status")
    val status: String?,
    
    @SerializedName("num_seasons")
    val numSeasons: Int?,
    
    @SerializedName("episodes")
    val episodes: List<PopcornEpisode>?,
    
    @SerializedName("genres")
    val genres: List<String>?,
    
    @SerializedName("images")
    val images: PopcornTvImages?,
    
    @SerializedName("rating")
    val rating: PopcornTvRating?
)

/**
 * Episode data with torrents.
 */
data class PopcornEpisode(
    @SerializedName("tvdb_id")
    val tvdbId: String?,
    
    @SerializedName("season")
    val season: Int?,
    
    @SerializedName("episode")
    val episode: Int?,
    
    @SerializedName("title")
    val title: String?,
    
    @SerializedName("overview")
    val overview: String?,
    
    @SerializedName("first_aired")
    val firstAired: Long?,
    
    @SerializedName("torrents")
    val torrents: Map<String, PopcornEpisodeTorrent>?
)

/**
 * Torrent info for an episode.
 */
data class PopcornEpisodeTorrent(
    @SerializedName("url")
    val url: String?,
    
    @SerializedName("seeds")
    val seeds: Int?,
    
    @SerializedName("peers")
    val peers: Int?,
    
    @SerializedName("provider")
    val provider: String?
)

/**
 * Image URLs for a TV show.
 */
data class PopcornTvImages(
    @SerializedName("poster")
    val poster: String?,
    
    @SerializedName("fanart")
    val fanart: String?,
    
    @SerializedName("banner")
    val banner: String?
)

/**
 * Rating info for a TV show.
 */
data class PopcornTvRating(
    @SerializedName("percentage")
    val percentage: Int?,
    
    @SerializedName("votes")
    val votes: Int?,
    
    @SerializedName("watching")
    val watching: Int?
)

/**
 * Resolved torrent info for a specific episode, ready for streaming.
 */
data class EpisodeTorrentInfo(
    val magnetUrl: String,
    val quality: String,
    val seeds: Int,
    val peers: Int,
    val provider: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val showTitle: String
)
