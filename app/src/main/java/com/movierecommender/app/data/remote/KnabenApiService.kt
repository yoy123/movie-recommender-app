package com.movierecommender.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.ln

/**
 * Client for Knaben's public torrent-search database API.
 *
 * Knaben aggregates metadata from multiple indexes and returns a magnet or
 * info-hash directly. It requires no API key or locally hosted service.
 */
class KnabenApiService(
    private val endpoint: String = DEFAULT_ENDPOINT
) {
    companion object {
        private const val DEFAULT_ENDPOINT = "https://api.knaben.org/v1"
        private const val TIMEOUT_SECONDS = 18L
        private const val USER_AGENT = "OpenStreamPlus/1.0 (Android TV)"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS + 4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val gson = Gson()

    suspend fun searchMovie(
        title: String,
        year: String?,
        preferredQuality: String = "1080p"
    ): TorrentInfo? = withContext(Dispatchers.IO) {
        val primaryQuery = listOfNotNull(title.trim().takeIf { it.isNotEmpty() }, year?.trim())
            .joinToString(" ")
        val primary = search(primaryQuery)
        val candidates = if (primary.isNotEmpty() || year.isNullOrBlank()) {
            primary
        } else {
            search(title)
        }

        KnabenResultSelector.pickMovie(candidates, title, year, preferredQuality)?.toTorrentInfo()
    }

    suspend fun searchEpisode(
        showTitle: String,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p"
    ): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        if (season < 0 || episode < 0) return@withContext null
        val token = "S%02dE%02d".format(season, episode)
        val results = search("$showTitle $token")
        KnabenResultSelector.pickEpisode(
            results = results,
            showTitle = showTitle,
            season = season,
            episode = episode,
            preferredQuality = preferredQuality
        )?.toEpisodeTorrentInfo(showTitle, season, episode)
    }

    private fun search(query: String): List<KnabenHit> {
        if (query.isBlank()) return emptyList()
        val payload = KnabenSearchRequest(
            searchType = "100%",
            searchField = "title",
            query = query,
            orderBy = "seeders",
            orderDirection = "desc",
            from = 0,
            size = 60,
            hideUnsafe = true,
            hideXxx = true
        )
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(gson.toJson(payload).toRequestBody(JSON))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.w("Knaben", "HTTP ${response.code} for torrent search")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            runCatching {
                gson.fromJson(body, KnabenSearchResponse::class.java).hits.orEmpty()
            }.onFailure {
                android.util.Log.w("Knaben", "Invalid search response: ${it.message}")
            }.getOrDefault(emptyList())
        }
    }
}

internal data class KnabenSearchRequest(
    @SerializedName("search_type") val searchType: String,
    @SerializedName("search_field") val searchField: String,
    @SerializedName("query") val query: String,
    @SerializedName("order_by") val orderBy: String,
    @SerializedName("order_direction") val orderDirection: String,
    @SerializedName("from") val from: Int,
    @SerializedName("size") val size: Int,
    @SerializedName("hide_unsafe") val hideUnsafe: Boolean,
    @SerializedName("hide_xxx") val hideXxx: Boolean
)

internal data class KnabenSearchResponse(
    @SerializedName("hits") val hits: List<KnabenHit>?
)

internal data class KnabenHit(
    @SerializedName("title") val title: String?,
    @SerializedName("magnetUrl") val magnetUrl: String?,
    @SerializedName("hash") val hash: String?,
    @SerializedName("seeders") val seeders: Int?,
    @SerializedName("peers") val peers: Int?,
    @SerializedName("bytes") val bytes: Long?,
    @SerializedName("category") val category: String?,
    @SerializedName("tracker") val tracker: String?
)

internal data class ParsedKnabenResult(
    val title: String,
    val magnetUrl: String,
    val quality: String,
    val seeds: Int,
    val peers: Int,
    val sizeBytes: Long?,
    val provider: String
) {
    fun toTorrentInfo() = TorrentInfo(
        magnetUrl = magnetUrl,
        quality = quality,
        seeds = seeds,
        peers = peers,
        size = sizeBytes?.let(::formatBytes),
        filesize = sizeBytes?.toString(),
        provider = provider
    )

    fun toEpisodeTorrentInfo(showTitle: String, season: Int, episode: Int) = EpisodeTorrentInfo(
        magnetUrl = magnetUrl,
        quality = quality,
        seeds = seeds,
        peers = peers,
        provider = provider,
        season = season,
        episode = episode,
        episodeTitle = title,
        showTitle = showTitle
    )

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return "%.2f GB".format(gb)
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return "%.0f MB".format(mb)
    }
}

internal object KnabenResultSelector {
    private val HASH_REGEX = Regex("^[a-fA-F0-9]{40}$|^[A-Z2-7]{32}$")
    private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")
    private val STOP_WORDS = setOf("a", "an", "and", "of", "the", "to", "in")

    fun pickMovie(
        hits: List<KnabenHit>,
        title: String,
        year: String?,
        preferredQuality: String
    ): ParsedKnabenResult? {
        val titleTokens = normalizedTokens(title)
        return hits.mapNotNull(::parse)
            .filter { matchesTitle(it.title, titleTokens) }
            .filter { matchesYear(it.title, year) }
            .filterNot { isLowQualityCapture(it.title) }
            .maxByOrNull { score(it, preferredQuality, episodeMode = false) }
    }

    fun pickEpisode(
        results: List<KnabenHit>,
        showTitle: String,
        season: Int,
        episode: Int,
        preferredQuality: String
    ): ParsedKnabenResult? {
        val titleTokens = normalizedTokens(showTitle)
        return results.mapNotNull(::parse)
            .filter { matchesTitle(it.title, titleTokens) }
            .filter { containsEpisode(it.title, season, episode) }
            .filterNot { isLowQualityCapture(it.title) }
            .maxByOrNull { score(it, preferredQuality, episodeMode = true) }
    }

    fun parse(hit: KnabenHit): ParsedKnabenResult? {
        val title = hit.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val magnet = hit.magnetUrl?.trim()?.takeIf { it.startsWith("magnet:?", true) }
            ?: hit.hash?.trim()?.takeIf { HASH_REGEX.matches(it) }?.let { hash ->
                "magnet:?xt=urn:btih:$hash&dn=${urlEncode(title)}"
            }
            ?: return null
        val providerName = hit.tracker?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedKnabenResult(
            title = title,
            magnetUrl = magnet,
            quality = detectQuality(title),
            seeds = (hit.seeders ?: 0).coerceAtLeast(0),
            peers = (hit.peers ?: 0).coerceAtLeast(0),
            sizeBytes = hit.bytes?.takeIf { it > 0 },
            provider = providerName?.let { "Knaben ($it)" } ?: "Knaben"
        )
    }

    private fun score(
        result: ParsedKnabenResult,
        preferredQuality: String,
        episodeMode: Boolean
    ): Double {
        val qualityScore = when {
            result.quality.equals(preferredQuality, true) -> 1100.0
            result.quality == "1080p" -> 950.0
            result.quality == "720p" -> 850.0
            result.quality == "2160p" -> if (preferredQuality.equals("1080p", true)) 880.0 else 760.0
            result.quality == "480p" -> 600.0
            else -> 420.0
        }
        val seedScore = ln((result.seeds + 1).toDouble()) * 120.0
        val knownSwarmBonus = if (result.seeds > 0 || result.peers > 0) 140.0 else 0.0
        val sizeGb = result.sizeBytes?.toDouble()?.div(1024.0 * 1024.0 * 1024.0)
        val softLimitGb = if (episodeMode) 4.0 else 8.0
        val sizePenalty = when {
            sizeGb == null -> 0.0
            sizeGb <= softLimitGb -> sizeGb * 7.0
            else -> softLimitGb * 7.0 + (sizeGb - softLimitGb) * 45.0
        }
        return qualityScore + seedScore + knownSwarmBonus - sizePenalty
    }

    private fun matchesTitle(candidate: String, expectedTokens: Set<String>): Boolean {
        if (expectedTokens.isEmpty()) return false
        val candidateTokens = normalizedTokens(candidate)
        val matched = expectedTokens.count(candidateTokens::contains)
        val required = when {
            expectedTokens.size <= 2 -> expectedTokens.size
            else -> kotlin.math.ceil(expectedTokens.size * 0.75).toInt()
        }
        return matched >= required
    }

    private fun matchesYear(candidate: String, expectedYear: String?): Boolean {
        val year = expectedYear?.takeIf { it.matches(YEAR_REGEX) } ?: return true
        val yearsInCandidate = YEAR_REGEX.findAll(candidate).map { it.value }.toSet()
        return yearsInCandidate.isEmpty() || year in yearsInCandidate
    }

    private fun containsEpisode(candidate: String, season: Int, episode: Int): Boolean {
        val s = season.toString().padStart(2, '0')
        val e = episode.toString().padStart(2, '0')
        val patterns = listOf(
            Regex("s0?$season[ ._-]*e0?$episode(?:\\D|$)", RegexOption.IGNORE_CASE),
            Regex("\\b0?$season[ ._-]*x[ ._-]*0?$episode\\b", RegexOption.IGNORE_CASE),
            Regex("s${s}e${e}", RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(candidate) }
    }

    private fun normalizedTokens(value: String): Set<String> {
        val ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return ascii.split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()
    }

    private fun detectQuality(text: String): String {
        val lower = text.lowercase()
        return when {
            "2160p" in lower || Regex("\\b4k\\b").containsMatchIn(lower) -> "2160p"
            "1080p" in lower -> "1080p"
            "720p" in lower -> "720p"
            "480p" in lower -> "480p"
            else -> "Unknown"
        }
    }

    private fun isLowQualityCapture(text: String): Boolean {
        val normalized = text.lowercase()
        return Regex("(?:^|[. _-])(cam|hdcam|telesync|telecine)(?:$|[. _-])").containsMatchIn(normalized)
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
