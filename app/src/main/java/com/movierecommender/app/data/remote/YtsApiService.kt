package com.movierecommender.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * YTS (YIFY) API service for fetching torrent information for movies.
 * YTS specializes in smaller file sizes with good quality.
 */
class YtsApiService {
    
    companion object {
        private const val BASE_URL = "https://yts.lt/api/v2"
        private const val TIMEOUT_SECONDS = 15L
        
        private val TRACKERS = listOf(
            "udp://glotorrents.pw:6969/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://torrent.gresille.org:80/announce",
            "udp://tracker.openbittorrent.com:80",
            "udp://tracker.coppersurfer.tk:6969",
            "udp://tracker.leechers-paradise.org:6969",
            "udp://p4p.arenabg.ch:1337",
            "udp://tracker.internetwarriors.net:1337"
        )
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Search for a movie by title and optional year.
     */
    suspend fun searchMovie(title: String, year: String?): YtsMovie? = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("$BASE_URL/list_movies.json?query_term=")
                append(java.net.URLEncoder.encode(title, "UTF-8"))
                if (year != null) {
                    append("&year=$year")
                }
                append("&limit=1")
            }
            
            android.util.Log.d("YtsApiService", "Searching: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val result = gson.fromJson(body, YtsResponse::class.java)
                        val movie = result.data?.movies?.firstOrNull()
                        
                        if (movie != null) {
                            android.util.Log.d("YtsApiService", "Found: ${movie.title} (${movie.year})")
                        } else {
                            android.util.Log.d("YtsApiService", "No results for: $title")
                        }
                        
                        return@withContext movie
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YtsApiService", "Error searching YTS", e)
        }
        
        null
    }
    
    /**
     * Search by IMDB ID for accurate matching.
     */
    suspend fun searchByImdbId(imdbId: String): YtsMovie? = withContext(Dispatchers.IO) {
        try {
            val normalizedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
            val url = "$BASE_URL/list_movies.json?query_term=$normalizedId&limit=1"
            
            android.util.Log.d("YtsApiService", "Searching by IMDB: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val result = gson.fromJson(body, YtsResponse::class.java)
                        return@withContext result.data?.movies?.firstOrNull()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YtsApiService", "Error searching YTS by IMDB", e)
        }
        
        null
    }
    
    /**
     * Pick the best torrent for streaming.
     *
     * The smallest file is not always the healthiest swarm. Prefer torrents that
     * still have seeds/peers, then break ties with streaming-friendly quality and
     * smaller size.
     */
    fun getSmallestTorrent(movie: YtsMovie): TorrentInfo? {
        val torrents = movie.torrents ?: return null

        val healthyTorrents = torrents.filter { (it.seeds ?: 0) > 0 || (it.peers ?: 0) > 0 }
        val candidatePool = if (healthyTorrents.isNotEmpty()) healthyTorrents else torrents

        val best = candidatePool.maxByOrNull { torrent ->
            val sizePenalty = ((torrent.sizeBytes ?: parseSizeToBytes(torrent.size ?: "999 GB")) / (1024.0 * 1024.0 * 1024.0) * 4).toInt()
            val qualityBonus = when (torrent.quality) {
                "720p" -> 90
                "1080p" -> 80
                "2160p" -> 40
                "480p" -> 50
                else -> 20
            }
            ((torrent.seeds ?: 0) * 100) + ((torrent.peers ?: 0) * 10) + qualityBonus - sizePenalty
        }

        return best?.let { ytsTorrent ->
            val streamUrl = ytsTorrent.url?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("magnet:?", ignoreCase = true)
            } ?: buildMagnetUrl(
                hash = ytsTorrent.hash,
                movieName = movie.title,
                trackers = TRACKERS
            )
            
            TorrentInfo(
                magnetUrl = streamUrl,
                quality = ytsTorrent.quality,
                seeds = ytsTorrent.seeds,
                peers = ytsTorrent.peers,
                size = ytsTorrent.size,
                filesize = ytsTorrent.size,
                provider = "YTS"
            )
        }
    }
    
    /**
     * Build a magnet URL from torrent hash and trackers.
     */
    private fun buildMagnetUrl(hash: String, movieName: String, trackers: List<String>): String {
        val encodedName = java.net.URLEncoder.encode(movieName, "UTF-8")
        val trackerParams = trackers.joinToString("") { "&tr=$it" }
        return "magnet:?xt=urn:btih:$hash&dn=$encodedName$trackerParams"
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
 * YTS API response wrapper.
 */
data class YtsResponse(
    @SerializedName("status")
    val status: String?,
    
    @SerializedName("data")
    val data: YtsData?
)

data class YtsData(
    @SerializedName("movie_count")
    val movieCount: Int?,
    
    @SerializedName("movies")
    val movies: List<YtsMovie>?
)

/**
 * YTS movie data.
 */
data class YtsMovie(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("imdb_code")
    val imdbCode: String?,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("year")
    val year: Int,
    
    @SerializedName("rating")
    val rating: Double?,
    
    @SerializedName("runtime")
    val runtime: Int?,
    
    @SerializedName("genres")
    val genres: List<String>?,
    
    @SerializedName("synopsis")
    val synopsis: String?,
    
    @SerializedName("torrents")
    val torrents: List<YtsTorrent>?
)

/**
 * YTS torrent data.
 */
data class YtsTorrent(
    @SerializedName("url")
    val url: String?,

    @SerializedName("hash")
    val hash: String,
    
    @SerializedName("quality")
    val quality: String?,
    
    @SerializedName("type")
    val type: String?,
    
    @SerializedName("seeds")
    val seeds: Int?,
    
    @SerializedName("peers")
    val peers: Int?,
    
    @SerializedName("size")
    val size: String?,
    
    @SerializedName("size_bytes")
    val sizeBytes: Long?
)
