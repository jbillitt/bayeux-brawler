package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiegeContentTest {
    private fun fighter(
        id: String,
        archetype: EnemyArchetype? = null,
        elevated: Boolean = false,
        player: Boolean = false
    ) = FighterState(
        id = FighterId(id), name = id, isPlayer = player, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WeaponHead.SWORD,
        weaponHandle = GameData.WeaponHandle.MEDIUM,
        shield = GameData.Shield.NONE,
        armor = GameData.ArmorPiece.PADDED,
        headgear = GameData.HeadgearPiece.NONE,
        posX = 500f, targetX = 500f, hairColor = Color.Black,
        archetype = archetype, elevated = elevated
    )

    @Test
    fun `1 gate break releases queue and sends fixed ranged fraction down but never melee up`() {
        val wall = (0 until 4).map { fighter("wall_$it", EnemyArchetype.WALL_ARCHER, elevated = true) }
        val queued = listOf(fighter("queued", EnemyArchetype.HOUSECARL).apply { isCombatInactive = true })
        val melee = fighter("melee", EnemyArchetype.HOUSECARL)
        val state = SiegeState(100f, 100f, wall.map { it.id }.toSet(), queued.map { it.id }.toSet())

        SiegeRules.breakGate(state, wall + queued + melee)

        assertEquals(2, wall.count { it.climbState == ClimbState.CLIMBING_DOWN })
        assertFalse(queued.single().isCombatInactive)
        assertFalse(melee.elevated)
        assertEquals(ClimbState.NONE, melee.climbState)
    }

    @Test
    fun `2 ladder exists only after gate breaks`() {
        val state = SiegeState(10f, 10f, emptySet(), emptySet())
        assertFalse(state.ladderSpawned)
        SiegeRules.breakGate(state, emptyList())
        assertTrue(state.ladderSpawned)
    }

    @Test
    fun `3 player mounts only while a living parapet enemy remains`() {
        val player = fighter("player", player = true)
        val wall = fighter("wall", EnemyArchetype.WALL_ARCHER, elevated = true)
        val state = SiegeState(0f, 10f, setOf(wall.id), emptySet(), ladderSpawned = true, gateBroken = true)
        assertTrue(SiegeRules.beginClimbUp(state, player, listOf(wall)))
        player.climbState = ClimbState.NONE
        wall.isDead = true
        assertFalse(SiegeRules.beginClimbUp(state, player, listOf(wall)))
    }

    /**
     * The bug: the player stopped using his own siege ladders. His retinue climbed and he stayed at
     * the bottom, because his branch demanded a clear field and theirs did not. Both now ask one
     * predicate, so if they ever diverge again this fails rather than shipping.
     */
    @Test
    fun `3a player and retinue answer the ladder identically`() {
        val wall = fighter("wall", EnemyArchetype.WALL_ARCHER, elevated = true)
        val state = SiegeState(50f, 100f, setOf(wall.id), emptySet(), siegeLadders = true)
        val atWall = { id: String, isPlayer: Boolean ->
            fighter(id, player = isPlayer).apply { posX = GameViewModel.SIEGE_GATE_X - 20f }
        }
        val player = atWall("player", true)
        val squire = atWall("squire", true)

        // Gate still standing, both stood at the wall, neither with anything left to hit down here.
        assertTrue(SiegeRules.mayScaleWall(state, player, hasReachableTarget = false))
        assertTrue(SiegeRules.mayScaleWall(state, squire, hasReachableTarget = false))

        // A foe he can actually close with keeps him on the ground — and it must keep BOTH of them
        // on the ground, which is the half that was broken.
        assertFalse(SiegeRules.mayScaleWall(state, player, hasReachableTarget = true))
        assertFalse(SiegeRules.mayScaleWall(state, squire, hasReachableTarget = true))

        // No climbing from the beach.
        player.posX = GameViewModel.SIEGE_GATE_X - 400f
        assertFalse(SiegeRules.mayScaleWall(state, player, hasReachableTarget = false))

        // And no ladder at all without the reward or a broken gate.
        val noLadder = SiegeState(50f, 100f, setOf(wall.id), emptySet())
        assertFalse(SiegeRules.mayScaleWall(noLadder, squire, hasReachableTarget = false))
    }

    @Test
    fun `4 elevated player automatically descends after last parapet death`() {
        val player = fighter("player", player = true).apply { elevated = true }
        val wall = fighter("wall", EnemyArchetype.WALL_ARCHER, elevated = true).apply { isDead = true }
        val state = SiegeState(0f, 10f, setOf(wall.id), emptySet(), ladderSpawned = true, gateBroken = true)
        SiegeRules.reconcileParapet(state, player, listOf(wall))
        assertEquals(ClimbState.CLIMBING_DOWN, player.climbState)
    }

    @Test
    fun `5 empty parapet makes ladder inert`() {
        val player = fighter("player", player = true)
        val state = SiegeState(0f, 10f, emptySet(), emptySet(), ladderSpawned = true, gateBroken = true)
        assertFalse(SiegeRules.ladderActive(state, emptyList()))
        assertFalse(SiegeRules.beginClimbUp(state, player, emptyList()))
    }

    @Test
    fun `6 climb is uninterruptible invulnerable and always completes`() {
        val player = fighter("player", player = true)
        val foe = fighter("foe", elevated = true)
        val state = SiegeState(0f, 10f, setOf(foe.id), emptySet(), ladderSpawned = true, gateBroken = true)
        assertTrue(SiegeRules.beginClimbUp(state, player, listOf(foe)))
        val hp = player.hp
        CombatEngine(NoOpBattleContext(player, listOf(foe))).applyFlatDamage(100f, player)
        assertEquals(hp, player.hp, 0f)
        SiegeRules.tickClimbs(listOf(player), SiegeRules.CLIMB_SECONDS)
        assertEquals(ClimbState.NONE, player.climbState)
        assertTrue(player.elevated)
    }

    @Test
    fun `7 representative live siege states reach victory or a legal transition within bounds`() {
        // Gate intact with a queued defender: breaking it releases the queue and starts a descent.
        val gatePlayer = fighter("gate_player", player = true)
        val wall = fighter("gate_wall", EnemyArchetype.WALL_ARCHER, elevated = true)
        val queued = fighter("gate_queue", EnemyArchetype.HOUSECARL).apply { isCombatInactive = true }
        val gateState = SiegeState(10f, 10f, setOf(wall.id), setOf(queued.id))
        // No single blow one-shots the gate (per-hit cap), so batter it down over several hits.
        repeat(5) { SiegeRules.damageGate(gateState, 10f, listOf(wall, queued)) }
        assertTrue(gateState.gateBroken)
        assertFalse(queued.isCombatInactive)
        SiegeRules.tickClimbs(listOf(wall), SiegeRules.CLIMB_SECONDS)
        val gateFoes = listOf(wall, queued)
        val gateEngine = CombatEngine(NoOpBattleContext(gatePlayer, gateFoes))
        gateFoes.forEach { gateEngine.applyFlatDamage(500f, it, isPlayerSource = true) }
        assertTrue(SiegeRules.isBattleWon(gateFoes))

        // Gate broken with a live parapet: the player climbs, makes the last kill, then descends.
        val climbingPlayer = fighter("climbing_player", player = true)
        val lastWall = fighter("last_wall", EnemyArchetype.WALL_ARCHER, elevated = true)
        val parapetState = SiegeState(
            0f, 10f, setOf(lastWall.id), emptySet(), ladderSpawned = true, gateBroken = true
        )
        assertTrue(SiegeRules.beginClimbUp(parapetState, climbingPlayer, listOf(lastWall)))
        SiegeRules.tickClimb(climbingPlayer, SiegeRules.CLIMB_SECONDS)
        CombatEngine(NoOpBattleContext(climbingPlayer, listOf(lastWall)))
            .applyFlatDamage(500f, lastWall, isPlayerSource = true)
        SiegeRules.reconcileParapet(parapetState, climbingPlayer, listOf(lastWall))
        assertEquals(ClimbState.CLIMBING_DOWN, climbingPlayer.climbState)
        SiegeRules.tickClimb(climbingPlayer, SiegeRules.CLIMB_SECONDS)
        assertFalse(climbingPlayer.elevated)
        assertTrue(SiegeRules.isBattleWon(listOf(lastWall)))

        // Empty parapet: the ladder is inert and the remaining live ground foe is terminally reachable.
        val emptyPlayer = fighter("empty_player", player = true)
        val ground = fighter("ground")
        val emptyState = SiegeState(0f, 10f, emptySet(), emptySet(), true, true)
        assertFalse(SiegeRules.beginClimbUp(emptyState, emptyPlayer, listOf(ground)))
        CombatEngine(NoOpBattleContext(emptyPlayer, listOf(ground)))
            .applyFlatDamage(500f, ground, isPlayerSource = true)
        assertTrue(SiegeRules.isBattleWon(listOf(ground)))
    }

    @Test
    fun `siege cadence is deterministic six to eight eligible levels apart and excludes bosses`() {
        val first = SiegeSchedule.levels(8675309L, 100)
        assertEquals(first, SiegeSchedule.levels(8675309L, 100))
        assertTrue(first.none { BossSchedule.forLevel(it) != null })
        val eligible = (5..100).filter { BossSchedule.forLevel(it) == null }
        first.zipWithNext().forEach { (a, b) ->
            assertTrue(eligible.indexOf(b) - eligible.indexOf(a) in 6..8)
        }
    }

    @Test
    fun `runtime siege queries are prefix stable when asked sequentially`() {
        val seed = 8675309L
        val sequential = (1..100).filter { SiegeSchedule.isSiegeLevel(seed, it) }
        assertEquals(SiegeSchedule.levels(seed, 100), sequential)
    }
}
