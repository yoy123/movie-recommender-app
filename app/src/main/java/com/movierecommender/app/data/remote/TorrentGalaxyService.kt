package com.movierecommender.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * TorrentGalaxy scraper service.
 * Scrapes static HTML from torrentgalaxy.to search results.
 *
 * Search URL pattern:
 *   https://torrentgalaxy.to/torrents.php?search={query}&sort=seeders&order=desc&c3=1&c42=1&c4=1
 *   - c3=1  = Movies (SD)
 *   - c42=1 = Movies (HD)
 *   - c4=1  = Movies (4K/UHD)
 *
 * TorrentGalaxy embeds magnet links directly on the search results page,
 * so no detail-page visit is needed.
 */
class TorrentGalaxyService {

    companion object {
        private val MIRROR_URLS = listOf(
            "https://torrentgalaxy.to",
            "https://tgx.rs",
            "https://torrentgalaxy.mx"
        )

        private const val TIMEOUT_SECONDS = 15L

        private val TRACKERS = listOf(
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://tracker.openbittorrent.com:80",
            "udp://p4p.arenabg.com:1337",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.tiny-vps.com:6969/announce"
        )

        // Regex to extract magnet links from HTML
        private val MAGNET_REGEX = Regex("""href="(magnet:\?xt=urn:btih:[^"]+)"""")

        // Regex to extract torrent rows from the results table
        // Each row contains: title, magnet, size, seeders, leechers
        private val ROW_REGEX = Regex(
            """<a\s+href="/torrent/[^"]*"\s*>([^<]+)</a>.*?""" +
            """href="(magnet:\?xt=urn:btih:[^"]+)".*?""" +
            """<span\s+class="badge\s+badge-table-row\s+badge-success">\s*(\d+)\s*</span>.*?""" +
            """<span\s+class="badge\s+badge-table-row\s+badge-danger">\s*(\d+)\s*</span>""",
            RegexOption.DOT_MATCHES_ALL
        )

        // Simpler per-row extraction approach
        private val TITLE_REGEX = Regex("""<a\s+href="/torrent/\d+/[^"]*"[^>]*>\s*<b>([^<]+)</b>""")
        private val SIZE_REGEX = Regex("""<span\s+class="badge\s+badge-secondary\s+badge-btn"[^>]*>([^<]+)</span>""")
        private val SEED_REGEX = Regex("""<span\s+title="Seeders/Leechers"[^>]*>\s*\[<font\s+color="green"><b>(\d+)</b></font>/""")
        private val LEECH_REGEX = Regex("""<font\s+color="#ff0000"><b>(\d+)</b></font>\]""")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Search for a movie torrent by title and optional year.
     * Returns the best TorrentInfo or null.
     */
    suspend fun searchMovie(title: String, year: String?): TorrentInfo? = withContext(Dispatchers.IO) {
        val query = if (year != null) "$title $year" else title

        for (baseUrl in MIRROR_URLS) {
            try {
                val results = scrapeSearch(baseUrl, query)
                val best = pickBest(results, title, year)
                if (best != null) {
                    android.util.Log.d("TorrentGalaxy", "Found torrent: ${best.quality} (${best.size}) ${best.seeds} seeds")
                    return@withContext best
                }
            } catch (e: Exception) {
                android.util.Log.w("TorrentGalaxy", "Mirror $baseUrl failed: ${e.message}")
                continue
            }
        }

        null
    }

    /**
     * Scrape search results from TorrentGalaxy.
     */
    private fun scrapeSearch(baseUrl: String, query: String): List<TgxResult> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        // c3=Movies(SD), c42=Movies(HD), c4=Movies(4K), sorted by seeders desc
        val url = "$baseUrl/torrents.php?search=$encodedQuery&sort=seeders&order=desc&c3=1&c42=1&c4=1"

        android.util.Log.d("TorrentGalaxy", "Scraping: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string() ?: return emptyList()
        }

        return parseResults(html)
    }

    /**
     * Parse torrent results from TorrentGalaxy HTML.
     * Uses a block-based approach: splits by row dividers and extracts fields.
     */
    private fun parseResults(html: String): List<TgxResult> {
        val results = mutableListOf<TgxResult>()

        // Extract all magnet links and their surrounding context
        val magnetMatches = MAGNET_REGEX.findAll(html).toList()
        if (magnetMatches.isEmpty()) {
            android.util.Log.d("TorrentGalaxy", "No magnet links found in HTML")
            return emptyList()
        }

        // Split HTML into blocks around each torrent row
        // TGx uses <div class="tgxtablerow"> for each result
        val rowBlocks = html.split(Regex("""<div\s+class="tgxtablerow[^"]*">"""))
            .drop(1) // First element is before the first row

        for (block in rowBlocks) {
            try {
                // Extract magnet
                val magnetMatch = MAGNET_REGEX.find(block) ?: continue
                val magnetUrl = magnetMatch.groupValues[1]
                    .replace("&amp;", "&")

                // Extract title - look for torrent link with <b> tag
                val titleMatch = TITLE_REGEX.find(block)
                val title = titleMatch?.groupValues?.get(1)?.trim()
                    ?.replace("&amp;", "&")
                    ?.replace("&#039;", "'")
                    ?.replace("&quot;", "\"")
                    ?: continue

                // Extract size
                val sizeMatch = SIZE_REGEX.find(block)
                val size = sizeMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

                // Extract seeders/leechers - look for the green/red font pattern
                val seedMatch = Regex("""<font\s+color="green"><b>(\d+)</b></font>""").find(block)
                val leechMatch = Regex("""<font\s+color="#ff0000"><b>(\d+)</b></font>""").find(block)
                val seeders = seedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val leechers = leechMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                results.add(TgxResult(
                    title = title,
                    magnetUrl = magnetUrl,
                    size = size,
                    seeders = seeders,
                    leechers = leechers
                ))
            } catch (e: Exception) {
                android.util.Log.w("TorrentGalaxy", "Error parsing row: ${e.message}")
                continue
            }
        }

        android.util.Log.d("TorrentGalaxy", "Parsed ${results.size} results")
        return results
    }

    /**
     * Pick the best torrent from results.
     */
    private fun pickBest(results: List<TgxResult>, title: String, year: String?): TorrentInfo? {
        if (results.isEmpty()) return null

        val valid = results.filter { it.seeders > 0 }
        if (valid.isEmpty()) return null

        val normalizedTitle = title.lowercase().trim()

        val scored = valid.map { result ->
            val name = result.title.lowercase()
            var score = result.seeders

            // Title word matching
            if (normalizedTitle.isNotEmpty()) {
                val titleWords = normalizedTitle.split(" ").filter { it.length > 2 }
                val matchCount = titleWords.count { name.contains(it) }
                score += matchCount * 100
            }

            // Year match bonus
            if (year != null && name.contains(year)) {
                score += 500
            }

            // Quality bonuses
            if (name.contains("1080p")) score += 200
            if (name.contains("720p")) score += 150
            if (name.contains("bluray") || name.contains("bdrip") || name.contains("brrip")) score += 100

            // Penalize cam/ts
            if (name.contains("cam") || name.contains("hdcam")) score -= 500
            if (name.contains("telesync") || name.contains(".ts.")) score -= 500

            result to score
        }

        val best = scored.maxByOrNull { it.second }?.first ?: return null
        return best.toTorrentInfo()
    }

    /**
     * Convert a TGx result to common TorrentInfo format.
     */
    private fun TgxResult.toTorrentInfo(): TorrentInfo {
        val qualityStr = detectQuality(title)

        return TorrentInfo(
            magnetUrl = magnetUrl,
            quality = qualityStr,
            seeds = seeders,
            peers = leechers,
            size = size,
            filesize = size,
            provider = "TorrentGalaxy"
        )
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

/**
 * Internal data class for parsed TorrentGalaxy search result.
 */
private data class TgxResult(
    val title: String,
    val magnetUrl: String,
    val size: String,
    val seeders: Int,
    val leechers: Int
)
