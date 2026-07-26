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
    var gateBroken: Boolean = false,
    /** Siege-ladder reward: the ladder is up from the first horn, no broken gate required. */
    val siegeLadders: Boolean = false
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
        (state.siegeLadders || (state.ladderSpawned && state.gateBroken)) &&
            livingParapetEnemies(state, fighters).isNotEmpty()

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
    WILLIAM_THE_BASTARD(30, "WILLELMUS BASTARDUS"),
    // The giants of Albion, out of the deep legend at the map's edge. Their retinue is a pack
    // of cynocephali — the dog-headed men of the mappae mundi.
    GOG(40, "GOG GIGAS"),
    MAGOG(50, "MAGOG GIGAS")
}

/**
 * What state a boss turns up in. A tier rather than ten more BossType entries: every rule that
 * keys off the type — Harold's arrow-eye window, Hardrada's staged retinue, the giants' stump,
 * the throne music — keeps working untouched, and the undead inherit all of it.
 */
enum class BossTier(val titlePrefix: String, val latinSuffix: String) {
    LIVING("", ""),
    UNDEAD("Undead ", " REDIVIVUS"),
    SUPER_UNDEAD("Barrow-King ", " REDIVIVUS MAXIMUS");

    /** Undead flesh does not feel a blow the way living flesh does. Multiplies FighterState.ccResist. */
    val ccResistScale: Float
        get() = when (this) {
            LIVING -> 1f
            UNDEAD -> 0.6f
            SUPER_UNDEAD -> 0.3f
        }

    val hpScale: Float
        get() = when (this) {
            LIVING -> 1f
            UNDEAD -> 1.6f
            SUPER_UNDEAD -> 2.6f
        }
}

object BossSchedule {
    /** The five originals, in the order the campaign first meets them. */
    private val CYCLE = listOf(
        BossType.HAROLD_GODWINSON, BossType.HARALD_HARDRADA, BossType.WILLIAM_THE_BASTARD,
        BossType.GOG, BossType.MAGOG
    )

    /**
     * Every tenth level. Up to 50 the originals appear on their own levels; from 60 the dead of
     * Senlac get up again and the five cycle round as UNDEAD, and past 130 as SUPER_UNDEAD.
     *
     * Before this, every level past 50 was William, over and over — a player who reached 136 met
     * the same man nine times.
     */
    fun forLevel(level: Int): BossType? = when {
        level == 10 -> BossType.HAROLD_GODWINSON
        level == 20 -> BossType.HARALD_HARDRADA
        level == 30 -> BossType.WILLIAM_THE_BASTARD
        level == 40 -> BossType.GOG
        level == 50 -> BossType.MAGOG
        level > 50 && level % 10 == 0 -> CYCLE[((level - 60) / 10) % CYCLE.size]
        else -> null
    }

    fun tierForLevel(level: Int): BossTier = when {
        level >= 130 -> BossTier.SUPER_UNDEAD
        level >= 60 -> BossTier.UNDEAD
        else -> BossTier.LIVING
    }

    fun forcesThroneMusic(boss: BossType?): Boolean = boss == BossType.WILLIAM_THE_BASTARD
}
