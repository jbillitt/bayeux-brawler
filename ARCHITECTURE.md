# Bayeux Brawler — Architecture

> Android-native medieval combat roguelike rendered in the style of the Bayeux Tapestry.
> Single-activity Jetpack Compose app. Zero external game engines. All rendering, physics, audio, and AI are hand-rolled in Kotlin.

---

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Platform | Android (minSdk 24, targetSdk 36) | Kotlin, Java 11 compat |
| UI Framework | Jetpack Compose + Material 3 | Single-activity, composable-driven UI |
| Rendering | Compose `Canvas` (DrawScope) | Custom 2D renderer — no game engine |
| Game Loop | `LaunchedEffect` + `withFrameMillis` | 60fps coroutine-driven tick loop |
| Audio — SFX | `AudioTrack` (PCM 16-bit, 22050 Hz) | Real-time procedural waveform synthesis |
| Audio — Music | `AudioTrack` (PCM 16-bit, looped) | Seed-based procedural medieval composer |
| Speech | Android `TextToSpeech` (Latin locale) | Comedic combat barks |
| State Mgmt | `StateFlow` + `GameViewModel` | Unidirectional data flow |
| Persistence | Room (via KSP) | Gear unlocks, high scores |
| Build | Gradle KTS + Version Catalog | AGP with Compose compiler plugin |
| Testing | Robolectric + Roborazzi | Screenshot + unit tests |
| Backend | Firebase AI (Gemini API) | Server-side capability declared |

## File Map

```
app/src/main/java/com/example/
├── MainActivity.kt              (72 KB)  — Compose UI: all screens, gear tabs, battle HUD
├── game/
│   ├── SimulationModels.kt      (23 KB)  — Data classes: FighterState, GearItem, GameData catalogs
│   ├── GameViewModel.kt         (52 KB)  — Game loop, combat sim, enemy AI, level-up system
│   ├── TapestryRenderer.kt      (74 KB)  — Canvas renderer: characters, weapons, shields, horses, VFX
│   ├── SoundSynth.kt            (43 KB)  — Procedural audio: SFX engine + medieval music composer
│   ├── MidiTest.kt              (0.2 KB) — Stub for MedievalVocalizer lifecycle
│   └── ui/
│       └── theme/
│           ├── Color.kt         — Tapestry palette (TapestryDark, TapestryRed, etc.)
│           ├── Theme.kt         — Material 3 theme wrapper
│           └── Type.kt          — Typography
```

## Core Systems

### 1. Rendering Pipeline (`TapestryRenderer.kt`)

Singleton `object TapestryRenderer` with a pure Canvas drawing pipeline. No bitmaps, no sprites — everything is vector paths drawn with a hand-stitched Bayeux Tapestry aesthetic.

**Draw order per character:**
1. Horse (if mounted) → behind rider
2. Legs (suppressed if mounted)
3. Torso
4. Head (with decapitation/blood fountain system)
5. Back arm + shield (kite/tower/buckler/heater)
6. Front arm + weapon

**Visual systems:**
- `drawStitchedFill` / `StitchedStroke` — simulates embroidery thread look
- `drawWeapon` — handles 16 weapon heads × 9 handles with chain/flail physics
- `drawHorse` — full horse with walk-cycle leg animation
- `drawAncillaries` — companion NPCs (squire, herald, crossbowman, etc.)
- Blood particles, combat popups, damage indicators
- Death animations: decapitation, crumple, arm dismemberment

### 2. Combat Simulation (`GameViewModel.kt`)

Coroutine-driven game loop at 60fps via `withFrameMillis`. Manages:
- **AI movement**: enemies approach, maintain weapon reach distance
- **Attack system**: cooldown-based with swing animation progress
- **Damage calc**: pierce/slash/blunt vs armor defense, shield blocking
- **Status effects**: poison, bleed, crumple, missing arm
- **Level progression**: escalating waves of Saxon enemies
- **Roguelike upgrades**: weapon attachments, armor layers, handle extensions, followers

**Enemy generation (`generateRandomSaxon`):**
- Random gear loadouts scaling with level
- Size variation (0.7x–1.4x) affecting speed/damage/health
- Mounted enemies at level 5+
- Named champions at higher levels

### 3. Data Model (`SimulationModels.kt`)

**Gear catalog (GameData):**
- 16 weapon heads (fists → crossbow)
- 9 weapon handles (bare wrists → double-ended pole)
- 5 shields (none → tower)
- 5 armor pieces (naked → scale)
- 4 headgear (bare → great helm)
- 7 ancillaries (squire, herald, trumpeter, crossbowman, warhorse, cupbearer, archer)

**FighterState** — mutable data class with 30+ fields:
- Equipment, position, animation, combat status
- Derived stats: `totalMass`, `totalArmor`, `baseDamage`, `attackSpeedDelay`, `moveSpeed`, `scoreMultiplier`
- Score multiplier rewards naked/underequipped builds (risk-reward)

### 4. Procedural Audio (`SoundSynth.kt`)

**SFX Engine (`MedievalAudioSynth`):**
- 6 sound types: CLANG, THWACK, SWOOSH, OUCH, HUZZAH, CRUNCH
- All generated as PCM waveforms using additive synthesis + noise

**Music Engine (`ProceduralMedievalComposer` + `MedievalHarpPlayer`):**
- Seed-based deterministic composition (same seed = same song per game run)
- Church modes: Dorian, Mixolydian, Aeolian, Ionian
- Markov chain melody generation with weighted stepwise motion
- Rhythmic phrase generation with level-dependent density
- 8 instrument layers gated by level progression:
  1. Harp (always) — arpeggiated melody with organum 5ths
  2. Lute chords (L2+)
  3. Drone strings (L3+)
  4. Bodhrán war drum (L4+)
  5. Recorder counter-melody (L5+)
  6. Tambourine (L6+)
  7. Horn organum (L7+)
  8. Psaltery sweep (L8+, ORCHESTRAL destiny only)
- Bonus: trumpeter ancillary adds detuned horn blasts
- Mastering: wrap-around delay reverb with low-pass filter + soft-clip limiter
- Two "destiny" paths per game: FANFARE (rising, martial) or ORCHESTRAL (descending, Greensleeves-esque)

### 5. UI Layer (`MainActivity.kt`)

Single Compose screen with state-driven panels:
- **Gear selection**: 5-tab scrollable gear picker with character preview canvas
- **Character customization**: name, hair color/style, size
- **Battle HUD**: health bars, score, kill count, level indicator
- **Victory screen**: level-up reward grid, character portrait, share button
- **Game over**: death recap with restart

## Data Flow

```
User Input → MainActivity (Compose events)
    → GameViewModel.dispatch(action)
        → _uiState.update { ... }  (StateFlow)
            → Compose recomposition
                → Canvas { TapestryRenderer.drawBattlefield(...) }
                → LaunchedEffect { MedievalHarpPlayer.startMusic(level) }
```

## Key Design Decisions

1. **No game engine** — Canvas-only rendering keeps the APK tiny and avoids native library complexity
2. **Mutable data class** — `FighterState` uses `var` fields mutated in the game loop for performance; `copy()` used only for state snapshots
3. **Procedural everything** — No asset files for audio or graphics; everything generated at runtime
4. **Single file per concern** — Each major system lives in one file (large but self-contained)
5. **Score multiplier = risk** — Naked/unarmed builds get massive score bonuses, creating interesting gear decisions
