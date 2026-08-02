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
        override val unlockedAncillaries = emptyList<Ancillary>()
        override var hasShieldbreaker: Boolean = false
        override var hasArmorPiercing: Boolean = false
        val projectiles = mutableListOf<Projectile>()
        val particles = mutableListOf<BloodParticle>()
        val popups = mutableListOf<String>()
        var kills = 0
        override fun spawnProjectile(p: Projectile) { projectiles.add(p) }
        override fun sound(type: SoundType) {}
        override fun popup(text: String, x: Float, y: Float, color: Color) { popups.add(text) }
        override fun bloodParticles(x: Float, y: Float, count: Int) {}
        override fun particle(p: BloodParticle) { particles.add(p) }
        override fun screenshake(amount: Float) {}
        override fun enemyKilled() { kills++ }
    }

    private fun fighter(
        head: String = "head_sword", handle: String = "handle_medium",
        isPlayer: Boolean = false, posX: Float = 0f, size: Float = 1.0f
    ) = FighterState(
        id = FighterId("f_$head"), name = "T", isPlayer = isPlayer, maxHp = 100f, hp = 100f,
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
    fun frontlineAllyOutrunsASpeedBoostedPlayer() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)

        val player = fighter(isPlayer = true, posX = 500f).copy(speedBoost = 1.5f)
        val dog = fighter(isPlayer = true, posX = 460f)
            .copy(id = FighterId("wardog#0"), weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" })
        val foe = fighter(posX = 1200f)
        ctx.player = player
        ctx.enemies = listOf(dog, foe)

        val dogStart = dog.posX
        val playerStart = player.posX
        repeat(60) {
            engine.updateFighter(dog, foe, 1f / 60f)
            engine.updateFighter(player, foe, 1f / 60f)
        }

        val dogMoved = dog.posX - dogStart
        val playerMoved = player.posX - playerStart
        assertTrue(
            "dog advanced $dogMoved, player advanced $playerMoved â€” frontline must lead",
            dogMoved > playerMoved
        )
    }

    /**
     * Drive one complete melee swing to the point of impact. `engine.tick` only drains queued
     * follow-ups â€” a strike lands when `updateFighter` carries swingProgress past the threshold.
     *
     * Callers repeat this: `meleeSweep` rolls a per-target dodge chance off the target's moveSpeed
     * (~10% for an unencumbered ally), so a single swing landing on everyone is not guaranteed.
     */
    private fun swing(engine: CombatEngine, attacker: FighterState, defender: FighterState) {
        attacker.isAttacking = true
        attacker.hasLandedStrike = false
        attacker.swingProgress = 0.99f
        engine.updateFighter(attacker, defender, 1f / 60f)
        engine.tick(1f / 60f)
    }

    @Test
    fun aBossSwingCleavesEveryAllyInReach() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)

        val boss = fighter(head = "head_maul", handle = "handle_iron", posX = 500f)
            .copy(bossType = BossType.HAROLD_GODWINSON)
        val allies = listOf(490f, 515f, 540f).mapIndexed { i, x ->
            fighter(isPlayer = true, posX = x).copy(id = FighterId("ally_$i"))
        }
        ctx.player = allies.first()
        ctx.enemies = allies + boss

        repeat(10) { swing(engine, boss, allies.first()) }

        val hurt = allies.count { it.hp < it.maxHp }
        assertEquals("a boss must strike every ally inside its reach", 3, hurt)
    }

    @Test
    fun anOrdinaryFighterStillStrikesOnlyItsTarget() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)

        val saxon = fighter(head = "head_maul", handle = "handle_iron", posX = 500f)
        val allies = listOf(490f, 515f, 540f).mapIndexed { i, x ->
            fighter(isPlayer = true, posX = x).copy(id = FighterId("ally_$i"))
        }
        ctx.player = allies.first()
        ctx.enemies = allies + saxon

        repeat(10) { swing(engine, saxon, allies.first()) }

        // The guard that keeps the cleave scoped to bosses. Asserting the defender IS hurt as well
        // as the bystanders being untouched, so the test cannot pass by the swing doing nothing.
        assertTrue("the Saxon never landed on his own target", allies[0].hp < allies[0].maxHp)
        assertEquals("a common Saxon cleaved a bystander", allies[1].maxHp, allies[1].hp, 0f)
        assertEquals("a common Saxon cleaved a bystander", allies[2].maxHp, allies[2].hp, 0f)
    }

    @Test
    fun theNailStuddedPlankLeavesAWoundThatGoesBad() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)

        val attacker = fighter(head = "head_club", handle = "handle_plank", posX = 500f)
        val victim = fighter(isPlayer = true, posX = 520f).copy(id = FighterId("victim"))
        ctx.player = victim
        ctx.enemies = listOf(victim, attacker)

        repeat(10) { swing(engine, attacker, victim) }

        assertTrue("the plank must poison on hit", victim.poisonDuration > 0f)
    }

    @Test
    fun anOrdinaryHaftDoesNotPoison() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)

        val attacker = fighter(head = "head_club", handle = "handle_medium", posX = 500f)
        val victim = fighter(isPlayer = true, posX = 520f).copy(id = FighterId("victim"))
        ctx.player = victim
        ctx.enemies = listOf(victim, attacker)

        repeat(10) { swing(engine, attacker, victim) }

        // Proves the swings actually landed, so the zero poison means something.
        assertTrue("the swings never connected at all", victim.hp < victim.maxHp)
        assertEquals(0f, victim.poisonDuration, 0.001f)
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
            // The real spawn id carries a "#i" copy suffix â€” use it here so an exact-match
            // check anywhere in the horse's logic fails this test instead of shipping.
            .let { it.copy(id = FighterId("trojan_horse#0")) }
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
    fun followersSprintToCatchThePlayerButPallbearersStayLocked() {
        val target = fighter(posX = 1300f)

        val farContext = FakeContext()
        val farPlayer = fighter(isPlayer = true, posX = 700f)
        val farFollower = fighter(isPlayer = true, posX = 100f)
        farContext.player = farPlayer
        farContext.enemies = listOf(target)
        val farStart = farFollower.posX
        CombatEngine(farContext).updateFighter(farFollower, target, 0.1f)
        assertTrue(
            "far follower did not hustle",
            farFollower.posX - farStart > farFollower.moveSpeed * 0.1f * 2f
        )

        val nearContext = FakeContext()
        val nearPlayer = fighter(isPlayer = true, posX = 200f)
        val nearFollower = fighter(isPlayer = true, posX = 100f)
        nearContext.player = nearPlayer
        nearContext.enemies = listOf(target)
        val nearStart = nearFollower.posX
        CombatEngine(nearContext).updateFighter(nearFollower, target, 0.1f)
        assertEquals(nearFollower.moveSpeed * 0.1f, nearFollower.posX - nearStart, 0.01f)

        val throneContext = FakeContext()
        val lord = fighter(isPlayer = true, posX = 600f)
        val bearer = fighter(isPlayer = true, posX = 0f).apply { pallbearerIndex = 0 }
        throneContext.player = lord
        throneContext.enemies = listOf(target, bearer)
        CombatEngine(throneContext).updateFighter(bearer, target, 0.1f)
        assertEquals(lord.posX + 45f, bearer.posX, 0.01f)
    }

    @Test
    fun enemiesStayGroundedWhenSwattingTheRaven() {
        val context = FakeContext()
        val attacker = fighter(posX = 100f).apply {
            isAttacking = true
            swingProgress = 0.49f
            visualOffsetY = -60f
        }
        val raven = fighter(isPlayer = true, posX = 140f).copy(id = FighterId("raven"), speedBoost = -1f)
        context.player = raven
        context.enemies = listOf(attacker)
        val hpBefore = raven.hp

        CombatEngine(context).updateFighter(attacker, raven, 0.1f)

        assertTrue("attacker leapt toward the airborne raven", attacker.visualOffsetY >= 0f)
        assertTrue("raven stopped being a legal melee target", raven.hp < hpBefore)
    }

    @Test
    fun hagMudPoisonsAndSplatsGreenOnImpact() {
        val context = FakeContext()
        val defender = fighter().copy(speedBoost = -1f)
        val mud = Projectile(
            id = "hag_mud_test",
            isPlayerOwned = true,
            posX = defender.posX,
            posY = 140f,
            velocityX = 100f,
            velocityY = 0f,
            damage = 5f,
            pierce = 0f,
            blunt = 0f,
            type = ProjectileType.ROCK
        )

        CombatEngine(context).applyProjectileDamage(mud, defender)

        assertEquals(3f, defender.poisonDuration, 0f)
        assertTrue("mud impact had no immediate green splat", context.particles.size >= 6)
        assertTrue(context.particles.all { it.color == Color(0xFF2E7D32) })
    }

    @Test
    fun playerOnlyHaroldEyeCritIsNotConsumedByAnAncillaryArrow() {
        val context = FakeContext()
        val player = fighter(head = "head_bow", handle = "handle_fists", isPlayer = true)
        context.player = player
        val harold = EnemyFactory.createBoss(BossType.HAROLD_GODWINSON, 10)
            .copy(speedBoost = -1f).apply {
            shield = GameData.Shield.NONE
            shieldHp = 0f
            arrowEyeCritWindow = 2.5f
        }
        context.enemies = listOf(harold)
        val engine = CombatEngine(context)
        fun arrow(sourceId: FighterId?) = Projectile(
            id = "arrow_${sourceId?.raw}",
            isPlayerOwned = true,
            posX = harold.posX,
            posY = 150f,
            velocityX = 300f,
            velocityY = 0f,
            damage = 10f,
            pierce = 10f,
            blunt = 0f,
            type = ProjectileType.ARROW,
            sourceFighterId = sourceId
        )

        // An allied archer's arrow â€” a real follower fighter, not the player.
        engine.applyProjectileDamage(arrow(FighterId("archer#0")), harold)
        assertEquals(2.5f, harold.arrowEyeCritWindow, 0f)
        engine.applyProjectileDamage(arrow(player.id), harold)
        assertEquals(0f, harold.arrowEyeCritWindow, 0f)
    }

    @Test
    fun heavyMeleeCleaveSkipsWrongElevationQueuedAndClimbingDefenders() {
        val context = FakeContext()
        val engine = CombatEngine(context)
        val attacker = fighter(
            head = "head_claymore", handle = "handle_iron", isPlayer = true, posX = 0f
        )
        val primary = fighter(posX = 30f)
        val elevated = fighter(posX = 35f).apply { this.elevated = true }
        val queued = fighter(posX = 40f).apply { isCombatInactive = true }
        val climbing = fighter(posX = 45f).apply { climbState = ClimbState.CLIMBING_DOWN }
        context.player = attacker
        context.enemies = listOf(primary, elevated, queued, climbing)
        val strike = CombatEngine::class.java.getDeclaredMethod(
            "performStrike", FighterState::class.java, FighterState::class.java
        ).apply { isAccessible = true }

        repeat(20) { strike.invoke(engine, attacker, primary) }

        assertTrue(primary.hp < primary.maxHp || primary.isDying)
        assertEquals(elevated.maxHp, elevated.hp, 0f)
        assertEquals(queued.maxHp, queued.hp, 0f)
        assertEquals(climbing.maxHp, climbing.hp, 0f)
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
        val reachPixels = player.reachPixels
        var minDist = abs(player.posX - enemy.posX)
        repeat(120) {
            engine.updateFighter(player, enemy, 0.033f)
            engine.updateFighter(enemy, player, 0.033f)
            minDist = minOf(minDist, abs(player.posX - enemy.posX))
        }
        assertTrue("fists player should have closed to melee reach ($reachPixels) but nearest was $minDist", minDist <= reachPixels)
    }

    /**
     * An enthroned lord does not swing â€” the retinue fights for him. He still advances, and he only
     * takes up the fight himself, bare-handed, once the throne is smashed out from under him.
     */
    @Test
    fun throneModeLordHoldsHisHandUntilTheThroneFalls() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val player = fighter(head = "head_bare", handle = "handle_fists", isPlayer = true, posX = 0f).apply {
            isLord = true
            isMounted = true
            mountHp = 100f
        }
        // Crumpled for the whole test, so it never hits back: whatever damage lands is the lord's.
        val enemy = fighter(head = "head_sword", handle = "handle_medium", posX = 300f)
        ctx.player = player
        ctx.enemies = listOf(enemy)
        enemy.crumpleDuration = 100f

        repeat(180) {
            engine.updateFighter(player, enemy, 0.033f)
            engine.updateFighter(enemy, player, 0.033f)
        }

        val reachPixels = player.reachPixels
        val dist = abs(player.posX - enemy.posX)
        assertTrue("throne-mode lord should still have closed to melee reach ($reachPixels) but dist=$dist", dist <= reachPixels)
        assertEquals("an enthroned lord should not be swinging himself", enemy.maxHp, enemy.hp, 0.001f)

        // Smash the throne. It absorbs the blow, collapses, and sets him down on his own two feet.
        engine.applyFlatDamage(150f, player)
        assertFalse("the throne should have collapsed", player.isLord)

        repeat(180) {
            engine.updateFighter(player, enemy, 0.033f)
            engine.updateFighter(enemy, player, 0.033f)
        }
        assertTrue("a dethroned lord should fight on bare-handed", enemy.hp < enemy.maxHp || enemy.isDying)
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
        val reachPixels = chariotPlayer.reachPixels
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
        val reachPixels = chariotArcher.reachPixels
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
        val reachPixels1 = tinyDagger.reachPixels
        assertTrue("Tiny dagger player stopped too far away: dist=$dist1, reachPixels=$reachPixels1", dist1 <= reachPixels1)

        // test huge player
        val hugeDagger = fighter(head = "head_sword", handle = "handle_dagger", isPlayer = true, posX = 0f, size = 1.45f)
        val hugeEnemy2 = fighter(posX = 300f, size = 1.45f)
        hugeEnemy2.crumpleDuration = 100f // stand still
        
        ctx.player = hugeDagger
        ctx.enemies = listOf(hugeEnemy2)
        repeat(300) { engine.updateFighter(hugeDagger, hugeEnemy2, 0.1f) }
        
        val dist2 = kotlin.math.abs(hugeDagger.posX - hugeEnemy2.posX)
        val reachPixels2 = hugeDagger.reachPixels
        assertTrue("Huge dagger player stopped too far away: dist=$dist2, reachPixels=$reachPixels2", dist2 <= reachPixels2)
    }

    @Test
    fun warPriestHealsTheWorstHurtOfHisFlockAndNeverAttacks() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val priest = EnemyFactory.warPriest(0, 18).apply { posX = 500f }
        val scratched = fighter(posX = 480f).apply { hp = 90f }
        val dying = fighter(posX = 520f).apply { hp = 20f }
        val player = fighter(isPlayer = true, posX = 100f)
        ctx.player = player
        ctx.enemies = listOf(priest, scratched, dying)

        repeat(10) { engine.updateFighter(priest, player, 0.1f) } // one second of prayer

        assertEquals("priest healed the wrong man", 20f + CombatEngine.WAR_PRIEST_HEAL_PER_SEC, dying.hp, 0.6f)
        assertEquals("priest healed someone he should not have", 90f, scratched.hp, 0.01f)
        assertFalse("the priest raised a hand", priest.isAttacking)
    }

    @Test
    fun warPriestCannotHealBeyondFullHealthOrAcrossTheField() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val priest = EnemyFactory.warPriest(0, 18).apply { posX = 500f }
        val faraway = fighter(posX = 500f + CombatEngine.WAR_PRIEST_RADIUS_PX + 100f).apply { hp = 10f }
        ctx.enemies = listOf(priest, faraway)
        repeat(10) { engine.updateFighter(priest, null, 0.1f) }
        assertEquals("priest healed a man out of earshot", 10f, faraway.hp, 0.01f)
    }

    @Test
    fun shieldbreakerTriplesShieldDamage() {
        // The shield is made unbreakable on purpose: let it shatter and the damage stops accruing
        // once it is gone, so the better card would score *lower*. Measure the rate, not the total.
        val unbreakable = 1_000_000f
        fun shieldLoss(withCard: Boolean): Float {
            val ctx = FakeContext()
            ctx.hasShieldbreaker = withCard
            val engine = CombatEngine(ctx)
            val player = fighter(head = "head_maul", isPlayer = true, posX = 0f)
            val walled = fighter(posX = 60f)
            walled.shield = GameData.SHIELDS.first { it.id == "shield_tower" }
            walled.shieldHp = unbreakable
            walled.hp = unbreakable
            walled.maxHp = unbreakable
            walled.crumpleDuration = 10_000f // hold still and take it
            // A shield only covers the side its owner faces, so he has to be turned toward the
            // attacker for this test to exercise the block path at all. The fixture defaults to
            // facing right, and the attacker stands to his left.
            walled.facingRight = false
            ctx.player = player
            ctx.enemies = listOf(walled)
            repeat(600) { engine.updateFighter(player, walled, 0.1f) } // blocks are a dice roll: sample a lot
            return unbreakable - walled.shieldHp
        }
        val plain = shieldLoss(withCard = false)
        val broken = shieldLoss(withCard = true)
        assertTrue("shield took no damage at all â€” the test never exercised the block path", plain > 0f)
        assertTrue("shieldbreaker did not splinter harder: plain=$plain broken=$broken", broken > plain * 1.8f)
    }

    @Test
    fun armorPiercingPushesDamageThroughScale() {
        fun hpLost(withCard: Boolean): Float {
            val ctx = FakeContext()
            ctx.hasArmorPiercing = withCard
            val engine = CombatEngine(ctx)
            val player = fighter(head = "head_sword", isPlayer = true, posX = 0f)
            val brute = fighter(posX = 60f)
            brute.armor = GameData.ARMOR_PIECES.first { it.id == "armor_scale" }
            brute.shield = GameData.SHIELDS.first { it.id == "shield_none" }
            brute.hp = 5000f
            brute.maxHp = 5000f
            brute.crumpleDuration = 1000f
            ctx.player = player
            ctx.enemies = listOf(brute)
            repeat(600) { engine.updateFighter(player, brute, 0.1f) }
            return 5000f - brute.hp
        }
        val plain = hpLost(withCard = false)
        val pierced = hpLost(withCard = true)
        assertTrue("no damage landed at all", plain > 0f)
        assertTrue("armour-piercing did not help: plain=$plain pierced=$pierced", pierced > plain * 1.05f)
    }

    @Test
    fun contagiousCarrierInfectsNearbyFoe() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val peasant = fighter(isPlayer = true, posX = 0f).apply { isContagious = true }
        val foe = fighter(posX = CombatEngine.DISEASE_RADIUS_PX - 20f)
        ctx.enemies = listOf(peasant, foe)
        engine.updateFighter(foe, null, 0.016f)
        assertTrue("foe standing in the miasma was not infected", foe.diseaseDuration > 0f)
    }

    @Test
    fun contagionDoesNotReachAcrossTheField() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val peasant = fighter(isPlayer = true, posX = 0f).apply { isContagious = true }
        val foe = fighter(posX = CombatEngine.DISEASE_RADIUS_PX + 200f)
        ctx.enemies = listOf(peasant, foe)
        engine.updateFighter(foe, null, 0.016f)
        assertEquals("foe infected from out of range", 0f, foe.diseaseDuration, 0.001f)
    }

    @Test
    fun freshCorpseStillSpreadsPlague() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val peasant = fighter(isPlayer = true, posX = 0f).apply {
            isContagious = true
            isDead = true
            deathTime = System.currentTimeMillis() // just fell
        }
        val foe = fighter(posX = 30f)
        ctx.enemies = listOf(peasant, foe)
        engine.updateFighter(foe, null, 0.016f)
        assertTrue("fresh plague corpse stopped being contagious", foe.diseaseDuration > 0f)

        // ...but a long-cold one does not
        val coldPeasant = fighter(isPlayer = true, posX = 0f).apply {
            isContagious = true
            isDead = true
            deathTime = System.currentTimeMillis() - 60_000L
        }
        val foe2 = fighter(posX = 30f)
        ctx.enemies = listOf(coldPeasant, foe2)
        engine.updateFighter(foe2, null, 0.016f)
        assertEquals("cold corpse still infecting", 0f, foe2.diseaseDuration, 0.001f)
    }

    @Test
    fun diseaseDealsDamageOverTime() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val sick = fighter(posX = 500f).apply { diseaseDuration = CombatEngine.DISEASE_DURATION }
        ctx.enemies = listOf(sick)
        // 10 seconds of rot â€” damage lands in whole points, so measure over a long enough window
        repeat(100) { engine.updateFighter(sick, null, 0.1f) }
        val lost = 100f - sick.hp
        assertEquals("disease dps off", CombatEngine.DISEASE_DPS * 10f, lost, 1.5f)
    }

    @Test
    fun playerEventuallyCatchesPlagueAndBecomesContagious() {
        val ctx = FakeContext()
        val engine = CombatEngine(ctx)
        val peasant = fighter(isPlayer = true, posX = 0f).apply { isContagious = true }
        val player = fighter(isPlayer = true, posX = 40f)
        ctx.player = player
        ctx.enemies = listOf(peasant)
        // 0.1%/sec â€” thousands of seconds of exposure makes a catch near-certain (E[catches] â‰ˆ 20)
        for (i in 0 until 20_000) {
            if (player.isContagious) break
            player.hp = 100f // keep him upright through the exposure so he can't die of it first
            engine.updateFighter(player, null, 1f)
        }
        assertTrue("player never caught the plague", player.isContagious)
        assertTrue("caught plague but no disease timer", player.diseaseDuration > 0f)
    }
}

