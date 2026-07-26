package com.movierecommender.app.data.repository

internal object RecommendationRankingPolicy {
    private const val PRIOR_MEAN = 6.5
    private const val PRIOR_WEIGHT = 250.0

    /**
     * Shrinks ratings with very few votes toward a neutral prior so a single 10/10 vote
     * cannot outrank a broadly supported high-quality title.
     */
    fun bayesianRating(voteAverage: Double, voteCount: Int): Double {
        val safeVoteCount = voteCount.coerceAtLeast(0)
        val safeAverage = voteAverage.coerceIn(0.0, 10.0)
        return ((safeAverage * safeVoteCount) + (PRIOR_MEAN * PRIOR_WEIGHT)) /
            (safeVoteCount + PRIOR_WEIGHT)
    }
}
