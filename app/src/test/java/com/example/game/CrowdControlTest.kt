package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fixes for "bosses get their arms chopped off early and just can't do anything":
 * crowd control now scales by ccResist, and every knockdown routes through tryCrumple so no
 * call site can quietly reintroduce a permanent flooring.
 */
class CrowdControlTest {

    private fun fighter(
        id: String = "saxon",
        boss: BossType? = null,
        retinue: Boolean = false
    ) = FighterState(
        id = FighterId(id), name = "T", isPlayer = false, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
        posX = 0f, targetX = 0f, hairColor = Color.Black, hairStyle = "short",
        bossType = boss, isBossRetinue = retinue
    )

    @Test
    fun aBossResistsCrowdControlFarBetterThanALevy() {
        assertEquals(1f, fighter().ccResist, 1e-4f)
        assertTrue("a boss must resist more than its retinue",
            fighter(boss = BossType.HAROLD_GODWINSON).ccResist < fighter(retinue = true).ccResist)
        assertTrue("a retinue elite must still resist more than a levy",
            fighter(retinue = true).ccResist < fighter().ccResist)
    }

    @Test
    fun aBossGoesDownFarLessOftenAndGetsUpFarSooner() {
        // chance = 1f, so the only thing separating them is ccResist.
        val levyDowns = (1..400).count { fighter().tryCrumple(2.5f) }
        val bossDowns = (1..400).count { fighter(boss = BossType.HARALD_HARDRADA).tryCrumple(2.5f) }
        assertTrue("a levy should be floored nearly every time, got $levyDowns", levyDowns > 380)
        assertTrue("a boss should rarely be floored, got $bossDowns", bossDowns < 160)

        // And when a boss does go down it is a stumble, not a nap.
        val boss = fighter(boss = BossType.HARALD_HARDRADA)
        while (!boss.tryCrumple(2.5f)) boss.crumpleDuration = 0f
        assertTrue("a floored boss should recover quickly, got ${boss.crumpleDuration}",
            boss.crumpleDuration < 1.2f)
    }

    @Test
    fun alreadyFlooredFightersAreNotReFloored() {
        val f = fighter()
        assertTrue(f.tryCrumple(2f))
        val held = f.crumpleDuration
        assertFalse("a second knockdown must not refresh the timer", f.tryCrumple(3.5f))
        assertEquals(held, f.crumpleDuration, 1e-4f)
    }

    /** The trojan horse is carpentry: it has no blood to spill and no arm to sever. */
    @Test
    fun theTrojanHorseIsInanimate() {
        assertTrue(fighter(id = "trojan_horse").isInanimate)
        assertTrue("a stacked horse is still carpentry", fighter(id = "trojan_horse#1").isInanimate)
        assertFalse(fighter(id = "saxon#3").isInanimate)
    }

    @Test
    fun mountsAreNeverOfferedAsATripleCandidate() {
        val stable = listOf(Ancillary.WARHORSE, Ancillary.CHARIOT, Ancillary.WARDOG, Ancillary.WARDOG)
        repeat(200) {
            val pick = pickTripleCandidate(stable, emptySet())
            assertTrue("a mount was offered for tripling: ${pick?.id}",
                pick == null || pick.id !in MOUNT_ANCILLARY_IDS)
        }
        // With nothing but mounts to choose from there is simply no candidate.
        assertNull(pickTripleCandidate(listOf(Ancillary.WARHORSE, Ancillary.CHARIOT), emptySet()))
    }

    @Test
    fun theEpitaphNamesWhoeverAndWhateverWasRecorded() {
        assertEquals(
            "Slain by Cerdic the Immovable, wielding a Dane Axe.",
            FlavourText.slainByLine("Cerdic the Immovable", "a Dane Axe")
        )
        assertEquals("Felled by an arrow.", FlavourText.slainByLine(null, "an arrow"))
        assertNull("a retirement has no killer", FlavourText.slainByLine(null, null))
    }
}
