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

// ─────────────────────────────────────────────────────────────────────────────
// TV Shows Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * External IDs for a TV show (IMDB, TVDB, etc.)
 */
data class TvShowExternalIds(
    val id: Int,
    @SerializedName("imdb_id")
    val imdbId: String?,
    @SerializedName("tvdb_id")
    val tvdbId: Int?,
    @SerializedName("wikidata_id")
    val wikidataId: String?,
    @SerializedName("facebook_id")
    val facebookId: String?,
    @SerializedName("instagram_id")
    val instagramId: String?,
    @SerializedName("twitter_id")
    val twitterId: String?
)

/**
 * Represents a TV show from TMDB API.
 * Similar structure to Movie but with TV-specific fields.
 */
data class TvShow(
    val id: Int,
    val name: String, // TV shows use "name" instead of "title"
    val overview: String,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?, // TV shows use first_air_date instead of release_date
    @SerializedName("vote_average")
    val voteAverage: Double,
    @SerializedName("vote_count")
    val voteCount: Int,
    val popularity: Double,
    @SerializedName("genre_ids")
    val genreIds: List<Int> = emptyList(),
    @SerializedName("origin_country")
    val originCountry: List<String> = emptyList()
)

data class TvShowResponse(
    val page: Int,
    val results: List<TvShow>,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("total_results")
    val totalResults: Int
)

data class TvShowDetails(
    val id: Int,
    val name: String,
    val overview: String,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double,
    @SerializedName("vote_count")
    val voteCount: Int,
    val popularity: Double,
    val genres: List<Genre>,
    @SerializedName("number_of_seasons")
    val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes")
    val numberOfEpisodes: Int?,
    val status: String?, // "Returning Series", "Ended", etc.
    @SerializedName("origin_country")
    val originCountry: List<String> = emptyList()
)

/**
 * Enum to distinguish between movie and TV content modes.
 */
enum class ContentMode {
    MOVIES,
    TV_SHOWS
}
