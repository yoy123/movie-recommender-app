package com.movierecommender.app.data.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.TimeUnit

class LlmRecommendationService(
    private val preferredModel: String = DEFAULT_MODEL
) {

    companion object {
        private const val TAG = "LlmRecommendation"
        internal const val DEFAULT_MODEL = "gpt-4.1"
        private val FALLBACK_MODELS = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o-mini")
    }

    private object SafeAndroidLog {
        fun d(tag: String, message: String): Int = runCatching { Log.d(tag, message) }.getOrDefault(0)
        fun i(tag: String, message: String): Int = runCatching { Log.i(tag, message) }.getOrDefault(0)
        fun w(tag: String, message: String): Int = runCatching { Log.w(tag, message) }.getOrDefault(0)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Candidate-constrained recommendations. The model may select only from the verified candidate list.
     */
    suspend fun getRecommendationsFromLlmCandidates(
        selectedMovies: List<String>,
        candidates: List<String>,
        genre: String,
        apiKey: String,
        indiePreference: Float = 0.5f,
        useIndiePreference: Boolean = true,
        popularityPreference: Float = 0.5f,
        usePopularityPreference: Boolean = true,
        releaseYearStart: Float = 1950f,
        releaseYearEnd: Float = 2025f,
        useReleaseYearPreference: Boolean = true,
        tonePreference: Float = 0.5f,
        useTonePreference: Boolean = true,
        internationalPreference: Float = 0.5f,
        useInternationalPreference: Boolean = true,
        experimentalPreference: Float = 0.5f,
        useExperimentalPreference: Boolean = true,
        excludedMovies: List<String> = emptyList()
    ): Result<String> {
        if (candidates.isEmpty()) return Result.success("")

        return try {
            val attempts = listOf(
                OpenAiAttempt(reasoningEffort = "minimal", extraInstructions = ""),
                OpenAiAttempt(
                    reasoningEffort = "low",
                    extraInstructions = "\n\nSTRICT RETRY (FINAL):\n" +
                        "Your previous response failed validation. Follow the rules exactly:\n" +
                        "- Start with a concise 3-4 sentence taste analysis that references the selected titles\n" +
                        "- Then output EXACTLY 15 recommendations, numbered 1..15\n" +
                        "- Every recommendation MUST be copied EXACTLY from the candidate list (same title and year)\n" +
                        "- Do NOT include any excluded/selected title\n" +
                        "- If year filtering is enabled, every year MUST be within range\n" +
                        "- Stop after item 15 (no extra text)"
                )
            )

            for ((index, attempt) in attempts.withIndex()) {
                val prompt = buildCandidateRerankPrompt(
                    selectedMovies = selectedMovies,
                    excludedMovies = excludedMovies,
                    candidates = candidates,
                    genre = genre,
                    indiePreference = indiePreference,
                    useIndiePreference = useIndiePreference,
                    popularityPreference = popularityPreference,
                    usePopularityPreference = usePopularityPreference,
                    releaseYearStart = releaseYearStart,
                    releaseYearEnd = releaseYearEnd,
                    useReleaseYearPreference = useReleaseYearPreference,
                    tonePreference = tonePreference,
                    useTonePreference = useTonePreference,
                    internationalPreference = internationalPreference,
                    useInternationalPreference = useInternationalPreference,
                    experimentalPreference = experimentalPreference,
                    useExperimentalPreference = useExperimentalPreference,
                    extraInstructions = attempt.extraInstructions
                )

                val processed = callOpenAI(
                    prompt = prompt,
                    apiKey = apiKey,
                    selectedTitles = selectedMovies,
                    excludedTitles = excludedMovies,
                    releaseYearStart = releaseYearStart,
                    releaseYearEnd = releaseYearEnd,
                    useReleaseYearPreference = useReleaseYearPreference,
                    reasoningEffort = attempt.reasoningEffort,
                    allowedCandidateTitles = candidates
                )

                if (processed.isNotBlank()) {
                    if (index > 0) {
                        SafeAndroidLog.i(TAG, "LLM candidate rerank needed retry to satisfy strict rules")
                    }
                    return Result.success(processed)
                }
            }

            Result.success("")
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private data class OpenAiAttempt(
        val reasoningEffort: String,
        val extraInstructions: String
    )

    private fun buildCandidateRerankPrompt(
        selectedMovies: List<String>,
        excludedMovies: List<String>,
        candidates: List<String>,
        genre: String,
        indiePreference: Float,
        useIndiePreference: Boolean,
        popularityPreference: Float,
        usePopularityPreference: Boolean,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        tonePreference: Float,
        useTonePreference: Boolean,
        internationalPreference: Float,
        useInternationalPreference: Boolean,
        experimentalPreference: Float,
        useExperimentalPreference: Boolean,
        extraInstructions: String
    ): String {
        val analysisMustReferenceCount = if (selectedMovies.size >= 2) 2 else 1

        SafeAndroidLog.d(TAG, "buildCandidateRerankPrompt: genre='$genre' selectedCount=${selectedMovies.size} excludedCount=${excludedMovies.size} candidates=${candidates.size}")

        // Keep the candidate-rerank prompt compact to reduce latency and avoid conflicting instructions.

        val activePreferences = mutableListOf<String>()

        if (useIndiePreference) {
            val styleGuidance = when {
                indiePreference <= 0.3f -> "Strongly prefer mainstream blockbusters. Use indie films only when they are unusually strong matches."
                indiePreference <= 0.45f -> "Lean towards blockbusters and popular studio films. Minimize indie films."
                indiePreference in 0.46f..0.54f -> "Balance mainstream hits and indie films equally."
                indiePreference <= 0.7f -> "Favor indie films and smaller productions. Include some mainstream films."
                else -> "Strongly prefer indie films and lesser-known titles. Use mainstream blockbusters only when they are unusually strong matches."
            }
            activePreferences.add("Production Style: $styleGuidance")
        }

        if (usePopularityPreference) {
            val popularityGuidance = when {
                popularityPreference <= 0.3f -> "Strongly prefer cult classics and obscure gems. Include a widely popular title only when it is an exceptional match."
                popularityPreference <= 0.45f -> "Favor cult classics and hidden gems. Minimize popular films."
                popularityPreference in 0.46f..0.54f -> "Mix mainstream and lesser-known titles equally."
                popularityPreference <= 0.7f -> "Favor well-known, widely recognized films. Include some hidden gems."
                else -> "Strongly prefer mainstream favorites. Include an obscure title only when it is an exceptional match."
            }
            activePreferences.add("Popularity: $popularityGuidance")
        }

        if (useReleaseYearPreference) {
            val startYear = releaseYearStart.toInt()
            val endYear = releaseYearEnd.toInt()
            activePreferences.add("Release Year: MANDATORY - every recommendation MUST be between $startYear and $endYear inclusive.")
        }

        if (useTonePreference) {
            val toneGuidance = when {
                tonePreference <= 0.3f -> "Strongly prefer uplifting, lighter films. Allow darker material only when it closely matches the selected titles."
                tonePreference <= 0.45f -> "Lean towards lighter films. Minimize dark themes."
                tonePreference in 0.46f..0.54f -> "Balance light and serious films equally."
                tonePreference <= 0.7f -> "Favor more serious, dramatic films. Include some lighter content."
                else -> "Strongly prefer dark, intense films. Allow lighter material only when it closely matches the selected titles."
            }
            activePreferences.add("Tone/Mood: $toneGuidance")
        }

        if (useInternationalPreference) {
            val internationalGuidance = when {
                internationalPreference <= 0.3f -> "Strongly prefer American/English-language films. Include international titles only when they are exceptional matches."
                internationalPreference <= 0.45f -> "Primarily American/English with occasional international picks."
                internationalPreference in 0.46f..0.54f -> "Include both Hollywood and international films equally."
                internationalPreference <= 0.7f -> "Favor international cinema with some Hollywood films."
                else -> "STRONGLY prioritize international cinema. Minimize Hollywood/English-language films."
            }
            activePreferences.add("Geographic: $internationalGuidance")
        }

        if (useExperimentalPreference) {
            val experimentalGuidance = when {
                experimentalPreference <= 0.3f -> "Strongly prefer traditional narrative structures. Include experimental work only when it is an exceptional match."
                experimentalPreference <= 0.45f -> "Strongly favor conventional storytelling. Minimize experimental content."
                experimentalPreference in 0.46f..0.54f -> "Mix conventional and innovative films equally."
                experimentalPreference <= 0.7f -> "Favor innovative films. Include some traditional ones."
                else -> "Strongly prefer unconventional and experimental films. Include conventional narratives only when they are exceptional matches."
            }
            activePreferences.add("Experimental: $experimentalGuidance")
        }

        val excludedSection = excludedMovies
            .mapNotNull { it.trim().takeIf { t -> t.isNotBlank() } }
            .distinct()
            .take(60)
            .takeIf { it.isNotEmpty() }
            ?.let { list ->
                "\n\nEXCLUDE TITLES (forbidden):\n" +
                    list.joinToString("\n") { "- $it" } +
                    "\n\nHard rule: Do NOT recommend any excluded title."
            }
            ?: ""

        val isFavoritesMode = genre.contains("Favorites", ignoreCase = true)
        val genreSection = if (isFavoritesMode) {
            "MODE: User's Favorites (mixed genres)\nInfer the dominant genres/themes from the selected titles below."
        } else {
            "GENRE (MANDATORY): $genre\nALL recommendations MUST be $genre films."
        }

        val preferenceSection = if (activePreferences.isNotEmpty()) {
            "\n\nPREFERENCES (mandatory):\n" + activePreferences.joinToString("\n") { "- $it" }
        } else {
            ""
        }

        val candidatesSection = candidates
            .mapNotNull { it.trim().takeIf { t -> t.isNotBlank() } }
            .distinct()
            .take(90)
            .takeIf { it.isNotEmpty() }
            ?.let { list ->
                "\n\nCANDIDATES (you MUST choose ONLY from this list):\n" +
                    list.joinToString("\n") { "- $it" }
            }
            ?: ""

        return """
You are a film curator. Generate personalized recommendations in plain text.

$genreSection

SELECTED TITLES (user taste signals):
${selectedMovies.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")}
$preferenceSection$excludedSection$candidatesSection$extraInstructions

TASK:
1) Write a comprehensive analysis of the user's overall taste based on their selections — what patterns, themes, and filmmaking qualities connect their choices.
2) Recommend exactly 15 movies, selected ONLY from the CANDIDATES list.

ANALYSIS RULES (validation-critical):
- Write 3-4 sentences providing a concise, personal analysis of the user's overall taste.
- Must explicitly mention at least $analysisMustReferenceCount of the selected titles by name in the analysis.
- Identify patterns across their selections: what themes, moods, eras, directorial styles, or narrative structures connect them.
- Be specific (pacing, structure, tone balance, cinematography, editing rhythm, sound design, performance style, narrative devices).
- Explain what these choices reveal about what the user values in cinema.
- FORBIDDEN (will cause rejection): Do NOT write generic intros like "Based on your selections...", "Here are 15 movies...", "Based off of your selected films...", or any variation. The analysis must be a genuine critical essay about their taste, not an introduction to the list.

RECOMMENDATION RULES (validation-critical):
- Output EXACTLY 15 unique movies.
- Every recommendation MUST be copied EXACTLY from the CANDIDATES list (same title and year).
- Do NOT include any selected title.
- Do NOT include any excluded title.
- If year range is enabled, every recommendation MUST be within range.
- Stop after item 15. No extra text.

OUTPUT FORMAT:
Begin with one 3-4 sentence analysis paragraph. Do not add an analysis heading.
Then write RECOMMENDATIONS: on its own line and output exactly 15 items:

1. Movie Title (YYYY)
Summary: one-sentence plot summary.

2. Movie Title (YYYY)
Summary: one-sentence plot summary.

Continue through item 15 and stop immediately after its summary.
        """.trimIndent()
    }

    private fun callOpenAI(
        prompt: String,
        apiKey: String,
        selectedTitles: List<String>,
        excludedTitles: List<String>,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        reasoningEffort: String,
        allowedCandidateTitles: List<String>?
    ): String {
        val models = buildList {
            preferredModel.trim().takeIf { it.isNotBlank() }?.let(::add)
            addAll(FALLBACK_MODELS)
        }.distinct()

        var lastFailure: OpenAiApiException? = null
        for ((modelIndex, model) in models.withIndex()) {
            SafeAndroidLog.d(TAG, "Starting OpenAI API call with model=$model")
            val json = OpenAiRequestFactory.create(
                prompt = prompt,
                reasoningEffort = reasoningEffort,
                model = model
            )
            SafeAndroidLog.d(TAG, "Request JSON length: ${json.toString().length}")

            val request = okhttp3.Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    SafeAndroidLog.d(
                        TAG,
                        "OpenAI response model=$model code=${response.code} success=${response.isSuccessful}"
                    )

                    if (!response.isSuccessful) {
                        throw parseOpenAiError(
                            statusCode = response.code,
                            responseMessage = response.message,
                            responseBody = responseBody,
                            model = model
                        )
                    }

                    if (responseBody.isBlank()) throw Exception("OpenAI returned an empty response body")
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.optJSONArray("choices")
                        ?: throw Exception("OpenAI response did not contain choices")
                    if (choices.length() == 0) throw Exception("OpenAI response contained no choices")

                    val choice = choices.getJSONObject(0)
                    val finishReason = choice.optString("finish_reason")
                    val message = choice.optJSONObject("message")
                        ?: throw Exception("OpenAI response did not contain a message")
                    val raw = message.optString("content")
                    SafeAndroidLog.d(
                        TAG,
                        "OpenAI model=$model finishReason=$finishReason contentLength=${raw.length}"
                    )
                    if (raw.isBlank()) {
                        throw Exception("OpenAI returned blank content (finish_reason=$finishReason)")
                    }

                    val processed = postProcess(
                        content = raw,
                        selectedTitles = selectedTitles,
                        excludedTitles = excludedTitles,
                        releaseYearStart = releaseYearStart,
                        releaseYearEnd = releaseYearEnd,
                        useReleaseYearPreference = useReleaseYearPreference,
                        allowedCandidateTitles = allowedCandidateTitles
                    )
                    SafeAndroidLog.d(TAG, "Post-processed content length: ${processed.length}")
                    if (processed.isEmpty()) {
                        val lines = raw.lines()
                        val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
                        val matchingLines = lines.count { numbered.containsMatchIn(it) }
                        SafeAndroidLog.w(
                            TAG,
                            "Post-processing rejected model=$model response; numberedLines=$matchingLines"
                        )
                    }
                    return processed
                }
            } catch (error: OpenAiApiException) {
                lastFailure = error
                val hasAnotherModel = modelIndex < models.lastIndex
                if (hasAnotherModel && error.canRetryWithFallbackModel) {
                    SafeAndroidLog.w(
                        TAG,
                        "OpenAI model=$model unavailable (${error.errorCode ?: error.statusCode}); trying fallback"
                    )
                    continue
                }
                throw error
            }
        }

        throw lastFailure ?: Exception("No OpenAI model was available")
    }

    private fun parseOpenAiError(
        statusCode: Int,
        responseMessage: String,
        responseBody: String,
        model: String
    ): OpenAiApiException {
        val errorObject = runCatching { JSONObject(responseBody).optJSONObject("error") }.getOrNull()
        val type = errorObject?.optString("type")?.takeIf { it.isNotBlank() }
        val code = errorObject?.optString("code")?.takeIf { it.isNotBlank() }
        val parameter = errorObject?.optString("param")?.takeIf { it.isNotBlank() && it != "null" }
        val detail = errorObject?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("(?i)bearer\\s+[^\\s]+"), "Bearer [redacted]")
            ?.replace(Regex("sk-[A-Za-z0-9_-]+"), "[redacted-key]")
            ?.take(500)
            ?: responseMessage
        return OpenAiApiException(
            statusCode = statusCode,
            errorCode = code,
            errorType = type,
            parameter = parameter,
            model = model,
            message = "OpenAI API error $statusCode for $model: $detail"
        )
    }

    private fun postProcess(
        content: String,
        selectedTitles: List<String>,
        excludedTitles: List<String>,
        releaseYearStart: Float,
        releaseYearEnd: Float,
        useReleaseYearPreference: Boolean,
        allowedCandidateTitles: List<String>?
    ): String {
        // Strip any echoed headers from the few-shot example format
        val cleaned = content.lines()
            .dropWhile { line ->
                val t = line.trim().uppercase()
                t.startsWith("YOUR ANALYSIS") || t.startsWith("EXAMPLE ANALYSIS") || t.isBlank()
            }
            .joinToString("\n")
        val lines = cleaned.lines()
        // Format: number, optional space, period, optional space (handles "1.", "1 .", "1 .Title")
        // LLM keeps varying the format despite instructions, so we tolerate all variations
        val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
        val firstIdx = lines.indexOfFirst { numbered.containsMatchIn(it) }
        // Require an analysis paragraph before numbered items.
        val rawAnalysis = if (firstIdx > 0) lines.take(firstIdx).joinToString("\n").trim() else ""
        // Strip any "YOUR ANALYSIS" or "RECOMMENDATIONS:" headers that ended up in the analysis block
        val analysisStripped = rawAnalysis.lines()
            .filterNot { line ->
                val t = line.trim().uppercase()
                t.startsWith("YOUR ANALYSIS") || t.startsWith("EXAMPLE ANALYSIS") || t == "RECOMMENDATIONS:" || t == "RECOMMENDATIONS"
            }
            .joinToString("\n").trim()
        val analysis = limitSentences(analysisStripped, 4)
        if (analysis.isBlank()) {
            return ""
        }
        // Reject if the LLM copied the example analysis verbatim
        if (analysis.contains("Mulholland Drive") && analysis.contains("Eternal Sunshine")) {
            SafeAndroidLog.w(TAG, "Analysis rejected: LLM copied the example verbatim")
            return ""
        }
        // Reject generic filler analysis that doesn't actually analyze taste
        val analysisLower = analysis.lowercase()
        val genericOpenings = listOf(
            "based on your", "based off of your", "here are", "here is a list",
            "here's a list", "heres a list", "i've selected", "i have selected", "below are"
        )
        if (genericOpenings.any { analysisLower.trimStart().startsWith(it) }) {
            SafeAndroidLog.w(TAG, "Analysis rejected as generic filler")
            return ""
        }
        // Require minimum substance (at least 3 sentences)
        val sentenceCount = Regex("[.!?]").findAll(analysis).count()
        if (sentenceCount < 3) {
            SafeAndroidLog.w(TAG, "Analysis too short ($sentenceCount sentences): ${analysis.take(100)}")
            return ""
        }
        val normalizedAnalysis = normalizeTitle(analysis)
        val referenceableSelections = selectedTitles
            .map(::normalizeTitle)
            .filter { it.length >= 4 }
            .distinct()
        val requiredReferences = when {
            referenceableSelections.isEmpty() -> 0
            referenceableSelections.size == 1 -> 1
            else -> 2
        }
        val referencedSelections = referenceableSelections.count { normalizedAnalysis.contains(it) }
        if (referencedSelections < requiredReferences) {
            SafeAndroidLog.w(TAG, "Analysis rejected: referenced $referencedSelections of $requiredReferences required selections")
            return ""
        }
        val items = mutableListOf<Pair<String, String>>()
        val seenKeys = mutableSetOf<String>()
        val seenNormalizedTitles = mutableSetOf<String>()
        val selectedNorm = selectedTitles.map { normalizeTitle(it) }.toSet()
        val excludedNorm = excludedTitles.map { normalizeTitle(it) }.toSet()

        val allowedTitleByKey: Map<String, String>? = allowedCandidateTitles
            ?.mapNotNull { candidate ->
                val year = Regex("\\((\\d{4})\\)").find(candidate)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: return@mapNotNull null
                (normalizeTitle(candidate) + year.toString()) to candidate.trim()
            }
            ?.toMap()

        val yearInParens = Regex("\\((\\d{4})\\)")
        val yearAtEnd = Regex("(\\d{4})\\s*$")
        fun extractYear(title: String): Int? {
            yearInParens.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            yearAtEnd.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            return null
        }
        val minYear = releaseYearStart.toInt()
        val maxYear = releaseYearEnd.toInt()

        var i = if (firstIdx >= 0) firstIdx else 0
        while (i < lines.size && items.size < 15) {
            val titleLine = lines[i].trim()
            if (!numbered.containsMatchIn(titleLine)) { i++; continue }
            // Clean title (remove number prefix)
            val title = titleLine.replace(numbered, "").trim()
            val year = extractYear(title)
            // Require a year for robust dedup + enforcing year filters.
            if (year == null || title.length < 3) {
                i++
                continue
            }

            if (useReleaseYearPreference && (year < minYear || year > maxYear)) {
                i++
                continue
            }
            val norm = normalizeTitle(title)
            val key = norm + year.toString()

            if (allowedTitleByKey != null && key !in allowedTitleByKey) {
                i++
                continue
            }

            // Exclude duplicates and exclude any of the user's selected titles
            if (key in seenKeys || norm in seenNormalizedTitles || norm in selectedNorm || norm in excludedNorm) {
                // Skip this title and continue scanning
                var jSkip = i + 1
                while (jSkip < lines.size) {
                    val l = lines[jSkip].trim()
                    if (l.isEmpty()) { jSkip++; continue }
                    if (numbered.containsMatchIn(l)) break
                    break
                }
                i = jSkip
                continue
            }
            var j = i + 1
            var desc = ""
            // Take the next non-empty, non-numbered line as description
            while (j < lines.size) {
                val l = lines[j].trim()
                if (l.isEmpty()) { j++; continue }
                if (numbered.containsMatchIn(l)) break
                desc = l
                break
            }
            if (desc.isBlank()) desc = "A critically acclaimed film."
            desc = desc
                .replace(Regex("^(Summary|Why this matches|Why):\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            desc = truncateWords(desc, 75)
            val canonicalTitle = allowedTitleByKey?.get(key) ?: title
                .trim()
                .trim('*', '_', '#', '"')
            seenKeys.add(key)
            seenNormalizedTitles.add(norm)
            items.add(canonicalTitle to desc)
            i = j
        }
        // If the model did not produce exactly 15 well-formed items, signal failure so the repository can fall back
        if (items.size < 15) {
            return ""
        }
        val sb = StringBuilder()
        sb.append("Analysis:\n")
        sb.append(analysis).append("\n\n")
        sb.append("RECOMMENDATIONS:\n\n")
        items.forEachIndexed { idx, (title, desc) ->
            sb.append("${idx + 1}. $title\n")
            sb.append("$desc\n\n")
        }
        return sb.toString().trim()
    }

    private fun normalizeTitle(title: String): String {
        val ascii = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return ascii
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("^(the|a|an)\\s+"), "")
            .replace(Regex("[^a-z0-9]+"), "")
            .trim()
    }
    
    private fun limitSentences(text: String, maxSentences: Int): String {
        if (text.isBlank()) return text
        val parts = Regex("(?<=[.!?])\\s+").split(text).filter { it.isNotBlank() }
        return parts.take(maxSentences).joinToString(" ").trim()
    }
    
    private fun truncateWords(text: String, maxWords: Int): String {
        val words = text.trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) text.trim() else words.take(maxWords).joinToString(" ") + "…"
    }
    

}

internal class OpenAiApiException(
    val statusCode: Int,
    val errorCode: String?,
    val errorType: String?,
    val parameter: String?,
    val model: String,
    message: String
) : Exception(message) {
    val canRetryWithFallbackModel: Boolean
        get() {
            if (errorCode == "model_not_found") return true
            if (message.orEmpty().contains("does not have access to model", ignoreCase = true)) return true
            return errorCode in setOf("unsupported_parameter", "unsupported_value") &&
                parameter in setOf(
                    "reasoning_effort",
                    "verbosity",
                    "temperature",
                    "max_completion_tokens"
                )
        }
}

internal object OpenAiRequestFactory {
    const val DEFAULT_MODEL = "gpt-4.1"

    fun create(
        prompt: String,
        reasoningEffort: String,
        model: String = DEFAULT_MODEL
    ): JSONObject {
        val spec = OpenAiRequestSpec.create(prompt, reasoningEffort, model)
        return JSONObject().apply {
            put("model", spec.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", spec.systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", spec.userPrompt)
                })
            })
            spec.reasoningEffort?.let { put("reasoning_effort", it) }
            spec.verbosity?.let { put("verbosity", it) }
            spec.temperature?.let { put("temperature", it) }
            put("max_completion_tokens", spec.maxCompletionTokens)
        }
    }
}

internal data class OpenAiRequestSpec(
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val reasoningEffort: String?,
    val verbosity: String?,
    val temperature: Double?,
    val maxCompletionTokens: Int
) {
    companion object {
        fun create(
            prompt: String,
            reasoningEffort: String,
            model: String = OpenAiRequestFactory.DEFAULT_MODEL
        ): OpenAiRequestSpec {
            val normalizedModel = model.trim().ifBlank { OpenAiRequestFactory.DEFAULT_MODEL }
            val isReasoningModel = normalizedModel.startsWith("gpt-5", ignoreCase = true) ||
                normalizedModel.startsWith("o", ignoreCase = true)
            val supportsVerbosity = normalizedModel.startsWith("gpt-5", ignoreCase = true)
            val strictRetry = reasoningEffort.equals("low", ignoreCase = true)

            return OpenAiRequestSpec(
                model = normalizedModel,
                systemPrompt = "You are an expert film curator and critic with deep knowledge of cinema history, genres, directors, and thematic elements. You excel at understanding WHY someone loves specific films and finding other films that share those same qualities. Your analysis paragraphs must be deeply personal and insightful - dissect the user's taste by naming their selected films and explaining the specific cinematic threads that connect them. NEVER write a generic intro like 'Based on your selections, here are some movies' - that will be rejected. Provide thoughtful, personalized recommendations - never generic suggestions. Write naturally in plain text, no markdown or formatting symbols.",
                userPrompt = prompt,
                reasoningEffort = reasoningEffort.takeIf { isReasoningModel },
                verbosity = "medium".takeIf { supportsVerbosity },
                temperature = if (isReasoningModel) null else if (strictRetry) 0.2 else 0.4,
                maxCompletionTokens = 4_000
            )
        }
    }
}
