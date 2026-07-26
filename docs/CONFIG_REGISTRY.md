# OpenStream+ Configuration Registry

**Last verified:** 2026-07-25

## Build-Time Configuration

### `local.properties`

```properties
TMDB_API_KEY=...
OPENAI_API_KEY=...
```

Both values are read by `app/build.gradle.kts` and compiled into `BuildConfig`.

| BuildConfig field | Value source |
| --- | --- |
| `TMDB_API_KEY` | `local.properties` |
| `TMDB_BASE_URL` | hardcoded `https://api.themoviedb.org/3/` |
| `OPENAI_API_KEY` | `local.properties` |

`OMDB_API_KEY` is not defined. OMDb code was removed.

## Android Configuration

| Setting | Value |
| --- | --- |
| namespace | `com.movierecommender.app` |
| compile SDK | 34 |
| target SDK | 34 |
| minimum SDK | 24 |
| version code | 1 by default |
| version name | `1.0.0` |
| Java compatibility | 1.8 |
| Kotlin JVM target | 1.8 |

### Product flavors

| Flavor | Application ID behavior | Version behavior |
| --- | --- | --- |
| `mobile` | base ID | default version |
| `firestick` | suffix `.firestick` | version code 4, `-firestick` name suffix |

### Release signing properties

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Default keystore path: `app/signing/release.jks`.

## DataStore Registry

`SettingsRepository` defines 26 preference keys.

### General and consent

| Key | Type | Default |
| --- | --- | --- |
| `user_name` | String | empty |
| `is_first_run` | Boolean | `true` |
| `dark_mode` | Boolean | `true` |
| `llm_consent_given` | Boolean | `false` |
| `llm_consent_asked` | Boolean | `false` |
| `last_db_cleanup` | Long | `0` |

### Recommendation controls

| Key | Type | Default |
| --- | --- | --- |
| `indie_preference` | Float | `0.5` |
| `use_indie` | Boolean | `true` |
| `popularity_preference` | Float | `0.5` |
| `use_popularity` | Boolean | `true` |
| `release_year_start` | Float | `1950` |
| `release_year_end` | Float | `2026` |
| `use_release_year` | Boolean | `true` |
| `tone_preference` | Float | `0.5` |
| `use_tone` | Boolean | `true` |
| `international_preference` | Float | `0.5` |
| `use_international` | Boolean | `true` |
| `experimental_preference` | Float | `0.5` |
| `use_experimental` | Boolean | `true` |

### Plex settings

| Key | Type | Default |
| --- | --- | --- |
| `plex_server_url` | String | empty |
| `plex_token` | String | empty |
| `plex_movie_library_path` | String | empty |
| `plex_tv_library_path` | String | empty |
| `plex_movie_library_id` | String | empty |
| `plex_tv_library_id` | String | empty |
| `plex_enabled` | Boolean | `false` |

Plex settings exist but are not currently connected to a user-facing flow.

## Room Configuration

Database name:

```text
movie_recommender_database
```

Version: `3`

Entities:

- `movies`
- `provider_content_crosswalk`

Schema export directory:

```text
app/schemas
```

Configured through KSP argument `room.schemaLocation`.

## Network Configuration

- Android network security file: `app/src/main/res/xml/network_security_config.xml`
- TMDB HTTP cache: 10 MB
- TMDB rate-limit retries: up to 3
- OpenAI endpoint: `https://api.openai.com/v1/chat/completions`
- OpenAI model: `gpt-4.1-mini`

## Recommendation Constants

- Selection range: 1–5 titles
- Output target: 15 recommendations
- Candidate-pool bounded-mode threshold: 25
- Open-mode temperatures: 0.6, then 0.3
- Bounded-mode temperatures: 0.4, then 0.2
- Favorites routing ID: `-1`

## Database Cleanup Constants

Defined in `MovieRepository`:

- orphan age: 30 days
- cleanup interval: 7 days

Favorites are never deleted by orphan cleanup.

## Torrent Cache Configuration

Defined in `TorrentStreamService`:

- minimum cache allowance: 100 MB
- reserved device space: 500 MB
- fraction of remaining free space: 75%
- explicit maximum: none

The function comment currently says 50%, while the actual constant is 75%. Code behavior is authoritative.

## Removed Configuration

The following embedded Live TV constants and dependency were removed on 2026-07-25:

- M3U playlist URL
- XMLTV EPG URL
- `media3-exoplayer-hls` dependency used only by that screen

## Security Notes

- `local.properties` and keystores must remain untracked.
- BuildConfig secrets are recoverable from distributed APKs.
- Plex tokens are stored in local Preferences DataStore without an encrypted-storage layer.
