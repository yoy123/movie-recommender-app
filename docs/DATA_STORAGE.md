# OpenStream+ Data Storage

**Last verified:** 2026-07-25

## Overview

OpenStream+ uses:

- Room for durable content and provider-link records
- Preferences DataStore for user settings and consent
- application cache storage for images, HTTP responses, and torrent data

## Room Database

Database: `movie_recommender_database`  
Version: `3`  
Schema export: enabled

### Entity: `movies`

Primary key: TMDB movie ID.

Important fields:

- title and overview
- poster/backdrop paths
- release date
- rating, vote count, popularity
- genre IDs and optional display genres
- `isSelected`
- `isRecommended`
- `isFavorite`
- timestamp

The three Boolean flags may coexist. DAO update methods change individual flags without intentionally clearing the others.

TV shows are not stored in a separate Room entity. TV selection state is currently held in the flavor ViewModel.

### Entity: `provider_content_crosswalk`

Composite primary key:

- `tmdbId`
- `mediaType`
- `providerId`

Stored values include:

- provider key
- provider-specific content ID
- canonical URL
- app deep link
- source
- confidence
- last verification time

The crosswalk allows exact imported/resolved provider links to override generic provider search links.

## DAOs

### `MovieDao`

Supports:

- selected/recommended/favorite flows
- insert, update, delete
- clear selection/recommendation flags
- favorite flag updates
- orphan cleanup

Orphan cleanup deletes only rows that are:

- not selected
- not recommended
- not favorite
- older than the supplied cutoff

### `ProviderContentCrosswalkDao`

Supports:

- all provider mappings for a TMDB item
- one mapping for a specific provider
- single and batch upsert

## Migrations

### 1 -> 2

No schema change. The migration preserves data and replaces the former destructive-upgrade behavior.

### 2 -> 3

Creates `provider_content_crosswalk`.

The database builder does not use `fallbackToDestructiveMigration()`.

## Startup Cleanup

`MovieRecommenderApplication` launches cleanup on an application-scoped IO coroutine.

Repository policy:

- delete orphaned movie rows older than 30 days
- run at most once every 7 days
- update `last_db_cleanup` after successful cleanup
- never delete favorites

## Preferences DataStore

File name:

```text
user_settings
```

The 26 keys cover:

- user name
- first-run state
- dark mode
- AI consent/asked state
- six recommendation preference groups
- cleanup timestamp
- seven Plex settings

All values are exposed as `Flow` and updated through suspend setter methods.

### User-name validation

Stored names are:

- trimmed
- whitespace-collapsed
- limited to letters, digits, spaces, apostrophes, and hyphens
- limited to 50 characters

### Sensitive values

The Plex token is stored as a normal DataStore string. It is local to the application sandbox but is not encrypted with Android Keystore-backed storage.

## Cache Storage

### TMDB HTTP cache

- directory: `cacheDir/tmdb_http_cache`
- size: 10 MB
- managed by OkHttp

### Image cache

Coil manages image caching. The project does not currently define a custom disk-cache policy in shared configuration.

### Torrent cache

TorrentStream uses an application cache directory managed by `TorrentStreamService`.

Allowance calculation:

```text
usable MB = max(free MB - 500, 0)
allowance = max(usable MB * 0.75, 100 MB)
```

There is no explicit upper cap.

The service exposes a clear-cache intent. Playback activities request cleanup during destruction, but process crashes can leave cache data until a later cleanup.

## Android Backup

The manifest enables Android backup and references:

- `backup_rules.xml`
- `data_extraction_rules.xml`

The actual included/excluded data depends on those XML policies and Android version. Room and DataStore restoration should be validated on target devices before relying on backup as a migration strategy.

## Data Integrity Risks

1. TV selection state is not persisted like movie selection state.
2. Provider crosswalk entries have no expiration or automatic revalidation job.
3. Plex token storage is not encrypted.
4. Torrent cache has no maximum cap.
5. No active migration instrumentation test is executing.
6. `Movie` uses one record for selection, recommendation, and favorite roles, making flag interactions important to preserve.
