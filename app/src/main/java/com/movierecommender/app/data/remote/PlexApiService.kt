package com.movierecommender.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with a Plex Media Server.
 * 
 * Supports:
 * - Testing server connectivity
 * - Listing available libraries
 * - Triggering library refresh/scan after downloads
 * 
 * Authentication uses X-Plex-Token parameter.
 */
class PlexApiService {
    
    companion object {
        private const val TAG = "PlexApiService"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Test connection to Plex server.
     * @param serverUrl Base URL of Plex server (e.g., "http://192.168.1.100:32400")
     * @param token X-Plex-Token for authentication
     * @return PlexServerInfo if successful, null if failed
     */
    suspend fun testConnection(serverUrl: String, token: String): PlexServerInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.trimEnd('/')}/?X-Plex-Token=$token"
            Log.d(TAG, "Testing Plex connection: ${serverUrl.trimEnd('/')}")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "Plex connection failed: ${response.code}")
                return@withContext null
            }
            
            val body = response.body?.string() ?: return@withContext null
            parseServerInfo(body)
        } catch (e: Exception) {
            Log.e(TAG, "Plex connection error", e)
            null
        }
    }
    
    /**
     * Get list of libraries on the Plex server.
     * @param serverUrl Base URL of Plex server
     * @param token X-Plex-Token for authentication
     * @return List of PlexLibrary objects
     */
    suspend fun getLibraries(serverUrl: String, token: String): List<PlexLibrary> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.trimEnd('/')}/library/sections?X-Plex-Token=$token"
            Log.d(TAG, "Fetching Plex libraries")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch libraries: ${response.code}")
                return@withContext emptyList()
            }
            
            val body = response.body?.string() ?: return@withContext emptyList()
            parseLibraries(body)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching libraries", e)
            emptyList()
        }
    }
    
    /**
     * Trigger a library scan/refresh.
     * Call this after downloading content to the library folder.
     * @param serverUrl Base URL of Plex server
     * @param token X-Plex-Token for authentication
     * @param libraryId The library key/ID to refresh
     * @param path Optional specific path to scan (for partial scans)
     * @return true if refresh was triggered successfully
     */
    suspend fun refreshLibrary(
        serverUrl: String,
        token: String,
        libraryId: String,
        path: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = "${serverUrl.trimEnd('/')}/library/sections/$libraryId/refresh"
            val url = if (path != null) {
                "$baseUrl?path=${java.net.URLEncoder.encode(path, "UTF-8")}&X-Plex-Token=$token"
            } else {
                "$baseUrl?X-Plex-Token=$token"
            }
            
            Log.d(TAG, "Triggering library refresh for library $libraryId")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Library refresh triggered successfully")
                true
            } else {
                Log.w(TAG, "Library refresh failed: ${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering library refresh", e)
            false
        }
    }
    
    /**
     * Parse server info from XML response.
     */
    private fun parseServerInfo(xml: String): PlexServerInfo? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "MediaContainer") {
                    return PlexServerInfo(
                        friendlyName = parser.getAttributeValue(null, "friendlyName") ?: "Plex Server",
                        machineIdentifier = parser.getAttributeValue(null, "machineIdentifier") ?: "",
                        version = parser.getAttributeValue(null, "version") ?: "",
                        platform = parser.getAttributeValue(null, "platform") ?: ""
                    )
                }
                parser.next()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server info", e)
            null
        }
    }
    
    /**
     * Parse libraries from XML response.
     */
    private fun parseLibraries(xml: String): List<PlexLibrary> {
        val libraries = mutableListOf<PlexLibrary>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Directory") {
                    val key = parser.getAttributeValue(null, "key")
                    val type = parser.getAttributeValue(null, "type")
                    val title = parser.getAttributeValue(null, "title")
                    
                    // Parse Location elements within this Directory
                    val locations = mutableListOf<String>()
                    var depth = 1
                    parser.next()
                    
                    while (depth > 0) {
                        when (parser.eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "Location") {
                                    parser.getAttributeValue(null, "path")?.let {
                                        locations.add(it)
                                    }
                                }
                                depth++
                            }
                            XmlPullParser.END_TAG -> {
                                depth--
                            }
                        }
                        if (depth > 0) parser.next()
                    }
                    
                    if (key != null && title != null && type != null) {
                        libraries.add(PlexLibrary(
                            key = key,
                            title = title,
                            type = type,
                            locations = locations
                        ))
                    }
                } else {
                    parser.next()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing libraries", e)
        }
        return libraries
    }
}

/**
 * Information about a Plex server.
 */
data class PlexServerInfo(
    val friendlyName: String,
    val machineIdentifier: String,
    val version: String,
    val platform: String
)

/**
 * Information about a Plex library.
 */
data class PlexLibrary(
    val key: String, // Library ID used for API calls
    val title: String, // Display name (e.g., "Movies", "TV Shows")
    val type: String, // "movie", "show", etc.
    val locations: List<String> // File paths where content is stored
)
