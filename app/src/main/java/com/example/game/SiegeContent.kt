package com.example.game

import kotlin.math.ceil
import kotlin.random.Random

enum class ClimbState { NONE, CLIMBING_UP, CLIMBING_DOWN }

data class SiegeState(
    var gateHp: Float,
    val gateMaxHp: Float,
    val parapetFighterIds: Set<String>,
    val queuedFighterIds: Set<String>,
    var ladderSpawned: Boolean = false,
    var gateBroken: Boolean = false
)

object SiegeRules {
    const val CLIMB_SECONDS = 0.75f
    const val DOWNHILL_DAMAGE_MULTIPLIER = 1.25f
    const val GROUND_FEET_OFFSET = 158f
    const val PARAPET_ELEVATION_OFFSET = -150f
    /** Cap on a single blow against the gate, so no heavy build one-shots it. */
    const val MAX_GATE_HIT_FRACTION = 0.34f

    fun parapetFeetY(scale: Float): Float =
        200f + (GROUND_FEET_OFFSET + PARAPET_ELEVATION_OFFSET) * scale

    fun damageGate(state: SiegeState, damage: Float, fighters: List<FighterState>) {
        if (state.gateBroken) return
        val capped = damage.coerceIn(0f, state.gateMaxHp * MAX_GATE_HIT_FRACTION)
        state.gateHp = (state.gateHp - capped).coerceAtLeast(0f)
        if (state.gateHp <= 0f) breakGate(state, fighters)
    }

    fun breakGate(state: SiegeState, fighters: List<FighterState>) {
        if (state.gateBroken) return
        state.gateHp = 0f
        state.gateBroken = true
        state.ladderSpawned = true
        fighters.filter { it.id in state.queuedFighterIds }.forEach { it.isCombatInactive = false }

        val eligible = fighters
            .filter {
                it.id in state.parapetFighterIds && it.elevated &&
                    (it.isRanged || it.archetype == EnemyArchetype.WALL_ARCHER) &&
                    !it.isDead && !it.isDying
            }
            .sortedBy { it.id }
        val descendCount = ceil(eligible.size / 2f).toInt()
        eligible.take(descendCount).forEach { beginClimb(it, ClimbState.CLIMBING_DOWN) }
    }

    fun livingParapetEnemies(state: SiegeState, fighters: List<FighterState>): List<FighterState> =
        fighters.filter {
            it.id in state.parapetFighterIds && it.elevated && !it.isDead && !it.isDying &&
                it.climbState != ClimbState.CLIMBING_DOWN
        }

    fun ladderActive(state: SiegeState, fighters: List<FighterState>): Boolean =
        state.ladderSpawned && state.gateBroken && livingParapetEnemies(state, fighters).isNotEmpty()

    fun beginClimbUp(state: SiegeState, fighter: FighterState, fighters: List<FighterState>): Boolean {
        if (fighter.elevated || fighter.climbState != ClimbState.NONE || !ladderActive(state, fighters)) return false
        beginClimb(fighter, ClimbState.CLIMBING_UP)
        return true
    }

    fun beginClimbDown(fighter: FighterState): Boolean {
        if (!fighter.elevated || fighter.climbState != ClimbState.NONE) return false
        beginClimb(fighter, ClimbState.CLIMBING_DOWN)
        return true
    }

    private fun beginClimb(fighter: FighterState, direction: ClimbState) {
        fighter.climbState = direction
        fighter.climbTimer = CLIMB_SECONDS
        fighter.isAttacking = false
        fighter.swingProgress = 0f
        fighter.hasLandedStrike = false
    }

    fun tickClimb(fighter: FighterState, dt: Float) {
        if (fighter.climbState == ClimbState.NONE) return
        fighter.climbTimer -= dt
        if (fighter.climbTimer <= 0f) {
            fighter.elevated = fighter.climbState == ClimbState.CLIMBING_UP
            fighter.climbState = ClimbState.NONE
            fighter.climbTimer = 0f
        }
    }

    fun tickClimbs(fighters: List<FighterState>, dt: Float) =
        fighters.forEach { tickClimb(it, dt) }

    fun reconcileParapet(state: SiegeState, player: FighterState, fighters: List<FighterState>) {
        if (player.elevated && player.climbState == ClimbState.NONE &&
            livingParapetEnemies(state, fighters).isEmpty()
        ) {
            beginClimbDown(player)
        }
    }

    fun isBattleWon(fighters: List<FighterState>): Boolean =
        fighters.none { !it.isPlayer && !it.isDead && !it.isDying }
}

fun elevationVisualOffset(fighter: FighterState): Float = fighter.terrainLiftY + when (fighter.climbState) {
    ClimbState.CLIMBING_UP ->
        SiegeRules.PARAPET_ELEVATION_OFFSET *
            (1f - fighter.climbTimer / SiegeRules.CLIMB_SECONDS).coerceIn(0f, 1f)
    ClimbState.CLIMBING_DOWN ->
        SiegeRules.PARAPET_ELEVATION_OFFSET *
            (fighter.climbTimer / SiegeRules.CLIMB_SECONDS).coerceIn(0f, 1f)
    ClimbState.NONE -> if (fighter.elevated) SiegeRules.PARAPET_ELEVATION_OFFSET else 0f
}

object SiegeSchedule {
    fun levels(gameSeed: Long, throughLevel: Int): List<Int> {
        if (throughLevel < 5) return emptyList()
        val eligible = (5..throughLevel).filter { BossSchedule.forLevel(it) == null }
        if (eligible.isEmpty()) return emptyList()
        val random = Random(gameSeed xor 0x53494547454cL)
        // Draw from the same range regardless of how short the queried prefix is. Using
        // eligible.size here made level-by-level runtime queries reroll the beginning.
        var index = random.nextInt(3)
        val result = mutableListOf<Int>()
        while (index < eligible.size) {
            result += eligible[index]
            index += random.nextInt(6, 9)
        }
        return result
    }

    fun isSiegeLevel(gameSeed: Long, level: Int): Boolean =
        BossSchedule.forLevel(level) == null && level in levels(gameSeed, level)
}

enum class BossType(val level: Int, val latinName: String) {
    HAROLD_GODWINSON(10, "HAROLDUS REX"),
    HARALD_HARDRADA(20, "HARALDUS DURUS"),
    WILLIAM_THE_BASTARD(30, "WILLELMUS BASTARDUS")
}

object BossSchedule {
    fun forLevel(level: Int): BossType? = when {
        level == 10 -> BossType.HAROLD_GODWINSON
        level == 20 -> BossType.HARALD_HARDRADA
        level >= 30 && level % 10 == 0 -> BossType.WILLIAM_THE_BASTARD
        else -> null
    }

    fun forcesThroneMusic(boss: BossType?): Boolean = boss == BossType.WILLIAM_THE_BASTARD
}
