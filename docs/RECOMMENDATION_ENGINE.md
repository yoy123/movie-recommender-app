# OpenStream+ Recommendation Engine

**Last verified:** 2026-07-25

## Overview

OpenStream+ supports two recommendation modes:

- OpenAI-assisted recommendations
- TMDB-only preference ranking

AI use is opt-in. TMDB-only mode remains available without sending selected titles to OpenAI.

## Inputs

- 1–5 selected movies or TV shows
- selected genre/content mode
- favorites and prior recommendations
- session retry exclusions
- active preference values and toggles

Preferences:

- indie
- popularity
- release-year range
- tone
- international
- experimental

## Exclusions

The engine excludes, where applicable:

- currently selected titles
- favorites
- already recommended titles
- titles returned during earlier retries in the same session

Comparison is normalized enough to reduce duplicate title variants.

## Candidate Construction

The repository builds candidates from TMDB similar/recommendation/discovery/search data depending on content mode and available inputs.

When the candidate pool is large enough, the LLM is constrained to the provided candidates. The current bounded-mode threshold is 25 candidates.

When the pool is too small, the service can use open generation with stronger post-validation and TMDB resolution.

## AI Mode

### Endpoint

```text
POST https://api.openai.com/v1/chat/completions
model: gpt-4.1-mini
```

### Open generation attempts

1. temperature `0.6`
2. temperature `0.3`

### Bounded rerank attempts

1. temperature `0.4`
2. temperature `0.2`

The second attempt is stricter and intended to correct formatting, duplication, or constraint failures.

### Output

The target response contains:

- personalized taste analysis
- exactly 15 recommendations
- title/year and explanation data in the expected text structure

## Validation

The service/repository validates:

- parseable structure
- recommendation count
- duplicate titles
- exclusion violations
- candidate membership in bounded mode
- genre rules when enabled

Validation failure causes a retry or TMDB fallback rather than displaying untrusted output.

## TMDB-Only Mode

TMDB-only mode ranks candidates using normalized metadata and active preference weights.

Signals include:

- popularity
- vote count
- release year
- language/origin proxies
- genre and keyword proxies
- preference-specific scoring

This path is used when:

- AI is disabled
- consent is declined
- no OpenAI key is available
- network/API call fails
- LLM output fails validation

## Movie and TV Differences

Movie recommendations can be persisted through the `Movie` Room entity and its recommendation flag.

TV recommendation models are not stored in a dedicated Room entity. TV selection and recommendation state is primarily held in the ViewModel and rendered from parsed recommendation text/candidate data.

Trailer, provider, and torrent actions are content-mode aware after recommendation generation.

## Favorites Mode

Favorites uses routing ID `-1` and selected favorite movies as recommendation inputs.

Because favorite selections can span genres, dominant-genre inference may be used rather than enforcing one selected genre.

## Retry

Retry generation adds current results to the session exclusion set before requesting another list.

The session deduplication state is not intended as permanent user-history learning.

## Consent and Privacy

The consent dialog describes the data sent to OpenAI:

- selected titles
- recommendation preferences

No AI call should occur before consent is granted. Declining AI must not block recommendations.

## Performance Characteristics

Main latency contributors:

- TMDB candidate collection
- OpenAI request and possible retry
- title resolution/validation requests
- sequential work inside some third-party services

TMDB uses a 10 MB HTTP cache and rate-limit retry interceptor.

## Active Risks

1. `LlmRecommendationService` parsing has no executing unit tests.
2. Model responses can change despite fixed prompts.
3. Candidate and genre validation can add many TMDB requests.
4. Movie and TV recommendation persistence is inconsistent.
5. Preference scoring logic is duplicated in multiple repository sections.
6. The current model name is hardcoded rather than centrally configured.
7. Recommendation output format is plain-text contract rather than structured JSON/schema output.

## Recommended Next Refactor

- Extract candidate collection, preference scoring, validation, and formatting into separate classes.
- Add deterministic tests for parsing, exclusions, and scoring.
- Move model and retry parameters into one configuration object.
- Consider structured output to reduce parser failures.
