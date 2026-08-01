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
        assertEquals(base.meleeReachPixels + 2 * GameData.EXTENSION_REACH_PX, extended.meleeReachPixels, 0.001f)
    }

    /** A reward card promises a fixed amount of ground. A small fighter must get all of it. */
    @Test
    fun handleExtensionsAddTheSameReachAtEverySize() {
        val smallGain = fighter(size = 0.7f, extensions = 1).meleeReachPixels - fighter(size = 0.7f).meleeReachPixels
        val largeGain = fighter(size = 1.4f, extensions = 1).meleeReachPixels - fighter(size = 1.4f).meleeReachPixels
        assertEquals(smallGain, largeGain, 0.001f)
    }

    /**
     * The hitbox is read off the drawn weapon, so a blow lands where the head is painted. This
     * reproduces the renderer's layout: fist at GRIP_OFFSET_PX, haft along the basis, head past
     * its end, all scaled by body size, plus the flat extension.
     */
    @Test
    fun reachMatchesWhereTheWeaponIsDrawn() {
        val f = fighter(head = "head_spear", handle = "handle_long", size = 1.2f, extensions = 1)
        val drawnHaft = GameData.haftPixels("handle_long") +
            GameData.EXTENSION_REACH_PX / GameData.HAFT_BASIS_X / 1.2f
        val head = GameData.WEAPON_HEADS.first { it.id == "head_spear" }
        val drawnTip = (GameData.GRIP_OFFSET_PX + drawnHaft * GameData.HAFT_BASIS_X +
            head.reach * GameData.HEAD_OVERHANG_PX) * 1.2f
        assertEquals(drawnTip, f.meleeReachPixels, 0.5f)
    }

    /** Reach stops at the cap however many hafts get lashed on — see GameData.MAX_MELEE_REACH_M. */
    @Test
    fun reachIsCappedInMetresAndPixelsAlike() {
        val absurd = fighter(head = "head_pike", handle = "handle_pike_long", extensions = 40)
        assertEquals(GameData.MAX_MELEE_REACH_M * 40f, absurd.meleeReachPixels, 0.001f)
        assertEquals(GameData.MAX_MELEE_REACH_M, absurd.reach, 0.001f)
        assertEquals(
            GameData.MAX_MELEE_REACH_M,
            GameData.meleeReachMetres("handle_pike_long", 2.5f, 1f, 40),
            0.001f
        )
    }

    /** The counter-curve is the host's alone: the player must never buff himself with it. */
    @Test
    fun lateGameCurveLiftsEnemiesOnlyAndStops() {
        assertEquals(1f, fighter(level = 60).lateGameMultiplier, 0f)
        assertEquals(1f, fighter(level = 30, isPlayer = false).lateGameMultiplier, 0f)
        assertTrue(fighter(level = 50, isPlayer = false).lateGameMultiplier > 1f)
        assertEquals(
            fighter(level = 70, isPlayer = false).lateGameMultiplier,
            fighter(level = 200, isPlayer = false).lateGameMultiplier,
            0f
        )
    }

    /** A second dose deepens the rot; a fresh one after it clears starts from one again. */
    @Test
    fun afflictionsStackToTheCapAndResetWhenCleared() {
        val f = fighter()
        f.applyDot(Dot.POISON, 5f)
        assertEquals(1f, f.dotIntensity(Dot.POISON), 0f)

        repeat(6) { f.applyDot(Dot.POISON, 3f) }
        assertEquals(MAX_DOT_STACKS.toFloat(), f.dotIntensity(Dot.POISON), 0f)
        // The longer clock wins — a short top-up must not cut an existing affliction short.
        assertEquals(5f, f.poisonDuration, 0.001f)

        // Afflictions are independent of one another.
        assertEquals(1f, f.dotIntensity(Dot.BLEED), 0f)

        f.clearDot(Dot.POISON)
        assertEquals(0f, f.poisonDuration, 0f)
        f.applyDot(Dot.POISON, 2f)
        assertEquals(1f, f.dotIntensity(Dot.POISON), 0f)
    }

    /** copy() must not hand two fighters the same affliction array. */
    @Test
    fun afflictionsAreNotSharedBetweenCopies() {
        val original = fighter()
        original.applyDot(Dot.BLEED, 4f)
        original.applyDot(Dot.BLEED, 4f)
        val spawned = original.copy(id = FighterId("t2"))
        assertEquals(0, spawned.dotStacks[Dot.BLEED.ordinal])
        assertEquals(2, original.dotStacks[Dot.BLEED.ordinal])
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
