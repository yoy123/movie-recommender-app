# OpenStream+ Recommendation Engine

**Last verified:** 2026-07-26

## Current Policy

OpenStream+ has one primary recommendation strategy:

1. Build and rank verified candidates from TMDB.
2. Use AI to analyze the selected titles and rerank those candidates.
3. Validate the AI response before displaying it.
4. Use deterministic TMDB ranking when AI cannot be used safely.

There is no user-selectable AI-versus-TMDB recommendation style. AI is the default primary engine. TMDB is the fallback.

## Privacy Permission

The first recommendation request asks whether recommendation inputs may be sent to OpenAI. This is a data-sharing permission, not an engine-style selector.

When permission is granted, OpenStream+ may send:

- the selected movie or TV-show titles
- the selected genre
- enabled recommendation preferences
- a bounded list of candidate titles collected from TMDB

The user's name, favorites list, playback history, and account information are not sent.

Declining permission leaves recommendations available through the TMDB fallback. Permission can later be changed under **Settings → AI Data Sharing**.

## Movie Pipeline

```text
1-5 selected movies
    -> collect TMDB similar/recommended titles
    -> optionally add genre discovery titles
    -> remove selected, favorite, previously recommended, and session-excluded titles
    -> enforce valid year and enabled year range
    -> reject candidates with fewer than 10 TMDB votes
    -> rank candidates locally
    -> keep up to 80 verified candidates
    -> AI reranks exactly 15 candidates
    -> validate response
    -> display AI result or TMDB fallback
```

Favorites mode infers a dominant genre only when that genre appears in at least 60% of the selected movies. Without a clear dominant genre, the pipeline does not force an arbitrary genre.

## TV Pipeline

The TV pipeline follows the same AI-first structure using:

- TMDB TV similar results
- TMDB TV recommendations
- TV genre discovery
- selected-show and session exclusions
- TV-specific genre/tone proxies

Movie and TV recommendations both use the same typed result model and fallback diagnostics.

## Candidate Ranking

Local candidate ranking combines:

- Bayesian-adjusted TMDB rating
- vote-count support
- popularity
- the enabled popularity preference
- the enabled indie/blockbuster preference
- a genre-based tone proxy

The Bayesian adjustment shrinks ratings with very few votes toward a neutral prior. This prevents a title with one 10/10 vote from outranking a broadly supported high-quality title.

International and experimental preferences are used by AI as ranking priorities. TMDB does not expose sufficiently reliable metadata to enforce those preferences deterministically in fallback mode.

## Bounded AI Behavior

Production recommendations call only `getRecommendationsFromLlmCandidates()`.

The older open-ended service method has been removed. Production and the opt-in live smoke test both use the same candidate-bounded method, so AI is never permitted to invent titles for the recommendation flow.

AI is attempted only when:

- data-sharing permission is granted
- an OpenAI API key is configured
- at least 15 safe TMDB candidates remain

The OpenAI request uses the Chat Completions endpoint with a configurable model. `gpt-4.1` is the default; model-access failures retry through `gpt-4.1`, `gpt-4.1-mini`, and `gpt-4o-mini` without requiring user configuration.

## Prompt Rules

Hard constraints:

- choose only from the supplied candidate list
- exclude selected and previously shown titles
- respect enabled release-year limits
- return exactly 15 numbered titles with years

Preference sliders are ranking priorities rather than mutually exclusive hard filters. When preferences conflict, the prompt prioritizes selected-title similarity, candidate validity, genre, exclusions, and year range. This avoids impossible prompts such as simultaneously requiring mainstream, obscure, light, dark, domestic, and international results.

The prompt contains no concrete example movie list. Removing the few-shot example prevents example titles and analysis text from leaking into results.

## Validation

An AI result is displayed only when all checks pass:

- a substantive 3-4 sentence analysis exists
- the analysis does not begin with generic filler
- the analysis references the user's selected titles where safe title matching is possible
- exactly 15 numbered recommendations are present
- every title includes a four-digit year
- every title belongs to the supplied candidate list
- selected, favorite, previously recommended, and session-excluded titles are absent
- normalized duplicate titles are absent, including remakes with the same visible title
- enabled release-year limits are respected

Validated output uses the canonical TMDB candidate title rather than model-supplied markdown or punctuation. `Summary:` and similar prefixes are removed before display.

The service makes two bounded attempts. A rejected response is never shown directly.

## TMDB Fallback

Fallback occurs when:

- data-sharing permission is declined
- the OpenAI key is missing
- fewer than 15 safe candidates remain for AI reranking
- the OpenAI request fails
- the AI response fails validation

Fallback reuses the verified TMDB pool, applies exclusions and deterministic preference ranking, and formats TMDB overviews as explanations.

The recommendation screen displays a **TMDB Fallback (Debug)** dialog with the fallback reason. Safe diagnostics may include candidate counts, exception type, or validation counts. API keys, prompts, raw OpenAI responses, and response bodies are not shown or logged.

## Retry and Session Deduplication

Retry adds every currently displayed recommendation to the session exclusion set. Both AI and TMDB fallback apply that set, reducing repeated lists during the same app session.

Starting over clears the session recommendation set and the typed source/fallback state.

## Result Types

`RecommendationResult` records:

- recommendation text
- `AI` or `TMDB_FALLBACK` source
- structured fallback reason
- optional sanitized diagnostic

Both flavor ViewModels expose the source and fallback notice through `MovieUiState`.

## Remaining Limitations

- TMDB metadata is not a complete representation of tone, production scale, international focus, or experimental style.
- A highly restrictive genre/year combination may produce fewer than 15 fallback titles.
- Real OpenAI behavior still requires an opt-in network smoke test.
- Repository orchestration and Compose consent/fallback flows need broader integration and UI test coverage.
