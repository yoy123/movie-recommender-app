package com.movierecommender.app.data.model

import androidx.room.Entity

/**
 * Stores a provider-specific content identifier and the best-known exact URL/deep link
 * for a TMDB movie or TV show.
 *
 * This is the durable crosswalk layer between canonical TMDB content and provider catalogs.
 */
@Entity(
    tableName = "provider_content_crosswalk",
    primaryKeys = ["tmdbId", "mediaType", "providerId"]
)
data class ProviderContentCrosswalk(
    val tmdbId: Int,
    val mediaType: String,
    val providerId: Int,
    val providerKey: String,
    val providerContentId: String,
    val canonicalUrl: String?,
    val appDeepLink: String?,
    val source: String = SOURCE_MANUAL,
    val confidence: Double = 1.0,
    val lastVerifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MEDIA_TYPE_MOVIE = "movie"
        const val MEDIA_TYPE_TV = "tv"

        const val SOURCE_MANUAL = "manual"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_RESOLVER = "resolver"
    }
}
