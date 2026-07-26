# Privacy Policy for OpenStream+

**Last updated:** July 25, 2026

## Overview

OpenStream+ is designed to work without creating an OpenStream+ account. Most user state is stored locally on the device.

This policy describes the behavior of the current application source. Distribution builds and third-party service policies should be reviewed again before publication.

## Information Stored on Your Device

OpenStream+ may store:

- your chosen display name
- first-run and appearance settings
- movie favorites
- current movie selection/recommendation flags
- recommendation preference values and enablement toggles
- whether you accepted or declined AI recommendations
- provider-content link mappings
- database cleanup timestamp
- Plex configuration values if entered by a future or development workflow
- temporary HTTP, image, and torrent cache data

This information is stored in the application sandbox using Room, Preferences DataStore, and cache storage.

OpenStream+ does not currently provide an OpenStream+ cloud account or synchronize these records to an OpenStream+ server.

## Information Sent to Third-Party Services

### The Movie Database (TMDB)

OpenStream+ sends requests to TMDB to load movie and TV metadata, search results, genres, trailers, external identifiers, recommendations, and watch-provider availability.

Requests can include:

- search text
- TMDB title identifiers
- language/country parameters
- normal network metadata such as IP address and device connection information visible to the service

### OpenAI API

AI recommendations are optional. Before the first AI recommendation request, OpenStream+ asks for consent.

When AI mode is enabled, the app may send:

- selected movie or TV-show titles
- release years where available
- active recommendation preference values
- exclusion titles needed to avoid repeats

OpenStream+ does not need to send your chosen display name for recommendation generation.

When AI mode is disabled or consent is declined, OpenStream+ uses TMDB-only recommendations and does not send recommendation prompts to OpenAI.

OpenAI states that API inputs and outputs are not used to train its models by default unless the API customer explicitly opts in. OpenAI may retain certain API data for abuse monitoring for a limited period under its current API data controls. OpenAI's current terms and policies govern its processing.

### Trailer and Metadata Fallbacks

When a TMDB trailer is unavailable, OpenStream+ may request IMDb pages using an IMDb identifier. IMDb and embedded video providers receive normal network request information.

### Streaming Providers

When you choose a streaming provider, OpenStream+ may launch the provider's installed application or open a provider/web link. The provider then processes your activity under its own privacy policy.

### Media-Source Services

When you request torrent-backed playback, OpenStream+ may query third-party indexing services using a title, year, IMDb identifier, season, or episode number. Those services receive the request and normal network metadata. Torrent peer-to-peer activity can also expose your IP address to other peers participating in the same torrent.

Do not use peer-to-peer playback unless you understand the privacy, security, and legal implications in your jurisdiction.

### Plex

Plex is mapped as an external provider. Development code also contains Plex server configuration/service support, although it is not currently connected to a completed user-facing workflow. If used in a future build, requests would be sent to the Plex server URL configured by the user.

## Embedded Live TV

The former embedded Live TV guide and third-party playlist/EPG integration were removed on July 25, 2026. The current app does not fetch those channel feeds.

## Analytics, Advertising, and Tracking

No analytics or advertising SDK usage was found in the current source.

The Android manifest currently declares the advertising ID permission, but the current code does not read or use the advertising ID. This permission is scheduled for removal unless a documented feature requires it.

OpenStream+ does not sell personal information.

## Data Retention and Deletion

Local data remains until it is:

- removed through app functionality
- cleared through Android application settings
- removed by uninstalling the app
- deleted by automatic cache/database cleanup rules

Favorites are excluded from automatic orphan-record cleanup.

Third-party services retain data according to their own policies and legal obligations.

## Security

OpenStream+ uses Android application sandboxing and standard HTTPS/TLS validation for supported services.

Important limitations:

- API keys compiled into an APK can be extracted.
- Plex tokens, if stored, use ordinary Preferences DataStore rather than a dedicated encrypted vault.
- Third-party provider and media-source services are outside OpenStream+'s control.
- Peer-to-peer torrent traffic reveals network information to peers.

## Children's Privacy

OpenStream+ is not specifically directed to children. Movie and TV metadata, external services, and linked media may include content unsuitable for children. Parents and guardians should supervise use and apply device/provider parental controls.

## Changes to This Policy

This policy should be updated whenever the app changes its:

- external services
- AI data fields
- analytics or advertising behavior
- account/cloud synchronization
- storage behavior
- Plex functionality
- media-source behavior

## Contact

The distributor must add a valid support contact and hosted policy URL before public store submission.
