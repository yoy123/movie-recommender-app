package com.movierecommender.app.data.model

enum class RecommendationSource {
    AI,
    TMDB_FALLBACK
}

enum class RecommendationFallbackReason(val userMessage: String) {
    DATA_SHARING_NOT_AUTHORIZED(
        "AI data sharing was not authorized, so TMDB recommendations are being shown."
    ),
    OPENAI_KEY_UNAVAILABLE(
        "The OpenAI API key is unavailable, so TMDB recommendations are being shown."
    ),
    INSUFFICIENT_SAFE_CANDIDATES(
        "TMDB did not provide at least 15 safe candidates for bounded AI reranking, so TMDB recommendations are being shown."
    ),
    OPENAI_REQUEST_FAILED(
        "The OpenAI request failed, so TMDB recommendations are being shown."
    ),
    OPENAI_RESPONSE_REJECTED(
        "The AI response failed validation, so TMDB recommendations are being shown."
    )
}

data class RecommendationResult(
    val text: String,
    val source: RecommendationSource,
    val fallbackReason: RecommendationFallbackReason? = null,
    val diagnostic: String? = null
) {
    val fallbackNotice: String?
        get() {
            if (source != RecommendationSource.TMDB_FALLBACK) return null
            val base = fallbackReason?.userMessage ?: "TMDB fallback recommendations are being shown."
            return diagnostic?.takeIf { it.isNotBlank() }?.let { "$base\n\nDebug: $it" } ?: base
        }
}
