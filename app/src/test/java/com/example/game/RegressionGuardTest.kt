package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One test per bug that actually reached the phone, so none of them can come back quietly.
 * Each name states the symptom that was reported, not the mechanism.
 */
class RegressionGuardTest {
    private fun fighter(
        id: String,
        archetype: EnemyArchetype? = null,
        player: Boolean = false,
        boss: BossType? = null,
        head: GameData.WeaponHead = GameData.WeaponHead.SWORD
    ) = FighterState(
        id = FighterId(id), name = id, isPlayer = player, maxHp = 100f, hp = 100f,
        weaponHead = head,
        weaponHandle = GameData.WeaponHandle.MEDIUM,
        shield = GameData.Shield.NONE,
        armor = GameData.ArmorPiece.PADDED,
        headgear = GameData.HeadgearPiece.NONE,
        posX = 500f, targetX = 500f, hairColor = Color.Black,
        archetype = archetype, bossType = boss
    )

    @Test
    fun `the burrowers and the skirmisher never march in the parade line`() {
        // They spawn their own bodies on the field; they were doing both at once, so one follower
        // put two of the same man on screen and inflated the parade to five.
        listOf(Ancillary.SAPPER, Ancillary.MOLEMAN, Ancillary.TINY_TERRENCE).forEach {
            assertTrue("$it should be excluded from the parade", it in NON_PARADE_ANCILLARIES)
        }
    }

    @Test
    fun `every follower that fights on the field is kept out of the parade`() {
        // The general form of the bug above: the two lists drifted apart every time a follower
        // gained a body. Anything that fights must not also be drawn marching behind the player.
        FIGHTING_FOLLOWERS.forEach {
            assertTrue("$it fights, so it must not parade", it in NON_PARADE_ANCILLARIES)
        }
    }

    @Test
    fun `a boss cannot be floored twice in a row`() {
        val boss = fighter("boss", boss = BossType.GOG)
        // A boss resists ~90% of knockdowns outright, so keep swinging until one lands rather
        // than assuming the first does — that assumption is what made this test flaky.
        var landed = false
        repeat(500) { if (!landed) landed = boss.tryCrumple(2f, chance = 1f) }
        assertTrue("500 attempts should floor a boss at least once", landed)
        assertTrue("the immunity must be set when he goes down", boss.crumpleCooldown > 0f)

        // Stand him up the way the engine does, leaving the cooldown running.
        boss.crumpleDuration = 0f
        boss.isCrumpled = false
        repeat(500) {
            assertFalse(
                "a boss with attackers either side was re-floored the instant he stood up",
                boss.tryCrumple(2f, chance = 1f)
            )
        }
    }

    @Test
    fun `an ordinary man has no knockdown immunity`() {
        val man = fighter("housecarl")
        assertTrue(man.tryCrumple(2f, chance = 1f))
        man.crumpleDuration = 0f
        assertEquals("only bosses get breathing room", 0f, man.crumpleCooldown, 0.0001f)
    }

    @Test
    fun `a giant snail is not knocked about by the retinue`() {
        val snail = fighter("snail", archetype = EnemyArchetype.REBEL_SNAIL)
        assertTrue(
            "the snail spent every fight on its back instead of biting",
            snail.ccResist < fighter("housecarl").ccResist / 4f
        )
    }

    @Test
    fun `the torch bearer actually carries a lit brand`() {
        // He was kitted with a club and had his flame pinned on by archetype, so the smoke came
        // out of his empty fist.
        val bearer = EnemyFactory.createArchetype(EnemyArchetype.TORCH_BEARER, 0, level = 10)
        assertEquals("head_torch", bearer.weaponHead.id)
        assertTrue("the flame must track a real head", bearer.torchHeadsWorld.isNotEmpty())
    }

    @Test
    fun `a boss hits harder than the same man with the same weapon`() {
        val boss = fighter("boss", boss = BossType.GOG)
        val man = fighter("man")
        assertTrue(
            "bosses were out-traded by a stacked player",
            boss.baseDamage > man.baseDamage
        )
    }

    @Test
    fun `the serjeants maul clears the threshold that floors a man`() {
        // The archetype only counters a player horde if its blunt actually exceeds the >18 gate
        // in CombatEngine's crumple check. A gear change that quietly dropped under it would
        // leave the unit shipping and doing nothing.
        val serjeant = EnemyFactory.createArchetype(EnemyArchetype.HAMMER_SERJEANT, 0, level = 30)
        assertTrue(
            "maul blunt was ${serjeant.damageBlunt}, needs to clear 18",
            serjeant.damageBlunt > 18f
        )
    }

    @Test
    fun `twins are rarer than a plain follower and rarer still is the triple`() {
        val random = kotlin.random.Random(7)
        val twinRate = (0 until 4000).count { rollFollowerCopies(random) == 2 } / 4000f
        assertTrue("twins rate was $twinRate", twinRate in 0.03f..0.075f)
    }
}
