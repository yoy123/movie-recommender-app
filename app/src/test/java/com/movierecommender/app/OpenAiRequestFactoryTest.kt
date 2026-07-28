package com.movierecommender.app

import com.movierecommender.app.data.remote.OpenAiApiException
import com.movierecommender.app.data.remote.OpenAiRequestFactory
import com.movierecommender.app.data.remote.OpenAiRequestSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRequestFactoryTest {

    @Test
    fun create_buildsDefaultGpt41CompatibleBody() {
        val request = OpenAiRequestSpec.create("Recommend films", "minimal")
        val json = OpenAiRequestFactory.create("Recommend films", "minimal")

        assertEquals("gpt-4.1", request.model)
        assertNull(request.reasoningEffort)
        assertNull(request.verbosity)
        assertEquals(0.4, request.temperature!!, 0.0)
        assertEquals(4_000, request.maxCompletionTokens)
        assertEquals("Recommend films", request.userPrompt)
        assertFalse(json.has("reasoning_effort"))
        assertFalse(json.has("verbosity"))
        assertTrue(json.has("temperature"))
    }

    @Test
    fun create_keepsReasoningFieldsForGpt5WhenConfigured() {
        val request = OpenAiRequestSpec.create("Recommend films", "minimal", "gpt-5")
        val json = OpenAiRequestFactory.create("Recommend films", "minimal", "gpt-5")

        assertEquals("gpt-5", request.model)
        assertEquals("minimal", request.reasoningEffort)
        assertEquals("medium", request.verbosity)
        assertNull(request.temperature)
        assertTrue(json.has("reasoning_effort"))
        assertTrue(json.has("verbosity"))
        assertFalse(json.has("temperature"))
    }

    @Test
    fun create_usesLowerTemperatureForStrictNonReasoningRetry() {
        val request = OpenAiRequestSpec.create("Recommend films", "low", "gpt-4.1-mini")

        assertEquals(0.2, request.temperature!!, 0.0)
    }

    @Test
    fun modelNotFound_isEligibleForAutomaticModelFallback() {
        val error = OpenAiApiException(
            statusCode = 403,
            errorCode = "model_not_found",
            errorType = "invalid_request_error",
            parameter = null,
            model = "gpt-5",
            message = "Project does not have access to model gpt-5"
        )

        assertTrue(error.canRetryWithFallbackModel)

        val unsupportedField = OpenAiApiException(
            statusCode = 400,
            errorCode = "unsupported_value",
            errorType = "invalid_request_error",
            parameter = "verbosity",
            model = "gpt-4.1-mini",
            message = "Unsupported verbosity value"
        )
        assertTrue(unsupportedField.canRetryWithFallbackModel)
    }

    @Test
    fun authenticationFailure_doesNotCycleThroughModels() {
        val error = OpenAiApiException(
            statusCode = 401,
            errorCode = "invalid_api_key",
            errorType = "invalid_request_error",
            parameter = null,
            model = "gpt-4.1",
            message = "Incorrect API key"
        )

        assertFalse(error.canRetryWithFallbackModel)
    }
}
