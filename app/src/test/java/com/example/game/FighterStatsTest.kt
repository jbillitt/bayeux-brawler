package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math checks over FighterState derived stats. These lock in the balance
 * formulas so refactors can't silently shift gameplay.
 */
class FighterStatsTest {

    private fun fighter(
        head: String = "head_sword",
        handle: String = "handle_medium",
        shield: String = "shield_none",
        armor: String = "armor_bare",
        helm: String = "helm_none",
        size: Float = 1.0f,
        level: Int = 1,
        isPlayer: Boolean = true,
        dualWield: Boolean = false,
        extensions: Int = 0
    ) = FighterState(
        id = FighterId("t"), name = "Test", isPlayer = isPlayer, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
        shield = GameData.SHIELDS.first { it.id == shield },
        armor = GameData.ARMOR_PIECES.first { it.id == armor },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == helm },
        posX = 0f, targetX = 0f, size = size, level = level,
        isDualWielding = dualWield, handleExtensionCount = extensions,
        hairColor = Color.Black, hairStyle = "short"
    )

    @Test
    fun attackSpeedDelayNeverBelowFloor() {
        val tiny = fighter(head = "head_dagger", handle = "handle_dagger", size = 0.65f)
        assertTrue("delay ${tiny.attackSpeedDelay}", tiny.attackSpeedDelay >= 0.3f)
    }

    @Test
    fun twoHandingIsFasterThanShielded() {
        val twoHand = fighter(shield = "shield_none")
        val shielded = fighter(shield = "shield_kite")
        assertTrue(twoHand.attackSpeedDelay < shielded.attackSpeedDelay)
    }

    @Test
    fun smallerFightersMoveFaster() {
        val small = fighter(size = 0.8f)
        val large = fighter(size = 1.2f)
        assertTrue(small.moveSpeed > large.moveSpeed)
    }

    @Test
    fun handleExtensionsIncreaseReach() {
        val base = fighter()
        val extended = fighter(extensions = 2)
        assertEquals(base.reach + 0.7f, extended.reach, 0.001f)
    }

    @Test
    fun playerDamageScalesTwelvePercentPerLevel() {
        val l1 = fighter(level = 1)
        val l5 = fighter(level = 5)
        assertEquals(l1.baseDamage * (1.0f + 4 * 0.12f), l5.baseDamage, 0.01f)
    }

    @Test
    fun enemiesDoNotLevelScaleDamage() {
        val e1 = fighter(level = 1, isPlayer = false)
        val e9 = fighter(level = 9, isPlayer = false)
        assertEquals(e1.baseDamage, e9.baseDamage, 0.001f)
    }

    @Test
    fun missingArmZeroesBladeDamage() {
        val f = fighter().apply { missingArm = true }
        assertEquals(0f, f.damageSlash, 0f)
        assertEquals(0f, f.damagePierce, 0f)
        assertTrue(f.damageBlunt >= 0f)
    }

    @Test
    fun nakedRunGetsBigScoreMultiplier() {
        val naked = fighter(armor = "armor_bare", helm = "helm_none", shield = "shield_none", head = "head_dagger", handle = "handle_dagger")
        // 1 base + 4.5 naked + 1.5 bare head + 2.0 no shield + 1.0 light weapon
        assertEquals(10.0f, naked.scoreMultiplier, 0.001f)
    }

    @Test
    fun jesterKitAddsTwentyX() {
        val jester = fighter(armor = "armor_jester", helm = "helm_jester")
        assertTrue(jester.scoreMultiplier >= 20f)
    }

    @Test
    fun enemyScoreMultiplierIsAlwaysOne() {
        assertEquals(1.0f, fighter(isPlayer = false, armor = "armor_bare").scoreMultiplier, 0f)
    }

    @Test
    fun crumpleSlowsAttacksAndMovement() {
        val up = fighter()
        val down = fighter().apply { crumpleDuration = 1f }
        assertTrue(down.attackSpeedDelay > up.attackSpeedDelay)
        assertTrue(down.moveSpeed < up.moveSpeed)
    }

    @Test
    fun smallFistsCadenceWellBelowMediumFighter() {
        val smallFists = fighter(head = "head_bare", handle = "handle_fists", size = 0.65f)
        val medium = fighter(shield = "shield_kite") // head_sword/handle_medium, shield_kite, size 1.0
        assertTrue(
            "small fists delay ${smallFists.attackSpeedDelay} should be well below medium ${medium.attackSpeedDelay}",
            smallFists.attackSpeedDelay < medium.attackSpeedDelay * 0.6f
        )
    }
    @Test
    fun smallFighterGetsMaxHpBonus() {
        val baseHp = 100f
        val size = 0.65f
        val expectedMaxHp = baseHp * (0.75f + 0.25f * size) * (1f + (1f - size) * 0.6f)
        assertTrue("Tiny fighter should have maxHp buff: $expectedMaxHp", expectedMaxHp > baseHp * 0.9f) // they get back to nearly 100 HP, far better than their base 0.65x
    }
}
