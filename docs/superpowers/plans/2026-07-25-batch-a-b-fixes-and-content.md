# Batch A + B — Fixes and Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix five combat/render defects and add seventeen new gear items plus six sound folders to Bayeux Brawler.

**Architecture:** Every change is an edit to an existing file. Combat rule changes go through `CombatEngine`'s existing chokepoints (`followerCatchUpMultiplier`, the melee target-gathering chain) rather than new systems. New gear is data in `GameData` enums plus draw cases in the existing renderer switches. Sound folders plug into the existing asset-folder enumeration, which already no-ops on empty folders.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas rendering), JUnit 4, Robolectric, Roborazzi. Gradle 9.6 (system gradle — **there is no wrapper**).

## Global Constraints

- **No gradle wrapper exists.** Use the system gradle on PATH (`C:\ProgramData\chocolatey\bin\gradle.exe`, Gradle 9.6.0). Never write `./gradlew`.
- Unit tests: `gradle :app:testDebugUnitTest --console=plain`
- Single class: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest" --console=plain`
- Compile check only: `gradle :app:compileDebugKotlin --console=plain`
- **Gradle daemon quirk:** a compile takes ~30-40s. If a gradle command has not returned after ~60s it is NOT still compiling — the daemon finished but never handed back the result. Check the newest `~/.gradle/daemon/9.6.0/daemon-*.out.log` for `BUILD SUCCESSFUL` or lines starting `e: `, then `gradle --stop` and re-run. Do not sit and wait.
- Do not pipe gradle output through `Select-Object -Last N` — it buffers everything until completion.
- `ArtScreenshotTest` and `MagazinePreviewTest` are **human-eyeball renders, not golden assertions**. Nothing fails on a pixel diff. They write PNGs to `app/src/test/screenshots/`. Never describe them as passing or failing a visual check — any real verification needs a value assertion.
- All new gear ids follow existing prefixes: `handle_`, `armor_`. All new `Ancillary`/gear entries carry a `description` and a `color` — both are required constructor params.
- Commit after every task.

---

### Task 1: Frontline entourage outruns the player

**Files:**
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt:145-151` (`followerCatchUpMultiplier`), plus a constant in the companion near `:97`
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`

**Interfaces:**
- Consumes: `FighterState.isKind(kind: String): Boolean` (`SimulationModels.kt:649`), `FighterState.moveSpeed: Float` (`SimulationModels.kt:580`)
- Produces: `CombatEngine.Companion.FRONTLINE_LEAD: Float`, `CombatEngine.Companion.FRONTLINE_KINDS: Set<String>`

**Background the implementer needs:** ancillary `speedBoost` is applied to the *player* (`GameViewModel.kt:604` reads `state.totalSpeedBoost`), not the ally. Allies carry fixed hardcoded boosts set at spawn. So every recruit makes the player faster while the entourage stays put. The fix must be a ratio against the player's live `moveSpeed`, never a bigger constant on the ally.

`followerCatchUpMultiplier` is called from four places (`:360`, `:486`, `:499`, `:513`). Changing it covers all of them.

- [ ] **Step 1: Write the failing test**

Add to `CombatEngineTest.kt`. Note the existing `fighter()` helper defaults `isPlayer = false`; allies are `isPlayer = true` with a `wardog#N` id.

```kotlin
@Test
fun frontlineAllyOutrunsASpeedBoostedPlayer() {
    val ctx = FakeContext()
    val engine = CombatEngine(ctx)

    val player = fighter(isPlayer = true, posX = 500f).apply { speedBoost = 1.5f }
    val dog = fighter(isPlayer = true, posX = 460f)
        .let { it.copy(id = "wardog#0", weaponHead = GameData.WEAPON_HEADS.first { h -> h.id == "head_bare" }) }
    val foe = fighter(posX = 1200f)
    ctx.player = player
    ctx.enemies = listOf(dog, foe)

    val dogStart = dog.posX
    val playerStart = player.posX
    repeat(60) {
        engine.updateFighter(dog, foe, 1f / 60f)
        engine.updateFighter(player, foe, 1f / 60f)
    }

    val dogMoved = dog.posX - dogStart
    val playerMoved = player.posX - playerStart
    assertTrue(
        "dog advanced $dogMoved, player advanced $playerMoved — frontline must lead",
        dogMoved > playerMoved
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest.frontlineAllyOutrunsASpeedBoostedPlayer" --console=plain`

Expected: FAIL — the dog advances the same distance or less than the player, because `followerCatchUpMultiplier` caps at parity.

- [ ] **Step 3: Add the constants**

In `CombatEngine.kt`, in the `companion object`, next to `TROJAN_ROLL_MULT` (`:97`):

```kotlin
/**
 * The frontline reaches the fray first. Ancillary speedBoost buffs the *player*, not the
 * ally, so every recruit makes the lord faster while his dogs stay put — the floor has to
 * be a ratio against his live speed, not a bigger number on the ally.
 */
const val FRONTLINE_LEAD = 1.2f

/** Melee chargers who lead the advance. Backline lobbers are ranged and must keep kiting. */
val FRONTLINE_KINDS = setOf("fanatic", "wardog", "plague_peasant", "raven")
```

- [ ] **Step 4: Rewrite `followerCatchUpMultiplier`**

Replace `CombatEngine.kt:145-151` entirely:

```kotlin
private fun followerCatchUpMultiplier(fighter: FighterState, direction: Float): Float {
    val player = ctx.player ?: return 1f
    if (!fighter.isPlayer || fighter.pallbearerIndex >= 0 || fighter === player) return 1f
    val deltaToPlayer = player.posX - fighter.posX
    val catchUp = if (deltaToPlayer == 0f || direction * deltaToPlayer <= 0f) 1f
        else 1f + ((abs(deltaToPlayer) - 150f) / 300f).coerceIn(0f, 1.5f)

    // Advancing only: a retreat-speed floor would shove kiters backwards faster than they mean to.
    if (direction <= 0f) return catchUp
    if (FRONTLINE_KINDS.none { fighter.isKind(it) }) return catchUp
    if (fighter.moveSpeed <= 0f) return catchUp
    return maxOf(catchUp, (player.moveSpeed * FRONTLINE_LEAD) / fighter.moveSpeed)
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest.frontlineAllyOutrunsASpeedBoostedPlayer" --console=plain`

Expected: PASS

- [ ] **Step 6: Run the whole combat suite for regressions**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest" --console=plain`

Expected: PASS, including the existing `trojanHorseRollsPastFoesThenBursts` (the Trojan uses `TROJAN_ROLL_MULT` on a separate path and must be unaffected).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game/CombatEngine.kt app/src/test/java/com/example/game/CombatEngineTest.kt
git commit -m "feat: frontline entourage always outpaces the player"
```

---

### Task 2: Buster's HP and the Trojan spearmen's panoply

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt:874` (Buster HP), `:951-960` (extract the arming pass), `:1404-1416` (Trojan spearman spawn)
- Test: `app/src/test/java/com/example/game/GameViewModelTest.kt` — create if absent

**Interfaces:**
- Produces: `private fun applyRetinuePanoply(fighter: FighterState)` on `GameViewModel`

**Background:** `hasRetinuePanoply` arms the retinue in one pass at battle start (`:951`), which runs *after* every spawn block. The three Trojan spearmen do not exist then — they spawn on the horse's death (`:1404`). Duplicating the gear block at the spawn site is the wrong fix; the next late-spawning ally would break again. Extract once, call twice.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/GameViewModelTest.kt` if it does not exist. If it does, add only the test method and any missing imports.

```kotlin
package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GameViewModelTest {

    private fun spearman() = FighterState(
        id = "trojan_knight_1_0", name = "Trojan Spearman", isPlayer = true,
        maxHp = 45f, hp = 45f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_spear" },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" },
        posX = 0f, targetX = 0f, size = 0.95f, hairColor = Color.Black, hairStyle = "short"
    )

    @Test
    fun panoplyArmsAFighterInMailAndGauntlets() {
        val f = spearman()
        GameViewModel.applyRetinuePanoply(f)
        assertEquals("armor_chainmail", f.armor.id)
        assertEquals("helm_spangen", f.headgear.id)
        assertEquals(true, f.extraArmors.any { it.id == "armor_gauntlets" })
    }
}
```

Note: the test calls it on the companion. Declare `applyRetinuePanoply` in a `companion object` on `GameViewModel` so it is reachable without constructing the ViewModel (which needs an Android context). If `GameViewModel` has no companion object, add one.

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameViewModelTest" --console=plain`

Expected: FAIL — `Unresolved reference: applyRetinuePanoply`.

- [ ] **Step 3: Extract the arming function**

Add to `GameViewModel`'s `companion object`:

```kotlin
/**
 * Kit one ally out in the retinue panoply. Shared by the start-of-battle pass and the
 * Trojan Horse's spearmen, who spawn long after that pass has run.
 */
fun applyRetinuePanoply(fighter: FighterState) {
    fighter.headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" }
    fighter.armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" }
    fighter.extraArmors = fighter.extraArmors + GameData.ARMOR_PIECES.first { it.id == "armor_gauntlets" }
}
```

- [ ] **Step 4: Call it from the start-of-battle pass**

Replace the body of the `forEach` at `GameViewModel.kt:955-959`. The surrounding `if (state.hasRetinuePanoply)` and its filter stay exactly as they are:

```kotlin
if (state.hasRetinuePanoply) {
    enemies.filter {
        it.isPlayer && !it.isKind("wardog") && !it.isKind("raven") &&
            it.id != "trojan_horse" && !it.id.startsWith("pallbearer_")
    }.forEach { ally -> applyRetinuePanoply(ally) }
}
```

- [ ] **Step 5: Call it from the Trojan spearman spawn**

At `GameViewModel.kt:1404`, inside the `for (i in 0 until 3)` loop, the spearman is currently added directly via `newEnemiesToSpawn.add(FighterState(...))`. Change it to build the fighter first, arm it, then add:

```kotlin
val spearman = FighterState(
    id = "trojan_knight_${System.currentTimeMillis()}_$i", name = "Trojan Spearman", isPlayer = true,
    maxHp = 45f, hp = 45f,
    weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_spear" },
    weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
    shield = GameData.SHIELDS.first { it.id == "shield_none" },
    armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
    headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" },
    posX = enemy.posX + Random.nextInt(-40, 40),
    targetX = enemy.posX, facingRight = true, size = 0.95f,
    hairColor = Color.Black, hairStyle = "short", isDualWielding = false
)
// The men in the belly are retinue too — they just arrive late.
if (_uiState.value.hasRetinuePanoply) applyRetinuePanoply(spearman)
newEnemiesToSpawn.add(spearman)
```

- [ ] **Step 6: Double Buster's HP**

At `GameViewModel.kt:874`, in the `WARDOG` spawn block, change `maxHp = 75f, hp = 75f` to:

```kotlin
id = "wardog#$i", name = "Buster", isPlayer = true, maxHp = 150f, hp = 150f,
```

The per-level `allyHpBonus` at `:941` still stacks on top — leave it alone.

- [ ] **Step 7: Run the tests**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS, all suites.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/GameViewModelTest.kt
git commit -m "fix: Trojan spearmen inherit the panoply; Buster gets double HP"
```

---

### Task 3: Stilts attach to the feet at every body size

**Files:**
- Modify: `app/src/main/java/com/example/game/MountRenderer.kt:306-328` (`drawStilts`), `app/src/main/java/com/example/game/TapestryRenderer.kt:157-161` (mount dispatch), `:241-243` (body draw)
- Test: `app/src/test/java/com/example/game/StiltsGeometryTest.kt` (create)

**Interfaces:**
- Produces: `stiltTopY(cy: Float): Float` and `stiltGroundY(cy: Float, effectiveSize: Float): Float` as internal top-level functions in `MountRenderer.kt`
- Consumes: `STILTS_LIFT_PX` (`MountRenderer.kt:18`)

**Background — the actual bug, with numbers.** The stilts and the body are drawn in different transform spaces and only agree at size ≈ 1.0.

`drawStilts` is called inside the mount block (`TapestryRenderer.kt:157-161`) under `scale(horseScale / effectiveSize, pivot = (cx, cy+80))`, which itself sits inside `drawCharacter`'s outer `scale(effectiveSize, pivot = (posX, 358))` (`:64`). Net screen y of the stilt top (`footY = cy + 105f - (STILTS_LIFT_PX - 45f)`, `MountRenderer.kt:314`) works out to `358 - 78*effectiveSize - 20*horseScale`.

The body's feet are drawn by `drawLegs` at local `cy + 155f`, inside the same outer ground-pivoted scale, then lifted by `adjustedMountOffsetY = -STILTS_LIFT_PX / effectiveSize` (`TapestryRenderer.kt:229`) — which cancels the scale, giving a flat 90px screen lift. Net foot y ≈ `265`, near enough size-independent.

| `fighter.size` | stilt top | feet | error |
|---|---|---|---|
| 0.65 | ~287 | ~266 | top **21px below** the feet — the player hovers |
| 1.0 | ~260 | ~265 | ~5px, reads as correct |
| 1.4 | ~229 | ~264 | feet **35px below** the top — legs sunk into the poles |

`horseScale` is a seeded random 0.9–1.1 (`TapestryRenderer.kt:159`), adding a further ±2px.

**The fix:** stop drawing the stilts in the mount's space. Draw them in the body's own lifted space, alongside `drawLegs`, anchored to the boot sole and running down to the true ground. They then scale and swing with the man automatically.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/StiltsGeometryTest.kt`:

```kotlin
package com.example.game

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stilt tops must meet the boots at every body size. Before this test the two were drawn in
 * different transform spaces and only lined up at size 1.0 — a small man hovered ~21px above his
 * own stilts, a large one sank ~35px into them.
 */
class StiltsGeometryTest {

    private val cy = 200f

    /** Where drawLegs puts the sole of the boot, in the body's own (lifted) local space. */
    private fun legSoleY(cy: Float) = cy + 155f

    @Test
    fun stiltTopMeetsTheBootSoleAtEverySize() {
        listOf(0.65f, 0.85f, 1.0f, 1.15f, 1.4f).forEach { size ->
            val gap = abs(stiltTopY(cy) - legSoleY(cy))
            assertTrue("size $size: stilt top is ${gap}px from the boot sole", gap <= 8f)
        }
    }

    @Test
    fun stiltsReachTheGroundBelowTheLiftedBody() {
        // The body is lifted STILTS_LIFT_PX screen px; in local space that is LIFT/size below the
        // sole. The poles must span the whole gap or the man stands on air.
        listOf(0.65f, 1.0f, 1.4f).forEach { size ->
            val span = stiltGroundY(cy, size) - stiltTopY(cy)
            val expected = STILTS_LIFT_PX / size
            assertTrue(
                "size $size: poles span $span, need about $expected",
                abs(span - expected) <= 12f
            )
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.StiltsGeometryTest" --console=plain`

Expected: FAIL — `Unresolved reference: stiltTopY`.

- [ ] **Step 3: Add the geometry functions and rewrite `drawStilts`**

In `MountRenderer.kt`, add above `drawStilts`:

```kotlin
/**
 * Top of the poles, in the body's own local space: the sole of the boot drawLegs draws at
 * cy + 155f, nudged up a touch so the footrest sits under the foot rather than through it.
 */
internal fun stiltTopY(cy: Float): Float = cy + 150f

/**
 * Bottom of the poles. The body is lifted STILTS_LIFT_PX *screen* pixels, which in this local
 * space (scaled by effectiveSize) is that much divided by the size. Anything else and a small
 * man's poles stop short of the ground.
 */
internal fun stiltGroundY(cy: Float, effectiveSize: Float): Float =
    cy + 155f + STILTS_LIFT_PX / effectiveSize
```

Replace `drawStilts` (`MountRenderer.kt:306-328`) entirely:

```kotlin
internal fun drawStilts(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState, effectiveSize: Float) {
    val wColor = Color(0xFF8B7355) // Wood
    // Match the leg anim exactly (animFrame * 0.45f) — same pivots drawLegs uses, so the poles
    // swing with the legs instead of being hand-matched to them.
    val angleL = if (fighter.isDead || fighter.isDying) 0f else kotlin.math.sin(fighter.animFrame) * 0.45f
    val angleR = if (fighter.isDead || fighter.isDying) 0f else -kotlin.math.sin(fighter.animFrame) * 0.45f

    val topY = stiltTopY(cy)
    val groundY = stiltGroundY(cy, effectiveSize)

    scope.withTransform({ rotate(radToDeg(angleL), pivot = Offset(cx - 10f, cy + 90f)) }) {
        drawLine(wColor, Offset(cx - 18f, topY), Offset(cx - 18f, groundY), strokeWidth = 8f)
        drawLine(Color(0xFF4A4A4A), Offset(cx - 25f, topY + 5f), Offset(cx - 5f, topY + 5f), strokeWidth = 4f)
    }
    scope.withTransform({ rotate(radToDeg(angleR), pivot = Offset(cx + 10f, cy + 90f)) }) {
        drawLine(wColor, Offset(cx + 18f, topY), Offset(cx + 18f, groundY), strokeWidth = 8f)
        drawLine(Color(0xFF4A4A4A), Offset(cx + 10f, topY + 5f), Offset(cx + 35f, topY + 5f), strokeWidth = 4f)
    }
}
```

The rotation pivots are now `cy + 90f` — the hip, matching `drawLegs` (`TapestryRenderer.kt:466`, `:487`) — so the poles hinge exactly where the legs do.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.StiltsGeometryTest" --console=plain`

Expected: PASS

- [ ] **Step 5: Move the call site out of the mount transform**

In `TapestryRenderer.kt`, delete the `isStilts` branch from the mount dispatch at `:157-161`, leaving:

```kotlin
if (fighter.isChariot) {
    drawChariot(this, cx, cy, fighter)
} else if (fighter.isLord) {
    drawThrone(this, cx, cy, fighter, isBattleActive)
} else {
    drawHorse(this, cx, cy, fighter)
}
```

Then guard the whole mount block so a stilt-walker no longer enters it. Change the condition at `:150` from `if (fighter.isMounted || fighter.isChariot)` to:

```kotlin
if ((fighter.isMounted && !fighter.isStilts) || fighter.isChariot) {
```

Now draw the stilts in the body's own space. In the `withTransform({ translate(0f, adjustedMountOffsetY) })` block (`:230`), immediately before `drawLegs` (`:242`):

```kotlin
drawBossSignature(this, cx, cy, fighter)
if (fighter.isStilts) {
    // Poles live in the body's space so they scale and swing with the man who is on them.
    drawStilts(this, cx, cy, fighter, effectiveSize)
}
if (!fighter.isChariot) {
    drawLegs(this, cx, cy, fighter)
}
```

Leave the `isStilts` special case in `drawLegs`' own `if (!fighter.isMounted || fighter.isStilts)` guard (`:463`) exactly as it is — it is what makes the legs visible on stilts at all.

- [ ] **Step 6: Compile and run the full suite**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS. `ArtScreenshotTest` writes `small_stilts` and `normal_stilts` PNGs; it asserts nothing about them.

- [ ] **Step 7: Add a large-size render for human review**

In `ArtScreenshotTest.kt:176-177`, next to the existing entries, add:

```kotlin
rider("large_stilts", 1.4f, stilts = true),
```

- [ ] **Step 8: Regenerate the screenshots and look at them**

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`

Then **open `app/src/test/screenshots/` and look at `small_stilts`, `normal_stilts` and `large_stilts` by eye.** These files assert nothing — a human confirming the boots sit on the footrests is the only visual check that exists. The `StiltsGeometryTest` assertions from Step 4 are what protect the fix in CI.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/game/MountRenderer.kt app/src/main/java/com/example/game/TapestryRenderer.kt app/src/test/java/com/example/game/StiltsGeometryTest.kt app/src/test/java/com/example/game/ArtScreenshotTest.kt
git commit -m "fix: stilts attach to the boots at every body size"
```

---

### Task 4: Bosses cleave through the swarm

**Files:**
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt:613-630` (melee target gathering)
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`

**Interfaces:**
- Consumes: `FighterState.bossType: BossType?` (`SimulationModels.kt:445`), `CombatEngine.meleeSweep` (`:734`)
- Produces: nothing new — this widens an existing list

**Background:** bosses are easy because they face the player plus up to eight followers and only ever swing at one. The machinery for a multi-target swing already exists: `meleeSweep` (`:734`) loops a `targets` list and applies `damageFalloff`, which multiplies down by 0.5 per additional target (`:953`). So the whole fix is one new branch in the `if/else` chain that builds `targets` at `:613`. **Do not add a new damage constant or a new damage path** — the existing falloff already gives secondary targets reduced damage.

The chain's existing branches are all keyed on `attacker.isPlayer`. The boss branch must come first, because a boss is `isPlayer = false` and would otherwise fall to the single-target `else`.

- [ ] **Step 1: Write the failing test**

Add to `CombatEngineTest.kt`:

```kotlin
@Test
fun aBossSwingCleavesEveryAllyInReach() {
    val ctx = FakeContext()
    val engine = CombatEngine(ctx)

    val boss = fighter(head = "head_maul", handle = "handle_iron", posX = 500f)
        .copy(bossType = BossType.HAROLD_GODWINSON)
    val allies = listOf(490f, 515f, 540f).mapIndexed { i, x ->
        fighter(isPlayer = true, posX = x).copy(id = "ally_$i")
    }
    ctx.player = allies.first()
    ctx.enemies = allies + boss

    engine.triggerAttack(boss)
    repeat(120) { engine.tick(1f / 60f) }

    val hurt = allies.count { it.hp < it.maxHp }
    assertEquals("a boss must strike every ally inside its reach", 3, hurt)
}

@Test
fun anOrdinaryFighterStillStrikesOnlyItsTarget() {
    val ctx = FakeContext()
    val engine = CombatEngine(ctx)

    val saxon = fighter(head = "head_maul", handle = "handle_iron", posX = 500f)
    val allies = listOf(490f, 515f, 540f).mapIndexed { i, x ->
        fighter(isPlayer = true, posX = x).copy(id = "ally_$i")
    }
    ctx.player = allies.first()
    ctx.enemies = allies + saxon

    engine.triggerAttack(saxon)
    repeat(120) { engine.tick(1f / 60f) }

    val hurt = allies.count { it.hp < it.maxHp }
    assertTrue("a common Saxon must not cleave — hurt $hurt", hurt <= 1)
}
```

- [ ] **Step 2: Run the tests to verify the first fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest" --console=plain`

Expected: `aBossSwingCleavesEveryAllyInReach` FAILS (1 hurt, expected 3). `anOrdinaryFighterStillStrikesOnlyItsTarget` already PASSES — it is the guard that proves the change stays scoped to bosses.

- [ ] **Step 3: Add the boss branch**

In `CombatEngine.kt`, insert as the **first** branch of the `targets` chain at `:613`, before the existing `if (attacker.isPlayer && isPiercingWeapon)`:

```kotlin
val targets = if (attacker.bossType != null) {
    // A king does not duel one man at a time. Swarming him with the whole retinue was the
    // reason bosses fell so easily — every follower in reach now eats the same swing.
    // Secondary targets are handled by meleeSweep's existing damageFalloff; no new constant.
    ctx.enemies.filter {
        !it.isDead && !it.isDying && !it.isCombatInactive &&
        it.climbState == ClimbState.NONE && it.elevated == attacker.elevated &&
        it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels
    }.sortedBy { abs(attacker.posX - it.posX) }
} else if (attacker.isPlayer && isPiercingWeapon) {
```

The rest of the chain is untouched. Note `ctx.enemies` holds both sides — allies are the entries with `isPlayer = true` — which is why the filter uses `it.isPlayer != attacker.isPlayer` exactly like the existing branches.

Boss *retinue* must not cleave: `isBossRetinue` fighters have `bossType == null`, so they fall through to the ordinary path with no extra guard needed.

- [ ] **Step 4: Run the tests to verify both pass**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest" --console=plain`

Expected: PASS, both.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/game/CombatEngine.kt app/src/test/java/com/example/game/CombatEngineTest.kt
git commit -m "feat: bosses cleave every foe in reach, punishing the swarm"
```

---

### Task 5: Fix the bogus starting-gear ids

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt:277-289`
- Test: `app/src/test/java/com/example/game/GameDataTest.kt` (create)

**Background:** `startNewGame` seeds `initialGear` with `"armor_none"` and `"head_none"` (`:281-282`). Neither id exists — the real ids are `armor_bare` and `helm_none`, which the second copy of this block at `:1991-1992` gets right. Both adds are silent no-ops today, so a new run can roll a loadout with no guaranteed bare-armour or bare-head fallback in the pool.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/GameDataTest.kt`:

```kotlin
package com.example.game

import org.junit.Assert.assertTrue
import org.junit.Test

class GameDataTest {

    private val allGearIds: Set<String> =
        (GameData.WEAPON_HEADS.map { it.id } +
         GameData.WEAPON_HANDLES.map { it.id } +
         GameData.SHIELDS.map { it.id } +
         GameData.ARMOR_PIECES.map { it.id } +
         GameData.HEADGEAR_PIECES.map { it.id }).toSet()

    @Test
    fun everyGuaranteedStartingIdResolvesToRealGear() {
        // These are the ids startNewGame always seeds. A typo here is silent: the add is a no-op
        // and the player simply never gets the fallback item in his pool.
        listOf("head_bare", "handle_fists", "shield_none", "armor_bare", "helm_none").forEach {
            assertTrue("$it is not a real gear id", it in allGearIds)
        }
    }
}
```

- [ ] **Step 2: Run it — it passes, and that is the point**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameDataTest" --console=plain`

Expected: PASS. This test asserts what the *correct* ids are; it is the reference the fix is made against. The defect is that `GameViewModel` does not use these ids.

- [ ] **Step 3: Fix the ids**

At `GameViewModel.kt:281-282`, change:

```kotlin
initialGear.add("armor_none")
initialGear.add("head_none")
```

to:

```kotlin
initialGear.add("armor_bare")
initialGear.add("helm_none")
```

- [ ] **Step 4: Verify the two blocks now agree**

Run: `grep -n 'initialGear.add' app/src/main/java/com/example/game/GameViewModel.kt`

Expected: both blocks (around `:278-282` and `:1988-1992`) list the same five ids — `head_bare`, `handle_fists`, `shield_none`, `armor_bare`, `helm_none`.

- [ ] **Step 5: Run the full suite**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/GameDataTest.kt
git commit -m "fix: startNewGame seeded two gear ids that do not exist"
```

---

### Task 6: Seven new weapon handles — data and gating

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt:171-203` (`WeaponHandle` enum, `WEAPON_HANDLES`), `app/src/main/java/com/example/game/GameViewModel.kt:286` (base roll)
- Test: `app/src/test/java/com/example/game/GameDataTest.kt`

**Interfaces:**
- Produces: `GameData.UNLOCKABLE_HANDLE_IDS: Set<String>`, and seven new `GameData.WeaponHandle` entries

**Background:** `GameData.STRANGE_HEAD_IDS` (`SimulationModels.kt:169`) is the existing precedent for content held out of the normal pools. Mirror it exactly. Batch C wires the unlock triggers; **this task wires no trigger**, so the seven handles are deliberately unreachable in play until then.

- [ ] **Step 1: Write the failing test**

Add to `GameDataTest.kt`:

```kotlin
@Test
fun unlockableHandlesExistAndAreHeldOutOfTheBasePool() {
    assertTrue("gating set must not be empty", GameData.UNLOCKABLE_HANDLE_IDS.isNotEmpty())
    GameData.UNLOCKABLE_HANDLE_IDS.forEach { id ->
        assertTrue("$id is gated but is not a real handle",
            GameData.WEAPON_HANDLES.any { it.id == id })
    }
    val expected = setOf(
        "handle_oar", "handle_femur", "handle_antler", "handle_trumpet",
        "handle_wheelbarrow", "handle_anchor", "handle_plank"
    )
    assertEquals(expected, GameData.UNLOCKABLE_HANDLE_IDS)
}
```

Add `import org.junit.Assert.assertEquals` to the file's imports.

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameDataTest" --console=plain`

Expected: FAIL — `Unresolved reference: UNLOCKABLE_HANDLE_IDS`.

- [ ] **Step 3: Add the handle entries**

In `SimulationModels.kt`, in `enum class WeaponHandle`, the last entry `PIKE_HANDLE` currently ends with `;`. Change its trailing `;` to `,` and append:

```kotlin
        // Unlockable handles — earned by play, never in the opening roll. See UNLOCKABLE_HANDLE_IDS.
        OAR("handle_oar", "Ship's Oar", 2.6f, reach = 1.4f, speedPenalty = 0.3f, blunt = 12f, description = "A broad ashen oar off a Norman longship. Still smells of the Channel.", color = Color(0xFF9C7D58)),
        FEMUR("handle_femur", "Thighbone Grip", 0.3f, reach = 0.1f, speedPenalty = -0.22f, description = "Somebody's thighbone, wrapped in cord. Blindingly quick, and deeply unsporting.", color = Color(0xFFE7DCC4)),
        ANTLER("handle_antler", "Stag Antler", 0.9f, reach = 0.3f, speedPenalty = -0.05f, pierce = 8f, description = "A branching antler off a great hart. The tines catch flesh on the backswing.", color = Color(0xFFBFA278)),
        TRUMPET("handle_trumpet", "Herald's Trumpet", 1.1f, reach = 0.6f, speedPenalty = 0.1f, blunt = 6f, description = "A flared brass horn swung by its bell. Announces each blow with an appalling parp.", color = Color(0xFFD6A420)),
        WHEELBARROW("handle_wheelbarrow", "Wheelbarrow", 6.0f, reach = 1.1f, speedPenalty = 0.7f, blunt = 25f, description = "An entire barrow, gripped by the handles. Momentum does the thinking.", color = Color(0xFF7A654C)),
        ANCHOR("handle_anchor", "Ship's Anchor", 12.0f, reach = 1.3f, speedPenalty = 0.9f, blunt = 45f, description = "A ship's iron anchor. The slowest swing in Christendom and the last one anybody sees.", color = Color(0xFF4C5154)),
        PLANK("handle_plank", "Nail-Studded Plank", 2.2f, reach = 0.7f, speedPenalty = 0.2f, pierce = 10f, description = "A splintered board bristling with rusted nails. The wounds it leaves go bad.", color = Color(0xFF8A5E38));
```

- [ ] **Step 4: Add the gating set**

Directly below `val WEAPON_HANDLES = WeaponHandle.values().toList()` (`SimulationModels.kt:203`):

```kotlin
    /**
     * Handles earned by playing, never in the opening roll — the same treatment STRANGE_HEAD_IDS
     * gives the relic heads. Batch C wires the milestones that grant them; until then they are
     * deliberately unreachable in play.
     */
    val UNLOCKABLE_HANDLE_IDS = setOf(
        "handle_oar", "handle_femur", "handle_antler", "handle_trumpet",
        "handle_wheelbarrow", "handle_anchor", "handle_plank"
    )
```

- [ ] **Step 5: Hold them out of the base roll**

At `GameViewModel.kt:286`, change:

```kotlin
initialGear.addAll(GameData.WEAPON_HANDLES.shuffled().take(2).map { it.id })
```

to:

```kotlin
initialGear.addAll(GameData.WEAPON_HANDLES.filter { it.id !in GameData.UNLOCKABLE_HANDLE_IDS }
    .shuffled().take(2).map { it.id })
```

- [ ] **Step 6: Run the tests**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game/SimulationModels.kt app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/GameDataTest.kt
git commit -m "feat: seven unlockable weapon handles, gated out of the base pool"
```

---

### Task 7: Render the four shaped handles

**Files:**
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt:2032` (grip-offset table), `:2079-2210` (the bespoke handle chain)
- Test: `app/src/test/java/com/example/game/ArtScreenshotTest.kt`

**Background:** `drawWeapon` (`:2019`) has a generic straight-haft path plus bespoke `else if (fighter.weaponHandle.id == ...)` blocks for `handle_wheel` (`:2079`), `handle_stump` (`:2096`), `handle_ram` (`:2123`), `handle_plough` (`:2139`) and `handle_blessed_branch` (`:2160`). Oar, femur and plank are straight and need **no** render code — the generic path already sockets any head onto them. Antler, trumpet, wheelbarrow and anchor each need a block in the same style.

- [ ] **Step 1: Read the existing blocks before writing any**

Read `TapestryRenderer.kt:2079-2210`. Every new block must follow the same conventions: draw with `drawStitchedFill` / `drawStitchedStrap` rather than raw `drawPath` where the existing blocks do, use the handle's own `color` from the enum, and leave the head socket where the generic path expects it so the weapon head still lands correctly.

- [ ] **Step 2: Add the grip offsets**

At `TapestryRenderer.kt:2032`, the `when` returns a grip length per handle. Add the four shaped handles alongside the existing `"handle_medium", "handle_stump", "handle_ram" -> 70f` entry, matching each one's `reach`:

```kotlin
"handle_wheelbarrow", "handle_anchor" -> 70f
"handle_trumpet" -> 55f
"handle_antler" -> 40f
```

- [ ] **Step 3: Add the four render blocks**

Insert into the chain, after the `handle_blessed_branch` block (`:2160`) and before the `handle_fists` branch (`:2208`). Each follows the shape of the blocks above it — a `Path` built from the grip point outward, filled with the handle's colour:

```kotlin
} else if (fighter.weaponHandle.id == "handle_antler") {
    // A forked antler: main beam plus two tines off the outside edge.
    val beam = Path().apply {
        moveTo(0f, 0f)
        quadraticTo(6f, -18f, 2f, -40f)
    }
    drawStitchedStrap(this, Offset(0f, 0f), Offset(2f, -40f), fighter.weaponHandle.color)
    drawPath(beam, fighter.weaponHandle.color, style = Stroke(width = 7f))
    drawLine(fighter.weaponHandle.color, Offset(3f, -18f), Offset(16f, -26f), strokeWidth = 5f)
    drawLine(fighter.weaponHandle.color, Offset(2f, -30f), Offset(14f, -40f), strokeWidth = 5f)
} else if (fighter.weaponHandle.id == "handle_trumpet") {
    // Gripped by the bell, tube pointing back along the arm.
    drawStitchedStrap(this, Offset(0f, 0f), Offset(0f, -46f), fighter.weaponHandle.color)
    val bell = Path().apply {
        moveTo(-14f, -46f)
        lineTo(14f, -46f)
        lineTo(7f, -60f)
        lineTo(-7f, -60f)
        close()
    }
    drawStitchedFill(this, bell, fighter.weaponHandle.color)
} else if (fighter.weaponHandle.id == "handle_wheelbarrow") {
    // Two shafts, a tray, and the wheel out front.
    drawStitchedStrap(this, Offset(-6f, 0f), Offset(-6f, -62f), fighter.weaponHandle.color)
    drawStitchedStrap(this, Offset(8f, 0f), Offset(8f, -62f), fighter.weaponHandle.color)
    val tray = Path().apply {
        moveTo(-14f, -34f)
        lineTo(16f, -34f)
        lineTo(12f, -56f)
        lineTo(-10f, -56f)
        close()
    }
    drawStitchedFill(this, tray, fighter.weaponHandle.color)
    drawCircle(Color(0xFF5D4831), radius = 13f, center = Offset(1f, -68f), style = Stroke(width = 5f))
} else if (fighter.weaponHandle.id == "handle_anchor") {
    // Iron shank with a stock across it and two hooked flukes at the crown.
    drawStitchedStrap(this, Offset(0f, 0f), Offset(0f, -70f), fighter.weaponHandle.color)
    drawLine(fighter.weaponHandle.color, Offset(-20f, -52f), Offset(20f, -52f), strokeWidth = 7f)
    val flukes = Path().apply {
        moveTo(-22f, -70f)
        quadraticTo(0f, -84f, 22f, -70f)
        quadraticTo(14f, -76f, 0f, -76f)
        quadraticTo(-14f, -76f, -22f, -70f)
        close()
    }
    drawStitchedFill(this, flukes, fighter.weaponHandle.color)
```

If `drawCircle` with a `Stroke` style is not already imported in this file, use the existing import set — `androidx.compose.ui.graphics.drawscope.Stroke` is already imported for the other handle blocks.

- [ ] **Step 4: Add render cases so a human can look at them**

In `ArtScreenshotTest.kt`, add a case rendering a fighter for each of the seven new handles paired with `head_axe`, so both the straight and shaped ones can be eyeballed. Follow the existing `rider(...)` helper convention in that file (`:157`).

- [ ] **Step 5: Compile and generate the screenshots**

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`

Expected: BUILD SUCCESSFUL, PNGs written to `app/src/test/screenshots/`.

- [ ] **Step 6: Look at every one of them**

**Open `app/src/test/screenshots/` and inspect all seven handles by eye.** Check specifically that the axe head sits at the top of each haft and does not float away from it or overlap the grip. These files assert nothing — this human pass is the only check on the new art, and because Batch C has not wired the unlocks, these handles cannot be reached in play to be checked any other way.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game/TapestryRenderer.kt app/src/test/java/com/example/game/ArtScreenshotTest.kt
git commit -m "feat: render the antler, trumpet, wheelbarrow and anchor handles"
```

---

### Task 8: The nail-studded plank poisons

**Files:**
- Modify: `app/src/main/java/com/example/game/CombatEngine.kt` (inside `meleeSweep`, after damage is applied around `:797-831`)
- Test: `app/src/test/java/com/example/game/CombatEngineTest.kt`

**Interfaces:**
- Consumes: `FighterState.poisonDuration: Float`, already ticked in `updateFighter` (`CombatEngine.kt:243-268`)

**Background:** poison is a solved problem here — `poisonDuration` counts down in `updateFighter`, spawns green popups and particles, and drains via `applyDotDamage(POISON_DPS, ...)`. The hag already applies it. This task only sets the field.

- [ ] **Step 1: Write the failing test**

Add to `CombatEngineTest.kt`:

```kotlin
@Test
fun theNailStuddedPlankLeavesAWoundThatGoesBad() {
    val ctx = FakeContext()
    val engine = CombatEngine(ctx)

    val attacker = fighter(head = "head_club", handle = "handle_plank", posX = 500f)
    val victim = fighter(isPlayer = true, posX = 520f).copy(id = "victim")
    ctx.player = victim
    ctx.enemies = listOf(victim, attacker)

    engine.triggerAttack(attacker)
    repeat(120) { engine.tick(1f / 60f) }

    assertTrue("the plank must poison on hit", victim.poisonDuration > 0f)
}

@Test
fun anOrdinaryHaftDoesNotPoison() {
    val ctx = FakeContext()
    val engine = CombatEngine(ctx)

    val attacker = fighter(head = "head_club", handle = "handle_medium", posX = 500f)
    val victim = fighter(isPlayer = true, posX = 520f).copy(id = "victim")
    ctx.player = victim
    ctx.enemies = listOf(victim, attacker)

    engine.triggerAttack(attacker)
    repeat(120) { engine.tick(1f / 60f) }

    assertEquals(0f, victim.poisonDuration, 0.001f)
}
```

- [ ] **Step 2: Run to verify the first fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest" --console=plain`

Expected: `theNailStuddedPlankLeavesAWoundThatGoesBad` FAILS (poisonDuration is 0). `anOrdinaryHaftDoesNotPoison` PASSES already.

- [ ] **Step 3: Add the constant**

In `CombatEngine.kt`'s `companion object`, near the other tuning constants:

```kotlin
/** Rusted nails leave a wound that turns. Seconds of poison per plank hit. */
const val PLANK_POISON_SECONDS = 4f
```

- [ ] **Step 4: Set the field on a landed hit**

In `meleeSweep`, immediately after the damage for `currTarget` is applied (after the `blunt`/`slash`/`pierce` block ending around `:799`, inside the per-target loop and after any dodge/evasion `continue`s):

```kotlin
if (attacker.weaponHandle.id == "handle_plank") {
    currTarget.poisonDuration = maxOf(currTarget.poisonDuration, PLANK_POISON_SECONDS)
}
```

`maxOf` rather than `+=` so repeated hits refresh the wound instead of stacking it into an instant kill.

- [ ] **Step 5: Run to verify both pass**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.CombatEngineTest" --console=plain`

Expected: PASS, both.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/game/CombatEngine.kt app/src/test/java/com/example/game/CombatEngineTest.kt
git commit -m "feat: the nail-studded plank poisons on hit"
```

---

### Task 9: Seven new body-armour pieces

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (`ArmorPiece` enum; `scoreMultiplier` at `:592-609`), `app/src/main/java/com/example/game/GameViewModel.kt:288` (base-roll exclusions)
- Test: `app/src/test/java/com/example/game/GameDataTest.kt`

**Background:** defence currently jumps from 22 (`armor_leather`) straight to 50 (`armor_lamellar`). The brigandine fills that hole. The zero-defence outfits are score-multiplier plays in the manner of `armor_jester` and must be added to `scoreMultiplier` or they are strictly worse than being naked.

- [ ] **Step 1: Write the failing test**

Add to `GameDataTest.kt`:

```kotlin
@Test
fun theBrigandineFillsTheGapBetweenLeatherAndLamellar() {
    val leather = GameData.ARMOR_PIECES.first { it.id == "armor_leather" }.defense
    val lamellar = GameData.ARMOR_PIECES.first { it.id == "armor_lamellar" }.defense
    val brigandine = GameData.ARMOR_PIECES.first { it.id == "armor_brigandine" }.defense
    assertTrue("brigandine ($brigandine) must sit between $leather and $lamellar",
        brigandine > leather && brigandine < lamellar)
}

@Test
fun everyZeroDefenceOutfitPaysAScoreMultiplier() {
    listOf("armor_habit", "armor_apron", "armor_frock", "armor_toga").forEach { id ->
        val piece = GameData.ARMOR_PIECES.first { it.id == id }
        assertEquals("$id should offer no protection", 0f, piece.defense, 0.001f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameDataTest" --console=plain`

Expected: FAIL — `NoSuchElementException` on `armor_brigandine`.

- [ ] **Step 3: Add the entries**

In `SimulationModels.kt`, in `enum class ArmorPiece`, change `JESTER_OUTFIT`'s trailing `;` to `,` and append:

```kotlin
        BRIGANDINE("armor_brigandine", "Brigandine", 8.0f, defense = 35f, speedPenalty = 0.14f, description = "Small iron plates riveted between layers of canvas. The compromise every sensible man reaches.", color = Color(0xFF6B5544)),
        BEARSKIN("armor_bearskin", "Bearskin Cloak", 4.0f, defense = 28f, speedPenalty = 0.07f, description = "The hide of a great bear, still bearing its head. Warm, heavy, and alarming.", color = Color(0xFF4A3A2C)),
        SMOCK("armor_smock", "Old Smock", 0.6f, defense = 5f, speedPenalty = 0.0f, description = "A peasant's linen smock, much mended. It has survived more harvests than you have battles.", color = Color(0xFFCFC3A6)),
        HABIT("armor_habit", "Monk's Habit", 0.8f, defense = 0f, speedPenalty = 0.0f, description = "Brown wool and a rope belt. God provides no armour class whatsoever.", color = Color(0xFF5E4B3C)),
        APRON("armor_apron", "Cook's Apron", 0.5f, defense = 0f, speedPenalty = 0.0f, description = "Stained with the grease of a hundred feasts. Offers nothing but a faint smell of onions.", color = Color(0xFFE3DAC4)),
        FROCK("armor_frock", "Maid's Frock", 0.5f, defense = 0f, speedPenalty = 0.0f, description = "A serving-maid's linen frock. Cuts a dash on the field of slaughter.", color = Color(0xFFA8557B)),
        TOGA("armor_toga", "Emperor's Toga", 0.7f, defense = 0f, speedPenalty = 0.0f, description = "Draped Roman cloth, a thousand years out of fashion. Fatal, but classical.", color = Color(0xFFF0E6D2));
```

- [ ] **Step 4: Pay the score multipliers**

In `scoreMultiplier` (`SimulationModels.kt:605`), the existing jester line reads:

```kotlin
if (headgear.id == "helm_jester" || armor.id == "armor_jester" || extraArmors.any { it.id == "armor_jester" }) {
    mult += 20.0f
}
```

Add below it:

```kotlin
// Fighting a battle dressed as a cook, a maid, a monk or a Roman senator earns its own reward.
if (armor.id in setOf("armor_habit", "armor_apron", "armor_frock", "armor_toga")) {
    mult += 6.0f
}
```

- [ ] **Step 5: Set the base-roll exclusions**

At `GameViewModel.kt:288`, the roll currently excludes layers and the jester. Extend the exclusion list so the new layers (added in Task 10) and the comic outfits stay out, while brigandine, bearskin and smock roll normally:

```kotlin
initialGear.addAll(GameData.ARMOR_PIECES.filter {
    it.id !in listOf(
        "armor_gauntlets", "armor_boots", "armor_coif", "armor_jester",
        "armor_greaves", "armor_spaulders", "armor_surcoat",
        "armor_habit", "armor_apron", "armor_frock", "armor_toga"
    )
}.shuffled().take(2).map { it.id })
```

- [ ] **Step 6: Run the tests**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS. `armor_greaves`, `armor_spaulders` and `armor_surcoat` do not exist yet — that is fine, the exclusion list is a set of strings and never resolves them.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game/SimulationModels.kt app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/GameDataTest.kt
git commit -m "feat: seven new body-armour pieces, brigandine filling the defence gap"
```

---

### Task 10: Three new armour layers

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (`ArmorPiece` enum), `app/src/main/java/com/example/game/TapestryRenderer.kt:687` (layer skip-list), `:477-505` (leg draw, for greaves), `:681-710` (torso layer draw, for spaulders and surcoat)
- Test: `app/src/test/java/com/example/game/GameDataTest.kt`

**Background:** layers stack through `extraArmors`. The renderer's layer loop at `:686` skips ids in a hardcoded list at `:687` (`armor_gauntlets`, `armor_boots`, `armor_coif`) because those are drawn by hand elsewhere — greaves belong in that category too. Spaulders and the surcoat are drawn over the torso and can go through the loop.

- [ ] **Step 1: Write the failing test**

Add to `GameDataTest.kt`:

```kotlin
@Test
fun theThreeNewLayersExistAndAreLight() {
    listOf("armor_greaves", "armor_spaulders", "armor_surcoat").forEach { id ->
        val piece = GameData.ARMOR_PIECES.first { it.id == id }
        assertTrue("$id is a layer and must not weigh like a hauberk", piece.mass <= 3.0f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameDataTest" --console=plain`

Expected: FAIL — `NoSuchElementException` on `armor_greaves`.

- [ ] **Step 3: Add the entries**

In `SimulationModels.kt`, in `enum class ArmorPiece`, change `TOGA`'s trailing `;` to `,` and append:

```kotlin
        GREAVES("armor_greaves", "Iron Greaves", 2.2f, defense = 15f, speedPenalty = 0.03f, description = "Shaped iron plates strapped over the shins. Saxon spears aim low.", color = Color(0xFF5D666B)),
        SPAULDERS("armor_spaulders", "Spaulders", 2.4f, defense = 15f, speedPenalty = 0.03f, description = "Overlapping plates capping the shoulders. Turns an overhead axe aside.", color = Color(0xFF6B747A)),
        SURCOAT("armor_surcoat", "Surcoat", 0.6f, defense = 3f, speedPenalty = 0.0f, description = "A cloth surcoat worn over the mail, in your own colours. Barely armour. Entirely the point.", color = Color(0xFFB03131));
```

- [ ] **Step 4: Keep greaves out of the generic layer loop**

At `TapestryRenderer.kt:687`, extend the skip-list — greaves are drawn on the legs, not the torso:

```kotlin
if (extraArmor.id in listOf("armor_gauntlets", "armor_boots", "armor_coif", "armor_greaves")) return@forEachIndexed
```

- [ ] **Step 5: Draw the greaves on both legs**

In `drawLegs`, both legs already read `hasBoots` (`:477`, `:499`). Add a sibling read and a shin plate. For the **left** leg, after the boot fill block:

```kotlin
val hasGreaves = fighter.extraArmors.any { it.id == "armor_greaves" }
if (hasGreaves) {
    val greaveL = Path().apply {
        moveTo(cx - 22f, cy + 108f)
        lineTo(cx - 12f, cy + 108f)
        lineTo(cx - 15f, cy + 145f)
        lineTo(cx - 24f, cy + 145f)
        close()
    }
    drawStitchedFill(this, greaveL, Color(0xFF5D666B))
}
```

For the **right** leg, after its boot fill block:

```kotlin
val hasGreaves = fighter.extraArmors.any { it.id == "armor_greaves" }
if (hasGreaves) {
    val greaveR = Path().apply {
        moveTo(cx + 12f, cy + 108f)
        lineTo(cx + 22f, cy + 108f)
        lineTo(cx + 24f, cy + 145f)
        lineTo(cx + 15f, cy + 145f)
        close()
    }
    drawStitchedFill(this, greaveR, Color(0xFF5D666B))
}
```

- [ ] **Step 6: Draw spaulders and the surcoat over the torso**

In the layer loop's texture `when` at `TapestryRenderer.kt:704`, add cases alongside the existing `"armor_lamellar"` entry:

```kotlin
"armor_spaulders" -> {
    // Caps over each shoulder, drawn as two stitched half-discs.
    drawStitchedFill(this, Path().apply {
        moveTo(cx - 34f, cy + 22f)
        quadraticTo(cx - 24f, cy + 6f, cx - 10f, cy + 22f)
        close()
    }, Color(0xFF6B747A))
    drawStitchedFill(this, Path().apply {
        moveTo(cx + 10f, cy + 22f)
        quadraticTo(cx + 24f, cy + 6f, cx + 34f, cy + 22f)
        close()
    }, Color(0xFF6B747A))
}
"armor_surcoat" -> {
    // Cloth over the mail, in the player's own colour rather than the piece's default.
    val surcoatColor = if (fighter.isPlayer) fighter.hairColor else extraArmor.color
    drawStitchedFill(this, Path().apply {
        moveTo(cx - 26f, cy + 24f)
        lineTo(cx + 26f, cy + 24f)
        lineTo(cx + 20f, cy + 86f)
        lineTo(cx - 20f, cy + 86f)
        close()
    }, surcoatColor)
}
```

- [ ] **Step 7: Add render cases so a human can look at them**

In `ArtScreenshotTest.kt`, add cases rendering a fighter wearing greaves, spaulders and the surcoat (each stacked over `armor_chainmail` via `extraArmors`, which is how they are worn in play).

- [ ] **Step 8: Compile, test and generate screenshots**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`

- [ ] **Step 9: Look at them**

**Open `app/src/test/screenshots/` and check the three layers by eye** — greaves on the shins and swinging with the legs, spaulders capping the shoulders without floating, the surcoat hanging over the torso without hiding the mail entirely. Nothing here asserts; this pass is the check.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/game/SimulationModels.kt app/src/main/java/com/example/game/TapestryRenderer.kt app/src/test/java/com/example/game/GameDataTest.kt app/src/test/java/com/example/game/ArtScreenshotTest.kt
git commit -m "feat: greaves, spaulders and surcoat armour layers"
```

---

### Task 11: Six new sound folders (Batch B)

**Files:**
- Create: `app/src/main/assets/{trojan,bee,herald,fanatic,monk,plague}/README.txt`
- Modify: `app/src/main/java/com/example/game/SoundSynth.kt:76` (folder list), `:151-159` (playback helpers)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt:1417` (Trojan burst trigger)
- Test: `app/src/test/java/com/example/game/SoundFolderTest.kt` (create)

**Interfaces:**
- Produces: `MedievalAudioSynth.playTrojanBurst()`, `playBeeSwarm()`, `playHeraldBoast()`, `playFanaticScream()`, `playMonkChant()`, `playPlagueCough()`
- Consumes: `MedievalAudioSynth.playFolder(folder: String): Boolean` (`SoundSynth.kt:139`, private)

**Background:** the machinery needs no change. `prepare()` (`:63`) enumerates each folder in the list at `:76` and loads any `.wav`/`.ogg`/`.mp3` into the `SoundPool`; `playFolder` (`:139`) returns `false` for an empty folder and callers fall through silently. So all six ship empty and start working the moment recordings are dropped in.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/SoundFolderTest.kt`:

```kotlin
package com.example.game

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catches the classic silent failure: a folder gets renamed or never created, playFolder returns
 * false forever, and the sound simply stops without anything going red.
 */
class SoundFolderTest {

    private val expected = listOf(
        "dog", "hag", "drums", "victory", "pain", "armour", "shield", "flesh",
        "trojan", "bee", "herald", "fanatic", "monk", "plague"
    )

    @Test
    fun everyFolderTheSynthLoadsExistsOnDisk() {
        expected.forEach { folder ->
            val dir = File("src/main/assets/$folder")
            assertTrue("assets/$folder is loaded by SoundSynth but does not exist", dir.isDirectory)
        }
    }
}
```

Note: Robolectric unit tests run with the working directory at the `app` module root, so `src/main/assets/...` is the correct relative path. If the assertion fails on path rather than content, print `File(".").absolutePath` once to confirm and adjust.

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.SoundFolderTest" --console=plain`

Expected: FAIL — `assets/trojan is loaded by SoundSynth but does not exist`.

- [ ] **Step 3: Create the six folders**

Each gets a `README.txt` following the convention of the existing `assets/armour/README.txt`. Read that file first and match its tone and structure. Create:

- `app/src/main/assets/trojan/README.txt` — clips for the belly of the horse bursting open: splintering timber, a war cry from inside.
- `app/src/main/assets/bee/README.txt` — Humble Bede's hive bursting: a rising swarm, distant yelps.
- `app/src/main/assets/herald/README.txt` — Sir Boast-a-lot's bellowed boasts on spawn.
- `app/src/main/assets/fanatic/README.txt` — Mad Boris screaming as he charges.
- `app/src/main/assets/monk/README.txt` — Brother Tuck's plainchant and muttered Latin.
- `app/src/main/assets/plague/README.txt` — Wretched Aldwin's wet coughs and death-rattles.

Each README must state: drop `.wav`, `.ogg` or `.mp3` files in here, any number, one is picked at random per trigger; an empty folder plays nothing and breaks nothing.

Git does not track empty directories, so the README is what makes each folder exist in the repo. This is load-bearing, not decoration.

- [ ] **Step 4: Add the folders to the load list**

At `SoundSynth.kt:76`:

```kotlin
for (folder in listOf(
    "dog", "hag", "drums", "victory", "pain", "armour", "shield", "flesh",
    "trojan", "bee", "herald", "fanatic", "monk", "plague"
)) {
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.SoundFolderTest" --console=plain`

Expected: PASS

- [ ] **Step 6: Add the playback helpers**

In `SoundSynth.kt`, after `playHagCackle` (`:157-159`), following exactly that pattern:

```kotlin
/** The belly of the great horse splitting open. */
fun playTrojanBurst() { if (sfxEnabled) playFolder("trojan") }

/** Humble Bede's hive bursting among the enemy. */
fun playBeeSwarm() { if (sfxEnabled) playFolder("bee") }

/** Sir Boast-a-lot announcing you, at length. */
fun playHeraldBoast() { if (sfxEnabled) playFolder("herald") }

/** Mad Boris, charging. */
fun playFanaticScream() { if (sfxEnabled) playFolder("fanatic") }

/** Brother Tuck at his devotions. */
fun playMonkChant() { if (sfxEnabled) playFolder("monk") }

/** Wretched Aldwin's cough, which is also his weapon. */
fun playPlagueCough() { if (sfxEnabled) playFolder("plague") }
```

- [ ] **Step 7: Wire the Trojan burst trigger**

At `GameViewModel.kt:1417`, the existing line reads `MedievalAudioSynth.playSound(SoundType.CRUNCH)`. Add the folder call beside it — the recording layers over the synth crunch rather than replacing it:

```kotlin
MedievalAudioSynth.playSound(SoundType.CRUNCH)
MedievalAudioSynth.playTrojanBurst()
```

- [ ] **Step 8: Wire the five remaining triggers**

Follow the existing pattern for occasional ally sounds. `playDogBark` is called from the battle tick under a rarity roll (`GameViewModel.kt:1609`); `playHagCackle` from the same area (`:1614`). Add calls in that block, each guarded by the same style of rarity roll and by the relevant ally being alive on the field:

- `playHeraldBoast()` — when Sir Boast-a-lot is in the retinue, on battle start.
- `playFanaticScream()` — when a `fanatic#` fighter is alive, occasional.
- `playMonkChant()` — when the Monk is in the retinue, occasional.
- `playPlagueCough()` — when a `plague_peasant#` fighter is alive, occasional.
- `playBeeSwarm()` — where the beekeeper's hive projectile bursts. Find it by searching `isKind("beekeeper")` in `CombatEngine.kt` and call it at the burst site via the existing `ctx.sound` route or a direct `MedievalAudioSynth` call, matching whatever that code already does for its impact sound.

- [ ] **Step 9: Run the full suite and compile**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add app/src/main/assets app/src/main/java/com/example/game/SoundSynth.kt app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/SoundFolderTest.kt
git commit -m "feat: six new sound folders for the trojan burst and entourage voices"
```

---

### Task 12: Full-build verification

**Files:** none modified.

- [ ] **Step 1: Run the complete unit test suite**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: BUILD SUCCESSFUL, zero failures.

- [ ] **Step 2: Build the debug APK**

Run: `gradle :app:assembleDebug --console=plain`

Expected: BUILD SUCCESSFUL. If it has not returned after ~60s, check `~/.gradle/daemon/9.6.0/daemon-*.out.log` per the Global Constraints rather than waiting.

- [ ] **Step 3: Regenerate all screenshots and review them together**

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --tests "*MagazinePreviewTest*" --console=plain`

**Open `app/src/test/screenshots/` and review every new item in one pass** — seven handles, seven body armours, three layers, three stilt sizes.

- [ ] **Step 4: Report what is and is not verified**

State plainly in the completion report:
- Assertions covering the frontline lead, the panoply, stilt geometry, boss cleave, plank poison, gear data and sound folders — all passing.
- The seven unlockable handles are **unreachable in play** until Batch C wires the milestones, so their only verification is the human screenshot pass.
- `ArtScreenshotTest` and `MagazinePreviewTest` assert nothing about pixels. Do not report them as visual regressions caught or passed.

---

## Self-Review

**Spec coverage.** Every numbered item in the Batch A + B spec maps to a task: frontline speed (1), Buster and Trojan panoply (2), stilts (3), boss cleave (4), handles data and gating (6), handle rendering (7), plank poison (8), body armour (9), armour layers (10), sound folders (11). The spec's incidental `initialGear` id fix is Task 5.

**Two deliberate deviations from the spec, both improvements found while reading the code:**

1. **Boss cleave needs no `BOSS_CLEAVE_FRACTION` constant.** The spec proposed one at 0.6. `meleeSweep` already applies `damageFalloff` (`CombatEngine.kt:743`, `:953`), multiplying secondary targets down by 0.5 per additional target. Task 4 reuses it and adds one branch instead of a new damage path.

2. **The screenshot tests cannot verify the stilts fix.** The spec said to "re-bless goldens". There are no goldens — `ArtScreenshotTest.kt:34-38` states outright that nothing fails on a pixel diff. Task 3 therefore extracts `stiltTopY` / `stiltGroundY` as pure functions and asserts them against the leg position across five body sizes. The screenshots remain a human eyeball pass, correctly labelled as such throughout.

**Placeholder scan.** No TBD/TODO. Every code step carries real code. Task 11 Step 8 and Task 7 Step 4 direct the implementer to locate existing call sites by search rather than quoting line numbers that will have shifted by then — the search terms are exact.

**Type consistency.** `applyRetinuePanoply(fighter: FighterState)` is defined in Task 2 and used only there. `UNLOCKABLE_HANDLE_IDS` is defined in Task 6 and consumed in Task 6 Step 5. `stiltTopY(cy)` / `stiltGroundY(cy, effectiveSize)` are defined in Task 3 Step 3 and consumed in Task 3 Step 1's test with matching signatures. `drawStilts` gains an `effectiveSize` parameter in Task 3 Step 3 and every call site is updated in Step 5. The armour ids used in Task 9 Step 5's exclusion list are defined across Tasks 9 and 10; the list is strings and never resolves them, which is called out at Task 9 Step 6.
