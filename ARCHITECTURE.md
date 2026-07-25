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
| Persistence | DataStore Preferences (`GameProfile`) | Earned gear, cleared milestones, highscore, deaths |
| Build | Gradle KTS + Version Catalog | AGP with Compose compiler plugin |
| Testing | Robolectric + Roborazzi | Screenshot + unit tests |
| Backend | none | The game is entirely offline; no server, no accounts |

## File Map

```
app/src/main/java/com/example/
├── MainActivity.kt              (72 KB)  — Compose UI: all screens, gear tabs, battle HUD
├── game/
│   ├── SimulationModels.kt      (23 KB)  — Data classes: FighterState, GearItem, GameData catalogs
│   ├── GameViewModel.kt         (52 KB)  — Game loop, combat sim, enemy AI, level-up system
│   ├── TapestryRenderer.kt      (74 KB)  — Canvas renderer: characters, weapons, shields, horses, VFX
│   ├── SoundSynth.kt            (43 KB)  — Procedural audio: SFX engine + medieval music composer
│   ├── GameProfile.kt           — DataStore-backed saved profile: unlocks, milestones, highscore
│   ├── Milestone.kt             — The table of earnable unlocks and their conditions
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
# Architecture Notes - Procedural Music Engine v2

(Merge this into the repo's architecture.md.)

## Overview

The music system generates a complete, seeded medieval piece per playthrough and re-renders it with more instruments as the player levels. Everything is synthesised at runtime - no audio assets. Same seed + same level = byte-identical audio; same seed + higher level = the identical tune with more forces (level changes crossfade in-phase).

## Module map (`com.example.game`, app/src/main/java/com/example/game/)

| File | Responsibility |
|---|---|
| MusicTheory.kt | Modes, harmonic grounds, seed → SongSpec (family/mode/final/metre/tempo/form); the three independent RNG streams |
| MelodyGenerator.kt | SongSpec → SongEvents: melody with real cadences (antecedent/consequent, ficta, Landini), ornamented variant, counter-voice (gymel 3rds), ground bass, pad chords |
| Orchestrator.kt | Seeded role pools and the level ladder (who plays what from which level), mood deltas, percussion/drone/sparkle/fanfare pattern generators, consort thickening past level 12 |
| DspCore.kt | Foundation DSP: fractionally-tuned Karplus-Strong (<1 cent error), RBJ biquads, envelopes. All oscillators are phase-accumulator based - vibrato/pitch-drop is integrated, so runaway-detune bugs are structurally impossible |
| InstrumentsPluck.kt | Voice enum + renderNote dispatcher; harp, lute, psaltery, recorder, panpipes |
| InstrumentsBowBrass.kt | Vielle family, shawm, sackbut/horn, hurdy-gurdy (wheel drone + trompette rasp when durSec < 0.15s), portative organ; shared `normalise()` |
| InstrumentsPerc.kt | Bells (inharmonic partials), nakers/timpani/bodhran (phase-correct membranes), tabor, tambourine (bandpassed jingles) |
| MixMaster.kt | MixBus: constant-power pan, loop-aware Schroeder reverb (tail wraps to loop start), DC block, 70 Hz high-pass, soft-knee limiter (-3 dBFS ceiling), TPDF dither. NO normalisation - fixed gain budget only |
| ProceduralMedievalComposer.kt | Facade. FROZEN API: `compose(seed, level, hasTrumpeter, sampleRate, moods): ShortArray` - returns INTERLEAVED STEREO; `currentRootMidi` feeds SFX pitches |
| SfxGenerator.kt | Pure SFX buffers (SoundType enum lives here); per-hit pitch/decay variation; CRUNCH is tanh-saturated by design |
| FlavourText.kt | Seeded battle names (≤44 chars guaranteed), Latin headlines, performance-aware victory quotes, defeat quotes, TTS barks, popup vocabulary |
| MedievalHarpPlayer.kt | Streaming stereo AudioTrack player: feeder thread, seamless loop, in-phase 250 ms crossfade on level change, audio focus, pause()/resume() for lifecycle |
| SoundSynth.kt | Thin remains: MedievalAudioSynth (SFX playback wrapper) + MedievalVocalizer (TTS) |

## Data flow

```
seed ──► resolveSongSpec ──► SongSpec (family, mode, final, ground, tempo, form)
             │                    │
   melodyRng │          orchRng   │
             ▼                    ▼
        generateSong        planOrchestration
        (SongEvents:        (assignments: voice × line × enterLevel × gain × pan;
         melody, ornamented, ALL rng draws unconditional - level only filters)
         counter, bass, pads)     │
             └────────┬───────────┘
                      ▼
     facade: for each active assignment → renderNote(...) per event
                      ▼
     MixBus (pan, reverb send) → master chain → interleaved stereo PCM
                      ▼
     MedievalHarpPlayer (stream, loop, crossfade, focus)
```

## Invariants (do not break)

1. **Frozen API**: compose() signature, currentRootMidi, midiToFreq; playSound(SoundType).
2. **Determinism**: three RNG streams (`melodyRng`/`orchRng`/`humaniseRng`, seed-derived). A level check must NEVER gate an RNG draw - draw everything, render selectively. This is what makes level-up crossfades seamless.
3. **Melody stays modal**: all melodic/ornament pitch maths goes through scale degrees (`degreeToMidi`/`nearestDegreeFor`), never raw semitone offsets (raw +2 caused audible clashes against pads - fixed at the listen gate).
4. **Gain discipline**: no per-render normalisation anywhere; role gains + duckFactor + limiter own loudness; master high-pass 70 Hz protects small speakers.
5. **Stereo semantics**: compose() output is L,R interleaved; length = totalSamples × 2.

## Tune families

| Family | Ground | Metre | Mode | Character |
|---|---|---|---|---|
| GREENSLEEVES | Romanesca i-VII-i-V | 6/8, ♩. 56-72 | Dorian | Wistful lilt |
| MINUET | I-V pairs + IV/vi colour | 3/4, ♩ 92-112 | Ionian/Mixolydian | Courtly, ornamented |
| TINTAGEL | i-i-VI-VII broad | 4/4, ♩ 60-76, 16 bars | Aeolian/Dorian | Noble arch, rising-4th opening |

## Level ladder (role slots; seeded pools fill them, family-biased)

1 soloist (self-accompanied) → 2 counter-voice → 3 bass takes the ground → 4 percussion → 5 drone → 6 third voice (divisions) → 7 pads/organum → 8 jingles → 9 waits band (shawm+sackbut) → 10 bells+psaltery → 11 timpani → 12 destiny (FANFARE or ORCHESTRAL, seeded) → 13+ thickening every ~2 levels (recorder II in 3rds, fiddle II, viola, octave doubles).

Moods (stackable param deltas): More Tempo / Merrier / More Solemn / Wilder / Nobler.

## Extending

- **New instrument**: add Voice enum entry + renderer branch (InstrumentsPluck.kt dispatcher) + a VoiceAssignment in planOrchestration with gain/pan/enterLevel.
- **New tune family**: ground table + branch in resolveSongSpec (metre/tempo/mode) - melody/orchestration machinery is family-agnostic.
- **New mood**: deltas in resolveSongSpec (tempo/mode/ornament) and/or planOrchestration (gains/enterLevels).

## Known quirks (reviewed, accepted)

- The gurdy's drone line receives both root and fifth events while its wheel already synthesises the fifth internally, so a faint supertonic colour (final+14) rides Tintagel drones - quiet, ducked, passed the listen gate. If drones ever sound muddy, skip the +7 drone event for GURDY in the facade.
- Tempo-changing moods crossfade two different-tempo loops for 250 ms (mechanically safe, momentary blur); level changes are perfectly in-phase.
- The destiny fanfare arpeggiates a major triad regardless of mode - a deliberate Picardy-bright flourish.
- Humanisation covers timing and velocity; the spec's ±2-cent tuning jitter was dropped (renderNote is integer-midi) - inaudible class.
- On older devices, watch first-render wall time at level 12+ (no per-note cache; render runs on Dispatchers.IO and the crossfade masks latency).

## Testing (desktop, not in the Android repo)

The engine is pure JVM. The dev workspace (C:\dev\test) carries a kotlinc-based harness: 12 property-test suites (`test.ps1` - determinism, cadence-on-final, tuning ≤6 cents, -3 dBFS ceiling, loop seam, monotonic layering, variety-across-seeds) and `AuditionMain.kt` which renders a seeds×levels×moods WAV matrix for listening. Change engine code there first, keep tests green, listen, then port.
