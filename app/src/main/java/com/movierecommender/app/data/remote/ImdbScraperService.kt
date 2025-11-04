package com.movierecommender.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ImdbScraperService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    /**
     * Scrape IMDB trailer video URL from the movie page
     * @param imdbId The IMDB ID (e.g., "tt0137523" for Fight Club)
     * @return Direct video URL or null if not found
     */
    suspend fun getTrailerUrl(imdbId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Fetch the IMDB movie page
            val url = "https://www.imdb.com/title/$imdbId/"
            android.util.Log.d("ImdbScraper", "Fetching IMDB page: $url")
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .build()
            
            val response = client.newCall(request).execute()
            android.util.Log.d("ImdbScraper", "Response code: ${response.code}")
            
            if (!response.isSuccessful) {
                android.util.Log.w("ImdbScraper", "Failed to fetch IMDB page: ${response.code}")
                return@withContext null
            }
            
            val html = response.body?.string() ?: return@withContext null
            android.util.Log.d("ImdbScraper", "HTML length: ${html.length} characters")
            
            // Look for video URL patterns in the HTML
            // IMDB embeds video URLs in various formats, typically in JSON-LD or data attributes
            val videoUrlPatterns = listOf(
                // Pattern 1: Direct MP4 URL in JSON-LD
                Regex(""""contentUrl":\s*"([^"]*\.mp4[^"]*)""""),
                // Pattern 2: Video URL in data attributes
                Regex("""data-video-url="([^"]*\.mp4[^"]*)""""),
                // Pattern 3: Video URL in script tags
                Regex("""videoUrl["\s:]+["']([^"']*\.mp4[^"']*)["']"""),
                // Pattern 4: IMDB video CDN URL
                Regex("""https://imdb-video\.media-imdb\.com/[^\s"'<>]+\.mp4[^\s"'<>]*""")
            )
            
            for (pattern in videoUrlPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val videoUrl = if (match.groupValues.size > 1) {
                        match.groupValues[1]
                    } else {
                        match.value
                    }
                    // Clean up the URL (remove escape characters, etc.)
                    val cleanUrl = videoUrl
                        .replace("\\u002F", "/")
                        .replace("\\/", "/")
                        .replace("\\u0026", "&")  // Fix unicode ampersand
                        .replace("\\u003D", "=")  // Fix unicode equals
                        .replace("\\u003F", "?")  // Fix unicode question mark
                        .trim()
                    
                    if (cleanUrl.isNotBlank() && cleanUrl.startsWith("http")) {
                        android.util.Log.d("ImdbScraper", "Found video URL: $cleanUrl")
                        return@withContext cleanUrl
                    }
                }
            }
            
            android.util.Log.w("ImdbScraper", "No video URL patterns matched in HTML")
            null
        } catch (e: Exception) {
            android.util.Log.e("ImdbScraper", "Error scraping IMDB", e)
            e.printStackTrace()
            null
        }
    }
    
    companion object {
        fun create(): ImdbScraperService {
            return ImdbScraperService()
        }
    }
}
