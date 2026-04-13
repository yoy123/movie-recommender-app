package com.movierecommender.app.data.remote

/**
 * Maps TMDB Watch Provider IDs to Android/Fire TV app package names and deep link patterns.
 * Used to launch the appropriate streaming app via Intent when the user selects a provider.
 *
 * Provider IDs sourced from TMDB API: https://api.themoviedb.org/3/watch/providers/movie?language=en-US
 * Package names verified against Fire TV app store listings.
 */
object StreamingAppRegistry {

    /**
     * TMDB provider ID → Android package name.
     * These are the most common streaming apps available on Fire TV.
     */
    private val providerPackages = mapOf(
        // ── Subscription (FLATRATE) ─────────────────────────────────────────
        8    to "com.netflix.ninja",                     // Netflix (Fire TV)
        9    to "com.amazon.avod",                       // Amazon Prime Video
        337  to "com.disney.disneyplus",                 // Disney+
        15   to "com.hulu.plus",                         // Hulu
        1899 to "com.hbo.hbonow",                        // Max (HBO)
        531  to "com.paramount.paramount",               // Paramount+
        386  to "com.peacocktv.peacockandroid",           // Peacock
        350  to "com.apple.atv",                          // Apple TV+
        283  to "com.crunchyroll.crunchyroid",            // Crunchyroll
        2    to "com.apple.atv",                          // Apple TV (same app as Apple TV+)
        
        // ── Free / Ad-supported ─────────────────────────────────────────────
        73   to "com.tubitv",                             // Tubi
        300  to "tv.pluto.android",                       // Pluto TV
        457  to "com.amazon.ftv.freevee",                 // Amazon Freevee
        1770 to "com.amazon.ftv.freevee",                 // Freevee (alt ID)
        
        // ── Rent/Buy ────────────────────────────────────────────────────────
        3    to "com.google.android.videos",              // Google Play Movies
        10   to "com.amazon.avod",                        // Amazon Video (rent/buy)
        7    to "com.vudu.air",                           // Vudu / Fandango at Home
        192  to "com.youtube.tv",                         // YouTube (rent/buy)
        
        // ── Other notable services ──────────────────────────────────────────
        43   to "com.starz.starzplay.android",            // Starz
        37   to "com.showtime.showtimeanytime",           // Showtime
        521  to "com.amazon.amazonvideo.livingroom",      // Amazon Channels
        1796 to "com.netflix.ninja",                      // Netflix basic with Ads (same app)
        1825 to "com.amazon.avod",                        // Amazon Prime with Ads
        538  to "com.plex.android",                       // Plex
        526  to "com.amcplus.amcfiretv",                  // AMC+
        636  to "com.kanopy.firetv",                      // Kanopy
        258  to "air.com.vudu.FubiTV",                    // fuboTV
        188  to "com.gotv.crackle.handset",               // Youtube Free (Crackle)
    )

    /**
     * Get the Android package name for a TMDB provider ID.
     * Returns null if the provider is not mapped.
     */
    fun getPackageName(providerId: Int): String? = providerPackages[providerId]

    /**
     * Check if we have a known app for this provider.
     */
    fun hasApp(providerId: Int): Boolean = providerPackages.containsKey(providerId)

    /**
     * Get a user-friendly label for the watch option type.
     */
    fun getTypeLabel(type: String): String = when (type) {
        "flatrate" -> "Stream"
        "free" -> "Free"
        "rent" -> "Rent"
        "buy" -> "Buy"
        "ads" -> "Free with Ads"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    /**
     * Build a deep link URL for a movie/show on popular streaming services.
     * Only returns custom URI schemes (nflx://, hulu://, etc.) that reliably
     * open the actual app on Fire TV. Web URLs (https://) are omitted because
     * they open in Amazon Silk instead of the target app.
     * When this returns null, launchStreamingApp() falls back to
     * getLaunchIntentForPackage() which opens the real app directly.
     */
    fun buildDeepLink(providerId: Int, title: String, tmdbId: Int, isMovie: Boolean): String? {
        val encoded = android.net.Uri.encode(title)
        return when (providerId) {
            8, 1796 -> "nflx://www.netflix.com/search?q=$encoded"
            // Amazon Prime Video — always installed on Fire TV, launched directly via package
            9, 10, 1825 -> null
            // Disney+ — no reliable custom scheme on Fire TV, launched via package
            337 -> null
            15 -> "hulu://search?query=$encoded"
            // Max (HBO) — no reliable custom scheme on Fire TV, launched via package
            1899 -> null
            // Paramount+ — no reliable custom scheme on Fire TV, launched via package
            531 -> null
            386 -> "peacock://search/$encoded"
            // Apple TV+ — no reliable custom scheme on Fire TV, launched via package
            350, 2 -> null
            73 -> "tubitv://search/$encoded"
            300 -> "pluto://search/$encoded"
            283 -> "crunchyroll://search?q=$encoded"
            else -> null
        }
    }
}
