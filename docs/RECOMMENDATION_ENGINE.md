# RECOMMENDATION_ENGINE.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

The recommendation engine combines OpenAI GPT-4o-mini LLM with TMDB-based fallback logic. This document details the complete recommendation pipeline: how "select 5 movies" becomes 15 personalized recommendations.

---

## High-Level Flow

```
User selects 1-5 movies
         ↓
MovieViewModel.generateRecommendations()
         ↓
MovieRepository.getRecommendations(...)
         ↓
    Fetch exclusions (favorites + recommended + selected)
         ↓
    Build TMDB candidate pool (similar + recommendations + genre discovery)
         ↓
    ┌─── Candidate pool ≥ 25? ───┐
    │                             │
   YES                           NO
    │                             │
    ↓                             ↓
LLM Bounded Mode          LLM Open Mode
(Rerank from candidates)  (Free-form generation)
    │                             │
    └─────────┬───────────────────┘
              ↓
    Validate LLM response:
      - Structure (Analysis + 15 items)
      - Genre constraint (TMDB search)
      - Candidate constraint (if bounded)
              ↓
         ┌─ Valid? ─┐
         │          │
        YES        NO
         │          │
         ↓          ↓
   Return LLM   Fallback to TMDB
   recommendation  (similar + rank)
         │          │
         └────┬─────┘
              ↓
    Return formatted text to UI
```

**Code Entry Point:** [MovieRepository.kt:152](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L152)

---

## Step 1: Exclusion List Construction

**Purpose:** Prevent recommending movies the user already knows or selected.

**Sources:**
1. **Selected Movies** (`isSelected = true` in Room)
2. **Favorites** (`isFavorite = true` in Room)
3. **Already Recommended** (`isRecommended = true` in Room)
4. **Session Retries** (titles from previous retry attempts, tracked in `MovieViewModel.sessionRecommendedTitles`)

**Format:** `"Movie Title (YYYY)"` with year

**Code:** [MovieRepository.kt:233](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L233)
```kotlin
val favoriteMovies = movieDao.getFavoriteMovies().first()
val alreadyRecommendedMovies = movieDao.getRecommendedMovies().first()

val formattedFavorites = formatExcludedMovies(favoriteMovies)
val formattedRecommended = formatExcludedMovies(alreadyRecommendedMovies)
val formattedSelected = formatExcludedMovies(selectedMovies)

val allExcluded = (formattedFavorites + formattedRecommended + formattedSelected + additionalExcludedTitles)
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()
```

**Session Retry Tracking:**
- ViewModel extracts titles from current recommendation text
- Adds to `sessionRecommendedTitles` set
- Passes to next `generateRecommendations()` call
- Prevents LLM from returning same titles across multiple retries

**Code:** [MovieViewModel.kt:358](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L358)

---

## Step 2: TMDB Candidate Pool Construction

**Purpose:** Provide LLM with bounded candidate list (reduces hallucinations, improves genre adherence).

**Threshold:** Minimum 25 candidates required for bounded mode.

**Sources:**
1. **Similar Movies** for each selected movie → `TmdbApiService.getSimilarMovies(movieId, page=1)`
2. **Recommendations** for each selected movie → `TmdbApiService.getMovieRecommendations(movieId, page=1)`
3. **Genre Discovery** (if not favorites mode) → `TmdbApiService.getMoviesByGenre(genreId, sortBy="vote_average.desc", page=1)`
4. **Genre Discovery** (popularity) → `TmdbApiService.getMoviesByGenre(genreId, sortBy="popularity.desc", page=1)`

**Filtering:**
- Remove selected/favorites/recommended (by ID)
- Require year in `releaseDate` (for stable formatting)
- If `useReleaseYearPreference = true`: filter by year range
- If genre enforcement: filter by `genreIds.contains(genreId)`
- Deduplicate by `id`

**Ranking:**
- Score each candidate using enabled preference sliders:
  - `indiePreference` → proxy: low popularity + low vote count
  - `popularityPreference` → normalized popularity
  - `tonePreference` → proxy: dark genres (27, 53, 80, 18, 9648, 10752) vs light genres (35, 16, 10751, 10402, 10749, 12, 14)
- Sort by score → take top 80 titles
- Format as `"Title (YYYY)"`

**Code:** [MovieRepository.kt:815](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L815)

**Output:** `List<String>` of 80 titles (or fewer if insufficient candidates)

---

## Step 3A: LLM Bounded Mode (Candidate Rerank)

**Trigger:** `candidates.size >= 25`

**Service:** `LlmRecommendationService.getRecommendationsFromLlmCandidates(...)`

**Code:** [LlmRecommendationService.kt:146](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L146)

---

### Prompt Structure

**System Role:**
```
You are a film curator. Generate personalized recommendations in plain text.
```

**User Prompt Sections:**

1. **Genre Context**
   - If favorites mode: "Infer genres from selected titles"
   - Else: "GENRE (MANDATORY): {genreName}. ALL recommendations MUST be {genre} films."

2. **Selected Titles** (1-5 movies with years)
   ```
   SELECTED TITLES (user taste signals):
   1. Movie Title (YYYY)
   2. Movie Title (YYYY)
   ...
   ```

3. **Candidate List** (80 titles)
   ```
   CANDIDATE LIST (you MUST select ONLY from these titles):
   - Movie Title (YYYY)
   - Movie Title (YYYY)
   ...
   ```

4. **Active Preferences** (only enabled sliders)
   - Indie: "STRONGLY favor indie" / "Lean towards blockbusters" / "Balance equally"
   - Popularity: "STRONGLY emphasize cult classics" / "Focus on mainstream" / "Mix equally"
   - Year Range: "MANDATORY - every recommendation MUST be between {start} and {end}"
   - Tone: "STRONGLY favor dark/serious" / "Lighter/uplifting" / "Balance"
   - International: "ONLY American" / "Favor international" / "Mix equally"
   - Experimental: "ONLY traditional" / "Favor avant-garde" / "Mix equally"

5. **Exclusion List**
   ```
   EXCLUDE TITLES (forbidden):
   - Movie Title (YYYY)
   - Movie Title (YYYY)
   ...
   Hard rule: Do NOT recommend any excluded title.
   ```

6. **Output Format**
   ```
   [3-sentence analysis paragraph]

   RECOMMENDATIONS:

   1. Movie Title (YYYY)
   Why: one sentence connecting to user's selections.

   2. Movie Title (YYYY)
   Why: one sentence.
   
   (Continue until 15; stop immediately after item 15.)
   ```

**Code:** [LlmRecommendationService.kt:460](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L460)

---

### API Parameters

**Model:** `gpt-4o-mini`  
**Temperature:** 0.4 (creative) → 0.2 (strict) on retry  
**Frequency Penalty:** 1.0  
**Presence Penalty:** 0.6  
**Max Tokens:** 4096

**Two Attempts:**
1. Creative attempt (temp=0.4)
2. Strict retry with extra instructions (temp=0.2)

**Code:** [LlmRecommendationService.kt:154](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L154)

---

## Step 3B: LLM Open Mode (Free-form Generation)

**Trigger:** `candidates.size < 25`

**Service:** `LlmRecommendationService.getRecommendationsFromLlm(...)`

**Difference from Bounded Mode:**
- No candidate list in prompt
- LLM can select any movie from its training data
- Higher risk of hallucinations
- Genre constraint more strictly enforced in validation

**Code:** [LlmRecommendationService.kt:50](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L50)

**Same Prompt Structure** minus candidate list section.

---

## Step 4: LLM Response Validation

**Three Validation Checks (all must pass):**

---

### 4.1: Structure Validation

**Required:**
- Contains "Analysis:" section (case-insensitive)
- Contains exactly 15 numbered items (1-15)
- Regex: `^\s*(\d{1,2})\.\s*` matches lines

**Code:** [MovieRepository.kt:519](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L519)
```kotlin
private fun isValidRecommendationStructure(text: String): Boolean {
    val hasAnalysis = text.lines().any { it.trim().equals("Analysis:", ignoreCase = true) }
    if (!hasAnalysis) return false

    val numbered = Regex("^\\s*(\\d{1,2})\\s*\\.\\s*")
    val numberedItems = lines.mapNotNull { line ->
        numbered.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    
    if (numberedItems.size < 15) return false
    
    val hasAllNumbers = (1..15).all { it in numberedItems }
    return hasAllNumbers
}
```

**Fail Reason:** Missing analysis, fewer than 15 items, non-sequential numbers

---

### 4.2: Genre Constraint Validation

**When:** NOT favorites mode AND `genreId` provided AND NOT bounded mode

**Process:**
1. Parse numbered recommendations → extract `ParsedRec(number, title, year)`
2. For each of first 15 recs:
   - `TmdbApiService.searchMovies(title)` → get candidates
   - If year provided: prefer match with same year
   - Check if `candidate.genreIds.contains(requiredGenreId)`
   - If `genre_ids` empty: fallback to `getMovieDetails()` → check `genres` list
3. Allow max 2 misses (for search ambiguity tolerance)

**Code:** [MovieRepository.kt:601](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L601)

**Skip Condition:** Bounded mode used → candidates already genre-filtered → skip expensive search

---

### 4.3: Candidate Constraint Validation

**When:** Bounded mode (candidate list provided to LLM)

**Process:**
1. Normalize all candidate titles:
   - Lowercase
   - Remove year in parentheses
   - Remove leading articles (the, a, an)
   - Strip non-alphanumerics
2. Build key: `normalizedTitle + year` (e.g., "matrixreloaded2003")
3. For each recommended title:
   - Normalize + append year → build key
   - Check if key in candidate set
4. Count misses → must be 0 (strict)

**Code:** [MovieRepository.kt:651](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L651)
```kotlin
private fun passesCandidateConstraint(
    recs: List<ParsedRec>,
    allowedTitles: List<String>
): Boolean {
    val allowedKeys: Set<String> = allowedTitles.mapNotNull { t ->
        val y = Regex("\\((\\d{4})\\)").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (y == null) return@mapNotNull null
        normalizeCandidateTitle(t) + y.toString()
    }.toSet()

    val missing = mutableListOf<String>()
    for (rec in recs.take(15)) {
        val y = rec.year ?: continue
        val key = normalizeCandidateTitle(rec.title) + y.toString()
        if (key !in allowedKeys) {
            missing.add("${rec.title} ($y)")
        }
    }

    return missing.isEmpty()
}
```

**Fail Reason:** LLM invented title not in candidate list (hallucination)

---

## Step 5: Fallback to TMDB

**Trigger:** LLM validation failed OR network error OR empty response

**Algorithm:** `buildFallbackRecommendations()`

**Code:** [MovieRepository.kt:689](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L689)

---

### Fallback Process

1. **Gather Candidates**
   - For each selected movie: fetch similar + recommendations from TMDB
   - Merge into pool (Map<Int, Movie> to dedupe)

2. **Filter**
   - Remove selected/favorites/recommended by ID
   - If `genreId` provided: filter by `genreIds.contains(genreId)`
   - If year range enabled: filter by `releaseDate` in range
   - Deduplicate by ID

3. **Rank**
   - Sort by `voteAverage` DESC, then `popularity` DESC
   - Take top 15

4. **Format**
   - Generate analysis paragraph: "Based on your selected films, here are 15 picks..."
   - Numbered list (1-15) with title (year)
   - "Why" line: truncated `overview` (max 75 words)

5. **Return** formatted text (same structure as LLM)

---

### Fallback Limitations

- No preference slider integration (TMDB doesn't expose indie/experimental metrics)
- Simple quality + popularity ranking
- Generic explanations (movie overview, not personalized)
- No thematic analysis

**Advantage:** Always succeeds (if TMDB reachable) → guaranteed 15 recommendations

---

## Preference Sliders Integration

**6 User-Configurable Preferences:**

### 1. Indie Preference (0–1)
**0.0 = Blockbusters**, **1.0 = Indie films**

**LLM Prompt Mapping:**
- ≤ 0.3: "STRONGLY favor mainstream blockbusters. Avoid indie completely."
- 0.31–0.45: "Lean towards blockbusters. Minimize indie."
- 0.46–0.54: "Balance equally."
- 0.55–0.7: "Favor indie films. Include some mainstream."
- \> 0.7: "STRONGLY favor indie. Avoid mainstream completely."

**TMDB Candidate Ranking:**
- Proxy: `(1 - normalizedPopularity) * 0.7 + (1 - normalizedVoteCount) * 0.3`
- Low popularity + low vote count → indie-leaning
- Score match: `1 - |indieProxy - indiePreference|`

**Code:** [LlmRecommendationService.kt:253](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L253)

---

### 2. Popularity Preference (0–1)
**0.0 = Cult classics**, **1.0 = Mainstream**

**LLM Prompt Mapping:**
- ≤ 0.3: "STRONGLY emphasize cult classics, obscure gems. Avoid popular."
- 0.31–0.45: "Favor hidden gems. Minimize popular."
- 0.46–0.54: "Mix equally."
- 0.55–0.7: "Favor well-known films. Include some cult classics."
- \> 0.7: "STRONGLY focus on mainstream favorites. Avoid obscure."

**TMDB Candidate Ranking:**
- Use normalized `popularity` field directly
- Score match: `1 - |normalizedPopularity - popularityPreference|`

**Code:** [LlmRecommendationService.kt:272](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L272)

---

### 3. Release Year Range (1950–2026)
**Two sliders:** `releaseYearStart`, `releaseYearEnd`

**LLM Prompt:**
- "MANDATORY - Every single recommendation MUST be released between {start} and {end}."

**TMDB Filtering:**
- Hard filter in candidate pool construction
- Parse `releaseDate` (YYYY-MM-DD) → extract year → check range

**Validation:**
- Year extracted from parsed recommendation
- Checked in genre constraint validation (implicit)

**Code:** [LlmRecommendationService.kt:285](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L285)

---

### 4. Tone Preference (0–1)
**0.0 = Light/uplifting**, **1.0 = Dark/serious**

**LLM Prompt Mapping:**
- ≤ 0.3: "STRONGLY favor uplifting, feel-good, comedy. Avoid dark."
- 0.31–0.45: "Lean towards lighter films. Minimize dark."
- 0.46–0.54: "Balance equally."
- 0.55–0.7: "Favor serious, dramatic films."
- \> 0.7: "STRONGLY favor dark, intense, thought-provoking."

**TMDB Candidate Ranking:**
- **Dark Genres:** 27 (Horror), 53 (Thriller), 80 (Crime), 18 (Drama), 9648 (Mystery), 10752 (War)
- **Light Genres:** 35 (Comedy), 16 (Animation), 10751 (Family), 10402 (Music), 10749 (Romance), 12 (Adventure), 14 (Fantasy)
- Proxy: `darkHits / (darkHits + lightHits)` → 0 = light, 1 = dark
- Score match: `1 - |toneProxy - tonePreference|`

**Code:** [LlmRecommendationService.kt:299](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L299)

---

### 5. International Preference (0–1)
**0.0 = Domestic (US)**, **1.0 = International**

**LLM Prompt Mapping:**
- ≤ 0.3: "ONLY American/English-language films. Avoid foreign completely."
- 0.31–0.45: "Primarily American with occasional international."
- 0.46–0.54: "Mix equally."
- 0.55–0.7: "Favor international cinema."
- \> 0.7: "STRONGLY prioritize international, foreign language, world cinema."

**TMDB Limitation:** No reliable metadata for origin country in standard endpoints.

**Code:** [LlmRecommendationService.kt:318](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L318)

---

### 6. Experimental Preference (0–1)
**0.0 = Traditional narrative**, **1.0 = Avant-garde**

**LLM Prompt Mapping:**
- ≤ 0.3: "ONLY traditional narrative, conventional filmmaking. Avoid experimental."
- 0.31–0.45: "Favor traditional storytelling. Minimize experimental."
- 0.46–0.54: "Mix equally."
- 0.55–0.7: "Favor creative, innovative films."
- \> 0.7: "STRONGLY prioritize avant-garde, unconventional, experimental techniques."

**TMDB Limitation:** No experimental flag in metadata.

**Code:** [LlmRecommendationService.kt:337](../app/src/main/java/com/movierecommender/app/data/remote/LlmRecommendationService.kt#L337)

---

## Favorites Mode Special Handling

**Pseudo-Genre:** `genreId = -1`, `genreName = "{UserName}'s Favorites"`

**Differences:**

1. **Genre Inference**
   - `inferDominantGenreId()` called → counts genre IDs across selected movies
   - If one genre appears in ≥ 60% of selections → use that genre
   - Else → treat as mixed-genre → pass `null` to LLM

**Code:** [MovieRepository.kt:665](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L665)

2. **LLM Prompt**
   - If dominant genre found: use it as effective genre
   - Else: "Infer genres from selected titles. Match dominant themes."

3. **Genre Constraint Validation**
   - Skipped if no dominant genre (mixed favorites)

**Code:** [MovieRepository.kt:201](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L201)

---

## Retry Mechanism

**User Action:** Click "Retry" button in RecommendationsScreen

**Flow:**
1. `MovieViewModel.retryRecommendations()` called
2. Extract titles from current `recommendationText` → `extractRecommendedTitlesFromText()`
3. Add to `additionalExcludedTitles` parameter
4. Call `generateRecommendations(additionalExcludedTitles)`
5. LLM receives previous 15 titles in exclusion list
6. Returns new 15 titles

**Session Tracking:**
- `sessionRecommendedTitles: MutableSet<String>` in ViewModel
- Persists across multiple retries (cleared on `clearSelections()`)
- Prevents returning same titles even if user retries 3+ times

**Code:** [MovieViewModel.kt:345](../app/src/mobile/java/com/movierecommender/app/ui/viewmodel/MovieViewModel.kt#L345)

---

## Performance Characteristics

### Latency Breakdown

**Fast Path (Bounded Mode, Valid):**
- TMDB candidate pool: 1–2 seconds (4 API calls)
- LLM request: 3–5 seconds
- Total: **4–7 seconds**

**Slow Path (Open Mode, Validation):**
- LLM request: 3–5 seconds
- Genre validation: 2–3 seconds (15 TMDB searches)
- Total: **5–8 seconds**

**Fallback Path:**
- TMDB similar/recommendations: 1–2 seconds
- Total: **1–2 seconds** (fastest)

---

## Known Edge Cases

### 1. LLM Refuses to Recommend (Safety Filter)
**Symptom:** OpenAI returns refusal message  
**Handling:** Validation fails → fallback to TMDB

### 2. All Candidates Excluded
**Symptom:** User has favorites + selected + recommended = 100 movies, candidate pool empty  
**Handling:** Bounded mode skipped → open mode used → LLM may still generate (not constrained)

### 3. No TMDB Similar/Recommendations
**Symptom:** Selected movies are very niche, TMDB returns empty lists  
**Handling:** Candidate pool small → open mode → LLM generates from training data

### 4. Genre Mismatch in Search
**Symptom:** LLM recommends "The Matrix" but TMDB search returns "The Matrix Reloaded" first  
**Handling:** Year matching preferred in search → if year matches, use that result

---

## Logging and Debugging

**Key Log Tags:**
- `"MovieRepository"` - Candidate pool size, exclusion counts, validation results
- `"LlmRecommendation"` - Prompt construction, API calls, retry attempts

**Verbose Logs:**
- [MovieRepository.kt:247](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L247) - Exclusion stats
- [MovieRepository.kt:289](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L289) - Candidate pool details
- [MovieRepository.kt:353](../app/src/main/java/com/movierecommender/app/data/repository/MovieRepository.kt#L353) - Validation results

---

## Future Improvements

### 1. Cache TMDB Candidate Pool
**Problem:** Same movies selected → same TMDB calls → wasted network  
**Fix:** Cache candidate pool keyed by `selectedMovieIds + genreId` for 5 minutes

### 2. Parallel Genre Validation
**Problem:** 15 sequential TMDB searches = slow  
**Fix:** Use `async` + `awaitAll` to parallelize searches

### 3. User Feedback Loop
**Problem:** No learning from user's favorite recommendations  
**Fix:** Track which recommended movies user added to favorites → use as positive signal

### 4. A/B Test LLM vs TMDB
**Problem:** Don't know if LLM actually better than TMDB baseline  
**Fix:** Randomize 50% users to TMDB-only → measure favorites rate

---

**Next Review:** When LLM model upgraded, prompt engineering changes, or validation logic modified.
