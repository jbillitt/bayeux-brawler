package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `the pounce lands short of the front rank instead of in among them`() {
        // It aimed at the bowman and carried the player clean over the shield line, so he landed
        // with enemies on both sides of him.
        val landing = leapLanding(playerX = 200f, frontRankX = 600f, levelWidth = 2400f)
        assertNotNull("a 400px gap is worth a pounce", landing)
        assertTrue("must stop short of the front rank, not past it", landing!! < 600f)
        assertTrue("and not fall short of being a pounce at all", landing > 400f)

        // Leaping leftwards mirrors exactly.
        val back = leapLanding(playerX = 900f, frontRankX = 400f, levelWidth = 2400f)
        assertTrue("the standoff is on the near side whichever way he jumps", back!! > 400f)

        // Already in contact: no leap at all rather than a hop into the man's face.
        assertNull(
            "the front rank is already on him — the cooldown is worth more than the hop",
            leapLanding(playerX = 560f, frontRankX = 600f, levelWidth = 2400f)
        )
    }

    @Test
    fun `a follower does not walk through a gate that is still standing`() {
        // The player has always honoured this; his retinue did not, so a defender woken up inside
        // the gate pulled the whole squad walking through a solid door instead of breaking it.
        val follower = fighter("follower", player = true).apply { posX = GameViewModel.SIEGE_GATE_X - 300f }
        val defender = fighter("garrison").apply { posX = GameViewModel.SIEGE_GATE_X + 260f }
        assertTrue(
            "a man behind a shut gate is not something to walk toward",
            unreachableBehindGate(follower, defender, gateStanding = true)
        )
        assertFalse(
            "once the gate is down he is fair game",
            unreachableBehindGate(follower, defender, gateStanding = false)
        )
        // And a digger who came up on the far side is already past the door — he fights normally.
        val sapper = fighter("sapper", player = true).apply { posX = GameViewModel.SIEGE_GATE_X + 200f }
        assertFalse(
            "the tunnel put him inside; the gate is not between them",
            unreachableBehindGate(sapper, defender, gateStanding = true)
        )
    }

    @Test
    fun `a miracle never falls on your own men`() {
        val host = fighter("housecarl")
        val ally = fighter("mad_boris", player = true)
        val targets = weatherTargets(listOf(host, ally))
        assertTrue("the host is what a miracle is for", host in targets)
        assertFalse("Rain of Frogs was flattening the player's own retinue", ally in targets)
    }

    @Test
    fun `a volley shaft passes over the man in front and only bites coming down`() {
        fun shaft(velocityY: Float, arcing: Boolean) = Projectile(
            id = "p", isPlayerOwned = true, posX = 0f, posY = 200f,
            velocityX = 300f, velocityY = velocityY, damage = 1f, pierce = 0f, blunt = 0f,
            type = ProjectileType.ARROW, isArcing = arcing
        )
        assertFalse(
            "a rising volley shaft was being eaten by the man directly ahead of the archer",
            shaft(velocityY = -180f, arcing = true).atBodyHeight()
        )
        assertTrue("but it kills what it lands on", shaft(velocityY = 120f, arcing = true).atBodyHeight())
        assertTrue("a flat shot is unchanged", shaft(velocityY = -180f, arcing = false).atBodyHeight())
    }

    @Test
    fun `no run ever opens wearing a cloak as its body armour`() {
        // The first-launch roll excluded capes and the between-runs roll did not, so they came
        // back as a starting option on every run after the first — and equipped into the body
        // slot, which also repainted the tunic in the cloak's colour.
        val pool = GameData.bodyArmourRollPool().map { it.id }
        assertTrue("the pool must not be empty", pool.isNotEmpty())
        GameData.CAPE_IDS.forEach {
            assertFalse("$it is a layer, not body armour", it in pool)
        }
        GameData.LAYER_ARMOUR_IDS.forEach {
            assertFalse("$it is worn over something else", it in pool)
        }
        assertTrue("plain armour still rolls", "armor_chainmail" in pool)
        // And the default a fresh sim state is built with comes from the same pool.
        assertFalse(BattleSimState().armor.id in GameData.CAPE_IDS)
    }

    @Test
    fun `a shield does not cover a man's back`() {
        val man = fighter("housecarl").apply {
            shield = GameData.SHIELDS.first { it.id == "shield_kite" }
            facingRight = true
        }
        assertTrue("braced into the blow", man.shieldCovers(attackFromRight = true))
        assertFalse("stabbed from behind", man.shieldCovers(attackFromRight = false))
        man.shield = GameData.SHIELDS.first { it.id == "shield_none" }
        assertFalse("no shield, no block, from any side", man.shieldCovers(attackFromRight = true))
    }

    @Test
    fun `the standard bearer is not announced twice`() {
        // The level-up card renders "<name> the <role>", and his name carried its own title, so
        // he read as "Wulfric the Banneret the Standard Bearer".
        val anc = Ancillary.STANDARD_BEARER
        assertEquals("Wulfric the Standard Bearer", "${anc.ancillaryName} the ${anc.role}")
        GameData.ANCILLARIES.forEach {
            assertFalse(
                "${it.ancillaryName} carries its own title and will double up with '${it.role}'",
                it.ancillaryName.contains(" the ", ignoreCase = true)
            )
        }
    }

    @Test
    fun `the bear is never the mount a run opens on`() {
        // 110hp and +70% speed from the first horn settled the run before it started. Only the
        // mule may begin one; the rest are still won mid-run.
        val random = kotlin.random.Random(3)
        val everOffered = (0 until 400).flatMap {
            GameViewModel.ancillariesWithUnlocks(emptyList(), random)
        }.toSet()
        assertFalse(Ancillary.WAR_BEAR in everOffered)
        assertFalse(Ancillary.WAR_OX in everOffered)
    }

    @Test
    fun `a thrown weapon is thrown rather than swung`() {
        // The overarm branch existed on the back-arm path only, so in game the javelin kept
        // playing whatever the generic swing did.
        assertTrue("head_javelin" in OVERARM_THROW_HEAD_IDS)
        assertTrue("head_bomb" in OVERARM_THROW_HEAD_IDS)
        // Both are thrown weapons, so both must actually be ranged or the throw never plays.
        OVERARM_THROW_HEAD_IDS.forEach {
            assertTrue("$it must be a missile", GameData.WEAPON_HEADS.first { h -> h.id == it }.isRanged)
        }
    }

    @Test
    fun `the sapper digs with a spade and it is still worth swinging`() {
        val spade = GameData.WEAPON_HEADS.first { it.id == "head_spade" }
        val axe = GameData.WEAPON_HEADS.first { it.id == "head_axe" }
        assertTrue("he traded an axe for it; it cannot be a joke", spade.slash + spade.blunt > axe.slash * 0.8f)
        assertFalse("a spade is not a missile", spade.isRanged)
    }

    @Test
    fun `no chord in any mode on any ground contains a tritone against its root`() {
        // The discordant twang. "The degree four steps up" is a fifth in most modes on most
        // degrees, and six semitones — a tritone — on vii in ionian and on iv in phrygian, which
        // the grounds visit constantly. Held under a drone and rolled across a harp it is the
        // noise Joshua could hear.
        Mode.values().forEach { mode ->
            val spec = SongSpec(
                seed = 1L, family = Family.GREENSLEEVES, mode = mode, finalMidi = 50,
                beatsPerBar = 4, secondsPerBeat = 0.5f, bpm = 120,
                ground = List(8) { GroundBar(it) }, totalBars = 8,
                ornamentDensity = 0.2f, moods = emptyList()
            )
            (0..6).forEach { degree ->
                val chord = groundChordMidis(spec, degree)
                assertFalse(
                    "$mode on degree $degree strums a tritone: $chord",
                    chord.any { (it - chord[0]) % 12 == 6 }
                )
                val strum = strumDegrees(spec, degree, listOf(0, 2, 4, 7, 9, 11))
                val root = degreeToMidi(spec, degree)
                strum.forEach { step ->
                    assertFalse(
                        "$mode on degree $degree rolls a tritone at step $step",
                        (degreeToMidi(spec, degree + step) - root).mod(12) == 6
                    )
                }
                assertTrue("a strum needs at least a root and a third", strum.size >= 3)
            }
        }
    }

    @Test
    fun `borrowed divisions and runs are a trait of some songs, not of all of them`() {
        // Adding triplets and runs to every tune only moves where the sameness lives. Each is
        // rolled once per song off its seed, so a run of songs is a mix of plain and florid.
        fun spec(seed: Long) = SongSpec(
            seed = seed, family = Family.GREENSLEEVES, mode = Mode.DORIAN, finalMidi = 50,
            beatsPerBar = 4, secondsPerBeat = 0.5f, bpm = 120,
            ground = List(8) { GroundBar(0) }, totalBars = 16,
            ornamentDensity = 0.3f, moods = emptyList()
        )
        // Melody generation is deterministic per seed, so "some do and some don't" is checkable.
        val noteCounts = (1L..40L).map { generateSong(spec(it), melodyRng(it)).melody.size }
        assertTrue("every song generated the same number of notes — nothing varies",
            noteCounts.toSet().size > 5)
    }

    @Test
    fun `twins are rarer than a plain follower and rarer still is the triple`() {
        val random = kotlin.random.Random(7)
        val twinRate = (0 until 4000).count { rollFollowerCopies(random) == 2 } / 4000f
        assertTrue("twins rate was $twinRate", twinRate in 0.03f..0.075f)
    }
}
