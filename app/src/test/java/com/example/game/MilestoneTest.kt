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
        GameViewModel.awardMilestone(Milestone.BEAT_HARDRADA)
        assertTrue("handle_anchor" in GameViewModel.poolWithUnlocks(emptySet()))
    }
}
