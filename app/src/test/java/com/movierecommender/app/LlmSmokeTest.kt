package com.movierecommender.app

import com.movierecommender.app.data.remote.LlmRecommendationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test

class LlmSmokeTest {

    @Test
    fun horrorPrompt_smokeTest_logsResponse() = runBlocking {
        // This test is intentionally opt-in because it calls a paid external service and depends on network access.
        // Enable it locally with either:
        // - env RUN_LLM_SMOKE_TESTS=1
        // - JVM property -DrunLlmSmokeTests=true
        val enabled = (System.getenv("RUN_LLM_SMOKE_TESTS") == "1") || (System.getProperty("runLlmSmokeTests") == "true")
        assumeTrue("Skipping LlmSmokeTest (set RUN_LLM_SMOKE_TESTS=1 or -DrunLlmSmokeTests=true to enable)", enabled)

        val key = BuildConfig.OPENAI_API_KEY

        // Skip unless the key looks real.
        assumeTrue(
            "Skipping LlmSmokeTest (missing/invalid OPENAI_API_KEY in local.properties)",
            key.startsWith("sk-") && key.length > 20
        )

        // Skip in CI / environments without a real key.
        // We only log non-sensitive metadata.
        println("LlmSmokeTest: OPENAI_API_KEY length=${key.length}, prefix='${key.take(6)}'")
        if (key.length <= 20) return@runBlocking

        val llm = LlmRecommendationService()

        // Use a few clear horror signals.
        val selected = listOf(
            "Hereditary (2018)",
            "The Witch (2015)",
            "It Follows (2014)"
        )

        println("LlmSmokeTest: Calling LLM with genre=Horror selected=$selected")

        val result = llm.getRecommendationsFromLlm(
            selectedMovies = selected,
            genre = "Horror",
            apiKey = key,
            // Keep sliders neutral to reduce confounding variables.
            indiePreference = 0.5f,
            useIndiePreference = false,
            popularityPreference = 0.5f,
            usePopularityPreference = false,
            releaseYearStart = 1950f,
            releaseYearEnd = 2025f,
            useReleaseYearPreference = true,
            tonePreference = 0.5f,
            useTonePreference = false,
            internationalPreference = 0.5f,
            useInternationalPreference = false,
            experimentalPreference = 0.5f,
            useExperimentalPreference = false,
            excludedMovies = emptyList()
        )

        // If the call failed (network/auth/rate-limit), don't fail the build; treat as skipped.
        val failure = result.exceptionOrNull()
        if (failure != null) {
            println("LlmSmokeTest: LLM call failed: ${failure::class.simpleName}: ${failure.message}")
            assumeNoException(failure)
        }

        val text = result.getOrNull().orEmpty()
        println("LlmSmokeTest: LLM raw response length=${text.length}")
        println("LlmSmokeTest: LLM raw response preview:\n${text.take(1200)}")

        // This is a smoke test: we only assert non-empty to avoid flakiness.
        assertTrue("Expected non-empty LLM response", text.isNotBlank())
    }
}
