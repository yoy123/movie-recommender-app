# Google Play Store Submission Guide — OpenStream+

**Last reviewed:** 2026-07-25

## Important Scope Note

The mobile flavor is the intended Google Play artifact. The Firestick flavor is designed for Fire TV distribution.

Before submission, review whether every included media-source feature complies with current Google Play policy and applicable law. Store policy should be checked again immediately before release.

The embedded Live TV guide was removed on 2026-07-25 and must not appear in listing text or screenshots.

## Build Artifact

Update version values in `app/build.gradle.kts`, configure release signing, then build:

```bash
./gradlew :app:bundleMobileRelease
```

Expected output:

```text
app/build/outputs/bundle/mobileRelease/
```

## Suggested Listing

### App title

```text
OpenStream+ — Movie & TV Picks
```

### Short description

```text
Pick movies or shows you like and get personalized recommendations and watch options.
```

### Full description

```text
Find something worth watching without endless scrolling.

OpenStream+ lets you choose 1–5 movies or TV shows you already enjoy, tune your preferences, and generate a personalized set of recommendations.

FEATURES

• Browse movies and TV shows by genre
• Search the TMDB catalog
• Choose optional AI recommendations or use TMDB-only recommendations
• Tune popularity, indie, year, tone, international, and experimental preferences
• Save favorite movies locally
• View trailers
• See available streaming, rental, and purchase providers
• Open supported provider apps when installed
• Browse TV seasons and episodes where supported

PRIVACY

Your favorites and settings are stored locally on your device. AI recommendations are optional and require consent before selected titles and preference settings are sent to OpenAI. You can continue using TMDB-only recommendations without enabling AI.
```

Do not name a specific OpenAI model in store copy unless the listing will be updated whenever the implementation changes. The current code uses `gpt-4.1-mini`, but model branding is not necessary for users.

## Screenshots

Recommended mobile screenshots:

1. Movie/TV genre selection
2. Title selection with 1–5 picks
3. Recommendation preference controls
4. Recommendation results
5. Favorites
6. Trailer or watch-provider options

Do not include removed Live TV UI.

## Data Safety Review

Verify declarations against the shipped binary.

Current relevant behavior:

- TMDB receives search/catalog requests and title identifiers.
- OpenAI receives selected titles and active preference values only after consent.
- OpenAI mode can be disabled.
- Favorites, settings, consent state, and provider crosswalks are stored locally.
- No analytics SDK usage was found in the current source.
- The manifest currently requests AD_ID permission even though no advertising SDK usage was found; remove it before submission unless required.

## Content Rating

Answer based on the actual app and linked content behavior. Consider:

- movie/TV metadata and artwork
- external provider links
- user-selected media sources
- unrestricted internet access

## Privacy Policy

Host `PRIVACY_POLICY.md` at a stable HTTPS URL and link it in Play Console and in-app where required.

Review the policy whenever:

- external services change
- AI data fields change
- analytics or ads are added
- account/cloud sync is added
- Plex functionality becomes user-facing

## Pre-Submission Checklist

- [ ] mobile release bundle builds
- [ ] release signature verified
- [ ] version code incremented
- [ ] privacy policy hosted
- [ ] Data Safety form matches code
- [ ] AI consent tested
- [ ] TMDB-only mode tested
- [ ] AD_ID permission removed or justified
- [ ] screenshots contain no removed Live TV feature
- [ ] no API keys printed in logs
- [ ] OpenAI key has usage limits/monitoring
- [ ] store-policy review completed for all media-source functionality
- [ ] manual regression checklist completed

## Suggested Release Notes

```text
Initial OpenStream+ release:

• Movie and TV discovery
• Personalized recommendation preferences
• Optional AI or TMDB-only recommendations
• Favorites stored locally
• Trailers and watch-provider options
• Mobile-friendly Material interface
```
