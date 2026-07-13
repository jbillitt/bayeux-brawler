package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatEngineTest {

    private class FakeContext : BattleContext {
        override var player: FighterState? = null
        override var enemies: List<FighterState> = emptyList()
        override val levelWidth = 1500f
        override val unlockedAncillaries = emptySet<Ancillary>()
        val projectiles = mutableListOf<Projectile>()
        val popups = mutableListOf<String>()
        var kills = 0
        override fun spawnProjectile(p: Projectile) { projectiles.add(p) }
        override fun sound(type: SoundType) {}
        override fun popup(text: String, x: Float, y: Float, color: Color) { popups.add(text) }
        override fun bloodParticles(x: Float, y: Float, count: Int) {}
        override fun particle(p: BloodParticle) {}
        override fun screenshake(amount: Float) {}
        override fun enemyKilled() { kills++ }
    }

    private fun fighter(
        head: String = "head_sword", handle: String = "handle_medium",
        isPlayer: Boolean = false, posX: Float = 0f, size: Float = 1.0f
    ) = FighterState(
        id = "f_$head", name = "T", isPlayer = isPlayer, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
        posX = posX, targetX = posX, size = size, hairColor = Color.Black, hairStyle = "short"
    )

    @Test
    fun slingersNeverRollWrestlingMoves() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val slinger = fighter(head = "head_slingshot", handle = "handle_fists")
        repeat(200) {
            engine.triggerAttack(slinger)
            assertNull("slinger rolled ${slinger.activeWrestlingMove}", slinger.activeWrestlingMove)
            slinger.isAttacking = false
        }
    }

    @Test
    fun brawlersDoRollWrestlingMoves() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val brawler = fighter(head = "head_bare", handle = "handle_fists").apply { isDualWielding = true }
        var rolled = false
        repeat(200) {
            engine.triggerAttack(brawler)
            if (brawler.activeWrestlingMove != null) rolled = true
            brawler.isAttacking = false
        }
        assertTrue("brawler never rolled a wrestling move in 200 attacks", rolled)
    }

    @Test
    fun queuedActionsFireAfterDelayAndClearOnReset() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        // Dual-wield ranged strike queues a 2nd shot at +160ms
        val archer = fighter(head = "head_bow", handle = "handle_fists", isPlayer = true).apply { isDualWielding = true }
        val target = fighter(posX = 100f)
        ctx.enemies = listOf(target)
        archer.isAttacking = true
        archer.swingProgress = 0.99f
        engine.updateFighter(archer, target, 0.033f) // crosses strike threshold -> fires
        assertEquals(1, ctx.projectiles.size)
        engine.tick(0.1f) // not yet due
        assertEquals(1, ctx.projectiles.size)
        engine.tick(0.1f) // 0.2s elapsed > 0.16s
        assertEquals(2, ctx.projectiles.size)
    }

    @Test
    fun queuedShotSkippedIfAttackerDies() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val archer = fighter(head = "head_bow", handle = "handle_fists", isPlayer = true).apply { isDualWielding = true }
        val target = fighter(posX = 100f)
        ctx.enemies = listOf(target)
        archer.isAttacking = true
        archer.swingProgress = 0.99f
        engine.updateFighter(archer, target, 0.033f)
        assertEquals(1, ctx.projectiles.size)
        archer.isDead = true
        engine.tick(0.3f)
        assertEquals("dead archer fired from the grave", 1, ctx.projectiles.size)
    }

    @Test
    fun flatDamageKillsAndCountsForPlayer() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val victim = fighter()
        ctx.enemies = listOf(victim)
        engine.applyFlatDamage(500f, victim, isPlayerSource = true)
        assertTrue(victim.isDying)
        assertEquals(1, ctx.kills)
        // Already-dying fighters take no further damage
        val hpAfter = victim.hp
        engine.applyFlatDamage(500f, victim, isPlayerSource = true)
        assertEquals(hpAfter, victim.hp, 0f)
        assertEquals(1, ctx.kills)
    }

    @Test
    fun trojanHorseRollsPastFoesThenBursts() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val horse = fighter(head = "head_bare", handle = "handle_fists", isPlayer = true, posX = 200f)
            .let { it.copy(id = "trojan_horse") }
        val foe = fighter(posX = 400f)
        ctx.enemies = listOf(foe, horse)
        val startX = horse.posX
        engine.updateFighter(horse, foe, 0.033f)
        assertTrue("horse should roll forward", horse.posX > startX)
        assertFalse(horse.isDying)
        // Move it past the last foe -> bursts
        horse.posX = 600f
        engine.updateFighter(horse, foe, 0.033f)
        assertTrue("horse should burst once behind all foes", horse.isDying)
    }

    @Test
    fun fistsPlayerClosesOnMeleeEnemyOnGround() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val player = fighter(head = "head_bare", handle = "handle_fists", isPlayer = true, posX = 200f)
        // Small, quick, long-reach melee enemy: its kiting retreat outruns the player's
        // approach, so a fists player can never close unless melee enemies hold ground.
        val enemy = fighter(head = "head_spear", handle = "handle_pike_long", posX = 300f, size = 0.5f)
            .copy(speedBoost = 1.0f)
        ctx.player = player
        ctx.enemies = listOf(enemy)
        // Drive both fighters at each other for a few simulated seconds, like the real battle loop.
        // Track the closest they get: combat knockback can bump them apart again after contact,
        // so what matters is whether the pursuer ever reaches melee range at all.
        val reachPixels = player.reach * 40f + 40f
        var minDist = abs(player.posX - enemy.posX)
        repeat(120) {
            engine.updateFighter(player, enemy, 0.033f)
            engine.updateFighter(enemy, player, 0.033f)
            minDist = minOf(minDist, abs(player.posX - enemy.posX))
        }
        assertTrue("fists player should have closed to melee reach ($reachPixels) but nearest was $minDist", minDist <= reachPixels)
    }

    @Test
    fun throneModeFistsPlayerReachesAdjacentEnemy() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val player = fighter(head = "head_bare", handle = "handle_fists", isPlayer = true, posX = 0f).apply {
            isLord = true
            isMounted = true
            mountHp = 100f
        }
        val enemy = fighter(head = "head_sword", handle = "handle_medium", posX = 300f)
        ctx.player = player
        ctx.enemies = listOf(enemy)
        enemy.crumpleDuration = 100f
        repeat(180) {
            engine.updateFighter(player, enemy, 0.033f)
            engine.updateFighter(enemy, player, 0.033f)
        }
        val reachPixels = player.reach * 40f + 40f
        val dist = abs(player.posX - enemy.posX)
        assertTrue("throne-mode lord should have closed to melee reach ($reachPixels) but dist=$dist", dist <= reachPixels)
        assertTrue("throne-mode fists lord should have landed punches", enemy.hp < enemy.maxHp || enemy.isDying)
    }

    @Test
    fun fistsHitHasAChanceToInterruptDefenderSwing() {
        var interrupted = false
        repeat(300) {
            val ctx = FakeContext()
            val engine = CombatEngine(ctx)
            val attacker = fighter(head = "head_bare", handle = "handle_fists", isPlayer = true, posX = 0f)
            val defender = fighter(posX = 10f).apply {
                isAttacking = true
                swingProgress = 0.4f
            }
            ctx.player = attacker
            ctx.enemies = listOf(defender)
            engine.triggerAttack(attacker)
            if (attacker.activeWrestlingMove == null) { // only the plain punch path lands via meleeSweep
                attacker.swingProgress = 0.99f
                engine.updateFighter(attacker, defender, 0.5f) // big dt: guarantees crossing the strike threshold
                if (!defender.isAttacking && defender.swingProgress == 0f && defender.attackCooldown >= 0.4f) {
                    interrupted = true
                }
            }
        }
        assertTrue("fists never interrupted a defender's swing across many attempts", interrupted)
    }

    @Test
    fun chariotMeleeClosesToHitbox() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val chariotPlayer = fighter(head = "head_sword", handle = "handle_medium", isPlayer = true, posX = 0f).apply { isMounted = true }
        val enemy = fighter(posX = 500f)
        ctx.player = chariotPlayer
        ctx.enemies = listOf(enemy)
        
        // Force the enemy to stand still
        enemy.crumpleDuration = 100f
        
        repeat(300) { engine.updateFighter(chariotPlayer, enemy, 0.1f) }
        
        val dist = kotlin.math.abs(chariotPlayer.posX - enemy.posX)
        val reachPixels = chariotPlayer.reach * 40f + 40f
        val mountReachPixels = 60f
        assertTrue("Chariot melee player stopped too far away: dist=$dist, hit range=${reachPixels - mountReachPixels}", dist <= reachPixels - mountReachPixels + 1f)
    }

    @Test
    fun chariotRangedKeepsDistance() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val chariotArcher = fighter(head = "head_bow", handle = "handle_fists", isPlayer = true, posX = 0f).apply { isMounted = true }
        val enemy = fighter(posX = 800f)
        ctx.player = chariotArcher
        ctx.enemies = listOf(enemy)
        
        // Force the enemy to stand still
        enemy.crumpleDuration = 100f
        
        repeat(300) { engine.updateFighter(chariotArcher, enemy, 0.1f) }
        
        val dist = kotlin.math.abs(chariotArcher.posX - enemy.posX)
        val reachPixels = chariotArcher.reach * 40f + 40f
        val rangeMult = 0.8f
        assertTrue("Chariot ranged player didn't hold distance: dist=$dist, optimal=${reachPixels * rangeMult}", dist >= reachPixels * rangeMult - 5f)
    }

    @Test
    fun daggerWielderClosesToHitboxHugeEnemy() {
        // dagger vs size 1.45f enemy
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        
        // test tiny player
        val tinyDagger = fighter(head = "head_sword", handle = "handle_dagger", isPlayer = true, posX = 0f, size = 0.8f)
        val hugeEnemy1 = fighter(posX = 300f, size = 1.45f)
        hugeEnemy1.crumpleDuration = 100f // stand still
        
        ctx.player = tinyDagger
        ctx.enemies = listOf(hugeEnemy1)
        repeat(300) { engine.updateFighter(tinyDagger, hugeEnemy1, 0.1f) }
        
        val dist1 = kotlin.math.abs(tinyDagger.posX - hugeEnemy1.posX)
        val reachPixels1 = tinyDagger.reach * 40f + 40f
        assertTrue("Tiny dagger player stopped too far away: dist=$dist1, reachPixels=$reachPixels1", dist1 <= reachPixels1)

        // test huge player
        val hugeDagger = fighter(head = "head_sword", handle = "handle_dagger", isPlayer = true, posX = 0f, size = 1.45f)
        val hugeEnemy2 = fighter(posX = 300f, size = 1.45f)
        hugeEnemy2.crumpleDuration = 100f // stand still
        
        ctx.player = hugeDagger
        ctx.enemies = listOf(hugeEnemy2)
        repeat(300) { engine.updateFighter(hugeDagger, hugeEnemy2, 0.1f) }
        
        val dist2 = kotlin.math.abs(hugeDagger.posX - hugeEnemy2.posX)
        val reachPixels2 = hugeDagger.reach * 40f + 40f
        assertTrue("Huge dagger player stopped too far away: dist=$dist2, reachPixels=$reachPixels2", dist2 <= reachPixels2)
    }
}
