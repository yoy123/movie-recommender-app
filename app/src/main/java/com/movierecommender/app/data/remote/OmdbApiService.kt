package com.movierecommender.app.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface OmdbApiService {
    @GET("/")
    suspend fun getRating(
        @Query("t") title: String,
        @Query("y") year: String?,
        @Query("apikey") apiKey: String
    ): OmdbResponse

    data class OmdbResponse(
        @SerializedName("imdbRating") val imdbRating: String? = null,
        @SerializedName("Error") val error: String? = null,
        @SerializedName("Response") val response: String? = null
    )

    companion object {
        fun create(debug: Boolean = false): OmdbApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://www.omdbapi.com")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OmdbApiService::class.java)
        }
    }
}
