package com.movierecommender.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Finds downloadable films in Internet Archive's curated Feature Films collection.
 * The returned HTTPS URL points to Archive's generated .torrent file, which the
 * torrent streaming engine accepts alongside magnet links.
 */
class InternetArchiveService {

    companion object {
        private const val BASE_URL = "https://archive.org"
        private const val TIMEOUT_SECONDS = 15L
        private const val MAX_CANDIDATES = 8
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val gson = Gson()

    suspend fun searchMovie(title: String, year: String?): TorrentInfo? = withContext(Dispatchers.IO) {
        val candidates = searchCandidates(title)
            .sortedByDescending { InternetArchiveParser.score(it, title, year) }

        for (candidate in candidates) {
            if (!InternetArchiveParser.isLikelyTitleMatch(candidate.title, title)) continue

            try {
                val item = fetchItem(candidate.identifier) ?: continue
                val torrent = InternetArchiveParser.toTorrentInfo(candidate, item) ?: continue
                android.util.Log.d(
                    "InternetArchive",
                    "Found downloadable film: ${candidate.title} (${candidate.identifier})"
                )
                return@withContext torrent
            } catch (e: Exception) {
                android.util.Log.w(
                    "InternetArchive",
                    "Item ${candidate.identifier} failed: ${e.message}"
                )
            }
        }

        null
    }

    private fun searchCandidates(title: String): List<InternetArchiveCandidate> {
        val escapedTitle = title
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val query = "title:(\"$escapedTitle\") AND mediatype:(movies) AND collection:(feature_films)"
        val url = "$BASE_URL/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "year")
            .addQueryParameter("fl[]", "downloads")
            .addQueryParameter("rows", MAX_CANDIDATES.toString())
            .addQueryParameter("page", "1")
            .addQueryParameter("output", "json")
            .build()

        val body = execute(url.toString())
        return InternetArchiveParser.parseSearch(body, gson)
    }

    private fun fetchItem(identifier: String): InternetArchiveItem? {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("metadata")
            .addPathSegment(identifier)
            .build()
        val body = execute(url.toString())
        return InternetArchiveParser.parseItem(body, gson)
    }

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }
}

internal object InternetArchiveParser {
    private val playableExtensions = setOf("mp4", "m4v", "mkv", "avi", "ogv", "webm")

    fun parseSearch(json: String, gson: Gson = Gson()): List<InternetArchiveCandidate> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, InternetArchiveSearchResponse::class.java)
                ?.response
                ?.docs
                .orEmpty()
                .filter { it.identifier.isNotBlank() && it.title.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun parseItem(json: String, gson: Gson = Gson()): InternetArchiveItem? {
        if (json.isBlank()) return null
        return runCatching { gson.fromJson(json, InternetArchiveItem::class.java) }.getOrNull()
    }

    fun isLikelyTitleMatch(candidateTitle: String, requestedTitle: String): Boolean {
        val candidate = normalize(candidateTitle)
        val requested = normalize(requestedTitle)
        if (candidate.isBlank() || requested.isBlank()) return false
        return candidate == requested || candidate.startsWith("$requested ")
    }

    fun score(candidate: InternetArchiveCandidate, title: String, year: String?): Long {
        val candidateTitle = normalize(candidate.title)
        val requestedTitle = normalize(title)
        var score = candidate.downloads.coerceAtMost(100_000L)
        if (candidateTitle == requestedTitle) score += 1_000_000L
        else if (candidateTitle.startsWith("$requestedTitle ")) score += 100_000L
        if (year != null && candidate.year == year) score += 250_000L
        return score
    }

    fun toTorrentInfo(
        candidate: InternetArchiveCandidate,
        item: InternetArchiveItem
    ): TorrentInfo? {
        if (item.isDark == true) return null
        if (!item.metadata?.mediatype.equals("movies", ignoreCase = true)) return null

        val torrentFile = item.files
            .firstOrNull { it.name.endsWith("_archive.torrent", ignoreCase = true) }
            ?: item.files.firstOrNull { it.name.endsWith(".torrent", ignoreCase = true) }
            ?: return null

        val largestVideo = item.files
            .filter { file -> file.name.substringAfterLast('.', "").lowercase() in playableExtensions }
            .maxByOrNull { it.size?.toLongOrNull() ?: 0L }
        val torrentUrl = BASE_DOWNLOAD_URL.toHttpUrl().newBuilder()
            .addPathSegment(candidate.identifier)
            .addPathSegment(torrentFile.name)
            .build()
            .toString()

        return TorrentInfo(
            magnetUrl = torrentUrl,
            quality = detectQuality(largestVideo?.name.orEmpty()),
            seeds = null,
            peers = null,
            size = formatFileSize(largestVideo?.size?.toLongOrNull()),
            filesize = formatFileSize(largestVideo?.size?.toLongOrNull()),
            provider = "Internet Archive"
        )
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun detectQuality(fileName: String): String {
        val normalized = fileName.lowercase()
        return when {
            normalized.contains("2160p") || normalized.contains("4k") -> "2160p"
            normalized.contains("1080p") -> "1080p"
            normalized.contains("720p") -> "720p"
            normalized.contains("512kb") -> "480p"
            else -> "Archive"
        }
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

    private const val BASE_DOWNLOAD_URL = "https://archive.org/download"
}

internal data class InternetArchiveSearchResponse(
    val response: InternetArchiveSearchResult?
)

internal data class InternetArchiveSearchResult(
    val docs: List<InternetArchiveCandidate>?
)

internal data class InternetArchiveCandidate(
    val identifier: String = "",
    val title: String = "",
    val year: String? = null,
    val downloads: Long = 0L
)

internal data class InternetArchiveItem(
    @SerializedName("is_dark") val isDark: Boolean? = null,
    val metadata: InternetArchiveMetadata? = null,
    val files: List<InternetArchiveFile> = emptyList()
)

internal data class InternetArchiveMetadata(
    val mediatype: String? = null
)

internal data class InternetArchiveFile(
    val name: String = "",
    val size: String? = null
)