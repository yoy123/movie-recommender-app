package com.movierecommender.app

import com.movierecommender.app.data.remote.LlmRecommendationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LlmSmokeTest {

    @Test
    fun candidateRerank_reachesOpenAiAndReturnsValidatedRecommendations() = runBlocking {
        // This test is intentionally opt-in because it calls a paid external service and depends on network access.
        // Enable it locally with either:
        // - env RUN_LLM_SMOKE_TESTS=1
        // - JVM property -DrunLlmSmokeTests=true
        val enabled = (System.getenv("RUN_LLM_SMOKE_TESTS") == "1") ||
            (System.getProperty("runLlmSmokeTests") == "true")
        assumeTrue(
            "Skipping LlmSmokeTest (set RUN_LLM_SMOKE_TESTS=1 or -DrunLlmSmokeTests=true to enable)",
            enabled
        )

        val key = BuildConfig.OPENAI_API_KEY
        assumeTrue(
            "Skipping LlmSmokeTest (missing/invalid OPENAI_API_KEY in local.properties)",
            key.startsWith("sk-") && key.length > 20
        )

        val selected = listOf(
            "Hereditary (2018)",
            "The Witch (2015)",
            "It Follows (2014)"
        )
        val candidates = listOf(
            "The Babadook (2014)",
            "Midsommar (2019)",
            "The Lighthouse (2019)",
            "Saint Maud (2019)",
            "The Blackcoat's Daughter (2015)",
            "A Dark Song (2016)",
            "The Night House (2020)",
            "The Lodge (2019)",
            "Relic (2020)",
            "The Wailing (2016)",
            "The Ritual (2017)",
            "The Autopsy of Jane Doe (2016)",
            "The Dark and the Wicked (2020)",
            "The Invitation (2015)",
            "The Killing of a Sacred Deer (2017)",
            "The Empty Man (2020)",
            "Possum (2018)",
            "His House (2020)",
            "The Medium (2021)",
            "The Innocents (2021)"
        )

        val requestedModel = System.getenv("LLM_SMOKE_MODEL")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENAI_MODEL
        println("LlmSmokeTest: model=$requestedModel selectedCount=${selected.size} candidateCount=${candidates.size}")

        val result = LlmRecommendationService(requestedModel)
            .getRecommendationsFromLlmCandidates(
                selectedMovies = selected,
                candidates = candidates,
                genre = "Horror",
                apiKey = key,
                indiePreference = 0.5f,
                useIndiePreference = false,
                popularityPreference = 0.5f,
                usePopularityPreference = false,
                releaseYearStart = 2014f,
                releaseYearEnd = 2021f,
                useReleaseYearPreference = true,
                tonePreference = 0.5f,
                useTonePreference = false,
                internationalPreference = 0.5f,
                useInternationalPreference = false,
                experimentalPreference = 0.5f,
                useExperimentalPreference = false,
                excludedMovies = selected
            )

        val failure = result.exceptionOrNull()
        assertTrue(
            "OpenAI smoke request failed: ${failure?.javaClass?.simpleName}: ${failure?.message}",
            failure == null
        )

        val text = result.getOrNull().orEmpty()
        println("LlmSmokeTest: responseLength=${text.length} recommendationLines=${text.lines().count { it.matches(Regex("^\\d{1,2}\\. .*")) }}")
        println("LlmSmokeTest: responsePreview=\n${text.take(1200)}")
        assertTrue("Expected validated non-empty AI recommendations", text.isNotBlank())
    }
}
