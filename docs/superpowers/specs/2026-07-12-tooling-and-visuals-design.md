# Audio, Visuals & Tooling - Design Spec

## Overview
This specification details the "Audio, Visuals & Tooling" sub-project (Group 6) for Bayeux Brawler. As requested, this group is now the **Top Priority**. It focuses heavily on developer-experience (DX) improvements, including an exhaustive type refactor, a web-based gear drawing/animation tool, and native asset integration for pain sounds.

## 1. The Kotlin Type Safety Refactor (Compiler-Driven Expansion)
**Problem:** The game relies heavily on `if/else` statements or raw string IDs for gear (e.g., `"head_axe"`). When a new item is added, there's no compiler warning if you forget to add its rendering logic, stats, or generation rules.
**Solution:**
- **Sealed Classes / Enums:** Refactor the `GameData` catalogs into Kotlin `sealed class` or `enum class` hierarchies (e.g., `enum class WeaponHead`).
- **Exhaustive `when` statements:** Replace existing `if/else` chains in `TapestryRenderer` and `GameViewModel` with exhaustive `when` statements.
- **The Result:** If you add a new weapon like `URUMI` to the enum, the project will immediately fail to compile until you explicitly add its stats and its drawing logic to the respective `when` blocks. The compiler will guide you exactly where updates are needed.

## 2. Web-Based Gear Drawing & Animation Tool
**Concept:** A standalone HTML5/JS single-page web app to allow for rapid, visual gear creation without consuming AI credits.
**Features:**
- **Vector Canvas:** A grid-based canvas where you can draw shapes (lines, curves, polygons) using a visual UI.
- **Kotlin Exporter:** A button that instantly translates the drawn shapes into a formatted Kotlin `Path` block that can be copy-pasted directly into `TapestryRenderer`.
- **Animation Visualizer:** A toggleable preview mode in the web app that simulates the game's swing/bobbing animations, allowing you to preview how the weapon will look in motion before putting it into Android Studio.

## 3. Pain Sounds (Custom Audio Support)
**Concept:** Moving away from purely procedural audio for pain grunts, allowing for custom voice acting.
**Mechanics:**
- **Asset Folder:** Create a dedicated resource folder (e.g., `res/raw/`) specifically for custom pain sounds.
- **Playback Logic:** Update the `SoundSynth` or create a new audio player that reads from this folder.
- **Cooldowns & Chance:** The sounds will trigger on a percentage chance (e.g., 25% chance on hit) rather than every single hit to prevent audio fatigue and clipping.

## 4. Music Tempo & Tapestry Aesthetic
- **Music:** Ensure the underlying music tempo actually syncs with the player's choices (tempo/happy variables).
- **Aesthetic:** A continued commitment to the Bayeux Tapestry style, utilizing reference imagery for future visual updates to ensure the stitched outlines and linen textures remain authentic.
