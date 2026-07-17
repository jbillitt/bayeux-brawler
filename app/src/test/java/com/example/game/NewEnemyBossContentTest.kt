package com.example.game

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEnemyBossContentTest {
    private fun fighter(player: Boolean = false, x: Float = 0f) = FighterState(
        id = if (player) "player" else "foe", name = "fighter", isPlayer = player,
        maxHp = 500f, hp = 500f, weaponHead = GameData.WeaponHead.SWORD,
        weaponHandle = GameData.WeaponHandle.MEDIUM, shield = GameData.Shield.NONE,
        armor = GameData.ArmorPiece.CHAINMAIL, headgear = GameData.HeadgearPiece.CONICAL,
        posX = x, targetX = x, hairColor = Color.Black
    )

    @Test
    fun `all new archetypes create complete valid kits across level bands`() {
        val newTypes = listOf(
            EnemyArchetype.WALL_ARCHER, EnemyArchetype.TORCH_BEARER,
            EnemyArchetype.DANE_AXE_EXECUTIONER, EnemyArchetype.MONK_MILITIA,
            EnemyArchetype.NORMAN_LOYALIST
        )
        for (level in listOf(5, 15, 31, 60)) for (type in newTypes) {
            val enemy = EnemyFactory.createArchetype(type, 0, level)
            assertEquals(type, enemy.archetype)
            assertTrue(enemy.hp > 0f)
            assertTrue(enemy.weaponHead.id.isNotBlank())
            assertTrue(enemy.armor.id.isNotBlank())
        }
    }

    @Test
    fun `ignite applies ticks expires and cannot stack`() {
        val victim = fighter()
        val engine = CombatEngine(NoOpBattleContext(null, listOf(victim)))
        engine.applyIgnite(victim)
        val original = victim.igniteDuration
        engine.applyIgnite(victim)
        assertEquals(original, victim.igniteDuration, 0f)
        repeat(50) { engine.updateFighter(victim, null, 0.1f) }
        assertTrue(victim.hp < victim.maxHp)
        assertEquals(0f, victim.igniteDuration, 0.01f)
    }

    @Test
    fun `torch bearer fires an igniting torch`() {
        val torch = EnemyFactory.createArchetype(EnemyArchetype.TORCH_BEARER, 0, 10)
        val player = fighter(player = true, x = 100f)
        val context = NoOpBattleContext(player, listOf(torch))
        val engine = CombatEngine(context)
        torch.isAttacking = true
        torch.swingProgress = 0.99f
        engine.updateFighter(torch, player, 0.1f)
        assertTrue(context.spawnedProjectiles.single().isIgniting)
        assertEquals(ProjectileType.TORCH, context.spawnedProjectiles.single().type)
    }

    @Test
    fun `monk aura buffs only nearby defenders`() {
        val monk = EnemyFactory.createArchetype(EnemyArchetype.MONK_MILITIA, 0, 10).apply { posX = 0f }
        val near = fighter(x = CombatEngine.MONK_AURA_RADIUS_PX - 1f)
        val far = fighter(x = CombatEngine.MONK_AURA_RADIUS_PX + 1f)
        val ally = fighter(player = true, x = 1f)
        val engine = CombatEngine(NoOpBattleContext(ally, listOf(monk, near, far)))
        engine.triggerAttack(near)
        engine.triggerAttack(far)
        engine.triggerAttack(ally)
        assertTrue(near.attackCooldown < far.attackCooldown)
        assertEquals(far.attackCooldown, ally.attackCooldown, 0.001f)
    }

    @Test
    fun `executioner shred lowers armor without directly lowering hp`() {
        val victim = fighter()
        val hp = victim.hp
        val armor = victim.totalArmor
        CombatEngine(NoOpBattleContext(null, listOf(victim))).applyArmorShred(victim)
        assertEquals(hp, victim.hp, 0f)
        assertTrue(victim.totalArmor < armor)
    }

    @Test
    fun `boss schedule maps 1066 trio through level one hundred`() {
        for (level in 1..100) {
            val expected = when {
                level == 10 -> BossType.HAROLD_GODWINSON
                level == 20 -> BossType.HARALD_HARDRADA
                level >= 30 && level % 10 == 0 -> BossType.WILLIAM_THE_BASTARD
                else -> null
            }
            assertEquals(expected, BossSchedule.forLevel(level))
            if (expected != null) assertFalse(SiegeSchedule.isSiegeLevel(42L, level))
        }
    }

    @Test
    fun `William forces throne while other bosses preserve brawl`() {
        assertTrue(BossSchedule.forcesThroneMusic(BossType.WILLIAM_THE_BASTARD))
        assertFalse(BossSchedule.forcesThroneMusic(BossType.HAROLD_GODWINSON))
        assertFalse(BossSchedule.forcesThroneMusic(BossType.HARALD_HARDRADA))
    }

    @Test
    fun `Harold eye crit multiplies player ranged damage only and consumes window`() {
        fun damage(projectile: Boolean): Float {
            val harold = EnemyFactory.createBoss(BossType.HAROLD_GODWINSON, 10).apply {
                shield = GameData.Shield.NONE
                armor = GameData.ArmorPiece.BARE
                arrowEyeCritWindow = 2f
            }
            val engine = CombatEngine(NoOpBattleContext(null, listOf(harold)))
            val before = harold.hp
            if (projectile) {
                engine.applyProjectileDamage(
                    Projectile("arrow", true, 0f, 0f, 1f, 0f, 20f, 0f, 0f, ProjectileType.ARROW),
                    harold
                )
                assertEquals(0f, harold.arrowEyeCritWindow, 0f)
            } else {
                engine.applyFlatDamage(20f, harold, isPlayerSource = true)
            }
            return before - harold.hp
        }
        assertTrue(damage(projectile = true) > damage(projectile = false))
    }

    @Test
    fun `boss factories supply signature retinues and headlines`() {
        BossType.entries.forEach { boss ->
            val encounter = EnemyFactory.createBossEncounter(boss, if (boss == BossType.WILLIAM_THE_BASTARD) 40 else boss.level)
            assertNotNull(encounter.firstOrNull { it.bossType == boss })
            assertTrue(encounter.any { it.isBossRetinue })
            assertTrue(FlavourText.latinHeadline(1L, boss.level, boss).contains(boss.latinName))
        }
        assertNull(BossSchedule.forLevel(11))
    }

    @Test
    fun `monk militia wears its archetype without war priest behavior`() {
        val monk = EnemyFactory.createArchetype(EnemyArchetype.MONK_MILITIA, 0, 10)
        assertEquals(EnemyArchetype.MONK_MILITIA, monk.archetype)
        assertFalse(monk.isWarPriest)
        assertFalse(monk.isRanged)
    }

    @Test
    fun `culling margin grows for reach size and mounts`() {
        val normal = fighter()
        val longBoss = normal.copy(
            size = 1.75f,
            weaponHead = GameData.WeaponHead.PIKE,
            weaponHandle = GameData.WeaponHandle.PIKE_HANDLE,
            handleExtensionCount = 3,
            isMounted = true
        )
        assertTrue(fighterCullMargin(longBoss, 1f) > fighterCullMargin(normal, 1f))
        assertTrue(fighterCullMargin(longBoss, 2f) > fighterCullMargin(longBoss, 1f))
    }

    @Test
    fun `throne battle separates rear bearers from foreground actors`() {
        val rear = fighter().copy(id = "rear", pallbearerIndex = 2)
        val front = fighter().copy(id = "front", pallbearerIndex = 0)
        val foe = fighter().copy(id = "foe", pallbearerIndex = -1)
        val (behindLord, afterLord) = splitThroneBattleActors(listOf(front, rear, foe))
        assertEquals(listOf("rear"), behindLord.map { it.id })
        assertEquals(listOf("front", "foe"), afterLord.map { it.id })
    }
}

internal class NoOpBattleContext(
    override val player: FighterState?,
    override val enemies: List<FighterState>
) : BattleContext {
    override val levelWidth = 2500f
    override val unlockedAncillaries = emptyList<Ancillary>()
    val spawnedProjectiles = mutableListOf<Projectile>()
    override fun spawnProjectile(p: Projectile) { spawnedProjectiles += p }
    override fun sound(type: SoundType) {}
    override fun popup(text: String, x: Float, y: Float, color: Color) {}
    override fun bloodParticles(x: Float, y: Float, count: Int) {}
    override fun particle(p: BloodParticle) {}
    override fun screenshake(amount: Float) {}
    override fun enemyKilled() {}
}
