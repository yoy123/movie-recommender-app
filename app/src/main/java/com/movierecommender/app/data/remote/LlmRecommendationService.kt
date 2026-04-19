package com.movierecommender.app.data.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LlmRecommendationService {
    
    companion object {
        private const val TAG = "LlmRecommendation"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Get movie recommendations from OpenAI GPT
     * @param selectedMovies List of 1-5 movies the user selected
     * @param apiKey OpenAI API key
     * @param indiePreference Float from 0.0 (blockbusters) to 1.0 (indie films)
     * @param useIndiePreference Whether to apply indie preference
     * @param popularityPreference Float from 0.0 (cult) to 1.0 (mainstream)
     * @param usePopularityPreference Whether to apply popularity preference
     * @param releaseYearStart Earliest release year to consider
     * @param releaseYearEnd Latest release year to consider
     * @param useReleaseYearPreference Whether to apply release year filter
     * @param tonePreference Float from 0.0 (light) to 1.0 (dark)
     * @param useTonePreference Whether to apply tone preference
     * @param internationalPreference Float from 0.0 (domestic) to 1.0 (international)
     * @param useInternationalPreference Whether to apply international preference
     * @param experimentalPreference Float from 0.0 (traditional) to 1.0 (experimental)
     * @param useExperimentalPreference Whether to apply experimental preference
     * @return Natural text response with recommendations
     */
    suspend fun getRecommendationsFromLlm(
        selectedMovies: List<String>,
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
        /**
         * Titles the model must NEVER recommend (favorites, currently-selected, and already-recommended).
         * Use a lightweight format like "Title (Year)"; normalization will remove years for matching.
         */
        excludedMovies: List<String> = emptyList()
    ): Result<String> {
        return try {
            // Two attempts: first is creative, second is more strict/compliant.
            val attempts = listOf(
                OpenAiAttempt(
                    // gpt-4o-mini tends to comply better with moderate temperature and penalties.
                    // Very high penalties can cause strange phrasing and format drift.
                    temperature = 0.6,
                    frequencyPenalty = 1.0,
                    presencePenalty = 0.6,
                    extraInstructions = ""
                ),
                OpenAiAttempt(
                    temperature = 0.3,
                    frequencyPenalty = 1.0,
                    presencePenalty = 0.6,
                    extraInstructions = "\n\nSTRICT RETRY (FINAL):\n" +
                        "The previous response failed validation. Follow the rules exactly:\n" +
                        "- Start with a concise 3-4 sentence taste analysis that references the selected titles\n" +
                        "- Then output EXACTLY 15 recommendations, numbered 1..15\n" +
                        "- Every recommendation MUST be in the specified GENRE - no exceptions\n" +
                        "- Every title MUST be formatted: Movie Title (YYYY)\n" +
                        "- Do NOT include any excluded/selected title\n" +
                        "- If year filtering is enabled, every year MUST be within range\n" +
                        "- Stop after item 15 (no extra text)"
                )
            )

            for ((idx, attempt) in attempts.withIndex()) {
                val prompt = buildPrompt(
                    selectedMovies = selectedMovies,
                    excludedMovies = excludedMovies,
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
                    temperature = attempt.temperature,
                    frequencyPenalty = attempt.frequencyPenalty,
                    presencePenalty = attempt.presencePenalty,
                    allowedCandidateTitles = null
                )

                if (processed.isNotBlank()) {
                    if (idx > 0) Log.i(TAG, "LLM needed retry to satisfy strict rules")
                    return Result.success(processed)
                }
            }

            // Signal failure so caller can fall back.
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Candidate-constrained recommendations.
     *
     * Instead of asking the model to invent titles, we provide a bounded candidate list (usually TMDB-derived)
     * and require it to select exactly 15 items from that list. This greatly reduces hallucinations and
     * improves relevance.
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
                OpenAiAttempt(
                    temperature = 0.4,
                    frequencyPenalty = 1.0,
                    presencePenalty = 0.6,
                    extraInstructions = ""
                ),
                OpenAiAttempt(
                    temperature = 0.2,
                    frequencyPenalty = 1.0,
                    presencePenalty = 0.6,
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

            for ((idx, attempt) in attempts.withIndex()) {
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
                    temperature = attempt.temperature,
                    frequencyPenalty = attempt.frequencyPenalty,
                    presencePenalty = attempt.presencePenalty,
                    allowedCandidateTitles = candidates
                )

                if (processed.isNotBlank()) {
                    if (idx > 0) Log.i(TAG, "LLM candidate rerank needed retry to satisfy strict rules")
                    return Result.success(processed)
                }
            }

            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildPrompt(
        selectedMovies: List<String>,
        excludedMovies: List<String>,
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

        Log.d(TAG, "buildPrompt: genre='$genre' selectedCount=${selectedMovies.size} excludedCount=${excludedMovies.size}")

        // Build active preferences list
        val activePreferences = mutableListOf<String>()
        
        // 1. Production Style (Indie vs Blockbuster)
        if (useIndiePreference) {
            val styleGuidance = when {
                indiePreference <= 0.3f -> "STRONGLY favor mainstream blockbusters, big-budget studio films, and widely distributed theatrical releases. Avoid indie films completely."
                indiePreference <= 0.45f -> "Lean heavily towards blockbusters and popular studio films. Minimize indie films."
                indiePreference in 0.46f..0.54f -> "Balance between mainstream hits and indie films equally."
                indiePreference <= 0.7f -> "Favor indie films, art house cinema, and smaller productions. Include some mainstream films."
                else -> "STRONGLY favor indie films, art house cinema, and lesser-known titles. Avoid mainstream blockbusters completely."
            }
            activePreferences.add("**Production Style**: $styleGuidance")
        }
        
        // 2. Popularity Level (Cult vs Mainstream)
        if (usePopularityPreference) {
            val popularityGuidance = when {
                popularityPreference <= 0.3f -> "STRONGLY emphasize cult classics, obscure gems, and lesser-known films with niche followings. Avoid widely popular films completely."
                popularityPreference <= 0.45f -> "Favor cult classics and hidden gems over mainstream hits. Minimize popular films."
                popularityPreference in 0.46f..0.54f -> "Mix popular mainstream films with lesser-known quality titles equally."
                popularityPreference <= 0.7f -> "Favor well-known, widely recognized films. Include some cult classics."
                else -> "STRONGLY focus on blockbuster hits, universally known films, and mainstream favorites. Avoid obscure titles completely."
            }
            activePreferences.add("**Popularity Level**: $popularityGuidance")
        }
        
        // 3. Release Year Range
        if (useReleaseYearPreference) {
            val startYear = releaseYearStart.toInt()
            val endYear = releaseYearEnd.toInt()
            activePreferences.add("**Release Year**: MANDATORY - Every single recommendation MUST be released between $startYear and $endYear inclusive. Do NOT recommend films outside this range.")
        }
        
        // 4. Tone/Mood
        if (useTonePreference) {
            val toneGuidance = when {
                tonePreference <= 0.3f -> "STRONGLY favor uplifting, feel-good films with lighter themes, comedy, and positive outcomes. Avoid dark content completely."
                tonePreference <= 0.45f -> "Lean towards lighter, more uplifting films. Minimize dark themes."
                tonePreference in 0.46f..0.54f -> "Balance between light-hearted entertainment and serious dramatic fare equally."
                tonePreference <= 0.7f -> "Favor more serious, dramatic films. Include some lighter content."
                else -> "STRONGLY favor dark, intense, thought-provoking films with serious themes and complex subject matter. Avoid lighthearted content completely."
            }
            activePreferences.add("**Tone/Mood**: $toneGuidance")
        }
        
        // 5. International vs Domestic
        if (useInternationalPreference) {
            val internationalGuidance = when {
                internationalPreference <= 0.3f -> "ONLY recommend American and English-language films. Completely avoid foreign language films."
                internationalPreference <= 0.45f -> "Primarily American/English films with occasional international exceptions."
                internationalPreference in 0.46f..0.54f -> "Include both Hollywood productions and international films equally."
                internationalPreference <= 0.7f -> "Favor international cinema with some Hollywood films."
                else -> "STRONGLY prioritize international cinema, foreign language films, and world cinema. Minimize Hollywood/English-language films."
            }
            activePreferences.add("**Geographic Focus**: $internationalGuidance")
        }
        
        // 6. Experimental vs Traditional
        if (useExperimentalPreference) {
            val experimentalGuidance = when {
                experimentalPreference <= 0.3f -> "ONLY recommend traditional narrative structures with conventional filmmaking. Completely avoid experimental films."
                experimentalPreference <= 0.45f -> "Strongly favor traditional storytelling and conventional approaches. Minimize experimental content."
                experimentalPreference in 0.46f..0.54f -> "Mix traditional storytelling with innovative creative filmmaking equally."
                experimentalPreference <= 0.7f -> "Favor creative, innovative films with unique approaches. Include some traditional films."
                else -> "STRONGLY prioritize avant-garde, unconventional films with experimental techniques. Avoid conventional narratives completely."
            }
            activePreferences.add("**Storytelling Style**: $experimentalGuidance")
        }

        // Help the model self-check: include the raw slider values and which toggles are enabled.
        // This improves adherence without changing the UI-visible output.
        val settingsSnapshot = "\nSETTINGS (for compliance):\n" + listOf(
            "- indie=${String.format("%.2f", indiePreference)} enabled=$useIndiePreference",
            "- popularity=${String.format("%.2f", popularityPreference)} enabled=$usePopularityPreference",
            "- year_range=${releaseYearStart.toInt()}-${releaseYearEnd.toInt()} enabled=$useReleaseYearPreference",
            "- tone=${String.format("%.2f", tonePreference)} enabled=$useTonePreference",
            "- international=${String.format("%.2f", internationalPreference)} enabled=$useInternationalPreference",
            "- experimental=${String.format("%.2f", experimentalPreference)} enabled=$useExperimentalPreference"
        ).joinToString("\n")
        
        val preferencesSection = if (activePreferences.isNotEmpty()) {
            "\n\nPREFERENCES (mandatory):\n" +
                activePreferences.joinToString("\n\n") { "- $it" } +
                "\n\nHard rules:\n" +
                "- Every recommendation MUST satisfy every enabled preference\n" +
                "- If a preference says avoid X, do not include X"
        } else {
            ""
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

        // Detect if this is favorites mode (genre name contains "Favorites")
        val isFavoritesMode = genre.contains("Favorites", ignoreCase = true)
        Log.d(TAG, "buildPrompt: isFavoritesMode=$isFavoritesMode")
        
        val genreSection = if (isFavoritesMode) {
            // In favorites mode, infer genres from selected movies rather than enforcing one genre
            """
MODE: User's Favorites (mixed genres)
Infer the genres and themes from the selected titles below. Match the dominant genres/themes of those selections.
            """.trimIndent()
        } else {
            // Normal genre mode - enforce the genre strictly
            """
GENRE (MANDATORY): $genre
ALL recommendations MUST be $genre films. Do NOT recommend movies from other genres.
            """.trimIndent()
        }

        val genreConstraintRule = if (isFavoritesMode) {
            "- GENRE: Match the genres/themes of the selected titles. If they selected Horror, recommend Horror. If mixed, recommend similar mixes."
        } else {
            "- GENRE CONSTRAINT: Every recommendation MUST belong to the $genre genre. Do NOT recommend movies from other genres, even if they share thematic elements."
        }

        Log.d(TAG, "buildPrompt: genreConstraintRule='${genreConstraintRule.take(140)}'")
        
    return """
You are a film curator. Generate personalized recommendations in plain text.

$genreSection

SELECTED TITLES (user taste signals):
${selectedMovies.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")}
$settingsSnapshot$preferencesSection$excludedSection$extraInstructions

TASK:
1) Write a comprehensive analysis of the user's overall taste based on their selections — what patterns, themes, and filmmaking qualities connect their choices.
2) Recommend exactly 15 movies that match the same underlying qualities.

ANALYSIS RULES (validation-critical):
- Write 3-4 sentences providing a concise, personal analysis of the user's overall taste.
- Must explicitly mention at least $analysisMustReferenceCount of the selected titles by name in the analysis.
- Identify patterns across their selections: what themes, moods, eras, directorial styles, or narrative structures connect them.
- Be specific: reference concrete filmmaking elements (e.g., pacing, structure, cinematography approach, tone balance, editing rhythm, sound design, performance style, narrative devices).
- Explain what these choices reveal about what the user values in cinema.
- FORBIDDEN (will cause rejection): Do NOT write generic intros like "Based on your selections...", "Here are 15 movies...", "Based off of your selected films...", or any variation. The analysis must be a genuine critical essay about their taste, not an introduction to the list.
- Avoid generic filler like "compelling storytelling" or "complex characters".

RECOMMENDATION RULES (validation-critical):
- Output EXACTLY 15 unique movies.
$genreConstraintRule
- Do NOT include any selected title.
- Do NOT include any excluded title.
- If year range is enabled, every recommendation MUST be within range.
- Prefer strong matches over obvious genre staples.
- Avoid sequels/spin-offs/franchise entries unless they are truly essential AND still satisfy preferences.

OUTPUT FORMAT (must match exactly — follow the EXAMPLE ANALYSIS style, not a generic intro):

EXAMPLE ANALYSIS (for illustration only — yours must reference the ACTUAL selected titles above):
The thread connecting Mulholland Drive and Eternal Sunshine of the Spotless Mind is an obsession with fractured identity and the unreliability of memory as narrative device. Both films weaponize non-linear editing to mirror psychological disintegration — Lynch through surrealist dream logic and Gondry through a collapsing visual landscape that literalizes emotional erasure. This points to a viewer who values atmosphere and formal ambition over conventional storytelling clarity, someone drawn to films that demand active interpretation and reward multiple viewings.

YOUR ANALYSIS (write about the actual selected titles above, not the example):
[Your 3-4 sentence analysis here — must reference the user's actual selected titles]

RECOMMENDATIONS:

1. Movie Title (YYYY)
Summary: one-sentence plot summary of the movie.

2. Movie Title (YYYY)
Summary: one-sentence plot summary.

(Continue until 15; stop immediately after item 15.)
        """.trimIndent()
    }
    
    private data class OpenAiAttempt(
        val temperature: Double,
        val frequencyPenalty: Double,
        val presencePenalty: Double,
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

        Log.d(TAG, "buildCandidateRerankPrompt: genre='$genre' selectedCount=${selectedMovies.size} excludedCount=${excludedMovies.size} candidates=${candidates.size}")

        // Reuse the existing preference wording by calling buildPrompt and then overriding the task section.
        // However, to avoid prompt bloat and accidental conflicts, we rebuild a slim prompt here.

        val activePreferences = mutableListOf<String>()

        if (useIndiePreference) {
            val styleGuidance = when {
                indiePreference <= 0.3f -> "STRONGLY favor mainstream blockbusters. Avoid indie films completely."
                indiePreference <= 0.45f -> "Lean towards blockbusters and popular studio films. Minimize indie films."
                indiePreference in 0.46f..0.54f -> "Balance mainstream hits and indie films equally."
                indiePreference <= 0.7f -> "Favor indie films and smaller productions. Include some mainstream films."
                else -> "STRONGLY favor indie films and lesser-known titles. Avoid mainstream blockbusters completely."
            }
            activePreferences.add("Production Style: $styleGuidance")
        }

        if (usePopularityPreference) {
            val popularityGuidance = when {
                popularityPreference <= 0.3f -> "STRONGLY emphasize cult classics and obscure gems. Avoid widely popular films completely."
                popularityPreference <= 0.45f -> "Favor cult classics and hidden gems. Minimize popular films."
                popularityPreference in 0.46f..0.54f -> "Mix mainstream and lesser-known titles equally."
                popularityPreference <= 0.7f -> "Favor well-known, widely recognized films. Include some hidden gems."
                else -> "STRONGLY focus on mainstream favorites. Avoid obscure titles completely."
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
                tonePreference <= 0.3f -> "STRONGLY favor uplifting, lighter films. Avoid dark content completely."
                tonePreference <= 0.45f -> "Lean towards lighter films. Minimize dark themes."
                tonePreference in 0.46f..0.54f -> "Balance light and serious films equally."
                tonePreference <= 0.7f -> "Favor more serious, dramatic films. Include some lighter content."
                else -> "STRONGLY favor dark, intense films. Avoid lighthearted content completely."
            }
            activePreferences.add("Tone/Mood: $toneGuidance")
        }

        if (useInternationalPreference) {
            val internationalGuidance = when {
                internationalPreference <= 0.3f -> "ONLY recommend American/English-language films. Avoid foreign language films."
                internationalPreference <= 0.45f -> "Primarily American/English with occasional international picks."
                internationalPreference in 0.46f..0.54f -> "Include both Hollywood and international films equally."
                internationalPreference <= 0.7f -> "Favor international cinema with some Hollywood films."
                else -> "STRONGLY prioritize international cinema. Minimize Hollywood/English-language films."
            }
            activePreferences.add("Geographic: $internationalGuidance")
        }

        if (useExperimentalPreference) {
            val experimentalGuidance = when {
                experimentalPreference <= 0.3f -> "ONLY traditional narrative structures. Avoid experimental films."
                experimentalPreference <= 0.45f -> "Strongly favor conventional storytelling. Minimize experimental content."
                experimentalPreference in 0.46f..0.54f -> "Mix conventional and innovative films equally."
                experimentalPreference <= 0.7f -> "Favor innovative films. Include some traditional ones."
                else -> "STRONGLY prioritize unconventional/experimental films. Avoid conventional narratives completely."
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

OUTPUT FORMAT (must match exactly — follow the EXAMPLE ANALYSIS style, not a generic intro):

EXAMPLE ANALYSIS (for illustration only — yours must reference the ACTUAL selected titles above):
The thread connecting Mulholland Drive and Eternal Sunshine of the Spotless Mind is an obsession with fractured identity and the unreliability of memory as narrative device. Both films weaponize non-linear editing to mirror psychological disintegration — Lynch through surrealist dream logic and Gondry through a collapsing visual landscape that literalizes emotional erasure. This points to a viewer who values atmosphere and formal ambition over conventional storytelling clarity, someone drawn to films that demand active interpretation and reward multiple viewings.

YOUR ANALYSIS (write about the actual selected titles above, not the example):
[Your 3-4 sentence analysis here — must reference the user's actual selected titles]

RECOMMENDATIONS:

1. Movie Title (YYYY)
Summary: one-sentence plot summary of the movie.

2. Movie Title (YYYY)
Summary: one-sentence plot summary.

(Continue until 15; stop immediately after item 15.)
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
        temperature: Double,
        frequencyPenalty: Double,
        presencePenalty: Double,
        allowedCandidateTitles: List<String>?
    ): String {
        Log.d(TAG, "Starting OpenAI API call")
        Log.d(TAG, "API Key length: ${apiKey.length}, starts with: ${apiKey.take(10)}...")
        Log.d(TAG, "Prompt preview (first 900 chars): ${prompt.take(900)}")
        
        val json = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an expert film curator and critic with deep knowledge of cinema history, genres, directors, and thematic elements. You excel at understanding WHY someone loves specific films and finding other films that share those same qualities. Your analysis paragraphs must be deeply personal and insightful - dissect the user's taste by naming their selected films and explaining the specific cinematic threads that connect them. NEVER write a generic intro like 'Based on your selections, here are some movies' - that will be rejected. Provide thoughtful, personalized recommendations - never generic suggestions. Write naturally in plain text, no markdown or formatting symbols.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", temperature)
            put("max_tokens", 2000)             // More room for comprehensive analysis + quality descriptions
            put("frequency_penalty", frequencyPenalty)       // Strong penalty to avoid repetitive phrasing
            put("presence_penalty", presencePenalty)        // Encourage diverse vocabulary and ideas
        }
        
        Log.d(TAG, "Request JSON length: ${json.toString().length}")
        
        val request = okhttp3.Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        Log.d(TAG, "Executing HTTP request to OpenAI...")
        
        client.newCall(request).execute().use { response ->
            Log.d(TAG, "Received response - Code: ${response.code}, Success: ${response.isSuccessful}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "OpenAI API error: ${response.code} - ${response.message}")
                Log.e(TAG, "Error body: $errorBody")
                throw Exception("OpenAI API error: ${response.code} - ${response.message} - $errorBody")
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "Response body length: ${responseBody.length}")
            Log.d(TAG, "Response preview: ${responseBody.take(300)}...")
            
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            Log.d(TAG, "Number of choices: ${choices.length()}")
            
            val message = choices.getJSONObject(0).getJSONObject("message")
            val raw = message.getString("content")
            Log.d(TAG, "Raw LLM content length: ${raw.length}")
            Log.d(TAG, "Raw LLM content preview: ${raw.take(200)}...")
            Log.d(TAG, "=== FULL RAW LLM RESPONSE ===")
            Log.d(TAG, raw)
            Log.d(TAG, "=== END FULL RAW RESPONSE ===")
            
            val processed = postProcess(
                content = raw,
                selectedTitles = selectedTitles,
                excludedTitles = excludedTitles,
                releaseYearStart = releaseYearStart,
                releaseYearEnd = releaseYearEnd,
                useReleaseYearPreference = useReleaseYearPreference,
                allowedCandidateTitles = allowedCandidateTitles
            )
            Log.d(TAG, "Post-processed content length: ${processed.length}")
            if (processed.isEmpty()) {
                Log.w(TAG, "Post-processing returned empty string - validation will fail")
                Log.w(TAG, "Checking why post-processing failed...")
                val lines = raw.lines()
                val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
                val matchingLines = lines.filter { numbered.containsMatchIn(it) }
                Log.w(TAG, "Found ${matchingLines.size} lines matching numbered format")
                if (matchingLines.size < 15) {
                    Log.e(TAG, "Only found ${matchingLines.size} valid items, need 15!")
                    Log.e(TAG, "All numbered lines:")
                    lines.filter { it.trim().matches(Regex("^\\d.*")) }.forEach { 
                        Log.e(TAG, "  '$it'") 
                    }
                }
            }
            
            return processed
        }
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
            Log.w(TAG, "Analysis rejected: LLM copied the example verbatim")
            return ""
        }
        // Reject generic filler analysis that doesn't actually analyze taste
        val analysisLower = analysis.lowercase()
        val genericPatterns = listOf(
            "based on your", "based off of your", "here are", "here is a list",
            "here's a list", "heres a list", "i've selected", "i have selected",
            "the following", "below are", "these recommendations"
        )
        if (genericPatterns.any { analysisLower.contains(it) }) {
            Log.w(TAG, "Analysis rejected as generic filler: ${analysis.take(100)}")
            return ""
        }
        // Require minimum substance (at least 3 sentences)
        val sentenceCount = Regex("[.!?]").findAll(analysis).count()
        if (sentenceCount < 3) {
            Log.w(TAG, "Analysis too short ($sentenceCount sentences): ${analysis.take(100)}")
            return ""
        }
        val items = mutableListOf<Pair<String, String>>()
        val seenKeys = mutableSetOf<String>()
        val selectedNorm = selectedTitles.map { normalizeTitle(it) }.toSet()
        val excludedNorm = excludedTitles.map { normalizeTitle(it) }.toSet()

        val allowedKeys: Set<String>? = allowedCandidateTitles
            ?.mapNotNull { t ->
                val y = Regex("\\((\\d{4})\\)").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (y == null) return@mapNotNull null
                normalizeTitle(t) + y.toString()
            }
            ?.toSet()

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

            if (allowedKeys != null && key !in allowedKeys) {
                i++
                continue
            }

            // Exclude duplicates and exclude any of the user's selected titles
            if (key in seenKeys || norm in selectedNorm || norm in excludedNorm) {
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
            desc = truncateWords(desc, 75)
            seenKeys.add(key)
            items.add(title to desc)
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
        // Lowercase, remove year, leading articles, and non-alphanumeric for robust dedup
        return title
            .lowercase()
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
