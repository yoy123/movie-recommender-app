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
        useExperimentalPreference: Boolean = true
    ): Result<String> {
        return try {
            val prompt = buildPrompt(
                selectedMovies,
                genre,
                indiePreference,
                useIndiePreference,
                popularityPreference,
                usePopularityPreference,
                releaseYearStart,
                releaseYearEnd,
                useReleaseYearPreference,
                tonePreference,
                useTonePreference,
                internationalPreference,
                useInternationalPreference,
                experimentalPreference,
                useExperimentalPreference
            )
            val response = callOpenAI(prompt, apiKey, selectedMovies)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildPrompt(
        selectedMovies: List<String>,
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
        useExperimentalPreference: Boolean
    ): String {
        // Build active preferences list
        val activePreferences = mutableListOf<String>()
        
        // 1. Production Style (Indie vs Blockbuster)
        if (useIndiePreference) {
            val styleGuidance = when {
                indiePreference < 0.4f -> "STRONGLY favor mainstream blockbusters, big-budget studio films, and widely distributed theatrical releases. Avoid indie films."
                indiePreference < 0.45f -> "Lean heavily towards blockbusters and popular studio films."
                indiePreference < 0.55f -> "Balance between mainstream hits and indie films."
                indiePreference < 0.6f -> "Lean towards indie films, art house cinema, and smaller productions."
                else -> "STRONGLY favor indie films, art house cinema, hidden gems, and lesser-known titles. Avoid mainstream blockbusters."
            }
            activePreferences.add("Production Style: $styleGuidance")
        }
        
        // 2. Popularity Level (Cult vs Mainstream)
        if (usePopularityPreference) {
            val popularityGuidance = when {
                popularityPreference < 0.4f -> "STRONGLY emphasize cult classics, obscure gems, and lesser-known films with niche followings. Avoid widely popular films."
                popularityPreference < 0.45f -> "Favor cult classics and hidden gems over mainstream hits."
                popularityPreference < 0.55f -> "Mix popular mainstream films with lesser-known quality titles."
                popularityPreference < 0.6f -> "Favor well-known, widely recognized films."
                else -> "STRONGLY focus on blockbuster hits, universally known films, and mainstream favorites. Avoid obscure titles."
            }
            activePreferences.add("Popularity Level: $popularityGuidance")
        }
        
        // 3. Release Year Range
        if (useReleaseYearPreference) {
            val startYear = releaseYearStart.toInt()
            val endYear = releaseYearEnd.toInt()
            activePreferences.add("Release Year: Only recommend films released between $startYear and $endYear.")
        }
        
        // 4. Tone/Mood
        if (useTonePreference) {
            val toneGuidance = when {
                tonePreference < 0.4f -> "STRONGLY favor uplifting, feel-good films with lighter themes, comedy, and positive outcomes. Avoid dark content."
                tonePreference < 0.45f -> "Lean towards lighter, more uplifting films."
                tonePreference < 0.55f -> "Balance between light-hearted entertainment and serious dramatic fare."
                tonePreference < 0.6f -> "Lean towards more serious, dramatic films."
                else -> "STRONGLY favor dark, intense, thought-provoking films with serious themes and complex subject matter. Avoid lighthearted content."
            }
            activePreferences.add("Tone/Mood: $toneGuidance")
        }
        
        // 5. International vs Domestic
        if (useInternationalPreference) {
            val internationalGuidance = when {
                internationalPreference < 0.4f -> "ONLY recommend American and English-language films. Completely avoid foreign language films."
                internationalPreference < 0.45f -> "Primarily recommend American/English films with rare international exceptions."
                internationalPreference < 0.55f -> "Include both Hollywood productions and notable international films."
                internationalPreference < 0.6f -> "Favor international cinema with some Hollywood films."
                else -> "STRONGLY prioritize international cinema, foreign language films, and world cinema from diverse countries. Minimize Hollywood films."
            }
            activePreferences.add("Geographic Focus: $internationalGuidance")
        }
        
        // 6. Experimental vs Traditional
        if (useExperimentalPreference) {
            val experimentalGuidance = when {
                experimentalPreference < 0.4f -> "ONLY recommend traditional narrative structures with conventional filmmaking. Completely avoid experimental films."
                experimentalPreference < 0.45f -> "Strongly favor traditional storytelling and conventional approaches."
                experimentalPreference < 0.55f -> "Mix traditional storytelling with some innovative and creative filmmaking."
                experimentalPreference < 0.6f -> "Favor creative, innovative films with unique approaches."
                else -> "STRONGLY prioritize avant-garde, unconventional films with experimental techniques and non-traditional storytelling. Avoid conventional narratives."
            }
            activePreferences.add("Storytelling Style: $experimentalGuidance")
        }
        
        val preferencesSection = if (activePreferences.size > 0) {
            "\n\nUSER PREFERENCES (STRICTLY FOLLOW THESE):\n" + activePreferences.joinToString("\n") { "- $it" } + 
            "\n\n⚠️ IMPORTANT: These preferences are CRITICAL. Every recommendation MUST align with these settings. Do NOT suggest films that contradict these preferences."
        } else {
            ""
        }
        
    return """
You are a passionate movie expert and critic. The user is exploring the $genre genre and has selected these movies they enjoyed:

${selectedMovies.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")}

Based on these selections, recommend exactly 15 movies that match their taste and preferences.$preferencesSection

FORMAT YOUR RESPONSE EXACTLY LIKE THIS:

[Brief 2-3 sentence analysis of their taste]

RECOMMENDATIONS:

1. Movie Title (Year)
One or two concise sentences describing why this fits their taste.

2. Another Movie Title (Year)
One or two concise sentences about why this matches.

[Continue for all 15 movies]

CRITICAL FORMATTING RULES - FOLLOW EXACTLY:
- Recommend EXACTLY 15 movies, no more, no less
- NUMBERING FORMAT: Use "1. " (number, period, ONE space) - NO colons, NO extra spaces, NO missing spaces
- Title format: "Movie Title (Year)" with 4-digit year in parentheses
- Each movie title MUST be on its OWN LINE
- Description goes on the NEXT LINE after the title
- Keep descriptions to 1-3 sentences MAXIMUM (≤ 75 words)

STRICT NUMBERING EXAMPLES (COPY THIS EXACTLY):
1. Movie Title (1994)
2. Another Movie (2001)
3. Third Movie (1987)

NOT LIKE THIS (WRONG):
1: Movie Title (wrong - colon)
1 . Movie (wrong - space before period)
1.Movie (wrong - no space after period)

ADDITIONAL RULES:
- Do NOT combine title and description on same line
- Do NOT repeat any movie title in the list
- Do NOT include any of the user's selected films
- Do NOT ask follow-up questions or add extra text after item 15
- STRICTLY respect the active preferences
        """.trimIndent()
    }
    
    private fun callOpenAI(prompt: String, apiKey: String, selectedTitles: List<String>): String {
        Log.d(TAG, "Starting OpenAI API call")
        Log.d(TAG, "API Key length: ${apiKey.length}, starts with: ${apiKey.take(10)}...")
        
        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")  // Fast and cost-effective
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a movie recommendation expert. Respond with plain text only, no markdown, no code blocks, no JSON formatting. Write naturally like you're talking to a friend.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.45)            // Lower for better adherence to constraints
            put("max_tokens", 1350)            // Allow longer responses without clipping
            put("frequency_penalty", 1.0)      // Stronger penalty for repetition
            put("presence_penalty", 0.7)       // Slightly higher to encourage variety
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
            
            val processed = postProcess(raw, selectedTitles)
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
    
    private fun postProcess(content: String, selectedTitles: List<String>): String {
        val lines = content.lines()
        // Format: number, optional space, period, optional space (handles "1.", "1 .", "1 .Title")
        // LLM keeps varying the format despite instructions, so we tolerate all variations
        val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
        val firstIdx = lines.indexOfFirst { numbered.containsMatchIn(it) }
        val analysis = if (firstIdx > 0) limitSentences(lines.take(firstIdx).joinToString("\n").trim(), 2) else ""
        val items = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        val selectedNorm = selectedTitles.map { normalizeTitle(it) }.toSet()
        var i = if (firstIdx >= 0) firstIdx else 0
        while (i < lines.size && items.size < 15) {
            val titleLine = lines[i].trim()
            if (!numbered.containsMatchIn(titleLine)) { i++; continue }
            // Clean title (remove number prefix)
            val title = titleLine.replace(numbered, "").trim()
            // Validate title format - prefer parentheses with year, but be lenient
            // Some titles may have year without parentheses or at the end
            val hasYear = title.contains(Regex("\\(\\d{4}\\)")) || title.contains(Regex("\\d{4}\\s*$"))
            if (!hasYear && title.length < 3) {
                // Only skip if it's truly malformed (too short and no year)
                i++
                continue
            }
            val norm = normalizeTitle(title)
            // Exclude duplicates and exclude any of the user's selected titles
            if (norm in seen || norm in selectedNorm) {
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
            if (desc.isBlank()) desc = "A strong match for your taste."
            desc = truncateWords(desc, 75)
            seen.add(norm)
            items.add(title to desc)
            i = j
        }
        // If the model did not produce exactly 15 well-formed items, signal failure so the repository can fall back
        if (items.size < 15) {
            return ""
        }
        val sb = StringBuilder()
        if (analysis.isNotBlank()) {
            sb.append("Analysis:\n")
            sb.append(analysis).append("\n\n")
        }
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
