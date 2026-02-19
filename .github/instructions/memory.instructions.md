---
applyTo: '**'
description: Workspace-specific AI memory for this project
lastOptimized: '2026-02-19T02:22:33.623841+00:00'
entryCount: 8
optimizationVersion: 3
lastOptimizedTokenCount: 577
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
- **2026-01-23 20:02:** **Firestick app launch command**
  ```bash
  adb -s emulator-5554 shell am start -n com.movierecommender.app.firestick/com.movierecommender.app.firestick.MainActivity
  ```
  - **Note:** the package is `com.movierecommender.app.firestick` and **MainActivity** is in the `firestick` subpackage, NOT `com.movierecommender.app.MainActivity`.

- **2026-02-18 15:14:** **DPAD Center Button KeyDown/KeyUp quirk (Android/Fire TV Compose focus):**
  - **Behavior:** DPAD center press generates TWO key events: `KeyDown` followed by `KeyUp`.
  - **Problem:** When using `onPreviewKeyEvent` to intercept center presses (e.g., to activate a card and move focus to child buttons), if only the `KeyDown` is consumed and focus moves, the subsequent `KeyUp` will arrive at the now-focused button and trigger its onclick handler.
  - **Fix pattern:** use a `consumeNextUp` boolean flag — set it true on activation `KeyDown`, then on the next `KeyUp` for the same key, consume it and clear the flag.
  - **Applies to:** any key (e.g., `Back`/`Escape`) where the handler changes focus as a side effect.

- **2026-02-18 21:22:** **Dan's Firestick ADB address**
  - Use `192.168.1.10:5555` (label: "Dan's Firestick") when connecting to Dan's Firestick via ADB.
  - If another firestick is mentioned, clarify which one.