# C1 — Persistence and the Unlock Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Bayeux Brawler a saved profile so gear earned by playing survives between runs, and wire eleven milestones that grant it.

**Architecture:** A single `object GameProfile` wraps one DataStore instance, initialised from `MainActivity` exactly as `VectorAsset.init` and `MedievalAudioSynth.init` already are. The existing `unlockedGearIds` set — which the gear tabs already filter on — becomes the random opening roll **unioned** with the saved set. Milestones are checked at points where the game already knows the run's shape, and each fires once, guarded by a saved `clearedMilestones` set.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences (already in the version catalog), JUnit 4, Robolectric.

## Global Constraints

- **No gradle wrapper exists.** Use the system gradle on PATH (Gradle 9.6.0). Never write `./gradlew`.
- Unit tests: `gradle :app:testDebugUnitTest --console=plain`. Compile check: `gradle :app:compileDebugKotlin --console=plain`.
- **Gradle daemon quirk:** if a command has not returned after ~60s it is not still compiling. Check `~/.gradle/daemon/9.6.0/daemon-*.out.log` for `BUILD SUCCESSFUL` or `e: ` lines, then `gradle --stop` and re-run.
- **Depends on Batch A + B being merged.** The seven handles in `GameData.UNLOCKABLE_HANDLE_IDS` must already exist; this plan makes them reachable.
- `GameViewModel` is a plain `ViewModel()` (`GameViewModel.kt:107`) with no Android context. Do **not** convert it to `AndroidViewModel`. Follow the existing context pattern: a singleton `object` with an `init(context)` called from `MainActivity.onCreate` (see `MainActivity.kt:70` for `VectorAsset.init`).
- DataStore reads and writes are suspending. Never block the game loop on them.
- Every milestone must grant exactly once, ever. A level-threshold check with no `clearedMilestones` guard re-fires on every run.
- Commit after every task.

---

### Task 1: Enable the DataStore dependency

**Files:**
- Modify: `app/build.gradle.kts:16`

**Background:** `androidx-datastore-preferences` is already declared in `gradle/libs.versions.toml` (`:36` for the version, `:87` for the library) and sits commented out in the app module. This is one uncommented line, not a new dependency. Room is also in the catalog — do not use it. The saved profile is a set of strings and three scalars.

- [ ] **Step 1: Uncomment the dependency**

At `app/build.gradle.kts:16`, change:

```kotlin
  // implementation(libs.androidx.datastore.preferences)
```

to:

```kotlin
  implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 2: Verify it resolves**

Run: `gradle :app:compileDebugKotlin --console=plain`

Expected: BUILD SUCCESSFUL. A resolution failure here means the catalog version needs bumping — report it rather than switching to a hardcoded coordinate.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: enable androidx datastore-preferences"
```

---

### Task 2: The profile store

**Files:**
- Create: `app/src/main/java/com/example/game/GameProfile.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt:70` (init call)
- Test: `app/src/test/java/com/example/game/GameProfileTest.kt` (create)

**Interfaces:**
- Produces:
  - `object GameProfile`
  - `fun init(context: Context)`
  - `suspend fun load(): Profile`
  - `suspend fun grant(itemId: String, milestoneId: String)`
  - `suspend fun recordDeath()`
  - `suspend fun setHighscore(score: Int)`
  - `suspend fun setAdFree(purchased: Boolean)` — written here, read only by C3
  - `data class Profile(val unlockedItemIds: Set<String>, val clearedMilestones: Set<String>, val highscore: Int, val totalDeaths: Int, val adFreePurchased: Boolean)`
  - `val cached: Profile` — the last loaded value, for synchronous reads from the game loop

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/GameProfileTest.kt`:

```kotlin
package com.example.game

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameProfileTest {

    @Before
    fun setUp() {
        GameProfile.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun aFreshProfileIsEmptyRatherThanACrash() = runTest {
        val p = GameProfile.load()
        assertTrue(p.unlockedItemIds.isEmpty())
        assertTrue(p.clearedMilestones.isEmpty())
        assertEquals(0, p.highscore)
        assertEquals(0, p.totalDeaths)
        assertFalse(p.adFreePurchased)
    }

    @Test
    fun aGrantedItemSurvivesAReload() = runTest {
        GameProfile.grant("handle_oar", "reach_level_5")
        val p = GameProfile.load()
        assertTrue("handle_oar" in p.unlockedItemIds)
        assertTrue("reach_level_5" in p.clearedMilestones)
    }

    @Test
    fun deathsAccumulate() = runTest {
        GameProfile.recordDeath()
        GameProfile.recordDeath()
        assertEquals(2, GameProfile.load().totalDeaths)
    }

    @Test
    fun theHighscoreOnlyEverClimbs() = runTest {
        GameProfile.setHighscore(500)
        GameProfile.setHighscore(200)
        assertEquals(500, GameProfile.load().highscore)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameProfileTest" --console=plain`

Expected: FAIL — `Unresolved reference: GameProfile`.

- [ ] **Step 3: Write the store**

Create `app/src/main/java/com/example/game/GameProfile.kt`:

```kotlin
package com.example.game

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.profileStore by preferencesDataStore(name = "bayeux_profile")

/**
 * Everything that survives between runs. Deliberately tiny: a couple of string sets and three
 * scalars. Room is in the version catalog and is the wrong tool for this.
 *
 * Initialised from MainActivity like VectorAsset and MedievalAudioSynth, because GameViewModel is
 * a plain ViewModel with no Context.
 */
object GameProfile {

    data class Profile(
        val unlockedItemIds: Set<String> = emptySet(),
        val clearedMilestones: Set<String> = emptySet(),
        val highscore: Int = 0,
        val totalDeaths: Int = 0,
        val adFreePurchased: Boolean = false
    )

    private val KEY_ITEMS = stringSetPreferencesKey("unlocked_item_ids")
    private val KEY_MILESTONES = stringSetPreferencesKey("cleared_milestones")
    private val KEY_HIGHSCORE = intPreferencesKey("highscore")
    private val KEY_DEATHS = intPreferencesKey("total_deaths")
    private val KEY_AD_FREE = booleanPreferencesKey("ad_free_purchased")

    private var appContext: Context? = null

    /** Last loaded profile, so the game loop can read it without suspending. */
    @Volatile
    var cached: Profile = Profile()
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun load(): Profile {
        val ctx = appContext ?: return Profile()
        val prefs = ctx.profileStore.data.first()
        return Profile(
            unlockedItemIds = prefs[KEY_ITEMS].orEmpty(),
            clearedMilestones = prefs[KEY_MILESTONES].orEmpty(),
            highscore = prefs[KEY_HIGHSCORE] ?: 0,
            totalDeaths = prefs[KEY_DEATHS] ?: 0,
            adFreePurchased = prefs[KEY_AD_FREE] ?: false
        ).also { cached = it }
    }

    /** Record one milestone and the item it awards. Both sets are additive; nothing is ever removed. */
    suspend fun grant(itemId: String, milestoneId: String) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { prefs ->
            prefs[KEY_ITEMS] = prefs[KEY_ITEMS].orEmpty() + itemId
            prefs[KEY_MILESTONES] = prefs[KEY_MILESTONES].orEmpty() + milestoneId
        }
        load()
    }

    suspend fun recordDeath() {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it[KEY_DEATHS] = (it[KEY_DEATHS] ?: 0) + 1 }
        load()
    }

    suspend fun setHighscore(score: Int) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it[KEY_HIGHSCORE] = maxOf(it[KEY_HIGHSCORE] ?: 0, score) }
        load()
    }

    suspend fun setAdFree(purchased: Boolean) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it[KEY_AD_FREE] = purchased }
        load()
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameProfileTest" --console=plain`

Expected: PASS, all four.

- [ ] **Step 5: Initialise it at startup**

In `MainActivity.kt`, beside the existing `VectorAsset.init(applicationContext)` at `:70`:

```kotlin
com.example.game.VectorAsset.init(applicationContext) // data-driven building art in assets/art/
com.example.game.GameProfile.init(applicationContext) // unlocks that survive between runs
```

The first `load()` happens in Task 3, on the ViewModel scope.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/game/GameProfile.kt app/src/main/java/com/example/MainActivity.kt app/src/test/java/com/example/game/GameProfileTest.kt
git commit -m "feat: DataStore-backed profile for cross-run unlocks"
```

---

### Task 3: Fold saved unlocks into the run's gear pool

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt:277-296` and `:1987-1995` (both `initialGear` blocks)
- Test: `app/src/test/java/com/example/game/GameProfileTest.kt`

**Background:** `unlockedGearIds` is the pool the gear tabs already filter on (`MainActivity.kt:1484-1487`, `:1542`). This task introduces no new concept — it changes where that set comes from. The random roll **stays**, so a fresh install still gets a varied opening loadout; earned items are simply always present on top.

There are two copies of the `initialGear` block (`:277` and `:1987`). Both must change, or a retry after death silently drops every unlock.

- [ ] **Step 1: Write the failing test**

Add to `GameProfileTest.kt`:

```kotlin
@Test
fun theRunPoolIsTheRandomRollPlusEverythingEarned() = runTest {
    GameProfile.grant("handle_anchor", "defeat_hardrada")
    GameProfile.load()

    val roll = setOf("head_sword", "handle_medium", "shield_kite")
    val pool = GameViewModel.poolWithUnlocks(roll)

    assertTrue("earned items must always be present", "handle_anchor" in pool)
    assertTrue("the random roll must survive", "head_sword" in pool)
}

@Test
fun anEmptyProfileLeavesTheRollUntouched() = runTest {
    val roll = setOf("head_sword", "handle_medium")
    assertEquals(roll, GameViewModel.poolWithUnlocks(roll))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameProfileTest" --console=plain`

Expected: FAIL — `Unresolved reference: poolWithUnlocks`.

- [ ] **Step 3: Add the helper**

In `GameViewModel`'s `companion object` (created in Batch A Task 2; add one if absent):

```kotlin
/**
 * The gear a run may draw on: this run's random roll, plus everything ever earned. Reads the
 * cached profile so it never suspends on the game loop.
 */
fun poolWithUnlocks(roll: Set<String>): Set<String> = roll + GameProfile.cached.unlockedItemIds
```

- [ ] **Step 4: Load the profile when the ViewModel starts**

In `GameViewModel`'s `init` block (add one if absent):

```kotlin
init {
    viewModelScope.launch { GameProfile.load() }
}
```

Add `import androidx.lifecycle.viewModelScope` and `import kotlinx.coroutines.launch` if not already present.

- [ ] **Step 5: Apply it at both `initialGear` sites**

At `GameViewModel.kt:293`, change:

```kotlin
unlockedGearIds = initialGear,
```

to:

```kotlin
unlockedGearIds = poolWithUnlocks(initialGear),
```

Do exactly the same at the second copy, `GameViewModel.kt:2037`. Verify both were changed:

Run: `grep -n 'unlockedGearIds = ' app/src/main/java/com/example/game/GameViewModel.kt`

Expected: both assignments read `poolWithUnlocks(initialGear)`.

- [ ] **Step 6: Run to verify it passes**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/GameProfileTest.kt
git commit -m "feat: earned gear joins the run pool alongside the random roll"
```

---

### Task 4: The milestone table

**Files:**
- Create: `app/src/main/java/com/example/game/Milestone.kt`
- Test: `app/src/test/java/com/example/game/MilestoneTest.kt` (create)

**Interfaces:**
- Produces: `enum class Milestone(val id: String, val label: String, val condition: String, val grants: String)`

**Background:** `condition` is player-facing text shown on locked rows in the Trophies panel. A mystery box the player cannot pursue is not a progression loop, so every locked row must state what to do.

The four Strange Relics (`GameData.STRANGE_HEAD_IDS`, `SimulationModels.kt:169`) already exist and are already drawn. Granting one adds its id to the profile, which puts it in `unlockedGearIds` via Task 3 — making it selectable. The rare relic *attachment* card at `GameViewModel.kt:1810-1821` is a separate mechanism and must not be touched.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/MilestoneTest.kt`:

```kotlin
package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MilestoneTest {

    private val allGearIds: Set<String> =
        (GameData.WEAPON_HEADS.map { it.id } +
         GameData.WEAPON_HANDLES.map { it.id } +
         GameData.SHIELDS.map { it.id } +
         GameData.ARMOR_PIECES.map { it.id } +
         GameData.HEADGEAR_PIECES.map { it.id }).toSet()

    @Test
    fun everyMilestoneGrantsSomethingThatExists() {
        Milestone.values().forEach { m ->
            assertTrue("${m.id} grants ${m.grants}, which is not real gear", m.grants in allGearIds)
        }
    }

    @Test
    fun everyMilestoneTellsThePlayerHowToEarnIt() {
        Milestone.values().forEach { m ->
            assertTrue("${m.id} has no condition text", m.condition.isNotBlank())
        }
    }

    @Test
    fun milestoneIdsAreUnique() {
        val ids = Milestone.values().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun allSevenUnlockableHandlesAreReachable() {
        val granted = Milestone.values().map { it.grants }.toSet()
        GameData.UNLOCKABLE_HANDLE_IDS.forEach {
            assertTrue("$it is gated but no milestone grants it", it in granted)
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MilestoneTest" --console=plain`

Expected: FAIL — `Unresolved reference: Milestone`.

- [ ] **Step 3: Write the table**

Create `app/src/main/java/com/example/game/Milestone.kt`:

```kotlin
package com.example.game

/**
 * Things earned by playing, kept forever. Each fires once — GameProfile.clearedMilestones is what
 * makes that true, since a level-threshold check would otherwise re-fire every single run.
 *
 * [condition] is shown to the player on locked rows. Never leave it vague: a trophy nobody can
 * work out how to earn is not progression, it is a locked door.
 */
enum class Milestone(
    val id: String,
    val label: String,
    val condition: String,
    val grants: String
) {
    REACH_5("reach_level_5", "Off the Beach", "Reach level 5", "handle_oar"),
    FIRST_SIEGE("first_siege", "Breaker of Gates", "Clear your first siege", "handle_plank"),
    BEAT_HAROLD("beat_harold", "The Eye of the King", "Defeat Harold Godwinson", "handle_antler"),
    THREE_SIEGES("three_sieges", "Castellan", "Clear three sieges", "handle_wheelbarrow"),
    BEAT_HARDRADA("beat_hardrada", "Stamford Bridge", "Defeat Harald Hardrada", "handle_anchor"),
    BEAT_GIANT("beat_giant", "Giant-Slayer", "Defeat Gog or Magog", "handle_femur"),
    BEAT_WILLIAM("beat_william", "The Usurper", "Defeat William the Bastard", "handle_trumpet"),

    // The Strange Relics already exist and are already drawn; earning one makes it selectable
    // rather than attachment-only.
    NAKED_WIN("naked_win", "Shameless", "Win a battle wearing no armour", "head_eel"),
    REACH_20("reach_level_20", "Seasoned", "Reach level 20", "head_cheese"),
    FIVE_HUNDRED_KILLS("five_hundred_kills", "The Long Harvest", "Slay 500 men in all", "head_goose"),
    BARE_FISTED_BOSS("bare_fisted_boss", "Bare-Knuckle Saint", "Defeat any boss with your fists", "head_femur")
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MilestoneTest" --console=plain`

Expected: PASS, all four.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/game/Milestone.kt app/src/test/java/com/example/game/MilestoneTest.kt
git commit -m "feat: milestone table for cross-run unlocks"
```

---

### Task 5: Fire the milestones

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` — the post-battle update at `:1690`, and the boss death path
- Test: `app/src/test/java/com/example/game/MilestoneTest.kt`

**Interfaces:**
- Produces: `suspend fun awardMilestone(m: Milestone): Boolean` on `GameViewModel`'s companion — returns true if it fired, false if already cleared

**Background:** the post-battle update at `:1690` already computes `nextLevel`, `state.totalKills`, `state.playerHp` and whether the battle was won. Every level-threshold and kill-count milestone can be checked there from state that already exists.

- [ ] **Step 1: Write the failing test**

Add to `MilestoneTest.kt`. It needs the Robolectric runner and the profile, so add these imports and annotation:

```kotlin
// add at the top of the file
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse
import org.robolectric.RobolectricTestRunner
```

Split the profile-backed tests into their own class in the same file:

```kotlin
@RunWith(RobolectricTestRunner::class)
class MilestoneAwardTest {

    @Before
    fun setUp() {
        GameProfile.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun aMilestoneFiresOnceAndOnlyOnce() = runTest {
        val first = GameViewModel.awardMilestone(Milestone.REACH_5)
        val second = GameViewModel.awardMilestone(Milestone.REACH_5)
        assertTrue("first award should fire", first)
        assertFalse("second award must not fire again", second)
    }

    @Test
    fun anAwardedMilestonePutsItsItemInThePool() = runTest {
        GameViewModel.awardMilestone(Milestone.BEAT_HARDRADA)
        assertTrue("handle_anchor" in GameViewModel.poolWithUnlocks(emptySet()))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MilestoneAwardTest" --console=plain`

Expected: FAIL — `Unresolved reference: awardMilestone`.

- [ ] **Step 3: Write the award function**

In `GameViewModel`'s `companion object`:

```kotlin
/**
 * Grant a milestone if it has not been granted before. Returns true only on the first award,
 * so the caller knows whether to shout about it.
 */
suspend fun awardMilestone(m: Milestone): Boolean {
    if (m.id in GameProfile.cached.clearedMilestones) return false
    GameProfile.grant(m.grants, m.id)
    return true
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MilestoneAwardTest" --console=plain`

Expected: PASS, both.

- [ ] **Step 5: Check milestones after a won battle**

In `GameViewModel`, add an instance helper that fires the checks off the game thread and announces any award:

```kotlin
/**
 * Check every milestone the run's current state could have satisfied. Cheap — eleven set
 * lookups — and only runs between battles, so there is no reason to be clever about it.
 */
private fun checkMilestones(won: Boolean, level: Int, kills: Int, woreNoArmour: Boolean, bossBeaten: BossType?, usedFistsOnly: Boolean, siegesCleared: Int) {
    if (!won) return
    val earned = buildList {
        if (level >= 5) add(Milestone.REACH_5)
        if (level >= 20) add(Milestone.REACH_20)
        if (kills >= 500) add(Milestone.FIVE_HUNDRED_KILLS)
        if (woreNoArmour) add(Milestone.NAKED_WIN)
        if (siegesCleared >= 1) add(Milestone.FIRST_SIEGE)
        if (siegesCleared >= 3) add(Milestone.THREE_SIEGES)
        when (bossBeaten) {
            BossType.HAROLD_GODWINSON -> add(Milestone.BEAT_HAROLD)
            BossType.HARALD_HARDRADA -> add(Milestone.BEAT_HARDRADA)
            BossType.WILLIAM_THE_BASTARD -> add(Milestone.BEAT_WILLIAM)
            BossType.GOG, BossType.MAGOG -> add(Milestone.BEAT_GIANT)
            null -> {}
        }
        if (bossBeaten != null && usedFistsOnly) add(Milestone.BARE_FISTED_BOSS)
    }
    if (earned.isEmpty()) return
    viewModelScope.launch {
        earned.forEach { m ->
            if (awardMilestone(m)) {
                addPopup("UNLOCKED: ${m.label}", 400f, 200f, androidx.compose.ui.graphics.Color(0xFFB08221))
            }
        }
    }
}
```

- [ ] **Step 6: Call it from the post-battle update**

In the `_uiState.update { state -> ... }` block at `GameViewModel.kt:1690`, inside the `if (won)` branch, after `newPerf` is computed. The arguments come from state that block already has in scope:

- `level` — the existing `nextLevel`
- `kills` — `state.totalKills`
- `woreNoArmour` — `state.armor.id == "armor_bare" && state.extraArmors.isEmpty()`
- `bossBeaten` — the `bossType` of the battle just finished; if the update block does not have it in scope, store it in a private field on the ViewModel when the battle is generated (`GameViewModel.kt:639` sets `bossType` already) and read it here
- `usedFistsOnly` — `state.weaponHead.id == "head_bare" && state.weaponHandle.id == "handle_fists"`
- `siegesCleared` — count of sieges completed this run; if no such counter exists, add a private `var siegesClearedThisRun = 0` incremented where the siege win is handled

Do not call `checkMilestones` from inside the `_uiState.update` lambda itself — that lambda can be re-executed. Call it immediately **after** the `update` block returns.

- [ ] **Step 7: Record the death count and highscore**

Where a run ends in defeat, add:

```kotlin
viewModelScope.launch { GameProfile.recordDeath() }
```

And where `newHighscore` is computed (`GameViewModel.kt:1694`):

```kotlin
viewModelScope.launch { GameProfile.setHighscore(newHighscore) }
```

`recordDeath` is what C3's ad cadence reads. `setHighscore` makes the highscore survive — it is currently reset to 0 on every new run (`GameViewModel.kt:292`).

- [ ] **Step 8: Run the full suite**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/game/GameViewModel.kt app/src/test/java/com/example/game/MilestoneTest.kt
git commit -m "feat: fire unlock milestones after a won battle"
```

---

### Task 6: The Trophies panel

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt` — the start screen inside `MainBayeuxGameScreen` (`:286`)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` — expose the cleared set to the UI
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` — add a field to the UI state data class (near `unlockedGearIds`, `:741`)

**Background:** without somewhere to see them, unlocks are invisible and the loop does not close. Locked rows must state the condition.

- [ ] **Step 1: Expose the cleared set in UI state**

In the UI state data class in `SimulationModels.kt`, beside `unlockedGearIds` (`:741`):

```kotlin
val clearedMilestones: Set<String> = emptySet(),
```

In `GameViewModel`'s `init`, after the profile loads, push it into state:

```kotlin
init {
    viewModelScope.launch {
        val p = GameProfile.load()
        _uiState.update { it.copy(clearedMilestones = p.clearedMilestones) }
    }
}
```

Also refresh it in `checkMilestones` after an award fires, so a newly-earned trophy appears without a restart:

```kotlin
_uiState.update { it.copy(clearedMilestones = GameProfile.cached.clearedMilestones) }
```

- [ ] **Step 2: Build the panel**

Add a composable to `MainActivity.kt`, following the styling of the existing gear tabs (`:1484`) — same colours from `com.example.ui.theme`, same row idiom:

```kotlin
@Composable
fun TrophiesPanel(uiState: GameUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            "TROPHIES",
            style = MaterialTheme.typography.titleMedium,
            color = TapestryDark
        )
        Milestone.values().forEach { m ->
            val earned = m.id in uiState.clearedMilestones
            val itemName = GameData.WEAPON_HEADS.find { it.id == m.grants }?.itemName
                ?: GameData.WEAPON_HANDLES.find { it.id == m.grants }?.itemName
                ?: GameData.ARMOR_PIECES.find { it.id == m.grants }?.itemName
                ?: GameData.HEADGEAR_PIECES.find { it.id == m.grants }?.itemName
                ?: m.grants
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(if (earned) "✦" else "·", modifier = Modifier.width(24.dp))
                Column {
                    Text(
                        m.label,
                        color = if (earned) TapestryDark else TapestryDark.copy(alpha = 0.45f)
                    )
                    // A locked trophy must always say how to earn it.
                    Text(
                        if (earned) itemName else m.condition,
                        style = MaterialTheme.typography.bodySmall,
                        color = TapestryDark.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
```

Replace `GameUiState` with whatever the real UI state class is named in `SimulationModels.kt`, and `TapestryDark` with the palette name actually exported by `com.example.ui.theme.Color` — read both before writing, do not guess.

- [ ] **Step 3: Put it on the start screen**

Add `TrophiesPanel(uiState)` to the start screen inside `MainBayeuxGameScreen` (`MainActivity.kt:286`), behind a toggle button in the same idiom as the existing gear tabs so it does not crowd the first screen a player sees.

- [ ] **Step 4: Compile and run**

Run: `gradle :app:testDebugUnitTest --console=plain` then `gradle :app:assembleDebug --console=plain`

Expected: BUILD SUCCESSFUL both times.

- [ ] **Step 5: Verify the loop by hand on a device or emulator**

Install the debug APK. Confirm, in order:
1. On a fresh install every trophy row is locked and each shows its condition text.
2. Reach level 5. The "Off the Beach" popup fires and the row flips to earned, naming the Ship's Oar.
3. Kill the app entirely and reopen it. The trophy is still earned.
4. Start a new run and open the weapon-handle tab. The Ship's Oar is selectable.

Step 3 is the one that matters — it is the only proof persistence actually works end to end.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/MainActivity.kt app/src/main/java/com/example/game/GameViewModel.kt app/src/main/java/com/example/game/SimulationModels.kt
git commit -m "feat: trophies panel showing earned and locked milestones"
```

---

### Task 7: Correct the stale architecture doc

**Files:**
- Modify: `ARCHITECTURE.md`

**Background:** `ARCHITECTURE.md` claims Room ("Persistence | Room (via KSP) | Gear unlocks, high scores") and a Firebase AI backend. Neither exists in the source. Now that real persistence has landed, correct it rather than leaving a second wrong claim on top.

- [ ] **Step 1: Verify what is actually true**

Run: `grep -rn "Room\|Firebase\|firebase" app/src/main/java app/build.gradle.kts`

Expected: no Room usage; confirm whether any Firebase dependency is actually active in `app/build.gradle.kts` (the catalog entries are commented out).

- [ ] **Step 2: Fix the table**

In `ARCHITECTURE.md`'s Tech Stack table, change the Persistence row to DataStore Preferences with a note that it stores unlocks, milestones, highscore and death count. Remove or correct the Backend row to match what Step 1 actually found. Do not add claims that are not in the source.

- [ ] **Step 3: Add GameProfile and Milestone to the file map**

The File Map section lists files with responsibilities. Add `GameProfile.kt` and `Milestone.kt`.

- [ ] **Step 4: Commit**

```bash
git add ARCHITECTURE.md
git commit -m "docs: correct the persistence claim now that DataStore is real"
```

---

## Self-Review

**Spec coverage.** C1 of the Batch C spec maps as: the profile store (Task 2), the `unlockedGearIds` union seam (Task 3), the milestone table and its unlock list (Task 4), milestone firing at level/boss/siege/feat points (Task 5), the Trophies surfacing requirement (Task 6). The spec's "Also in this batch" architecture-doc correction is Task 7. The dependency enable is Task 1.

**Placeholder scan.** No TBD/TODO. Task 5 Step 6 and Task 6 Step 2 direct the implementer to read the real state-class and palette names rather than guessing — the specific unknowns are named, not hand-waved, because those identifiers could not be verified without opening files this plan does not otherwise touch.

**Type consistency.** `GameProfile.Profile` fields are defined in Task 2 Step 3 and read in Tasks 3, 5 and 6 under the same names. `poolWithUnlocks(roll: Set<String>): Set<String>` is defined in Task 3 Step 3, tested in Task 3 Step 1 and reused in Task 5 Step 1's test. `awardMilestone(m: Milestone): Boolean` is defined in Task 5 Step 3 and used in Step 5. `Milestone.grants` / `.label` / `.condition` / `.id` are defined in Task 4 Step 3 and consumed in Tasks 5 and 6 under those exact names. `GameProfile.cached` is defined in Task 2 and read synchronously in Tasks 3 and 5.

**One gap deliberately left to the implementer:** Task 5 Step 6 needs a `siegesClearedThisRun` counter and access to the finished battle's `bossType`. Both may already exist under other names; the step says to search first and add only if absent, because inventing a duplicate counter would be worse than reusing the real one.
