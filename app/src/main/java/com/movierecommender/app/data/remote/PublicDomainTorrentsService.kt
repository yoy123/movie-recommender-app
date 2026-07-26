package com.movierecommender.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Searches Public Domain Torrents and returns its HTTPS .torrent downloads.
 * The site publishes HTTP links, but the same download host supports HTTPS.
 */
class PublicDomainTorrentsService {

    companion object {
        private const val BASE_URL = "https://www.publicdomaintorrents.info"
        private const val CATALOG_URL = "$BASE_URL/nshowcat.html?category=ALL"
        private const val TIMEOUT_SECONDS = 15L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var cachedCatalog: List<PublicDomainMovie> = emptyList()

    suspend fun searchMovie(title: String, imdbId: String?): TorrentInfo? = withContext(Dispatchers.IO) {
        val catalog = cachedCatalog.ifEmpty {
            PublicDomainTorrentsParser.parseCatalog(execute(CATALOG_URL)).also {
                cachedCatalog = it
            }
        }
        val candidates = catalog.filter {
            PublicDomainTorrentsParser.isExactTitleMatch(it.title, title)
        }

        for (candidate in candidates) {
            try {
                val detail = PublicDomainTorrentsParser.parseDetail(
                    execute("$BASE_URL/nshowmovie.html?movieid=${candidate.id}")
                )
                if (!PublicDomainTorrentsParser.matchesImdb(detail.imdbId, imdbId)) continue

                val torrent = PublicDomainTorrentsParser.toTorrentInfo(detail) ?: continue
                android.util.Log.d(
                    "PublicDomainTorrents",
                    "Found downloadable film: ${candidate.title} (${candidate.id})"
                )
                return@withContext torrent
            } catch (e: Exception) {
                android.util.Log.w(
                    "PublicDomainTorrents",
                    "Movie ${candidate.id} failed: ${e.message}"
                )
            }
        }

        null
    }

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }
}

internal object PublicDomainTorrentsParser {
    private val movieRegex = Regex(
        """href\s*=\s*["']?nshowmovie\.html\?movieid=(\d+)["']?[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE
    )
    private val imdbRegex = Regex(
        """imdb\.com/title/(tt\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val torrentRegex = Regex(
        """href\s*=\s*["']?(https?://(?:www\.)?publicdomaintorrents\.com/bt/btdownload\.php\?[^"'\s>]+)["']?[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE
    )
    private val sizeRegex = Regex("""(\d+(?:\.\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)

    fun parseCatalog(html: String): List<PublicDomainMovie> {
        return movieRegex.findAll(html)
            .map { match ->
                PublicDomainMovie(
                    id = match.groupValues[1],
                    title = decodeHtml(match.groupValues[2]).trim()
                )
            }
            .filter { it.title.isNotBlank() }
            .distinctBy(PublicDomainMovie::id)
            .toList()
    }

    fun parseDetail(html: String): PublicDomainMovieDetail {
        val downloads = torrentRegex.findAll(html).map { match ->
            val label = decodeHtml(match.groupValues[2]).trim()
            PublicDomainTorrentDownload(
                url = match.groupValues[1]
                    .replace("&amp;", "&")
                    .replaceFirst("http://", "https://", ignoreCase = true),
                label = label,
                sizeMb = parseSizeMb(label)
            )
        }.distinctBy(PublicDomainTorrentDownload::url).toList()

        return PublicDomainMovieDetail(
            imdbId = imdbRegex.find(html)?.groupValues?.get(1)?.lowercase(),
            downloads = downloads
        )
    }

    fun isExactTitleMatch(candidateTitle: String, requestedTitle: String): Boolean {
        return normalize(candidateTitle) == normalize(requestedTitle)
    }

    fun matchesImdb(candidateImdbId: String?, requestedImdbId: String?): Boolean {
        if (candidateImdbId.isNullOrBlank() || requestedImdbId.isNullOrBlank()) return true
        return normalizeImdbId(candidateImdbId) == normalizeImdbId(requestedImdbId)
    }

    fun toTorrentInfo(detail: PublicDomainMovieDetail): TorrentInfo? {
        val best = detail.downloads.maxByOrNull { download ->
            val label = download.label.lowercase()
            val compatibilityScore = if (label.contains("mp4")) 10_000.0 else 1_000.0
            compatibilityScore + download.sizeMb
        } ?: return null

        return TorrentInfo(
            magnetUrl = best.url,
            quality = if (best.label.contains("mp4", ignoreCase = true)) "480p" else "SD",
            seeds = null,
            peers = null,
            size = best.label,
            filesize = best.label,
            provider = "Public Domain Torrents"
        )
    }

    private fun parseSizeMb(label: String): Double {
        val match = sizeRegex.find(label) ?: return 0.0
        val amount = match.groupValues[1].toDoubleOrNull() ?: return 0.0
        return if (match.groupValues[2].equals("GB", ignoreCase = true)) amount * 1024.0 else amount
    }

    private fun normalize(value: String): String {
        return decodeHtml(value)
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun normalizeImdbId(value: String): String {
        return value.lowercase().removePrefix("tt").trimStart('0')
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
    }
}

internal data class PublicDomainMovie(
    val id: String,
    val title: String
)

internal data class PublicDomainMovieDetail(
    val imdbId: String?,
    val downloads: List<PublicDomainTorrentDownload>
)

internal data class PublicDomainTorrentDownload(
    val url: String,
    val label: String,
    val sizeMb: Double
)