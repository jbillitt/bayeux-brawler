# Sleek Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the six audit findings with zero gameplay regressions: typed sim, split god files, tick-driven combat, JVM tests, perf, and data-driven art shared between the game and the vector builder.

**Architecture:** Pure combat logic moves out of `GameViewModel` into a testable `CombatEngine` with an event-sink interface (audio/popups/particles). `TapestryRenderer` splits into focused renderer files behind the existing `object TapestryRenderer` facade. Building art migrates from hand-coded Kotlin paths to typed JSON assets in `app/src/main/assets/art/`, rendered by a small interpreter; the vector builder edits those JSON files directly (shapes AND fill colours) instead of doing regex surgery on Kotlin.

**Tech Stack:** Kotlin/Compose (app), JUnit4 JVM unit tests, kotlinx-serialization-json for art assets, Node/Express (builder server).

## Global Constraints

- No gameplay regressions: every phase compiles (`gradle :app:compileDebugKotlin`) and unit tests pass before commit.
- Multi-hit timing may quantise to the 33ms tick (user-approved); everything else behaviourally identical.
- Word-popups stay suppressed (current behaviour) — but via an explicit no-op `addFlavourShout`, not a hidden letter filter.
- Vector builder must be able to: list assets, edit shapes on canvas, edit named fill colours, save — for buildings at minimum.
- One commit per phase, message prefix `refactor:`/`test:`/`feat:` as appropriate.

---

### Phase A: Safety net + quick purges

**Files:**
- Create: `app/src/test/java/com/example/game/FighterStatsTest.kt`
- Modify: `app/build.gradle.kts` (ensure junit testImplementation), `.gitignore`, `app/src/main/java/com/example/game/GameViewModel.kt`

**Steps:**
- [ ] Add `.gitignore` entries: `my-upload-key.jks`, `logcat*.txt`, `build_output.txt`, `scripts/vector_builder/node_modules/`
- [ ] Delete the stdlib-shadowing `fun Float.coerceIn` at the bottom of GameViewModel.kt
- [ ] Fix enemy RNG: `kotlin.random.Random(System.currentTimeMillis() + index)` → `kotlin.random.Random.Default`
- [ ] Write `FighterStatsTest.kt`: attackSpeedDelay floor (>= 0.3f), two-handed speed halving, moveSpeed small-vs-large ordering, reach with extensions, baseDamage level scaling, scoreMultiplier (naked/jester), missingArm zeroes slash/pierce, piercing falloff constants
- [ ] Run `gradle :app:testDebugUnitTest` → PASS; run compile → PASS
- [ ] Commit `test: add JVM unit tests for fighter stat math + repo hygiene`

### Phase B: Typing

**Files:**
- Modify: `SimulationModels.kt`, `GameViewModel.kt`, `TapestryRenderer.kt`, `MainActivity.kt` (only where string ids are compared)

**Interfaces produced:**
- `enum class WrestlingMove { CHOKE_SLAM, BODY_THROW, SUPLEX }` replaces `activeWrestlingMove: String?`
- `enum class ProjectileType { ARROW, STONE, JAVELIN, ROCK, BOLT }` replaces `Projectile.type: String` and `StuckProj.type`
- `enum class DeathType` replacing magic ints (incl. `CRUMPLED_IN_PLACE` for 99)
- Identity comparisons: `weaponHead == GameData.WeaponHead.BARE` style replaces `.id == "head_bare"` string compares throughout sim code (renderer keeps id-based dispatch where the builder needs it)
- `FighterState.isBrawler: Boolean` computed property (bare head + fists) used by both wrestle-gate sites

**Steps:**
- [ ] Introduce enums; mechanical replacement; keep `id` strings on gear enums (data identity for saves/builder)
- [ ] Compile + tests PASS; play-logic diff review (grep for remaining `"head_`/`"handle_` compares in sim files)
- [ ] Commit `refactor: replace stringly-typed sim state with enums`

### Phase C: Combat engine extraction + tick-driven hits

**Files:**
- Create: `app/src/main/java/com/example/game/CombatEngine.kt`, `app/src/main/java/com/example/game/EnemyFactory.kt`, `app/src/test/java/com/example/game/CombatEngineTest.kt`
- Modify: `GameViewModel.kt` (shrinks to state-flow orchestration), `SimulationModels.kt` (PendingHit)

**Interfaces produced:**
- `interface BattleEvents { fun sound(t: SoundType); fun popup(text: String, x: Float, y: Float, color: Color); fun flavourShout(text: String, x: Float, y: Float, color: Color) /* no-op impl */; fun particles(...); fun screenshake(f: Float) }`
- `class CombatEngine(val events: BattleEvents)` owning: `updateFighter`, `triggerAttack`, `performStrike`, `applyProjectileDamage`, `applyFlatDamage` — no ViewModel, no coroutines
- `data class PendingHit(var delay: Float, val targets: List<FighterState>, val dmgScale: Float, ...)` on FighterState; processed each tick in `updateFighter` (replaces `viewModelScope.launch { delay(160) }`)
- `object EnemyFactory { fun randomSaxon(index: Int, level: Int): FighterState; fun ancillaryFighters(state: BattleSimState, player: FighterState): List<FighterState> }`

**Steps:**
- [ ] Extract EnemyFactory (pure move, no logic change)
- [ ] Extract CombatEngine with BattleEvents sink; GameViewModel implements BattleEvents
- [ ] Replace detached-coroutine multi-hit/ranged-volley with PendingHit ticked at 160ms intervals; dead-attacker check preserved
- [ ] CombatEngineTest: wrestle-gating (slinger never wrestles, brawler does), interrupt rule, block/shield-break math, pending-hit cancels on attacker death
- [ ] Compile + tests PASS. Commit `refactor: extract testable CombatEngine, tick-driven multi-hits`

### Phase D: Renderer split

**Files:**
- Create: `renderer/FighterRenderer.kt`, `renderer/MountRenderer.kt`, `renderer/BuildingRenderer.kt`, `renderer/StitchCraft.kt` (stitched fill/textures/shared consts)
- Modify: `TapestryRenderer.kt` becomes facade delegating (public API unchanged: `drawCharacter`, `drawAncillaries`, `drawBackgroundObject`)

**Steps:**
- [ ] Pure moves, no signature changes to the three public entry points; shared constants (ThreadColor, StitchedStroke) live in StitchCraft
- [ ] Compile PASS. Commit `refactor: split TapestryRenderer into focused renderer files`

### Phase E: Data-driven art + vector builder rework

**Files:**
- Create: `app/src/main/assets/art/*.json` (ship, fort_palace, fort_dinan, fort_tower, fort_motte, building_bosham, building_manor, trojan_horse), `renderer/VectorAsset.kt` (schema + loader + `drawVectorAsset`)
- Modify: `app/build.gradle.kts` (kotlinx-serialization), `BuildingRenderer.kt` (buildings render from assets; damage decals/stuck arrows stay code), `scripts/vector_builder/server.js` (+ `/api/art` list/get/save endpoints), `scripts/vector_builder/index.html` (asset picker, path editing, palette colour pickers), `scripts/vector_builder/verify_asset_roundtrip.js`

**Interfaces produced:**
- Schema: `VectorAsset { id, viewBox, palette: Map<String,String>, layers: [{ id, fill: paletteKey, stitched: Bool, stroke?: paletteKey, commands: [[op,args...]] }] }` ops: M,L,Q,C,Z,RECT,OVAL,CIRCLE,LINE
- Kotlin: `object VectorAssets { fun get(context, id): VectorAsset }` cached; `fun DrawScope.drawVectorAsset(asset, cx, cy, paletteOverride)`
- Server: GET `/api/art` → list, GET `/api/art/:id`, POST `/api/art/:id` (validated JSON schema before write)

**Steps:**
- [ ] Implement schema/loader/interpreter with unit test (parse sample, command count, palette lookup)
- [ ] Port buildings one at a time: transcribe existing Kotlin paths to JSON, render side-by-side check via existing preview, delete Kotlin body
- [ ] Rework server.js endpoints; keep legacy Kotlin-injection for weapon heads (still code)
- [ ] index.html: asset dropdown loads JSON, canvas edits layer points, colour `<input type="color">` per palette entry, save posts JSON
- [ ] Run `node verify_asset_roundtrip.js` → PASS; compile PASS
- [ ] Commit `feat: data-driven building art shared by game and vector builder`

### Phase F: Perf + popup honesty

**Files:**
- Modify: `GameViewModel.kt`, `SimulationModels.kt`

**Steps:**
- [ ] Batch particle additions: collect per-tick into one list, single `_particlesState.value` update per tick (currently O(n) copy per hit)
- [ ] Cap unbounded lists: `bloodDecals` (30), `stuckProjectiles` (12), particles (250, drop oldest)
- [ ] Popups: `addPopup` keeps numbers-only contract explicitly; all word callsites route to `events.flavourShout` (no-op today, one switch to enable later)
- [ ] Compile + tests PASS. Commit `perf: batch particle updates, cap unbounded battle lists`
