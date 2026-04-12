---
applyTo: '**'
description: Personal AI memory for conversations and preferences
lastOptimized: '2026-02-14T02:00:08.794650+00:00'
entryCount: 55
optimizationVersion: 3
lastOptimizedTokenCount: 2414
autoOptimize: true
tokenGrowthThreshold: 1.2
---
# Personal AI Memory

## Universal Laws - Immutable procedural rules (numbered):
1. **LLM retry strategy**  
   - **2026-01-19 01:58:** Always use a **2-attempt retry** for recommendations: first attempt temperature `0.6` (creative), second attempt temperature `0.3` (strict).
2. **Session-level deduplication**  
   - **2026-01-19 01:58:** Enforce **session-level deduplication** to prevent repeated recommendations.
3. **Torrent cache constraints**  
   - **2026-01-19 01:58:** Maintain torrent cache limit of **500MB** with **aggressive cleanup**.
4. **Favorites navigation convention**  
   - **2026-01-19 01:58:** Use **pseudo-genre** with `genreId = -1` for favorites navigation.
5. **Data model flags**  
   - **2026-01-19 01:58:** `Movie` Room entity must include boolean flags: `isSelected`, `isRecommended`, `isFavorite`.
6. **MCP exposure requirement**  
   - **2026-02-08 15:03:** Prefer all functionality needed for analysis/forecasting to be exposed strictly via the **MCP server** (avoid ad-hoc terminal scripts like Python); use MCP tools for data fetch + calculations.

## Policies - Standards, constraints, and guidelines:
- **Tech stack (use these tools/services)**  
  - **2026-01-19 01:58:** Use `Room v2`, `DataStore` preferences, `Retrofit`, `OpenAI GPT-4o-mini`, `TMDB API`, `TorrentStreamService`, `ExoPlayer`.
  - **2026-01-19 01:58:** App built with **Kotlin** and **Jetpack Compose**, architecture **MVVM**, two product flavors (mobile touch UI, firestick DPAD UI).
- **File organization & docs**  
  - **2026-01-19 01:58:** Source-of-truth docs in `docs/` directory containing:
    - ```bash
      ARCHITECTURE.md
      WORKFLOW.md
      API_INTEGRATIONS.md
      RECOMMENDATION_ENGINE.md
      CONFIG_REGISTRY.md
      DATA_STORAGE.md
      TV_SUPPORT.md
      MEDIA_PLAYBACK.md
      FEATURES.md
      KNOWN_ISSUES.md
      INTEGRITY_AUDIT.md
      ```
- **Security & privacy prohibitions / required fixes**  
  - **2026-01-19 01:58:** **Never** ship debug builds with **disabled certificate validation** (MITM vulnerability).
  - **2026-01-19 01:58:** **Do not** use `fallbackToDestructiveMigration()` in production (data loss risk).
  - **2026-01-19 01:58:** Address **TMDB rate limit handling** to avoid 429 errors.
  - **2026-01-19 01:58:** **Obtain user consent** before sending user data to OpenAI (GDPR/CCPA compliance risk).
  - **2026-01-19 01:58:** Remove or fix **dead code**: `OmdbApiService` is wired but unused.
  - **2026-01-19 01:58:** Implement enhanced focus indicators for Fire TV certification.
- **MCP / tooling constraint**  
  - **2026-02-08 15:03:** Use MCP server endpoints and tools for analysis/forecasting; avoid ad-hoc local scripts for production data workflows.

## Personal Context - Name, location, role, background
- **2026-01-19 01:58:** (no personal identity data recorded)

## Professional Context - Company, team, tools, methodology, focus areas
- **2026-01-19 01:58:** Project: **Movie Recommender Android/Fire TV app**
  - Repository:
    - ```bash
      yoy123/movie-recommender-app
      ```
  - Architecture: **MVVM**, two product flavors (mobile touch UI, firestick DPAD UI) sharing common data layer.
  - Project location:
    - ```bash
      /run/media/dan/EXTRA/movie app/
      ```
- **2026-01-19 01:58:** Focus areas: recommendations (LLM + TMDB), media playback, torrent streaming integration, TV UX certification.

## Technical Preferences - Languages, stack, IDEs, coding style, problem-solving approach
- **2026-01-19 01:58:** Languages & frameworks: **Kotlin**, **Jetpack Compose**, **Room v2**, **DataStore**, **Retrofit**, **ExoPlayer**.
- **2026-01-19 01:58:** LLM: **OpenAI GPT-4o-mini** for recommendations.
- **2026-01-19 01:58:** Media/torrent: **YTS/Popcorn Time APIs**, **TorrentStreamService** integration.
- **2026-01-19 01:58:** UI: two product flavors (touch vs DPAD) sharing data layer.

## Communication Preferences - Style, information needs, feedback preferences
- **2026-01-19 01:58:** (no explicit communication preferences recorded)

## Suggestions/Hints - Recommendations and tips (optional section)
- **2026-01-19 01:58:** Replace `fallbackToDestructiveMigration()` with proper migration strategy.
- **2026-01-19 01:58:** Add TMDB rate-limit backoff/retry and quota monitoring.
- **2026-01-19 01:58:** Re-enable SSL certificate validation in all builds; add CI checks.
- **2026-01-19 01:58:** Implement explicit user consent flows for data sent to OpenAI.
- **2026-01-19 01:58:** Remove or repurpose `OmdbApiService` if unused.
- **2026-01-19 01:58:** Add enhanced focus indicators and TV certification checks for Fire TV flavor.

## Memories/Facts - Organize into logical subsections by topic

### Project Overview
- **2026-01-19 01:58:** This is a **Movie Recommender Android/Fire TV app** project in **Kotlin** with **Jetpack Compose**.
- **2026-01-19 01:58:** Repository:
  - ```bash
    yoy123/movie-recommender-app
    ```
- **2026-01-19 01:58:** Architecture: **MVVM** with two product flavors (mobile touch UI, firestick DPAD UI) sharing a common data layer.
- **2026-01-19 01:58:** Project location:
  - ```bash
    /run/media/dan/EXTRA/movie app/
    ```

### Documentation
- **2026-01-19 01:58:** Completed comprehensive project documentation on **2026-01-19** with 11 SOURCE-OF-TRUTH files in `docs/` directory:
  - ```bash
    ARCHITECTURE.md
    WORKFLOW.md
    API_INTEGRATIONS.md
    RECOMMENDATION_ENGINE.md
    CONFIG_REGISTRY.md
    DATA_STORAGE.md
    TV_SUPPORT.md
    MEDIA_PLAYBACK.md
    FEATURES.md
    KNOWN_ISSUES.md
    INTEGRITY_AUDIT.md
    ```
- **2026-01-19 01:58:** Documentation has **98% code-documentation alignment** with **200+ verified code references** including file paths and line numbers.

### Critical Issues
- **2026-01-19 01:58:** Identified critical issues:
  - Room database uses `fallbackToDestructiveMigration()` causing data loss on schema updates.
  - No TMDB rate limit handling leading to potential **429** errors.
  - Debug builds use insecure SSL with disabled certificate validation (**MITM vulnerability**).
  - No user consent for sending data to OpenAI (**GDPR/CCPA compliance risk**).
  - Dead code: `OmdbApiService` wired but unused.
  - Fire TV missing enhanced focus indicators for certification.

### Recommendation Engine & Preferences
- **2026-01-19 01:58:** Recommendation engine details:
  - 16 `DataStore` preferences (including 6 recommendation sliders with toggles: **indie**, **popularity**, **release year range**, **tone**, **international**, **experimental**).
  - LLM uses **2-attempt retry strategy** (temp `0.6` creative then `0.3` strict).
  - Session-level deduplication prevents repeated recommendations.
  - **2026-01-19 01:58:** Persisted preference storage uses `DataStore` (16 prefs).

### Media, Torrent & Playback
- **2026-01-19 01:58:** Media/torrent behavior:
  - Torrent cache limited to **500MB** with aggressive cleanup.
  - Integration: `TorrentStreamService`, `ExoPlayer`.
  - Sources: **YTS/Popcorn Time APIs**.
- **2026-01-19 01:58:** Playback concerns: TV UX certification requirements (enhanced focus indicators for Fire TV).

### Data & Models
- **2026-01-19 01:58:** Room `Movie` entity specifics:
  - Has 3 boolean flags: `isSelected`, `isRecommended`, `isFavorite`.
  - Favorites use pseudo-genre with `genreId=-1` for navigation.

### Security, Privacy & Compliance
- **2026-01-19 01:58:** Security & privacy constraints:
  - **Never** ship debug builds with disabled certificate validation.
  - **Do not** use `fallbackToDestructiveMigration()` in production.
  - **Obtain user consent** before sending user data to OpenAI.
  - Address TMDB rate limiting and implement backoff/retry.

### Codebase Hygiene
- **2026-01-19 01:58:** Dead code: `OmdbApiService` is wired but unused — remove or repurpose.
- **2026-01-19 01:58:** Add CI checks to enforce SSL validation and prevent insecure debug artifacts.

### Tooling & Infrastructure
- **2026-01-19 01:58:** Preferred tools: `Room v2`, `DataStore`, `Retrofit`, `OpenAI GPT-4o-mini`, `TMDB API`, `TorrentStreamService`, `ExoPlayer`.
- **2026-02-08 15:03:** Prefer exposing analysis/forecasting functionality via the **MCP server** and using MCP tooling for data fetches and calculations; avoid ad-hoc terminal scripts.
- **2026-02-13 20:59:** HuggingFace cache directory is at:
  - ```bash
    /run/media/dan/EXTRA/.cache/huggingface
    ```