package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/** Everything the combat rules need from the battle around them. */
interface BattleContext {
    val player: FighterState?
    val enemies: List<FighterState>
    val levelWidth: Float
    val unlockedAncillaries: List<Ancillary>
    /** Player's "out" cards against the late-game counters. */
    val hasShieldbreaker: Boolean get() = false
    val hasArmorPiercing: Boolean get() = false
    fun spawnProjectile(p: Projectile)
    fun sound(type: SoundType)
    fun popup(text: String, x: Float, y: Float, color: Color)
    fun bloodParticles(x: Float, y: Float, count: Int)
    fun particle(p: BloodParticle)
    fun screenshake(amount: Float)
    fun enemyKilled()
}

/**
 * All combat rules: movement AI, attack triggering, strikes, projectile and flat damage.
 * Pure of ViewModel/coroutines — delayed follow-up hits are tick-driven via [tick].
 */
class CombatEngine(private val ctx: BattleContext) {

    companion object {
        // Fists hit fast enough to occasionally stagger a mid-swing defender.
        const val FIST_INTERRUPT_CHANCE = 0.25f

        /** Each dual-wielded weapon swings and misses on its own. Two hands, two rolls. */
        const val DUAL_WIELD_MISS_CHANCE = 0.35f

        /** Up on stilts you are simply above most of it — a man on foot swings at your poles. */
        const val STILTS_EVASION = 0.55f

        /** Odds a heavy blunt hit permanently concertinas an enemy's head into his shoulders. */
        const val HEAD_SQUASH_CHANCE = 0.4f
        /** How often a double-ended swing reverses its overhead whirl. */
        const val DOUBLE_ENDED_REVERSE_CHANCE = 0.25f

        /**
         * Melee fighters close to this fraction of their reach before swinging, instead of stopping
         * dead on the hitbox boundary. Standing exactly at max reach meant any drift during a slow
         * windup — a pike takes most of a second — dropped the target out of range and every blow
         * whiffed. Close a little further and the swing lands.
         */
        private const val MELEE_ENGAGE_MARGIN = 0.8f
        // Melee pursuers tighten up on a kiting (ranged) target instead of stalling at their full reach.
        private const val MELEE_VS_RANGED_CHASE_MULT = 0.8f

        // Ranged/backpedal balance (all tunable — kept gentle so ranged stays a real threat).
        /** Retreating (kiting) is slower than advancing, so a melee chaser can eventually close. */
        const val KITE_RETREAT_MULT = 0.6f
        /** Per-second chance an unmounted kiter stumbles on rough ground: brief slow + lost shot. */
        const val KITE_STUMBLE_CHANCE_PER_SEC = 0.12f
        /** Early-game enemy ranged fire is slower (level <= EARLY_RANGED_LEVEL). */
        const val EARLY_RANGED_SLOW = 1.4f
        const val EARLY_RANGED_LEVEL = 3
        /**
         * A melee weapon this long earns polearm spacing: it keeps foes at its tip. 3.0 not 2.0
         * because reach is now measured off the drawn weapon (grip + haft + head) rather than the
         * old head+handle stat sum; the same builds fall either side of it as before.
         */
        const val LONG_MELEE_REACH = 3.0f
        /** Foes inside this fraction of a long weapon's pixel reach are "point-blank" — back off to the tip. */
        const val LONG_MELEE_DEADZONE = 0.5f
        /** Damage a long weapon does to someone already inside its dead zone. Tunable; the highest
         *  balance risk in the ranged/spacing pass, so it starts gentle. */
        const val POINT_BLANK_DMG_MULT = 0.6f
        /** A severed arm bleeds — the stump keeps costing HP after the limb is gone. Tunable. */
        const val ARM_BLEED_SECONDS = 5.0f

        // Damage-over-time rates, in points per second (see applyDotDamage).
        const val POISON_DPS = 14f
        const val BLEED_DPS = 10f
        const val IGNITE_DPS = 12f
        const val IGNITE_DURATION = 5f
        /** The player burns briefly and coolly: ~3.5s at PLAYER_IGNITE_DPS is a scare, not a death. */
        const val PLAYER_IGNITE_DURATION = 3.5f
        const val PLAYER_IGNITE_DPS = 5f
        /** Rot bites the player at half rate, and never runs on him for long. */
        const val PLAYER_DOT_DAMAGE_MULT = 0.5f
        const val PLAYER_DOT_MAX_SECS = 2.5f
        const val ARMOR_SHRED_PER_HIT = 15f
        const val MONK_AURA_RADIUS_PX = 240f
        const val MONK_AURA_ATTACK_DELAY_MULT = 0.8f
        /**
         * How much longer a slowed man takes between swings.
         *
         * Slow only ever touched move speed, and in a melee where both lines are already in
         * contact nobody is walking anywhere — so the Killing Frost, the greaser's fat, the hag's
         * mud and the sapper's grit all read as doing nothing at all. A man on glass ice cannot
         * set his feet to swing either.
         */
        const val SLOWED_ATTACK_DELAY_MULT = 1.6f
        /** How far the standard can be seen, and what seeing it is worth. Wider than the monk's. */
        const val BANNER_AURA_RADIUS_PX = 320f
        const val BANNER_AURA_ATTACK_DELAY_MULT = 0.78f

        // Curve counters and their outs.
        const val SHIELDBREAKER_MULT = 3f
        const val ARMOR_PIERCE_FRACTION = 0.33f
        const val WAR_PRIEST_HEAL_PER_SEC = 5f
        const val WAR_PRIEST_RADIUS_PX = 260f

        // Plague peasant contagion. Tunable starting values.
        const val DISEASE_DPS = 5f
        const val DISEASE_DURATION = 12f
        const val DISEASE_RADIUS_PX = 70f
        const val PLAYER_CATCH_CHANCE_PER_SEC = 0.001f
        const val CORPSE_CONTAGION_SECS = 4f
        const val DEATH_PLAGUE_BURST_PX = 130f // his dying gift — wider than the passive miasma
        const val TROJAN_ROLL_MULT = 1.9f // must outpace the player to reach the enemy rear first

        /**
         * The frontline reaches the fray first. Ancillary speedBoost buffs the *player*, not the
         * ally, so every recruit makes the lord faster while his dogs stay put — the floor has to
         * be a ratio against his live speed, not a bigger number on the ally.
         */
        const val FRONTLINE_LEAD = 1.2f

        /** Rusted nails leave a wound that turns. Seconds of poison per plank hit. */
        const val PLANK_POISON_SECONDS = 4f

        /** Melee chargers who lead the advance. Backline lobbers are ranged and must keep kiting. */
        // These are matched with isKind(), which compares against the spawn id — Boris spawns as
        // "fanatic_boris#N", so a bare "fanatic" here matched nothing and he alone never got the lead.
        val FRONTLINE_KINDS = setOf("fanatic_boris", "wardog", "plague_peasant", "raven")

        // Greaser (all tunable). He's crowd control, not damage — the trip is the whole point.
        const val GREASE_SLOW_SECS = 2.5f
        const val GREASE_TRIP_CHANCE = 0.3f
        const val GREASE_TRIP_SECS = 1.8f

        /** Consecutive staggers before a fighter powers through and finishes his swing anyway. */
        const val MAX_INTERRUPT_STREAK = 3

        /** Below this level a boss's blows are whatever his stats say, so early giants stay beatable. */
        const val BOSS_RAMP_START_LEVEL = 40
        /** Added share of damage per level past the ramp start. */
        const val BOSS_RAMP_PER_LEVEL = 0.012f
        /** Ceiling. A deep-level giant should be terrifying, not a one-frame loss. */
        const val BOSS_RAMP_MAX = 3.0f

        /**
         * How much harder a boss hits than his raw stat line, by level.
         *
         * The ordinary stat curve treats a boss as a big man, so by the deep levels the player's
         * own growth has outrun him and his blows stopped mattering. Flat 1.0 through the early
         * fights — the first giants must stay winnable — then a ramp with a hard ceiling.
         */
        fun bossBrutality(attacker: FighterState): Float {
            if (attacker.bossType == null) return 1f
            val over = (attacker.level - BOSS_RAMP_START_LEVEL).coerceAtLeast(0)
            return (1f + over * BOSS_RAMP_PER_LEVEL).coerceAtMost(BOSS_RAMP_MAX)
        }

        // Rare ranged rewards. All tunable; these are the fun knobs, not the balance-critical ones.
        /** Fragments a cluster charge sprays on impact. */
        const val CLUSTER_FRAGMENTS = 4
        /** And what a bomb packed with nails throws instead — it is a bigger pot to fill. */
        const val BOMB_SHRAPNEL_FRAGMENTS = 7
        /** A bolt is a spike driven by a steel prod. It goes through mail a shaft would rattle off. */
        const val CROSSBOW_ARMOUR_PIERCE = 0.45f
        /** Each fragment's share of the parent shot's damage. */
        const val CLUSTER_FRAGMENT_DAMAGE = 0.35f
        /** How much further a lofted volley shot travels than the flat shot beside it. */
        const val VOLLEY_RANGE_MULT = 1.45f
        /** Delay between the flat shot and its lofted twin, so they read as two loosings. */
        const val VOLLEY_DELAY_SECS = 0.12f
        /** Ground a ballista spear shoves a man back. Its card promised a knockback; nothing did it. */
        const val BALLISTA_KNOCKBACK_PX = 45f
        /** Skid drag. Distance travelled works out as launch speed / this, so 6 gives ~0.5s of slide. */
        const val SKID_DRAG_PER_SEC = 6f

        // The unmuzzled bear, all tunable. The friendly-fire chance is the price of the reward and
        // is deliberately low enough to be a story rather than a tax.
        // Raised: at 30%/34 the maul was quieter than the friendly-fire it caused, so cutting the
        // straps read as a pure liability. A bear off the strap should be the reason men die.
        const val BEAR_MAUL_CHANCE = 0.45f
        const val BEAR_MAUL_DAMAGE = 62f
        const val BEAR_FRIENDLY_FIRE_CHANCE = 0.18f
        const val BEAR_FRIENDLY_FIRE_PX = 110f

        // The sledge dogs. Two rolls per swing rather than one big one, so the pair reads as a
        // pair — and much smaller than the bear's maul, because there is no downside to pay for.
        const val SLEIGH_BITE_CHANCE = 0.4f
        const val SLEIGH_BITE_DAMAGE = 16f

        /** Extra block chance per metre a shielded foe stands beyond LONG_MELEE_REACH of the swing. */
        const val REAR_RANK_BLOCK_PER_M = 0.04f
        /** Nobody blocks everything, however braced and however far down the shaft they stand. */
        const val MAX_BLOCK_CHANCE = 0.85f

        /** The sapper's handful of spoil, per landed blow. */
        const val SAPPER_GRIT_CHANCE = 0.35f

        /** Tiny Terrence's chance to duck a missile outright. High on purpose; see the comment. */
        const val TERRENCE_DODGE = 0.75f

        /** How long a kiter is on the ground after tripping on the rough. */
        const val KITE_STUMBLE_FALL_SECS = 0.9f
        /** Share of missile damage a timber body actually takes. */
        const val INANIMATE_RANGED_SOAK = 0.35f

        /** The barrow-king's hurl: chance per landed blow, and how far it throws a man. */
        const val BARROW_KING_HURL_CHANCE = 0.22f
        const val BARROW_KING_HURL_PX = 130f

        /** A routed man runs faster than he marches. Multiplier on moveSpeed while panicking. */
        const val PANIC_RUN_MULT = 1.35f

        /** Bosses swing quicker than their bulk earns them. Tunable; the boss-fight pacing knob. */
        const val BOSS_SWING_SPEEDUP = 0.6f  // 0.8 → 0.6: bosses were out-traded by a stacked player

        /** From here the host arcs a second shaft over the front rank onto the player's archers. */
        const val ENEMY_VOLLEY_LEVEL = 25
        /** From here a heavy enemy blunt weapon can floor the player's FOLLOWERS. Never the player. */
        const val ENEMY_KNOCKDOWN_LEVEL = 25
        /** From here enemy slingers dip their stones. */
        const val ENEMY_SLING_POISON_LEVEL = 30
        /** A boss keeps both arms until this fraction of his hp is left. */
        const val BOSS_ARM_LOSS_HP_FRACTION = 0.15f
    }

    /**
     * Stagger [target] out of his swing — unless he has already been staggered
     * [MAX_INTERRUPT_STREAK] times without landing one, in which case he grits his teeth and the
     * swing continues (the streak clears when a swing completes). Stops fast builds stun-locking
     * bosses forever.
     */
    private fun tryInterrupt(target: FighterState, requireSwing: Boolean = true): Boolean {
        if (requireSwing && !target.isAttacking) return false
        // Most staggers simply do not land on something huge. ccResist is already this game's
        // "how much crowd control does this thing feel" (1.0 for a man, 0.1 for a living boss) and
        // the crumple and skid paths both honour it — but this one never did, so a ring of
        // followers could keep a giant permanently mid-flinch. The streak cap below was a floor on
        // that, not a fix: it still cost him three swings out of every four.
        if (Random.nextFloat() >= target.ccResist.coerceAtMost(1f)) return false
        if (target.interruptStreak >= MAX_INTERRUPT_STREAK) {
            ctx.popup("IMPERTURBATUS!", target.posX, 150f, Color(0xFFD6A420))
            return false
        }
        target.interruptStreak++
        target.isAttacking = false
        target.swingProgress = 0f
        target.hasLandedStrike = false
        return true
    }

    // Delayed follow-up swings/shots (dual-wield 2nd hit, double-ended pole hits, multishot).
    // Ticked by the game loop so nothing fires after the battle ends.
    private class QueuedAction(var delay: Float, val run: () -> Unit)
    private val queuedActions = mutableListOf<QueuedAction>()

    fun reset() = queuedActions.clear()

    fun tick(dt: Float) {
        if (queuedActions.isEmpty()) return
        val due = mutableListOf<QueuedAction>()
        queuedActions.forEach { it.delay -= dt }
        queuedActions.removeAll { if (it.delay <= 0f) { due.add(it); true } else false }
        due.forEach { it.run() }
    }

    private fun schedule(delay: Float, action: () -> Unit) {
        queuedActions.add(QueuedAction(delay, action))
    }

    /** The man who loosed it, if he is still on the field — a missile often outlives its archer. */
    private fun shooterOf(proj: Projectile): FighterState? {
        val id = proj.sourceFighterId ?: return null
        return ctx.enemies.firstOrNull { it.id == id } ?: ctx.player?.takeIf { it.id == id }
    }

    /** What the chronicle calls the thing that hit you. */
    private fun missileNote(proj: Projectile): String = when (proj.type) {
        ProjectileType.ARROW -> "an arrow"
        ProjectileType.BOLT -> "a crossbow bolt"
        ProjectileType.STONE -> "a slung stone"
        ProjectileType.JAVELIN -> "a javelin"
        ProjectileType.ROCK -> "a hurled rock"
        ProjectileType.DART -> "a dart"
        ProjectileType.TORCH -> "a firebrand"
        ProjectileType.BOMB -> "a bursting pot of black powder"
    }

    private fun followerCatchUpMultiplier(fighter: FighterState, direction: Float): Float {
        val player = ctx.player ?: return 1f
        if (!fighter.isPlayer || fighter.pallbearerIndex >= 0 || fighter === player) return 1f
        val deltaToPlayer = player.posX - fighter.posX
        val catchUp = if (deltaToPlayer == 0f || direction * deltaToPlayer <= 0f) 1f
            else 1f + ((abs(deltaToPlayer) - 150f) / 300f).coerceIn(0f, 1.5f)

        // Advancing only: a retreat-speed floor would shove kiters backwards faster than they mean to.
        if (direction <= 0f) return catchUp
        if (FRONTLINE_KINDS.none { fighter.isKind(it) }) return catchUp
        if (fighter.moveSpeed <= 0f) return catchUp
        return maxOf(catchUp, (player.moveSpeed * FRONTLINE_LEAD) / fighter.moveSpeed)
    }

    /**
     * Damage-over-time, in whole points at the stated rate.
     *
     * [applyFlatDamage] clamps every call to a minimum of 1 point, which is right for a sword blow and
     * ruinous for a per-tick trickle: a nominal 4/sec poison landed 1 point *per frame* (~60/sec), with a
     * screenshake and blood spray each time. So sub-point damage accrues here and is spent only once it
     * makes a whole point.
     */
    private fun applyDotDamage(dps: Float, dt: Float, fighter: FighterState) {
        // The player himself takes rot at half rate. He is exposed to it — poison and bleeding
        // should be things that happen to him, not things only his men suffer — but a full-rate
        // stacking DoT on the one body you cannot replace is a death sentence rather than a
        // setback. His FOLLOWERS take it in full; they are a resource, he is the run.
        val scale = if (fighter === ctx.player) PLAYER_DOT_DAMAGE_MULT else 1f
        fighter.dotDebt += dps * scale * dt
        if (fighter.dotDebt < 1f) return
        val whole = floor(fighter.dotDebt)
        fighter.dotDebt -= whole
        applyFlatDamage(whole, fighter, isPlayerSource = !fighter.isPlayer, quiet = true)
    }

    /**
     * And it wears off quickly on him. Capped every tick rather than at each application site,
     * because a dozen different things lay poison and every one of them would otherwise need to
     * remember the player is a special case.
     */
    private fun capPlayerDotDurations(fighter: FighterState) {
        if (fighter !== ctx.player) return
        if (fighter.poisonDuration > PLAYER_DOT_MAX_SECS) fighter.poisonDuration = PLAYER_DOT_MAX_SECS
        if (fighter.bleedDuration > PLAYER_DOT_MAX_SECS) fighter.bleedDuration = PLAYER_DOT_MAX_SECS
        if (fighter.diseaseDuration > PLAYER_DOT_MAX_SECS) fighter.diseaseDuration = PLAYER_DOT_MAX_SECS
    }

    /** True if a plague carrier — living, or a corpse not yet cold — is close enough to breathe on [f]. */
    // ponytail: O(n²) across the roster each tick; fine at <=16 fighters, bucket by x if the cap ever rises
    private fun carrierNear(f: FighterState): Boolean {
        val now = System.currentTimeMillis()
        val corpseWindowMs = (CORPSE_CONTAGION_SECS * 1000f).toLong()
        val all = ctx.enemies + listOfNotNull(ctx.player)
        return all.any { c ->
            c !== f && c.isContagious &&
                (!c.isDead || now - c.deathTime <= corpseWindowMs) &&
                abs(c.posX - f.posX) <= DISEASE_RADIUS_PX
        }
    }

    fun updateFighter(fighter: FighterState, target: FighterState?, dt: Float) {
        // Handle Death animation delay
        if (fighter.isDying) {
            fighter.animFrame += dt * 6f
            if (fighter.animFrame >= 6f) {
                fighter.isDying = false
                fighter.isDead = true
            }
            return
        }
        if (fighter.isDead) return
        if (fighter.climbState != ClimbState.NONE || fighter.isCombatInactive) {
            fighter.isAttacking = false
            fighter.swingProgress = 0f
            return
        }

        // Trojan horse never fights: it rolls right past the enemy line, then bursts open
        if (fighter.isKind("trojan_horse")) {
            val foes = ctx.enemies.filter { !it.isDead && !it.isDying && !it.isPlayer }
            if (foes.any { it.posX > fighter.posX - 60f } && fighter.posX < ctx.levelWidth - 80f) {
                // Was 0.7 — slower than the player, who routinely charged past and killed the line
                // before the decoy ever reached its spot. It must lead the advance, not trail it.
                // A flat multiple of its OWN speed was not enough: every speed upgrade the player
                // takes widens the gap again, so floor it against the player the same way the
                // frontline entourage is floored.
                val ownRoll = fighter.moveSpeed * TROJAN_ROLL_MULT
                val leadRoll = (ctx.player?.moveSpeed ?: 0f) * FRONTLINE_LEAD
                fighter.posX += maxOf(ownRoll, leadRoll) * dt
                fighter.animFrame += dt * 6f
                fighter.facingRight = true
            } else {
                // Behind every foe on the field — the belly bursts open
                fighter.isDying = true
                fighter.animFrame = 0f
                fighter.deathType = DeathType.FALL_BACK
                fighter.deathTime = System.currentTimeMillis()
            }
            return
        }

        // Ease out any stale wrestling lift (attacker died/switched targets mid-move);
        // an active lift re-sets this every tick so it wins over the decay
        if (fighter.visualOffsetY != 0f) {
            val decay = 250f * dt
            fighter.visualOffsetY = if (abs(fighter.visualOffsetY) <= decay) 0f
                else fighter.visualOffsetY + if (fighter.visualOffsetY < 0f) decay else -decay
        }

        // Update damage indicators
        if (fighter.damageIndicator != null) {
            fighter.damageIndicatorTimer -= dt
            if (fighter.damageIndicatorTimer <= 0) {
                fighter.damageIndicator = null
            }
        }

        capPlayerDotDurations(fighter)

        // Poison tick over time
        if (fighter.poisonDuration > 0f) {
            fighter.poisonDuration -= dt
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) { // occasionally show green "+POISON+" popup
                    ctx.popup("POISON!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF2E7D32))
                }
                if (Random.nextFloat() < dt) {
                    ctx.particle(
                        BloodParticle(
                            x = fighter.posX + Random.nextInt(-12, 13),
                            y = 115f + Random.nextInt(0, 30),
                            vx = Random.nextFloat() * 16f - 8f,
                            vy = 25f + Random.nextFloat() * 25f,
                            color = Color(0xFF2E7D32).copy(alpha = 0.55f)
                        )
                    )
                }
                applyDotDamage(POISON_DPS * fighter.dotIntensity(Dot.POISON), dt, fighter)
            }
            if (fighter.poisonDuration <= 0f) fighter.clearDot(Dot.POISON)
        }

        if (fighter.igniteDuration > 0f) {
            fighter.igniteDuration = (fighter.igniteDuration - dt).coerceAtLeast(0f)
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) {
                    ctx.popup("ARDENS!", fighter.posX, 130f, Color(0xFFE07020))
                }
                val igniteDps = if (fighter === ctx.player) PLAYER_IGNITE_DPS else IGNITE_DPS
                applyDotDamage(igniteDps * fighter.dotIntensity(Dot.IGNITE), dt, fighter)
            }
            if (fighter.igniteDuration <= 0f) fighter.clearDot(Dot.IGNITE)
        }
        if (fighter.arrowEyeCritWindow > 0f) {
            fighter.arrowEyeCritWindow = (fighter.arrowEyeCritWindow - dt).coerceAtLeast(0f)
        } else if (fighter.bossType == BossType.HAROLD_GODWINSON) {
            fighter.arrowEyeCritCooldown -= dt
            if (fighter.arrowEyeCritCooldown <= 0f) {
                fighter.arrowEyeCritWindow = 1.5f
                fighter.arrowEyeCritCooldown = 5f
                ctx.popup("OCULUS APERTUS!", fighter.posX, 95f, Color.Yellow)
            }
        }

        // Bleed tick over time
        if (fighter.bleedDuration > 0f) {
            fighter.bleedDuration -= dt
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) {
                    ctx.popup("BLEED!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFFA62B2B))
                }
                applyDotDamage(BLEED_DPS * fighter.dotIntensity(Dot.BLEED), dt, fighter)
            }
            if (fighter.bleedDuration <= 0f) fighter.clearDot(Dot.BLEED)
        }

        // Plague: rot away, then breathe in whatever the neighbours are carrying
        if (fighter.diseaseDuration > 0f) {
            fighter.diseaseDuration -= dt
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.2f) {
                    ctx.popup("PESTILENCE!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF6B7D4A))
                }
                applyDotDamage(DISEASE_DPS * fighter.dotIntensity(Dot.DISEASE), dt, fighter)
            }
            if (fighter.diseaseDuration <= 0f) fighter.clearDot(Dot.DISEASE)
        }
        if (carrierNear(fighter)) {
            if (fighter === ctx.player) {
                // The peasant is your own man, so you only risk it by standing in his miasma
                if (!fighter.isContagious && Random.nextFloat() < PLAYER_CATCH_CHANCE_PER_SEC * dt) {
                    fighter.applyDot(Dot.DISEASE, DISEASE_DURATION)
                    fighter.isContagious = true
                    ctx.popup("YOU FEEL UNWELL...", fighter.posX, 150f, Color(0xFF6B7D4A))
                }
            } else if (!fighter.isPlayer) {
                // Standing in the miasma only ever tops the clock back up — a man cannot catch the
                // same plague twice a second, so no stack is added while he is already rotting.
                if (fighter.diseaseDuration <= 0f) fighter.applyDot(Dot.DISEASE, DISEASE_DURATION)
                else fighter.diseaseDuration = DISEASE_DURATION
            }
        }

        // Slow tick over time
        if (fighter.slowDuration > 0f) {
            fighter.slowDuration -= dt
        }

        // Knockback skid. Runs ahead of every AI branch and outside the crumple guard, so a man
        // shoved while floored still slides instead of stopping dead where the spear hit him.
        if (fighter.skidVelocityX != 0f) {
            fighter.posX = (fighter.posX + fighter.skidVelocityX * dt)
                .coerceIn(30f, ctx.levelWidth - 30f)
            fighter.skidVelocityX *= (1f - SKID_DRAG_PER_SEC * dt).coerceAtLeast(0f)
            if (abs(fighter.skidVelocityX) < 4f) fighter.skidVelocityX = 0f
        }

        // Panic tick. A routed man keeps his feet but not his head: he blunders off in whatever
        // direction the last frog sent him, so his target keeps being rewritten from under him.
        if (fighter.panicDuration > 0f) {
            fighter.panicDuration -= dt
            if (Random.nextFloat() < dt * 3f) {
                fighter.targetX = fighter.posX + (Random.nextFloat() * 2f - 1f) * 220f
                fighter.facingRight = fighter.targetX > fighter.posX
            }
        }

        // Crumple tick over time. isCrumpled was never cleared, so anyone knocked down stayed
        // flagged down forever — they stood back up and never swung again.
        if (fighter.crumpleCooldown > 0f) fighter.crumpleCooldown -= dt
        if (fighter.crumpleDuration > 0f) {
            fighter.crumpleDuration -= dt
            if (fighter.crumpleDuration <= 0f) {
                fighter.crumpleDuration = 0f
                fighter.isCrumpled = false
                fighter.slipped = false
                // Get up ready to fight, not stuck mid-swing from before they were floored
                fighter.isAttacking = false
                fighter.hasLandedStrike = false
                fighter.swingProgress = 0f
                fighter.attackCooldown = 0f
            }
        }

        if (fighter.faceHoldTimer > 0f) fighter.faceHoldTimer -= dt

        // Cooldown tick
        if (fighter.attackCooldown > 0) {
            val cooldownRate = if (!fighter.isPlayer && fighter.armor.id == "armor_bare") 1.3f else 1f
            fighter.attackCooldown -= dt * cooldownRate
        }

        // The war-priest never lifts a hand. He keeps behind his flock and mends whoever is worst
        // hurt — so he must be killed first, or the host heals faster than you can cut it down.
        // (Below the status ticks above, so plague and poison still rot him; flattened by hail he stops.)
        if (fighter.isWarPriest) {
            fighter.isAttacking = false
            fighter.swingProgress = 0f
            if (fighter.crumpleDuration > 0f) return

            ctx.enemies
                .filter { it !== fighter && !it.isPlayer && !it.isDead && !it.isDying }
                .filter { it.hp < it.maxHp && abs(it.posX - fighter.posX) <= WAR_PRIEST_RADIUS_PX }
                .minByOrNull { it.hp }
                ?.let { worstHurt ->
                    worstHurt.hp = (worstHurt.hp + WAR_PRIEST_HEAL_PER_SEC * dt).coerceAtMost(worstHurt.maxHp)
                    if (Random.nextFloat() < dt * 0.8f) {
                        ctx.popup("BENEDICTIO!", worstHurt.posX, 150f, Color(0xFFD6C48A))
                    }
                }

            val player = ctx.player
            if (player != null && !player.isDead) {
                val gap = fighter.posX - player.posX
                if (gap > WAR_PRIEST_RADIUS_PX * 0.75f) {
                    fighter.posX -= fighter.moveSpeed * 0.45f *
                        followerCatchUpMultiplier(fighter, -1f) * dt
                    fighter.animFrame += dt * 5f
                }
                fighter.facingRight = gap < 0f
            }
            return
        }

        // Update Swing Progress
        if (fighter.isAttacking) {
            if (target?.isKind("raven") == true && fighter.visualOffsetY < 0f) {
                fighter.visualOffsetY = 0f
            }
            fighter.swingProgress += dt * (1.2f / fighter.attackSpeedDelay)
            val chainDelay = if (fighter.weaponHandle.id == "handle_chain") 0.15f else 0f
            val effectiveSwingProgress = (fighter.swingProgress - chainDelay).coerceAtLeast(0f)

            // Damage connects halfway through the swing visually, or near the end for heavy/chain windups
            val isChain = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain") || fighter.weaponHead.id in listOf("head_flail", "head_war_flail")
            val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe", "head_lucerne", "head_saber")
            val isChokeSlam = fighter.activeWrestlingMove == WrestlingMove.CHOKE_SLAM
            val isSuplex = fighter.activeWrestlingMove == WrestlingMove.SUPLEX
            val strikeThreshold = if (isChokeSlam) 0.7f else if (isChain) 0.65f else if (isHeavy) 0.85f else 0.5f

            if (fighter.isBrawler && target != null && !target.isDead) {
                // Apply visual lift for wrestling moves
                val distToTarget = abs(fighter.posX - target.posX)
                if (distToTarget < fighter.reachPixels + 20f) {
                    val p = effectiveSwingProgress.coerceIn(0f, 1f)
                    // Scaled by the target's own resistance: you do not suplex a giant snail, and
                    // hoisting one clean off the ground was the last way it kept being crowd-
                    // controlled after its knockdown resistance went up.
                    val liftMax = (if (isChokeSlam) -140f else if (isSuplex) -90f else if (fighter.activeWrestlingMove == WrestlingMove.BODY_THROW) -70f else 0f) *
                        target.ccResist.coerceAtMost(1f)
                    if (liftMax != 0f) {
                        target.visualOffsetY = if (p < strikeThreshold) {
                            liftMax * (p / strikeThreshold)
                        } else {
                            liftMax * (1f - (p - strikeThreshold) / (1f - strikeThreshold))
                        }
                    }
                }
            }

            if (effectiveSwingProgress >= strikeThreshold && !fighter.hasLandedStrike) {
                fighter.hasLandedStrike = true
                if (target != null) {
                    performStrike(fighter, target)
                }
            }

            if (effectiveSwingProgress >= 1f) {
                fighter.isAttacking = false
                fighter.hasLandedStrike = false
                fighter.swingProgress = 0f
                fighter.interruptStreak = 0 // a completed swing earns back the right to be staggered
                // Ensure a slammed enemy never sticks mid-air if the move was interrupted
                if (fighter.activeWrestlingMove != null) {
                    target?.visualOffsetY = 0f
                    fighter.activeWrestlingMove = null
                }
            }
        }
        // Pallbearer lock
        if (fighter.pallbearerIndex >= 0) {
            val player = ctx.player
            if (player != null) {
                fighter.facingRight = player.facingRight
                val offset = when (fighter.pallbearerIndex) {
                    0 -> 45f
                    1 -> 35f
                    2 -> -35f
                    3 -> -45f
                    else -> 0f
                }
                fighter.posX = player.posX + offset
                fighter.animFrame = player.animFrame * 1.5f // Walk in sync with the throne

                // Only the gear-bearing front pair fight; the rear pair just carry
                if (fighter.pallbearerIndex < 2 && target != null && !target.isDead) {
                    val dist = abs(fighter.posX - target.posX)
                    val reachPixels = fighter.reachPixels
                    if (dist <= reachPixels && fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                        triggerAttack(fighter)
                    }
                }
                return
            }
        }

        // Routed. He is not fighting anyone now — he is running from a frog, and the direction is
        // whatever the panic tick last wrote into targetX.
        //
        // This branch is the whole miracle. Panic used to set targetX and nothing else, and enemy
        // movement steers off `target.posX` — the chosen foe — so targetX was read by nobody: the
        // host fought straight through a Rain of Frogs while the player's own men fell over, which
        // is why the miracle looked like it was aimed at the wrong army.
        // A boss does not run from a frog. He never disengages: no panic, and no spacing shuffle
        // further down — once he is on you he stays on you until one of you is down.
        if (fighter.panicDuration > 0f && !fighter.isPlayer && fighter.bossType == null &&
            fighter.crumpleDuration <= 0f
        ) {
            fighter.isAttacking = false
            fighter.swingProgress = 0f
            fighter.hasLandedStrike = false
            val direction = if (fighter.targetX > fighter.posX) 1f else -1f
            // A man on a parapet blunders about up there rather than sprinting off a wall.
            if (!fighter.elevated && abs(fighter.targetX - fighter.posX) > 8f) {
                fighter.posX += direction * fighter.moveSpeed * PANIC_RUN_MULT * dt
                fighter.animFrame += dt * 14f // legs going like a hare
            }
            fighter.facingRight = direction > 0f
            return
        }

        // Elevated wall defenders hold their parapet — they shoot but never walk off the castle
        // to chase, which was dragging archers left into mid-air off the wall's footprint.
        if (fighter.elevated && !fighter.isPlayer && target != null && !target.isDead &&
            fighter.crumpleDuration <= 0f
        ) {
            fighter.faceToward(target.posX)
            if (fighter.attackCooldown <= 0 && !fighter.isAttacking) triggerAttack(fighter)
            val piF = Math.PI.toFloat()
            val nearestRest = kotlin.math.round(fighter.animFrame / piF) * piF
            fighter.animFrame += (nearestRest - fighter.animFrame).coerceIn(-8f * dt, 8f * dt)
            return
        }

        // Decide movement & actions
        if (target != null && !target.isDead && fighter.crumpleDuration <= 0f) {
            val dist = abs(fighter.posX - target.posX)
            val reachPixels = fighter.reachPixels
            var rangeMult = if (!fighter.isPlayer && fighter.level > 5) 0.8f + (fighter.level - 5) * 0.05f else 0.8f
            // A sling outranges a self bow in life, and the host's slingers were the shortest-ranged
            // thing on the field. They stand off and whirl now.
            if (!fighter.isPlayer && fighter.weaponHead.id == "head_slingshot") rangeMult *= 1.35f
            // Only ranged fighters kite to keep their distance; melee (including fists) holds ground
            // and stands to trade blows once in range, so a fists player is never stalled just outside
            // reach by an enemy backpedaling from a weapon it doesn't have.
            val mountReachPixels = if (fighter.isMounted) 1.5f * 40f else 0f
            val baseOptimal = reachPixels - mountReachPixels
            // Short weapons step right inside the boundary; everything else still closes enough that a
            // slow swing cannot whiff on a target that shuffled a few pixels during the windup.
            // 1.6 not 1.2 because reach is now measured off the drawn weapon: fists and a dagger
            // grip land either side of 1.6 where they used to land either side of 1.2.
            val approachTarget = if (!fighter.isRanged && fighter.reach < 1.6f) baseOptimal * 0.45f
                else baseOptimal * MELEE_ENGAGE_MARGIN
            val optimalDistance = when {
                fighter.isRanged -> reachPixels * rangeMult
                target.isRanged -> approachTarget * MELEE_VS_RANGED_CHASE_MULT // tighten up chasing a kiting target
                else -> approachTarget
            }
            val isShieldWall = !fighter.isPlayer && fighter.shield.id == "shield_tower"

            fighter.faceToward(target.posX)

            if (dist > optimalDistance) {
                // Walk closer
                val direction = if (target.posX > fighter.posX) 1f else -1f
                val moveMult = if (isShieldWall) 0.6f else 1f
                fighter.posX += direction * fighter.moveSpeed * moveMult *
                    followerCatchUpMultiplier(fighter, direction) * dt
                // Desync animations slightly based on maxHp to avoid identical marching
                fighter.animFrame = fighter.animFrame + dt * (9f + (fighter.maxHp % 3f))
            } else if (fighter.isRanged && dist < optimalDistance * 0.7f && fighter.moveSpeed > 0f) {
                // Ranged only: step back to keep the target at missile range. Retreat is slower than
                // advancing (KITE_RETREAT_MULT) so a chaser can close, and an unmounted kiter can
                // trip on rough ground — a brief slow that costs this frame's shot. Riders never slip.
                if (!fighter.isPlayer && !fighter.isMounted && fighter.slowDuration <= 0f &&
                    Random.nextFloat() < KITE_STUMBLE_CHANCE_PER_SEC * dt) {
                    // A stumble you can SEE. This only set slowDuration, which reads as "he walked
                    // a bit slower for a moment" and was invisible — the kiter never appeared to
                    // trip at all. He goes down on the spot now, briefly, and loses the shot.
                    // He goes over backwards: tryCrumple's render angle is FALL_BACK's angle.
                    fighter.slowDuration = 0.8f
                    if (fighter.tryCrumple(KITE_STUMBLE_FALL_SECS)) fighter.slipped = true
                    // Divots kicked up by the skidding heel, thrown the way his feet went — the
                    // whole tell, no word popup. A shout on every trip was noise at this cadence.
                    val skid = if (target.posX > fighter.posX) -1f else 1f
                    repeat(5) {
                        ctx.particle(BloodParticle(
                            x = fighter.posX + Random.nextInt(-8, 8),
                            y = 190f,
                            vx = skid * (40f + Random.nextFloat() * 90f),
                            vy = -90f - Random.nextFloat() * 70f,
                            color = if (it % 2 == 0) Color(0xFF6B5638) else Color(0xFF4F6B3A),
                            isSmoke = false
                        ))
                    }
                } else {
                    val direction = if (target.posX > fighter.posX) -1f else 1f
                    fighter.posX += direction * fighter.moveSpeed * KITE_RETREAT_MULT *
                        followerCatchUpMultiplier(fighter, direction) * dt
                    fighter.animFrame = fighter.animFrame - dt * (6f + (fighter.maxHp % 3f))

                    if (fighter.attackCooldown <= 0 && !fighter.isAttacking && lordMayFight(fighter)) {
                        triggerAttack(fighter)
                    }
                }
            } else if (!fighter.isRanged && fighter.bossType == null && fighter.reach > LONG_MELEE_REACH &&
                       dist < reachPixels * LONG_MELEE_DEADZONE && fighter.moveSpeed > 0f) {
                // Polearm spacing: a foe has crowded inside the point of a long weapon, where a pike is
                // useless. Shuffle back to keep him at the tip — but keep swinging, and retreat is slow
                // (KITE_RETREAT_MULT) so a brute who commits can still close inside and win the trade.
                val direction = if (target.posX > fighter.posX) -1f else 1f
                fighter.posX += direction * fighter.moveSpeed * KITE_RETREAT_MULT *
                    followerCatchUpMultiplier(fighter, direction) * dt
                fighter.animFrame = fighter.animFrame - dt * (6f + (fighter.maxHp % 3f))
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking && !fighter.isLord) {
                    triggerAttack(fighter)
                }
            } else {
                // Wield weapon/Attack!
                // Settle to the nearest sine-zero instead of snapping to 0, so the walk bob lands smoothly
                val piF = Math.PI.toFloat()
                val nearestRest = kotlin.math.round(fighter.animFrame / piF) * piF
                fighter.animFrame += (nearestRest - fighter.animFrame).coerceIn(-8f * dt, 8f * dt)
                // A lord normally lets his retinue fight — but once the two men at the front of his
                // litter are down he defends himself; see lordMayFight.
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking && lordMayFight(fighter)) {
                    triggerAttack(fighter)
                }
            }

            // Clamp position to level bounds
            fighter.posX = fighter.posX.coerceIn(30f, ctx.levelWidth - 30f)
        }
    }

    fun triggerAttack(fighter: FighterState) {
        if (fighter.climbState != ClimbState.NONE || fighter.isCombatInactive) return
        fighter.isAttacking = true
        fighter.swingProgress = 0f
        // Double-ended whirl: clockwise overhead by default, occasionally reversed. Rolled once here,
        // per swing, rather than read off the parity of a millisecond timestamp in the renderer.
        if (fighter.weaponHandle.id == "handle_double_ended") {
            fighter.whirlCounterClockwise = Random.nextFloat() < DOUBLE_ENDED_REVERSE_CHANCE
        }

        if (fighter.isBrawler) {
            if (fighter.missingArm) {
                fighter.activeWrestlingMove = null
            } else {
                val rand = Random.nextFloat()
                // Default is always the plain punch; wrestling moves are the occasional special.
                fighter.activeWrestlingMove = when {
                    fighter.isDualWielding && rand < 0.25f -> WrestlingMove.CHOKE_SLAM
                    fighter.isDualWielding && rand < 0.4f -> WrestlingMove.BODY_THROW
                    !fighter.isDualWielding && fighter.brawlerUpgrades.contains("champion_belt") && rand < 0.35f -> WrestlingMove.SUPLEX
                    // The belt also teaches the throw: hurl a man into his mates and scatter them.
                    !fighter.isDualWielding && fighter.brawlerUpgrades.contains("champion_belt") && rand < 0.55f -> WrestlingMove.BODY_THROW
                    // One free hand is enough to grab a throat — shield-and-fist builds slam too, just rarer
                    !fighter.isDualWielding && rand < 0.12f -> WrestlingMove.CHOKE_SLAM
                    !fighter.isDualWielding && rand < 0.2f -> WrestlingMove.BODY_THROW
                    else -> null
                }
            }
        }

        var cooldown = fighter.attackSpeedDelay
        // Underfoot conditions reach the swing, not just the walk — see SLOWED_ATTACK_DELAY_MULT.
        if (fighter.slowDuration > 0f) cooldown *= SLOWED_ATTACK_DELAY_MULT
        // A boss is big, and big means slow: at his size the mass and shield factors had him
        // swinging so rarely that a stacked player could simply stand there and out-trade him.
        if (fighter.bossType != null) cooldown *= BOSS_SWING_SPEEDUP
        if (!fighter.isPlayer && fighter.isRanged && fighter.level > 15) {
            // Ranged Escalation. The second step is for the level-40+ sieges, where a wall of
            // archers had stopped being a threat at all by the time the player got there.
            cooldown *= if (fighter.level > 40) 0.5f else 0.7f
        }
        if (!fighter.isPlayer && fighter.isRanged && fighter.level <= EARLY_RANGED_LEVEL) {
            cooldown *= EARLY_RANGED_SLOW // early rounds: ranged foes fire slower so range isn't dominant at the start
        }
        if (!fighter.isPlayer && ctx.enemies.any {
                it !== fighter && it.archetype == EnemyArchetype.MONK_MILITIA &&
                    !it.isDead && !it.isDying && !it.isCombatInactive &&
                    abs(it.posX - fighter.posX) <= MONK_AURA_RADIUS_PX
            }
        ) {
            cooldown *= MONK_AURA_ATTACK_DELAY_MULT
        }
        // The banner. The mirror of the monk's aura, on the player's side: every one of his men in
        // sight of a LIVING standard bearer swings faster, and the instant the bearer goes down it
        // is simply gone — no timer, no lingering buff. Fighting to keep him upright is the point.
        if (fighter.isPlayer && ctx.enemies.any {
                it !== fighter && it.isKind("standard_bearer") &&
                    !it.isDead && !it.isDying && !it.isCombatInactive &&
                    abs(it.posX - fighter.posX) <= BANNER_AURA_RADIUS_PX
            }
        ) {
            cooldown *= BANNER_AURA_ATTACK_DELAY_MULT
        }
        fighter.attackCooldown = cooldown

        // Play melee/ranged swing swoosh sound at start of attack animation
        ctx.sound(SoundType.SWOOSH)
    }

    /**
     * Whether this fighter is allowed to swing. Everyone is, except an enthroned lord — he lets
     * his retinue do the fighting while he is carried.
     *
     * The exception: once the two men at the FRONT of the litter are down, nothing stands between
     * him and whatever is hitting the throne, so he fights from where he sits. Enemies chewing
     * through a throne while its occupant watched with his hands in his lap was not a fight.
     */
    private fun lordMayFight(fighter: FighterState): Boolean {
        if (!fighter.isLord) return true
        val frontBearers = ctx.enemies.filter { it.pallbearerIndex in 0..1 }
        // Requires that the front of the litter EXISTED and has since gone down, not merely that
        // nobody is standing there. A lord carried by nobody at all — which is what a bare test
        // fixture looks like — is still an enthroned lord and still holds his hand.
        return frontBearers.isNotEmpty() && frontBearers.all { it.isDead || it.isDying }
    }

    private fun performStrike(attacker: FighterState, defender: FighterState) {
        if (attacker.climbState != ClimbState.NONE || defender.climbState != ClimbState.NONE) return
        // Nobody looses an arrow with a frog down his collar. Melee still lands — a panicking man
        // will swing wildly at whatever is nearest, he just cannot aim anything.
        if (attacker.isRanged && attacker.panicDuration > 0f) return
        if (attacker.isRanged) {
            val isDualWielding = attacker.isDualWielding && attacker.shield.id == "shield_none"
            var hitCount = if (isDualWielding) 2 else 1

            if (!attacker.isPlayer && attacker.level > 20) {
                hitCount += 1 // Ranged Escalation: Multishot
            }
            // The rare multishot rewards. Nocking two or three shafts at once is the whole prize,
            // so they add to the count rather than replacing the dual-wield or escalation shots.
            if (attacker.rangedUpgrades.contains("multishot_double")) hitCount += 1
            if (attacker.rangedUpgrades.contains("multishot_triple")) hitCount += 2

            for (hitIdx in 0 until hitCount) {
                if (hitIdx == 0) fireRangedShot(attacker, hitIdx)
                else schedule(0.16f * hitIdx) {
                    if (!attacker.isDead && attacker.climbState == ClimbState.NONE) {
                        fireRangedShot(attacker, hitIdx)
                    }
                }
            }

            // Volley: one extra shaft goes up rather than out, and comes down on the back ranks.
            // It rides alongside the normal attack — this is an addition, never a replacement.
            // The host learns it too. A late-game player stands behind a screen of his own archers,
            // and a flat shot never reaches them — an arcing one drops on the back rank, which is
            // exactly where his bowmen are standing.
            val enemyVolley = !attacker.isPlayer && attacker.level >= ENEMY_VOLLEY_LEVEL
            if (attacker.rangedUpgrades.contains("volley") || enemyVolley) {
                schedule(VOLLEY_DELAY_SECS) {
                    if (!attacker.isDead && attacker.climbState == ClimbState.NONE) {
                        fireRangedShot(attacker, 0, arcing = true)
                    }
                }
            }
        } else {
            if (attacker.elevated != defender.elevated) return
            // Melee hit
            val reachPixels = attacker.reachPixels
            val isPiercingWeapon = attacker.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd")

            // Gather all targets in a line if we are using a piercing weapon
            val targets = if (attacker.bossType != null) {
                // A king does not duel one man at a time. Swarming him with the whole retinue was the
                // reason bosses fell so easily — every follower in reach now eats the same swing.
                // Secondary targets are handled by meleeSweep's existing damageFalloff; no new constant.
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && !it.isCombatInactive &&
                    it.climbState == ClimbState.NONE && it.elevated == attacker.elevated &&
                    it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer && isPiercingWeapon) {
                val dir = if (attacker.facingRight) 1f else -1f
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && !it.isCombatInactive &&
                    it.climbState == ClimbState.NONE && it.elevated == attacker.elevated &&
                    it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels &&
                    ((dir > 0 && it.posX >= attacker.posX - 30f) || (dir < 0 && it.posX <= attacker.posX + 30f))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer && attacker.weaponHandle.id == "handle_double_ended") {
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && !it.isCombatInactive &&
                    it.climbState == ClimbState.NONE && it.elevated == attacker.elevated &&
                    it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer) {
                val dir = if (attacker.facingRight) 1f else -1f
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && !it.isCombatInactive &&
                    it.climbState == ClimbState.NONE && it.elevated == attacker.elevated &&
                    it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels &&
                    ((dir > 0 && it.posX >= attacker.posX - 30f) || (dir < 0 && it.posX <= attacker.posX + 30f))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else {
                listOf(defender)
            }

            if (targets.isEmpty() || (!targets.contains(defender) && abs(attacker.posX - defender.posX) > reachPixels)) {
                // Missed!
                ctx.sound(SoundType.SWOOSH)
                return
            }

            // Dual wield swings BOTH weapons. The miss is rolled per swing inside meleeSweep — the
            // hands are independent, so one going wide must not cancel the other.
            val hitCount = if (attacker.weaponHandle.id == "handle_double_ended") {
                if (attacker.isDualWielding) 4 else 2
            } else if (attacker.isDualWielding) {
                2
            } else {
                1
            }
            val dmgScale = if (hitCount == 4) 0.3f else if (hitCount == 2) 0.6f else 1.0f

            // Brawler abilities
            if (attacker.isBrawler && targets.isNotEmpty()) {
                val target = targets.first()

                if (target.weaponHead.id != "head_bare" && Random.nextFloat() < 0.05f) {
                    // Steal their weapon!
                    attacker.weaponHead = target.weaponHead
                    attacker.weaponHandle = target.weaponHandle
                    attacker.isDualWielding = false
                    attacker.stolenWeaponOwnerId = target.id // drop it back to fists once this owner is dead

                    target.weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
                    target.weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" }

                    ctx.popup("STOLEN!", target.posX, 130f, Color.Yellow)
                    ctx.sound(SoundType.CLANG)
                    // We continue into the regular attack loop below to hit them with their own weapon!
                } else if (attacker.activeWrestlingMove == WrestlingMove.SUPLEX) {
                    val secondTarget = targets.drop(1).firstOrNull() ?: target
                    applyFlatDamage(60f, target, attacker.isPlayer, attacker = attacker)
                    applyFlatDamage(60f, secondTarget, attacker.isPlayer, attacker = attacker)
                    if (!target.isPlayer) target.tryCrumple(3.5f)
                    if (!secondTarget.isPlayer) secondTarget.tryCrumple(3.5f)
                    ctx.popup("SUPLEX!", target.posX, 120f, Color.Red)
                    ctx.sound(SoundType.CRUNCH)
                    return
                } else if (attacker.activeWrestlingMove == WrestlingMove.BODY_THROW) {
                    // Hurl the grabbed enemy down the line — if he lands on a mate, both go down
                    val dir = if (attacker.facingRight) 1f else -1f
                    // A belted champion throws harder and further, and everyone in the skittle
                    // lane goes down — not just the first man he lands on.
                    val belted = attacker.brawlerUpgrades.contains("champion_belt")
                    val hurlDist = if (belted) 220f else 140f
                    val bowlRadius = if (belted) 110f else 60f
                    applyFlatDamage(if (belted) 65f else 45f, target, attacker.isPlayer, attacker = attacker)
                    if (!target.isPlayer) {
                        target.tryCrumple(3f)
                        target.posX += dir * hurlDist
                        target.targetX = target.posX
                        target.deathType = DeathType.KNOCKED_FLYING
                    }
                    val skittles = targets.drop(1).filter { abs(it.posX - target.posX) < bowlRadius }
                    val struck = if (belted) skittles else listOfNotNull(skittles.firstOrNull())
                    struck.forEach { second ->
                        applyFlatDamage(30f, second, attacker.isPlayer, attacker = attacker)
                        if (!second.isPlayer) {
                            second.tryCrumple(3f)
                            // Bowled off their feet, scattered along the throw
                            second.posX += dir * 40f
                            second.targetX = second.posX
                        }
                        ctx.popup("BOWLED OVER!", second.posX, 120f, Color.Red)
                    }
                    if (belted && struck.isNotEmpty()) ctx.screenshake(18f)
                    ctx.popup("HURLED!", target.posX, 120f, Color.Red)
                    ctx.sound(SoundType.CRUNCH)
                    return
                } else if (attacker.activeWrestlingMove == WrestlingMove.CHOKE_SLAM) {
                    applyFlatDamage(attacker.baseDamage * 3.5f, target, attacker.isPlayer, attacker = attacker)
                    if (!target.isPlayer) target.tryCrumple(2.5f)
                    ctx.popup("-CHOKE SLAM-", target.posX, 120f, Color.Red) // Using hyphens so it passes word filter
                    ctx.sound(SoundType.CRUNCH)
                    return
                }
            }

            for (hitIdx in 0 until hitCount) {
                if (hitIdx == 0) meleeSweep(attacker, targets, dmgScale, isPiercingWeapon)
                else schedule(0.16f * hitIdx) {
                    if (!attacker.isDead && attacker.climbState == ClimbState.NONE) {
                        meleeSweep(attacker, targets, dmgScale, isPiercingWeapon)
                    }
                }
            }
        }
    }

    /**
     * The unmuzzled bear. Take the straps off Grimm and he stops being transport and starts being
     * a second combatant: he mauls whatever his rider is swinging at, and every so often it is one
     * of the rider's own men in his jaws instead. The reward card states that trade plainly.
     */
    private fun bearMaul(rider: FighterState, target: FighterState) {
        if (!rider.isBear || !rider.isBearUnmuzzled) return
        if (Random.nextFloat() >= BEAR_MAUL_CHANCE) return
        applyFlatDamage(BEAR_MAUL_DAMAGE, target, isPlayerSource = rider.isPlayer, attacker = rider, weaponNote = "a bear's jaws")
        ctx.popup("GRIMM MAULS!", target.posX, 165f, Color(0xFF8B4513))
        ctx.sound(SoundType.CRUNCH)

        // ...and sometimes it is one of your own in the jaws. Never the rider himself: being
        // thrown and eaten by your own mount is a run-ender, not a drawback.
        if (Random.nextFloat() < BEAR_FRIENDLY_FIRE_CHANCE) {
            val ally = ctx.enemies.filter {
                it.isPlayer && it !== rider && !it.isDead && !it.isDying &&
                    abs(it.posX - rider.posX) < BEAR_FRIENDLY_FIRE_PX
            }.randomOrNull() ?: return
            applyFlatDamage(BEAR_MAUL_DAMAGE * 0.6f, ally, isPlayerSource = true, attacker = rider, weaponNote = "a bear's jaws")
            ctx.popup("GRIMM BITES HIS OWN!", ally.posX, 165f, Color(0xFFB03030))
        }
    }

    /**
     * Skoll and Hati in harness. Two dogs at the front of a sledge cannot be steered and are not
     * trying to help: they take whatever the sledge brings them alongside. Two bites, each its own
     * roll, and no friendly fire — they are in front, and everything in front is the enemy.
     */
    private fun sleighBite(rider: FighterState, target: FighterState) {
        if (!rider.isSleigh) return
        var bit = 0
        repeat(2) {
            if (Random.nextFloat() < SLEIGH_BITE_CHANCE) {
                applyFlatDamage(
                    SLEIGH_BITE_DAMAGE, target, isPlayerSource = rider.isPlayer,
                    attacker = rider, weaponNote = "a sledge-dog's teeth"
                )
                bit++
            }
        }
        if (bit == 0) return
        ctx.popup(if (bit > 1) "BOTH HOUNDS!" else "THE HOUNDS BITE!", target.posX, 160f, Color(0xFF6B5B4A))
        MedievalAudioSynth.playDogBark("sleigh_${rider.id.raw}")
        // Dogs at the legs put men down. Never the rider of anything — a horseman is above them.
        if (!target.isMounted) target.tryCrumple(1.1f, chance = 0.25f)
    }

    // One swing sweeping through all gathered targets, with piercing falloff
    private fun meleeSweep(attacker: FighterState, targets: List<FighterState>, dmgScale: Float, isPiercingWeapon: Boolean) {
        targets.firstOrNull { !it.isDead && !it.isDying }?.let {
            bearMaul(attacker, it)
            sleighBite(attacker, it)
        }
        // This swing's own miss roll. A dual-wielder throws two of these, and one going wide says
        // nothing about the other hand.
        if (attacker.isDualWielding && Random.nextFloat() < DUAL_WIELD_MISS_CHANCE) {
            targets.firstOrNull()?.let { ctx.popup("MISS!", it.posX, 140f, Color.Gray) }
            ctx.sound(SoundType.SWOOSH)
            return
        }

        var damageFalloff = 1f

        for (currTarget in targets) {
            if (currTarget.isDead || currTarget.isDying || currTarget.climbState != ClimbState.NONE ||
                currTarget.isCombatInactive || currTarget.elevated != attacker.elevated
            ) continue

            // Point-blank falloff: a pike is murder at its tip and useless against a man already
            // inside the shaft. Pairs with the polearm spacing above — a spear build wants to keep
            // its distance, and a brute who closes the gap earns his kill. Reuses the existing
            // falloff so it flows into every damage type. Long weapons only; a fist has no dead zone.
            val attackerReachPx = attacker.reachPixels
            val pointBlankMult = if (!attacker.isRanged && attacker.reach > LONG_MELEE_REACH &&
                abs(attacker.posX - currTarget.posX) < attackerReachPx * LONG_MELEE_DEADZONE
            ) POINT_BLANK_DMG_MULT else 1f

            // Stilts hold you above the fight. A man on the ground mostly hits wood.
            if (currTarget.isStilts && !attacker.isStilts && !attacker.isMounted && Random.nextFloat() < STILTS_EVASION) {
                ctx.popup("TOO HIGH!", currTarget.posX, 150f, Color.Gray)
                ctx.sound(SoundType.SWOOSH)
                continue
            }

            // Speed Advantage: Capped melee dodge chance
            val meleeDodgeChance = (currTarget.moveSpeed * 0.0015f).coerceIn(0f, 0.25f)
            if (Random.nextFloat() < meleeDodgeChance) {
                ctx.popup("DODGE!", currTarget.posX, 140f, Color.Gray)
                ctx.sound(SoundType.SWOOSH)
                continue
            }

            // Speed Advantage: Interrupt slow enemy attack progress
            if (currTarget.isAttacking && (attacker.moveSpeed > currTarget.moveSpeed * 1.2f || attacker.size < currTarget.size * 0.95f) && !currTarget.isPlayer) {
                if (tryInterrupt(currTarget)) {
                    ctx.popup("INTERRUPT!", currTarget.posX, 150f, Color.Gray)
                }
            }

            // Block chance comes from the shield's sheer size/weight; every block costs shield HP.
            var blockChance = currTarget.shield.blockChance
            if (!currTarget.isPlayer && currTarget.shield.id == "shield_tower") blockChance = 0.8f

            val shieldBypass = if (attacker.weaponHead.id == "head_flail" || attacker.weaponHead.id == "head_war_flail" || attacker.weaponHandle.id == "handle_flail_chain") 0.4f else 0f
            blockChance *= (1f - shieldBypass)

            // Braced at the far end of the arc. A weapon with enormous reach was sweeping whole
            // rows of shielded men, none of whom ever got the shield up — but a man standing a
            // dozen paces off watches that head travel the entire way and has every chance to
            // cover himself. The further out he is, the better he brings the shield across.
            // Shields only: the isBlocked test below still requires one, so this changes nothing
            // for the bare-armed ranks. Enemies only — long enemy reach is rare and the player
            // has his own dodge and block rolls already.
            val reachMetres = abs(attacker.posX - currTarget.posX) / 40f
            if (!currTarget.isPlayer && reachMetres > LONG_MELEE_REACH) {
                blockChance += (reachMetres - LONG_MELEE_REACH) * REAR_RANK_BLOCK_PER_M
            }
            blockChance = blockChance.coerceAtMost(MAX_BLOCK_CHANCE)

            val isBlocked = currTarget.shieldCovers(attacker.posX > currTarget.posX) &&
                Random.nextFloat() < blockChance

            // Shared damage components
            val distToTarget = abs(attacker.posX - currTarget.posX)
            val attachmentDmgMultiplier = if (attacker.isRanged && distToTarget > 80f) 0f else 1f
            val attachSlash = attacker.extraAttachments.sumOf { it.slash.toDouble() * 0.5 }.toFloat()
            val attachPierce = attacker.extraAttachments.sumOf { it.pierce.toDouble() * 0.5 }.toFloat()
            val attachBlunt = attacker.extraAttachments.sumOf { it.blunt.toDouble() * 0.5 }.toFloat()
            val scaleLvl = if (attacker.isPlayer) 1.0f + (attacker.level - 1) * 0.12f else 1.0f
            val slash = ((attacker.damageSlash - attachSlash * scaleLvl) + attachSlash * scaleLvl * attachmentDmgMultiplier) * damageFalloff * pointBlankMult
            val pierce = ((attacker.damagePierce - attachPierce * scaleLvl) + attachPierce * scaleLvl * attachmentDmgMultiplier) * damageFalloff * pointBlankMult
            val blunt = ((attacker.damageBlunt - attachBlunt * scaleLvl) + attachBlunt * scaleLvl * attachmentDmgMultiplier) * damageFalloff * pointBlankMult
            val baseArmorFactor = (1f - (currTarget.totalArmor / 100f)).coerceIn(0.1f, 1f)
            // Armour-Piercing Stitch: a third of the damage the armour would have eaten gets through
            val armorFactor = if (attacker.isPlayer && ctx.hasArmorPiercing) {
                baseArmorFactor + (1f - baseArmorFactor) * ARMOR_PIERCE_FRACTION
            } else baseArmorFactor

            if (isBlocked) {
                // Blocked by shield!
                ctx.sound(SoundType.SHIELD_BLOCK)

                val blockDamage = ((slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt) * dmgScale

                if (currTarget.shieldHp > 0f) {
                    // Shield takes half damage — unless you took Shieldbreaker, which splinters it
                    val shieldMult = if (attacker.isPlayer && ctx.hasShieldbreaker) SHIELDBREAKER_MULT else 1f
                    currTarget.shieldHp -= blockDamage * 0.5f * shieldMult
                    if (currTarget.shieldHp <= 0f) {
                        currTarget.shieldHp = 0f
                        currTarget.shield = GameData.SHIELDS.first { it.id == "shield_none" }
                        ctx.sound(SoundType.CRUNCH)
                        ctx.popup("SHIELD BROKEN!", currTarget.posX, 160f, Color.LightGray)
                        // Add shattering particles
                        val px = currTarget.posX
                        val py = 140f
                        repeat(5) {
                            ctx.particle(BloodParticle(x = px + Random.nextInt(-10, 10), y = py + Random.nextInt(-10, 10), vx = (Random.nextFloat() * 100f - 50f), vy = -100f - Random.nextFloat() * 50f, color = Color(0xFF6E5536), isSmoke = false))
                        }
                    }
                }

                // Still take minimal blunt impact damage
                val bluntDamage = (attacker.damageBlunt * 0.15f * damageFalloff).coerceAtLeast(1f)
                if (bluntDamage > 5f && Random.nextBoolean()) ctx.sound(SoundType.CRUNCH)
                applyFlatDamage(bluntDamage, currTarget, attacker.isPlayer, attacker = attacker)
            } else {
                // Full hit!
                var totalDamage = ((slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt) * dmgScale

                // High-ground bonus: only ever non-zero on a hill (terrainLiftY is 0 on flat fields),
                // so a charge downhill hits harder and climbing to the crest neutralises it.
                if (HillField.hasHighGround(attacker, currTarget)) totalDamage *= HillField.DOWNHILL_MULT

                // Cupbearer strength bonus!
                if (attacker.isPlayer && ctx.unlockedAncillaries.contains(Ancillary.CUPBEARER)) {
                    totalDamage *= 1.25f // 25% strength boost from the cupbearer's refill
                }

                // A deep-level boss hits like one. No effect at all below BOSS_RAMP_START_LEVEL.
                totalDamage *= bossBrutality(attacker)

                applyFlatDamage(totalDamage, currTarget, attacker.isPlayer, attacker = attacker)

                // Rusted nails. Only on a full hit — a blocked swing leaves the nails in the shield.
                // maxOf rather than += so repeated hits refresh the wound instead of stacking it
                // into an instant kill.
                if (attacker.weaponHandle.id == "handle_plank") {
                    currTarget.applyDot(Dot.POISON, PLANK_POISON_SECONDS)
                }

                if (attacker.archetype == EnemyArchetype.DANE_AXE_EXECUTIONER && totalDamage > 0f) {
                    applyArmorShred(currTarget)
                }

                // Fists connect fast enough to stagger the defender out of their attack rhythm —
                // even between swings (requireSwing = false pushes the cooldown), but the same
                // streak cap applies so fists can't stun-lock either.
                if (attacker.isFists && Random.nextFloat() < FIST_INTERRUPT_CHANCE &&
                    tryInterrupt(currTarget, requireSwing = false)
                ) {
                    currTarget.attackCooldown = currTarget.attackCooldown.coerceAtLeast(0.4f)
                    ctx.popup("INTERRUPTUS!", currTarget.posX, 150f, Color.Yellow)
                }

                // A heavy blunt blow drives an enemy's head down into his shoulders, and it stays
                // there. Enemies only — the player keeps his dignity.
                if (blunt > 14f && !currTarget.isPlayer && Random.nextFloat() < HEAD_SQUASH_CHANCE) {
                    currTarget.headSquashed = true
                }

                // Knock off helmet randomly!
                if (Random.nextFloat() < 0.10f && currTarget.headgear.id != "helm_none") {
                    currTarget.headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }
                    ctx.sound(SoundType.CLANG)
                    ctx.popup("HELM LOST!", currTarget.posX, 120f, Color.LightGray)

                    // Spawn a particle for the helmet flying off
                    ctx.particle(BloodParticle(x = currTarget.posX, y = 80f, vx = (Random.nextFloat() * 100f - 50f), vy = -200f - Random.nextFloat() * 100f, color = Color.Gray, isSmoke = false))
                }

                // Play hit sounds & comedically yell in latin!
                if (totalDamage > 0f) {
                    // Iron rings, flesh (and cloth/leather/fur — and snail shell) thuds.
                    // The occasional bone crunch only ever comes off an unarmoured body.
                    val isCrunch = blunt > 15f && Random.nextFloat() < 0.4f
                    ctx.sound(when {
                        // Carpentry. The horse was screaming like a man every time it was hit.
                        currTarget.isInanimate -> SoundType.SHIELD_BLOCK
                        currTarget.wearsMetalArmour -> SoundType.ARMOUR_HIT
                        isCrunch -> SoundType.CRUNCH
                        else -> SoundType.FLESH
                    })

                    // The goose does most of the work, and it is audible about it. Welded on as an
                    // attachment counts too — it is still a goose, and it still has hold of him.
                    if (attacker.weaponHead.id == "head_goose" ||
                        attacker.extraAttachments.any { it.id == "head_goose" }
                    ) {
                        MedievalAudioSynth.playGooseBite()
                    }

                    // Random blood particles (the wooden horse splinters instead of bleeding)
                    if (!currTarget.isInanimate) {
                        val px = currTarget.posX + (Random.nextFloat() * 20f - 10f)
                        val py = 120f + (Random.nextFloat() * 60f - 30f)
                        ctx.particle(BloodParticle(x = px, y = py, vx = (Random.nextFloat() * 200f - 100f), vy = -150f - Random.nextFloat() * 150f, color = Color(0xFF8B0000)))
                    }
                }

                // Anything with fire in its hand sets what it hits alight: the torch bearer's brand,
                // and the Torch weapon head, which advertises exactly that and did nothing. Melee
                // only, by construction: this is the swing path. Only the torch BEARER may burn the
                // player — see applyIgnite — so a foe who happens to roll a Torch cannot.
                // A welded-on brand burns exactly as well as one held in the hand — a player who
                // lashes a Burning Brand to his axe is carrying fire and should set men alight.
                val bearsFire = attacker.archetype == EnemyArchetype.TORCH_BEARER ||
                    attacker.weaponHead.id == "head_torch" ||
                    attacker.extraAttachments.any { it.id == "head_torch" }
                if (bearsFire && totalDamage > 0f) {
                    applyIgnite(currTarget, onPlayer = attacker.archetype == EnemyArchetype.TORCH_BEARER)
                }

                // Brawler Bleeding (Spiked Wraps)
                if (attacker.brawlerUpgrades.contains("spiked_wraps") && totalDamage > 0f &&
                    !currTarget.isInanimate && Random.nextFloat() < 0.5f
                ) {
                    currTarget.applyDot(Dot.BLEED, 4.0f)
                    ctx.popup("+BLEEDING+", currTarget.posX, 120f, Color(0xFFA62B2B))
                }

                // Limb loss mechanic! (heavy slash)
                // Losing an arm ends a fight, so the two bodies a battle is built around keep both
                // until they are nearly finished. The player already had that grace; a boss who
                // takes it in the opening exchange spends the whole duel as a punching bag.
                val hpFraction = currTarget.hp / currTarget.maxHp
                val armThreshold = if (currTarget.bossType != null) BOSS_ARM_LOSS_HP_FRACTION else 0.10f
                val spared = currTarget.isPlayer || currTarget.bossType != null
                val canLoseArm = (!spared || hpFraction < armThreshold) && !currTarget.isInanimate
                if (slash > 18f && Random.nextFloat() < 0.2f * currTarget.ccResist &&
                    !currTarget.missingArm && canLoseArm
                ) {
                    currTarget.missingArm = true
                    // A man mid-suplex who loses the arm he was lifting with does not finish the
                    // move. triggerAttack already refuses to pick one, but a swing chosen before
                    // the arm came off carried on regardless — a one-armed man hoisting a housecarl.
                    currTarget.activeWrestlingMove = null
                    currTarget.applyDot(Dot.BLEED, ARM_BLEED_SECONDS) // the stump bleeds out
                    // Disarm off-hand/shield logically
                    if (currTarget.isDualWielding || currTarget.shield.id != "shield_none") {
                        currTarget.isDualWielding = false
                        GameData.SHIELDS.find { it == GameData.Shield.NONE }?.let { currTarget.shield = it }
                    }
                    applyFlatDamage(totalDamage, currTarget, attacker.isPlayer, attacker = attacker)
                    ctx.popup("-${totalDamage.toInt()}", currTarget.posX, 140f, Color.Red)

                    ctx.sound(SoundType.THWACK)
                    ctx.popup("ARM SEVERED!", currTarget.posX, 160f, Color.Red)

                    ctx.particle(BloodParticle(x = currTarget.posX, y = 140f, vx = (Random.nextFloat() * 100f - 50f), vy = -200f - Random.nextFloat() * 100f, color = Color(0xFF8B0000), isSmoke = false))
                }

                // Crumple mechanic (heavy blunt). The player HIMSELF is never floored by the host —
                // being knocked about is not a fight. His followers are fair game from
                // ENEMY_KNOCKDOWN_LEVEL, which is the counter to a late-game horde: a serjeant with
                // a maul wading into the retinue puts several of them on their backs at once.
                val canFloor = currTarget !== ctx.player &&
                    (attacker.isPlayer || attacker.level >= ENEMY_KNOCKDOWN_LEVEL)
                if (blunt > 18f && !attacker.isKind("raven") && canFloor &&
                    currTarget.tryCrumple(2.5f, chance = 0.25f)
                ) {
                    ctx.sound(SoundType.CRUNCH)
                    ctx.popup("-CRUMPLED-", currTarget.posX, 160f, Color.DarkGray)
                }

                // The sapper comes up out of the ground with a pouch full of it, and throws a
            // handful into the next man's face. Blinds and slows — he is a digger who fights
            // dirty, not a swordsman, and a spade alone was a dull thing to field.
            if (attacker.isKind("sapper") && totalDamage > 0f && !currTarget.isInanimate &&
                Random.nextFloat() < SAPPER_GRIT_CHANCE
            ) {
                currTarget.slowDuration = maxOf(currTarget.slowDuration, 2.2f)
                currTarget.panicDuration = maxOf(currTarget.panicDuration, 1.2f)
                ctx.popup("GRIT IN THE EYES!", currTarget.posX, 155f, Color(0xFF6E5536))
                repeat(8) {
                    ctx.particle(BloodParticle(
                        x = currTarget.posX + Random.nextInt(-12, 13),
                        y = 130f + Random.nextInt(-10, 11),
                        vx = Random.nextFloat() * 90f - 45f,
                        vy = -40f - Random.nextFloat() * 50f,
                        color = Color(0xFF6E5536), maxAge = 0.9f
                    ))
                }
            }

            // The moleman does not carry a weapon; he simply keeps hitting, and what he opens up
            // does not close. Bleed on every landed punch is the whole of his damage model.
            if (attacker.isKind("moleman") && totalDamage > 0f && !currTarget.isInanimate) {
                currTarget.applyDot(Dot.BLEED, 4.5f)
            }

            // A barrow-king hits like a falling wall: now and then he simply hurls a man away.
            // Only the SUPER_UNDEAD tier — the living bosses keep their footing-based fight.
            // GameViewModel clamps everyone to the field, so nobody is hurled off the map for good.
            if (attacker.bossTier == BossTier.SUPER_UNDEAD && !currTarget.isInanimate &&
                Random.nextFloat() < BARROW_KING_HURL_CHANCE
            ) {
                val away = if (attacker.posX < currTarget.posX) 1f else -1f
                // Skid, not a jump, same as the ballista shove — and scaled by ccResist so the
                // things that are simply too big to throw barely shift.
                currTarget.skidVelocityX = away * BARROW_KING_HURL_PX * currTarget.ccResist * SKID_DRAG_PER_SEC
                currTarget.tryCrumple(1.5f, chance = 0.5f)
                ctx.sound(SoundType.CRUNCH)
                ctx.popup("HURLED!", currTarget.posX, 150f, Color(0xFF9AA88C))
            }

            // Wardog trip mechanic!
                if (attacker.isKind("wardog") && !currTarget.isPlayer &&
                    currTarget.tryCrumple(2f, chance = 0.25f)
                ) {
                    ctx.sound(SoundType.CRUNCH)
                }

                // Splash damage for big heavy weapons!
                if (attacker.totalMass > 5.0f && attacker.damageSlash > 10f) { // heavy weapon like axe or claymore
                    val splashDmg = (totalDamage * 0.4f).coerceAtLeast(2f)
                    if (attacker.isPlayer) {
                        ctx.enemies.forEach { enemy ->
                            if (enemy != currTarget && !enemy.isDead && !enemy.isDying &&
                                !enemy.isCombatInactive && enemy.climbState == ClimbState.NONE &&
                                enemy.elevated == attacker.elevated &&
                                abs(enemy.posX - currTarget.posX) < 100f
                            ) {
                                applyFlatDamage(splashDmg, enemy, attacker.isPlayer, attacker = attacker)
                                ctx.popup("CLEAVE!", enemy.posX, 120f, Color(0xFF9E3624))
                            }
                        }
                    }
                }
            }

            if (isPiercingWeapon) {
                // Pike handle keeps most of its force as it skewers down the line
                damageFalloff *= if (attacker.weaponHandle.id == "handle_pike_long") 0.8f else 0.5f
            }
        }
    }

    /**
     * One ranged shot (arrow/stone/javelin) with all upgrade modifiers.
     *
     * [arcing] is the Volley reward: the same shot lobbed high so it drops on the rear ranks well
     * past normal range, fired alongside the flat shot rather than instead of it.
     */
    private fun fireRangedShot(attacker: FighterState, hitIdx: Int, arcing: Boolean = false) {
        val isPlayer = attacker.isPlayer
        val dir = if (attacker.facingRight) 1f else -1f
        var startX = attacker.posX + (dir * 25f)
        // Loose from where the shooter actually stands: a parapet archer's arrow leaves the wall
        // top, a hillside archer's leaves the slope — not a fixed ground height for everyone.
        var startY = 230f + elevationVisualOffset(attacker)

        if (hitIdx == 1) {
            startX -= (dir * 15f)
            startY -= 25f
        }

        // Stats based on weapon head
        val isSlingshot = attacker.weaponHead.id == "head_slingshot"
        val isJavelin = attacker.weaponHead.id == "head_javelin"
        val isCrossbow = attacker.weaponHead.id == "head_crossbow"
        val isBomb = attacker.weaponHead.id == "head_bomb"
        var projType = when {
            isSlingshot -> ProjectileType.STONE
            isJavelin -> ProjectileType.JAVELIN
            isCrossbow -> ProjectileType.BOLT
            // Draws as its own thing: a round pot with a lit cord, not an arrow.
            isBomb -> ProjectileType.BOMB
            else -> ProjectileType.ARROW
        }

        var sizeMult = 1f
        var spikes = false
        var launchedWep: String? = null
        var splash = false
        var poison = false
        var ballista = false
        var plaguing = false
        var armorPierce = 0f
        var cluster = 0
        // Declared up here rather than beside the torch branch: the bomb's fire upgrade sets it too.
        var igniting = false
        var finalDmg = attacker.baseDamage *
            if (attacker.elevated && !attacker.isPlayer) SiegeRules.DOWNHILL_DAMAGE_MULTIPLIER else 1f
        var finalPierce = attacker.damagePierce
        var finalBlunt = attacker.damageBlunt
        var velY = -55f // slightly arched trajectory
        var velX = dir * 350f
        var gravMult = 1f

        if (!attacker.isPlayer && attacker.level > 25) {
            velX *= 1.5f // Ranged Escalation: Projectile speed
            velY *= 1.2f
        }

        // The Far-Eastern bomb. Bursting is the WEAPON, not an upgrade — a bomb that hit one man
        // would just be a bad sling — so splash is on from the moment you pick it up, and it lobs
        // rather than flying flat.
        if (isBomb) {
            splash = true
            sizeMult = 1.3f
            gravMult = 1.5f
            velY -= 40f
            // More black powder: a bigger charge, a bigger pot, and it throws further.
            if (attacker.rangedUpgrades.contains("bomb_powder")) {
                finalDmg += 14f
                finalBlunt += 10f
                sizeMult += 0.5f
                velX *= 1.25f
            }
            // Shrapnel: the pot is packed with nails, so it sprays fragments the way the cluster
            // charge does. Same machinery, so the two stack into a genuinely silly amount of iron.
            if (attacker.rangedUpgrades.contains("bomb_shrapnel")) {
                spikes = true
                finalPierce += 10f
                armorPierce = 0.3f
                cluster += CLUSTER_FRAGMENTS
            }
            // Fire: the burst lights whoever it catches.
            if (attacker.rangedUpgrades.contains("bomb_fire")) {
                igniting = true
                finalDmg += 6f
            }
            // The late pot: nothing but scrap around the charge. Taken on top of the nails, not
            // instead of them, so a fully-worked bomb throws an absurd wall of iron.
            if (attacker.rangedUpgrades.contains("bomb_shrapnel_more")) {
                cluster += BOMB_SHRAPNEL_FRAGMENTS
                finalPierce += 8f
                sizeMult += 0.25f
            }
        }

        // The bombardier's pot. Reuses the splash machinery the player's own burst shot uses, so
        // it cleaves his massed archers exactly the way his cleaves the host's.
        if (attacker.archetype == EnemyArchetype.BOMBARDIER) {
            splash = true
            sizeMult = 1.6f
            finalBlunt += 12f
            gravMult = 1.4f   // a lobbed pot, not a flat shot
        }
        // The host's slingers learn their trade too: further, and eventually envenomed.
        if (!attacker.isPlayer && isSlingshot && attacker.level >= ENEMY_SLING_POISON_LEVEL) {
            poison = true
        }

        if (isSlingshot) {
            if (attacker.rangedUpgrades.contains("slingshot_bigger")) {
                sizeMult = 2.2f
                finalDmg += 10f
                finalBlunt += 10f
            }
            if (attacker.rangedUpgrades.contains("slingshot_spikes")) {
                spikes = true
                finalPierce += 8f
                armorPierce = 0.25f // barbs find the gaps in mail
            }
            if (attacker.rangedUpgrades.contains("slingshot_plague")) {
                plaguing = true
            }
            if (attacker.rangedUpgrades.contains("slingshot_weapon_heads")) {
                val heads = listOf("head_axe", "head_sword", "head_morningstar", "head_claymore", "head_halberd")
                launchedWep = heads.random()
                finalDmg *= 1.5f
            }
            if (attacker.rangedUpgrades.contains("slingshot_splash")) {
                splash = true
            }
            if (attacker.rangedUpgrades.contains("slingshot_poison")) {
                poison = true
            }
        } else if (isJavelin) {
            gravMult = 0.4f // glide!
            velY = -30f
            velX = dir * 380f
        } else {
            // Bow performance variance
            val varianceMult = Random.nextFloat() * 0.9f + 0.6f // 0.6 to 1.5
            finalDmg *= varianceMult
            finalPierce *= varianceMult
            velY = -55f + (Random.nextFloat() * 30f - 15f)
            velX = dir * (320f + Random.nextFloat() * 60f)

            if (attacker.isPlayer) {
                if (varianceMult < 0.8f) {
                    ctx.popup("POOR DRAW!", attacker.posX, 120f, Color.Gray)
                } else if (varianceMult > 1.2f) {
                    ctx.popup("PERFECT RELEASE!", attacker.posX, 120f, Color(0xFF4C613D))
                } else {
                    ctx.popup("STEADY AIM!", attacker.posX, 120f, Color(0xFF265063))
                }
            }

            if (attacker.rangedUpgrades.contains("bow_bigger")) {
                ballista = true
                sizeMult = 2.5f
                finalDmg += 15f
                velX *= 1.2f
            }
            if (attacker.rangedUpgrades.contains("bow_spikes")) {
                spikes = true
                finalPierce *= 1.8f
                armorPierce = 0.5f // the card has always said "ignores 50% armor"; now it does
            }
            if (attacker.rangedUpgrades.contains("bow_weapon_heads")) {
                val heads = listOf("head_axe", "head_sword", "head_morningstar")
                launchedWep = heads.random()
                finalDmg *= 1.4f
            }
        }

        // Cluster charge: a pot of flint and iron scrap that bursts on the mark. Rolled here so it
        // rides on top of whatever else the shot is carrying.
        if (attacker.rangedUpgrades.contains("cluster")) {
            cluster = CLUSTER_FRAGMENTS
        }

        // Late-game work on the missile itself, applied to every kind of shot rather than to one
        // weapon's branch — a bomb-thrower and a crossbowman both benefit from a better point, and
        // a build that has run out of upgrades to take is a build that has stopped progressing.
        if (attacker.rangedUpgrades.contains("ranged_sharpen")) {
            finalPierce *= 1.35f
            armorPierce = maxOf(armorPierce, 0.2f)
        }
        if (attacker.rangedUpgrades.contains("ranged_extra_tip")) {
            // A second head behind the first: it does not fly as sweetly, but what it hits, it keeps.
            finalDmg += 12f
            spikes = true
            gravMult *= 1.15f
        }
        if (attacker.rangedUpgrades.contains("ranged_fire")) {
            igniting = true
            finalDmg += 5f
        }
        if (attacker.rangedUpgrades.contains("ranged_plague")) {
            plaguing = true
        }
        // A bolt is a short heavy spike driven by a steel prod, and it always should have gone
        // through mail better than a shaft loosed off a stave.
        if (isCrossbow) {
            armorPierce = maxOf(armorPierce, CROSSBOW_ARMOUR_PIERCE)
            if (attacker.rangedUpgrades.contains("ranged_sharpen")) armorPierce = maxOf(armorPierce, 0.7f)
        }

        // Volley: lofted high, so it comes down well past where a flat shot dies. Applied last of
        // the trajectory maths or the bow/sling branches above would overwrite it.
        if (arcing) {
            velY = -235f
            velX *= VOLLEY_RANGE_MULT
            gravMult = 0.8f
        }

        var projId = "proj_${System.currentTimeMillis()}_${Random.nextInt(100)}"
        if (attacker.isKind("greaser")) {
            // A pot of rendered fat: it barely hurts, it makes them fall over.
            projId = "grease_pot_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            finalDmg = 2f
            splash = true
            projType = ProjectileType.ROCK
        }
        if (attacker.isKind("beekeeper")) {
            // A whole hive on a lazy arc. Bursts on the mark; everyone nearby is stung (splash),
            // and the victim carries the swarm (poison) a while.
            projId = "bee_hive_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            finalDmg = 4f
            splash = true
            projType = ProjectileType.ROCK
        }
        if (attacker.isKind("hag")) {
            projId = "hag_mud_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            finalDmg = 5f
            splash = true
            projType = ProjectileType.ROCK
        }
        if (attacker.isKind("firebrand")) {
            // Cinder Cedric: a lit torch on a lazy arc. Modest damage, but they burn.
            projId = "torch_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            projType = ProjectileType.TORCH
            finalDmg = 6f
            finalPierce = 0f
            finalBlunt = 2f
            igniting = true
        }
        if (attacker.archetype == EnemyArchetype.TORCH_BEARER) {
            projId = "torch_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            projType = ProjectileType.TORCH
            finalDmg = 4f
            finalPierce = 0f
            finalBlunt = 2f
            igniting = true
        }

        ctx.spawnProjectile(Projectile(
            id = projId,
            isPlayerOwned = isPlayer,
            posX = startX,
            posY = startY,
            velocityX = velX,
            velocityY = velY,
            damage = finalDmg,
            pierce = finalPierce,
            blunt = finalBlunt,
            type = projType,
            sizeMultiplier = sizeMult,
            hasSpikes = spikes,
            launchedWeaponId = launchedWep,
            isSplash = splash,
            isPoisonous = poison,
            isBallista = ballista,
            isIgniting = igniting,
            // Was computed for javelins ("glide!") and every arcing shot and then never passed, so
            // every missile in the game fell at the same rate regardless.
            gravityMult = gravMult,
            isPlaguing = plaguing,
            isArcing = arcing,
            armorPierceFraction = armorPierce,
            clusterCount = cluster,
            sourceFighterId = attacker.id,
            launchLiftY = elevationVisualOffset(attacker)
        ))
    }

    /** Burst a cluster charge at the point of impact: scrap sprayed both ways off the mark. */
    private fun burstCluster(proj: Projectile, atX: Float, atY: Float) {
        repeat(proj.clusterCount) { i ->
            val spread = (i - (proj.clusterCount - 1) / 2f)
            ctx.spawnProjectile(Projectile(
                id = "cluster_${System.currentTimeMillis()}_${Random.nextInt(1000)}_$i",
                isPlayerOwned = proj.isPlayerOwned,
                posX = atX,
                posY = atY,
                velocityX = spread * 150f + (Random.nextFloat() * 60f - 30f),
                velocityY = -120f - Random.nextFloat() * 60f,
                damage = proj.damage * CLUSTER_FRAGMENT_DAMAGE,
                pierce = proj.pierce * CLUSTER_FRAGMENT_DAMAGE,
                blunt = proj.blunt * CLUSTER_FRAGMENT_DAMAGE,
                type = ProjectileType.ROCK,
                sizeMultiplier = 0.45f,
                armorPierceFraction = proj.armorPierceFraction,
                // No clusterCount: fragments must never burst again, or one shot fills the field.
                sourceFighterId = proj.sourceFighterId,
                launchLiftY = proj.launchLiftY
            ))
        }
        ctx.sound(SoundType.CRUNCH)
    }

    /**
     * The pot goes off: a ball of grey powder-smoke and a few embers where it struck. Public
     * because the gate hit in [GameViewModel] bursts the same pot against the door.
     */
    fun bombBurst(atX: Float, atY: Float) {
        repeat(16) {
            val ember = it % 4 == 0
            ctx.particle(
                BloodParticle(
                    x = atX + Random.nextInt(-22, 23),
                    y = atY.coerceIn(90f, 320f) + Random.nextInt(-16, 17),
                    vx = Random.nextFloat() * 220f - 110f,
                    vy = -60f - Random.nextFloat() * 130f,
                    color = if (ember) Color(0xFFD6A420) else Color(0xFF6B6459),
                    maxAge = if (ember) 0.5f + Random.nextFloat() * 0.3f else 1.1f + Random.nextFloat() * 0.7f
                )
            )
        }
        ctx.screenshake(10f)
        ctx.sound(SoundType.CRUNCH)
        MedievalAudioSynth.playBombBurst()
    }

    fun applyProjectileDamage(proj: Projectile, defender: FighterState) {
        if (defender.climbState != ClimbState.NONE) return
        // A cluster charge bursts wherever it stops, deflected or blocked or buried in a man.
        if (proj.clusterCount > 0) burstCluster(proj, proj.posX, proj.posY)
        // A little puff of powder-smoke where the pot cracked, so a burst reads as a burst.
        if (proj.type == ProjectileType.BOMB) bombBurst(proj.posX, proj.posY)
        val eyeCritCandidate = proj.sourceFighterId == ctx.player?.id && proj.type.isArrowLike &&
            defender.bossType == BossType.HAROLD_GODWINSON && defender.arrowEyeCritWindow > 0f
        // Speed Advantage: Ranged deflection based on speed. Tiny Terrence is a very small man
        // moving very fast, and being nearly unshootable is the only reason he survives the run
        // across the field — he has almost no hp and no armour worth the name.
        val deflectionChance = if (defender.isKind("tiny_terrence")) TERRENCE_DODGE
            else (defender.moveSpeed * 0.002f).coerceIn(0f, 0.35f)
        if (!eyeCritCandidate && Random.nextFloat() < deflectionChance) {
            ctx.sound(SoundType.SWOOSH)
            ctx.popup("DEFLECT!", defender.posX, 120f, Color.Gray)
            return
        }

        // Ranged hit calculation — same weight-based block chance as melee, and the same blind
        // side: a shaft travelling leftwards came from his right. Shot in the back, no shield.
        val isBlocked = !eyeCritCandidate &&
            defender.shieldCovers(attackFromRight = proj.velocityX < 0f) &&
            Random.nextFloat() < defender.shield.blockChance

        if (isBlocked) {
            ctx.sound(SoundType.SHIELD_BLOCK)
            if (proj.type.isArrowLike) {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, true, proj.isBallista))
                if (defender.stuckProjectiles.size > 12) defender.stuckProjectiles.removeAt(0)
            }

            if (defender.shieldHp > 0f) {
                defender.shieldHp -= proj.damage * 0.3f
                if (defender.shieldHp <= 0f) {
                    defender.shieldHp = 0f
                    defender.shield = GameData.SHIELDS.first { it.id == "shield_none" }
                    ctx.sound(SoundType.CRUNCH)
                    ctx.popup("SHIELD BROKEN!", defender.posX, 160f, Color.LightGray)
                    val px = defender.posX
                    val py = 140f
                    repeat(5) {
                        ctx.particle(BloodParticle(x = px + Random.nextInt(-10, 10), y = py + Random.nextInt(-10, 10), vx = (Random.nextFloat() * 100f - 50f), vy = -100f - Random.nextFloat() * 50f, color = Color(0xFF6E5536), isSmoke = false))
                    }
                }
            }
        } else {
            // Arrows bury themselves in a thick timber flank and do very little. The men inside
            // are another matter — once they burst out they are ordinary flesh.
            val timberSoak = if (defender.isInanimate) INANIMATE_RANGED_SOAK else 1f
            // A bodkin or a barb ignores part of the mail outright, per armorPierceFraction.
            val effectiveArmor = defender.totalArmor * (1f - proj.armorPierceFraction)
            val armorFactor = (1f - (effectiveArmor / 100f)).coerceIn(0.15f, 1f)
            val eyeCrit = eyeCritCandidate
            val totalDamage = ((proj.damage * armorFactor) + (proj.blunt * 0.6f)) *
                (if (eyeCrit) 3f else 1f) * timberSoak
            // Missile striking home: iron rings, everything else thuds
            ctx.sound(when {
                defender.isInanimate -> SoundType.SHIELD_BLOCK // timber, not a man
                defender.wearsMetalArmour -> SoundType.ARMOUR_HIT
                else -> SoundType.FLESH
            })
            applyFlatDamage(totalDamage, defender, proj.isPlayerOwned, attacker = shooterOf(proj), weaponNote = missileNote(proj))
            if (eyeCrit) {
                defender.arrowEyeCritWindow = 0f
                defender.arrowEyeCritCooldown = 5f
                ctx.popup("SAGITTA IN OCULO!", defender.posX, 100f, Color.Yellow)
            }

            if (proj.type.isArrowLike) {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, false, proj.isBallista))
                if (defender.stuckProjectiles.size > 12) defender.stuckProjectiles.removeAt(0)
            }

            // Ballista Spears say "knocks foes back" on the card and did nothing of the kind.
            // Never against the player: being shoved around by archery is not a fight.
            if (proj.isBallista && !defender.isPlayer && !defender.isInanimate) {
                val shove = if (proj.velocityX >= 0f) BALLISTA_KNOCKBACK_PX else -BALLISTA_KNOCKBACK_PX
                // Launch speed, not a teleport. The old `posX +=` moved him the whole 45px between
                // one frame and the next, which read as the man blinking backwards.
                defender.skidVelocityX = shove * defender.ccResist * SKID_DRAG_PER_SEC
                defender.tryCrumple(1.2f, chance = 0.35f)
            }
            // Apply Poison Upgrade. Stacks with the hag's venom and with the plague peasant's rot
            // rather than overwriting either — see FighterState.applyDot.
            if (proj.isPoisonous) {
                defender.applyDot(Dot.POISON, 5.0f)
                val doses = defender.dotStacks[Dot.POISON.ordinal]
                ctx.popup(
                    if (doses > 1) "+POISONED x$doses+" else "+POISONED+",
                    defender.posX, 120f, Color(0xFF2E7D32)
                )
            }
            // Plague-tipped shot: the sling's answer to a peasant's miasma, and it stacks on it.
            if (proj.isPlaguing) {
                defender.applyDot(Dot.DISEASE, DISEASE_DURATION)
                ctx.popup("+PESTILENT+", defender.posX, 135f, Color(0xFF6B7D4A))
            }
            if (proj.isIgniting) applyIgnite(defender)

            // Bee hive burst: not poison (that's the hag's trade) — stings hurt NOW, and the victim
            // flails at the swarm instead of fighting: slowed, and his next swing is delayed.
            if (proj.id.startsWith("bee_hive_")) {
                applyFlatDamage(10f, defender, proj.isPlayerOwned, attacker = shooterOf(proj), weaponNote = missileNote(proj))
                defender.slowDuration = 2.5f
                defender.attackCooldown = (defender.attackCooldown + 1.2f).coerceAtMost(3f)
                ctx.popup("STUNG!", defender.posX, 140f, Color(0xFFD6A420))
                // Direct call rather than ctx.sound: that route only carries synth SoundTypes, and
                // this is a recording folder. Harmless with no app context — playFolder returns false.
                // Keyed to the beekeeper who threw it: one man's hives no longer talk over each
                // other, but a second beekeeper's swarm still sounds at the same time.
                MedievalAudioSynth.playBeeSwarm(shooterOf(proj)?.id?.raw ?: "bee")
                repeat(10) {
                    ctx.particle(
                        BloodParticle(
                            x = defender.posX + Random.nextInt(-20, 21),
                            y = 110f + Random.nextInt(-15, 16),
                            vx = Random.nextFloat() * 160f - 80f,
                            vy = Random.nextFloat() * -60f - 10f,
                            color = if (it % 2 == 0) Color(0xFFD6A420) else Color(0xFF2C2219),
                            maxAge = 1.5f + Random.nextFloat()
                        )
                    )
                }
            }

            // Hag Mud effect
            if (proj.id.startsWith("hag_mud_")) {
                defender.slowDuration = 3.0f
                defender.applyDot(Dot.POISON, 3.0f)
                ctx.popup("SLIMED!", defender.posX, 140f, Color(0xFF384033))
                repeat(8) {
                    ctx.particle(
                        BloodParticle(
                            x = defender.posX + Random.nextInt(-18, 19),
                            y = 115f + Random.nextInt(-12, 13),
                            vx = Random.nextFloat() * 120f - 60f,
                            vy = -80f - Random.nextFloat() * 100f,
                            color = Color(0xFF2E7D32)
                        )
                    )
                }
            }

            // Grease pot: skid over and flounder. Mounted foes keep their feet — a rider doesn't
            // slip, same rule as the kiting stumble.
            if (proj.id.startsWith("grease_pot_")) {
                defender.slowDuration = GREASE_SLOW_SECS
                if (!defender.isMounted) {
                    defender.tryCrumple(GREASE_TRIP_SECS, chance = GREASE_TRIP_CHANCE)
                }
            }

            // Apply Spikes Bleed
            if (proj.hasSpikes && !defender.isInanimate) { // timber does not bleed
                defender.applyDot(Dot.BLEED, 4.0f)
                ctx.popup("+BLEEDING+", defender.posX, 120f, Color(0xFFA62B2B))
            }

            // Apply Splash Upgrade
            if (proj.isSplash) {
                ctx.popup("SPLASH SPLIT!", defender.posX, 110f, Color(0xFF8A7156))
                val splashDmg = (totalDamage * 0.5f).coerceAtLeast(2f)
                // Symmetrical. An enemy splash used to reach ctx.player and nobody else, so a
                // horde of followers stood inside a burst untouched while the same upgrade in the
                // player's hands cleaved a whole rank. The player's own branch had no side filter
                // either and was quietly splashing his own retinue.
                val bystanders = ctx.enemies.filter { it.isPlayer != proj.isPlayerOwned } +
                    listOfNotNull(ctx.player?.takeUnless { proj.isPlayerOwned })
                bystanders.forEach { other ->
                    if (other !== defender && !other.isDead && !other.isDying &&
                        abs(other.posX - defender.posX) < 120f
                    ) {
                        applyFlatDamage(splashDmg, other, proj.isPlayerOwned, attacker = shooterOf(proj), weaponNote = missileNote(proj))
                        ctx.popup("SPLASH!", other.posX, 140f, Color(0xFF9E3624))
                    }
                }
            }
        }
    }

    /** [quiet] = damage-over-time: no blood spray, no screenshake, no decals. They cost frames. */
    /**
     * @param attacker who swung, when known — recorded on the defender so the defeat screen can
     *   name the killer. Left null by sources with no author (weather, bleed ticks, collisions),
     *   which deliberately leaves the last real attacker standing as the attribution.
     * @param weaponNote overrides the attacker's armament, for missiles that outlive their shooter.
     */
    fun applyFlatDamage(
        dmg: Float,
        defender: FighterState,
        isPlayerSource: Boolean = false,
        quiet: Boolean = false,
        attacker: FighterState? = null,
        weaponNote: String? = null
    ) {
        if (defender.isDead || defender.isDying || defender.climbState != ClimbState.NONE) return
        if (attacker != null && attacker !== defender) {
            defender.slayerName = attacker.name
            defender.slayerWeapon = weaponNote ?: attacker.weaponDescription
        } else if (weaponNote != null) {
            // An anonymous missile: better "felled by an arrow" than a stale name from the melee.
            defender.slayerName = null
            defender.slayerWeapon = weaponNote
        }
        var finalDmg = dmg
        if (!defender.isPlayer && defender.armor.id == "armor_bare") {
            finalDmg *= 1.5f
        }

        val finalDmgInt = finalDmg.coerceAtLeast(1f).toInt().toFloat()

        if (defender.isMounted && defender.mountHp > 0f) {
            defender.mountHp -= finalDmgInt
            if (defender.mountHp <= 0f) {
                defender.mountHp = 0f
                defender.isMounted = false
                ctx.popup("MOUNT SHATTERED!", defender.posX, 130f, Color.Gray)
                ctx.sound(SoundType.CRUNCH)
                // Throne collapse: the whole retinue is crushed, the lord fights on bare-handed
                if (defender.isLord) {
                    defender.isLord = false
                    ctx.enemies.filter { it.pallbearerIndex >= 0 && !it.isDead && !it.isDying }.forEach { bearer ->
                        bearer.pallbearerIndex = -1
                        bearer.isDying = true
                        bearer.animFrame = 0f
                        bearer.deathType = DeathType.randomTame()
                        bearer.deathTime = System.currentTimeMillis()
                    }
                    ctx.popup("THE THRONE FALLS!", defender.posX, 110f, Color.Red)
                }
            }
        } else {
            defender.hp = (defender.hp - finalDmgInt).coerceAtLeast(0f)
            if (!quiet && !defender.isInanimate) {
                val rx = Random.nextFloat() * 14f - 7f
                val ry = Random.nextFloat() * 20f - 10f
                defender.bloodDecals.add(Triple(rx, ry, Random.nextInt(6)))
                if (defender.bloodDecals.size > 30) defender.bloodDecals.removeAt(0) // cap: decals stack forever otherwise
            }
        }

        defender.damageIndicator = "-${finalDmgInt.toInt()}"
        defender.damageIndicatorTimer = 0.5f

        if (!quiet) {
            // Trigger screenshake on hit! (Reduced unless absolute unit)
            val shakeMultiplier = if ((ctx.player?.size ?: 1.0f) > 1.2f) 1.5f else 0.4f
            ctx.screenshake((finalDmgInt * shakeMultiplier).coerceIn(4f, 35f))

            // Spawn blood particles based on damage
            if (!defender.isInanimate) {
                ctx.bloodParticles(defender.posX, 160f * defender.size, (finalDmgInt / 4).toInt().coerceIn(3, 10))
            }
        }

        if (defender.hp <= 0f) {
            if (defender.isMounted && Random.nextFloat() < 0.5f) {
                defender.hp = 1f
                defender.isMounted = false
                ctx.popup("DISMOUNTED!", defender.posX, 130f, Color.Gray)
                if (isPlayerSource) ctx.enemyKilled()
                return
            }
            defender.isDying = true
            defender.animFrame = 0f
            // Killed while already crumpled on the ground → die where they lie, no standing back up
            defender.deathType = if (defender.crumpleDuration > 0f) DeathType.CRUMPLED_IN_PLACE else DeathType.randomAny()
            defender.deathTime = System.currentTimeMillis()
            // A brawler who stole this man's weapon drops it now that its owner is dead — back to fists.
            (listOfNotNull(ctx.player) + ctx.enemies).forEach { thief ->
                if (thief.stolenWeaponOwnerId == defender.id) {
                    thief.weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
                    thief.weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" }
                    thief.stolenWeaponOwnerId = null
                }
            }
            // A plague carrier bursts when he falls. The passive carrierNear() miasma needed a foe
            // parked within 70px on the exact tick the corpse was still warm, which the melee shoving
            // made unreliable — so his death now deliberately infects everyone in a wider radius.
            // This is his whole purpose; it shouldn't hinge on where the scrum happened to drift.
            if (defender.isContagious) {
                ctx.enemies.filter {
                    !it.isPlayer && !it.isDead && abs(it.posX - defender.posX) <= DEATH_PLAGUE_BURST_PX
                }.forEach { victim ->
                    victim.applyDot(Dot.DISEASE, DISEASE_DURATION)
                    victim.isContagious = true // it spreads on from here — that's a plague
                }
            }
            ctx.sound(SoundType.OUCH)
            val deathShout = if (defender.isPlayer) "VÆ MIHI MORTIS!" else "AARRGGHH!"
            ctx.popup(deathShout, defender.posX, 130f, Color.DarkGray)
            if (isPlayerSource && !defender.isPlayer) {
                ctx.enemyKilled()
            }
        } else if (!quiet && Random.nextBoolean()) {
            // quiet = DoT trickle: a 1hp poison tick must not yelp — it spammed the pain channel
            ctx.sound(SoundType.OUCH)
        }
    }

    /**
     * @param onPlayer whether this source is allowed to set the PLAYER alight. Only the torch
     *   bearer's brand passes true, and only in melee: an unavoidable ranged igniter is chip
     *   damage you cannot answer, whereas a man walking at you with a burning brand is a threat
     *   you can read and back away from.
     *
     * Burning the player is deliberately survivable. The existing guard — no re-ignite while
     * already alight — is what stops a pack of them chaining it into a death sentence, and the
     * player's burn is shorter and cooler than an enemy's on top of that. It always runs out.
     */
    fun applyIgnite(defender: FighterState, onPlayer: Boolean = false) {
        val isPlayer = defender === ctx.player
        if (isPlayer && !onPlayer) return
        // The player's burn still never stacks and never re-arms mid-burn: a chain-ignite is an
        // unavoidable death rather than a fight. A foe touched by a second brand burns harder.
        if (isPlayer) {
            if (defender.igniteDuration > 0f) return
            defender.igniteDuration = PLAYER_IGNITE_DURATION
        } else {
            defender.applyDot(Dot.IGNITE, IGNITE_DURATION)
        }
        ctx.popup("IGNIS!", defender.posX, 120f, Color(0xFFE07020))
    }

    fun applyArmorShred(defender: FighterState) {
        defender.armorShred = (defender.armorShred + ARMOR_SHRED_PER_HIT)
            .coerceAtMost(defender.armor.defense + defender.headgear.defense +
                defender.extraArmors.sumOf { it.defense.toDouble() }.toFloat())
        ctx.popup("ARMATURA RUPTA!", defender.posX, 145f, Color.LightGray)
    }
}
