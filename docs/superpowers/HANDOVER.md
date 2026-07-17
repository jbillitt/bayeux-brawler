# Handover (2026-07-17) — A/B music in progress; bug-fixes, C, D unbuilt

**Repo:** Windows 11, PowerShell, Android/Kotlin, Jetpack Compose custom-canvas
game. **No gradle wrapper** — use system `gradle` on PATH (Gradle 9.6, AGP 9.1.1).
Fast compile: `gradle :app:compileDebugKotlin --console=plain`. Unit tests:
`gradle :app:testDebugUnitTest`. Scoped: `--tests "com.example.game.*"`.

## CORRECTION to the previous handover

The prior handover wrongly claimed sub-projects A and B (music themes + drums)
were "IMPLEMENTED & VERIFIED." **They were not.** As of today only the four
design specs/plans were committed. The BRAWL/THRONE strings elsewhere in the
code (`brawlMode`, `isThroneMode`) are pre-existing *gameplay* flags, unrelated
to the music `Family` enum.

## Actual current state

- Branch `main`, clean. Latest relevant commit: `866d382`.
- **Music-themes-drums plan** (`docs/superpowers/plans/2026-07-17-music-themes-drums.md`):
  Tasks **1–3 DONE & committed**, tests green (`gradle :app:testDebugUnitTest
  --tests "com.example.game.SoundTest"` passes):
  - T1 `325b2b5` — `Family.BRAWL`/`THRONE`, `Mode.PHRYGIAN`, rewritten
    `resolveSongSpec(seed, moods, brawl, throne)`, old "Brawl" mood deleted.
  - T2 `6bb0dc3` — `compose(..., brawl, throne)` plumbing.
  - T3 `866d382` — `planBrawlOrchestration`, THRONE **stub**
    (`planThroneOrchestration`), NAKERS gallop in `percussionEvents`.
  - Tasks **4–7 REMAIN** (see below).
- **Bug-fixes plan** (`.../2026-07-17-bug-fixes.md`): NOT started.
- **Battle-performance / sub-project C** (`.../2026-07-17-battle-performance.md`):
  NOT started. 6 tasks.
- **New-content / sub-project D** (`.../2026-07-17-new-content.md`): NOT started.
  8 tasks. **C strictly before D** (D Task 5 renders through C's backdrop cache).

## Remaining music-themes-drums tasks (finish this plan first)

Follow the plan file's TDD steps exactly. Each is a separate conventional commit.

- **Task 4 — CHOIR voice.** Add `Voice.CHOIR` (enum + dispatch in
  `InstrumentsPluck.kt`), `choir()` synth in `InstrumentsBowBrass.kt`, and the
  `choirEnergyLivesInTheFormantBand` test + `bandRms` helper in `SoundTest.kt`.
  Full code is in the plan. (An earlier partial attempt was reverted; start fresh
  from the plan's Task 4 code block.)
- **Task 5 — THRONE orchestration + doom timpani.** Replace the Task-3 THRONE
  stub with the real choir-led `planThroneOrchestration`; replace the TIMPANI arm
  in `percussionEvents`; add the 3 throne tests.
- **Task 6 — phone-first drum re-voicing.** Rewrite `membrane()` in
  `InstrumentsPerc.kt` (add `bodyAmp`), re-voice NAKERS/TIMPANI/BODHRAN call
  sites; add `drumsSpeakInThePhoneBand` test. Keep `everyVoiceRendersAudibleSound`
  green (no clipping).
- **Task 7 — player + UI wiring, mood-hack removal, on-device rite.** Thread
  `brawl`/`throne` through `MedievalHarpPlayer.startMusic` and the
  `MainActivity` LaunchedEffect; delete the `appliedMusicMoods + "Brawl"` shim;
  comment updates in `SimulationModels.kt`/`GameViewModel.kt`; `git grep "\"Brawl\""`
  must be clean in music code. Step 7 is a manual on-device audio check (human).

## Then, in order

1. **Bug-fixes** plan.
2. **Battle-performance (C)** — 6 tasks, zero visual change is a hard requirement.
3. **New-content (D)** — 8 tasks. Be thorough on D Task 1 (7 ladder invariants —
   implement every invariant test before implementation code) and D Task 7
   (weather full-bleed). C before D.

## Working agreements (binding)

- Work through tasks continuously; do NOT stop between tasks. Pause only for
  genuinely ambiguous design questions.
- TDD per task: failing test → red → implement → green. Commit per task via
  `git commit --no-gpg-sign`. CRLF warnings are normal; ignore.
- All spawns/scenery/siege cadence must be seed-deterministic (`gameSeed + level`).
- Audio is phone-band mixed (300 Hz–4 kHz) — keep the band-RMS /
  `everyVoiceRendersAudibleSound` tests green if touching synth code.

## Gotchas (carried forward)

- `CombatEngineTest.throneModeFistsPlayerReachesAdjacentEnemy` is flaky
  (~1 in 8 full-suite runs) — random attack rolls; rerun before blaming a change.
- BRAWL music plays only with fists equipped from battle start; THRONE is forced
  during William boss fights via `appliedMusicMoods`. D Task 4 hooks this pipe —
  don't add a parallel mechanism.
- Building art is data-driven (`app/src/main/assets/art/*.json`, drawn via
  `BackgroundObjectType.VECTOR`); new static backdrops can be JSON assets,
  interactive pieces (gate, damage states) stay Kotlin.

## Key code landmarks

| Area | Location |
|---|---|
| Music theory/selection | `MusicTheory.kt` (`resolveSongSpec`) |
| Orchestration | `Orchestrator.kt` (`planOrchestration`, `percussionEvents`) |
| Instrument synths | `InstrumentsPluck.kt`, `InstrumentsBowBrass.kt`, `InstrumentsPerc.kt` |
| Composer facade | `ProceduralMedievalComposer.kt` (`compose`) |
| Audio player/UI wiring | `MedievalHarpPlayer.kt`, `MainActivity.kt` |
| Sim/battle loop | `GameViewModel.kt` (`updateSimulation`; bgObjects ~`:995`) |
| Combat/status effects | `CombatEngine.kt` (mirror `poisonDuration` for IGNITE) |
| Rendering + weather | `MainActivity.kt` (`drawWeatherFlourish` ~`:2405`), `BuildingRenderer.kt`, `TapestryRenderer.kt` |
| Music tests | `app/src/test/java/com/example/game/SoundTest.kt` |
