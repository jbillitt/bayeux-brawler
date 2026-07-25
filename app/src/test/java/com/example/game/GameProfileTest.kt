package com.example.game

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk 34: the default (36) needs Java 21 and this toolchain is on 17, same as every other
// Robolectric test in this module.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameProfileTest {

    @Before
    fun setUp() {
        GameProfile.init(ApplicationProvider.getApplicationContext())
        // The store outlives a single test method, so start every one from a known-empty profile.
        kotlinx.coroutines.runBlocking { GameProfile.clearForTest() }
    }

    /** Leaving this Application on the singleton deadlocks later test classes. See GameProfile.shutdown. */
    @After
    fun tearDown() = GameProfile.shutdown()

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

    @Test
    fun theBatchAMigrationDecidesOnceAndOnlyOnce() = runTest {
        GameProfile.migrateBatchAHandles()
        val first = GameProfile.load()
        assertTrue(
            "the migration must record itself or it re-runs every launch",
            GameProfile.MIGRATION_BATCH_A_HANDLES in first.clearedMilestones
        )

        // A second call must change nothing, even if the install record now looks different.
        GameProfile.migrateBatchAHandles()
        assertEquals(first.unlockedItemIds, GameProfile.load().unlockedItemIds)
    }

    @Test
    fun aFreshInstallIsNotHandedTheSevenHandles() = runTest {
        // Robolectric reports firstInstallTime == lastUpdateTime, i.e. never upgraded, which is
        // exactly the new-player case. Veterans are covered by the upgraded branch on device.
        GameProfile.migrateBatchAHandles()
        val unlocked = GameProfile.load().unlockedItemIds
        assertTrue(
            "a brand-new install was given the unlockable handles for free: $unlocked",
            unlocked.none { it in GameData.UNLOCKABLE_HANDLE_IDS }
        )
    }
}
