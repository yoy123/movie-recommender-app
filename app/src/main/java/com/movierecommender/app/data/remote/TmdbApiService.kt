package com.movierecommender.app.data.remote

import com.movierecommender.app.BuildConfig
import com.movierecommender.app.data.model.GenreResponse
import com.movierecommender.app.data.model.MovieDetails
import com.movierecommender.app.data.model.MovieResponse
import com.movierecommender.app.data.model.VideoResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
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
        fun create(): TmdbApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            
            val client = OkHttpClient.Builder()
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
