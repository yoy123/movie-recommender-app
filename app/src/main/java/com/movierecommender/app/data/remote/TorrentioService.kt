package com.movierecommender.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.ln

/**
 * Standalone client for Torrentio's public Stremio stream endpoint.
 *
 * Torrentio resolves an IMDb movie ID or an IMDb/season/episode tuple into
 * torrent info-hashes gathered from multiple upstream indexes. No user account,
 * API key, Prowlarr, or FlareSolverr instance is required.
 */
class TorrentioService(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        private const val DEFAULT_BASE_URL = "https://torrentio.strem.fun"
        private const val TIMEOUT_SECONDS = 18L
        private const val USER_AGENT = "OpenStreamPlus/1.0 (Android TV)"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS + 4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    suspend fun searchMovie(
        imdbId: String,
        preferredQuality: String = "1080p"
    ): TorrentInfo? = withContext(Dispatchers.IO) {
        val cleanImdbId = imdbId.trim()
        if (!cleanImdbId.matches(Regex("tt\\d{5,12}", RegexOption.IGNORE_CASE))) {
            return@withContext null
        }

        val streams = fetchStreams("movie", cleanImdbId) ?: return@withContext null
        TorrentioResponseParser.pickBest(streams, preferredQuality, episodeMode = false)
            ?.toTorrentInfo()
    }

    suspend fun searchEpisode(
        imdbId: String,
        season: Int,
        episode: Int,
        preferredQuality: String = "720p",
        showTitle: String
    ): EpisodeTorrentInfo? = withContext(Dispatchers.IO) {
        val cleanImdbId = imdbId.trim()
        if (!cleanImdbId.matches(Regex("tt\\d{5,12}", RegexOption.IGNORE_CASE))) {
            return@withContext null
        }
        if (season < 0 || episode < 0) return@withContext null

        val streams = fetchStreams("series", "$cleanImdbId:$season:$episode")
            ?: return@withContext null
        TorrentioResponseParser.pickBest(streams, preferredQuality, episodeMode = true)
            ?.toEpisodeTorrentInfo(showTitle, season, episode)
    }

    private fun fetchStreams(type: String, id: String): List<TorrentioStream>? {
        val url = "${baseUrl.trimEnd('/')}/stream/$type/$id.json"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.w("Torrentio", "HTTP ${response.code} for $type stream lookup")
                return null
            }
            val body = response.body?.string() ?: return null
            runCatching {
                gson.fromJson(body, TorrentioResponse::class.java).streams.orEmpty()
            }.onFailure {
                android.util.Log.w("Torrentio", "Invalid stream response: ${it.message}")
            }.getOrNull()
        }
    }
}

internal data class TorrentioResponse(
    @SerializedName("streams") val streams: List<TorrentioStream>?
)

internal data class TorrentioStream(
    @SerializedName("name") val name: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("infoHash") val infoHash: String?,
    @SerializedName("fileIdx") val fileIdx: Int?,
    @SerializedName("behaviorHints") val behaviorHints: TorrentioBehaviorHints?
)

internal data class TorrentioBehaviorHints(
    @SerializedName("filename") val filename: String?
)

internal data class ParsedTorrentioStream(
    val magnetUrl: String,
    val quality: String,
    val seeds: Int,
    val peers: Int,
    val size: String?,
    val sizeBytes: Long?,
    val provider: String,
    val displayTitle: String
) {
    fun toTorrentInfo() = TorrentInfo(
        magnetUrl = magnetUrl,
        quality = quality,
        seeds = seeds,
        peers = peers,
        size = size,
        filesize = size,
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
        episodeTitle = displayTitle,
        showTitle = showTitle
    )
}

internal object TorrentioResponseParser {
    private val HASH_REGEX = Regex("^[a-fA-F0-9]{40}$|^[A-Z2-7]{32}$")
    private val SEEDS_REGEX = Regex("(?:👤|👥)\\s*([0-9][0-9,]*)")
    private val PEERS_REGEX = Regex("(?:peers?|leech(?:ers?)?)\\s*[:=]?\\s*([0-9][0-9,]*)", RegexOption.IGNORE_CASE)
    private val SIZE_REGEX = Regex("💾\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(KB|MB|GB|TB)", RegexOption.IGNORE_CASE)
    private val PROVIDER_REGEX = Regex("⚙(?:️)?\\s*([^\\n]+)")

    fun pickBest(
        streams: List<TorrentioStream>,
        preferredQuality: String,
        episodeMode: Boolean
    ): ParsedTorrentioStream? {
        return streams.mapNotNull(::parse)
            .filterNot { isLowQualityCapture(it.displayTitle) }
            .maxByOrNull { score(it, preferredQuality, episodeMode) }
    }

    fun parse(stream: TorrentioStream): ParsedTorrentioStream? {
        val hash = stream.infoHash?.trim()?.takeIf { HASH_REGEX.matches(it) } ?: return null
        val fullText = listOfNotNull(stream.name, stream.title, stream.behaviorHints?.filename)
            .joinToString("\n")
        val displayTitle = stream.behaviorHints?.filename
            ?.takeIf { it.isNotBlank() }
            ?: stream.title?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?: stream.name.orEmpty()
        val quality = detectQuality(fullText)
        val seeds = SEEDS_REGEX.find(fullText)?.groupValues?.get(1)
            ?.replace(",", "")?.toIntOrNull() ?: 0
        val peers = PEERS_REGEX.find(fullText)?.groupValues?.get(1)
            ?.replace(",", "")?.toIntOrNull() ?: 0
        val sizeMatch = SIZE_REGEX.find(fullText)
        val size = sizeMatch?.let { "${it.groupValues[1]} ${it.groupValues[2].uppercase()}" }
        val sizeBytes = sizeMatch?.let {
            parseSizeBytes(it.groupValues[1].toDoubleOrNull(), it.groupValues[2])
        }
        val providerName = PROVIDER_REGEX.find(fullText)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
        val magnet = buildMagnet(hash, stream.fileIdx, stream.behaviorHints?.filename ?: displayTitle)

        return ParsedTorrentioStream(
            magnetUrl = magnet,
            quality = quality,
            seeds = seeds,
            peers = peers,
            size = size,
            sizeBytes = sizeBytes,
            provider = providerName?.let { "Torrentio ($it)" } ?: "Torrentio",
            displayTitle = displayTitle
        )
    }

    private fun buildMagnet(hash: String, fileIdx: Int?, filename: String?): String {
        val encodedName = filename?.takeIf { it.isNotBlank() }?.let(::urlEncode)
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(hash)
            if (encodedName != null) append("&dn=").append(encodedName)
            if (fileIdx != null && fileIdx >= 0) append("&so=").append(fileIdx)
        }
    }

    private fun score(
        stream: ParsedTorrentioStream,
        preferredQuality: String,
        episodeMode: Boolean
    ): Double {
        val qualityScore = qualityScore(stream.quality, preferredQuality)
        val seedScore = ln((stream.seeds + 1).toDouble()) * 120.0
        val knownSwarmBonus = if (stream.seeds > 0 || stream.peers > 0) 140.0 else 0.0
        val sizeGb = stream.sizeBytes?.toDouble()?.div(1024.0 * 1024.0 * 1024.0)
        val softLimitGb = if (episodeMode) 4.0 else 8.0
        val sizePenalty = when {
            sizeGb == null -> 0.0
            sizeGb <= softLimitGb -> sizeGb * 7.0
            else -> softLimitGb * 7.0 + (sizeGb - softLimitGb) * 45.0
        }
        val packPenalty = if (episodeMode && looksLikeSeasonPack(stream.displayTitle)) 120.0 else 0.0
        return qualityScore + seedScore + knownSwarmBonus - sizePenalty - packPenalty
    }

    private fun qualityScore(quality: String, preferredQuality: String): Double {
        if (quality.equals(preferredQuality, ignoreCase = true)) return 1100.0
        return when (quality.lowercase()) {
            "2160p" -> if (preferredQuality.equals("1080p", true)) 880.0 else 760.0
            "1080p" -> 950.0
            "720p" -> 850.0
            "480p" -> 600.0
            else -> 420.0
        }
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

    private fun looksLikeSeasonPack(text: String): Boolean {
        val lower = text.lowercase()
        return Regex("s\\d{1,2}[-–]s?\\d{1,2}").containsMatchIn(lower) ||
            Regex("(?:complete|full)[. _-]*season").containsMatchIn(lower) ||
            Regex("season[. _-]*\\d{1,2}[. _-]*(?:complete|pack)").containsMatchIn(lower)
    }

    private fun parseSizeBytes(value: Double?, unit: String): Long? {
        value ?: return null
        val multiplier = when (unit.uppercase()) {
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024.0
            "GB" -> 1024.0 * 1024.0 * 1024.0
            "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> return null
        }
        return (value * multiplier).toLong()
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
