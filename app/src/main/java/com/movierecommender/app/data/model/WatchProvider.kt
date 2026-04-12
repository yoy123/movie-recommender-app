package com.movierecommender.app.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
// TMDB Watch Providers (from /movie/{id}/watch/providers and /tv/{id}/watch/providers)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from TMDB watch providers endpoint.
 * Results are keyed by ISO 3166-1 country code (e.g., "US", "GB").
 */
data class WatchProviderResponse(
    val id: Int?,
    val results: Map<String, CountryWatchProviders>?
)

/**
 * Watch provider availability for a specific country.
 * Contains separate lists for each availability type.
 */
data class CountryWatchProviders(
    /** JustWatch deep-link for the content in this country */
    val link: String?,
    /** Subscription-based streaming (e.g., Netflix, Disney+, Hulu) */
    val flatrate: List<WatchProviderEntry>?,
    /** Available for free (e.g., Tubi, Pluto TV) */
    val free: List<WatchProviderEntry>?,
    /** Available to rent (e.g., Apple TV, Amazon Video) */
    val rent: List<WatchProviderEntry>?,
    /** Available to buy (e.g., iTunes, Google Play) */
    val buy: List<WatchProviderEntry>?,
    /** In theaters (for movies) */
    val ads: List<WatchProviderEntry>?
)

/**
 * A single streaming/purchase provider entry from TMDB.
 */
data class WatchProviderEntry(
    @SerializedName("provider_id")
    val providerId: Int,
    @SerializedName("provider_name")
    val providerName: String,
    @SerializedName("logo_path")
    val logoPath: String?,
    @SerializedName("display_priority")
    val displayPriority: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// App-level Watch Option model (combines streaming providers + torrent)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a single "watch option" presented to the user.
 * Can be either a streaming service or a torrent source.
 */
data class WatchOption(
    /** Display name (e.g., "Netflix", "Hulu", "Torrent (1080p)") */
    val name: String,
    /** Type of watch option */
    val type: WatchOptionType,
    /** TMDB provider logo path (for streaming), null for torrent */
    val logoPath: String?,
    /** Android package name for the streaming app (for Intent launch), null for torrent */
    val packageName: String?,
    /** Deep link URL for the streaming app */
    val deepLinkUrl: String?,
    /** JustWatch link (fallback if no deep link) */
    val justWatchLink: String?,
    /** Magnet URL (for torrent option only) */
    val magnetUrl: String?,
    /** Quality label (e.g., "1080p", "720p") — for torrent option */
    val quality: String?,
    /** Seed count (for torrent option) */
    val seeds: Int?,
    /** Provider (for torrent: "YTS", "PirateBay", etc.) */
    val provider: String?,
    /** Display priority (lower = show first) */
    val displayPriority: Int = 100
)

enum class WatchOptionType {
    /** Free streaming (Tubi, Pluto, etc.) */
    FREE,
    /** Subscription streaming (Netflix, Hulu, Disney+, etc.) */
    SUBSCRIPTION,
    /** Available to rent */
    RENT,
    /** Available to buy */
    BUY,
    /** Ad-supported streaming */
    ADS,
    /** Torrent streaming */
    TORRENT
}
