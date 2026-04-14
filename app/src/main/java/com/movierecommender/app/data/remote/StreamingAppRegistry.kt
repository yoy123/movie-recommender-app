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
        put(422,  "com.amazon.ftv.freevee")      // Amazon Freevee (alt ID)

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
        put(215,  "com.sling")                    // Sling TV
        put(158,  "com.univision.unow")           // Univision NOW
        put(363,  "com.bravo.bravonow")           // Bravo
        put(360,  "com.usa.usanetwork")           // USA Network
        put(365,  "com.syfy.syfynow")             // SYFY
        put(369,  "com.oxygen.oxygenapp")         // Oxygen
        put(248,  "com.betnetworks.ep")           // BET+
        put(343,  "com.bet.shows")                // BET (network)
        put(361,  "com.e.eonline")                // E!
        put(247,  "com.hallmark.hmcfiretv")       // Hallmark Movies Now

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
        put(204,  "com.iqiyi.i18n")                 // iQIYI
        put(342,  "com.viki.android")               // Rakuten Viki
        put(569,  "tv.redbull.firetv")              // Red Bull TV

        // ═══════════════════════════════════════════════════════════════
        // Sports streaming
        // ═══════════════════════════════════════════════════════════════
        put(571,  "com.espn.score_center")        // ESPN / ESPN+
        put(575,  "com.dazn")                     // DAZN
        put(486,  "com.foxsports.android")        // FOX Sports
        put(559,  "com.nfl.app.android")          // NFL+

        // ═══════════════════════════════════════════════════════════════
        // Live TV / Cable Replacement
        // ═══════════════════════════════════════════════════════════════
        put(366,  "com.directv")                  // DirecTV
        put(373,  "com.att.tv")                   // DirecTV Stream

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
            1856,  // Showtime Apple TV Channel
            1857,  // MGM+ Apple TV Channel
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
            637,   // Showtime Roku Premium Channel
        )
        for (id in rokuChannelIds) put(id, "com.roku.web.trc")
    }

    fun getPackageName(providerId: Int): String? = providerPackages[providerId]

    /** Convert a title to a URL slug: lowercase, spaces→hyphens, strip non-alphanum. */
    private fun toSlug(title: String): String =
        title.lowercase()
            .replace(Regex("['']"), "")           // don't → dont
            .replace(Regex("[^a-z0-9]+"), "-")    // non-alphanum → hyphen
            .trim('-')                              // strip leading/trailing

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
     *
     * Legend:
     *   (verified)  = ADB-tested on actual Fire TV hardware
     *   (known)     = documented scheme, not yet device-verified
     */
    fun buildNativeDeepLink(providerId: Int, title: String): String? {
        val encoded = android.net.Uri.encode(title)
        return when (providerId) {
            // Only schemes that are ADB-verified on actual Fire TV hardware.
            // Everything else returns null → falls to HTTPS (Step 2B) or launch-to-home (Step 3).

            // Netflix — nflx:// (verified)
            8, 1796, 175 -> "nflx://www.netflix.com/search?q=$encoded"
            // Hulu — hulu:// (verified)
            15 -> "hulu://hulu.com/search?query=$encoded"
            // Tubi — tubitv:// (verified)
            73 -> "tubitv://media-browse?search=$encoded"
            // Amazon Prime Video — firebat:// (verified)
            9, 10, 1825 -> "firebat://search-v2?searchPhrase=$encoded"
            // Paramount+ — pplus:// with www.paramountplus.com authority (verified)
            2303, 2616, 531, 153 -> "pplus://www.paramountplus.com/search?q=$encoded"

            // Amazon Channel variants → Prime Video native scheme (verified via Prime Video)
            in amazonChannelProviderIds -> "firebat://search-v2?searchPhrase=$encoded"

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
     * the primary launch path.  Used as Step 2B (scoped via setPackage).
     */
    fun buildDeepLink(providerId: Int, title: String, tmdbId: Int, isMovie: Boolean): String? {
        val encoded = android.net.Uri.encode(title)
        return when (providerId) {
            // ══ Major services ═══════════════════════════════════════
            // Netflix (verified)
            8, 1796, 175 -> "https://www.netflix.com/search?q=$encoded"
            // Amazon Prime Video (verified)
            9, 10, 1825 -> "https://app.primevideo.com/search?phrase=$encoded"
            // Hulu (verified)
            15 -> "https://www.hulu.com/search?q=$encoded"
            // Max / HBO (verified)
            1899, 384, 616 -> "https://play.max.com/search?q=$encoded"
            // Paramount+
            2303, 2616, 531, 153 -> "https://www.paramountplus.com/search/?q=$encoded"
            // Disney+
            337 -> "https://www.disneyplus.com/search?q=$encoded"
            // Peacock
            386, 387, 1771 -> "https://www.peacocktv.com/search?q=$encoded"
            // Tubi (verified)
            73 -> "https://tubitv.com/search/$encoded"
            // Pluto TV
            300 -> "https://pluto.tv/search/$encoded"
            // Crunchyroll
            283 -> "https://www.crunchyroll.com/search?q=$encoded"
            // Apple TV
            350, 2 -> "https://tv.apple.com/search?term=$encoded"
            // YouTube — Cobalt ignores search URLs
            192, 188, 2528 -> null

            // ══ Rent/Buy ════════════════════════════════════════════
            // Fandango at Home (Vudu)
            7, 332 -> "https://www.vudu.com/content/movies/search?searchString=$encoded"
            // Google Play Movies
            3 -> "https://play.google.com/store/search?q=$encoded&c=movies"

            // ══ Free / Ad-supported ═════════════════════════════════
            // ViX
            457 -> "https://www.vix.com/es/buscar?q=$encoded"

            // ══ Cable / Network apps ════════════════════════════════
            // Starz
            43 -> "https://www.starz.com/search?q=$encoded"
            // Showtime
            37 -> "https://www.sho.com/search?q=$encoded"
            // MGM+
            34 -> "https://www.mgmplus.com/search?q=$encoded"
            // AMC+
            526 -> "https://www.amcplus.com/search?q=$encoded"
            // AMC (network)
            80 -> "https://www.amc.com/search/$encoded"
            // FXNow
            123 -> "https://fxnow.fxnetworks.com/shows"
            // NBC
            79 -> "https://www.nbc.com/search?q=$encoded"
            // The CW
            83 -> "https://www.cwtv.com/search/?q=$encoded"
            // ABC
            148 -> "https://abc.com/search?q=$encoded"
            // PBS
            209 -> "https://www.pbs.org/search/?q=$encoded"
            // Sling TV
            215 -> "https://www.sling.com/search?q=$encoded"
            // fuboTV
            257 -> "https://www.fubo.tv/search?q=$encoded"
            // Bravo
            363 -> "https://www.bravotv.com/search?q=$encoded"
            // USA Network
            360 -> "https://www.usanetwork.com/search?q=$encoded"
            // SYFY
            365 -> "https://www.syfy.com/search?q=$encoded"
            // Oxygen
            369 -> "https://www.oxygen.com/search?q=$encoded"
            // BET+
            248 -> "https://www.bet.com/shows"
            // BET (network)
            343 -> "https://www.bet.com/shows"
            // E!
            361 -> "https://www.eonline.com/search?q=$encoded"
            // Hallmark Movies Now
            247 -> "https://www.hallmarkmoviesandmysteries.com/search?q=$encoded"
            // Univision NOW
            158 -> "https://www.univision.com/buscar?q=$encoded"
            // ESPN / ESPN+
            571 -> "https://www.espn.com/search/_/q/$encoded"

            // ══ A&E Networks ════════════════════════════════════════
            // A&E
            156 -> "https://www.aetv.com/search/$encoded"
            // Lifetime
            157 -> "https://www.mylifetime.com/search/$encoded"
            // History
            155 -> "https://www.history.com/search?q=$encoded"
            // Freeform
            211 -> "https://www.freeform.com/search?q=$encoded"
            // Lifetime Movie Club
            284 -> "https://www.mylifetime.com/movies/search?q=$encoded"

            // ══ Specialty / Niche ═══════════════════════════════════
            // Plex — slug-based URL opens directly to movie/show page (verified)
            538 -> {
                val slug = toSlug(title)
                val type = if (isMovie) "movie" else "show"
                "https://watch.plex.tv/$type/$slug"
            }
            // Shudder
            99 -> "https://www.shudder.com/search?q=$encoded"
            // MUBI
            11 -> "https://mubi.com/search?q=$encoded"
            // Criterion Channel
            258 -> "https://www.criterionchannel.com/search?q=$encoded"
            // CuriosityStream
            190 -> "https://curiositystream.com/search?q=$encoded"
            // Acorn TV
            87 -> "https://acorn.tv/search?q=$encoded"
            // Sundance Now
            143 -> "https://www.sundancenow.com/search?q=$encoded"
            // BritBox
            151 -> "https://www.britbox.com/us/search?q=$encoded"
            // ALLBLK
            251 -> "https://www.allblk.tv/search?q=$encoded"
            // HiDive
            430 -> "https://www.hidive.com/search?q=$encoded"
            // Kanopy
            191 -> "https://www.kanopy.com/search?query=$encoded"
            // Hoopla
            212 -> "https://www.hoopladigital.com/search?q=$encoded&scope=everything"
            // Pure Flix
            278 -> "https://www.pureflix.com/search?q=$encoded"
            // Philo
            2383 -> "https://www.philo.com/search/$encoded"
            // Roku Channel
            207 -> "https://therokuchannel.roku.com/search?q=$encoded"
            // Viki / Rakuten Viki
            342 -> "https://www.viki.com/search?q=$encoded"
            // iQIYI
            204 -> "https://www.iq.com/search?query=$encoded"
            // Kocowa
            464 -> "https://www.kocowa.com/search?keyword=$encoded"
            // WWE Network
            260 -> "https://www.wwe.com/search?q=$encoded"
            // Red Bull TV
            569 -> "https://www.redbull.com/int-en/search?q=$encoded"

            // ══ Sports ══════════════════════════════════════════════
            // DAZN
            575 -> "https://www.dazn.com/search/$encoded"
            // FOX Sports
            486 -> "https://www.foxsports.com/search?q=$encoded"

            // ══ Live TV / Cable Replacement ═════════════════════════
            // DirecTV
            366 -> "https://www.directv.com/movies-and-shows/search?q=$encoded"
            // DirecTV Stream
            373 -> "https://stream.directv.com/search?q=$encoded"

            // ══ Amazon Channel variants → Prime Video search ════════
            in amazonChannelProviderIds -> "https://app.primevideo.com/search?phrase=$encoded"
            // ══ Apple TV Channel variants → Apple TV search ═════════
            in appleTvChannelProviderIds -> "https://tv.apple.com/search?term=$encoded"
            // ══ Roku Channel variants → Roku Channel search ═════════
            in rokuChannelProviderIds -> "https://therokuchannel.roku.com/search?q=$encoded"

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

    /** Provider IDs that are Apple TV Channel subscriptions. */
    private val appleTvChannelProviderIds = setOf(
        1853, 1854, 1855, 1852, 1856, 1857,
    )

    /** Provider IDs that are Roku Premium Channel subscriptions. */
    private val rokuChannelProviderIds = setOf(
        633, 634, 635, 636, 637,
    )

    private val browserFallbackProviderIds: Set<Int> = buildSet {
        // Major services
        addAll(listOf(8, 1796, 175))                 // Netflix
        addAll(listOf(9, 10, 1825))                  // Amazon Prime Video
        add(15)                                       // Hulu
        addAll(listOf(1899, 384, 616))               // Max (HBO)
        addAll(listOf(2303, 2616, 531, 153))         // Paramount+
        add(337)                                      // Disney+
        addAll(listOf(386, 387, 1771))               // Peacock
        add(73)                                       // Tubi
        add(300)                                      // Pluto TV
        add(283)                                      // Crunchyroll
        addAll(listOf(350, 2))                       // Apple TV
        addAll(listOf(192, 188, 2528))               // YouTube
        add(457)                                      // ViX
        // Rent/Buy
        addAll(listOf(7, 332))                       // Fandango at Home
        add(3)                                        // Google Play Movies
        // Cable / Network
        add(43)                                       // Starz
        add(37)                                       // Showtime
        add(34)                                       // MGM+
        add(526)                                      // AMC+
        add(80)                                       // AMC
        add(123)                                      // FXNow
        add(79)                                       // NBC
        add(83)                                       // The CW
        add(148)                                      // ABC
        add(209)                                      // PBS
        add(215)                                      // Sling TV
        add(257)                                      // fuboTV
        add(158)                                      // Univision NOW
        add(363)                                      // Bravo
        add(360)                                      // USA Network
        add(365)                                      // SYFY
        add(369)                                      // Oxygen
        add(248)                                      // BET+
        add(343)                                      // BET
        add(361)                                      // E!
        add(247)                                      // Hallmark Movies Now
        add(571)                                      // ESPN / ESPN+
        // A&E Networks
        add(156)                                      // A&E
        add(157)                                      // Lifetime
        add(155)                                      // History
        add(211)                                      // Freeform
        add(284)                                      // Lifetime Movie Club
        // Specialty / Niche
        add(538)                                      // Plex
        add(99)                                       // Shudder
        add(11)                                       // MUBI
        add(258)                                      // Criterion Channel
        add(190)                                      // CuriosityStream
        add(87)                                       // Acorn TV
        add(143)                                      // Sundance Now
        add(151)                                      // BritBox
        add(251)                                      // ALLBLK
        add(430)                                      // HiDive
        add(191)                                      // Kanopy
        add(212)                                      // Hoopla
        add(278)                                      // Pure Flix
        add(2383)                                     // Philo
        add(207)                                      // Roku Channel
        add(342)                                      // Viki
        add(204)                                      // iQIYI
        add(464)                                      // Kocowa
        add(260)                                      // WWE Network
        add(569)                                      // Red Bull TV
        // Sports
        add(575)                                      // DAZN
        add(486)                                      // FOX Sports
        // Live TV
        add(366)                                      // DirecTV
        add(373)                                      // DirecTV Stream
        // Aggregated channels
        addAll(amazonChannelProviderIds)
        addAll(appleTvChannelProviderIds)
        addAll(rokuChannelProviderIds)
    }
}
