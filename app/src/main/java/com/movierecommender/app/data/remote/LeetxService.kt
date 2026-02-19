package com.movierecommender.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 1337x torrent site scraper service.
 * Scrapes static HTML from 1337x.to search results and detail pages.
 *
 * Two-step process:
 *   1. Search page returns list of torrent links (no magnets on search page).
 *   2. Detail page for the best result contains the magnet link.
 *
 * Search URL pattern:
 *   https://1337x.to/category-search/{query}/Movies/1/
 *   https://1337x.to/category-search/{query}/TV/1/
 *
 * 1337x serves static HTML (no JS rendering needed), but may use Cloudflare
 * protection. A browser-like User-Agent header is required.
 */
class LeetxService {

    companion object {
        private val MIRROR_URLS = listOf(
            "https://1337x.to",
            "https://1337x.st",
            "https://1337xx.to",
            "https://1337x.gd"
        )

        private const val TIMEOUT_SECONDS = 15L

        // Regex patterns for HTML parsing
        // Search page: extract torrent links and names from result table
        private val SEARCH_ROW_REGEX = Regex(
            """<a\s+href="(/torrent/\d+/[^"]+/)"[^>]*>([^<]+)</a>""",
            RegexOption.IGNORE_CASE
        )

        // Search page: extract seeders and leechers from table cells
        private val SEEDS_REGEX = Regex(
            """<td\s+class="coll-2\s+seeds">(\d+)</td>""",
            RegexOption.IGNORE_CASE
        )
        private val LEECH_REGEX = Regex(
            """<td\s+class="coll-3\s+leeches">(\d+)</td>""",
            RegexOption.IGNORE_CASE
        )

        // Search page: extract size from table
        private val SIZE_REGEX = Regex(
            """<td\s+class="coll-4\s+size[^"]*">([^<]+)<span""",
            RegexOption.IGNORE_CASE
        )

        // Detail page: extract magnet link
        private val MAGNET_REGEX = Regex(
            """href="(magnet:\?xt=urn:btih:[^"]+)"""",
            RegexOption.IGNORE_CASE
        )
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
                val results = scrapeSearchPage(baseUrl, query, "Movies")
                val best = pickBest(results, title, year)
                if (best != null) {
                    // Fetch magnet from detail page
                    val magnet = fetchMagnet(baseUrl, best.detailPath)
                    if (magnet != null) {
                        android.util.Log.d("1337x", "Found torrent: ${best.title} (${best.seeds} seeds)")
                        return@withContext TorrentInfo(
                            magnetUrl = magnet,
                            quality = detectQuality(best.title),
                            seeds = best.seeds,
                            peers = best.leechers,
                            size = best.size,
                            filesize = best.size,
                            provider = "1337x"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("1337x", "Mirror $baseUrl failed: ${e.message}")
                continue
            }
        }

        null
    }

    /**
     * Scrape the search results page.
     * Returns a list of parsed results (without magnet links — those are on detail pages).
     */
    private fun scrapeSearchPage(baseUrl: String, query: String, category: String): List<LeetxResult> {
        val encodedQuery = query.replace(" ", "+")
        val url = "$baseUrl/category-search/$encodedQuery/$category/1/"

        android.util.Log.d("1337x", "Scraping: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Referer", "$baseUrl/")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.w("1337x", "HTTP ${response.code} for $url")
                return emptyList()
            }
            response.body?.string() ?: return emptyList()
        }

        return parseSearchResults(html)
    }

    /**
     * Parse search results from 1337x HTML.
     * The search results table has rows with: name/link, seeds, leeches, date, size, uploader.
     */
    private fun parseSearchResults(html: String): List<LeetxResult> {
        val results = mutableListOf<LeetxResult>()

        // Split by table body rows
        // 1337x uses <tr> rows in the search results table
        val rows = html.split(Regex("""<tr\b""", RegexOption.IGNORE_CASE)).drop(1)

        for (row in rows) {
            try {
                // Extract the torrent link and name
                // Pattern: <a href="/torrent/12345/Name-Here/">Name Here</a>
                // There are usually two <a> tags - the second one (inside coll-1) has the title
                val linkMatches = SEARCH_ROW_REGEX.findAll(row).toList()
                if (linkMatches.isEmpty()) continue

                // The relevant link is typically the second one in the name column
                val linkMatch = linkMatches.lastOrNull { it.groupValues[1].startsWith("/torrent/") }
                    ?: continue

                val detailPath = linkMatch.groupValues[1]
                val name = linkMatch.groupValues[2].trim()
                    .replace("&amp;", "&")
                    .replace("&#039;", "'")
                    .replace("&quot;", "\"")

                // Extract seeds
                val seedMatch = SEEDS_REGEX.find(row)
                val seeds = seedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                // Extract leechers
                val leechMatch = LEECH_REGEX.find(row)
                val leechers = leechMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                // Extract size
                val sizeMatch = SIZE_REGEX.find(row)
                val size = sizeMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

                if (name.isNotBlank() && detailPath.isNotBlank()) {
                    results.add(LeetxResult(
                        title = name,
                        detailPath = detailPath,
                        seeds = seeds,
                        leechers = leechers,
                        size = size
                    ))
                }
            } catch (e: Exception) {
                continue
            }
        }

        android.util.Log.d("1337x", "Parsed ${results.size} results")
        return results
    }

    /**
     * Fetch the magnet link from a torrent's detail page.
     */
    private fun fetchMagnet(baseUrl: String, detailPath: String): String? {
        val url = "$baseUrl$detailPath"

        android.util.Log.d("1337x", "Fetching magnet from: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Referer", baseUrl)
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }

        val magnetMatch = MAGNET_REGEX.find(html)
        val magnet = magnetMatch?.groupValues?.get(1)
            ?.replace("&amp;", "&")

        if (magnet != null) {
            android.util.Log.d("1337x", "Got magnet link (${magnet.length} chars)")
        } else {
            android.util.Log.w("1337x", "No magnet link found on detail page")
        }

        return magnet
    }

    /**
     * Pick the best torrent from search results.
     */
    private fun pickBest(results: List<LeetxResult>, title: String, year: String?): LeetxResult? {
        if (results.isEmpty()) return null

        val valid = results.filter { it.seeds > 0 }
        if (valid.isEmpty()) return null

        val normalizedTitle = title.lowercase().trim()

        val scored = valid.map { result ->
            val name = result.title.lowercase()
            var score = result.seeds

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
            if (name.contains("webrip") || name.contains("web-dl")) score += 80

            // Penalize cam/ts
            if (name.contains("cam") || name.contains("hdcam")) score -= 500
            if (name.contains("telesync") || name.contains(".ts.")) score -= 500

            result to score
        }

        return scored.maxByOrNull { it.second }?.first
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
 * Internal data class for parsed 1337x search result.
 * Magnet link is NOT included — must be fetched from the detail page separately.
 */
private data class LeetxResult(
    val title: String,
    val detailPath: String,
    val seeds: Int,
    val leechers: Int,
    val size: String
)
