package com.movierecommender.app.data.remote

/**
 * Maps TMDB Watch Provider IDs to Android/Fire TV app package names and deep link patterns.
 * Used to launch the appropriate streaming app via Intent when the user selects a provider.
 *
 * Provider IDs sourced from TMDB API watch/providers endpoint (US region).
 * Package names verified against Fire TV and standard Android app stores.
 *
 * Strategy:
 *  - "X Amazon Channel" variants all launch Amazon Prime Video (com.amazon.firebat on Fire TV)
 *  - "X Apple TV Channel" variants all launch Apple TV (com.apple.atv)
 *  - "X Roku Premium Channel" variants all launch The Roku Channel app
 *  - Standard apps use their own Fire TV or Android package names
 */
object StreamingAppRegistry {

    /**
     * TMDB provider ID → Android/Fire TV package name.
     * Covers all US providers returned by TMDB that have a known app.
     */
    private val providerPackages: Map<Int, String> = buildMap {
        // ═══════════════════════════════════════════════════════════════
        // Major streaming services (direct apps)
        // ═══════════════════════════════════════════════════════════════
        put(8,    "com.netflix.ninja")          // Netflix
        put(1796, "com.netflix.ninja")          // Netflix basic with Ads
        put(175,  "com.netflix.ninja")          // Netflix Kids

        put(9,    "com.amazon.firebat")         // Amazon Prime Video (Fire TV)
        put(10,   "com.amazon.firebat")         // Amazon Video (rent/buy)
        put(1825, "com.amazon.firebat")         // Amazon Prime with Ads

        put(337,  "com.disney.disneyplus")      // Disney+

        put(15,   "com.hulu.plus")              // Hulu

        put(1899, "com.hbo.hbonow")             // Max (HBO)
        put(384,  "com.hbo.hbonow")             // HBO Max (alt ID)
        put(616,  "com.hbo.hbonow")             // HBO Max Free

        put(386,  "com.peacock.peacockfiretv")   // Peacock Premium
        put(387,  "com.peacock.peacockfiretv")   // Peacock Premium Plus
        put(1771, "com.peacock.peacockfiretv")   // Peacock Free

        put(2303, "com.cbs.ott")                // Paramount Plus Premium
        put(2616, "com.cbs.ott")                // Paramount Plus Essential
        put(531,  "com.cbs.ott")                // Paramount+ (base)
        put(153,  "com.cbs.ott")                // CBS → now Paramount+

        put(350,  "com.apple.atv")              // Apple TV
        put(2,    "com.apple.atv")              // Apple TV Store (rent/buy)

        put(283,  "com.crunchyroll.crunchyroid") // Crunchyroll

        put(192,  "com.amazon.firetv.youtube")   // YouTube (Fire TV)
        put(188,  "com.amazon.firetv.youtube")   // YouTube Premium (Fire TV)
        put(2528, "com.amazon.firetv.youtube")   // YouTube TV (Fire TV)

        put(3,    "com.google.android.videos")   // Google Play Movies

        // ═══════════════════════════════════════════════════════════════
        // Free / Ad-supported
        // ═══════════════════════════════════════════════════════════════
        put(73,   "com.tubitv.ott")              // Tubi TV
        put(300,  "tv.pluto.android")            // Pluto TV
        put(457,  "com.vix.vixtv")               // ViX
        put(1770, "com.amazon.ftv.freevee")      // Freevee

        // ═══════════════════════════════════════════════════════════════
        // Rent/Buy
        // ═══════════════════════════════════════════════════════════════
        put(7,    "com.vudu.air")                // Fandango At Home (Vudu)
        put(332,  "com.vudu.air")                // Fandango at Home Free

        // ═══════════════════════════════════════════════════════════════
        // Cable/Network apps
        // ═══════════════════════════════════════════════════════════════
        put(43,   "com.starz.starzplay.firetv")  // Starz
        put(37,   "com.showtime.showtimeanytime") // Showtime
        put(34,   "com.mgm.mgmplus")             // MGM+
        put(526,  "com.amcplus.amcfiretv")        // AMC+
        put(80,   "com.amctve.amcfullepisodes")   // AMC (network)
        put(123,  "com.fxnetworks.fxnow")         // FXNow
        put(79,   "com.nbcuni.nbc")               // NBC
        put(83,   "com.cw.fulfillment.android")   // The CW
        put(148,  "com.abc.abcvideo")             // ABC
        put(156,  "com.aetn.aetv")                // A&E
        put(157,  "com.aetn.lifetime")            // Lifetime
        put(211,  "com.disney.datg.videoplatforms.android.abc.freeform") // Freeform
        put(155,  "com.aetn.history")             // History
        put(209,  "com.pbs.video")                // PBS

        // ═══════════════════════════════════════════════════════════════
        // Specialty / Niche streaming
        // ═══════════════════════════════════════════════════════════════
        put(538,  "com.plexapp.android")          // Plex
        put(191,  "com.kanopy.firetv")            // Kanopy
        put(212,  "com.hoopladigital.android")    // Hoopla
        put(257,  "com.fubo.firetv")              // fuboTV
        put(258,  "com.criterionchannel")          // Criterion Channel
        put(11,   "com.mubi")                      // MUBI
        put(190,  "com.curiositystream.curiositystream") // CuriosityStream
        put(99,   "com.shudder.android")           // Shudder
        put(87,   "com.acorn.acorntv")             // Acorn TV
        put(143,  "com.sundancenow.sundancenow")   // Sundance Now
        put(151,  "com.britbox.us")                // BritBox
        put(251,  "com.amcnetworks.allblk")        // ALLBLK
        put(278,  "com.pureflix.pureflixapp")      // Pure Flix
        put(284,  "com.aetn.lifetimemovieclub")    // Lifetime Movie Club
        put(430,  "com.sentaifilmworks.android")   // HiDive
        put(2383, "com.philo.philo")               // Philo
        put(100,  "com.guidedoc.android")           // GuideDoc
        put(207,  "com.roku.web.trc")               // The Roku Channel
        put(464,  "com.kocowa.android")              // Kocowa
        put(260,  "com.wwe.universe")                // WWE Network

        // ═══════════════════════════════════════════════════════════════
        // Amazon Channel variants → all launch Prime Video (Fire TV)
        // ═══════════════════════════════════════════════════════════════
        val amazonChannelIds = intArrayOf(
            582,   // Paramount+ Amazon Channel
            583,   // MGM+ Amazon Channel
            584,   // Discovery+ Amazon Channel
            528,   // AMC+ Amazon Channel
            1968,  // Crunchyroll Amazon Channel
            1825,  // HBO Max Amazon Channel (already mapped above, put is idempotent)
            289,   // Cinemax Amazon Channel
            290,   // Hallmark+ Amazon Channel
            291,   // MZ Choice Amazon Channel
            293,   // PBS Kids Amazon Channel
            294,   // PBS Masterpiece Amazon Channel
            295,   // RetroCrush Amazon Channel
            521,   // Amazon Channels (generic)
            600,   // Shout! Factory Amazon Channel
            602,   // FilmBox Live Amazon Channel
            603,   // CuriosityStream Amazon Channel
            619,   // Starz Amazon Channel
            1715,  // Britbox Amazon Channel
            1746,  // Hallmark TV Amazon Channel
            2066,  // UP Faith & Family Amazon Channel
            2068,  // Tastemade Amazon Channel
            2069,  // ScreenPix Amazon Channel
            2164,  // Gaia Amazon Channel
            2266,  // Qello Concerts Amazon Channel
            2296,  // Viaplay Amazon Channel
            2376,  // DocCom Amazon Channel
            2377,  // Docurama Amazon Channel
            2378,  // Dove Amazon Channel
            2379,  // Dox Amazon Channel
            2390,  // Hidive Amazon Channel
            2392,  // Echoboom Amazon Channel
            2394,  // Fear Factory Amazon Channel
            2395,  // Film Movement Plus Amazon Channel
            2396,  // Fitfusion Amazon Channel
            2398,  // Food Matters Amazon Channel
            2401,  // Fuse+ Amazon Channel
            2403,  // Hi-YAH Amazon Channel
            2404,  // Indie Club Amazon Channel
            2405,  // IndieFlix Shorts Amazon Channel
            2406,  // Here TV Amazon Channel
            2400,  // France Channel Amazon Channel
            2407,  // IndiePix Amazon Channel
            2408,  // Doki Amazon Channel
            2414,  // Kartoon Channel Amazon Channel
            2415,  // Kidstream Amazon Channel
            2418,  // Magnolia Selects Amazon Channel
            2419,  // Monsters and Nightmares Amazon Channel
            2420,  // Marquee TV Amazon Channel
            2424,  // Outside TV Features Amazon Channel
            2427,  // Passionflix Amazon Channel
            2428,  // Pinoy Box Office Amazon Channel
            2430,  // PBS Documentaries Amazon Channel
            2431,  // PBS Living Amazon Channel
            2432,  // PixL Amazon Channel
            2433,  // Pure Flix Amazon Channel
            2435,  // Revry Amazon Channel
            2436,  // Ryan and Friends Plus Amazon Channel
            2438,  // Sensical Amazon Channel
            2439,  // ZenLIFE Amazon Channel
            2442,  // Demand Africa Amazon Channel
            2443,  // Surf Network Amazon Channel
            2444,  // Toku Amazon Channel
            2445,  // MovieSphere+ Amazon Channel
            2446,  // True Royalty Amazon Channel
            2448,  // FUEL TV+ Amazon Channel
            2452,  // Dreamscape Kids Amazon Channel
            2454,  // Green Planet Stream Amazon Channel
            2462,  // Yoga and Fitness TV Amazon Channel
            2464,  // Young Hollywood Amazon Channel
            2465,  // Vemox Cine Amazon Channel
            2466,  // Warriors and Gangsters Amazon Channel
            2467,  // Xive TV Documentaries Amazon Channel
            2468,  // XLTV Amazon Channel
            2470,  // Yipee Kids TV Amazon Channel
            2668,  // Wonder Project Amazon Channel
        )
        for (id in amazonChannelIds) put(id, "com.amazon.firebat")

        // ═══════════════════════════════════════════════════════════════
        // Apple TV Channel variants → all launch Apple TV
        // ═══════════════════════════════════════════════════════════════
        val appleTvChannelIds = intArrayOf(
            1853,  // Paramount Plus Apple TV Channel
            1854,  // AMC Plus Apple TV Channel
            1855,  // Starz Apple TV Channel
            1852,  // Britbox Apple TV Channel
        )
        for (id in appleTvChannelIds) put(id, "com.apple.atv")

        // ═══════════════════════════════════════════════════════════════
        // Roku Premium Channel variants → Roku Channel app
        // ═══════════════════════════════════════════════════════════════
        val rokuChannelIds = intArrayOf(
            633,   // Paramount+ Roku Premium Channel
            634,   // Starz Roku Premium Channel
            635,   // AMC+ Roku Premium Channel
            636,   // MGM Plus Roku Premium Channel
        )
        for (id in rokuChannelIds) put(id, "com.roku.web.trc")
    }

    fun getPackageName(providerId: Int): String? = providerPackages[providerId]

    fun hasApp(providerId: Int): Boolean = providerPackages.containsKey(providerId)

    /**
     * Fire TV streaming apps tested so far ignore or misroute third-party SEARCH intents,
     * so we currently disable app-driven search entirely and only open the provider app.
     */
    fun supportsInAppSearch(providerId: Int): Boolean = false

    /**
     * Build a native custom-scheme deep link for apps that support them.
     * Custom URI schemes bypass Silk browser interception on Fire TV.
     * Returns null if no custom scheme is known for the provider.
     */
    fun buildNativeDeepLink(providerId: Int, title: String): String? {
        val encoded = android.net.Uri.encode(title)
        return when (providerId) {
            // Netflix — nflx:// scheme (verified on Fire TV)
            8, 1796, 175 -> "nflx://www.netflix.com/search?q=$encoded"
            // Hulu — hulu:// with hulu.com authority (verified on Fire TV)
            15 -> "hulu://hulu.com/search?query=$encoded"
            // Tubi — tubitv:// scheme (verified on Fire TV)
            73 -> "tubitv://media-browse?search=$encoded"
            // YouTube — Cobalt app on Fire TV ignores search deep links; only /watch?v= works.
            // Returning null lets the cascade fall to Step 3 (launch to home + toast).
            192, 188, 2528 -> null
            // Amazon Prime Video — firebat:// scheme with search-v2 (Fire TV internal)
            9, 10, 1825 -> "firebat://search-v2?searchPhrase=$encoded"
            // Pluto TV — plutotv:// scheme
            300 -> "plutotv://on-demand?search=$encoded"
            // Starz — starz:// scheme with search authority
            43 -> "starz://search?query=$encoded"
            // Plex — plex:// scheme
            538 -> "plex://search?query=$encoded"
            else -> null
        }
    }

    /**
     * Browser-backed search URLs are a weak fallback on Fire TV because they often
     * resolve to Silk instead of the provider app.
     */
    fun shouldUseBrowserFallback(providerId: Int): Boolean = providerId in browserFallbackProviderIds

    fun getTypeLabel(type: String): String = when (type) {
        "flatrate" -> "Stream"
        "free" -> "Free"
        "rent" -> "Rent"
        "buy" -> "Buy"
        "ads" -> "Free with Ads"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    /**
     * Build a browser fallback URL for a movie/show on streaming services.
     * On Fire TV these often resolve to Silk, so callers should not use them as
     * the primary launch path.
     */
    fun buildDeepLink(providerId: Int, title: String, tmdbId: Int, isMovie: Boolean): String? {
        val encoded = android.net.Uri.encode(title)
        return when (providerId) {
            // Netflix — app registers https://www.netflix.com (verified)
            8, 1796, 175 -> "https://www.netflix.com/search?q=$encoded"
            // Amazon Prime Video — app registers https://app.primevideo.com with /search path (verified)
            9, 10, 1825 -> "https://app.primevideo.com/search?phrase=$encoded"
            // Hulu — app registers https://hulu.com (verified)
            15 -> "https://www.hulu.com/search?q=$encoded"
            // Max (HBO) — app registers https://play.max.com (verified)
            1899, 384, 616 -> "https://play.max.com/search?q=$encoded"
            // Paramount+
            2303, 2616, 531, 153 -> "https://www.paramountplus.com/search/?q=$encoded"
            // Disney+
            337 -> "https://www.disneyplus.com/search?q=$encoded"
            // Peacock — no deep link handlers, will fall to app launch
            386, 387, 1771 -> null
            // Tubi — app registers https://tubitv.com (verified)
            73 -> "https://tubitv.com/search/$encoded"
            // Pluto TV — app registers https://pluto.tv
            300 -> "https://pluto.tv/search/$encoded"
            // Crunchyroll
            283 -> "https://www.crunchyroll.com/search?q=$encoded"
            // Apple TV
            350, 2 -> "https://tv.apple.com/search?term=$encoded"
            // YouTube — Cobalt app ignores search URLs, opens to home regardless.
            192, 188, 2528 -> null
            // Starz — app registers https://www.starz.com
            43 -> "https://www.starz.com/search?q=$encoded"
            // fuboTV
            257 -> "https://www.fubo.tv/search?q=$encoded"
            // Plex — app registers https://watch.plex.tv
            538 -> "https://watch.plex.tv/search?q=$encoded"
            // Amazon Channel variants → Prime Video search
            in amazonChannelProviderIds -> "https://app.primevideo.com/search?phrase=$encoded"
            else -> null
        }
    }

    /** Provider IDs that are Amazon Channel subscriptions. */
    private val amazonChannelProviderIds = setOf(
        582, 583, 584, 528, 1968, 289, 290, 291, 293, 294, 295,
        521, 600, 602, 603, 619, 1715, 1746,
        2066, 2068, 2069, 2164, 2266, 2296,
        2376, 2377, 2378, 2379, 2390, 2392, 2394, 2395, 2396, 2398,
        2400, 2401, 2403, 2404, 2405, 2406, 2407, 2408,
        2414, 2415, 2418, 2419, 2420, 2424, 2427, 2428,
        2430, 2431, 2432, 2433, 2435, 2436, 2438, 2439,
        2442, 2443, 2444, 2445, 2446, 2448, 2452, 2454,
        2462, 2464, 2465, 2466, 2467, 2468, 2470, 2668,
    )

    private val browserFallbackProviderIds = setOf(
        8, 1796, 175,
        9, 10, 1825,
        15,
        1899, 384, 616,
        2303, 2616, 531, 153,
        337,
        386, 387, 1771,
        73,
        300,
        283,
        350, 2,
        192, 188, 2528,
        43,
        257,
        538,
    ) + amazonChannelProviderIds
}
