package com.movierecommender.app

import com.movierecommender.app.data.repository.RecommendationRankingPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRankingPolicyTest {

    @Test
    fun broadlySupportedEightOutranksSingleVoteTen() {
        val broadlySupported = RecommendationRankingPolicy.bayesianRating(
            voteAverage = 8.0,
            voteCount = 5_000
        )
        val oneVotePerfect = RecommendationRankingPolicy.bayesianRating(
            voteAverage = 10.0,
            voteCount = 1
        )

        assertTrue(broadlySupported > oneVotePerfect)
    }

    @Test
    fun invalidRatingInputsAreClamped() {
        val result = RecommendationRankingPolicy.bayesianRating(
            voteAverage = 15.0,
            voteCount = -5
        )

        assertTrue(result in 0.0..10.0)
    }
}
