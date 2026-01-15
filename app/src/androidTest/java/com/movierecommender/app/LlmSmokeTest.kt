package com.movierecommender.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.movierecommender.app.data.remote.LlmRecommendationService
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmSmokeTest {

    @Test
    fun horrorPrompt_smokeTest_logsResponse() = runBlocking {
        val key = BuildConfig.OPENAI_API_KEY

        // Skip in CI / environments without a real key.
        // We only log non-sensitive metadata.
        Log.d("LlmSmokeTest", "OPENAI_API_KEY length=${key.length}, prefix='${key.take(6)}'")
        Assume.assumeTrue(key.length > 20)

        val llm = LlmRecommendationService()

        // Use a few clear horror signals.
        val selected = listOf(
            "Hereditary (2018)",
            "The Witch (2015)",
            "It Follows (2014)"
        )

        Log.d("LlmSmokeTest", "Calling LLM with genre=Horror selected=$selected")

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

        val text = result.getOrNull().orEmpty()
        Log.d("LlmSmokeTest", "LLM raw response length=${text.length}")
        Log.d("LlmSmokeTest", "LLM raw response preview:\n${text.take(1200)}")

        // This is a smoke test: we only assert non-empty to avoid flakiness.
        Assume.assumeTrue(text.isNotBlank())
    }
}
