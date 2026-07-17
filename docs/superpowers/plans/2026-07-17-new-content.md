# New Content Implementation Plan (Sub-project D)

**Design:** `docs/superpowers/specs/2026-07-17-new-content-design.md`

**TDD loop per task:** write the failing test, run
`.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.*"` to red,
implement, run to green. Commit per task with conventional messages
(`feat: ...` / `fix: ...` / `test: ...`).

## Task 1 — Siege core: elevation band, gate, ladder state machine

- `app/src/main/java/com/example/game/SimulationModels.kt`: add siege state —
  `SiegeState(gateHp, parapetFighterIds, queuedFighterIds, ladderSpawned)`,
  fighter-level `elevated: Boolean` and `ClimbState` (NONE / CLIMBING_UP /
  CLIMBING_DOWN, uninterruptible timer).
- `app/src/main/java/com/example/game/GameViewModel.kt` (`updateSimulation`):
  siege branch — melee can't target elevated fighters; parapet ranged
  defenders get the downhill damage bonus; gate is melee-attackable; on gate
  break: release queue, split parapet (fixed fraction climbs down), spawn
  ladder.
- Tests FIRST (new `SiegeStateTest.kt` / `GameViewModelTest.kt` cases) — one
  per ladder invariant from the spec:
  1. Gate break sends a fixed fraction of parapet defenders down; melee-only
     defenders never climb.
  2. No ladder before gate break.
  3. Player can mount only while ≥1 living parapet enemy.
  4. Last parapet kill while player elevated → auto climb-down.
  5. Empty parapet → ladder inert.
  6. Climbing fighters can't attack or be hit; climb always completes.
  7. Battle end reachable from every siege configuration (exhaustive
     small-state sweep).

## Task 2 — Siege scheduling

- Seeded cadence from `gameSeed + level` (every 6–8 non-boss levels from
  level 5). Boss levels (10, 20, 30, 40…) are excluded.
- Tests: deterministic for a fixed seed; cadence within 6–8; zero overlap
  with boss levels across levels 1–100.

## Task 3 — New archetypes + IGNITE

- `app/src/main/java/com/example/game/EnemyFactory.kt`: add `WALL_ARCHER`,
  `TORCH_BEARER`, `DANE_AXE_EXECUTIONER`, `MONK_MILITIA`, `NORMAN_LOYALIST`
  to `EnemyArchetype` with kits per spec (level-banded like existing entries).
- `app/src/main/java/com/example/game/CombatEngine.kt`: IGNITE DoT mirroring
  the existing `poisonDuration` plumbing (apply/tick/expire, no stacking);
  torch projectile type; executioner armor-shred hit; monk chant aura
  (attack-speed buff radius); loyalist elite tier.
- Tests (mirror existing `EnemyFactory`/`CombatEngineTest.kt` patterns): valid
  kits across level ranges; IGNITE lifecycle + no-stack; aura applies only to
  defenders in radius; shred reduces armor not HP.

## Task 4 — Bosses: the 1066 trio

- Boss schedule: level 10 Harold, 20 Hardrada, 30+10n William (scaling).
- `GameViewModel.kt` battle setup: boss spawn (oversized model via existing
  size scaling), retinues — elite `HOUSECARL` (Harold), elite `BERSERKER`
  choke-fed (Hardrada), `NORMAN_LOYALIST` (William).
- Harold's arrow-to-the-eye crit window on player ranged hits.
- Music/banner: `FlavourText.latinHeadline` boss variant; DRUM_ROLL stinger;
  William forces THRONE theme via `appliedMusicMoods` pipe.
- Tests: schedule mapping 1–100; boss levels never siege; William battle
  applies THRONE; eye-crit window multiplies ranged damage only.

## Task 5 — Siege backdrop set (interactive)

- `BackgroundObjectType`: `CASTLE_WALL`, `CASTLE_GATE`, `MOTTE` +
  `BuildingRenderer.kt` draw functions (stitched pipeline). Gate renders
  damage states from `SiegeState.gateHp`; wall parapet defines the elevated
  Y-band used by Task 1.
- Static wall/motte go through sub-project C's backdrop cache; gate and
  parapet fighters stay live.
- Tests: siege levels select the siege set; gate damage state thresholds.

## Task 6 — New battlegrounds

- `BuildingRenderer.kt` + `BackgroundObjectType`: feasting hall, fleet
  crossing (ships + sea band), Mont-Saint-Michel (+ quicksand band).
- Level-band selection in `bgObjects` generation (`GameViewModel.kt:995`),
  seed-deterministic like existing scenery.
- Tests: band mapping deterministic per seed; all types renderable (existing
  renderer smoke-test pattern).

## Task 7 — Weather full-bleed fix

- `MainActivity.kt:2405` `drawWeatherFlourish`: extract per-weather geometry
  to pure functions taking an `innerFieldRect` (canvas inset by border);
  wrap all drawing in `clipRect(innerFieldRect)`.
- Rework hardcoded fractions: LIGHTNING strike positions/ground from inner
  rect; FLOOD sweep traverses full inner width with overshoot; audit every
  `DivineWeather` case the same way.
- Tests (new `WeatherFlourishTest.kt`): for phone + tablet aspect ratios,
  geometry bounds cover the full inner rect and never exceed it.

## Task 8 — Final verification

- `.\gradlew.bat :app:compileDebugKotlin` clean; full
  `.\gradlew.bat :app:testDebugUnitTest` green;
  `.\gradlew.bat :app:assembleDebug`.
- Manual pass: each boss; one full siege including ladder up/down and the
  auto-descent; each new backdrop; every weather flourish at full bleed.
- Commit history: one conventional commit per task.
