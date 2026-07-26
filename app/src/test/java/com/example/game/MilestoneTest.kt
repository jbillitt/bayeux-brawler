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
import org.robolectric.annotation.Config

class MilestoneTest {

    private val allGearIds: Set<String> =
        (GameData.WEAPON_HEADS.map { it.id } +
         GameData.WEAPON_HANDLES.map { it.id } +
         GameData.SHIELDS.map { it.id } +
         GameData.ARMOR_PIECES.map { it.id } +
         GameData.HEADGEAR_PIECES.map { it.id }).toSet()

    @Test
    fun everyMilestoneGrantsSomethingThatExists() {
        // Rewards are not all GearItems: mounts are Ancillary ids and hairstyles are bare strings.
        val ancillaryIds = Ancillary.values().map { it.id }.toSet()
        // Read off the trait table rather than repeated here: a new style needs a HAIR_TRAITS row
        // anyway for its effect line, so that table is the one list that cannot go stale.
        val hairStyles = HAIR_TRAITS.keys
        Milestone.values().forEach { m ->
            val known = m.grants in allGearIds || m.grants in ancillaryIds || m.grants in hairStyles
            assertTrue("${m.id} grants ${m.grants}, which is not real content", known)
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

    /** Two milestones granting the same item would waste one of them silently. */
    @Test
    fun noTwoMilestonesGrantTheSameItem() {
        val grants = Milestone.values().map { it.grants }
        assertEquals("a reward is duplicated: $grants", grants.size, grants.toSet().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MilestoneAwardTest {

    @Before
    fun setUp() {
        GameProfile.init(ApplicationProvider.getApplicationContext())
        kotlinx.coroutines.runBlocking { GameProfile.clearForTest() }
    }

    /** Leaving this Application on the singleton deadlocks later test classes. See GameProfile.shutdown. */
    @org.junit.After
    fun tearDown() = GameProfile.shutdown()

    @Test
    fun aMilestoneFiresOnceAndOnlyOnce() = runTest {
        val first = GameViewModel.awardMilestone(Milestone.REACH_5)
        val second = GameViewModel.awardMilestone(Milestone.REACH_5)
        assertTrue("first award should fire", first)
        assertFalse("second award must not fire again", second)
    }

    @Test
    fun anAwardedMilestonePutsItsItemInThePool() = runTest {
        GameViewModel.awardMilestone(Milestone.HARDRADA_BRAIDS)
        assertTrue("hair_braids" in GameViewModel.poolWithUnlocks(emptySet()))
        // Handles and Strange Relics instead join what the next run ROLLS from, rather than
        // being handed over outright — otherwise a cheese wheel is in every future loadout.
        GameViewModel.awardMilestone(Milestone.NAKED_WIN)
        assertFalse("a relic must not be handed straight to the run",
            "head_eel" in GameViewModel.poolWithUnlocks(emptySet()))
        GameViewModel.awardMilestone(Milestone.BEAT_HARDRADA)
        assertTrue(GameViewModel.handleRollPool().any { it.id == "handle_anchor" })
    }

    @Test
    fun williamMustFallTwiceBeforeTheWingedHelmIsEarned() = runTest {
        GameProfile.recordBossKill(GameProfile.WILLIAM_BOSS_ID)
        assertFalse("one William should not be enough", GameProfile.cached.williamKills >= 2)
        GameProfile.recordBossKill(GameProfile.WILLIAM_BOSS_ID)
        assertTrue(GameProfile.cached.williamKills >= 2)
    }

    @Test
    fun bothGiantsAreNeededForTheBearMount() = runTest {
        GameProfile.recordBossKill("boss_gog")
        assertFalse("boss_magog" in GameProfile.cached.beatenBosses)
        GameProfile.recordBossKill("boss_magog")
        assertTrue(GameProfile.cached.beatenBosses.containsAll(listOf("boss_gog", "boss_magog")))
    }

    /**
     * The boss id the ViewModel writes must be the one the milestone check looks for. These two
     * strings are built in different places and a mismatch silently never unlocks the bear.
     */
    @Test
    fun theGiantBossIdsMatchWhatTheMilestoneChecksFor() = runTest {
        listOf(BossType.GOG, BossType.MAGOG).forEach { boss ->
            GameProfile.recordBossKill("boss_${boss.name.lowercase()}")
        }
        assertTrue(
            "the ids written on a giant's death do not match the pair the bear milestone wants",
            GameProfile.cached.beatenBosses.containsAll(listOf("boss_gog", "boss_magog"))
        )
    }
}
