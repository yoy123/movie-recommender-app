package com.movierecommender.app.data.remote

import android.net.Uri

data class ResolvedProviderLink(
    val providerKey: String,
    val providerContentId: String,
    val canonicalUrl: String,
    val appDeepLink: String?
)

interface ProviderContentResolver {
    val providerKey: String
    val supportedProviderIds: Set<Int>

    fun normalizeContentId(rawContentIdOrUrl: String): String?

    fun buildResolvedLink(contentId: String, isMovie: Boolean): ResolvedProviderLink
}

private class NetflixResolver : ProviderContentResolver {
    override val providerKey: String = "netflix"
    override val supportedProviderIds: Set<Int> = setOf(8, 175, 1796)

    override fun normalizeContentId(rawContentIdOrUrl: String): String? {
        val trimmed = rawContentIdOrUrl.trim()
        val regex = Regex("netflix\\.com/(?:[^/]+/)?title/(\\d+)")
        return when {
            trimmed.matches(Regex("\\d+")) -> trimmed
            regex.containsMatchIn(trimmed) -> regex.find(trimmed)?.groupValues?.get(1)
            else -> null
        }
    }

    override fun buildResolvedLink(contentId: String, isMovie: Boolean): ResolvedProviderLink {
        val canonicalUrl = "https://www.netflix.com/title/$contentId"
        val appDeepLink = "nflx://www.netflix.com/title/$contentId"
        return ResolvedProviderLink(providerKey, contentId, canonicalUrl, appDeepLink)
    }
}

private class PrimeVideoResolver : ProviderContentResolver {
    override val providerKey: String = "prime_video"
    override val supportedProviderIds: Set<Int> = setOf(9, 10, 1825)

    override fun normalizeContentId(rawContentIdOrUrl: String): String? {
        val trimmed = rawContentIdOrUrl.trim()
        val detailRegex = Regex("/(?:detail|dp)/([A-Z0-9]{10})", RegexOption.IGNORE_CASE)
        return when {
            trimmed.matches(Regex("[A-Z0-9]{10}", RegexOption.IGNORE_CASE)) -> trimmed.uppercase()
            detailRegex.containsMatchIn(trimmed) -> detailRegex.find(trimmed)?.groupValues?.get(1)?.uppercase()
            else -> null
        }
    }

    override fun buildResolvedLink(contentId: String, isMovie: Boolean): ResolvedProviderLink {
        val canonicalUrl = "https://www.amazon.com/gp/video/detail/$contentId"
        return ResolvedProviderLink(providerKey, contentId, canonicalUrl, null)
    }
}

private class HuluResolver : ProviderContentResolver {
    override val providerKey: String = "hulu"
    override val supportedProviderIds: Set<Int> = setOf(15)

    override fun normalizeContentId(rawContentIdOrUrl: String): String? {
        val trimmed = rawContentIdOrUrl.trim()
        return when {
            trimmed.contains("hulu.com/") -> Uri.parse(trimmed).path?.trimStart('/')?.takeIf { it.isNotBlank() }
            trimmed.startsWith("/") -> trimmed.trimStart('/').ifBlank { null }
            trimmed.isNotBlank() -> trimmed
            else -> null
        }
    }

    override fun buildResolvedLink(contentId: String, isMovie: Boolean): ResolvedProviderLink {
        val normalizedPath = contentId.trimStart('/')
        val canonicalUrl = "https://www.hulu.com/$normalizedPath"
        val appDeepLink = "hulu://com.hulu.plus/$normalizedPath"
        return ResolvedProviderLink(providerKey, normalizedPath, canonicalUrl, appDeepLink)
    }
}

private class YouTubeResolver : ProviderContentResolver {
    override val providerKey: String = "youtube"
    override val supportedProviderIds: Set<Int> = setOf(188, 192, 2528)

    override fun normalizeContentId(rawContentIdOrUrl: String): String? {
        val trimmed = rawContentIdOrUrl.trim()
        val regex = Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})")
        return when {
            trimmed.matches(Regex("[A-Za-z0-9_-]{11}")) -> trimmed
            regex.containsMatchIn(trimmed) -> regex.find(trimmed)?.groupValues?.get(1)
            else -> null
        }
    }

    override fun buildResolvedLink(contentId: String, isMovie: Boolean): ResolvedProviderLink {
        val canonicalUrl = "https://www.youtube.com/watch?v=$contentId"
        val appDeepLink = "vnd.youtube:$contentId"
        return ResolvedProviderLink(providerKey, contentId, canonicalUrl, appDeepLink)
    }
}

object ProviderContentResolverRegistry {
    private val resolvers: List<ProviderContentResolver> = listOf(
        NetflixResolver(),
        PrimeVideoResolver(),
        HuluResolver(),
        YouTubeResolver(),
    )

    fun getResolver(providerId: Int): ProviderContentResolver? =
        resolvers.firstOrNull { providerId in it.supportedProviderIds }

    fun resolve(providerId: Int, rawContentIdOrUrl: String, isMovie: Boolean): ResolvedProviderLink? {
        val resolver = getResolver(providerId) ?: return null
        val contentId = resolver.normalizeContentId(rawContentIdOrUrl) ?: return null
        return resolver.buildResolvedLink(contentId, isMovie)
    }
}
