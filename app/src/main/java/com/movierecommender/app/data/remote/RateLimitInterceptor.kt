package com.movierecommender.app.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that handles HTTP 429 (Too Many Requests) responses
 * from TMDB API by implementing exponential backoff retry logic.
 * 
 * TMDB rate limits: 40 requests per 10 seconds
 * This interceptor:
 * - Detects 429 responses
 * - Reads Retry-After header if present
 * - Waits and retries up to MAX_RETRIES times
 * - Uses exponential backoff between retries
 */
class RateLimitInterceptor : Interceptor {
    
    companion object {
        private const val TAG = "RateLimitInterceptor"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L // 1 second
        private const val MAX_BACKOFF_MS = 10000L // 10 seconds
    }
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var retryCount = 0
        
        while (response.code == 429 && retryCount < MAX_RETRIES) {
            retryCount++
            
            // Get retry delay from header or use exponential backoff
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            val backoffMs = if (retryAfterSeconds != null) {
                (retryAfterSeconds * 1000).coerceAtMost(MAX_BACKOFF_MS)
            } else {
                // Exponential backoff: 1s, 2s, 4s...
                (INITIAL_BACKOFF_MS * (1 shl (retryCount - 1))).coerceAtMost(MAX_BACKOFF_MS)
            }
            
            Log.w(TAG, "Rate limited (429). Retry $retryCount/$MAX_RETRIES after ${backoffMs}ms. URL: ${request.url}")
            
            // Close the previous response before retrying
            response.close()
            
            try {
                Thread.sleep(backoffMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during rate limit backoff", e)
            }
            
            // Retry the request
            response = chain.proceed(request)
        }
        
        if (response.code == 429) {
            Log.e(TAG, "Rate limit exceeded after $MAX_RETRIES retries. URL: ${request.url}")
        }
        
        return response
    }
}
