---
applyTo: '**'
description: Workspace-specific AI memory for this project
lastOptimized: '2026-03-02T02:53:10.503290+00:00'
entryCount: 13
optimizationVersion: 4
lastOptimizedTokenCount: 657
autoOptimize: true
tokenGrowthThreshold: 1.2
---
# Workspace AI Memory

## Universal Laws - Immutable procedural rules (numbered):
1. (no entries)

## Policies - Standards, constraints, and guidelines:
- (no entries)

## Personal Context - Name, location, role, background
- (no entries)

## Professional Context - Company, team, tools, methodology, focus areas
- (no entries)

## Technical Preferences - Languages, stack, IDEs, coding style, problem-solving approach
- (no entries)

## Communication Preferences - Style, information needs, feedback preferences
- (no entries)

## Suggestions/Hints - Recommendations and tips (optional)
- (no entries)

## Memories/Facts

### Firestick
- **2026-03-01 21:52:** **Dan's girlfriend's Firestick ADB address**
  ```bash
  71.59.81.82:5555
  ```
  - **Label:** "Girlfriend's Firestick"
  - **Use:** Use this when installing or connecting to the girlfriend's Firestick via ADB.

- **2026-02-18 21:22:** **Dan's Firestick ADB address**
  ```bash
  192.168.1.10:5555
  ```
  - **Label:** "Dan's Firestick"
  - **Note:** If another Firestick is mentioned, clarify which one.

- **2026-02-18 15:14:** **DPAD Center Button KeyDown/KeyUp quirk (Android/Fire TV Compose focus)**
  - **Behavior:** DPAD center press generates TWO key events: `KeyDown` followed by `KeyUp`.
  - **Problem:** When using `onPreviewKeyEvent` to intercept center presses (e.g., to activate a card and move focus to child buttons), if only the `KeyDown` is consumed and focus moves, the subsequent `KeyUp` will arrive at the now-focused button and trigger its onclick handler.
  - **Fix pattern:** Use a boolean flag (e.g., `consumeNextUp`) — set it `true` on activation `KeyDown`, then on the next `KeyUp` for the same key, consume it and clear the flag.
  - **Applies to:** Any key (e.g., `Back`/`Escape`) where the handler changes focus as a side effect.

- **2026-01-23 20:02:** **Firestick app launch command**
  ```bash
  adb -s emulator-5554 shell am start -n com.movierecommender.app.firestick/com.movierecommender.app.firestick.MainActivity
  ```
  - **Note:** The package is `com.movierecommender.app.firestick` and **MainActivity** is in the `firestick` subpackage, NOT `com.movierecommender.app.MainActivity`.