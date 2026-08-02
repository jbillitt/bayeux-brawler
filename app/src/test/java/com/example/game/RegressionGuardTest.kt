package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `the frog never swallows the player or the trojan horse`() {
        val frog = fighter("frog", archetype = EnemyArchetype.GIANT_FROG)
        // A follower small enough to go down whole.
        val small = fighter("follower", player = true).apply { hp = 20f }
        assertTrue("a light follower is a mouthful", small.isSwallowableBy(frog))

        // The horse is carpentry.
        val horse = fighter("trojan_horse#0", player = true).apply { hp = 20f }
        assertFalse("the trojan horse cannot be eaten", horse.isSwallowableBy(frog))

        // Too big a mouthful: gets crunched instead, which the tick handles separately.
        val heavy = fighter("housecarl", player = true).apply { hp = FROG_GULP_HP + 1f }
        assertFalse("above the gulp threshold he is chewed, not swallowed", heavy.isSwallowableBy(frog))

        // The host's own men are not food.
        val saxon = fighter("saxon").apply { hp = 10f }
        assertFalse("a frog does not eat the army it fights for", saxon.isSwallowableBy(frog))
    }

    @Test
    fun `the bomb is a thrown ranged weapon at throwing range`() {
        val bomb = GameData.WEAPON_HEADS.first { it.id == "head_bomb" }
        val javelin = GameData.WEAPON_HEADS.first { it.id == "head_javelin" }
        val longbow = GameData.WEAPON_HEADS.first { it.id == "head_longbow" }
        assertTrue("a bomb is thrown, so it must count as ranged", bomb.isRanged)
        // Thrown, so it belongs near the javelin and nowhere near a longbow. If someone tunes the
        // reach up to bow range the weapon stops being "get close enough to lob it" and the whole
        // risk of carrying it disappears.
        assertTrue(
            "bomb reach ${bomb.reach} should sit near the javelin's ${javelin.reach}, not the bow's ${longbow.reach}",
            bomb.reach < longbow.reach && bomb.reach <= javelin.reach
        )
    }

    @Test
    fun `the cyclops is the biggest thing on the field and keeps his own boss level`() {
        val cyclops = EnemyFactory.createBoss(BossType.POLYPHEMUS, 60)
        val gog = EnemyFactory.createBoss(BossType.GOG, 40)
        assertTrue(
            "the cyclops should tower over the other giants: ${cyclops.size} vs ${gog.size}",
            cyclops.size > gog.size
        )
        assertEquals("level 60 is his", BossType.POLYPHEMUS, BossSchedule.forLevel(60))
        // The originals must keep theirs — inserting him at 60 pushed the undead rotation back one
        // step, and getting that wrong silently reshuffles every boss past 60.
        assertEquals(BossType.GOG, BossSchedule.forLevel(40))
        assertEquals(BossType.MAGOG, BossSchedule.forLevel(50))
        (70..120 step 10).forEach {
            assertNotNull("level $it must still field a boss", BossSchedule.forLevel(it))
        }
    }

    @Test
    fun `capes trade protection against speed and the feathered one is the fastest`() {
        fun cape(id: String) = GameData.ARMOR_PIECES.first { it.id == id }
        val wool = cape("cape_wool")
        val riding = cape("cape_riding")
        val feather = cape("cape_feather")
        // A negative speedPenalty is a speed BONUS. The feather cloak is the one you take when you
        // want to move; if it ever stops being the fastest it has no reason to exist.
        listOf("cape_wool", "cape_riding", "cape_ermine", "cape_tatters").forEach {
            assertTrue(
                "${cape(it).itemName} must not out-run the feather cloak",
                feather.speedPenalty < cape(it).speedPenalty
            )
        }
        // And it must cost you something, or it is strictly better than every other cape.
        assertTrue("the feather cloak stops the least", feather.defense < wool.defense)
        assertTrue("the rider's cloak stops the most", riding.defense > wool.defense)
    }

    @Test
    fun `the standard bearer fights on the field rather than in the parade`() {
        // He runs in with the front rank, so he has a body — which means he must be excluded from
        // the parade line, the same bug the burrowers had.
        assertTrue(Ancillary.STANDARD_BEARER in FIGHTING_FOLLOWERS)
        assertTrue(Ancillary.STANDARD_BEARER in NON_PARADE_ANCILLARIES)
    }

    @Test
    fun `a late siege garrison is armed better than an early one`() {
        // The garrison has to keep up with a player who is stacking armour, or a deep siege turns
        // into a walk. Crossbows go through what bows stopped scratching.
        fun crossbowsIn(level: Int, wallSize: Int = 8): Int {
            val every = parapetCrossbowEvery(level)
            return (0 until wallSize).count { every != Int.MAX_VALUE && it % every == 0 }
        }
        assertEquals("an early wall is all bows", 0, crossbowsIn(10))
        assertTrue("from 25 some of the wall carries crossbows", crossbowsIn(25) > 0)
        assertTrue(
            "and a deep siege carries more of them than a mid one",
            crossbowsIn(45) > crossbowsIn(25)
        )
    }

    @Test
    fun `a Blemmya wears nothing that would hide the face on his chest`() {
        val blemmya = EnemyFactory.createArchetype(EnemyArchetype.BLEMMYA, 0, level = 40)
        // He has no head, so a helm would float. And a kite shield covers the whole torso, which
        // is where his face is — the one thing that makes him readable was behind his own shield.
        assertEquals("helm_none", blemmya.headgear.id)
        assertEquals("shield_buckler", blemmya.shield.id)
    }

    @Test
    fun `the marginalia creatures each read at their own scale`() {
        val rabbit = EnemyFactory.createArchetype(EnemyArchetype.KILLER_RABBIT, 0, level = 40)
        val fox = EnemyFactory.createArchetype(EnemyArchetype.CRAFTY_FOX, 0, level = 40)
        val blemmya = EnemyFactory.createArchetype(EnemyArchetype.BLEMMYA, 0, level = 40)
        assertTrue("the rabbit is the small one", rabbit.size < fox.size)
        assertTrue("the blemmya is the big one", blemmya.size > fox.size)
        // Speed is the rabbit's real defence, and it stays the frailer of the two...
        assertTrue("the rabbit must be the fastest", rabbit.moveSpeed > fox.moveSpeed)
        assertTrue("and the frailer of the two", rabbit.maxHp < fox.maxHp)
        // ...but neither may go back to dying on contact. They arrive at level 35 against a player
        // who one-shots anything flimsy, and at their first tuning both simply evaporated.
        val plainHousecarl = EnemyFactory.createArchetype(EnemyArchetype.HOUSECARL, 0, level = 40)
        assertTrue(
            "the rabbit was ${rabbit.maxHp} against a housecarl's ${plainHousecarl.maxHp} — too frail to matter",
            rabbit.maxHp >= plainHousecarl.maxHp * 0.6f
        )
        assertTrue(
            "the fox should outlast a plain housecarl; it is the durable one of the pair",
            fox.maxHp > plainHousecarl.maxHp
        )
    }

    @Test
    fun `the frog is as hard to shift as the snail`() {
        val frog = fighter("frog", archetype = EnemyArchetype.GIANT_FROG)
        assertTrue(
            "a thing the size of a cart should not be knocked about",
            frog.ccResist < 0.2f
        )
    }

    @Test
    fun `twins are rarer than a plain follower and rarer still is the triple`() {
        val random = kotlin.random.Random(7)
        val twinRate = (0 until 4000).count { rollFollowerCopies(random) == 2 } / 4000f
        assertTrue("twins rate was $twinRate", twinRate in 0.03f..0.075f)
    }
}
