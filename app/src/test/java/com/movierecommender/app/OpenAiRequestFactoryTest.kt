package com.movierecommender.app

import com.movierecommender.app.data.remote.OpenAiRequestSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiRequestFactoryTest {

    @Test
    fun create_buildsGpt5CompatibleChatCompletionBody() {
        val request = OpenAiRequestSpec.create("Recommend films", "minimal")

        assertEquals("gpt-5", request.model)
        assertEquals("minimal", request.reasoningEffort)
        assertEquals("medium", request.verbosity)
        assertEquals(4_000, request.maxCompletionTokens)
        assertEquals("Recommend films", request.userPrompt)
    }
}