package com.movierecommender.app

import com.movierecommender.app.data.model.RecommendationFallbackReason
import com.movierecommender.app.data.model.RecommendationResult
import com.movierecommender.app.data.model.RecommendationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationResultTest {

    @Test
    fun aiResult_hasNoFallbackNotice() {
        val result = RecommendationResult(
            text = "recommendations",
            source = RecommendationSource.AI
        )

        assertNull(result.fallbackNotice)
    }

    @Test
    fun tmdbFallback_explainsReasonAndIncludesSafeDiagnostic() {
        val result = RecommendationResult(
            text = "fallback recommendations",
            source = RecommendationSource.TMDB_FALLBACK,
            fallbackReason = RecommendationFallbackReason.INSUFFICIENT_SAFE_CANDIDATES,
            diagnostic = "safe candidate count=12; required=15"
        )

        val notice = result.fallbackNotice.orEmpty()
        assertTrue(notice.contains("TMDB"))
        assertTrue(notice.contains("safe candidate count=12"))
    }

    @Test
    fun fallbackWithoutDiagnostic_stillHasActionableMessage() {
        val result = RecommendationResult(
            text = "fallback recommendations",
            source = RecommendationSource.TMDB_FALLBACK,
            fallbackReason = RecommendationFallbackReason.OPENAI_REQUEST_FAILED
        )

        assertEquals(
            RecommendationFallbackReason.OPENAI_REQUEST_FAILED.userMessage,
            result.fallbackNotice
        )
    }
}
