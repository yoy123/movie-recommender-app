# OpenStream+ Configuration Registry

**Last verified:** 2026-07-25

## Build-Time Configuration

### `local.properties`

```properties
TMDB_API_KEY=...
OPENAI_API_KEY=...
TORZNAB_SOURCES=...
```

The values are read by `app/build.gradle.kts` and compiled into `BuildConfig`.

| BuildConfig field | Value source |
| --- | --- |
| `TMDB_API_KEY` | `local.properties` |
| `TMDB_BASE_URL` | hardcoded `https://api.themoviedb.org/3/` |
| `OPENAI_API_KEY` | `local.properties` |
| `TORZNAB_SOURCES` | optional semicolon-separated Torznab endpoint configuration from `local.properties` |

`OMDB_API_KEY` is not defined. OMDb code was removed.

`TORZNAB_SOURCES` entries use `Name|HTTPS endpoint|API key`. Multiple entries are separated with semicolons. An empty value disables the integration.

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
- OpenAI model: `gpt-5`

## Recommendation Constants

- Selection range: 1–5 titles
- Output target: 15 recommendations
- Candidate-pool bounded-mode threshold: 25
- GPT-5 retry reasoning efforts: `minimal`, then `low`
- GPT-5 verbosity: `medium`
- GPT-5 maximum completion tokens: 4,000
- Favorites routing ID: `-1`

## Database Cleanup Constants

Defined in `MovieRepository`:

- orphan age: 30 days
- cleanup interval: 7 days

Favorites are never deleted by orphan cleanup.

## Torrent Cache Configuration

Defined across `TorrentStreamService`, `TorrentBufferPolicy`, and `TorrentCachePolicy`:

- reserved device space: 500 MB
- minimum protected startup buffer: 256 MB, capped by remaining media bytes
- peak-rate headroom: 10 minutes
- peak bitrate factor: 1.5
- measured-speed safety factor: 0.65
- active-session retention after player exit: 60 minutes
- no-progress failure threshold before playback: 3 minutes
- piece-priority lookahead: 15 minutes
- Media3 minimum/maximum buffers: 60 seconds / 300 seconds

The selected source must fit within the physically safe cache allowance before playback starts. The allowance includes already allocated cache blocks plus currently free storage above the 500 MB reserve.

## Removed Configuration

The following embedded Live TV constants and dependency were removed on 2026-07-25:

- M3U playlist URL
- XMLTV EPG URL
- `media3-exoplayer-hls` dependency used only by that screen

## Security Notes

- `local.properties` and keystores must remain untracked.
- BuildConfig secrets are recoverable from distributed APKs.
- Plex tokens are stored in local Preferences DataStore without an encrypted-storage layer.
