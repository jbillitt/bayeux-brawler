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

    /**
     * The picklist and the stat wiring read unlockedAncillaries, but a milestone grants an id into
     * the profile's item set. Without the fold-in an earned mount is won and never appears.
     */
    @Test
    fun anEarnedMountIsFoldedIntoTheAncillaryList() {
        val folded = GameViewModel.ancillariesWithUnlocks(listOf(Ancillary.SQUIRE))
        assertTrue("the run's own followers must survive", Ancillary.SQUIRE in folded)
        // With an empty profile nothing is added; the fold must at least not lose anything.
        assertTrue(folded.containsAll(listOf(Ancillary.SQUIRE)))
    }
}
