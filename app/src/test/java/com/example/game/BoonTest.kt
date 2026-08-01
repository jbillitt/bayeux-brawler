package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The rewarded-ad payout. The thing worth pinning down is that it is no longer always the strongest
 * option: extra spoils used to be the whole payout, and the complaint was that watching an ad was
 * strictly better than not.
 */
class BoonTest {

    private fun state(level: Int = 10, followers: List<Ancillary> = listOf(Ancillary.ARCHER)) =
        BattleSimState(
            level = level,
            unlockedAncillaries = followers,
            pendingLevelUpChoices = listOf(
                LevelUpChoice(id = "held", title = "Held", description = "", type = "armor", itemId = "held")
            )
        )

    @Test
    fun `extra spoils is a minority of payouts`() {
        val counts = (0 until 4000)
            .groupingBy { Boons.roll(state(), Random(it)) }
            .eachCount()
        val spoils = (counts[Boons.Boon.SPOILS] ?: 0) + (counts[Boons.Boon.EXTRA_SPOILS] ?: 0)
        assertTrue("cards came up $spoils/4000 — the ad is meant to be a lottery, not a card shop",
            spoils < 2600)
        assertTrue("two-card payouts came up ${counts[Boons.Boon.EXTRA_SPOILS]}/4000, too often",
            (counts[Boons.Boon.EXTRA_SPOILS] ?: 0) < 900)
        Boons.Boon.entries.forEach {
            assertTrue("$it never came up in 4000 rolls", (counts[it] ?: 0) > 0)
        }
    }

    @Test
    fun `the retinue boon is not offered when there is nobody to promote`() {
        val alone = state(followers = listOf(Ancillary.HERALD)) // parades, never fights
        repeat(500) {
            assertTrue(Boons.roll(alone, Random(it)) != Boons.Boon.RETAINER)
        }
    }

    @Test
    fun `vigour is small early and capped late`() {
        assertEquals(5, Boons.vigourFor(1))
        assertTrue(Boons.vigourFor(5) < 15)
        assertEquals(50, Boons.vigourFor(60))
        assertEquals(50, Boons.vigourFor(200))
    }

    @Test
    fun `every boon banks something and reports it`() {
        Boons.Boon.entries.forEach { boon ->
            val after = Boons.apply(state(), boon, Random(1))
            assertNotNull("$boon told the player nothing", after.lastBoon)
            assertTrue("$boon did not consume this level's offer", after.adRewardClaimedThisLevel)
            val changed = when (boon) {
                Boons.Boon.SPOILS, Boons.Boon.EXTRA_SPOILS ->
                    after.pendingLevelUpChoices.size > state().pendingLevelUpChoices.size
                Boons.Boon.VIGOUR -> after.bonusMaxHp > 0f
                Boons.Boon.RETAINER -> after.buffedFollowerKinds.isNotEmpty()
            }
            assertTrue("$boon granted nothing", changed)
        }
    }

    @Test
    fun `retainer doses stack rather than dedupe`() {
        var s = state(followers = listOf(Ancillary.ARCHER))
        repeat(3) { s = Boons.apply(s, Boons.Boon.RETAINER, Random(it)) }
        assertEquals(listOf("archer", "archer", "archer"), s.buffedFollowerKinds)
    }

    @Test
    fun `the herald calls every other level, not every one`() {
        val offered = (1..20).count { BattleSimState(level = it).boonLevel }
        assertEquals(10, offered)
        assertTrue(BattleSimState(level = 4).boonLevel)
        assertTrue(!BattleSimState(level = 5).boonLevel)
    }

    /**
     * A follower who fights has a body and hit points of his own. Saying "Max HP +30" on his card
     * reads as a buff to the player, which it is not — and for the several whose boosts are zero it
     * used to render as the literal "Max HP +0, speed +0%".
     */
    @Test
    fun `a follower's card only claims a player bonus when there is one`() {
        Ancillary.entries.forEach { anc ->
            val line = followerStatLine(anc)
            assertTrue("$anc still advertises a zero bonus: $line", "+0" !in line)
            if (anc in FIGHTING_FOLLOWERS) {
                assertTrue("$anc fights, but its card reads as a player buff: $line",
                    line.startsWith("Fights for you"))
            }
            // Whatever the wording, a "+" may only appear when a boost really lands on the player.
            val boosts = anc.hpBoost != 0f || anc.speedBoost != 0f
            if (!boosts && anc.id !in OBJECT_ANCILLARY_IDS) {
                assertTrue("$anc grants nothing yet quotes a number: $line", "+" !in line)
            }
        }
    }

    @Test
    fun `a negative boost is written as a minus, never as a plus`() {
        // The cupbearer slows you down: "speed +-10%" is what the old string produced.
        val line = followerStatLine(Ancillary.CUPBEARER)
        assertTrue("cupbearer reads: $line", "+-" !in line && "-10%" in line)
    }

    @Test
    fun `an ancillary id maps to the fighter id its spawn uses`() {
        assertEquals("moleman", Ancillary.MOLEMAN.fighterKind)
        val spawned = FighterId("moleman#2")
        assertTrue(spawned.raw.startsWith("${Ancillary.MOLEMAN.fighterKind}#"))
    }
}
