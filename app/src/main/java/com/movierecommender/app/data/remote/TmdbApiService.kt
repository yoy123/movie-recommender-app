package com.movierecommender.app.data.remote

import android.content.Context
import com.movierecommender.app.BuildConfig
import com.movierecommender.app.data.model.GenreResponse
import com.movierecommender.app.data.model.MovieDetails
import com.movierecommender.app.data.model.MovieResponse
import com.movierecommender.app.data.model.VideoResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

interface TmdbApiService {
    
    @GET("genre/movie/list")
    suspend fun getGenres(
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("language") language: String = "en-US"
    ): GenreResponse
    
    @GET("discover/movie")
    suspend fun getMoviesByGenre(
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): MovieResponse
    
    @GET("movie/{movie_id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): MovieResponse
    
    @GET("movie/{movie_id}/similar")
    suspend fun getSimilarMovies(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): MovieResponse
    
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): MovieResponse
    
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("append_to_response") appendToResponse: String = "keywords",
        @Query("language") language: String = "en-US"
    ): MovieDetails
    
    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
        @Query("language") language: String = "en-US"
    ): VideoResponse
    
    companion object {
        /** HTTP cache size: 10 MB */
        private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024
        
        /**
         * Create TmdbApiService with HTTP caching.
         * @param context Application context for cache directory
         */
        fun create(context: Context): TmdbApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            
            // Rate limit interceptor handles TMDB 429 responses with exponential backoff
            val rateLimitInterceptor = RateLimitInterceptor()
            
            // HTTP cache for TMDB responses (genre lists, movie lists rarely change)
            // TMDB responses include Cache-Control headers, OkHttp will respect them
            val cache = Cache(
                directory = File(context.cacheDir, "tmdb_http_cache"),
                maxSize = CACHE_SIZE_BYTES
            )

            // SECURITY: Using standard OkHttpClient for all builds.
            // Debug SSL handling is now done via network_security_config.xml which:
            // - Allows user-installed certs in debug builds (for proxy debugging)
            // - Only trusts system certs in release builds
            // This replaces the dangerous buildInsecureClientBuilder() that trusted ALL certs.
            val client = OkHttpClient.Builder()
                .cache(cache)  // Enable HTTP caching
                .addInterceptor(rateLimitInterceptor)  // Add rate limit handling first
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            return Retrofit.Builder()
                .baseUrl(BuildConfig.TMDB_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmdbApiService::class.java)
        }
    }
}
