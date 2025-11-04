package com.movierecommender.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey
    val id: Int,
    val title: String,
    val overview: String,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double,
    @SerializedName("vote_count")
    val voteCount: Int,
    val popularity: Double,
    @SerializedName("genre_ids")
    val genreIds: List<Int> = emptyList(),
    val genres: String? = null, // Comma-separated genre names for display
    val isSelected: Boolean = false,
    val isRecommended: Boolean = false,
    val isFavorite: Boolean = false, // For Dee's Favorites collection
    val timestamp: Long = System.currentTimeMillis()
)

data class MovieResponse(
    val page: Int,
    val results: List<Movie>,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("total_results")
    val totalResults: Int
)

data class Genre(
    val id: Int,
    val name: String
)

data class GenreResponse(
    val genres: List<Genre>
)

data class Keyword(
    val id: Int,
    val name: String
)

data class KeywordsResponse(
    val keywords: List<Keyword>
)

data class MovieDetails(
    val id: Int,
    val title: String,
    val overview: String,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double,
    @SerializedName("vote_count")
    val voteCount: Int,
    val popularity: Double,
    val genres: List<Genre>,
    val keywords: KeywordsResponse?,
    val runtime: Int?,
    @SerializedName("imdb_id")
    val imdbId: String?
)
