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
 * Popcorn Time API service for fetching torrent information for movies.
 * Uses multiple mirror domains for redundancy.
 */
class PopcornApiService {
    
    companion object {
        private val MIRROR_URLS = listOf(
            "https://fusme.link",
            "https://jfper.link",
            "https://uxert.link",
            "https://yrkde.link"
        )
        
        private const val TIMEOUT_SECONDS = 15L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Search for a movie by title and year to get torrent information.
     * Searches through all movies in the API to find matches.
     */
    suspend fun searchMovie(title: String, year: String?): PopcornMovie? = withContext(Dispatchers.IO) {
        val normalizedTitle = title.lowercase().trim()
        val targetYear = year?.trim()
        
        // Search through first 10 pages (500 movies) for a match
        for (page in 1..10) {
            try {
                val movies = fetchMoviesPage(page) ?: continue
                
                // Try exact match first
                val exactMatch = movies.find { movie ->
                    val movieTitle = movie.title.lowercase().trim()
                    val yearMatch = targetYear == null || movie.year == targetYear
                    movieTitle == normalizedTitle && yearMatch
                }
                if (exactMatch != null) return@withContext exactMatch
                
                // Try contains match
                val partialMatch = movies.find { movie ->
                    val movieTitle = movie.title.lowercase().trim()
                    val yearMatch = targetYear == null || movie.year == targetYear
                    (movieTitle.contains(normalizedTitle) || normalizedTitle.contains(movieTitle)) && yearMatch
                }
                if (partialMatch != null) return@withContext partialMatch
                
            } catch (e: Exception) {
                android.util.Log.e("PopcornApiService", "Error fetching page $page: ${e.message}")
            }
        }
        
        null
    }
    
    /**
     * Search by IMDB ID for more accurate matching.
     */
    suspend fun searchByImdbId(imdbId: String): PopcornMovie? = withContext(Dispatchers.IO) {
        val normalizedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        
        for (page in 1..20) {
            try {
                val movies = fetchMoviesPage(page) ?: continue
                val match = movies.find { it.imdbId == normalizedId }
                if (match != null) return@withContext match
            } catch (e: Exception) {
                android.util.Log.e("PopcornApiService", "Error searching by IMDB ID: ${e.message}")
            }
        }
        
        null
    }
    
    /**
     * Fetch a page of movies from the API.
     */
    private suspend fun fetchMoviesPage(page: Int): List<PopcornMovie>? = withContext(Dispatchers.IO) {
        for (baseUrl in MIRROR_URLS) {
            try {
                val url = "$baseUrl/movies/$page"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val type = object : TypeToken<List<PopcornMovie>>() {}.type
                            return@withContext gson.fromJson<List<PopcornMovie>>(body, type)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PopcornApiService", "Mirror $baseUrl failed: ${e.message}")
                continue
            }
        }
        null
    }
    
    /**
     * Pick the best available torrent for streaming.
     *
     * Popcorn often lists multiple qualities, and the smallest file can have a
     * dead swarm. Prefer torrents with live seeds/peers, then break ties with a
     * streaming-friendly quality and smaller size.
     */
    fun getSmallestTorrent(movie: PopcornMovie): TorrentInfo? {
        val torrents = movie.torrents?.get("en") ?: return null

        val allTorrents = torrents.values.toList()
        val healthyTorrents = allTorrents.filter { (it.seeds ?: 0) > 0 || (it.peers ?: 0) > 0 }
        val candidatePool = if (healthyTorrents.isNotEmpty()) healthyTorrents else allTorrents

        return candidatePool.maxByOrNull { torrent ->
            val sizePenalty = (parseSizeToBytes(torrent.size ?: torrent.filesize ?: "999 GB") / (1024.0 * 1024.0 * 1024.0) * 4).toInt()
            val qualityBonus = when (torrent.quality) {
                "720p" -> 90
                "1080p" -> 80
                "2160p" -> 40
                "480p" -> 50
                else -> 20
            }
            ((torrent.seeds ?: 0) * 100) + ((torrent.peers ?: 0) * 10) + qualityBonus - sizePenalty
        }
    }
    
    /**
     * Parse file size string to bytes for comparison.
     */
    private fun parseSizeToBytes(size: String): Long {
        return try {
            val parts = size.trim().split(" ")
            if (parts.size >= 2) {
                val value = parts[0].toDoubleOrNull() ?: return Long.MAX_VALUE
                val unit = parts[1].uppercase()
                
                val multiplier = when {
                    unit.startsWith("GB") -> 1024L * 1024L * 1024L
                    unit.startsWith("MB") -> 1024L * 1024L
                    unit.startsWith("KB") -> 1024L
                    else -> 1L
                }
                
                (value * multiplier).toLong()
            } else {
                Long.MAX_VALUE
            }
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }
}

/**
 * Movie data from Popcorn Time API.
 */
data class PopcornMovie(
    @SerializedName("_id")
    val id: String,
    
    @SerializedName("imdb_id")
    val imdbId: String,
    
    @SerializedName("tmdb_id")
    val tmdbId: Int?,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("year")
    val year: String,
    
    @SerializedName("synopsis")
    val synopsis: String?,
    
    @SerializedName("runtime")
    val runtime: String?,
    
    @SerializedName("genres")
    val genres: List<String>?,
    
    @SerializedName("images")
    val images: PopcornImages?,
    
    @SerializedName("rating")
    val rating: PopcornRating?,
    
    @SerializedName("torrents")
    val torrents: Map<String, Map<String, TorrentInfo>>?,
    
    @SerializedName("trailer")
    val trailer: String?
)

data class PopcornImages(
    @SerializedName("poster")
    val poster: String?,
    
    @SerializedName("fanart")
    val fanart: String?,
    
    @SerializedName("banner")
    val banner: String?
)

data class PopcornRating(
    @SerializedName("percentage")
    val percentage: Int?,
    
    @SerializedName("votes")
    val votes: Int?
)

/**
 * Torrent information including magnet link and quality details.
 */
data class TorrentInfo(
    @SerializedName("url")
    val magnetUrl: String,
    
    @SerializedName("quality")
    val quality: String?,
    
    @SerializedName("seed")
    val seeds: Int?,
    
    @SerializedName("peer")
    val peers: Int?,
    
    @SerializedName("size")
    val size: String?,
    
    @SerializedName("filesize")
    val filesize: String?,
    
    @SerializedName("provider")
    val provider: String?
)
