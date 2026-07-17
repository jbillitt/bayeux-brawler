package com.example.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * Open-field battleground: trees and long grass instead of buildings, and — later in the game —
 * a hill the enemy holds. The hill's whole gameplay effect keys off [FighterState.terrainLiftY],
 * which is 0 in every flat battle, so the high-ground bonus can never leak outside a hill fight.
 */
data class HillState(
    val footX: Float,
    val crestX: Float
)

object HillField {
    /** How far the crest stands above the foot, in world px. Tunable on device. */
    const val MAX_LIFT = 150f
    /** Minimum lift difference before the high-ground damage bonus applies. */
    const val BONUS_THRESHOLD = 30f
    const val DOWNHILL_MULT = 1.25f

    /** Negative = higher up the slope. Eases in at the foot and steepens toward the crest. */
    fun liftAt(posX: Float, hill: HillState?): Float {
        if (hill == null || hill.crestX <= hill.footX) return 0f
        val t = ((posX - hill.footX) / (hill.crestX - hill.footX)).coerceIn(0f, 1f)
        return -MAX_LIFT * (t * t)
    }

    /** True when [attacker] holds meaningfully higher ground than [defender]. */
    fun hasHighGround(attacker: FighterState, defender: FighterState): Boolean =
        attacker.terrainLiftY < defender.terrainLiftY - BONUS_THRESHOLD

    fun stateFor(gameSeed: Long, level: Int, levelWidth: Float): HillState =
        HillState(footX = levelWidth * 0.12f, crestX = levelWidth * 0.9f)

    /** Field battles turn hilly from this level on. */
    const val HILL_FROM_LEVEL = 8

    fun isHillLevel(gameSeed: Long, level: Int): Boolean =
        level >= HILL_FROM_LEVEL && Random(gameSeed xor 0x48494c4cL + level).nextFloat() < 0.5f
}

object FieldScenery {
    /**
     * Scattered trees (varied types via seed), the odd farm/hut, and long-grass tufts spanning the
     * field. Trees and huts are placed with the same width-aware spacing as buildings so nothing
     * grows out of anything else; grass tufts are decorative and packed loosely between them.
     */
    fun objectsFor(gameSeed: Long, level: Int, levelWidth: Float): List<BackgroundObject> {
        val random = Random(gameSeed + level * 31)
        val out = mutableListOf<BackgroundObject>()
        val placed = mutableListOf<Pair<Float, Float>>() // x, halfWidth
        fun place(halfWidth: Float): Float? {
            repeat(20) {
                val c = 300f + random.nextFloat() * (levelWidth - 600f)
                if (placed.none { abs(it.first - c) < it.second + halfWidth + 50f }) {
                    placed.add(c to halfWidth)
                    return c
                }
            }
            return null
        }

        // The odd farm or hut breaks up the treeline.
        if (random.nextFloat() < 0.5f) place(160f)?.let { x ->
            val type = if (random.nextBoolean()) BackgroundObjectType.BUILDING_BOSHAM
                else BackgroundObjectType.BUILDING_MANOR
            out += BackgroundObject("field_hut", type, x, width = 300f, hp = 500f, maxHp = 500f, seed = random.nextInt())
        }

        val treeCount = 3 + random.nextInt(3)
        repeat(treeCount) { i ->
            place(130f)?.let { x ->
                out += BackgroundObject(
                    "field_tree_$i", BackgroundObjectType.FIELD_TREE, x,
                    width = 260f, hp = 400f, maxHp = 400f, seed = random.nextInt()
                )
            }
        }

        val grassCount = 6 + random.nextInt(5)
        repeat(grassCount) { i ->
            out += BackgroundObject(
                "field_grass_$i", BackgroundObjectType.FIELD_GRASS,
                150f + random.nextFloat() * (levelWidth - 300f),
                width = 120f, hp = 1f, maxHp = 1f, seed = random.nextInt()
            )
        }
        return out
    }
}
