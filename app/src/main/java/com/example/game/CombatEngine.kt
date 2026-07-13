package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.random.Random

/** Everything the combat rules need from the battle around them. */
interface BattleContext {
    val player: FighterState?
    val enemies: List<FighterState>
    val levelWidth: Float
    val unlockedAncillaries: Set<Ancillary>
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

        // Trojan horse never fights: it rolls right past the enemy line, then bursts open
        if (fighter.id == "trojan_horse") {
            val foes = ctx.enemies.filter { !it.isDead && !it.isDying && !it.isPlayer }
            if (foes.any { it.posX > fighter.posX - 60f } && fighter.posX < ctx.levelWidth - 80f) {
                fighter.posX += fighter.moveSpeed * 0.7f * dt
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

        // Poison tick over time
        if (fighter.poisonDuration > 0f) {
            fighter.poisonDuration -= dt
            val poisonDmg = 4f * dt // deals 4 damage per second (halved)
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) { // occasionally show green "+POISON+" popup
                    ctx.popup("POISON!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF2E7D32))
                }
                applyFlatDamage(poisonDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }

        // Bleed tick over time
        if (fighter.bleedDuration > 0f) {
            fighter.bleedDuration -= dt
            val bleedDmg = 3f * dt // 3 damage per second — 6/s melted enemies too fast
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) {
                    ctx.popup("BLEED!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFFA62B2B))
                    ctx.bloodParticles(fighter.posX, 100f, 3)
                }
                applyFlatDamage(bleedDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }

        // Slow tick over time
        if (fighter.slowDuration > 0f) {
            fighter.slowDuration -= dt
        }

        // Crumple tick over time
        if (fighter.crumpleDuration > 0f) {
            fighter.crumpleDuration -= dt
        }

        // Cooldown tick
        if (fighter.attackCooldown > 0) {
            val cooldownRate = if (!fighter.isPlayer && fighter.armor.id == "armor_bare") 1.3f else 1f
            fighter.attackCooldown -= dt * cooldownRate
        }

        // Update Swing Progress
        if (fighter.isAttacking) {
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
                if (distToTarget < fighter.reach * 40f + 60f) {
                    val p = effectiveSwingProgress.coerceIn(0f, 1f)
                    val liftMax = if (isChokeSlam) -140f else if (isSuplex) -90f else if (fighter.activeWrestlingMove == WrestlingMove.BODY_THROW) -70f else 0f
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
                    val reachPixels = fighter.reach * 40f + 40f
                    if (dist <= reachPixels && fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                        triggerAttack(fighter)
                    }
                }
                return
            }
        }

        // Decide movement & actions
        if (target != null && !target.isDead && fighter.crumpleDuration <= 0f) {
            val dist = abs(fighter.posX - target.posX)
            val reachPixels = fighter.reach * 40f + 40f // generous hitbox
            val rangeMult = if (!fighter.isPlayer && fighter.level > 5) 0.8f + (fighter.level - 5) * 0.05f else 0.8f
            val optimalDistance = if (fighter.isRanged) reachPixels * rangeMult else reachPixels
            val isShieldWall = !fighter.isPlayer && fighter.shield.id == "shield_tower"

            fighter.facingRight = target.posX > fighter.posX

            if (dist > optimalDistance) {
                // Walk closer
                val direction = if (target.posX > fighter.posX) 1f else -1f
                val moveMult = if (isShieldWall) 0.6f else 1f
                fighter.posX += direction * fighter.moveSpeed * moveMult * dt
                // Desync animations slightly based on maxHp to avoid identical marching
                fighter.animFrame = fighter.animFrame + dt * (9f + (fighter.maxHp % 3f))
            } else if (dist < optimalDistance * 0.7f && fighter.moveSpeed > 0f) {
                // Step back to keep them at the tip of our longer weapon!
                val direction = if (target.posX > fighter.posX) -1f else 1f
                val retreatSpeed = if (fighter.isRanged) fighter.moveSpeed else (fighter.moveSpeed * 0.45f)
                fighter.posX += direction * retreatSpeed * dt
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
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking && !fighter.isLord) {
                    triggerAttack(fighter)
                }
            }

            // Clamp position to level bounds
            fighter.posX = fighter.posX.coerceIn(30f, ctx.levelWidth - 30f)
        }
    }

    fun triggerAttack(fighter: FighterState) {
        fighter.isAttacking = true
        fighter.swingProgress = 0f

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
                    // One free hand is enough to grab a throat — shield-and-fist builds slam too, just rarer
                    !fighter.isDualWielding && rand < 0.12f -> WrestlingMove.CHOKE_SLAM
                    !fighter.isDualWielding && rand < 0.2f -> WrestlingMove.BODY_THROW
                    else -> null
                }
            }
        }

        var cooldown = fighter.attackSpeedDelay
        if (!fighter.isPlayer && fighter.isRanged && fighter.level > 15) {
            cooldown *= 0.7f // Ranged Escalation: Faster attack speed
        }
        fighter.attackCooldown = cooldown

        // Play melee/ranged swing swoosh sound at start of attack animation
        ctx.sound(SoundType.SWOOSH)
    }

    private fun performStrike(attacker: FighterState, defender: FighterState) {
        if (attacker.isRanged) {
            val isDualWielding = attacker.isDualWielding && attacker.shield.id == "shield_none"
            var hitCount = if (isDualWielding) 2 else 1

            if (!attacker.isPlayer && attacker.level > 20) {
                hitCount += 1 // Ranged Escalation: Multishot
            }

            for (hitIdx in 0 until hitCount) {
                if (hitIdx == 0) fireRangedShot(attacker, hitIdx)
                else schedule(0.16f * hitIdx) { if (!attacker.isDead) fireRangedShot(attacker, hitIdx) }
            }
        } else {
            // Melee hit
            val reachPixels = attacker.reach * 40f + 40f // generous hitbox
            val isPiercingWeapon = attacker.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd")

            // Gather all targets in a line if we are using a piercing weapon
            val targets = if (attacker.isPlayer && isPiercingWeapon) {
                val dir = if (attacker.facingRight) 1f else -1f
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels &&
                    ((dir > 0 && it.posX >= attacker.posX - 30f) || (dir < 0 && it.posX <= attacker.posX + 30f))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer && attacker.weaponHandle.id == "handle_double_ended") {
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer) {
                val dir = if (attacker.facingRight) 1f else -1f
                ctx.enemies.filter {
                    !it.isDead && !it.isDying && it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels &&
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

            // Dual Wield miss chance (higher for dual wield in general)
            if (attacker.isDualWielding && Random.nextFloat() < 0.35f) {
                ctx.popup("MISS!", defender.posX, 140f, Color.Gray)
                ctx.sound(SoundType.SWOOSH)
                return
            }

            val hitCount = if (attacker.weaponHandle.id == "handle_double_ended") {
                if (attacker.isDualWielding) 4 else 2
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

                    target.weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
                    target.weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" }

                    ctx.popup("STOLEN!", target.posX, 130f, Color.Yellow)
                    ctx.sound(SoundType.CLANG)
                    // We continue into the regular attack loop below to hit them with their own weapon!
                } else if (attacker.activeWrestlingMove == WrestlingMove.SUPLEX) {
                    val secondTarget = targets.drop(1).firstOrNull() ?: target
                    applyFlatDamage(60f, target, attacker.isPlayer)
                    applyFlatDamage(60f, secondTarget, attacker.isPlayer)
                    if (!target.isPlayer) target.crumpleDuration = 3.5f
                    if (!secondTarget.isPlayer) secondTarget.crumpleDuration = 3.5f
                    ctx.popup("SUPLEX!", target.posX, 120f, Color.Red)
                    ctx.sound(SoundType.CRUNCH)
                    return
                } else if (attacker.activeWrestlingMove == WrestlingMove.BODY_THROW) {
                    // Hurl the grabbed enemy down the line — if he lands on a mate, both go down
                    val dir = if (attacker.facingRight) 1f else -1f
                    applyFlatDamage(45f, target, attacker.isPlayer)
                    if (!target.isPlayer) {
                        target.crumpleDuration = 3f
                        target.posX += dir * 140f
                        target.targetX = target.posX
                    }
                    val second = targets.drop(1).firstOrNull { abs(it.posX - target.posX) < 60f }
                    if (second != null) {
                        applyFlatDamage(30f, second, attacker.isPlayer)
                        if (!second.isPlayer) second.crumpleDuration = 3f
                        ctx.popup("BOWLED OVER!", second.posX, 120f, Color.Red)
                    }
                    ctx.popup("HURLED!", target.posX, 120f, Color.Red)
                    ctx.sound(SoundType.CRUNCH)
                    return
                } else if (attacker.activeWrestlingMove == WrestlingMove.CHOKE_SLAM) {
                    applyFlatDamage(attacker.baseDamage * 3.5f, target, attacker.isPlayer)
                    if (!target.isPlayer) target.crumpleDuration = 2.5f
                    ctx.popup("-CHOKE SLAM-", target.posX, 120f, Color.Red) // Using hyphens so it passes word filter
                    ctx.sound(SoundType.CRUNCH)
                    return
                }
            }

            for (hitIdx in 0 until hitCount) {
                if (hitIdx == 0) meleeSweep(attacker, targets, dmgScale, isPiercingWeapon)
                else schedule(0.16f * hitIdx) { if (!attacker.isDead) meleeSweep(attacker, targets, dmgScale, isPiercingWeapon) }
            }
        }
    }

    // One swing sweeping through all gathered targets, with piercing falloff
    private fun meleeSweep(attacker: FighterState, targets: List<FighterState>, dmgScale: Float, isPiercingWeapon: Boolean) {
        var damageFalloff = 1f

        for (currTarget in targets) {
            if (currTarget.isDead || currTarget.isDying) continue

            // Speed Advantage: Capped melee dodge chance
            val meleeDodgeChance = (currTarget.moveSpeed * 0.0015f).coerceIn(0f, 0.25f)
            if (Random.nextFloat() < meleeDodgeChance) {
                ctx.popup("DODGE!", currTarget.posX, 140f, Color.Gray)
                ctx.sound(SoundType.SWOOSH)
                continue
            }

            // Speed Advantage: Interrupt slow enemy attack progress
            if (currTarget.isAttacking && (attacker.moveSpeed > currTarget.moveSpeed * 1.2f || attacker.size < currTarget.size * 0.95f) && !currTarget.isPlayer) {
                currTarget.isAttacking = false
                currTarget.swingProgress = 0f
                ctx.popup("INTERRUPT!", currTarget.posX, 150f, Color.Gray)
            }

            var blockChance = currTarget.shield.defense / 100f
            if (!currTarget.isPlayer && currTarget.shield.id == "shield_tower") blockChance = 0.8f

            val shieldBypass = if (attacker.weaponHead.id == "head_flail" || attacker.weaponHead.id == "head_war_flail" || attacker.weaponHandle.id == "handle_flail_chain") 0.4f else 0f
            blockChance *= (1f - shieldBypass)

            val isBlocked = currTarget.shield.id != "shield_none" && Random.nextFloat() < blockChance

            // Shared damage components
            val distToTarget = abs(attacker.posX - currTarget.posX)
            val attachmentDmgMultiplier = if (attacker.isRanged && distToTarget > 80f) 0f else 1f
            val attachSlash = attacker.extraAttachments.sumOf { it.slash.toDouble() * 0.5 }.toFloat()
            val attachPierce = attacker.extraAttachments.sumOf { it.pierce.toDouble() * 0.5 }.toFloat()
            val attachBlunt = attacker.extraAttachments.sumOf { it.blunt.toDouble() * 0.5 }.toFloat()
            val scaleLvl = if (attacker.isPlayer) 1.0f + (attacker.level - 1) * 0.12f else 1.0f
            val slash = ((attacker.damageSlash - attachSlash * scaleLvl) + attachSlash * scaleLvl * attachmentDmgMultiplier) * damageFalloff
            val pierce = ((attacker.damagePierce - attachPierce * scaleLvl) + attachPierce * scaleLvl * attachmentDmgMultiplier) * damageFalloff
            val blunt = ((attacker.damageBlunt - attachBlunt * scaleLvl) + attachBlunt * scaleLvl * attachmentDmgMultiplier) * damageFalloff
            val armorFactor = (1f - (currTarget.totalArmor / 100f)).coerceIn(0.1f, 1f)

            if (isBlocked) {
                // Blocked by shield!
                ctx.sound(SoundType.CLANG)

                val blockDamage = ((slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt) * dmgScale

                if (currTarget.shieldHp > 0f) {
                    currTarget.shieldHp -= blockDamage * 0.5f // Shield takes half damage
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
                applyFlatDamage(bluntDamage, currTarget, attacker.isPlayer)
            } else {
                // Full hit!
                var totalDamage = ((slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt) * dmgScale

                // Cupbearer strength bonus!
                if (attacker.isPlayer && ctx.unlockedAncillaries.contains(Ancillary.CUPBEARER)) {
                    totalDamage *= 1.25f // 25% strength boost from wine!
                }

                applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)

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
                    val isCrunch = blunt > 15f && Random.nextFloat() < 0.4f
                    ctx.sound(if (isCrunch) SoundType.CRUNCH else SoundType.THWACK)

                    // Random blood particles
                    val px = currTarget.posX + (Random.nextFloat() * 20f - 10f)
                    val py = 120f + (Random.nextFloat() * 60f - 30f)
                    ctx.particle(BloodParticle(x = px, y = py, vx = (Random.nextFloat() * 200f - 100f), vy = -150f - Random.nextFloat() * 150f, color = Color(0xFF8B0000)))
                }

                // Brawler Bleeding (Spiked Wraps)
                if (attacker.brawlerUpgrades.contains("spiked_wraps") && totalDamage > 0f && Random.nextFloat() < 0.5f) {
                    currTarget.bleedDuration = 4.0f
                    ctx.popup("+BLEEDING+", currTarget.posX, 120f, Color(0xFFA62B2B))
                }

                // Limb loss mechanic! (heavy slash)
                val canLoseArm = !currTarget.isPlayer || (currTarget.hp / currTarget.maxHp < 0.10f)
                if (slash > 18f && Random.nextFloat() < 0.2f && !currTarget.missingArm && canLoseArm) {
                    currTarget.missingArm = true
                    // Disarm off-hand/shield logically
                    if (currTarget.isDualWielding || currTarget.shield.id != "shield_none") {
                        currTarget.isDualWielding = false
                        GameData.SHIELDS.find { it == GameData.Shield.NONE }?.let { currTarget.shield = it }
                    }
                    applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)
                    ctx.popup("-${totalDamage.toInt()}", currTarget.posX, 140f, Color.Red)

                    ctx.sound(SoundType.THWACK)
                    ctx.popup("ARM SEVERED!", currTarget.posX, 160f, Color.Red)

                    ctx.particle(BloodParticle(x = currTarget.posX, y = 140f, vx = (Random.nextFloat() * 100f - 50f), vy = -200f - Random.nextFloat() * 100f, color = Color(0xFF8B0000), isSmoke = false))
                }

                // Crumple mechanic! (heavy blunt) - enemies can't knock the player down, only the reverse
                if (blunt > 18f && attacker.id != "raven" && Random.nextFloat() < 0.25f && currTarget.crumpleDuration <= 0f && !currTarget.isPlayer) {
                    currTarget.crumpleDuration = 2.5f
                    ctx.sound(SoundType.CRUNCH)
                    ctx.popup("-CRUMPLED-", currTarget.posX, 160f, Color.DarkGray)
                }

                // Wardog trip mechanic!
                if (attacker.id == "wardog" && Random.nextFloat() < 0.25f && currTarget.crumpleDuration <= 0f && !currTarget.isPlayer) {
                    currTarget.crumpleDuration = 2f
                    ctx.sound(SoundType.CRUNCH)
                }

                // Splash damage for big heavy weapons!
                if (attacker.totalMass > 5.0f && attacker.damageSlash > 10f) { // heavy weapon like axe or claymore
                    val splashDmg = (totalDamage * 0.4f).coerceAtLeast(2f)
                    if (attacker.isPlayer) {
                        ctx.enemies.forEach { enemy ->
                            if (enemy != currTarget && !enemy.isDead && !enemy.isDying && abs(enemy.posX - currTarget.posX) < 100f) {
                                applyFlatDamage(splashDmg, enemy, attacker.isPlayer)
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

    // One ranged shot (arrow/stone/javelin) with all upgrade modifiers
    private fun fireRangedShot(attacker: FighterState, hitIdx: Int) {
        val isPlayer = attacker.isPlayer
        val dir = if (attacker.facingRight) 1f else -1f
        var startX = attacker.posX + (dir * 25f)
        var startY = 230f

        if (hitIdx == 1) {
            startX -= (dir * 15f)
            startY -= 25f
        }

        // Stats based on weapon head
        val isSlingshot = attacker.weaponHead.id == "head_slingshot"
        val isJavelin = attacker.weaponHead.id == "head_javelin"
        val isCrossbow = attacker.weaponHead.id == "head_crossbow"
        var projType = if (isSlingshot) ProjectileType.STONE else if (isJavelin) ProjectileType.JAVELIN else if (isCrossbow) ProjectileType.BOLT else ProjectileType.ARROW

        var sizeMult = 1f
        var spikes = false
        var launchedWep: String? = null
        var splash = false
        var poison = false
        var ballista = false
        var finalDmg = attacker.baseDamage
        var finalPierce = attacker.damagePierce
        var finalBlunt = attacker.damageBlunt
        var velY = -55f // slightly arched trajectory
        var velX = dir * 350f
        var gravMult = 1f

        if (!attacker.isPlayer && attacker.level > 25) {
            velX *= 1.5f // Ranged Escalation: Projectile speed
            velY *= 1.2f
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
            }
            if (attacker.rangedUpgrades.contains("bow_weapon_heads")) {
                val heads = listOf("head_axe", "head_sword", "head_morningstar")
                launchedWep = heads.random()
                finalDmg *= 1.4f
            }
        }

        var projId = "proj_${System.currentTimeMillis()}_${Random.nextInt(100)}"
        if (attacker.id == "hag") {
            projId = "hag_mud_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            finalDmg = 5f
            splash = true
            projType = ProjectileType.ROCK
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
            isBallista = ballista
        ))
    }

    fun applyProjectileDamage(proj: Projectile, defender: FighterState) {
        // Speed Advantage: Ranged deflection based on speed
        val deflectionChance = (defender.moveSpeed * 0.002f).coerceIn(0f, 0.35f)
        if (Random.nextFloat() < deflectionChance) {
            ctx.sound(SoundType.SWOOSH)
            ctx.popup("DEFLECT!", defender.posX, 120f, Color.Gray)
            return
        }

        // Ranged hit calculation
        val isBlocked = defender.shield.id != "shield_none" && Random.nextFloat() < (defender.shield.defense / 110f)

        if (isBlocked) {
            ctx.sound(SoundType.CLANG)
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
            val armorFactor = (1f - (defender.totalArmor / 100f)).coerceIn(0.15f, 1f)
            val totalDamage = (proj.damage * armorFactor) + (proj.blunt * 0.6f)
            applyFlatDamage(totalDamage, defender, proj.isPlayerOwned)

            if (proj.type.isArrowLike) {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, false, proj.isBallista))
                if (defender.stuckProjectiles.size > 12) defender.stuckProjectiles.removeAt(0)
            }
            // Apply Poison Upgrade
            if (proj.isPoisonous) {
                defender.poisonDuration = 5.0f
                ctx.popup("+POISONED+", defender.posX, 120f, Color(0xFF2E7D32))
            }

            // Hag Mud effect
            if (proj.id.startsWith("hag_mud_")) {
                defender.slowDuration = 3.0f
                defender.poisonDuration = 3.0f
                ctx.popup("SLIMED!", defender.posX, 140f, Color(0xFF384033))
            }

            // Apply Spikes Bleed
            if (proj.hasSpikes) {
                defender.bleedDuration = 4.0f
                ctx.popup("+BLEEDING+", defender.posX, 120f, Color(0xFFA62B2B))
            }

            // Apply Splash Upgrade
            if (proj.isSplash) {
                ctx.popup("SPLASH SPLIT!", defender.posX, 110f, Color(0xFF8A7156))
                val splashDmg = (totalDamage * 0.5f).coerceAtLeast(2f)
                if (proj.isPlayerOwned) {
                    ctx.enemies.forEach { enemy ->
                        if (enemy != defender && !enemy.isDead && !enemy.isDying && abs(enemy.posX - defender.posX) < 120f) {
                            applyFlatDamage(splashDmg, enemy, proj.isPlayerOwned)
                            ctx.popup("SPLASH!", enemy.posX, 140f, Color(0xFF9E3624))
                        }
                    }
                } else {
                    ctx.player?.let { player ->
                        if (player != defender && !player.isDead && !player.isDying && abs(player.posX - defender.posX) < 120f) {
                            applyFlatDamage(splashDmg, player, proj.isPlayerOwned)
                            ctx.popup("SPLASH!", player.posX, 140f, Color(0xFF9E3624))
                        }
                    }
                }
            }
        }
    }

    fun applyFlatDamage(dmg: Float, defender: FighterState, isPlayerSource: Boolean = false) {
        if (defender.isDead || defender.isDying) return
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
            val rx = Random.nextFloat() * 14f - 7f
            val ry = Random.nextFloat() * 20f - 10f
            defender.bloodDecals.add(Triple(rx, ry, Random.nextInt(6)))
            if (defender.bloodDecals.size > 30) defender.bloodDecals.removeAt(0) // cap: decals stack forever otherwise
        }

        defender.damageIndicator = "-${finalDmgInt.toInt()}"
        defender.damageIndicatorTimer = 0.5f

        // Trigger screenshake on hit! (Reduced unless absolute unit)
        val shakeMultiplier = if ((ctx.player?.size ?: 1.0f) > 1.2f) 1.5f else 0.4f
        ctx.screenshake((finalDmgInt * shakeMultiplier).coerceIn(4f, 35f))

        // Spawn blood particles based on damage
        ctx.bloodParticles(defender.posX, 160f * defender.size, (finalDmgInt / 2).toInt().coerceIn(5, 20))

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
            ctx.sound(SoundType.OUCH)
            val deathShout = if (defender.isPlayer) "VÆ MIHI MORTIS!" else "AARRGGHH!"
            ctx.popup(deathShout, defender.posX, 130f, Color.DarkGray)
            if (isPlayerSource && !defender.isPlayer) {
                ctx.enemyKilled()
            }
        } else if (Random.nextBoolean()) {
            ctx.sound(SoundType.OUCH)
        }
    }
}

