# Handover (2026-07-17) — build-out of plans A → B → C → D

**Repo:** `C:\Users\Josh\bayeux-brawler` · Windows 11, PowerShell,
Android/Kotlin, Jetpack Compose custom-canvas game. Use `.\gradlew.bat`.

## Current state (verify before starting)

- Working tree clean on `main`. Latest commit: `1e92b40`
  (`docs: new-content spec + implementation plan`).
- **A — Music themes (BRAWL/THRONE + choir): IMPLEMENTED & VERIFIED.**
  TTS system was removed en route (`2d1197b`).
- **B — Drums/instruments phone-band mix: IMPLEMENTED & VERIFIED.**
- **C — Battle performance: spec + plan written, NOT implemented.**
- **D — New content (sieges/bosses/backdrops/weather fix): spec + plan
  written, NOT implemented.**

First action: run `.\gradlew.bat :app:testDebugUnitTest` and confirm green.
That is your baseline. If A/B tests fail, fix before touching C/D.

## Execution order

1. **A, B** — already done; sanity-check only. Do not rebuild.
2. **C** — plan: `docs/superpowers/plans/2026-07-17-battle-performance-design.md`
   (spec: `specs/2026-07-17-battle-performance-design.md`). 6 tasks: tick
   scheduler, backdrop cache, stitch batching, popup/particle hygiene,
   regression harness, verification.
3. **D** — plan: `docs/superpowers/plans/2026-07-17-new-content.md`
   (spec: `specs/2026-07-17-new-content-design.md`). 8 tasks: siege core
   (7 ladder invariants — test each), siege scheduling, archetypes + IGNITE,
   1066 bosses, siege backdrop, new battlegrounds, weather full-bleed fix,
   verification.
4. **C strictly before D** — D Task 5 (siege backdrop) renders static
   wall/motte through C's backdrop cache.

## Working agreements (from the user, binding)

- Work through tasks continuously; do NOT stop for approval between tasks or
  plans. Pause only for genuinely ambiguous design questions.
- TDD per task: failing test → red → implement → green. Scoped run:
  `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.*"`.
- One conventional commit per task (`feat:`/`fix:`/`test:`/`docs:`), via
  `git commit --no-gpg-sign`. CRLF warnings on commit are normal; ignore.
- Keep the task list (TaskCreate/TaskUpdate) in sync with plan tasks.

## Key code landmarks

| Area | Location |
|---|---|
| Sim/battle loop | `app/src/main/java/com/example/game/GameViewModel.kt` (`updateSimulation`; bgObjects gen ~`:995`) |
| Combat/status effects | `.../CombatEngine.kt` (mirror `poisonDuration` plumbing for IGNITE) |
| Enemy kits | `.../EnemyFactory.kt` |
| Models | `.../SimulationModels.kt` |
| Rendering + weather | `.../MainActivity.kt` (`drawWeatherFlourish` ~`:2405`), `BuildingRenderer.kt`, `TapestryRenderer.kt` |
| Music/audio (done — don't break) | `MusicTheory.kt`, `Orchestrator.kt`, `ProceduralMedievalComposer.kt`, `MedievalAudioSynth.kt`, `InstrumentsPluck.kt` |
| Art screenshot harness | `app/src/test/java/com/example/game/ArtScreenshotTest.kt` — renders real draw fns to PNGs (`-Proborazzi.test.record=true --rerun`). Use it to eyeball new backdrops in D Tasks 5–6. |

## Gotchas

- BRAWL theme plays ONLY with fists equipped, starting at battle start;
  THRONE is forced during William boss fights via the `appliedMusicMoods`
  pipe. D Task 4 hooks into this — do not add a parallel mechanism.
- All spawns/scenery/siege cadence must be seed-deterministic
  (`gameSeed + level`); tests assert exact sequences for fixed seeds.
- Audio is phone-band mixed (300 Hz–4 kHz emphasis) — keep
  `everyVoiceRendersAudibleSound`-style tests green if touching synth code.
- After C lands: static scenery goes through the backdrop cache; live actors
  (gate, parapet fighters) must NOT be cached.
- Weather fix (D Task 7): extract per-`DivineWeather` geometry to pure
  functions of an `innerFieldRect`, wrap drawing in `clipRect`; tests assert
  full-bleed coverage without overflow on phone + tablet aspect ratios.
- Building art is data-driven (`app/src/main/assets/art/*.json`, discovered
  not registered, drawn via `BackgroundObjectType.VECTOR`). New static
  backdrops in D can be JSON assets; seeded/procedural and interactive
  pieces (gate, damage states) stay Kotlin.

## Known issues carried forward (pre-existing, deliberate)

- `CombatEngineTest.throneModeFistsPlayerReachesAdjacentEnemy` is flaky
  (~1 in 8 full-suite runs) — random attack rolls; rerun before assuming
  your change broke it.
- Poison/bleed were root-cause rebalanced (`applyDotDamage` accrues
  sub-point damage); still awaiting a human playtest.
- Untracked cruft never touched: `test_out.txt`, `tapestry_options.html`,
  logcats. Stale Gemini worktrees may still need `git worktree prune`.

## Definition of done (whole effort)

`compileDebugKotlin` clean; full `testDebugUnitTest` green; `assembleDebug`
builds; manual pass per D plan Task 8 (each boss, one full siege with ladder
up/down + auto-descent, each new backdrop, every weather flourish at full
bleed); clean conventional commit history, one commit per task.
