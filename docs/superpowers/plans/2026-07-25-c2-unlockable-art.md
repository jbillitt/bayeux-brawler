# C2 — New Unlockable Art Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three special mounts, four hats and four hairstyles, each earned by a milestone, to give the unlock loop built in C1 something to keep giving.

**Architecture:** Mounts are new `Ancillary` entries with `anc_mount_` ids plus draw functions beside the existing horse, chariot and stilts in `MountRenderer.kt`. Hats are `HeadgearPiece` entries plus cases in the helm draw switch. Hairstyles are new branches in the hair switch, the start-screen picker and the surname generator. Every item is granted by a `Milestone` row added to the table from C1.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, JUnit 4, Robolectric, Roborazzi.

## Global Constraints

- **No gradle wrapper exists.** Use the system gradle on PATH (Gradle 9.6.0). Never write `./gradlew`.
- Unit tests: `gradle :app:testDebugUnitTest --console=plain`. Screenshots: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`.
- **Gradle daemon quirk:** if a command has not returned after ~60s, check `~/.gradle/daemon/9.6.0/daemon-*.out.log` for `BUILD SUCCESSFUL` or `e: ` lines, then `gradle --stop` and re-run. Do not wait.
- **Depends on C1 being merged.** `Milestone`, `GameProfile` and `GameViewModel.awardMilestone` must already exist.
- **`ArtScreenshotTest` and `MagazinePreviewTest` assert nothing about pixels** (`ArtScreenshotTest.kt:34-38`). They write PNGs to `app/src/test/screenshots/` for a human to look at. Never report them as a passed or failed visual check.
- Mount ids must start with `anc_mount_` or `MOUNT_ANCILLARY_IDS` (`SimulationModels.kt:73`) will not recognise them. Mounts must also be added to `NON_PARADE_ANCILLARIES` (`:81`) or they will spawn a grey twin in the parade line behind the player — this is the exact bug the comment there warns about.
- Every new `Ancillary` and gear entry needs a `description` and a `color`; both are required constructor params.
- Commit after every task.

---

### Task 1: Extend the milestone table

**Files:**
- Modify: `app/src/main/java/com/example/game/Milestone.kt`
- Test: `app/src/test/java/com/example/game/MilestoneTest.kt`

**Background:** C1's `MilestoneTest.everyMilestoneGrantsSomethingThatExists` asserts every `grants` id resolves to real gear. Adding milestones before the gear exists will fail that test — which is exactly the ordering signal wanted. This task adds the rows; Tasks 2-4 add the gear that satisfies them.

Mounts are `Ancillary` entries, not `GearItem`s, so the existing test's gear lookup will not find them. The test needs widening first.

- [ ] **Step 1: Widen the existence test to cover ancillaries**

In `MilestoneTest.kt`, replace `everyMilestoneGrantsSomethingThatExists` with:

```kotlin
@Test
fun everyMilestoneGrantsSomethingThatExists() {
    val ancillaryIds = Ancillary.values().map { it.id }.toSet()
    val hairStyles = setOf("hair_tonsure_norman", "hair_braids", "hair_tonsure_monk", "hair_topknot")
    Milestone.values().forEach { m ->
        val known = m.grants in allGearIds || m.grants in ancillaryIds || m.grants in hairStyles
        assertTrue("${m.id} grants ${m.grants}, which is not real content", known)
    }
}
```

- [ ] **Step 2: Add the eleven new milestone rows**

In `Milestone.kt`, change `BARE_FISTED_BOSS`'s trailing `)` to `),` and append:

```kotlin
    // C2 — mounts
    FIVE_SIEGES("five_sieges", "Master of Siegecraft", "Clear five sieges", "anc_mount_ox"),
    REACH_15("reach_level_15", "Baggage Train", "Reach level 15", "anc_mount_mule"),
    BOTH_GIANTS("both_giants", "Albion Broken", "Defeat both Gog and Magog", "anc_mount_bear"),

    // C2 — hats
    REACH_25("reach_level_25", "Antlered Lord", "Reach level 25", "helm_antlered"),
    WILLIAM_TWICE("william_twice", "Twice a Traitor", "Defeat William the Bastard twice", "helm_winged"),
    DOUBLE_BEST("double_best", "Beyond Reckoning", "Double your own best score", "helm_wolf"),
    DIE_TWENTY_FIVE("die_twenty_five", "Well Acquainted with Death", "Fall in battle 25 times", "helm_pot"),

    // C2 — hairstyles
    REACH_10("reach_level_10", "A Norman Cut", "Reach level 10", "hair_tonsure_norman"),
    HARDRADA_BRAIDS("hardrada_braids", "Northern Ways", "Defeat Harald Hardrada", "hair_braids"),
    MONK_SURVIVES("monk_survives", "Brother in Arms", "Win a battle with Brother Tuck still alive", "hair_tonsure_monk"),
    REACH_35("reach_level_35", "Old Campaigner", "Reach level 35", "hair_topknot")
```

Note `HARDRADA_BRAIDS` and the C1 `BEAT_HARDRADA` share a trigger condition but are separate rows granting separate items — `milestoneIdsAreUnique` still holds because the ids differ.

- [ ] **Step 3: Run the tests and expect failure**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MilestoneTest" --console=plain`

Expected: FAIL — none of the eleven granted ids exist yet. This failure is the checklist for Tasks 2-4.

- [ ] **Step 4: Commit the rows**

```bash
git add app/src/main/java/com/example/game/Milestone.kt app/src/test/java/com/example/game/MilestoneTest.kt
git commit -m "feat: milestone rows for the C2 mounts, hats and hairstyles"
```

Committing a red test deliberately: the next three tasks turn it green, and each can be reviewed against it.

---

### Task 2: Three special mounts

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (`Ancillary` enum `:49-70`, `MOUNT_ANCILLARY_IDS` `:73`, `NON_PARADE_ANCILLARIES` `:81`)
- Modify: `app/src/main/java/com/example/game/MountRenderer.kt` (new draw functions)
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt:150-161` (mount dispatch), `:228` (mount offset), `app/src/main/java/com/example/game/GameViewModel.kt:609-612` (mount stat wiring), `app/src/main/java/com/example/MainActivity.kt:923-929` (preview)
- Test: `app/src/test/java/com/example/game/MountTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/MountTest.kt`:

```kotlin
package com.example.game

import org.junit.Assert.assertTrue
import org.junit.Test

class MountTest {

    private val newMounts = listOf("anc_mount_ox", "anc_mount_mule", "anc_mount_bear")

    @Test
    fun everyNewMountIsRecognisedAsAMount() {
        newMounts.forEach { assertTrue("$it missing from MOUNT_ANCILLARY_IDS", it in MOUNT_ANCILLARY_IDS) }
    }

    @Test
    fun noMountMarchesInTheParadeLine() {
        // A mount left out of NON_PARADE_ANCILLARIES spawns a grey twin trotting behind the player.
        newMounts.forEach { id ->
            val anc = Ancillary.values().first { it.id == id }
            assertTrue("$id would draw a parade twin", anc in NON_PARADE_ANCILLARIES)
        }
    }

    @Test
    fun theMuleTradesStatsForScore() {
        val mule = Ancillary.values().first { it.id == "anc_mount_mule" }
        assertTrue("the mule should be a poor ride", mule.speedBoost < 0f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MountTest" --console=plain`

Expected: FAIL — `NoSuchElementException` on `anc_mount_ox`.

- [ ] **Step 3: Add the ancillary entries**

In `SimulationModels.kt`, in `enum class Ancillary`, change `BEEKEEPER`'s trailing `)` to `),` and append:

```kotlin
    WAR_OX("anc_mount_ox", "Bregu", "War Ox", "A plough ox in barding. Immensely strong, immensely slow, and entirely unbothered by arrows.", hpBoost = 160f, speedBoost = -0.25f, color = Color(0xFF6B5B4A)),
    PACK_MULE("anc_mount_mule", "Chestnut", "Pack Mule", "A baggage mule, protesting. A ridiculous mount for a conqueror, and the chroniclers will say so.", hpBoost = 20f, speedBoost = -0.35f, color = Color(0xFF8A7156)),
    WAR_BEAR("anc_mount_bear", "Grimm", "Muzzled Bear", "A great muzzled bear, ridden. It is fast, it is furious, and it does not always mind whose side it is on.", hpBoost = 110f, speedBoost = 0.7f, color = Color(0xFF3A2E24))
```

- [ ] **Step 4: Register them as mounts and keep them out of the parade**

At `SimulationModels.kt:73`:

```kotlin
val MOUNT_ANCILLARY_IDS = setOf(
    "anc_mount_horse", "anc_mount_chariot", "anc_mount_stilts",
    "anc_mount_ox", "anc_mount_mule", "anc_mount_bear"
)
```

At `SimulationModels.kt:81`, add to `NON_PARADE_ANCILLARIES`:

```kotlin
    Ancillary.WAR_OX, Ancillary.PACK_MULE, Ancillary.WAR_BEAR,
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MountTest" --console=plain`

Expected: PASS, all three.

- [ ] **Step 6: Draw them**

Read `drawHorse` in `MountRenderer.kt` first and follow its structure exactly — walk-cycle leg animation driven by `fighter.animFrame`, `drawStitchedFill` for bodies, the same ground line. Add three functions:

```kotlin
/** A plough ox: heavy barrel body, short legs, low head, horns. */
internal fun drawWarOx(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) { /* body per drawHorse */ }

/** A baggage mule: smaller than a horse, long ears, panniers. */
internal fun drawPackMule(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) { /* body per drawHorse */ }

/** A muzzled bear: broad shoulders, heavy forelimbs, a strapped muzzle. */
internal fun drawWarBear(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) { /* body per drawHorse */ }
```

Each body must be built from the same primitives `drawHorse` uses. Do not invent a new drawing idiom — the whole renderer is one hand-stitched embroidery style and a mount drawn in plain strokes will stand out badly.

- [ ] **Step 7: Dispatch to them**

In `TapestryRenderer.kt`'s mount block (`:150-161`), extend the chain. `FighterState` needs a way to say which mount it is riding — check whether a field already carries this (`isChariot`, `isStilts`, `isLord` are separate booleans). Follow that existing pattern and add `isOx`, `isMule`, `isBear` booleans rather than introducing a new mechanism mid-file:

```kotlin
if (fighter.isChariot) {
    drawChariot(this, cx, cy, fighter)
} else if (fighter.isLord) {
    drawThrone(this, cx, cy, fighter, isBattleActive)
} else if (fighter.isOx) {
    drawWarOx(this, cx, cy, fighter)
} else if (fighter.isMule) {
    drawPackMule(this, cx, cy, fighter)
} else if (fighter.isBear) {
    drawWarBear(this, cx, cy, fighter)
} else {
    drawHorse(this, cx, cy, fighter)
}
```

Add the three booleans to `FighterState` beside `isChariot` / `isStilts`, and set them in `GameViewModel.kt:609-612` where `isMounted`, `mountHp`, `isChariot` and `isStilts` are already derived from `currentMount`. Give each a `mountHp` in the same expression — ox 140, mule 40, bear 100.

Set the rider's vertical offset at `TapestryRenderer.kt:228` — the ox and bear are broader and taller than a horse, the mule shorter. Add cases to that `if` chain alongside the existing `isChariot` / `isLord` / `isStilts` ones.

- [ ] **Step 8: Add them to the mount preview**

`MainActivity.kt:923-929` previews the selected mount. Extend both the `isStilts`-style flags and the height `when` at `:929` to cover the three new mounts.

- [ ] **Step 9: Render and look at them**

Add the three mounts to `ArtScreenshotTest.kt`'s render list, then:

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`

**Open `app/src/test/screenshots/` and check each mount by eye** — the rider seated on the back rather than floating above or sunk into it, legs meeting the ground line, the walk cycle reading as a walk. Nothing here asserts.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/game app/src/main/java/com/example/MainActivity.kt app/src/test/java/com/example/game/MountTest.kt app/src/test/java/com/example/game/ArtScreenshotTest.kt
git commit -m "feat: war ox, pack mule and muzzled bear mounts"
```

---

### Task 3: Four unlockable hats

**Files:**
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt` (`HeadgearPiece` enum), `app/src/main/java/com/example/game/GameViewModel.kt:289` (base-roll exclusion)
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt` (`drawHead` helm switch, around `:1340`)
- Test: `app/src/test/java/com/example/game/GameDataTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `GameDataTest.kt`:

```kotlin
@Test
fun theFourUnlockableHatsExist() {
    listOf("helm_antlered", "helm_winged", "helm_wolf", "helm_pot").forEach { id ->
        assertTrue("$id is granted by a milestone but does not exist",
            GameData.HEADGEAR_PIECES.any { it.id == id })
    }
}

@Test
fun theCookingPotIsAJokeNotArmour() {
    val pot = GameData.HEADGEAR_PIECES.first { it.id == "helm_pot" }
    assertTrue("a cooking pot should protect poorly", pot.defense < 15f)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.GameDataTest" --console=plain`

Expected: FAIL — `helm_antlered` does not exist.

- [ ] **Step 3: Add the entries**

In `SimulationModels.kt`, in `enum class HeadgearPiece`, change the last entry's trailing `;` to `,` and append. Read the existing entries first to match the constructor argument order exactly — it differs from `ArmorPiece`:

```kotlin
        ANTLERED("helm_antlered", "Antlered Helm", /* mass, defense, speedPenalty per the existing entries */ description = "An iron helm crowned with the antlers of a great hart. Doorways become a problem.", color = Color(0xFF7A868C)),
        WINGED("helm_winged", "Winged Helm", description = "Two iron wings sweeping back from the temples. Utterly impractical and utterly magnificent.", color = Color(0xFF8C969E)),
        WOLF_COWL("helm_wolf", "Wolf-Head Cowl", description = "The head and pelt of a wolf worn as a hood, in the old northern manner.", color = Color(0xFF5A5048)),
        COOKING_POT("helm_pot", "Cooking Pot", description = "A cauldron jammed over your head. It rings like a bell every time somebody hits it, which is often.", color = Color(0xFF4A4E51))
```

Fill in `mass`, `defense` and `speedPenalty` for each following the pattern of neighbouring entries: antlered and winged around a spangenhelm's protection, the wolf cowl light and weak, the pot heavy and poor.

- [ ] **Step 4: Hold them out of the base roll**

At `GameViewModel.kt:289`, the roll currently excludes only `helm_jester`:

```kotlin
initialGear.addAll(GameData.HEADGEAR_PIECES.filter {
    it.id !in listOf("helm_jester", "helm_antlered", "helm_winged", "helm_wolf", "helm_pot")
}.shuffled().take(2).map { it.id })
```

- [ ] **Step 5: Draw them**

Add four cases to the helm switch in `drawHead` (`TapestryRenderer.kt`, near `:1340`). Read the existing `helm_spangen` and `helm_great` cases first and match their construction. The antlered and winged helms build on the conical silhouette with extra strokes; the wolf cowl is a hood shape with ears; the pot is a plain cylinder with a handle.

- [ ] **Step 6: Run tests, render and look**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

Add the four hats to `ArtScreenshotTest.kt`, then:

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`

**Open `app/src/test/screenshots/` and check each hat sits on the head** at the sizes rendered — not floating above the skull or clipping through the face.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game app/src/test/java/com/example/game
git commit -m "feat: antlered, winged, wolf-cowl and cooking-pot headgear"
```

---

### Task 4: Four new hairstyles

**Files:**
- Modify: `app/src/main/java/com/example/game/TapestryRenderer.kt:1244-1252` (hair switch)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt:302` and `:2036` (random style lists), `:475-478` (surname generator)
- Modify: `app/src/main/java/com/example/MainActivity.kt:1446` (style picker)
- Modify: `app/src/main/java/com/example/game/EnemyFactory.kt:374` — **leave enemy styles alone**, see below
- Test: `app/src/test/java/com/example/game/HairstyleTest.kt` (create)

**Background:** hair is a bare string on `FighterState`, with exactly three values today — `short`, `long`, `bald` — read in `TapestryRenderer.kt:1244-1252`, rolled in two places in `GameViewModel`, listed in the start-screen picker, and branched on by the surname generator (`GameViewModel.kt:475`). The `EnemyFactory` list at `:374` is for Saxons and must **not** gain the unlockable styles — these are the player's earned cuts.

The milestone `grants` ids for hairstyles (`hair_tonsure_norman` etc.) are not gear ids. They are stored in the profile's `unlockedItemIds` like anything else, and the picker filters on them.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/game/HairstyleTest.kt`:

```kotlin
package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HairstyleTest {

    @Test
    fun theBaseStylesAreAlwaysAvailable() {
        listOf("short", "long", "bald").forEach {
            assertTrue("$it must never be locked", it in HairStyles.BASE)
        }
    }

    @Test
    fun everyUnlockableStyleIsGrantedBySomeMilestone() {
        val granted = Milestone.values().map { it.grants }.toSet()
        HairStyles.UNLOCKABLE.forEach { style ->
            assertTrue("$style is unlockable but no milestone grants it", style in granted)
        }
    }

    @Test
    fun theStyleIdsAreDistinctFromTheBaseOnes() {
        assertEquals(emptySet<String>(), HairStyles.BASE.intersect(HairStyles.UNLOCKABLE))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.HairstyleTest" --console=plain`

Expected: FAIL — `Unresolved reference: HairStyles`.

- [ ] **Step 3: Add the style registry**

In `SimulationModels.kt`, near `HAIR_COLORS` (`:619`):

```kotlin
/**
 * Hair is a bare string on FighterState. Kept in one place so the renderer, the picker, the
 * random roll and the surname generator cannot drift apart — they did not have a shared list
 * before, and adding a style meant finding four separate places by hand.
 */
object HairStyles {
    val BASE = setOf("short", "long", "bald")
    val UNLOCKABLE = setOf("hair_tonsure_norman", "hair_braids", "hair_tonsure_monk", "hair_topknot")
    val ALL = BASE + UNLOCKABLE
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.HairstyleTest" --console=plain`

Expected: PASS, all three.

- [ ] **Step 5: Draw the four styles**

In `TapestryRenderer.kt:1244-1252`, the switch currently reads `if (hairStyle == "long") … else if (hairStyle == "bald") … else` (short). Add branches before the fallback:

- `hair_tonsure_norman` — shaved from the crown down to the nape, hair only on top and at the temples. Worth doing carefully: the shaved nape is on the tapestry itself and is the single most recognisable Norman silhouette in the source.
- `hair_braids` — two braids falling forward over the shoulders.
- `hair_tonsure_monk` — a shaved circle on the crown with a ring of hair around it.
- `hair_topknot` — gathered and tied above the crown.

Use the same stroke and fill primitives the existing three use.

- [ ] **Step 6: Offer them in the picker, gated on the profile**

At `MainActivity.kt:1446`, the picker iterates a hardcoded style list. Change it to iterate `HairStyles.BASE + HairStyles.UNLOCKABLE.filter { it in uiState.unlockedGearIds }` so earned styles appear and unearned ones do not. `unlockedGearIds` already carries everything the profile granted, via C1 Task 3.

- [ ] **Step 7: Keep the random roll on base styles only**

`GameViewModel.kt:302` and `:2036` both roll a starting style. Change both to `HairStyles.BASE.random(...)` — a new player must not be randomly assigned a cut they have not earned. Verify both changed:

Run: `grep -n 'listOf("short", "long", "bald")' app/src/main/java/com/example/game/GameViewModel.kt`

Expected: no matches in `GameViewModel.kt`. The match in `EnemyFactory.kt:374` must remain — Saxons keep the three base styles.

- [ ] **Step 8: Extend the surname generator**

`GameViewModel.kt:475-478` branches on `bald` and `long` to pick a surname. Add cases so the new styles get their own — a tonsured man and a braided one should not both be surnamed as though they were shorn. Follow the existing list idiom in that function.

- [ ] **Step 9: Run tests, render and look**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS

Add a render of each of the four styles to `ArtScreenshotTest.kt`, then:

Run: `gradle :app:testDebugUnitTest --tests "*ArtScreenshotTest*" --console=plain`

**Open `app/src/test/screenshots/` and check each cut by eye**, especially the Norman tonsure against a reference image of the tapestry — it is the one most likely to read as a bald patch rather than a deliberate cut.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/game app/src/main/java/com/example/MainActivity.kt app/src/test/java/com/example/game/HairstyleTest.kt app/src/test/java/com/example/game/ArtScreenshotTest.kt
git commit -m "feat: four unlockable hairstyles including the Norman tonsure"
```

---

### Task 5: Fire the new milestones

**Files:**
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt` (`checkMilestones`, added in C1 Task 5)
- Test: `app/src/test/java/com/example/game/MilestoneTest.kt`

**Background:** C1's `checkMilestones` covers levels, kills, sieges, bosses and two feats. Four of the C2 milestones need state it does not yet read: William beaten twice, the score doubling the previous best, 25 deaths, and Brother Tuck surviving.

- [ ] **Step 1: Extend the milestone checks**

`REACH_10`, `REACH_15`, `REACH_25`, `REACH_35` and `FIVE_SIEGES` slot into the existing level and siege conditions with no new state. Add to `checkMilestones`:

```kotlin
if (level >= 10) add(Milestone.REACH_10)
if (level >= 15) add(Milestone.REACH_15)
if (level >= 25) add(Milestone.REACH_25)
if (level >= 35) add(Milestone.REACH_35)
if (siegesCleared >= 5) add(Milestone.FIVE_SIEGES)
if (bossBeaten == BossType.HARALD_HARDRADA) add(Milestone.HARDRADA_BRAIDS)
```

- [ ] **Step 2: Track the four that need new state**

- `BOTH_GIANTS` — needs both Gog and Magog beaten, possibly across different runs. Persist it: add `beatenBosses: Set<String>` to `GameProfile.Profile` with its own key, written when a boss falls. Fire when both ids are present.
- `WILLIAM_TWICE` — needs a count, not a set. Add `williamKills: Int` to the profile alongside it.
- `DIE_TWENTY_FIVE` — `GameProfile.cached.totalDeaths >= 25`, already tracked by C1 Task 5 Step 7. Check it where the death is recorded, not in `checkMilestones` — that function returns early unless the battle was won.
- `MONK_SURVIVES` — read whether a `Monk` ancillary is in `state.unlockedAncillaries` and the corresponding on-field fighter is alive at the win. The monk is a parade follower, not an on-field body (`NON_PARADE_ANCILLARIES` at `SimulationModels.kt:81` does not list him), so "still alive" means the player has him recruited and won — pass `state.unlockedAncillaries.contains(Ancillary.MONK)`.

Add the two new profile fields following exactly the pattern of `KEY_ITEMS` / `KEY_DEATHS` in `GameProfile.kt`, each with its own preferences key, its own writer, and a default.

- [ ] **Step 3: Write the tests**

Add to `MilestoneAwardTest` in `MilestoneTest.kt`:

```kotlin
@Test
fun williamMustFallTwiceBeforeTheWingedHelmIsEarned() = runTest {
    GameProfile.recordBossKill("boss_william_the_bastard")
    assertFalse(GameProfile.cached.williamKills >= 2)
    GameProfile.recordBossKill("boss_william_the_bastard")
    assertTrue(GameProfile.cached.williamKills >= 2)
}

@Test
fun bothGiantsAreNeededForTheBearMount() = runTest {
    GameProfile.recordBossKill("boss_gog")
    assertFalse("boss_magog" in GameProfile.cached.beatenBosses)
    GameProfile.recordBossKill("boss_magog")
    assertTrue(GameProfile.cached.beatenBosses.containsAll(listOf("boss_gog", "boss_magog")))
}
```

`recordBossKill(bossId: String)` is a new suspend function on `GameProfile` that adds to `beatenBosses` and increments `williamKills` when the id is William's.

- [ ] **Step 4: Run to verify they fail, then implement, then pass**

Run: `gradle :app:testDebugUnitTest --tests "com.example.game.MilestoneAwardTest" --console=plain`

Expected first: FAIL — `Unresolved reference: recordBossKill`. After implementing: PASS.

- [ ] **Step 5: Run the full suite**

Run: `gradle :app:testDebugUnitTest --console=plain`

Expected: PASS, including `MilestoneTest.everyMilestoneGrantsSomethingThatExists`, which has been red since Task 1 and turns green here.

- [ ] **Step 6: Verify the whole loop by hand**

Install the debug APK. Confirm a mount, a hat and a hairstyle each unlock, appear in their pickers, and survive a full app restart.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/game app/src/test/java/com/example/game
git commit -m "feat: fire the C2 mount, hat and hairstyle milestones"
```

---

## Self-Review

**Spec coverage.** The C2 section of the Batch C spec lists three mounts (Task 2), four hats (Task 3) and four hairstyles (Task 4), each with a milestone (Tasks 1 and 5). All eleven rows in the spec's tables appear in Task 1 Step 2 with the same conditions.

**Placeholder scan.** Tasks 2, 3 and 4 leave the *interior* of the draw functions to the implementer — deliberately, and with the constraint named: read the neighbouring function and match its primitives. Specifying eighty lines of hand-tuned Bezier coordinates in a plan would be worse than useless, because the coordinates can only be judged by looking at the rendered PNG. Every other step carries real code. Task 3 Step 3 leaves `mass`/`defense`/`speedPenalty` blank with an explicit instruction to match neighbouring entries, because `HeadgearPiece`'s constructor order was not verified while writing this plan — the step says to read it first rather than guess.

**Type consistency.** `HairStyles.BASE` / `.UNLOCKABLE` / `.ALL` are defined in Task 4 Step 3 and consumed in Steps 6 and 7 and in Task 1's widened test. `Milestone.grants` ids in Task 1 Step 2 match exactly the gear ids created in Tasks 2, 3 and 4 — `anc_mount_ox`/`mule`/`bear`, `helm_antlered`/`winged`/`wolf`/`pot`, `hair_tonsure_norman`/`braids`/`tonsure_monk`/`topknot`. `GameProfile.recordBossKill(bossId: String)`, `beatenBosses` and `williamKills` are introduced in Task 5 Steps 2-3 and used only there.

**One ordering note:** Task 1 deliberately commits a failing test. `MilestoneTest.everyMilestoneGrantsSomethingThatExists` stays red from Task 1 until Task 5 Step 5. This is called out at Task 1 Step 4 so a reviewer does not treat it as a broken build.
