# Plague, Weather & Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the 2026-07-13 batch: six bug fixes, small-fighter viability, diseased peasant, divine weather events, armour weight/chariot collapse, curve counters, visual work, and vector-builder auto-discovery.

**Architecture:** Kotlin/Compose Android game. Simulation state in `SimulationModels.kt`, tick logic in `CombatEngine.kt`, orchestration in `GameViewModel.kt`, rendering split across `TapestryRenderer.kt` / `BuildingRenderer.kt` / `MountRenderer.kt` / `StitchCraft.kt`. JUnit tests exist under `app/src/test/java/com/example/game/`.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, JUnit. Vector builder: Node/Express (`scripts/vector_builder/`).

**Spec:** `docs/superpowers/specs/2026-07-13-plague-weather-and-polish-design.md` — read the matching section before each task.

## Global Constraints

- Build: `gradle compileDebugKotlin` (global gradle, no wrapper). Tests: `gradle testDebugUnitTest`.
- Never add assets under `app/build/`.
- **Art direction:** every new visual must be distinct at a glance but built from tapestry vocabulary — `drawStitchedFill`, period palette, Bayeux linework. No modern-looking art.
- All balance numbers below are starting values — keep them as named constants so playtests can tune them.
- Commit after every task.

## Model/effort key (per user request — prefer medium/low)

- **[sonnet/low]** mechanical, well-bounded edits.
- **[sonnet/medium]** logic changes with tests.
- **[opus/medium]** vector art, cross-cutting systems, visual judgment. No task needs high effort; if one feels like it does, stop and flag rather than burn tokens.

---

### Task 1: Fists preselected must not equip a hilt — [sonnet/low]

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (weapon-selection logic ~lines 199-207, randomised loadout ~line 1117, and the `handlesWithoutFists` path ~line 277)
- Test: `app/src/test/java/com/example/game/GameViewModelTest.kt`

**Interfaces:** none new. Invariant to enforce: `weaponHead.id == "head_bare"` ⇒ `weaponHandle.id == "handle_fists"` at battle start.

- [ ] **Step 1: Write failing test** — in `GameViewModelTest.kt`, add a test that sets weapon head to `head_bare` via the view model's selection path, starts a new battle, and asserts the player fighter's `weaponHandle.id == "handle_fists"`. Follow the existing test style in that file for constructing the view model.
- [ ] **Step 2: Run** `gradle testDebugUnitTest --tests "*GameViewModelTest*"` — expect FAIL (a hilt gets equipped).
- [ ] **Step 3: Fix** — audit every place a handle is assigned when `head_bare` is the head. Line 199 already forces fists for bare fists; the bug is in a path that runs after or instead of it (check line 277's `handlesWithoutFists` and line 1117's random loadout). Enforce the invariant in one shared place if possible rather than patching each call site.
- [ ] **Step 4: Run tests** — expect PASS. Then `gradle compileDebugKotlin`.
- [ ] **Step 5: Commit** — `fix: fists never equip a hilt at battle start`

---

### Task 2: Fist reach — ground, throne, and interrupt-capable cadence — [sonnet/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (BARE weapon head, line 94; `attackSpeedDelay` ~line 450)
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` (approach/retreat logic ~lines 198-228; attack landing ~line 297+)
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`, `FighterStatsTest.kt`

**Interfaces:**
- Produces: `FighterState.isFists: Boolean` (true when `weaponHead.id == "head_bare"` — reuse `isBrawler` where dual condition already fits), constant `FIST_INTERRUPT_CHANCE = 0.25f` in CombatEngine.

- [ ] **Step 1: Failing tests** — (a) a fists player vs a retreating melee enemy closes to within `reach * 40f + 40f` pixels within a few simulated seconds (drive `CombatEngine` ticks directly like existing CombatEngineTest cases do); (b) a throne-mode fists player (isLord=true) reaches an adjacent enemy; (c) small (0.65f) fists fighter has `attackSpeedDelay` well below a medium fighter's.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement:**
  - Raise BARE reach `1.0f → 1.4f` in SimulationModels.kt:94.
  - In CombatEngine approach logic: when the fighter's weapon is fists (or any melee) and the target is retreating, allow the pursuer's `optimalDistance` to shrink (e.g. `reachPixels * 0.8f`) so it keeps closing instead of stalling at the boundary. Root-cause note: check why the enemy backs off at all when it has melee — the `optimalDistance`/retreat block at lines 210-228 likely retreats when the *player* is inside its optimal distance; melee enemies should hold ground, only ranged should kite.
  - Throne mode: find where the throne/lord player's position or reach is computed (pallbearer lock in `updateFighter`) and make sure the lord's own reach check uses his real posX + a throne offset so fists connect.
  - Fist cadence: in `attackSpeedDelay`, for fists give small/medium sizes a further multiplier (e.g. `if (weaponHead.id == "head_bare" && size <= 1.0f) 0.55f else 1f`).
  - Interrupt: in CombatEngine where a strike lands (~line 297+), if attacker is fists and `Random.nextFloat() < FIST_INTERRUPT_CHANCE`, reset the defender's `swingProgress = 0f; isAttacking = false; attackCooldown = max(attackCooldown, 0.4f)` and show a popup ("INTERRUPTUS!").
- [ ] **Step 4: Run tests** — PASS; `gradle compileDebugKotlin`.
- [ ] **Step 5: Commit** — `fix: fists reach/pursuit on ground and throne; fast interrupting punches for small fighters`

---

### Task 3: Small-fighter viability (HP + attack speed) — [OUTSOURCED: Gemini/antigravity; reviewed in main session. Sequence AFTER Tasks 2/4/6/7 — shares SimulationModels.kt/GameViewModel.kt with them]

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (player maxHp setup at battle start)
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (`attackSpeedDelay` sizeScale, line 462; `SIZE_PRESETS` descriptions, lines 20-26)
- Test: `app/src/test/java/com/example/game/FighterStatsTest.kt`

**Interfaces:** none new.

- [ ] **Step 1: Failing test** — a size-0.65f fighter gets a maxHp bonus vs baseline (e.g. `maxHp >= base * 1.15f`) and lower `attackSpeedDelay` than the current formula gives.
- [ ] **Step 2: Implement** — HP: where player maxHp is computed at battle start, add `* (1f + (1f - size).coerceAtLeast(0f) * 0.6f)` (tiny 0.65 → +21%, small 0.8 → +12%, medium+ unchanged). Attack speed: steepen the melee sizeScale at line 462 from `(0.4f + size * 0.6f)` to `(0.25f + size * 0.75f)`. Update `SIZE_PRESETS` description strings so the UI stops calling small sizes "fragile as wet parchment" if they no longer are.
- [ ] **Step 3: Tests PASS + compile.**
- [ ] **Step 4: Commit** — `balance: small fighters get bonus HP and faster attacks`

---

### Task 4: Chariot closes to melee range — [OUTSOURCED: Gemini/antigravity, worktree; reviewed in main session. Touches CombatEngine.kt — branch from main AFTER Task 2 merges]

**Files:**
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` (optimal-distance logic ~lines 210-228)
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`

**Interfaces:** none new. `FighterState.isChariot` already exists.

- [ ] **Step 1: Failing test** — a chariot-mounted melee player closes to within melee reach of a static enemy; a chariot-mounted *ranged* player still holds `reachPixels * rangeMult`.
- [ ] **Step 2: Implement** — the bug is the chariot using ranged-style standoff (or mount reach inflating `reach` so `optimalDistance` sits outside real hit range) regardless of weapon. Make `optimalDistance` depend on `fighter.isRanged` only; verify the `+ 1.5f` mountReach in `FighterState.reach` (SimulationModels.kt:402) matches where the attack-landing check measures from — if the chariot body offsets the player's posX visually but not in the sim, close the gap in the approach target rather than the renderer.
- [ ] **Step 3: Tests PASS + compile.**
- [ ] **Step 4: Commit** — `fix: chariot approaches to melee range for melee/fists weapons`

---

### Task 5: Crossbow bolt embed size — [OUTSOURCED: Gemini/antigravity; reviewed in main session. Renderer-only, safe to run in parallel]

**Files:**
- Modify: wherever bolts spawn `StuckProj` / building `stuckArrows` (search `ProjectileType.BOLT` in `GameViewModel.kt` / `CombatEngine.kt`; rendering in `TapestryRenderer.kt` and `BuildingRenderer.kt`)

**Interfaces:** none new. `StuckProj` already carries `size` and `isBallista`.

- [ ] **Step 1: Locate** — grep `BOLT` across game files; compare the size/scale maths for bolts against arrows (HANDOVER: embedded arrows divide out `fighter.size`, flying multiply by `scaleFactor`). Bolts are missing this normalisation or use an inflated base size.
- [ ] **Step 2: Fix** — apply the identical normalisation the ARROW path uses; if bolts share the arrow code path, reduce the bolt's base draw dimensions instead. One place, not per-call-site.
- [ ] **Step 3: Compile**, then `gradle installDebug` and eyeball a crossbow battle (bolts in enemies and buildings at sane size).
- [ ] **Step 4: Commit** — `fix: embedded crossbow bolts render at correct size`

---

### Task 6: Dagger — short hilt art + guaranteed-hit approach — [OUTSOURCED: Gemini/antigravity, worktree; reviewed in main session. Touches CombatEngine.kt — branch from main AFTER Tasks 2 & 4 merge]

**Files:**
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt` (dagger grip drawing in `drawWeapon`, search `handle_dagger`)
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` (approach logic)
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`

- [ ] **Step 1: Failing test** — dagger wielder (reach ≈ 0.9f total) vs largest enemy (size 1.45f): assert it closes to a distance where the landing check `abs(dx) <= reachPixels` is true within simulated time, for both tiny and huge player sizes.
- [ ] **Step 2: Implement** — approach: for very short reach weapons (`reach < 1.2f`), set approach target inside the hit boundary (e.g. `reachPixels * 0.6f`) so size mismatches can't leave them whiffing at the edge. Art: shorten the drawn dagger grip length substantially (roughly half) in the `handle_dagger` branch of `drawWeapon`.
- [ ] **Step 3: Tests PASS + compile; installDebug and eyeball the grip.**
- [ ] **Step 4: Commit** — `fix: dagger hilt shortened; short-reach weapons close to guaranteed-hit range`

---

### Task 7: Armour weight → chariot collapse + Silken Garments card — [OUTSOURCED: Gemini/antigravity, worktree; reviewed in main session. Touches GameViewModel.kt/SimulationModels.kt — expect a small rebase against parallel tasks]

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (BattleSimState: add `hasSilkenGarments: Boolean = false`)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (battle start ~line 316-350 where chariot mounts; level-up choice pool ~line 895+)
- Modify: `app/src/main/java/com/example/game/MountRenderer.kt` (`drawChariot`, line 189 — collapsed variant)
- Test: `app/src/test/java/com/example/game/GameViewModelTest.kt`

**Interfaces:**
- Produces: `ARMOR_WEIGHT_LIMIT = 22f` (kg, constant in GameViewModel companion — chainmail+coif fits, scale+extras does not); `BattleSimState.hasSilkenGarments`; LevelUpChoice id `"silken_garments"`.

- [ ] **Step 1: Failing tests** — (a) player with armour mass > limit and chariot unlocked starts battle with `isChariot == false` (on foot); (b) same loadout + `hasSilkenGarments = true` starts mounted; (c) the `silken_garments` choice appears in the pool only when armour mass > limit.
- [ ] **Step 2: Implement** — compute armour-only mass (armor + headgear + extraArmors) at battle start; if over limit and no silk: don't mount, set a flag on state so the UI shows a "THE CHARIOT COLLAPSES!" popup (reuse the CombatPopup path the throne uses for "THE THRONE FALLS!"), and draw the chariot as broken scenery at spawn (collapsed variant in `drawChariot`: snapped axle, tilted body — a few paths, stitched fills). Silken Garments card: type "armor", when taken sets `hasSilkenGarments = true`; description must say it lightens your armour below chariot weight while preserving armour level.
- [ ] **Step 3: Tests PASS + compile + installDebug eyeball.**
- [ ] **Step 4: Commit** — `feat: armour weight limit collapses chariot; Silken Garments card restores it`

---

### Task 8: Diseased peasant — behaviour, contagion, reward card — [opus/low]

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (Ancillary enum + FighterState fields)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (spawn at battle start — copy FANATIC pattern at line 366; ancillary reward pool)
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` (contagion tick + DoT — mirror `poisonDuration` at lines 93-94)
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`

**Interfaces:**
- Produces: `Ancillary.PLAGUE_PEASANT("anc_plague_peasant", "Wretched Aldwin", "Plague-Bearer", "A dying peasant who sprints at the foe. His pestilence spreads to ALL who come near — there is a small chance YOU catch it too.", color = Color(0xFF6B7D4A))`
- FighterState fields: `var diseaseDuration: Float = 0f`, `var isContagious: Boolean = false`
- CombatEngine constants: `DISEASE_DPS = 1.5f`, `DISEASE_DURATION = 12f`, `DISEASE_RADIUS_PX = 70f`, `PLAYER_CATCH_CHANCE_PER_SEC = 0.001f` (1% per 10s), `CORPSE_CONTAGION_SECS = 4f`

- [ ] **Step 1: Failing tests** — (a) an enemy within `DISEASE_RADIUS_PX` of a contagious fighter gets `diseaseDuration > 0` within a tick; (b) diseased fighter loses ~`DISEASE_DPS` hp/sec; (c) diseased player becomes `isContagious`; (d) peasant fighter has very low hp and `targetX` toward enemies.
- [ ] **Step 2: Implement** — spawn: copy the FANATIC block; `maxHp = 15f`, fists, no armour, speedy. Contagion tick in CombatEngine's per-fighter update: for each contagious, alive-or-recently-dead (`deathTime` within `CORPSE_CONTAGION_SECS`) fighter, infect enemies in radius (set `diseaseDuration = DISEASE_DURATION`); for the player roll `PLAYER_CATCH_CHANCE_PER_SEC * dt`; catching it sets both `diseaseDuration` and `isContagious = true`. DoT: alongside the poison block at line 93. The peasant himself spawns `isContagious = true`.
- [ ] **Step 3: Reward card** — add to the ancillary pool with the description above (the catch-risk sentence is required by spec).
- [ ] **Step 4: Tests PASS + compile.**
- [ ] **Step 5: Commit** — `feat: plague peasant ancillary — kamikaze contagion, DoT, player catch risk`

---

### Task 9: Diseased peasant + green tint — vector art — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt` (`drawAncillaries` line 2049 — add PLAGUE_PEASANT branch; skin-colour resolution in `drawCharacter`/`drawTorso`/`drawHead` for the green tint)

**Interfaces:** consumes `Ancillary.PLAGUE_PEASANT`, `FighterState.diseaseDuration`.

- [ ] **Step 1: Green tint** — where fighter skin colour is resolved, if `diseaseDuration > 0f` blend skin toward sickly green (`lerp(skin, Color(0xFF7A9B4E), 0.45f)`). One place so it applies to enemies AND player.
- [ ] **Step 2: Peasant art** — distinct silhouette per art direction: hunched posture, ragged hemline tunic (jagged bottom edge) in muddy `0xFF6B7D4A`, bare feet, sickly green-tinged skin, sparse straw hair. Stitched fills throughout. Keep it inside the `when (anc)` branch so the vector builder picks it up.
- [ ] **Step 3: Compile + installDebug** — eyeball: peasant readable at gameplay zoom, tint visible on green-infected enemies.
- [ ] **Step 4: Commit** — `feat: plague peasant vector art + disease green tint`

---

### Task 10: Divine weather events — core system — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (new enum + BattleSimState fields)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (trigger fn, reward pool gating, cooldown tick)
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` or GameViewModel (effect application)
- Test: `app/src/test/java/com/example/game/GameViewModelTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class DivineWeather(val id: String, val label: String, val description: String) {
    LIGHTNING("weather_lightning", "Divine Bolt", "The heavens smite your mightiest foe."),
    FLOOD("weather_flood", "The Deluge", "A wall of water sweeps enemies from the field."),
    HAIL("weather_hail", "Hailstorm", "Fist-sized hail batters every foe to the ground."),
    FROST("weather_frost", "Killing Frost", "Ice underfoot — the enemy host slips and falls.")
}
```
- BattleSimState: `val divineWeathers: List<DivineWeather> = emptyList()`, `val weatherCooldowns: Map<String, Float> = emptyMap()`
- GameViewModel: `fun triggerWeather(id: String)`; constant `WEATHER_COOLDOWN = 60f`. Charge ready at battle start (cooldowns reset to 0 on battle start).
- Effects: LIGHTNING — kill/massively damage highest-hp enemy (150 dmg), scorch popup. FLOOD — the 2-3 enemies nearest the right edge get swept off-field (instant death, `KNOCKED_FLYING`, big x-velocity). HAIL — all enemies `isCrumpled = true, crumpleDuration = 2.5f`. FROST — all enemies `slowDuration = 6f` plus each has 50% chance to crumple 1.2s.
- Reward gating: only offered in the level-up pool at `level >= 12`, low weight, max 2 held per run.

- [ ] **Step 1: Failing tests** — trigger each weather on a stocked battle state and assert its effect (highest-hp enemy damaged; rightmost swept dead; all crumpled; all slowed). Assert cooldown blocks a second trigger and battle start resets it.
- [ ] **Step 2: Implement** per the interfaces above. Cooldown ticks down in the existing per-frame update.
- [ ] **Step 3: Add reward cards** — LevelUpChoice type `"weather"`, appearing only when `level >= 12 && divineWeathers.size < 2`, weighted rare.
- [ ] **Step 4: Tests PASS + compile.**
- [ ] **Step 5: Commit** — `feat: divine weather events — lightning, flood, hail, frost`

---

### Task 11: Weather border icons + battle visuals — [opus/medium] — use frontend-design skill

**Files:**
- Modify: `app/src/main/java/com/example/game/ui/GameMainScreen.kt` / `BayeuxBattleground.kt` (tapestry border rendering + tap handling)
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt` (effect visuals)

**Interfaces:** consumes `BattleSimState.divineWeathers`, `weatherCooldowns`, `GameViewModel.triggerWeather`.

- [ ] **Step 1: Icons** — mini icons drawn in the tapestry border (40px bands), one per held weather: jagged bolt, curling wave, cluster of hailstones, frost crystal/snowflake. Bayeux-border style: stitched outline, period palette, sitting between the existing border motifs. Greyed/dimmed while cooling down (radial or alpha sweep). Tap = `triggerWeather`.
- [ ] **Step 2: Effect visuals** — brief full-field flourishes in tapestry style: lightning = jagged gold-thread bolt from top border; flood = stitched wave band sweeping right-to-left; hail = falling white stitches; frost = pale blue ground tint. Keep each to one draw pass, no persistent particles beyond existing systems.
- [ ] **Step 3: Compile + installDebug** — eyeball icons at both cooldown states, trigger each event.
- [ ] **Step 4: Commit** — `feat: weather border icons and battle visual flourishes`

---

### Task 12: Mount selector on reward screen — [OUTSOURCED: Gemini/antigravity, worktree; reviewed in main session. Match existing reward-screen styling exactly]

**Files:**
- Modify: `app/src/main/java/com/example/game/ui/GameMainScreen.kt` (level-up/reward screen composables)
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (BattleSimState: `val activeMount: Ancillary? = null`)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (battle-start mount resolution + `fun selectMount(a: Ancillary)`)
- Test: `app/src/test/java/com/example/game/GameViewModelTest.kt`

**Interfaces:** mounts are the ancillaries whose id starts with `anc_mount_` (WARHORSE, CHARIOT, STILTS) plus THRONE mode — keep throne out of scope; selector covers `anc_mount_*` only.

- [ ] **Step 1: Failing test** — with two mounts unlocked and `activeMount` set to horse, battle starts with horse flags, not chariot's.
- [ ] **Step 2: Implement** — battle start resolves mount from `activeMount` (default: most recently unlocked). `selectMount` updates state.
- [ ] **Step 3: UI** — on the reward screen, when >1 mount unlocked, show a compact row of mount cards (name + small canvas thumbnail via the existing mount draw fns) with the active one highlighted; tap to swap. Match existing reward-screen styling.
- [ ] **Step 4: Tests PASS + compile + installDebug.**
- [ ] **Step 5: Commit** — `feat: mount selector on reward screen`

---

### Task 13: Curve counters + out cards + light reward weighting — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/EnemyFactory.kt` (counter enemy variants)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (counter detection + reward pool weighting; new LevelUpChoices)
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (BattleSimState: `val hasShieldbreaker: Boolean = false`, `val hasArmorPiercing: Boolean = false`)
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` (shieldbreaker/armour-pierce application; war-priest heal tick)
- Test: `app/src/test/java/com/example/game/GameViewModelTest.kt`, `CombatEngineTest.kt`

**Interfaces:**
- Counters (phase in by level): **shield-wall pairs** — from level 12, ~20% of waves include 2 enemies with TOWER shields and high `shieldHp`; **armoured brutes** — from level 15, occasional SCALE-armoured high-mass enemy; **war-priest** — from level 18, ~15% of waves include one MONK-robed enemy who heals the lowest-hp living ally 5 hp/sec within his radius, never attacks.
- Out cards: `LevelUpChoice("counter_shieldbreaker", "Shieldbreaker", "Your blows splinter shields to kindling. Triple damage to shield hp.", ...)` → shield damage ×3; `LevelUpChoice("counter_armor_piercing", "Armour-Piercing Stitch", "A blessed needle-point edge. A third of your damage ignores armour.", ...)` → 33% of damage bypasses `totalArmor`.
- Light weighting: when composing `pendingLevelUpChoices`, if the counter is active in recent waves and the player lacks the out, *add* the matching out card to the pool (guaranteed presence, normal position) — never remove other options, never auto-pick. That is the whole "light guidance" mechanism.

- [ ] **Step 1: Failing tests** — (a) war-priest heals an ally per tick; (b) shieldbreaker triples shield damage; (c) armour-piercing reduces effective armour by a third; (d) reward pool includes the out card when its counter was seen and player lacks it.
- [ ] **Step 2: Implement** per interfaces. War-priest art: reuse the MONK ancillary robes with enemy colours — keep it visually distinct (raised staff, no weapon).
- [ ] **Step 3: Tests PASS + compile.**
- [ ] **Step 4: Commit** — `feat: curve counters (shield-walls, brutes, war-priest) with out cards and light reward weighting`

---

### Task 14: Barechested skin + chest hair — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt` (`drawTorso` line 291 and arm drawing — search `armor_bare`)

- [ ] **Step 1: Locate** the `armor_bare` torso path (currently renders white shirt + blue arms).
- [ ] **Step 2: Implement** — torso and both arms filled with the fighter's skin tone (same colour used for face/hands), stitched fill; overlay sparse short hair strokes on chest and forearms in `fighter.hairColor` (a dozen short curved lines, deterministic jitter — no `Math.random` per frame, per the bgBitmap lesson). Pants untouched.
- [ ] **Step 3: Compile + installDebug** — eyeball with different hair colours and sizes.
- [ ] **Step 4: Commit** — `fix: barechested style renders skin + chest hair, not white shirt`

---

### Task 15: Blue woad paint + enemy cosmetic variance — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (FighterState: `val warPaint: Int = 0` — 0 none, 1 woad)
- Modify: `app/src/main/java/com/example/game/EnemyFactory.kt` (roll ~15% woad on spawn; widen existing hair/face variance rolls)
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt` (face/torso: woad overlay)

- [ ] **Step 1: Implement** — woad = bold blue (`0xFF3A5A8C`) stitched stripes/spirals across face and any bare skin, drawn after skin fill, deterministic per-fighter (seed from id hash). Scary, tapestry-flavoured. Variance: widen EnemyFactory's cosmetic rolls (hair styles/colours, mustache, forehead) so fewer clone waves.
- [ ] **Step 2: Compile + installDebug** — eyeball a wave with painted and unpainted mixed.
- [ ] **Step 3: Commit** — `feat: blue woad war paint enemy variant + wider cosmetic variance`

---

### Task 16: Palisade redraw — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/BuildingRenderer.kt` (palisade code inline in `drawFortMotte` line 235+ and `drawFortDinan` line 332+)

- [ ] **Step 1: Review** the current palisade paths to see why it reads as a termite mound (likely a single lumpy silhouette with spikes on top).
- [ ] **Step 2: Redraw** as individual vertical timber posts: repeated tall thin rectangles with pointed tops, slight per-post height/lean jitter (deterministic from seed), visible gaps, one or two horizontal lashing rails, stitched wood-grain fill (`0xFF735835` range). Extract a small `drawPalisadeRun(scope, x0, x1, y, seed)` helper used by both forts — it becomes vector-builder-editable automatically after Task 18.
- [ ] **Step 3: Compile + installDebug** — eyeball both fort types; bgBitmapCache means art changes need a cache-key-changing event or fresh battle to show.
- [ ] **Step 4: Commit** — `fix: palisade reads as timber posts, not termite mound`

---

### Task 17: Procedural Bayeux buildings + Rapunzel towers — [opus/medium]

**Files:**
- Modify: `app/src/main/java/com/example/game/BuildingRenderer.kt` (new draw fns + wire into `drawBackgroundObject` line 22)
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (BackgroundObjectType: add `BUILDING_BAYEUX`, `TOWER_SPIRAL`)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (spawn them in level generation where existing buildings spawn)

**Interfaces:** `fun drawBayeuxBuilding(scope: DrawScope, obj: BackgroundObject)` and `fun drawSpiralTower(scope: DrawScope, obj: BackgroundObject)` — same signature shape as `drawBuildingBosham`.

- [ ] **Step 1: Bayeux building** — procedural from `obj.seed`: 2-4 arched openings (semicircle arches on pillars), a pillared upper storey, tiled roof of overlapping scallop rows, palette varying between the tapestry building colours already used by Bosham/Manor. Every dimension seeded so no two identical.
- [ ] **Step 2: Spiral tower** — tall narrow tower (2-3x building height), tiled bands spiralling up (offset each row's scallops by seed-driven phase), small arched windows, conical roof with a pennant — like the tapestry's Rapunzel towers.
- [ ] **Step 3: Wire in** — add both to the level-gen building pool, HP similar to existing buildings; confirm bgBitmapCache keys work unchanged (they key off the object, not the type).
- [ ] **Step 4: Compile + installDebug** — eyeball several levels for variety and silhouette readability.
- [ ] **Step 5: Commit** — `feat: procedural bayeux buildings with arches and spiral-tiled towers`

---

### Task 18: Vector builder auto-discovery — [OUTSOURCED: Gemini/antigravity; reviewed in main session. scripts/vector_builder/ only, fully parallel-safe]

**Files:**
- Modify: `scripts/vector_builder/server.js` (replace `BUILDING_FNS`/`CREATURE_FNS`/`ANCILLARY_IDS` whitelists, lines 158-165)
- Test: `scripts/vector_builder/verify_asset_roundtrip.js` (extend)

**Interfaces:** `/api/assets` and `/api/asset/save` behaviour unchanged from the client's view; ids remain fn names / ancillary enum names.

- [ ] **Step 1: Implement discovery** — scan every file in `RENDERER_FILES` (+ keep the list of files current: it already covers the four split files): regex `(?:private |internal )?fun (draw[A-Z]\w*)\(` for draw functions (exclude the fighter-internal ones that take a FighterState and are too entangled to hand-edit: keep an `EXCLUDE` set of `drawCharacter, drawLegs, drawTorso, drawHead, drawWeaponHead, drawWeapon, drawFrontArmAndWeapon, drawBackArmAndShield, drawAncillaries, drawStitchedFill, drawStitchedStrap, drawDamageDecals, drawDamageFlurry, drawBackgroundObject` and the texture fns); regex `com\.example\.game\.Ancillary\.(\w+) ->` inside the held-item `when` for ancillary ids. Save path validates against the discovered set instead of the whitelists.
- [ ] **Step 2: Verify** — extend `verify_asset_roundtrip.js` to assert discovery finds `drawThrone` and (after Task 9) `PLAGUE_PEASANT`, and that a load→save roundtrip is byte-identical. Run `node verify_asset_roundtrip.js` — PASS.
- [ ] **Step 3: Commit** — `feat: vector builder auto-discovers draw functions and ancillary branches`

---

### Task 18b: Data-driven JSON art (sleek-refactor Phase E, previously deferred) — [opus/medium]

**Run AFTER Tasks 16 & 17** so we transcribe the *final* fort/palisade art, not art about to be replaced.

**Files:** exactly as specified in `docs/superpowers/plans/2026-07-13-sleek-refactor.md` Phase E:
- Create: `app/src/main/assets/art/*.json` (ship, fort_palace, fort_dinan, fort_tower, fort_motte, building_bosham, building_manor, trojan_horse), `VectorAsset.kt` (schema + loader + `drawVectorAsset`)
- Modify: `app/build.gradle.kts` (kotlinx-serialization), `BuildingRenderer.kt`, `scripts/vector_builder/server.js` (`/api/art` list/get/save), `scripts/vector_builder/index.html`, `verify_asset_roundtrip.js`

**Interfaces:** the Phase E schema verbatim — `VectorAsset { id, viewBox, palette, layers: [{ id, fill, stitched, stroke?, commands }] }`, ops M,L,Q,C,Z,RECT,OVAL,CIRCLE,LINE; `VectorAssets.get(context, id)` cached; `DrawScope.drawVectorAsset(asset, cx, cy, paletteOverride)`.

**Scope limits (why this was safe to defer and how we de-risk it now):**
- Static background objects ONLY. The new procedural Bayeux buildings / spiral towers (Task 17) are seed-driven and **stay code**. Damage decals / stuck arrows stay code.
- Port one building at a time; after each, side-by-side check in the builder preview AND `gradle installDebug` for an on-device eyeball before deleting the Kotlin body. The original deferral reason was fidelity risk — the per-building device check is the mitigation, so do not batch the deletions.

- [ ] **Step 1:** Schema/loader/interpreter in `VectorAsset.kt` + unit test (parse a sample asset, assert command count and palette lookup).
- [ ] **Step 2:** Port buildings one at a time per the scope limits above. bgBitmapCache is unaffected (it caches the rendered result, not the source).
- [ ] **Step 3:** server.js `/api/art` endpoints (validate JSON schema before write); index.html asset picker + per-palette `<input type="color">`; keep legacy Kotlin-region editing for everything not ported.
- [ ] **Step 4:** `node verify_asset_roundtrip.js` PASS (extend it to roundtrip one JSON asset byte-identically); `gradle testDebugUnitTest` + compile PASS.
- [ ] **Step 5: Commit** — `feat: data-driven building art shared by game and vector builder`

---

### Task 19: Final verification + version bump — [opus/low]

- [ ] **Step 1:** `gradle testDebugUnitTest` — full suite PASS.
- [ ] **Step 2:** `gradle installDebug` — playtest checklist on device: fists battle (no hilt, reach, interrupts), chariot melee, overweight collapse + silk card, peasant contagion + green + catch text on card, each weather event via border icon, mount swap, palisade/buildings/barechested/woad eyeball.
- [ ] **Step 3:** Bump version (v0.3.1) in the same place as the v0.3.0 bump commit (25232e4 shows where).
- [ ] **Step 4:** Commit — `chore: bump version to v0.3.1`

---

## Task order & batching

Tasks 1-6 are independent bug fixes (any order). Task 8 before 9 (art consumes the enum/fields). Task 10 before 11. Task 9 before Task 18's peasant assertion. Tasks 16 & 17 before 18b (port final art only). Everything else independent.

## Self-review notes

- Spec coverage: all spec sections map to tasks (1:§1 fists-hilt, 2:§1 fists-range+throne + §2 interrupt, 3:§2, 4:§1 chariot, 5:§1 bolts, 6:§1 dagger, 7:§4 weight, 8+9:§4 peasant, 10+11:§4 weather, 12:§4 mount selector, 13:§5 curve, 14-17:§3 visuals, 18:§6 tooling).
- Balance constants are starting values by design, flagged as tunable in Global Constraints.
