package com.example.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hair is a bare string on FighterState, so a typo between a Milestone's `grants` id, the renderer's
 * branch and the picker's list is silent: the style is "earned", selectable, and draws the default
 * bowl cut. These ids are the contract between those three places.
 */
class HairstyleTest {

    /** Must match the branches in TapestryRenderer's hair switch and the picker in MainActivity. */
    private val renderableStyles = setOf(
        "short", "long", "bald",
        "hair_tonsure_norman", "hair_braids", "hair_tonsure_monk", "hair_topknot",
        "hair_mystic", "hair_germanic", "hair_samson"
    )

    /**
     * Every renderable style must carry a HairTrait, because the picker reads its effect line and
     * its stat deltas straight off that table. A style missing from it silently falls back to the
     * bowl cut's "no effect", which reads as a bug in the tooltip rather than a missing row.
     */
    @Test
    fun everyRenderableStyleHasATrait() {
        renderableStyles.forEach {
            assertTrue("$it has no HAIR_TRAITS row", HAIR_TRAITS.containsKey(it))
        }
    }

    @Test
    fun everyHairstyleMilestoneGrantsAStyleTheRendererKnows() {
        val hairGrants = Milestone.values()
            .map { it.grants }
            .filter { it.startsWith("hair_") }
        assertTrue("no hairstyle milestones found at all", hairGrants.isNotEmpty())
        hairGrants.forEach {
            assertTrue("$it is granted but the renderer has no branch for it", it in renderableStyles)
        }
    }

    @Test
    fun theFourEarnedStylesAreAllReachable() {
        val hairGrants = Milestone.values().map { it.grants }.toSet()
        listOf("hair_tonsure_norman", "hair_braids", "hair_tonsure_monk", "hair_topknot").forEach {
            assertTrue("$it can never be earned by any milestone", it in hairGrants)
        }
    }

    /**
     * A hairstyle id must never collide with a gear id — both live in the profile's one item set, and
     * the gear tabs filter on that set. A collision would put a haircut in the weapon list.
     */
    @Test
    fun hairstyleIdsDoNotCollideWithGearIds() {
        val gearIds = (GameData.WEAPON_HEADS.map { it.id } +
            GameData.WEAPON_HANDLES.map { it.id } +
            GameData.SHIELDS.map { it.id } +
            GameData.ARMOR_PIECES.map { it.id } +
            GameData.HEADGEAR_PIECES.map { it.id }).toSet()
        renderableStyles.filter { it.startsWith("hair_") }.forEach {
            assertTrue("$it collides with a real gear id", it !in gearIds)
        }
    }
}
