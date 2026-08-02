package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal fun fighterCullMargin(fighter: FighterState, scale: Float): Float =
    (120f * fighter.size + fighter.reach * 40f +
        if (fighter.isMounted || fighter.isChariot || fighter.isStilts) 120f else 0f) * scale

internal fun splitThroneBattleActors(
    fighters: List<FighterState>
): Pair<List<FighterState>, List<FighterState>> =
    fighters.partition { it.pallbearerIndex >= 2 }

/**
 * Heads that are thrown rather than swung or thrust. One list: the front arm and the back arm
 * each decide the attack animation for themselves, and keeping two hand-written copies is how
 * the javelin ended up throwing properly on one of them and swinging on the other.
 */
internal val OVERARM_THROW_HEAD_IDS = setOf("head_javelin", "head_bomb")

object TapestryRenderer {

    /** Plague palette: the pallor of the sick, and Aldwin's mud-coloured rags. */
    // Jaundiced yellow, NOT green: the hag's poison owns green, and the two statuses were
    // indistinguishable on the field. Disease is yellow everywhere — flesh, rags and status icon.
    private val PlagueFlesh = Color(0xFFC9B03C)
    private val PlagueRags = Color(0xFFA8912F)

    /**
     * How far the frog's tongue reaches on screen at full extension. This is the DRAWN length, in
     * the frog's own body space; the rules use FROG_TONGUE_REACH_PX in world space. They are
     * deliberately separate numbers — the tongue must look like it arrives just past its victim.
     */
    private const val FROG_TONGUE_DRAW_PX = 210f

    /**
     * The cyclops head is drawn this much larger than a man's, about the neck join. The design is
     * Harryhausen's rather than Homer's, and the whole read of it is that the head — and the eye in
     * it — is too big for the body carrying it.
     */
    private const val CYCLOPS_HEAD_SCALE = 1.85f
    private val PlagueBubo = Color(0xFF6B4A1F) // dark swollen lumps against the yellow

    /** The war-priest's habit — monk's undyed wool, so he reads as a churchman among soldiers. */
    private val MonkBrown = Color(0xFF5E4B3C)

    /** Woad: the old blue, daubed on before a fight. */
    private val Woad = Color(0xFF3A5A8C)

    /**
     * Draw a character (Player Norman Knight or Saxon Enemy)
     */
    fun drawCharacter(
        drawScope: DrawScope,
        fighter: FighterState,
        scale: Float = 1.0f,
        isBattleActive: Boolean = true
    ) {
        val effectiveSize = if (fighter.isMounted && fighter.size > 1.3f) 1.3f + (fighter.size - 1.3f) * 0.5f else fighter.size

        // A barrow-king burns with corpse-light. Drawn BEFORE the transform and before the body so
        // it sits behind him as a halo rather than a tint on him — a player who has walked into
        // one of these needs to know from across the field that this is not the ordinary undead.
        if (fighter.bossTier == BossTier.SUPER_UNDEAD && !fighter.isDead) {
            // Positioned the way the body transform below positions itself: screen scale pivots
            // at 200, so a point at local y maps to 200 + (y - 200) * scale. Computing this in
            // raw coordinates instead left the halo hanging below the man's feet.
            val haloR = 210f * effectiveSize * scale
            val cx = fighter.posX
            val cy = 200f + (250f - 200f) * scale
            listOf(1.0f to 0.10f, 0.72f to 0.14f, 0.46f to 0.20f).forEach { (rf, alpha) ->
                drawScope.drawCircle(
                    color = Color(0xFF8FE3B0).copy(alpha = alpha),
                    radius = haloR * rf,
                    center = Offset(cx, cy)
                )
            }
        }

        drawScope.withTransform({
            // Apply scale (e.g. for flip/facing and overall sizing)
            val hFlip = if (fighter.facingRight) 1f else -1f
            // Screen scale pivots at 200 (as the scene layout expects); the fighter's own
            // size scales around the feet (358) so any size stands on the ground line
            // instead of floating (small) or sinking (large).
            scale(hFlip * scale, scale, pivot = Offset(fighter.posX, 200f))
            // Elevation is world-space: apply it after scene scale but before fighter size.
            translate(top = elevationVisualOffset(fighter))
            // Size scales from the feet so everyone stands on the ground — except the raven,
            // which pivots at shoulder height so it flies instead of shrinking into the floor
            val sizePivotY = if (fighter.isKind("raven")) 240f else 358f
            // The raven's sim size is tiny for balance; render it at proper raven size
            val renderSize = if (fighter.isKind("raven")) effectiveSize.coerceAtLeast(0.85f) else effectiveSize
            scale(renderSize, renderSize, pivot = Offset(fighter.posX, sizePivotY))
        }) {
            val cx = fighter.posX
            val cy = 200f
            val bounceY = -kotlin.math.abs(sin(fighter.animFrame.toDouble())).toFloat() * 10f

            // 1. Draw dropped weapon on the ground if dead or dying
            if (fighter.isDead || fighter.isDying) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                val weaponGroundX = cx + if (fighter.facingRight) (50f * progress) else (-50f * progress)
                val weaponGroundY = cy + 142f
                
                withTransform({
                    rotate(if (fighter.facingRight) 75f else -75f, pivot = Offset(weaponGroundX, weaponGroundY))
                }) {
                    drawWeapon(this, weaponGroundX, weaponGroundY, fighter)
                }
            }

            // Handle dying fall down rotation
            var rotationAngle = 0f
            // Lil Guy used to be special-cased here by id, but he has no FighterState at all —
            // he is drawn by drawAncillaries off the Ancillary enum, so these never fired.
            var offsetX = 0f
            var offsetY = fighter.visualOffsetY + bounceY
            var scaleY = 1f
            if (fighter.crumpleDuration > 0f && !fighter.isDead && !fighter.isDying) {
                // Ragdolled onto the floor. A slip while backpedalling lands the other way up —
                // see FighterState.slipped.
                val backwards = if (fighter.slipped) -1f else 1f
                rotationAngle = backwards * (if (fighter.facingRight) -90f else 90f)
                offsetY += 60f
                offsetX += backwards * (if (fighter.facingRight) -30f else 30f)
            }
            if (fighter.isDead || fighter.isDying) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                
                if (fighter.isMounted) {
                    rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                    offsetY = 30f * progress
                    offsetX = if (fighter.facingRight) -30f * progress else 30f * progress
                } else when (fighter.deathType) {
                    DeathType.FALL_BACK -> { // Fall backwards
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                    DeathType.FACEPLANT -> { // Faceplant forward
                        rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                        offsetY = 65f * progress
                    }
                    DeathType.CARTWHEEL -> { // Cartwheel of death
                        rotationAngle = if (fighter.facingRight) 630f * progress else -630f * progress
                        offsetY = 65f * progress
                    }
                    DeathType.PANCAKE -> { // Squashed pancake falling over
                        rotationAngle = if (fighter.facingRight) -85f * progress else 85f * progress
                        scaleY = (if (fighter.crumpleDuration > 0f) 0.6f else 1f) - 0.7f * progress
                        offsetY = 65f * progress
                    }
                    DeathType.KNOCKED_FLYING -> { // Knocked flying backwards landing flat
                        val flyDir = if (fighter.facingRight) -1f else 1f
                        rotationAngle = flyDir * 270f * progress
                        offsetX = flyDir * 150f * progress
                        offsetY = 65f * progress - 100f * sin(progress * Math.PI).toFloat()
                    }
                    DeathType.KNEEL_KEEL -> { // Sink to the knees, then keel over sideways
                        val kneel = (progress * 2f).coerceAtMost(1f)
                        val keel = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
                        offsetY = 35f * kneel + 30f * keel
                        rotationAngle = if (fighter.facingRight) -90f * keel else 90f * keel
                    }
                    DeathType.SKY_LAUNCH -> { // Launched skyward spinning, crashes down flat
                        val flyDir = if (fighter.facingRight) -1f else 1f
                        rotationAngle = flyDir * 540f * progress
                        offsetX = flyDir * 80f * progress
                        offsetY = 65f * progress - 160f * sin(progress * Math.PI).toFloat()
                    }
                    DeathType.CRUMPLED_IN_PLACE -> { // Was already crumpled on the ground — dies where they lie
                        rotationAngle = if (fighter.facingRight) -90f else 90f
                        offsetX = if (fighter.facingRight) -30f else 30f
                        offsetY = 65f
                    }
                    DeathType.DECAPITATED -> { // Fall flat (the blood fountain is drawn separately)
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                }
            }

            if ((fighter.isMounted && !fighter.isStilts) || fighter.isChariot) {
                var horseRot = 0f
                if (fighter.isDead || fighter.isDying) {
                    val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                    horseRot = if (fighter.facingRight) -15f * progress else 15f * progress
                }
                withTransform({ 
                    rotate(horseRot, pivot = Offset(cx, cy + 80f)) 
                    val rng = kotlin.random.Random(fighter.id.raw.hashCode())
                    val horseScale = 0.9f + rng.nextFloat() * 0.2f
                    scale(horseScale / effectiveSize, horseScale / effectiveSize, Offset(cx, cy + 80f))
                }) {
                    if (fighter.isChariot) {
                        drawChariot(this, cx, cy, fighter)
                    } else if (fighter.isLord) {
                        drawThrone(this, cx, cy, fighter, isBattleActive)
                    } else if (fighter.isOx) {
                        drawWarOx(this, cx, cy, fighter)
                    } else if (fighter.isMule) {
                        drawPackMule(this, cx, cy, fighter)
                    } else if (fighter.isBear) {
                        drawWarBear(this, cx, cy, fighter)
                    } else {
                        drawHorse(this, cx, cy, fighter)
                    }
                }
            }

            // Draw blood pool and stream BEFORE the ragdoll transform so it stays flat on the floor AND underneath the body!
            // Nothing wooden bleeds. Every other blood path already checks isInanimate; these two
            // did not, so the Trojan horse — size 1.8, and the pools scale with size — collapsed
            // into a lake three times the width of a man's.
            if ((fighter.isDead || fighter.isDying) && fighter.deathType == DeathType.DECAPITATED &&
                !fighter.isInanimate) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                val fountainProgress = progress.coerceIn(0f, 1f)
                if (fountainProgress > 0.05f) {
                    val flyDir = if (fighter.facingRight) -1f else 1f
                    
                    // The pool is where the neck ultimately lands, but clamped to the floor level (cy+150f is feet)
                    val poolCenterX = cx + flyDir * 70f * effectiveSize
                    val poolCenterY = cy + 150f 
                    
                    // 2. Draw Pool
                    val poolProgress = ((fountainProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    if (poolProgress > 0f) {
                        val poolW = 45f * poolProgress * effectiveSize
                        val poolH = 15f * poolProgress * effectiveSize
                        drawScope.drawOval(
                            color = Color(0xFF9E3624).copy(alpha = 0.85f),
                            topLeft = Offset(poolCenterX - poolW, poolCenterY - poolH),
                            size = androidx.compose.ui.geometry.Size(poolW * 2f, poolH * 2f)
                        )
                        drawScope.drawOval(
                            color = Color(0xFF6E2215).copy(alpha = 0.5f),
                            topLeft = Offset(poolCenterX - poolW * 0.6f, poolCenterY - poolH * 0.4f),
                            size = androidx.compose.ui.geometry.Size(poolW * 1.2f, poolH * 0.8f)
                        )
                    }
                }
            }

            // Persistent blood pool for dying/dead fighters (all death types except deathType 5 which has its own)
            if (fighter.isDead && fighter.deathType != DeathType.DECAPITATED && !fighter.isInanimate) {
                // Time since death drives pool expansion
                val timeSinceDeath = (System.currentTimeMillis() - fighter.deathTime) / 1000f
                val progress = (timeSinceDeath * 0.5f).coerceIn(0f, 1f)
                if (progress > 0f) {
                    val poolProgress = progress
                    val groundY = cy + 155f
                    val fallOffset = if (fighter.facingRight) -50f else 50f
                    val poolW = 55f * poolProgress * effectiveSize
                    val poolH = 12f * poolProgress * effectiveSize
                    drawScope.drawOval(
                        color = Color(0xAA600000).copy(alpha = 0.66f * poolProgress),
                        topLeft = Offset((cx + fallOffset) - poolW, groundY - poolH),
                        size = androidx.compose.ui.geometry.Size(poolW * 2f, poolH * 2f)
                    )
                }
            }

            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                // The ox and bear are taller through the shoulder than a horse, the mule a good deal
                // shorter — the rider sits at the saddle each one actually draws.
                val mountOffsetY = if (fighter.isChariot) -15f
                    else if (fighter.isMounted && fighter.isLord) -20f - THRONE_LIFT
                    else if (fighter.isMounted && fighter.isStilts) -STILTS_LIFT_PX
                    else if (fighter.isMounted && fighter.isOx) -38f
                    else if (fighter.isMounted && fighter.isMule) -22f
                    else if (fighter.isMounted && fighter.isBear) -40f
                    else if (fighter.isMounted) -35f else 0f
                val adjustedMountOffsetY = mountOffsetY / effectiveSize
                withTransform({ translate(0f, adjustedMountOffsetY) }) {
                    if (fighter.isKind("trojan_horse")) {
                        drawTrojanHorse(this, cx, cy, fighter)
                    } else if (fighter.isKind("wardog")) {
                        drawWardog(this, cx, cy, fighter)
                    } else if (fighter.isKind("raven")) {
                        drawRaven(this, cx, cy, fighter)
                    } else if (fighter.archetype == EnemyArchetype.REBEL_SNAIL) {
                        drawRebelSnail(this, cx, cy, fighter)
                    } else if (fighter.archetype == EnemyArchetype.GIANT_FROG) {
                        drawGiantFrog(this, cx, cy, fighter)
                    } else {
                        // Behind the body, like the boss cloaks, so the figure stands in front of
                        // his own cape rather than wearing it as a bib.
                        drawCape(this, cx, cy, fighter)
                        drawStandard(this, cx, cy, fighter)
                        drawBossSignature(this, cx, cy, fighter)
                        if (fighter.isStilts) {
                            // Poles live in the body's space so they scale and swing with the man who is on them.
                            drawStilts(this, cx, cy, fighter, effectiveSize)
                        }
                        if (!fighter.isChariot) {
                            drawLegs(this, cx, cy, fighter)
                        }
                        drawTorso(this, cx, cy, fighter)
                        drawHead(this, cx, cy, fighter)
                        drawBackArmAndShield(this, cx, cy, fighter)
                        // Nothing but the man falls. The rail lives inside the RIDER's transform
                        // (it has to, to be drawn over him), which means it inherited his ragdoll
                        // rotation — so a dying charioteer took a brown box spinning round with him
                        // while his actual cart, rotated separately with the mount, stayed put. He
                        // has fallen out of it; the cart from drawChariot is still there.
                        if (fighter.isChariot && !fighter.isDead && !fighter.isDying) {
                            // The cart's front rail, drawn over the rider so he stands *in* the cart.
                            // It must sit in the same space as the cart body from drawChariot — this
                            // used to be drawn in the rider's own scale, so a small man got a second,
                            // smaller cart bouncing along in front of the real one.
                            val railRng = kotlin.random.Random(fighter.id.raw.hashCode())
                            val railScale = (0.9f + railRng.nextFloat() * 0.2f) / effectiveSize
                            withTransform({
                                translate(0f, -adjustedMountOffsetY) // undo the rider's lift
                                scale(railScale, railScale, Offset(cx, cy + 80f)) // match the mount layer
                            }) {
                                val cartW = 110f
                                val cartH = 65f
                                val cartTopY = cy + 35f
                                drawRect(Color(0xFF8B5A2B), topLeft = Offset(cx - cartW / 2, cartTopY), size = androidx.compose.ui.geometry.Size(cartW, cartH))
                                drawRect(ThreadColor, topLeft = Offset(cx - cartW / 2, cartTopY), size = androidx.compose.ui.geometry.Size(cartW, cartH), style = StitchedStroke)
                                drawLine(Color(0xFF5C4033), Offset(cx - cartW / 2, cartTopY + 30f), Offset(cx + cartW / 2, cartTopY + 30f), strokeWidth = 3f)
                                drawLine(Color(0xFF5C4033), Offset(cx - cartW / 2, cartTopY + 60f), Offset(cx + cartW / 2, cartTopY + 60f), strokeWidth = 3f)
                            }
                        }
                        drawFrontArmAndWeapon(this, cx, cy, fighter)
                    }
                }
            }
        }
    }

    /**
     * The Rebel Snail of the marginalia: a great slug body under a spiral shell, eyestalks raised.
     * Attack anim: it rears back through the windup, then the whole head-end lunges forward and the
     * eyestalks whip. Death: it slumps flat and the shell rolls off-true.
     */
    /**
     * The Gaping Frog. Squat, wide and low, in the plague peasant's sick ochre-green, with the
     * mouth open the whole time it is hunting — the tell is that you can see the inside of it.
     *
     * The tongue is drawn straight off [FighterState.tongueExtend], the same 0..1 the rules run on,
     * so what you see is exactly the state the frog is in: shut, reaching, stuck, or reeling in.
     */
    private fun drawGiantFrog(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val dir = if (fighter.facingRight) 1f else -1f
        val skin = Color(0xFF6E7A3A)        // marginalia bog-green
        val belly = Color(0xFFBFAF6A)
        val mouthDark = Color(0xFF3A2622)
        val tonguePink = Color(0xFFD4728A)
        val deathP = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f)
            else if (fighter.isDead) 1f else 0f
        // Breathing throat, and a squat down onto the haunches as the tongue goes out
        val throat = sin(fighter.animFrame * 1.6f) * 3f
        val crouch = fighter.tongueExtend * 10f

        scope.withTransform({
            if (deathP > 0f) {
                // Frogs die on their backs.
                rotate(170f * deathP * dir, pivot = Offset(cx, cy + 140f))
            }
        }) {
            // Haunches: one big rear leg folded up behind, the shape that says frog at any size
            val haunch = Path().apply {
                moveTo(cx - dir * 88f, cy + 142f)
                quadraticTo(cx - dir * 96f, cy + 62f + crouch, cx - dir * 44f, cy + 66f + crouch)
                quadraticTo(cx - dir * 20f, cy + 104f, cx - dir * 30f, cy + 142f)
                close()
            }
            drawStitchedFill(scope, haunch, skin)
            scope.drawStitchedOutline(haunch, ThreadColor)

            // Body: wide, low, belly slung on the ground
            val body = Path().apply {
                moveTo(cx - dir * 74f, cy + 142f)
                quadraticTo(cx - dir * 78f, cy + 74f + crouch, cx, cy + 70f + crouch)
                quadraticTo(cx + dir * 62f, cy + 66f + crouch, cx + dir * 86f, cy + 104f)
                quadraticTo(cx + dir * 92f, cy + 142f, cx + dir * 40f, cy + 142f)
                close()
            }
            drawStitchedFill(scope, body, skin)
            scope.drawStitchedOutline(body, ThreadColor)

            // Pale throat sac, pulsing
            val sac = Path().apply {
                moveTo(cx + dir * 16f, cy + 128f)
                quadraticTo(cx + dir * 52f, cy + 138f + throat, cx + dir * 82f, cy + 122f)
                quadraticTo(cx + dir * 50f, cy + 146f, cx + dir * 16f, cy + 140f)
                close()
            }
            drawStitchedFill(scope, sac, belly)

            // Front foot, splayed and toed. Kept low and short on purpose: drawn any longer or any
            // higher the toes come out level with the gape and read as the tongue.
            for (t in 0..2) {
                scope.drawLine(
                    skin, Offset(cx + dir * 66f, cy + 140f),
                    Offset(cx + dir * (82f + t * 5f), cy + 146f + t * 2f),
                    strokeWidth = 6f, cap = StrokeCap.Round
                )
            }

            // The gape. Open whenever it is hunting, which is the warning.
            val gape = 1f
            val mouth = Path().apply {
                moveTo(cx + dir * 44f, cy + 96f)
                quadraticTo(cx + dir * 78f, cy + 92f, cx + dir * 90f, cy + 104f)
                quadraticTo(cx + dir * 76f, cy + (104f + 26f * gape), cx + dir * 44f, cy + 112f)
                close()
            }
            drawStitchedFill(scope, mouth, mouthDark)
            scope.drawStitchedOutline(mouth, ThreadColor)

            // Eye: high, domed, on top of the skull the way a frog's is
            val eyeX = cx + dir * 30f
            val eyeY = cy + 62f + crouch
            scope.drawCircle(skin, radius = 17f, center = Offset(eyeX, eyeY))
            scope.drawCircle(ThreadColor, radius = 17f, center = Offset(eyeX, eyeY), style = StitchedStroke)
            scope.drawCircle(Color(0xFFE8C34D), radius = 10f, center = Offset(eyeX, eyeY))
            // A horizontal slit pupil, and a dead frog's eye rolls over
            scope.drawLine(
                mouthDark,
                Offset(eyeX - 9f, eyeY + deathP * 5f), Offset(eyeX + 9f, eyeY + deathP * 5f),
                strokeWidth = 5f, cap = StrokeCap.Round
            )

            // The tongue. Anchored in the mouth, thrown out along the ground toward the victim,
            // fattest at the root and tipped with the sticky pad that does the catching.
            if (fighter.tongueExtend > 0.01f) {
                val rootX = cx + dir * 62f
                val rootY = cy + 106f
                val tipX = rootX + dir * (FROG_TONGUE_DRAW_PX * fighter.tongueExtend)
                // A little slack in the middle so it reads as flesh, not a stick
                val sagY = rootY + 16f * fighter.tongueExtend
                val tongue = Path().apply {
                    moveTo(rootX, rootY - 7f)
                    quadraticTo((rootX + tipX) / 2f, sagY - 9f, tipX, rootY + 2f)
                    lineTo(tipX, rootY + 10f)
                    quadraticTo((rootX + tipX) / 2f, sagY + 9f, rootX, rootY + 9f)
                    close()
                }
                drawStitchedFill(scope, tongue, tonguePink)
                scope.drawStitchedOutline(tongue, ThreadColor)
                // Sticky pad on the end
                scope.drawCircle(tonguePink, radius = 11f, center = Offset(tipX, rootY + 5f))
                scope.drawCircle(ThreadColor, radius = 11f, center = Offset(tipX, rootY + 5f), style = StitchedStroke)
            }
        }
    }

    private fun drawRebelSnail(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val dir = if (fighter.facingRight) 1f else -1f
        val bodyColor = Color(0xFF9C8A5A)   // marginalia ochre
        val shellColor = Color(0xFF8A5E38)
        val shellDark = Color(0xFF5C4033)
        val dead = fighter.isDead || fighter.isDying
        val deathP = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else if (fighter.isDead) 1f else 0f

        // Attack lunge: rear back (negative) through windup, snap forward at the strike
        val swing = fighter.swingProgress
        val lunge = if (fighter.isAttacking) {
            if (swing < 0.5f) -14f * (swing / 0.5f) else 34f * ((swing - 0.5f) / 0.5f)
        } else 0f
        // Idle glide ripple down the foot
        val ripple = sin(fighter.animFrame * 2f) * 3f

        scope.withTransform({
            if (deathP > 0f) {
                rotate(-12f * dir * deathP, pivot = Offset(cx, cy + 140f))
                scale(1f, 1f - 0.35f * deathP, pivot = Offset(cx, cy + 140f))
            }
        }) {
            // Slime trail behind
            scope.drawLine(
                Color(0xFFB6D0CB).copy(alpha = 0.5f),
                Offset(cx - dir * 150f, cy + 142f), Offset(cx - dir * 40f, cy + 142f),
                strokeWidth = 5f, cap = StrokeCap.Round
            )
            // Foot / slug body: a long muscular arc, head end forward
            val headX = cx + dir * (55f + lunge)
            val foot = Path().apply {
                moveTo(cx - dir * 85f, cy + 140f)
                quadraticTo(cx - dir * 60f, cy + 100f + ripple, cx, cy + 105f)
                quadraticTo(cx + dir * 40f, cy + 108f, headX, cy + 88f - lunge * 0.3f)
                quadraticTo(headX + dir * 18f, cy + 100f, headX + dir * 10f, cy + 140f)
                close()
            }
            drawStitchedFill(scope, foot, bodyColor)
            scope.drawStitchedOutline(foot, ThreadColor)

            // Spiral shell riding the back, rolled slightly off-true in death
            val shellCx = cx - dir * 30f
            val shellCy = cy + 70f + deathP * 22f
            scope.withTransform({ rotate(deathP * 40f * dir, pivot = Offset(shellCx, shellCy)) }) {
                scope.drawCircle(shellColor, radius = 52f, center = Offset(shellCx, shellCy))
                scope.drawCircle(ThreadColor, radius = 52f, center = Offset(shellCx, shellCy), style = StitchedStroke)
                // Couched spiral, three turns inward
                var r = 44f
                var a = 0f
                val spiral = Path().apply {
                    moveTo(shellCx + r, shellCy)
                    while (r > 6f) {
                        a += 0.45f
                        r -= 2.6f
                        lineTo(shellCx + cos(a) * r, shellCy + sin(a) * r)
                    }
                }
                scope.drawPath(spiral, shellDark, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }

            // Eyestalks: two stalks off the head, whipping forward with the lunge
            val stalkBase = Offset(headX, cy + 92f - lunge * 0.3f)
            val whip = lunge * 0.6f
            listOf(-8f, 6f).forEachIndexed { i, dxBase ->
                val tip = Offset(
                    stalkBase.x + dir * (14f + i * 6f + whip),
                    stalkBase.y - 34f + i * 6f + sin(fighter.animFrame * 3f + i) * 3f
                )
                scope.drawLine(bodyColor, Offset(stalkBase.x + dir * dxBase, stalkBase.y), tip, strokeWidth = 5f, cap = StrokeCap.Round)
                scope.drawLine(ThreadColor, Offset(stalkBase.x + dir * dxBase, stalkBase.y), tip, strokeWidth = 1.5f)
                scope.drawCircle(Color.White, radius = 5f, center = tip)
                scope.drawCircle(ThreadColor, radius = 5f, center = tip, style = Stroke(width = 1.5f))
                scope.drawCircle(ThreadColor, radius = 2f, center = Offset(tip.x + dir * 1.5f, tip.y))
            }

            // The rebel's mouth — a surprisingly toothy grin when striking
            if (fighter.isAttacking && swing > 0.5f) {
                scope.drawLine(ThreadColor, Offset(headX + dir * 6f, cy + 100f), Offset(headX + dir * 16f, cy + 96f), strokeWidth = 2.5f)
                repeat(3) { t ->
                    scope.drawLine(
                        Color.White,
                        Offset(headX + dir * (7f + t * 3f), cy + 99f), Offset(headX + dir * (8f + t * 3f), cy + 103f),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }

    /**
     * A cape, if he is wearing one. Drawn behind the body so he stands in front of it, and trailing
     * away from the direction he is facing so it reads as cloth rather than a board strapped on.
     *
     * The sway is off animFrame plus his own movement, so a man standing still has a cloak that
     * merely breathes and a man running has one that streams.
     */
    private fun drawCape(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val cape = fighter.extraArmors.firstOrNull { it.id.startsWith("cape_") } ?: return
        val dir = if (fighter.facingRight) 1f else -1f
        // Trails BEHIND him: opposite his facing, and further out the faster he is going.
        val drift = (14f + kotlin.math.abs(fighter.velocityX) * 0.06f).coerceAtMost(38f)
        val flap = sin(fighter.animFrame * 1.4f) * 4f
        val tail = -dir * (drift + flap)

        // Shifted BACK off the spine and flared wider than the tunic. Drawn centred and tunic-width
        // it simply read as a recoloured tunic — the cloak has to show past his back and past his
        // hem or there is no cloak, only a differently coloured man.
        val back = -dir * 14f
        val cloth = Path().apply {
            moveTo(cx + back - 20f, cy + 4f)
            lineTo(cx + back + 20f, cy + 4f)
            quadraticTo(cx + back + 34f + tail * 0.4f, cy + 70f, cx + back + 42f + tail, cy + 130f)
            lineTo(cx + back - 42f + tail, cy + 130f)
            quadraticTo(cx + back - 34f + tail * 0.4f, cy + 70f, cx + back - 20f, cy + 4f)
            close()
        }
        drawStitchedFill(scope, cloth, cape.color)
        scope.drawStitchedOutline(cloth, ThreadColor)

        when (cape.id) {
            // Rows of feathers, drawn as short overlapping strokes down the cloak
            "cape_feather" -> for (row in 0..3) {
                val y = cy + 26f + row * 24f
                for (col in -2..2) {
                    val x = cx + col * 13f + tail * (row / 4f)
                    scope.drawLine(
                        ThreadColor.copy(alpha = 0.5f),
                        Offset(x, y), Offset(x + 5f, y + 9f),
                        strokeWidth = 2f, cap = StrokeCap.Round
                    )
                }
            }
            // Ermine: the black tail-tips, in the heraldic pattern
            "cape_ermine" -> for (row in 0..2) {
                for (col in -1..1) {
                    val x = cx + col * 18f + (row % 2) * 9f + tail * (row / 3f)
                    val y = cy + 34f + row * 28f
                    scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(x, y))
                    scope.drawLine(ThreadColor, Offset(x, y + 3f), Offset(x, y + 8f), strokeWidth = 2f)
                }
            }
            // Tatters: the hem is cut away into streaming rags
            "cape_tatters" -> for (i in 0..5) {
                val x = cx - 30f + i * 12f + tail
                scope.drawLine(
                    cape.color, Offset(x, cy + 100f), Offset(x + tail * 0.35f, cy + 138f),
                    strokeWidth = 6f, cap = StrokeCap.Round
                )
            }
        }
    }

    /**
     * The banneret's standard: a tall pole behind him carrying a painted cloth with the player's
     * own face on it, drawn with the same head routine every other face uses so it is recognisably
     * HIS face rather than a generic device.
     *
     * Behind the body, so the man stands in front of his own banner.
     */
    private fun drawStandard(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        if (!fighter.isKind("standard_bearer")) return
        val dir = if (fighter.facingRight) 1f else -1f
        val poleX = cx - dir * 26f
        val topY = cy - 118f

        // Pole, with a small cross finial
        scope.drawLine(
            Color(0xFF6B4B2D), Offset(poleX, cy + 120f), Offset(poleX, topY),
            strokeWidth = 5f, cap = StrokeCap.Round
        )
        scope.drawLine(
            Color(0xFFD6A420), Offset(poleX - 7f, topY + 8f), Offset(poleX + 7f, topY + 8f),
            strokeWidth = 3f, cap = StrokeCap.Round
        )

        // The cloth, hanging off the pole away from the man, with a slow ripple in it
        val wave = sin(fighter.animFrame * 1.1f) * 5f
        val far = poleX + dir * 62f
        val cloth = Path().apply {
            moveTo(poleX, topY + 14f)
            lineTo(far, topY + 20f + wave)
            lineTo(far, topY + 84f + wave)
            lineTo(poleX, topY + 78f)
            close()
        }
        drawStitchedFill(scope, cloth, Color(0xFFB03131))
        scope.drawStitchedOutline(cloth, ThreadColor)
        // Gold border along the flying edge
        scope.drawLine(
            Color(0xFFD6A420), Offset(far, topY + 20f + wave), Offset(far, topY + 84f + wave),
            strokeWidth = 3f
        )

        // The player's face, painted on it. Drawn through the shared head routine at banner scale
        // so it inherits his actual features rather than being a second, drifting design.
        val faceX = poleX + dir * 31f
        val faceY = topY + 52f + wave
        scope.withTransform({
            scale(0.72f, 0.72f, pivot = Offset(faceX, faceY))
        }) {
            scope.drawCircle(Color(0xFFE8C9A0), radius = 22f, center = Offset(faceX, faceY))
            scope.drawCircle(ThreadColor, radius = 22f, center = Offset(faceX, faceY), style = StitchedStroke)
            // Eyes, brows, nose and moustache — the Bayeux face reduced to its four marks
            listOf(-8f, 8f).forEach { dx ->
                scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(faceX + dx, faceY - 5f))
                scope.drawLine(
                    ThreadColor, Offset(faceX + dx - 6f, faceY - 12f), Offset(faceX + dx + 5f, faceY - 11f),
                    strokeWidth = 2.5f, cap = StrokeCap.Round
                )
            }
            scope.drawLine(
                ThreadColor, Offset(faceX, faceY - 3f), Offset(faceX, faceY + 5f),
                strokeWidth = 2.5f, cap = StrokeCap.Round
            )
            scope.drawLine(
                ThreadColor, Offset(faceX - 9f, faceY + 9f), Offset(faceX + 9f, faceY + 9f),
                strokeWidth = 3f, cap = StrokeCap.Round
            )
        }
    }

    private fun drawBossSignature(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        fighter: FighterState
    ) {
        val boss = fighter.bossType ?: return

        // Per-boss regalia so the 1066 trio each read as their own figure, not one re-tinted model.
        // Drawn before the body (behind it): a sweeping cloak in the boss's colour, fur-trimmed at
        // the collar, plus a signature accent.
        val cloakColor = when (boss) {
            BossType.HAROLD_GODWINSON -> Color(0xFF2E6B4A)   // Wessex green
            BossType.HARALD_HARDRADA -> Color(0xFF3A2B5B)    // Norse royal purple
            BossType.WILLIAM_THE_BASTARD -> Color(0xFF7A1B18) // Norman blood-red
            BossType.GOG -> Color(0xFF4A5D23)                 // moss and bog
            BossType.MAGOG -> Color(0xFF384048)               // storm-slate
            BossType.POLYPHEMUS -> Color(0xFF6B4A2A)          // untanned hide, still hairy
        }
        val cloak = Path().apply {
            moveTo(cx - 26f, cy + 8f)
            lineTo(cx - 60f, cy + 120f)
            lineTo(cx + 60f, cy + 120f)
            lineTo(cx + 26f, cy + 8f)
            quadraticTo(cx, cy - 2f, cx - 26f, cy + 8f)
            close()
        }
        drawStitchedFill(scope, cloak, cloakColor)
        scope.drawStitchedOutline(cloak, ThreadColor)
        // Fur collar
        scope.drawLine(Color(0xFFE7DCC4), Offset(cx - 30f, cy + 6f), Offset(cx + 30f, cy + 6f), strokeWidth = 9f, cap = StrokeCap.Round)
        when (boss) {
            BossType.HAROLD_GODWINSON ->
                // Housecarl's great Dane-axe slung across the back
                scope.drawLine(Color(0xFF9EA3A8), Offset(cx + 34f, cy - 40f), Offset(cx - 34f, cy + 118f), strokeWidth = 6f, cap = StrokeCap.Round)
            BossType.HARALD_HARDRADA ->
                // Twin ravens of the North on the shoulders
                listOf(-40f, 40f).forEach { dx ->
                    scope.drawCircle(Color(0xFF23201C), 7f, Offset(cx + dx, cy - 2f))
                }
            BossType.WILLIAM_THE_BASTARD ->
                // Golden hem befitting the would-be king
                scope.drawLine(Color(0xFFD6A420), Offset(cx - 58f, cy + 116f), Offset(cx + 58f, cy + 116f), strokeWidth = 5f)
            BossType.GOG, BossType.MAGOG, BossType.POLYPHEMUS ->
                // A necklace of bleached skulls across the collar
                listOf(-34f, -12f, 12f, 34f).forEach { dx ->
                    scope.drawCircle(Color(0xFFE7DCC4), 6f, Offset(cx + dx, cy + 16f))
                    scope.drawCircle(ThreadColor, 6f, Offset(cx + dx, cy + 16f), style = Stroke(width = 1.5f))
                    scope.drawCircle(ThreadColor, 1.5f, Offset(cx + dx - 2f, cy + 15f))
                    scope.drawCircle(ThreadColor, 1.5f, Offset(cx + dx + 2f, cy + 15f))
                }
        }

        val poleX = cx - 42f
        scope.drawLine(
            Color(0xFF6B4B2D),
            Offset(poleX, cy + 118f),
            Offset(poleX, cy - 82f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        val bannerColor = when (boss) {
            BossType.HAROLD_GODWINSON -> Color(0xFFB08221)
            BossType.HARALD_HARDRADA -> Color(0xFF265063)
            BossType.WILLIAM_THE_BASTARD -> Color(0xFF9E3624)
            BossType.GOG -> Color(0xFF6E5536)   // raw hide banners
            BossType.MAGOG -> Color(0xFF384033)
            BossType.POLYPHEMUS -> Color(0xFF7A5A38)
        }
        val banner = Path().apply {
            moveTo(poleX, cy - 80f)
            lineTo(poleX + 62f, cy - 68f)
            lineTo(poleX + 48f, cy - 34f)
            lineTo(poleX, cy - 42f)
            close()
        }
        drawStitchedFill(scope, banner, bannerColor)
        scope.drawPath(banner, ThreadColor, style = Stroke(2f))
        scope.drawLine(
            Color(0xFFD6C48A),
            Offset(poleX + 10f, cy - 58f),
            Offset(poleX + 45f, cy - 50f),
            strokeWidth = 4f
        )
    }

    private fun drawLegs(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        if (fighter.isChariot) return
        val anim = fighter.animFrame
        // Simple leg swing: left/right legs swing in opposition
        val angleL = if (fighter.isDead || fighter.isDying) 0f else sin(anim) * 0.45f
        val angleR = if (fighter.isDead || fighter.isDying) 0f else -sin(anim) * 0.45f

        // Leg colors typical of Bayeux: terracotta, mustard, green
        val legColorL = if (fighter.isPlayer) Color(0xFF9E3624) else Color(0xFF4C613D)
        val legColorR = if (fighter.isPlayer) Color(0xFFB08221) else Color(0xFF265063)

        // Left Leg (Back leg) - don't draw if mounted (hidden behind horse) unless on stilts
        if (!fighter.isMounted || fighter.isStilts) {
            scope.withTransform({
                rotate(radToDeg(angleL), pivot = Offset(cx - 10f, cy + 90f))
            }) {
                // The strap is a 17px round-capped line, so its end bulges ~8.5px past this point
                // in every direction. Ending it at the boot's SOLE put that bulge outside the
                // boot entirely — the leg overshot the foot and poked out below and behind the
                // heel. It now stops at the ankle, where the boot covers the join.
                drawStitchedStrap(this, Offset(cx - 10f, cy + 90f), Offset(cx - 17f, cy + 143f), legColorL)
                val bootPathL = Path().apply {
                    moveTo(cx - 23.5f, cy + 145.5f) // back heel
                    lineTo(cx - 24f, cy + 155f) // bottom heel
                    lineTo(cx + 3.5f, cy + 155.5f)  // bottom toe
                    lineTo(cx + 2.5f, cy + 149f)  // top toe
                    lineTo(cx - 11f, cy + 144.5f) // front ankle
                    close()
                }
                val hasBoots = fighter.extraArmors.any { it.id == "armor_boots" }
                val bootColorL = if (hasBoots) Color(0xFF5D666B) else Color(0xFF382F22)
                drawStitchedFill(this, bootPathL, bootColorL)
                if (hasBoots) {
                    drawLine(Color(0xFF9EA3A8), Offset(cx - 13f, cy + 144.5f), Offset(cx - 23.5f, cy + 144.5f), strokeWidth = 3f)
                }
                // Greaves ride the shin, inside the leg's own rotate, so they swing with it.
                if (fighter.extraArmors.any { it.id == "armor_greaves" }) {
                    val greaveL = Path().apply {
                        moveTo(cx - 22f, cy + 108f)
                        lineTo(cx - 12f, cy + 108f)
                        lineTo(cx - 12.5f, cy + 144.5f)
                        lineTo(cx - 23.5f, cy + 144.5f)
                        close()
                    }
                    drawStitchedFill(this, greaveL, Color(0xFF5D666B))
                }
            }
        }

        // Right Leg (Front leg)
        scope.withTransform({
            rotate(radToDeg(angleR), pivot = Offset(cx + 10f, cy + 90f))
        }) {
            // Same as the back leg: stop at the ankle, inside the boot, not on the sole. The cap
            // used to bulge left of the heel at x+6 when the boot only starts at x+12.
            drawStitchedStrap(this, Offset(cx + 10f, cy + 90f), Offset(cx + 20f, cy + 143f), legColorR)
            val bootPathR = Path().apply {
                moveTo(cx + 14f, cy + 145f) // back heel
                lineTo(cx + 12f, cy + 155.5f) // bottom heel
                lineTo(cx + 42f, cy + 155.5f)  // bottom toe
                lineTo(cx + 40.5f, cy + 149.5f)  // top toe
                lineTo(cx + 26f, cy + 145f) // front ankle
                close()
            }
            val hasBoots = fighter.extraArmors.any { it.id == "armor_boots" }
            val bootColorR = if (hasBoots) Color(0xFF5D666B) else Color(0xFF382F22)
            drawStitchedFill(this, bootPathR, bootColorR)
            if (hasBoots) {
                drawLine(Color(0xFF9EA3A8), Offset(cx + 14f, cy + 145f), Offset(cx + 26f, cy + 145f), strokeWidth = 3f)
            }
            if (fighter.extraArmors.any { it.id == "armor_greaves" }) {
                val greaveR = Path().apply {
                    moveTo(cx + 12f, cy + 108f)
                    lineTo(cx + 22f, cy + 108f)
                    lineTo(cx + 24f, cy + 145f)
                    lineTo(cx + 15f, cy + 145f)
                    close()
                }
                drawStitchedFill(this, greaveR, Color(0xFF5D666B))
            }
        }
    }

    /**
     * Flesh colour for every exposed part — face, neck, hands. One place so the sick look the same
     * whether they are the player, an enemy or a follower.
     */
    private fun skinTone(fighter: FighterState): Color = when {
        // Grave-pallor first: a risen king is sallow whatever else ails him, and being poisoned
        // or alight cannot make dead flesh look healthier. Face, neck and hands all come through
        // here, so the whole body reads as one corpse rather than a pale head on a living man.
        fighter.bossTier == BossTier.SUPER_UNDEAD -> Color(0xFF8E9A8C)
        fighter.bossTier == BossTier.UNDEAD -> Color(0xFFA8AE9A)
        // Plague shows before it hurts: the peasant is contagious from the moment he spawns
        fighter.diseaseDuration > 0f || fighter.isContagious -> PlagueFlesh
        fighter.igniteDuration > 0f -> Color(0xFFE6A15A)
        fighter.poisonDuration > 0f -> Color(0xFF8CAF8A)
        else -> Color(0xFFE8C5A4)
    }

    private fun wearsMonkRobe(fighter: FighterState): Boolean =
        fighter.isWarPriest || fighter.archetype == EnemyArchetype.MONK_MILITIA

    /**
     * Arm colour. Bare-chested fighters have bare *arms* — the six sleeve sites used to hardcode a
     * blue/red sleeve regardless of armour, which is what put a shirt on a naked man.
     */
    private fun sleeveTone(fighter: FighterState, back: Boolean): Color = when {
        wearsMonkRobe(fighter) -> if (back) Color(0xFF4A3B2F) else MonkBrown
        fighter.armor.id == "armor_bare" -> skinTone(fighter)
        fighter.isPlayer -> if (back) Color(0xFF1E3F4F) else Color(0xFF265063)
        else -> if (back) Color(0xFF8A2E1E) else Color(0xFF9E3624)
    }

    /** True when nothing covers the chest — so it is drawn as flesh, with hair on it. */
    private fun isBarechested(fighter: FighterState): Boolean =
        fighter.armor.id == "armor_bare" &&
            !wearsMonkRobe(fighter) &&
            !fighter.isKind("hag") && !fighter.isKind("fanatic_boris") && !fighter.isKind("plague_peasant")

    /** The Moleman's digging claws — three pale sickles off the knuckles. Nobody else grows them. */
    private fun drawClaws(scope: DrawScope, hx: Float, hy: Float, fighter: FighterState) {
        if (!fighter.isKind("moleman")) return
        for (i in -1..1) {
            val ty = hy + i * 4.5f
            val claw = Path().apply {
                moveTo(hx + 2f, ty - 1.8f)
                quadraticTo(hx + 9f, ty - 3.5f, hx + 14f, ty + 1.5f)
                quadraticTo(hx + 8f, ty + 0.5f, hx + 2f, ty + 1.8f)
                close()
            }
            scope.drawPath(claw, Color(0xFFE3D9C0))
            scope.drawPath(claw, ThreadColor, style = Stroke(width = 1.2f))
        }
    }

    private fun drawTorso(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val monkRobe = wearsMonkRobe(fighter)
        val longRobe = fighter.archetype == EnemyArchetype.MONK_MILITIA
        val tunicPath = Path().apply {
            moveTo(cx - 25f, cy + 15f)
            lineTo(cx + 25f, cy + 15f)
            lineTo(cx + if (longRobe) 38f else 30f, cy + if (longRobe) 145f else 95f)
            lineTo(cx - if (longRobe) 38f else 30f, cy + if (longRobe) 145f else 95f)
            close()
        }

        // Color theme derived directly from the armor's designated color
        val fillCol = when {
            fighter.isKind("hag") -> Color(0xFF3E3A2E) // dodgy ragged cloak
            fighter.isKind("fanatic_boris") -> Color(0xFF7A1F1F) // blood-red madman's rags, not a clean white shirt
            fighter.isKind("plague_peasant") -> PlagueRags // filthy undyed homespun
            fighter.isKind("greaser") -> Color(0xFF7A6A3A) // fat-stained apron, greasy through
            monkRobe -> MonkBrown
            isBarechested(fighter) -> skinTone(fighter) // bare means bare: skin, not a white shirt
            else -> fighter.armor.color
        }

        // Fill with textured stitching
        drawStitchedFill(scope, tunicPath, fillCol)

        if (isBarechested(fighter)) {
            // Sparse chest thatch. Seeded off the fighter's id so it does not crawl about between frames.
            // The Moleman is "something hairy that swims through soil": a full pelt from collar to
            // hem, not a thatch, which is most of what tells him apart from a bare-chested levy.
            val pelt = fighter.isKind("moleman")
            val hairRng = Random(fighter.id.raw.hashCode())
            repeat(if (pelt) 44 else 14) {
                val hx = cx - (if (pelt) 26f else 18f) + hairRng.nextFloat() * (if (pelt) 52f else 36f)
                val hy = cy + (if (pelt) 18f else 26f) + hairRng.nextFloat() * (if (pelt) 68f else 44f)
                val curl = Path().apply {
                    moveTo(hx, hy)
                    quadraticTo(hx + 2f, hy + 3f, hx - 1f + hairRng.nextFloat() * 3f, hy + (if (pelt) 8f else 6f))
                }
                scope.drawPath(curl, fighter.hairColor.copy(alpha = if (pelt) 0.9f else 0.75f), style = Stroke(width = if (pelt) 2.1f else 1.6f, cap = StrokeCap.Round))
            }

            if (fighter.warPaint == 1) {
                // A woad spiral across the bare chest — the paint goes on the skin, not the shirt
                val spiral = Path().apply {
                    moveTo(cx, cy + 45f)
                    var r = 3f
                    var a = 0f
                    while (r < 20f) {
                        a += 0.5f
                        r += 1.1f
                        lineTo(cx + cos(a) * r, cy + 45f + sin(a) * r * 0.8f)
                    }
                }
                scope.drawPath(spiral, Woad, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            }
        }

        if (fighter.isKind("hag")) {
            // A proper hag: humped back and a long trailing gown over the ragged cloak
            val gown = Path().apply {
                moveTo(cx - 30f, cy + 60f)
                lineTo(cx + 30f, cy + 60f)
                lineTo(cx + 42f, cy + 150f)
                quadraticTo(cx, cy + 138f, cx - 42f, cy + 150f)
                close()
            }
            drawStitchedFill(scope, gown, Color(0xFF2F2C25))
            scope.drawStitchedOutline(gown, ThreadColor)
            // Hunch: a great rounded hump rising behind the shoulders
            val hump = Path().apply {
                moveTo(cx - 26f, cy + 26f)
                quadraticTo(cx - 34f, cy - 22f, cx + 4f, cy - 10f)
                quadraticTo(cx + 2f, cy + 14f, cx - 26f, cy + 26f)
                close()
            }
            drawStitchedFill(scope, hump, Color(0xFF3E3A2E))
            scope.drawStitchedOutline(hump, ThreadColor)
        }

        if (fighter.isWarPriest) {
            // Unarmed and robed: a tall staff and a rope girdle mark him out as the man to kill first
            val staffX = cx + 34f
            scope.drawLine(
                Color(0xFF6B5433),
                Offset(staffX, cy - 55f), Offset(staffX, cy + 95f),
                strokeWidth = 5f, cap = StrokeCap.Round
            )
            // A carved wooden knob on the staff — no religious imagery on the enemy side.
            scope.drawCircle(Color(0xFF8A6A3E), radius = 8f, center = Offset(staffX, cy - 58f))
            scope.drawCircle(ThreadColor, radius = 8f, center = Offset(staffX, cy - 58f), style = Stroke(width = 1.5f))
            // Rope girdle
            scope.drawLine(
                Color(0xFFD6C48A),
                Offset(cx - 26f, cy + 62f), Offset(cx + 26f, cy + 62f),
                strokeWidth = 3f
            )
        }

        // Champion Belt brawler upgrade — big gold wrestling belt at the waist
        if (fighter.brawlerUpgrades.contains("champion_belt")) {
            scope.drawRect(Color(0xFFD6A420), topLeft = Offset(cx - 27f, cy + 68f), size = androidx.compose.ui.geometry.Size(54f, 10f))
            scope.drawRect(ThreadColor, topLeft = Offset(cx - 27f, cy + 68f), size = androidx.compose.ui.geometry.Size(54f, 10f), style = Stroke(width = 1.5f))
            scope.drawCircle(Color(0xFFF0D060), radius = 8f, center = Offset(cx, cy + 73f))
            scope.drawCircle(ThreadColor, radius = 8f, center = Offset(cx, cy + 73f), style = Stroke(width = 1.5f))
        }

        if (fighter.isKind("plague_peasant")) {
            // No embroidery for a dying man: the hem is torn into a row of ragged teeth
            val ragPath = Path().apply {
                moveTo(cx - 30f, cy + 82f)
                var x = -30f
                var deep = true
                while (x < 30f) {
                    val nextX = (x + 7.5f).coerceAtMost(30f)
                    lineTo(cx + (x + nextX) / 2f, cy + if (deep) 100f else 92f)
                    lineTo(cx + nextX, cy + 82f)
                    deep = !deep
                    x = nextX
                }
                close()
            }
            drawStitchedFill(scope, ragPath, fillCol)
            scope.drawStitchedOutline(ragPath, ThreadColor)
        } else {
            // Add a traditional embroidered pattern trim at the bottom hem
            val hemPath = Path().apply {
                moveTo(cx - 29f, cy + 85f)
                lineTo(cx + 29f, cy + 85f)
                lineTo(cx + 30f, cy + 95f)
                lineTo(cx - 30f, cy + 95f)
                close()
            }
            drawStitchedFill(scope, hemPath, Color(0xFFB08221)) // Gold/mustard border
            scope.drawStitchedOutline(hemPath, ThreadColor)
        }

        // Torso outline
        scope.drawStitchedOutline(tunicPath, ThreadColor)

        // The Blemmya's face, which is where his chest would be. Drawn after the tunic outline so
        // it sits ON him rather than under the cloth, and sized to fill the torso — the whole
        // silhouette gag is that the face is the body.
        if (fighter.archetype == EnemyArchetype.BLEMMYA) {
            val fx = cx
            val fy = cy + 42f
            val skin = skinTone(fighter)
            // A broad bare patch of chest for the face to live on
            scope.drawCircle(skin, radius = 26f, center = Offset(fx, fy))
            scope.drawCircle(ThreadColor, radius = 26f, center = Offset(fx, fy), style = StitchedStroke)
            // Two wide-set eyes up on the collarbones
            listOf(-11f, 11f).forEach { dx ->
                scope.drawCircle(Color.White, radius = 6.5f, center = Offset(fx + dx, fy - 9f))
                scope.drawCircle(ThreadColor, radius = 6.5f, center = Offset(fx + dx, fy - 9f), style = Stroke(width = 1.5f))
                scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(fx + dx, fy - 9f))
            }
            // Heavy brows, because he is not pleased about any of this
            scope.drawLine(ThreadColor, Offset(fx - 18f, fy - 18f), Offset(fx - 5f, fy - 15f), strokeWidth = 3.5f, cap = StrokeCap.Round)
            scope.drawLine(ThreadColor, Offset(fx + 18f, fy - 18f), Offset(fx + 5f, fy - 15f), strokeWidth = 3.5f, cap = StrokeCap.Round)
            // A wide mouth across the belly
            val mouth = Path().apply {
                moveTo(fx - 15f, fy + 6f)
                quadraticTo(fx, fy + 20f, fx + 15f, fy + 6f)
                quadraticTo(fx, fy + 11f, fx - 15f, fy + 6f)
                close()
            }
            drawStitchedFill(scope, mouth, Color(0xFF3A2622))
            scope.drawStitchedOutline(mouth, ThreadColor)
        }

        // Textures
        when (fighter.armor.id) {
            "armor_chainmail" -> drawChainmailTexture(scope, cx - 22f, cy + 20f, 44f, 65f)
            "armor_padded", "armor_felt" -> drawGambesonTexture(scope, cx, cy + 15f, 44f, 70f)
            "armor_scale" -> drawScaleTexture(scope, cx, cy + 15f, 44f, 70f)
            "armor_lamellar" -> drawLamellarTexture(scope, cx, cy + 15f, 44f, 70f)
            "armor_fur" -> drawFurTexture(scope, cx, cy + 15f, 44f, 70f)
        }

        // Extra layered armors on top
        fighter.extraArmors.forEachIndexed { i, extraArmor ->
            // Drawn by hand elsewhere: gauntlets on the arms, boots and greaves on the legs, coif on the head.
            if (extraArmor.id in listOf("armor_gauntlets", "armor_boots", "armor_coif", "armor_greaves")) return@forEachIndexed
            // And the capes, which hang behind the body in drawCape. Falling through to the generic
            // layer-tunic below painted a second full garment over the torso in the cape's colour,
            // which is why picking up a cloak appeared to repaint the body armour.
            if (extraArmor.id in GameData.CAPE_IDS) return@forEachIndexed
            if (extraArmor.id == "armor_spaulders") {
                // Caps over each shoulder. These bypass the generic tunic path below, which would
                // otherwise drape a whole extra steel garment over the body.
                val capL = Path().apply {
                    moveTo(cx - 34f, cy + 22f)
                    quadraticTo(cx - 24f, cy + 6f, cx - 10f, cy + 22f)
                    close()
                }
                val capR = Path().apply {
                    moveTo(cx + 10f, cy + 22f)
                    quadraticTo(cx + 24f, cy + 6f, cx + 34f, cy + 22f)
                    close()
                }
                drawStitchedFill(scope, capL, extraArmor.color)
                scope.drawStitchedOutline(capL, ThreadColor)
                drawStitchedFill(scope, capR, extraArmor.color)
                scope.drawStitchedOutline(capR, ThreadColor)
                return@forEachIndexed
            }
            if (extraArmor.id == "armor_surcoat") {
                // Narrower and shorter than the generic layer tunic on purpose: a surcoat worn over
                // mail should leave the mail showing at the shoulders and the hem, not replace it.
                val surcoat = Path().apply {
                    moveTo(cx - 26f, cy + 24f)
                    lineTo(cx + 26f, cy + 24f)
                    lineTo(cx + 20f, cy + 86f)
                    lineTo(cx - 20f, cy + 86f)
                    close()
                }
                drawStitchedFill(scope, surcoat, extraArmor.color)
                scope.drawStitchedOutline(surcoat, ThreadColor)
                return@forEachIndexed
            }
            val dx = 32f + i * 2f
            val dy = 98f + i * 2f
            val extraTunicPath = Path().apply {
                moveTo(cx - 25f, cy + 15f)
                lineTo(cx + 25f, cy + 15f)
                lineTo(cx + dx, cy + dy)
                lineTo(cx - dx, cy + dy)
                close()
            }
            drawStitchedFill(scope, extraTunicPath, extraArmor.color)
            scope.drawStitchedOutline(extraTunicPath, ThreadColor)
            
            when (extraArmor.id) {
                "armor_chainmail" -> drawChainmailTexture(scope, cx - 25f, cy + 20f, 50f, dy - 20f)
                "armor_padded", "armor_felt" -> drawGambesonTexture(scope, cx, cy + 15f, 50f, dy - 15f)
                "armor_scale" -> drawScaleTexture(scope, cx, cy + 15f, 50f, dy - 15f)
                "armor_lamellar" -> drawLamellarTexture(scope, cx, cy + 15f, 50f, dy - 15f)
                "armor_fur" -> drawFurTexture(scope, cx, cy + 15f, 50f, dy - 15f)
            }
        }

        // Battle Scars / Stuck Projectiles
        val numProjs = fighter.stuckProjectiles.size.coerceAtMost(6)
        for (i in 0 until numProjs) {
            val proj = fighter.stuckProjectiles[i]
            
            // Determine entry point (shield or body)
            val ax: Float
            val ay: Float
            val isHeadshot = (i % 3) == 0 && !proj.inShield
            if (proj.inShield) {
                // Approximate shield location (in front of character)
                ax = cx + 25f + (i * 5f)
                ay = cy + 40f + (i * 12f % 30f)
            } else if (isHeadshot) {
                val offset = 5f + (i * 2f)
                ax = cx + offset
                ay = cy - 20f + (i * 5f % 15f) // near head
            } else {
                val offset = 15f + (i * 5f)
                ax = cx + offset
                ay = cy + 40f + (i * 18f % 40f)
            }

            // Direction calculation (point opposite of velocity)
            val mag = kotlin.math.hypot(proj.velocityX.toDouble(), proj.velocityY.toDouble()).toFloat().coerceAtLeast(0.001f)
            val localVelocityX = if (fighter.facingRight) proj.velocityX else -proj.velocityX
            val baseDirX = -(localVelocityX / mag)
            val baseDirY = -(proj.velocityY / mag)

            // Add some variance to direction
            val varianceAngle = -0.1f + ((i % 3) * 0.1f) // rads
            val cosV = kotlin.math.cos(varianceAngle.toDouble()).toFloat()
            val sinV = kotlin.math.sin(varianceAngle.toDouble()).toFloat()
            
            val dirX = baseDirX * cosV - baseDirY * sinV
            val dirY = baseDirX * sinV + baseDirY * cosV

            // This draws inside the fighter's own scale transform, so divide it back out —
            // otherwise big enemies wear giant arrows while the flying ones stayed small.
            // Cap stuck-shaft size so ballista spears don't blot out the fighter
            val effSize = ((if (proj.isBallista) 0.4f else if (proj.type == ProjectileType.BOLT) 0.6f else if (proj.type == ProjectileType.DART) 0.5f else 1f) * proj.size / fighter.size).coerceAtMost(1.2f)
            val length = when {
                proj.type == ProjectileType.DART -> 14f
                proj.type == ProjectileType.BOLT && !proj.isBallista -> 20f
                else -> 35f
            } * effSize
            val tailX = ax + (dirX * length)
            val tailY = ay + (dirY * length)

            // Stick out the back (penetration)
            val penetration = if (proj.inShield) 8f else 20f * effSize
            val headX = ax - (dirX * penetration)
            val headY = ay - (dirY * penetration)

            // Draw shaft
            val strokeW = (if (proj.type == ProjectileType.JAVELIN) 6f else if (proj.isBallista) 8f else if (proj.type == ProjectileType.DART) 2f else 3f) * effSize.coerceAtLeast(0.6f)
            scope.drawLine(Color(0xFF8A5E38), Offset(tailX, tailY), Offset(headX, headY), strokeWidth = strokeW)

            // Fletching (only for arrows/bolts)
            if (proj.type == ProjectileType.ARROW || proj.type == ProjectileType.BOLT) {
                val fletchW = 7f
                val perpX = -dirY
                val perpY = dirX
                scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX + dirX*7f + perpX * fletchW, tailY + dirY*7f + perpY * fletchW), strokeWidth = 3f)
                scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX + dirX*7f - perpX * fletchW, tailY + dirY*7f - perpY * fletchW), strokeWidth = 3f)
            }
            
            // Projectile head sticking out the other side
            if (proj.type != ProjectileType.STONE && proj.type != ProjectileType.DART) {
                val headSize = if (proj.type == ProjectileType.JAVELIN) 8f else 5f
                val perpX = -dirY
                val perpY = dirX
                val pt1 = Offset(headX + dirX * headSize + perpX * headSize, headY + dirY * headSize + perpY * headSize)
                val pt2 = Offset(headX + dirX * headSize - perpX * headSize, headY + dirY * headSize - perpY * headSize)
                val pt3 = Offset(headX - dirX * (headSize * 1.5f), headY - dirY * (headSize * 1.5f))
                val headPath = Path().apply {
                    moveTo(headX, headY) // base of head
                    lineTo(pt1.x, pt1.y)
                    lineTo(pt3.x, pt3.y) // tip
                    lineTo(pt2.x, pt2.y)
                    close()
                }
                drawStitchedFill(scope, headPath, Color(0xFFBAC5CC))
            }

            // Blood stain only if hit body
            if (!proj.inShield) {
                scope.drawCircle(Color(0xFF9E3624).copy(alpha = 0.6f), radius = 8f, center = Offset(ax, ay))
                scope.drawCircle(Color(0xFF9E3624).copy(alpha = 0.5f), radius = 6f, center = Offset(headX, headY)) // exit wound
            }
        }

        // Blood pool beneath heavily wounded fighter (drawn at feet level ~cy+155)
        val hpRatioTorso = if (fighter.maxHp > 0f) fighter.hp / fighter.maxHp else 1f
        if (!fighter.isDead && !fighter.isDying && !fighter.isInanimate &&
            (fighter.missingArm || hpRatioTorso < 0.5f)) {
            val groundY = cy + 158f
            val poolAlpha = if (fighter.missingArm) 0.75f else (0.5f - hpRatioTorso).coerceIn(0f, 0.5f) * 1.5f
            scope.drawOval(
                color = Color(0x88800000).copy(alpha = poolAlpha),
                topLeft = Offset(cx - 20f, groundY - 5f),
                size = androidx.compose.ui.geometry.Size(40f, 10f)
            )
        }
    }

    /**
     * The cynocephalus head, in profile like the human one. Face types vary from the same seeded
     * fields humans use: faceNoseShape = muzzle (wolf/hound/mastiff/jackal), faceForehead = ears
     * (pricked/floppy/torn), faceBiteShape = jaw (fangs bared / lolling tongue / underbite).
     * Fur takes the fighter's hairColor so a pack reads as individuals.
     */
    /**
     * Polyphemus, after Harryhausen rather than after Homer: a big brutish skull far larger than a
     * man's, one enormous eye under a shelf of brow, a single horn, pointed animal ears, a flat
     * broad nose and a snarl with tusks in it.
     *
     * A full replacement head like [drawDogHead], drawn inside the same transform so it still pops
     * off when he is decapitated. Everything is scaled up by [CYCLOPS_HEAD_SCALE] about the neck
     * join, because the point of the design is that the head is too big for the body.
     */
    private fun drawCyclopsHead(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val hx = cx
        val hy = cy - 25f
        val skin = skinTone(fighter)
        val skinDark = androidx.compose.ui.graphics.lerp(skin, Color.Black, 0.28f)
        val hair = fighter.hairColor

        scope.withTransform({
            scale(CYCLOPS_HEAD_SCALE, CYCLOPS_HEAD_SCALE, pivot = Offset(hx, hy + 40f))
        }) {
            // Thick neck running straight into the jaw — no chin to speak of
            val neck = Path().apply {
                moveTo(hx - 15f, hy + 42f); lineTo(hx - 13f, hy + 6f)
                lineTo(hx + 13f, hy + 6f); lineTo(hx + 15f, hy + 42f); close()
            }
            drawStitchedFill(scope, neck, skin)
            scope.drawStitchedOutline(neck, ThreadColor)

            // Ears: pointed, set low and back, like an animal's
            listOf(-1f, 1f).forEach { side ->
                val ear = Path().apply {
                    moveTo(hx + side * 26f, hy - 6f)
                    lineTo(hx + side * 42f, hy - 20f)
                    lineTo(hx + side * 30f, hy + 8f)
                    close()
                }
                drawStitchedFill(scope, ear, skinDark)
                scope.drawStitchedOutline(ear, ThreadColor)
            }

            // The skull: broad and square across the brow, narrowing to a heavy jaw
            val skull = Path().apply {
                moveTo(hx - 28f, hy + 6f)
                quadraticTo(hx - 34f, hy - 26f, hx - 16f, hy - 38f)
                quadraticTo(hx, hy - 46f, hx + 16f, hy - 38f)
                quadraticTo(hx + 34f, hy - 26f, hx + 28f, hy + 6f)
                quadraticTo(hx + 20f, hy + 22f, hx, hy + 24f)
                quadraticTo(hx - 20f, hy + 22f, hx - 28f, hy + 6f)
                close()
            }
            drawStitchedFill(scope, skull, skin)
            scope.drawStitchedOutline(skull, ThreadColor)

            // The horn. One, off-centre, the way a real growth would be.
            val horn = Path().apply {
                moveTo(hx - 6f, hy - 40f)
                lineTo(hx + 8f, hy - 40f)
                quadraticTo(hx + 8f, hy - 60f, hx - 2f, hy - 68f)
                quadraticTo(hx - 4f, hy - 52f, hx - 6f, hy - 40f)
                close()
            }
            drawStitchedFill(scope, horn, Color(0xFFD8CBA8))
            scope.drawStitchedOutline(horn, ThreadColor)

            // Shaggy hair round the back and sides of the skull
            for (i in 0..5) {
                val t = i / 5f
                val sx = hx - 30f + t * 60f
                scope.drawLine(
                    hair, Offset(sx, hy - 34f + kotlin.math.abs(t - 0.5f) * 14f),
                    Offset(sx - 6f, hy - 48f + kotlin.math.abs(t - 0.5f) * 16f),
                    strokeWidth = 5f, cap = StrokeCap.Round
                )
            }

            // The brow: a single heavy shelf right across the face
            scope.drawLine(
                skinDark, Offset(hx - 24f, hy - 18f), Offset(hx + 24f, hy - 18f),
                strokeWidth = 11f, cap = StrokeCap.Round
            )

            // THE eye. Enormous, and the whole reason for the design.
            val ex = hx
            val ey = hy - 2f
            scope.drawCircle(Color.White, radius = 19f, center = Offset(ex, ey))
            scope.drawCircle(ThreadColor, radius = 19f, center = Offset(ex, ey), style = StitchedStroke)
            scope.drawCircle(Color(0xFFB8892E), radius = 11f, center = Offset(ex, ey))
            scope.drawCircle(ThreadColor, radius = 5.5f, center = Offset(ex, ey))
            // Catchlight, so it reads as wet rather than as a painted disc
            scope.drawCircle(Color.White, radius = 3f, center = Offset(ex - 5f, ey - 6f))

            // Flat broad nose below the eye, and flared nostrils
            scope.drawLine(
                skinDark, Offset(hx, hy + 2f), Offset(hx, hy + 11f),
                strokeWidth = 4f, cap = StrokeCap.Round
            )
            listOf(-5f, 5f).forEach { dx ->
                scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(hx + dx, hy + 12f))
            }

            // The snarl, with two tusks coming up out of the lower jaw
            val mouth = Path().apply {
                moveTo(hx - 16f, hy + 17f)
                quadraticTo(hx, hy + 23f, hx + 16f, hy + 16f)
                quadraticTo(hx, hy + 14f, hx - 16f, hy + 17f)
                close()
            }
            drawStitchedFill(scope, mouth, Color(0xFF3A2622))
            listOf(-9f, 9f).forEach { dx ->
                val tusk = Path().apply {
                    moveTo(hx + dx - 3f, hy + 19f)
                    lineTo(hx + dx + 3f, hy + 19f)
                    lineTo(hx + dx, hy + 10f)
                    close()
                }
                drawStitchedFill(scope, tusk, Color(0xFFE7DCC4))
                scope.drawStitchedOutline(tusk, ThreadColor)
            }
        }
    }

    /** Reynard of the margins: a fox's head on a man's shoulders, sharp muzzle and pricked ears. */
    private fun drawFoxHead(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val hx = cx
        val hy = cy - 25f
        val fur = Color(0xFFB4622A)
        val furDark = androidx.compose.ui.graphics.lerp(fur, Color.Black, 0.3f)
        val dir = if (fighter.facingRight) 1f else -1f

        val neck = Path().apply {
            moveTo(hx - 10f, hy + 40f); lineTo(hx - 8f, hy + 4f)
            lineTo(hx + 8f, hy + 4f); lineTo(hx + 10f, hy + 40f); close()
        }
        drawStitchedFill(scope, neck, fur)
        scope.drawStitchedOutline(neck, ThreadColor)

        // Tall triangular ears, black-tipped
        listOf(-1f, 1f).forEach { side ->
            val ear = Path().apply {
                moveTo(hx + side * 6f, hy - 14f)
                lineTo(hx + side * 15f, hy - 34f)
                lineTo(hx + side * 19f, hy - 10f)
                close()
            }
            drawStitchedFill(scope, ear, fur)
            scope.drawStitchedOutline(ear, ThreadColor)
            scope.drawCircle(furDark, radius = 3f, center = Offset(hx + side * 15f, hy - 31f))
        }

        // Skull plus a long narrow muzzle running forward
        val skull = Path().apply {
            moveTo(hx - 15f, hy - 10f)
            quadraticTo(hx, hy - 20f, hx + 15f, hy - 10f)
            lineTo(hx + dir * 34f, hy + 6f)
            lineTo(hx + dir * 32f, hy + 12f)
            quadraticTo(hx, hy + 18f, hx - 15f, hy + 6f)
            close()
        }
        drawStitchedFill(scope, skull, fur)
        scope.drawStitchedOutline(skull, ThreadColor)

        // White cheek flash, the fox's own marking
        scope.drawLine(
            Color(0xFFEFE6D4), Offset(hx + dir * 6f, hy + 10f), Offset(hx + dir * 28f, hy + 11f),
            strokeWidth = 5f, cap = StrokeCap.Round
        )
        // Black nose on the end of the muzzle, and a narrow cunning eye
        scope.drawCircle(ThreadColor, radius = 4f, center = Offset(hx + dir * 34f, hy + 7f))
        scope.drawLine(
            ThreadColor, Offset(hx + dir * 8f, hy - 4f), Offset(hx + dir * 17f, hy - 1f),
            strokeWidth = 3.5f, cap = StrokeCap.Round
        )
    }

    /** The marginal rabbit that executes knights: long ears, buck teeth, and no sense of scale. */
    private fun drawRabbitHead(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val hx = cx
        val hy = cy - 25f
        val furColor = Color(0xFFD8CBB4)
        val inner = Color(0xFFD4728A)
        val dir = if (fighter.facingRight) 1f else -1f

        val neck = Path().apply {
            moveTo(hx - 9f, hy + 40f); lineTo(hx - 7f, hy + 4f)
            lineTo(hx + 7f, hy + 4f); lineTo(hx + 9f, hy + 40f); close()
        }
        drawStitchedFill(scope, neck, furColor)
        scope.drawStitchedOutline(neck, ThreadColor)

        // The ears. Absurdly long, one up and one folded, so he reads as a rabbit instantly.
        val earSpecs = listOf(-8f to -46f, 6f to -40f)
        earSpecs.forEachIndexed { i, (dx, tipY) ->
            val lean = if (i == 0) -5f else 9f
            val ear = Path().apply {
                moveTo(hx + dx - 5f, hy - 12f)
                quadraticTo(hx + dx + lean - 7f, hy + tipY / 2f, hx + dx + lean, hy + tipY)
                quadraticTo(hx + dx + lean + 7f, hy + tipY / 2f, hx + dx + 5f, hy - 10f)
                close()
            }
            drawStitchedFill(scope, ear, furColor)
            scope.drawStitchedOutline(ear, ThreadColor)
            scope.drawLine(
                inner, Offset(hx + dx, hy - 14f), Offset(hx + dx + lean, hy + tipY * 0.75f),
                strokeWidth = 3f, cap = StrokeCap.Round
            )
        }

        // Round skull
        scope.drawCircle(furColor, radius = 16f, center = Offset(hx, hy + 2f))
        scope.drawCircle(ThreadColor, radius = 16f, center = Offset(hx, hy + 2f), style = StitchedStroke)

        // Eye: a flat black bead, entirely without mercy
        scope.drawCircle(ThreadColor, radius = 3.5f, center = Offset(hx + dir * 7f, hy - 2f))
        // Nose and the two buck teeth under it
        scope.drawCircle(inner, radius = 3f, center = Offset(hx + dir * 15f, hy + 5f))
        listOf(-2.5f, 2.5f).forEach { off ->
            scope.drawLine(
                Color.White,
                Offset(hx + dir * 14f + off, hy + 8f), Offset(hx + dir * 14f + off, hy + 15f),
                strokeWidth = 3f
            )
        }
    }

    private fun drawDogHead(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val hx = cx
        val hy = cy - 25f
        val fur = fighter.hairColor
        val furDark = androidx.compose.ui.graphics.lerp(fur, Color.Black, 0.35f)

        // Furred neck, a touch thicker than a man's
        val neckPath = Path().apply {
            moveTo(hx - 12f, hy + 40f); lineTo(hx - 10f, hy + 5f)
            lineTo(hx + 10f, hy + 5f); lineTo(hx + 12f, hy + 40f); close()
        }
        drawStitchedFill(scope, neckPath, fur)
        scope.drawStitchedOutline(neckPath, ThreadColor)

        // Muzzle geometry by face type
        val muzzleLen = when (fighter.faceNoseShape) {
            1 -> 26f  // hound: long square muzzle
            2 -> 16f  // mastiff: short blunt
            3 -> 30f  // jackal: long and pointed
            else -> 22f // wolfish
        }
        val muzzleDrop = if (fighter.faceNoseShape == 3) 2f else 5f
        val jawDrop = when (fighter.faceBiteShape) {
            1 -> 8f   // underbite: heavy jaw juts forward
            3 -> 10f  // lantern jaw
            else -> 5f
        }
        // A dog's skull is domed and has a STOP — the step down from brow to muzzle that a
        // straight line from crown to nose removes entirely, which is what made these read as
        // an odd wedge rather than a hound. The marginalia cynocephalus is drawn the same way:
        // round braincase, sharp stop, long muzzle carried level.
        val headPath = Path().apply {
            moveTo(hx - 12f, hy + 14f)                          // back of skull, low
            quadraticTo(hx - 14f, hy - 4f, hx - 4f, hy - 9f)    // domed braincase
            quadraticTo(hx + 4f, hy - 12f, hx + 8f, hy - 5f)    // brow ridge
            lineTo(hx + 7f, hy + muzzleDrop - 3f)               // the stop
            lineTo(hx + muzzleLen, hy + muzzleDrop)             // nose tip
            lineTo(hx + muzzleLen - 3f, hy + muzzleDrop + 4f)   // nose pad underside
            lineTo(hx + 9f, hy + 7f + jawDrop)                  // jaw line back under the muzzle
            quadraticTo(hx + 2f, hy + 15f, hx - 6f, hy + 16f)   // cheek ruff
            close()
        }
        drawStitchedFill(scope, headPath, fur)
        scope.drawStitchedOutline(headPath, ThreadColor)
        // Nose pad
        scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(hx + muzzleLen - 1f, hy + muzzleDrop + 1f))

        // Ears by forehead type
        when (fighter.faceForehead) {
            1 -> { // floppy hound ear draped down the skull
                val ear = Path().apply {
                    moveTo(hx - 8f, hy - 5f)
                    quadraticTo(hx - 16f, hy + 2f, hx - 12f, hy + 14f)
                    quadraticTo(hx - 6f, hy + 8f, hx - 4f, hy - 3f)
                    close()
                }
                drawStitchedFill(scope, ear, furDark)
                scope.drawPath(ear, ThreadColor, style = Stroke(width = 2f))
            }
            2 -> { // torn/battle-notched prick ear
                val ear = Path().apply {
                    moveTo(hx - 8f, hy - 4f); lineTo(hx - 10f, hy - 18f)
                    lineTo(hx - 5f, hy - 12f); lineTo(hx - 3f, hy - 16f)
                    lineTo(hx - 1f, hy - 4f); close()
                }
                drawStitchedFill(scope, ear, furDark)
                scope.drawPath(ear, ThreadColor, style = Stroke(width = 2f))
            }
            else -> { // pricked wolf ear
                val ear = Path().apply {
                    moveTo(hx - 8f, hy - 4f); lineTo(hx - 6f, hy - 20f)
                    lineTo(hx + 1f, hy - 5f); close()
                }
                drawStitchedFill(scope, ear, furDark)
                scope.drawPath(ear, ThreadColor, style = Stroke(width = 2f))
            }
        }

        // Jaw dressing: bared fangs or a lolling tongue
        if (fighter.faceBiteShape == 2) {
            // Lolling tongue out the side of the mouth
            val tongue = Path().apply {
                moveTo(hx + muzzleLen - 8f, hy + muzzleDrop + 4f)
                quadraticTo(hx + muzzleLen - 4f, hy + muzzleDrop + 14f, hx + muzzleLen - 10f, hy + muzzleDrop + 16f)
                quadraticTo(hx + muzzleLen - 13f, hy + muzzleDrop + 9f, hx + muzzleLen - 12f, hy + muzzleDrop + 5f)
                close()
            }
            scope.drawPath(tongue, Color(0xFFC96A6A))
            scope.drawPath(tongue, ThreadColor, style = Stroke(width = 1.5f))
        } else {
            // Two white fangs along the jaw
            listOf(muzzleLen - 6f, muzzleLen - 12f).forEach { dx ->
                val fang = Path().apply {
                    moveTo(hx + dx, hy + muzzleDrop + 3f)
                    lineTo(hx + dx - 2f, hy + muzzleDrop + 9f)
                    lineTo(hx + dx - 4f, hy + muzzleDrop + 3f)
                    close()
                }
                scope.drawPath(fang, Color(0xFFF2EAD8))
                scope.drawPath(fang, ThreadColor, style = Stroke(width = 1f))
            }
        }

        // Amber hound eye with a slit pupil
        scope.drawCircle(Color(0xFFD6A420), radius = 3.5f, center = Offset(hx + 2f, hy + 1f))
        scope.drawCircle(ThreadColor, radius = 3.5f, center = Offset(hx + 2f, hy + 1f), style = Stroke(width = 1.5f))
        scope.drawLine(ThreadColor, Offset(hx + 2f, hy - 1f), Offset(hx + 2f, hy + 3f), strokeWidth = 1.8f)

        // A few fur ticks along the cheek so it reads as pelt, not painted skin
        for (i in 0..2) {
            scope.drawLine(
                furDark, Offset(hx - 6f + i * 4f, hy + 10f + i), Offset(hx - 9f + i * 4f, hy + 15f + i),
                strokeWidth = 1.5f
            )
        }
    }

    /** PROTOTYPE knob: 1f is the current even proportion, ~0.8 is the tapestry's small head. */
    private val BAYEUX_HEAD_SCALE = 0.82f

    /**
     * The head, in profile, facing right — brow, nose, lip, chin and jaw.
     *
     * Shared so that every head in the game is the same head. The parade followers used to be
     * drawn as plain circles, which made them read as a different species standing next to the
     * men they follow.
     */
    private fun headProfilePath(
        hx: Float, hy: Float, forehead: Int, noseShape: Int, biteShape: Int
    ): Path {
        // Brows. 0-2 are the originals; 3-5 widen the crowd so a wave stops looking like one
        // man cloned — a sloping brow, a heavy shelf, and a high domed forehead.
        val fhX = when (forehead) {
            1 -> 14f; 2 -> 4f; 3 -> 11f; 4 -> 16f; 5 -> 6f; else -> 8f
        }
        val fhY = when (forehead) {
            1 -> -6f; 3 -> 1f; 4 -> -3f; 5 -> -9f; else -> -2f
        }
        return Path().apply {
            moveTo(hx - 12f, hy)
            // Forehead
            lineTo(hx + fhX, hy + fhY)
            lineTo(hx + fhX + 1f, hy + 2f) // Brow indentation

            // Nose tip and nostril
            var noseTipX = hx + 23f
            var noseTipY = hy + 6f
            var nostrilX = hx + 10f
            when (noseShape) {
                1 -> { noseTipY += 4f; noseTipX -= 2f }          // hook
                2 -> { noseTipX = hx + 16f; nostrilX = hx + 12f } // bulbous
                3 -> noseTipX = hx + 25f                          // pointy
                4 -> { noseTipX = hx + 17f; noseTipY -= 3f }      // snub, turned up
                5 -> { noseTipX = hx + 21f; noseTipY += 2f; nostrilX = hx + 9f } // broken, bent
                else -> noseTipX = hx + 18f                       // normal
            }
            lineTo(noseTipX, noseTipY) // Nose tip
            lineTo(nostrilX, hy + 9f) // Nostril/lip fold

            // Jaw / Bite
            var lipX = hx + 12f
            var chinX = hx + 9f
            when (biteShape) {
                1 -> { lipX += 3f; chinX += 10f }  // underbite
                2 -> { lipX -= 4f; chinX -= 8f }   // overbite
                3 -> chinX += 14f                   // lantern jaw
                4 -> { lipX -= 1f; chinX -= 4f }   // weak, receding chin
                5 -> { lipX += 2f; chinX += 6f }   // square, jutting
            }
            lineTo(lipX, hy + 13f) // Lip crease
            val chinY = when (biteShape) { 3 -> hy + 24f; 5 -> hy + 21f; 4 -> hy + 16f; else -> hy + 18f }
            lineTo(chinX, chinY) // Chin
            lineTo(hx - 12f, chinY)
            close()
        }
    }

    private fun drawHead(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        var headOffsetX = 0f
        var headOffsetY = 0f
        var headRot = 0f

        if ((fighter.isDead || fighter.isDying) && fighter.deathType == DeathType.DECAPITATED) {
            val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
            // Head flies up and back — trajectory varies per victim for ragdoll variety:
            // some heads pop straight up, some sail far, spin count differs
            val seed = kotlin.math.abs(fighter.id.raw.hashCode())
            val flyDir = if (fighter.facingRight) -1f else 1f
            val dist = 40f + (seed % 121)              // 40..160 px
            val peak = 110f + ((seed / 7) % 91)        // 110..200 px arc height
            val spins = 1f + (seed % 3)                // 1..3 full spins
            headOffsetX = flyDir * dist * progress
            headOffsetY = -peak * sin(progress * Math.PI).toFloat() + 50f * progress
            headRot = flyDir * 360f * progress * spins
            
            // Blood fountain is now drawn in world-space in drawCharacter() so it connects properly to the pool!
        }

        // A heavy blunt hit drove this head into the shoulders. It does not come back out.
        val squash = if (fighter.headSquashed) 0.72f else 1f

        scope.withTransform({
            translate(headOffsetX, headOffsetY)
            rotate(headRot, pivot = Offset(cx, cy - 25f))
            if (squash != 1f) scale(1f + (1f - squash) * 0.5f, squash, pivot = Offset(cx, cy + 12f))
            // PROTOTYPE — Bayeux proportion. Figures on the tapestry have notably small heads on
            // long bodies; ours are evenly proportioned, which is most of what makes them read as
            // cartoon rather than as the artefact. Pivoted at the neck join so the head stays
            // attached and only its size changes.
            if (BAYEUX_HEAD_SCALE != 1f) {
                scale(BAYEUX_HEAD_SCALE, BAYEUX_HEAD_SCALE, pivot = Offset(cx, cy + 15f))
            }
        }) {
            val hx = cx
            val hy = cy - 25f

            if (fighter.bossType == BossType.POLYPHEMUS) {
                drawCyclopsHead(scope, cx, cy, fighter)
                return@withTransform
            }

            // A Blemmya has no head at all. Not a small head, none — the face is down in his
            // chest, drawn by drawTorso. Returning here is the whole implementation: no neck, no
            // skull, and nothing for a helm to sit on.
            if (fighter.archetype == EnemyArchetype.BLEMMYA) return@withTransform

            if (fighter.archetype == EnemyArchetype.CRAFTY_FOX) {
                drawFoxHead(scope, cx, cy, fighter)
                return@withTransform
            }

            if (fighter.archetype == EnemyArchetype.KILLER_RABBIT) {
                drawRabbitHead(scope, cx, cy, fighter)
                return@withTransform
            }

            if (fighter.archetype == EnemyArchetype.CYNOCEPHALUS) {
                // Dog-headed man of the mappae mundi: full replacement head, still inside the
                // decapitation/squash transform so his head pops off like anyone else's.
                drawDogHead(scope, cx, cy, fighter)
                return@withTransform
            }

            // 1. Draw Neck
            val neckPath = Path().apply {
                moveTo(cx - 10f, cy + 15f)
                lineTo(cx - 8f, cy - 10f)
                lineTo(cx + 8f, cy - 10f)
                lineTo(cx + 10f, cy + 15f)
                close()
            }
            val skinColor = skinTone(fighter)
        drawStitchedFill(scope, neckPath, skinColor)
        scope.drawStitchedOutline(neckPath, ThreadColor)

        // 2. Head Profile
        val headPath = headProfilePath(
            hx, hy, fighter.faceForehead, fighter.faceNoseShape, fighter.faceBiteShape
        )
        drawStitchedFill(scope, headPath, skinColor)
        scope.drawStitchedOutline(headPath, ThreadColor)

        if (fighter.warPaint == 1) {
            // Woad, daubed straight over the skin: two bars across the eyes and a stripe down the jaw.
            // Deterministic from the id, so a given Saxon wears the same paint every frame.
            val woadRng = Random(fighter.id.raw.hashCode() * 31)
            scope.drawLine(
                Woad,
                Offset(hx - 10f, hy + 2f), Offset(hx + 15f + woadRng.nextFloat() * 4f, hy + 1f),
                strokeWidth = 5f, cap = StrokeCap.Round
            )
            scope.drawLine(
                Woad.copy(alpha = 0.85f),
                Offset(hx - 8f, hy + 9f), Offset(hx + 8f + woadRng.nextFloat() * 5f, hy + 8f),
                strokeWidth = 3f, cap = StrokeCap.Round
            )
            scope.drawLine(
                Woad,
                Offset(hx + 2f, hy + 13f), Offset(hx + 4f, hy + 22f),
                strokeWidth = 3f, cap = StrokeCap.Round
            )
        }

        if (fighter.isKind("beekeeper")) {
            // Wide straw brim with a mesh veil over the face, and his swarm orbiting the hat.
            val brim = Path().apply {
                moveTo(hx - 22f, hy - 6f); lineTo(hx + 22f, hy - 6f)
                lineTo(hx + 18f, hy - 10f); lineTo(hx + 6f, hy - 18f)
                lineTo(hx - 6f, hy - 18f); lineTo(hx - 18f, hy - 10f); close()
            }
            drawStitchedFill(scope, brim, Color(0xFFD9B871))
            scope.drawStitchedOutline(brim, ThreadColor)
            // Veil mesh: translucent panel with cross-hatch threads
            val veil = Path().apply {
                moveTo(hx - 20f, hy - 6f); lineTo(hx + 20f, hy - 6f)
                lineTo(hx + 16f, hy + 20f); lineTo(hx - 16f, hy + 20f); close()
            }
            scope.drawPath(veil, Color(0xFFEFE6D4).copy(alpha = 0.45f))
            for (i in 0..3) {
                scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(hx - 18f + i * 12f, hy - 6f), Offset(hx - 15f + i * 11f, hy + 20f), strokeWidth = 1f)
                scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(hx - 19f, hy - 2f + i * 7f), Offset(hx + 19f, hy - 2f + i * 7f), strokeWidth = 1f)
            }
            scope.drawPath(veil, ThreadColor, style = Stroke(width = 1.5f))
            // The loyal swarm, orbiting the brim (animFrame drives the orbit)
            repeat(4) { b ->
                val a = fighter.animFrame * 1.6f + b * 1.57f
                val bx = hx + cos(a) * (26f + b * 3f)
                val by = hy - 8f + sin(a) * 10f
                scope.drawCircle(if (b % 2 == 0) Color(0xFFD6A420) else Color(0xFF2C2219), radius = 2f, center = Offset(bx, by))
            }
        }

        if (fighter.isKind("hag")) {
            val hagNose = Path().apply {
                moveTo(hx + 10f, hy + 2f)
                lineTo(hx + 35f, hy + 8f) // long pointy
                lineTo(hx + 10f, hy + 10f)
                close()
            }
            drawStitchedFill(scope, hagNose, Color(0xFF6B8E23)) // green warty
            scope.drawStitchedOutline(hagNose, ThreadColor)
            // Warts
            scope.drawCircle(Color(0xFF4A5D23), radius = 1.5f, center = Offset(hx + 20f, hy + 6f))
            scope.drawCircle(Color(0xFF4A5D23), radius = 1f, center = Offset(hx + 28f, hy + 5f))
            scope.drawCircle(Color(0xFF4A5D23), radius = 2f, center = Offset(hx + 15f, hy + 8f))
        }


        // Face RNG for scars/eyepatches
        val faceRng = kotlin.random.Random(fighter.id.raw.hashCode())
        val hasEyepatch = !fighter.isPlayer && faceRng.nextFloat() < 0.2f
        val hasScars = fighter.level > 1 && faceRng.nextFloat() < 0.5f
        val hasTiredEyes = faceRng.nextFloat() < 0.2f
        val numWarts = if (faceRng.nextFloat() < 0.2f) faceRng.nextInt(1, 4) else 0
        val hasBeautySpot = faceRng.nextFloat() < 0.1f
        val hasJuttingTooth = faceRng.nextFloat() < 0.15f
        val hasLongBeard = !fighter.isPlayer && faceRng.nextFloat() < 0.15f

        // 3. Embroidered eye and facial features
        if (fighter.bossType == BossType.POLYPHEMUS) {
            // One eye, and it is enormous — the whole silhouette hangs on it, so it is drawn
            // instead of the ordinary almond rather than on top of it, and centred on the brow
            // where two eyes would otherwise sit.
            val ex = hx + 4.5f
            val ey = hy + 3f
            scope.drawCircle(Color.White, radius = 9f, center = Offset(ex, ey))
            scope.drawCircle(ThreadColor, radius = 9f, center = Offset(ex, ey), style = Stroke(width = 2f))
            scope.drawCircle(Color(0xFF8A5E38), radius = 5f, center = Offset(ex, ey))
            scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(ex, ey))
            // A single heavy brow right across it
            scope.drawLine(
                ThreadColor, Offset(ex - 11f, ey - 10f), Offset(ex + 11f, ey - 8f),
                strokeWidth = 5f, cap = StrokeCap.Round
            )
        } else if (hasEyepatch) {
            // Draw black eyepatch over the eye
            scope.drawPath(Path().apply {
                moveTo(hx - 1f, hy + 2f)
                quadraticTo(hx + 4f, hy, hx + 10f, hy + 4f)
                quadraticTo(hx + 8f, hy + 10f, hx, hy + 7f)
                close()
            }, Color(0xFF1E1A17))
            scope.drawLine(Color(0xFF1E1A17), Offset(hx - 10f, hy - 2f), Offset(hx + 12f, hy + 8f), strokeWidth = 2.5f)
        } else {
            // Draw white almond-shaped eye sclera
            val eyeScleraPath = Path().apply {
                moveTo(hx + 1f, hy + 4f)
                quadraticTo(hx + 4.5f, hy + 1f, hx + 8f, hy + 4f)
                quadraticTo(hx + 4.5f, hy + 7f, hx + 1f, hy + 4f)
                close()
            }
            scope.drawPath(eyeScleraPath, Color.White)
            scope.drawPath(eyeScleraPath, ThreadColor, style = Stroke(width = 1.5f))
            
            // Pupil centered inside the almond
            scope.drawCircle(ThreadColor, radius = 1.5f, center = Offset(hx + 4.5f, hy + 4f))

            if (hasTiredEyes) {
                scope.drawPath(Path().apply {
                    moveTo(hx + 1f, hy + 7f)
                    quadraticTo(hx + 4.5f, hy + 10f, hx + 8f, hy + 6f)
                }, Color(0x664A3D36), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            }
        }
        
        // Eyebrow variants: bushy, angry-slanted, raised, or the classic blocky line.
        // The cyclops brought his own single brow above; a second one lands across his eye.
        if (fighter.bossType != BossType.POLYPHEMUS) when ((kotlin.math.abs(fighter.id.raw.hashCode()) / 13) % 4) {
            0 -> scope.drawLine(ThreadColor, Offset(hx - 1f, hy - 1f), Offset(hx + 9f, hy + 1f), strokeWidth = 4f, cap = StrokeCap.Round) // bushy
            1 -> scope.drawLine(ThreadColor, Offset(hx, hy + 2f), Offset(hx + 8f, hy - 2f), strokeWidth = 2.5f, cap = StrokeCap.Round) // angry slant
            2 -> scope.drawLine(ThreadColor, Offset(hx, hy - 3f), Offset(hx + 8f, hy - 2f), strokeWidth = 2.5f, cap = StrokeCap.Round) // raised/surprised
            else -> scope.drawLine(ThreadColor, Offset(hx, hy), Offset(hx + 8f, hy + 1f), strokeWidth = 2.5f, cap = StrokeCap.Round) // classic
        }

        // Cheek/jowl line on some faces
        if (faceRng.nextFloat() < 0.3f) {
            scope.drawPath(Path().apply {
                moveTo(hx - 4f, hy + 8f)
                quadraticTo(hx - 1f, hy + 12f, hx + 2f, hy + 15f)
            }, ThreadColor.copy(alpha = 0.5f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
        }

        // Mouth variants: grim line, downturned frown, or slight open gawp
        when ((kotlin.math.abs(fighter.id.raw.hashCode()) / 29) % 3) {
            0 -> scope.drawPath(Path().apply {
                moveTo(hx + 6f, hy + 12f)
                quadraticTo(hx + 8.5f, hy + 15f, hx + 11f, hy + 12f)
            }, ThreadColor, style = Stroke(width = 1.5f, cap = StrokeCap.Round)) // frown
            1 -> {
                scope.drawCircle(Color(0xFF3A241C), radius = 2f, center = Offset(hx + 8f, hy + 13f)) // gawp
            }
            else -> scope.drawLine(ThreadColor, Offset(hx + 6f, hy + 13f), Offset(hx + 11f, hy + 13f), strokeWidth = 1.5f, cap = StrokeCap.Round) // grim line
        }

        if (hasJuttingTooth) {
            scope.drawLine(Color(0xFFEFE6D4), Offset(hx + 8f, hy + 13f), Offset(hx + 8f, hy + 9f), strokeWidth = 2.5f)
            scope.drawLine(ThreadColor, Offset(hx + 6.5f, hy + 13f), Offset(hx + 6.5f, hy + 9f), strokeWidth = 1f)
        }

        if (hasBeautySpot) {
            scope.drawCircle(Color(0xFF2C1E16), radius = 1.5f, center = Offset(hx - 2f, hy + 12f))
        }
        for (i in 0 until numWarts) {
            val wx = hx + faceRng.nextFloat() * 10f
            val wy = hy + 8f + faceRng.nextFloat() * 12f
            scope.drawCircle(Color(0xFF4A5C3D), radius = 1.5f + faceRng.nextFloat() * 1f, center = Offset(wx, wy))
            scope.drawCircle(Color(0xFF1C2B11), radius = 0.5f, center = Offset(wx, wy)) // wart core
        }

        // Buboes: the swellings that make the plague read as bubonic rather than "man painted
        // yellow". Anyone carrying it gets them — the peasant from spawn, his victims once infected.
        if (fighter.diseaseDuration > 0f || fighter.isContagious) {
            val boRng = kotlin.random.Random(fighter.id.raw.hashCode().toLong() xor 0x8UL.toLong())
            repeat(5) {
                val bx = hx - 4f + boRng.nextFloat() * 16f
                val by = hy + 2f + boRng.nextFloat() * 18f
                val r = 1.8f + boRng.nextFloat() * 1.6f
                scope.drawCircle(PlagueBubo.copy(alpha = 0.75f), radius = r + 0.8f, center = Offset(bx, by))
                scope.drawCircle(PlagueBubo, radius = r, center = Offset(bx, by))
            }
        }

        // Persistent Blood Decals
        fighter.bloodDecals.forEach { decal ->
            val sx = hx + decal.first
            val sy = hy + decal.second
            when (decal.third) {
                0, 1 -> { // Cut with optional stitches
                    scope.drawLine(Color(0xFF8B0000), Offset(sx, sy), Offset(sx + 8f, sy + 4f), strokeWidth = 2f)
                    if (decal.first > 0f) {
                        scope.drawLine(ThreadColor, Offset(sx + 2f, sy), Offset(sx + 2f, sy + 6f), strokeWidth = 1f)
                        scope.drawLine(ThreadColor, Offset(sx + 5f, sy + 1f), Offset(sx + 5f, sy + 7f), strokeWidth = 1f)
                    }
                }
                2 -> { // Eyepatch / big bruise
                    scope.drawPath(Path().apply {
                        moveTo(sx - 1f, sy + 2f)
                        quadraticTo(sx + 4f, sy, sx + 10f, sy + 4f)
                        quadraticTo(sx + 8f, sy + 10f, sx, sy + 7f)
                        close()
                    }, Color(0x661E1A17))
                }
                3 -> { // Puffy/Tears Eye
                    val puffPath = androidx.compose.ui.graphics.Path().apply {
                        addArc(androidx.compose.ui.geometry.Rect(sx + 1f, sy + 3f, sx + 11f, sy + 10f), 0f, 180f)
                    }
                    scope.drawPath(puffPath, Color(0xFFD4624A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = StrokeCap.Round))
                }
                else -> { // Blood smear
                     scope.drawCircle(Color(0x888B0000), radius = 3f + (decal.first % 4f), center = Offset(sx, sy))
                }
            }
        }

        // Accrued Bandages
        if (fighter.bandagesCount > 0) {
            val bandageRng = kotlin.random.Random(fighter.id.raw.hashCode())
            for (i in 0 until fighter.bandagesCount) {
                // Keep bandages up on the forehead/crown most of the time so the face
                // stays visible on the share screen; only rarely across the face itself
                val onFace = bandageRng.nextFloat() < 0.2f
                val bx = hx - 5f + bandageRng.nextFloat() * 20f
                val by = if (onFace) hy + 5f + bandageRng.nextFloat() * 15f else hy - 12f + bandageRng.nextFloat() * 8f
                val ang = -30f + bandageRng.nextFloat() * 60f
                scope.withTransform({ rotate(ang, pivot = Offset(bx, by)) }) {
                    scope.drawRect(Color(0xFFF5F0E8), topLeft = Offset(bx - 12f, by - 3f), size = androidx.compose.ui.geometry.Size(24f, 6f))
                    scope.drawLine(Color(0xFFA8906A), Offset(bx - 4f, by - 3f), Offset(bx - 4f, by + 3f), strokeWidth = 1.5f)
                    scope.drawLine(Color(0xFFA8906A), Offset(bx + 4f, by - 3f), Offset(bx + 4f, by + 3f), strokeWidth = 1.5f)
                }
            }
        }


            // Hair
            val hairPath = Path().apply {
                if (fighter.hairStyle == "long") {
                    moveTo(hx - 12f, hy)
                    lineTo(hx - 5f, hy - 12f)
                    lineTo(hx + 5f, hy - 10f)
                    lineTo(hx + 2f, hy - 2f)
                    lineTo(hx - 6f, hy + 18f)
                    lineTo(hx - 14f, hy + 20f)
                    close()
                } else if (fighter.hairStyle == "bald") {
                    moveTo(hx - 12f, hy + 5f)
                    lineTo(hx - 12f, hy + 10f)
                    lineTo(hx - 6f, hy + 10f)
                    lineTo(hx - 6f, hy + 5f)
                    close()
                } else if (fighter.hairStyle == "hair_tonsure_norman") {
                    // The Norman crop: shaved high at the back, a blunt fringe at the front.
                    moveTo(hx - 12f, hy + 2f)
                    lineTo(hx - 3f, hy - 9f)
                    lineTo(hx + 7f, hy - 7f)
                    lineTo(hx + 4f, hy + 1f)
                    lineTo(hx - 4f, hy + 2f)
                    lineTo(hx - 12f, hy + 8f)
                    close()
                } else if (fighter.hairStyle == "hair_braids") {
                    // Two heavy braids falling past the jaw, in the northern manner.
                    moveTo(hx - 12f, hy)
                    lineTo(hx - 3f, hy - 11f)
                    lineTo(hx + 7f, hy - 9f)
                    lineTo(hx + 4f, hy - 1f)
                    lineTo(hx - 2f, hy + 3f)
                    lineTo(hx - 4f, hy + 26f)
                    lineTo(hx - 10f, hy + 26f)
                    lineTo(hx - 9f, hy + 4f)
                    lineTo(hx - 14f, hy + 24f)
                    lineTo(hx - 19f, hy + 22f)
                    lineTo(hx - 13f, hy + 4f)
                    close()
                } else if (fighter.hairStyle == "hair_tonsure_monk") {
                    // A monk's tonsure: bare crown, a ring of hair all round.
                    moveTo(hx - 13f, hy + 1f)
                    lineTo(hx - 13f, hy + 9f)
                    lineTo(hx - 5f, hy + 11f)
                    lineTo(hx + 6f, hy + 8f)
                    lineTo(hx + 6f, hy + 1f)
                    lineTo(hx - 2f, hy + 4f)
                    close()
                } else if (fighter.hairStyle == "hair_topknot") {
                    // Shaved sides and a bound knot on top — an old campaigner's affectation.
                    moveTo(hx - 11f, hy + 1f)
                    lineTo(hx - 5f, hy - 6f)
                    lineTo(hx + 5f, hy - 5f)
                    lineTo(hx + 3f, hy + 2f)
                    lineTo(hx - 5f, hy + 4f)
                    close()
                    // The knot itself.
                    moveTo(hx - 6f, hy - 6f)
                    lineTo(hx - 9f, hy - 22f)
                    lineTo(hx - 1f, hy - 24f)
                    lineTo(hx + 2f, hy - 6f)
                    close()
                } else if (fighter.hairStyle == "hair_mystic") {
                    // A seer's uncut mane: flat across the brow, then one heavy fall of straight
                    // hair all the way down the back, well past the shoulder blades.
                    moveTo(hx - 12f, hy)
                    lineTo(hx - 3f, hy - 11f)
                    lineTo(hx + 7f, hy - 9f)
                    lineTo(hx + 4f, hy - 1f)
                    lineTo(hx - 2f, hy + 2f)
                    lineTo(hx - 5f, hy + 54f)   // the fall, straight and long
                    lineTo(hx - 17f, hy + 52f)
                    lineTo(hx - 14f, hy + 3f)
                    close()
                } else if (fighter.hairStyle == "hair_samson") {
                    // Samsonite locks: a full uncut mane, wider than the head and falling in
                    // seven heavy hanks. Bigger in every direction than the mystic's straight
                    // fall — you should be able to tell these two apart at a glance.
                    moveTo(hx - 16f, hy - 2f)
                    lineTo(hx - 5f, hy - 15f)
                    lineTo(hx + 9f, hy - 12f)
                    lineTo(hx + 6f, hy - 1f)
                    lineTo(hx + 1f, hy + 3f)
                    lineTo(hx + 4f, hy + 40f)
                    lineTo(hx - 6f, hy + 44f)
                    lineTo(hx - 10f, hy + 46f)
                    lineTo(hx - 22f, hy + 42f)
                    lineTo(hx - 18f, hy + 4f)
                    close()
                    // Hanks, cut into the mass so it does not read as one slab of colour.
                    moveTo(hx - 20f, hy + 14f)
                    lineTo(hx - 25f, hy + 34f)
                    lineTo(hx - 19f, hy + 36f)
                    lineTo(hx - 16f, hy + 15f)
                    close()
                    moveTo(hx + 2f, hy + 12f)
                    lineTo(hx + 8f, hy + 32f)
                    lineTo(hx + 2f, hy + 35f)
                    lineTo(hx - 1f, hy + 13f)
                    close()
                } else if (fighter.hairStyle == "hair_germanic") {
                    // Pigtails: a centre-parted cap with a bound tail swinging off each side.
                    moveTo(hx - 12f, hy)
                    lineTo(hx - 3f, hy - 11f)
                    lineTo(hx + 7f, hy - 9f)
                    lineTo(hx + 4f, hy - 1f)
                    lineTo(hx - 4f, hy + 3f)
                    lineTo(hx - 13f, hy + 4f)
                    close()
                    // Left tail, hanging clear of the jaw.
                    moveTo(hx - 15f, hy + 2f)
                    lineTo(hx - 24f, hy + 20f)
                    lineTo(hx - 17f, hy + 24f)
                    lineTo(hx - 10f, hy + 4f)
                    close()
                    // Right tail, shorter — it reads as the far side of the head.
                    moveTo(hx + 3f, hy + 1f)
                    lineTo(hx + 9f, hy + 16f)
                    lineTo(hx + 3f, hy + 19f)
                    lineTo(hx - 1f, hy + 3f)
                    close()
                } else { // "short" or default bowl cut
                    moveTo(hx - 12f, hy)
                    lineTo(hx - 2f, hy - 10f)
                    lineTo(hx + 6f, hy - 8f)
                    lineTo(hx + 3f, hy - 1f)
                    lineTo(hx - 6f, hy + 4f)
                    lineTo(hx - 12f, hy + 6f)
                    close()
                }
            }
            drawStitchedFill(scope, hairPath, fighter.hairColor)
            scope.drawStitchedOutline(hairPath, ThreadColor)

            // Hag's dodgy crooked witch hat
            if (fighter.isKind("hag")) {
                val hatColor = Color(0xFF2B2A22)
                val hatPath = Path().apply {
                    moveTo(hx - 16f, hy - 6f)
                    lineTo(hx + 4f, hy - 44f) // crooked pointed tip
                    lineTo(hx + 11f, hy - 4f)
                    close()
                }
                drawStitchedFill(scope, hatPath, hatColor)
                scope.drawStitchedOutline(hatPath, ThreadColor)
                val brimPath = Path().apply {
                    moveTo(hx - 21f, hy - 4f)
                    lineTo(hx + 17f, hy - 6f)
                    lineTo(hx + 13f, hy)
                    lineTo(hx - 19f, hy + 2f)
                    close()
                }
                drawStitchedFill(scope, brimPath, hatColor)
                scope.drawStitchedOutline(brimPath, ThreadColor)
            }

            // Draw facial hair
            val mustache = Path().apply {
                if (hasLongBeard) {
                    val beardRight = when (fighter.faceBiteShape) {
                        1 -> hx + 21f // underbite
                        3 -> hx + 25f // lantern jaw
                        else -> hx + 14f
                    }
                    moveTo(hx + 3f, hy + 12f)
                    lineTo(beardRight, hy + 14f)
                    quadraticTo(
                        beardRight - 2f,
                        hy + 25f,
                        hx + 8f,
                        hy + 40f + faceRng.nextFloat() * 20f
                    )
                    lineTo(hx - 8f, hy + 35f)
                    lineTo(hx - 12f, hy + 18f)
                    close()
                // The Englishmen of the tapestry are known by their moustaches: long, heavy things
                // that spring from under the nose and sweep down past the corner of the mouth,
                // tapering to a point. All four below are curved, filled, tapering masses.
                //
                // They used to be two single stroked quadratics — which draw as a bare quarter-
                // circle scratch — and two three-point triangles. Neither reads as hair.
                // Kept well INSIDE the nose. In profile the moustache sits on the lip and drops
                // toward the jaw; when it reached out as far as the nose tip (hx+18..25) it read
                // as a second nose rather than as hair, which is a perspective error, not a
                // styling one. Nothing here goes past hx+14.
                } else if (fighter.faceMustache == 0) { // Full, drooping at the corner
                    moveTo(hx + 12f, hy + 10f)
                    quadraticTo(hx + 6f, hy + 10f, hx + 2f, hy + 16f)
                    quadraticTo(hx + 6f, hy + 13f, hx + 12f, hy + 13f)
                    close()
                } else if (fighter.faceMustache == 1) { // A longer fall past the mouth
                    moveTo(hx + 12f, hy + 10f)
                    quadraticTo(hx + 5f, hy + 10f, hx + 1f, hy + 19f)
                    quadraticTo(hx + 5f, hy + 13f, hx + 12f, hy + 13f)
                    close()
                } else if (fighter.faceMustache == 2) { // Bushy, level, barely falls at all
                    moveTo(hx + 13f, hy + 9f)
                    quadraticTo(hx + 6f, hy + 9f, hx + 1f, hy + 13f)
                    quadraticTo(hx + 6f, hy + 13f, hx + 13f, hy + 13f)
                    close()
                } else { // Trim
                    moveTo(hx + 12f, hy + 10f)
                    quadraticTo(hx + 7f, hy + 10f, hx + 4f, hy + 14f)
                    quadraticTo(hx + 7f, hy + 13f, hx + 12f, hy + 13f)
                    close()
                }
            }
            // Filled in hair colour with a hairline edge in the SAME colour — a heavy ThreadColor
            // outline on something this small swallowed the shape and read as a black smear.
            drawStitchedFill(scope, mustache, fighter.hairColor)
            scope.drawPath(mustache, fighter.hairColor, style = Stroke(width = 0.8f, cap = StrokeCap.Round))

        // 4. Helmet Overlay
        val helmId = fighter.headgear.id

        if (helmId == "helm_coif" || fighter.extraArmors.any { it.id == "armor_coif" }) {
            val coifPath = Path().apply {
                moveTo(hx - 14f, hy + 10f)
                lineTo(hx - 14f, hy - 4f)
                lineTo(hx, hy - 12f)
                lineTo(hx + 12f, hy - 4f)
                lineTo(hx + 12f, hy + 14f)
                lineTo(hx - 2f, hy + 14f)
                lineTo(hx - 10f, hy + 12f)
                close()
            }
            drawStitchedFill(scope, coifPath, Color(0xFF868C91))
            scope.drawStitchedOutline(coifPath, ThreadColor)
            drawChainmailTexture(scope, hx - 12f, hy - 4f, 24f, 18f)
        }
        
        if (helmId == "helm_conical") {
            // Authentic conical metal helmet
            val helmPath = Path().apply {
                moveTo(hx - 15f, hy)
                lineTo(hx, hy - 25f) // pointed top
                lineTo(hx + 15f, hy)
                close()
            }
            drawStitchedFill(scope, helmPath, Color(0xFFBAC5CC)) // metallic steel
            scope.drawStitchedOutline(helmPath, ThreadColor)

            // Nasal Guard (iron strip protecting the nose)
            val nasalPath = Path().apply {
                moveTo(hx + 9f, hy)
                lineTo(hx + 11f, hy + 14f)
                lineTo(hx + 6f, hy + 14f)
                lineTo(hx + 6f, hy)
                close()
            }
            drawStitchedFill(scope, nasalPath, Color(0xFF727A80))
            scope.drawStitchedOutline(nasalPath, ThreadColor)
        } else if (helmId == "helm_cervelliere") {
            val skullCap = Path().apply {
                moveTo(hx - 14f, hy)
                quadraticTo(hx, hy - 20f, hx + 14f, hy)
                close()
            }
            drawStitchedFill(scope, skullCap, Color(0xFF9EA3A8))
            scope.drawStitchedOutline(skullCap, ThreadColor)
        } else if (helmId == "helm_phrygian") {
            // Iron cap whose peak curls forward over the brow — the Norman helm of the tapestry
            val cap = Path().apply {
                moveTo(hx - 15f, hy + 2f)
                quadraticTo(hx - 14f, hy - 20f, hx + 2f, hy - 24f)
                quadraticTo(hx + 16f, hy - 26f, hx + 13f, hy - 12f) // the forward curl
                quadraticTo(hx + 11f, hy - 4f, hx + 15f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, cap, Color(0xFF8C959B))
            scope.drawStitchedOutline(cap, ThreadColor)
            scope.drawLine(ThreadColor, Offset(hx - 12f, hy - 6f), Offset(hx + 10f, hy - 10f), strokeWidth = 1.5f)
        } else if (helmId == "helm_mitre") {
            // Bishop Odo's cloth-of-gold mitre: two peaks with an orphrey band
            val mitre = Path().apply {
                moveTo(hx - 14f, hy + 2f)
                lineTo(hx - 9f, hy - 30f)
                lineTo(hx, hy - 16f)
                lineTo(hx + 9f, hy - 32f)
                lineTo(hx + 14f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, mitre, Color(0xFFD8C48A))
            scope.drawStitchedOutline(mitre, ThreadColor)
            scope.drawLine(Color(0xFF9E3624), Offset(hx - 12f, hy - 6f), Offset(hx + 12f, hy - 6f), strokeWidth = 3f)
            scope.drawLine(Color(0xFF9E3624), Offset(hx - 2f, hy - 24f), Offset(hx + 2f, hy - 4f), strokeWidth = 2f)
        } else if (helmId == "helm_straw") {
            // Wide straw brim with a low crown
            val brim = Path().apply {
                moveTo(hx - 26f, hy + 1f)
                quadraticTo(hx, hy - 6f, hx + 26f, hy + 1f)
                quadraticTo(hx, hy + 7f, hx - 26f, hy + 1f)
                close()
            }
            drawStitchedFill(scope, brim, Color(0xFFD9B871))
            scope.drawStitchedOutline(brim, ThreadColor)
            val crown = Path().apply {
                moveTo(hx - 12f, hy - 1f)
                quadraticTo(hx, hy - 20f, hx + 12f, hy - 1f)
                close()
            }
            drawStitchedFill(scope, crown, Color(0xFFC9A45C))
            scope.drawStitchedOutline(crown, ThreadColor)
            // straw texture
            for (i in -2..2) {
                scope.drawLine(Color(0xFFA98643), Offset(hx + i * 5f, hy - 2f), Offset(hx + i * 5f + 2f, hy - 12f), strokeWidth = 1f)
            }
        } else if (helmId == "helm_spangen") {
            val spangen = Path().apply {
                moveTo(hx - 16f, hy + 2f)
                lineTo(hx - 16f, hy - 4f)
                quadraticTo(hx, hy - 24f, hx + 16f, hy - 4f)
                lineTo(hx + 16f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, spangen, Color(0xFF7A8389))
            scope.drawStitchedOutline(spangen, ThreadColor)
            // Rivet bands
            scope.drawLine(Color(0xFFB08221), Offset(hx - 16f, hy), Offset(hx + 16f, hy), strokeWidth = 3f)
            scope.drawLine(Color(0xFFB08221), Offset(hx, hy), Offset(hx, hy - 20f), strokeWidth = 3f)
        } else if (helmId == "helm_kettle") {
            val kettle = Path().apply {
                moveTo(hx - 12f, hy - 4f)
                quadraticTo(hx, hy - 22f, hx + 12f, hy - 4f)
                close()
            }
            drawStitchedFill(scope, kettle, Color(0xFF8B9298))
            scope.drawStitchedOutline(kettle, ThreadColor)
            // Wide brim
            val brim = Path().apply {
                moveTo(hx - 22f, hy)
                quadraticTo(hx, hy - 6f, hx + 22f, hy)
                lineTo(hx + 20f, hy + 4f)
                quadraticTo(hx, hy - 2f, hx - 20f, hy + 4f)
                close()
            }
            drawStitchedFill(scope, brim, Color(0xFF8B9298))
            scope.drawStitchedOutline(brim, ThreadColor)
        } else if (helmId == "helm_mask") {
            val mask = Path().apply {
                moveTo(hx - 16f, hy - 20f)
                quadraticTo(hx, hy - 26f, hx + 16f, hy - 20f)
                lineTo(hx + 18f, hy + 18f)
                quadraticTo(hx, hy + 22f, hx - 16f, hy + 18f)
                close()
            }
            drawStitchedFill(scope, mask, Color(0xFF7B858B))
            scope.drawStitchedOutline(mask, ThreadColor)
            // One eye slit. The head is drawn in profile, so the far eye is round the back of it.
            scope.drawLine(Color(0xFF222222), Offset(hx + 2f, hy - 2f), Offset(hx + 14f, hy + 2f), strokeWidth = 2.5f)
            // Breathing holes
            scope.drawCircle(Color(0xFF222222), radius = 1.5f, center = Offset(hx + 10f, hy + 10f))
            scope.drawCircle(Color(0xFF222222), radius = 1.5f, center = Offset(hx + 6f, hy + 12f))
            scope.drawCircle(Color(0xFF222222), radius = 1.5f, center = Offset(hx + 14f, hy + 12f))
        } else if (helmId == "helm_great") {
            // A flat topped cylindrical great helm (bucket helm)
            val bucketPath = Path().apply {
                moveTo(hx - 16f, hy - 20f)
                lineTo(hx + 18f, hy - 20f) // flat top
                lineTo(hx + 18f, hy + 18f)
                lineTo(hx - 16f, hy + 18f)
                close()
            }
            drawStitchedFill(scope, bucketPath, Color(0xFF727A80))
            scope.drawStitchedOutline(bucketPath, ThreadColor)
            
            // Eye slit
            val slitPath = Path().apply {
                moveTo(hx + 2f, hy - 4f)
                lineTo(hx + 18f, hy - 4f)
                lineTo(hx + 18f, hy - 1f)
                lineTo(hx + 2f, hy - 1f)
                close()
            }
            drawStitchedFill(scope, slitPath, Color(0xFF222222))
            
            // Cross studs / brass trim
            scope.drawLine(Color(0xFFB08221), Offset(hx + 10f, hy - 20f), Offset(hx + 10f, hy + 18f), strokeWidth = 2f)
            scope.drawLine(Color(0xFFB08221), Offset(hx - 16f, hy - 2f), Offset(hx + 18f, hy - 2f), strokeWidth = 2f)
        } else if (helmId == "helm_jester") {
            val hatPath = Path().apply {
                moveTo(hx - 15f, hy)
                quadraticTo(hx - 20f, hy - 30f, hx - 30f, hy - 10f) // Left floop
                lineTo(hx - 20f, hy - 5f)
                lineTo(hx, hy - 30f) // Center floop
                lineTo(hx + 10f, hy - 10f)
                lineTo(hx + 15f, hy - 5f)
                quadraticTo(hx + 20f, hy - 30f, hx + 30f, hy - 10f) // Right floop
                close()
            }
            drawStitchedFill(scope, hatPath, Color(0xFFD63C3C))
            scope.drawStitchedOutline(hatPath, ThreadColor)
            // Bells
            scope.drawCircle(Color(0xFFFFD700), radius = 4f, center = Offset(hx - 30f, hy - 10f))
            scope.drawCircle(Color(0xFFFFD700), radius = 4f, center = Offset(hx, hy - 30f))
            scope.drawCircle(Color(0xFFFFD700), radius = 4f, center = Offset(hx + 30f, hy - 10f))
        } else if (helmId == "helm_antlered") {
            // A spangenhelm silhouette, with a rack of antlers off the brow.
            val cap = Path().apply {
                moveTo(hx - 16f, hy + 2f)
                lineTo(hx - 16f, hy - 4f)
                quadraticTo(hx, hy - 24f, hx + 16f, hy - 4f)
                lineTo(hx + 16f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, cap, Color(0xFF7A868C))
            scope.drawStitchedOutline(cap, ThreadColor)
            val antler = Color(0xFFBFA278)
            listOf(-1f, 1f).forEach { side ->
                val beam = Path().apply {
                    moveTo(hx + side * 9f, hy - 16f)
                    quadraticTo(hx + side * 20f, hy - 34f, hx + side * 15f, hy - 46f)
                }
                scope.drawPath(beam, antler, style = Stroke(width = 4f, cap = StrokeCap.Round))
                // Tines, so it reads as a rack and not a pair of bent wires.
                scope.drawLine(antler, Offset(hx + side * 16f, hy - 28f), Offset(hx + side * 27f, hy - 33f), strokeWidth = 3f, cap = StrokeCap.Round)
                scope.drawLine(antler, Offset(hx + side * 16f, hy - 38f), Offset(hx + side * 26f, hy - 45f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        } else if (helmId == "helm_winged") {
            val cap = Path().apply {
                moveTo(hx - 15f, hy + 2f)
                quadraticTo(hx, hy - 24f, hx + 15f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, cap, Color(0xFF8C969E))
            scope.drawStitchedOutline(cap, ThreadColor)
            // Wings sweeping back from the temples.
            listOf(-1f, 1f).forEach { side ->
                val wing = Path().apply {
                    moveTo(hx + side * 13f, hy - 6f)
                    quadraticTo(hx + side * 34f, hy - 20f, hx + side * 30f, hy + 2f)
                    quadraticTo(hx + side * 22f, hy - 4f, hx + side * 13f, hy - 6f)
                    close()
                }
                drawStitchedFill(scope, wing, Color(0xFFD8D2C4))
                scope.drawStitchedOutline(wing, ThreadColor)
            }
        } else if (helmId == "helm_wolf") {
            // A hood of pelt: snout forward over the brow, ears up, pelt falling behind.
            val cowl = Path().apply {
                moveTo(hx - 18f, hy + 6f)
                quadraticTo(hx - 14f, hy - 10f, hx + 6f, hy - 18f)
                quadraticTo(hx + 20f, hy - 8f, hx + 25f, hy - 4f)
                lineTo(hx + 5.5f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, cowl, Color(0xFF5A5048))
            scope.drawStitchedOutline(cowl, ThreadColor)
            // Ears.
            listOf(-6f, 8f).forEach { ex ->
                val ear = Path().apply {
                    moveTo(hx + ex - 4f, hy - 18f)
                    lineTo(hx + ex, hy - 30f)
                    lineTo(hx + ex + 5f, hy - 17f)
                    close()
                }
                drawStitchedFill(scope, ear, Color(0xFF4A4038))
                scope.drawStitchedOutline(ear, ThreadColor)
            }
            // The wolf's own eye, on the snout above the wearer's.
            scope.drawCircle(Color(0xFFD9B871), radius = 2f, center = Offset(hx + 10f, hy - 9.5f))
            scope.drawCircle(Color(0xFF2C2219), radius = 3f, center = Offset(hx + 25.5f, hy - 4f))
            scope.drawCircle(Color(0xFF2C2219), radius = 2f, center = Offset(hx + 25.5f, hy - 4f), style = StitchedStroke)
            val triangle_3 = Path().apply {
                moveTo(hx + 21.5f, hy - 2.5f)
                lineTo(hx + 21f, hy)
                lineTo(hx + 19f, hy - 1.5f)
                close()
            }
            drawStitchedFill(scope, triangle_3, Color(0xFFFAF6EB))
            scope.drawStitchedOutline(triangle_3, Color(0xFFBAC5CC))
            val triangle_4 = Path().apply {
                moveTo(hx + 18f, hy - 1.5f)
                lineTo(hx + 17.5f, hy + 2f)
                lineTo(hx + 15.5f, hy - 0.5f)
                close()
            }
            drawStitchedFill(scope, triangle_4, Color(0xFFFAF6EB))
            scope.drawStitchedOutline(triangle_4, Color(0xFFBAC5CC))
            val triangle_5 = Path().apply {
                moveTo(hx + 13.5f, hy + 0.5f)
                lineTo(hx + 13.5f, hy + 4f)
                lineTo(hx + 10f, hy + 1.5f)
                close()
            }
            drawStitchedFill(scope, triangle_5, Color(0xFFFAF6EB))
            scope.drawStitchedOutline(triangle_5, Color(0xFFBAC5CC))
} else if (helmId == "helm_pot") {
            // A cauldron, upside down, jammed on. Flat base up, handle out the side.
            val pot = Path().apply {
                moveTo(hx - 17f, hy - 20f)
                lineTo(hx + 17f, hy - 20f)
                lineTo(hx + 15f, hy + 6f)
                lineTo(hx - 15f, hy + 6f)
                close()
            }
            drawStitchedFill(scope, pot, Color(0xFF4A4E51))
            scope.drawStitchedOutline(pot, ThreadColor)
            // Rim (the pot's foot, now its crown) and a soot band.
            scope.drawLine(Color(0xFF6E7477), Offset(hx - 17f, hy - 18f), Offset(hx + 17f, hy - 18f), strokeWidth = 3f)
            scope.drawLine(Color(0xFF2C2219), Offset(hx - 16f, hy - 6f), Offset(hx + 16f, hy - 6f), strokeWidth = 2f)
            // Handle.
            scope.drawArc(
                color = Color(0xFF6E7477),
                startAngle = -90f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(hx + 14f, hy - 16f),
                size = Size(14f, 18f),
                style = Stroke(width = 3f)
            )
        }

        // A crown is worn by an enemy lord — and by any player who equips one. It used to hang on
        // isLord alone, so the King's Crown headgear rendered as nothing at all.
        if (fighter.isLord || fighter.headgear.id == "helm_crown") {
            // Seat the band ON something. It was pinned at hy-10 whatever was underneath, and
            // the skull's own top edge is only around hy-4 — so it hovered above a bare head and
            // floated well clear of a helm.
            val wearsHelm = fighter.headgear.id !in listOf("helm_none", "helm_crown")
            val seat = if (wearsHelm) hy - 13f else hy - 4f
            val crownPath = Path().apply {
                moveTo(hx - 12f, seat)
                lineTo(hx - 15f, seat - 15f)
                lineTo(hx - 5f, seat - 5f)
                lineTo(hx, seat - 20f)
                lineTo(hx + 5f, seat - 5f)
                lineTo(hx + 15f, seat - 15f)
                lineTo(hx + 12f, seat)
                close()
            }
            drawStitchedFill(scope, crownPath, Color(0xFFFFD700)) // Gold
            scope.drawStitchedOutline(crownPath, ThreadColor)
            // Jewels
            scope.drawCircle(Color(0xFFBF3030), radius = 2f, center = Offset(hx - 10f, seat - 5f))
            scope.drawCircle(Color(0xFF305ABF), radius = 2f, center = Offset(hx, seat - 10f))
            scope.drawCircle(Color(0xFF30BF5A), radius = 2f, center = Offset(hx + 10f, seat - 5f))
        }
        } // Close withTransform
    }

    /**
     * What a missile weapon accumulates as it is upgraded: gilt binding at the tips, etched
     * chevrons down the limb, a masterwork cord wrap at the grip, barbed spurs, a volley pennon
     * and extra strings for multishot.
     *
     * Reads rangedUpgrades directly, which nothing in the renderer did before — a fully upgraded
     * bow was drawn exactly like a starting one.
     */
    private fun drawRangedEvolution(scope: DrawScope, fighter: FighterState, headPos: Offset) {
        val ups = fighter.rangedUpgrades
        if (ups.isEmpty() || !fighter.weaponHead.isRanged) return
        val isSling = fighter.weaponHead.id == "head_slingshot"
        val long = fighter.weaponHead.id == "head_longbow"
        val span = if (isSling) 12f else if (long) 36f else 30f
        val gilt = Color(0xFFC9A227)
        val cord = Color(0xFF8E2B2B)

        // Gilt binding at the limb tips: one band per upgrade taken, up to three.
        val bands = ups.size.coerceAtMost(3)
        for (b in 0 until bands) {
            val off = span - b * 6f
            scope.drawLine(gilt, Offset(headPos.x - 13f, headPos.y - off),
                Offset(headPos.x - 7f, headPos.y - off), strokeWidth = 2f)
            scope.drawLine(gilt, Offset(headPos.x - 13f, headPos.y + off),
                Offset(headPos.x - 7f, headPos.y + off), strokeWidth = 2f)
        }
        // Etched chevrons down the belly of the limb — the engraver's work.
        if (ups.size >= 2) {
            for (c in -1..1) {
                val y = headPos.y + c * 11f
                scope.drawLine(ThreadColor.copy(alpha = 0.65f), Offset(headPos.x - 4f, y),
                    Offset(headPos.x + 2f, y - 3f), strokeWidth = 1.2f)
                scope.drawLine(ThreadColor.copy(alpha = 0.65f), Offset(headPos.x - 4f, y),
                    Offset(headPos.x + 2f, y + 3f), strokeWidth = 1.2f)
            }
        }
        // Masterwork: a red cord wrap at the grip.
        if (ups.size >= 3) {
            for (w in 0..2) {
                scope.drawLine(cord, Offset(headPos.x - 15f, headPos.y - 4f + w * 4f),
                    Offset(headPos.x - 9f, headPos.y - 4f + w * 4f), strokeWidth = 2.2f)
            }
        }
        // Barbs: iron spurs stand off the limb.
        if (ups.any { it.endsWith("_spikes") }) {
            listOf(-1f, 1f).forEach { sgn ->
                scope.drawLine(Color(0xFF9EA3A8),
                    Offset(headPos.x - 10f, headPos.y + sgn * span * 0.55f),
                    Offset(headPos.x - 18f, headPos.y + sgn * span * 0.75f), strokeWidth = 2f)
            }
        }
        // Volley: a small pennon on the upper limb, for judging the wind.
        if (ups.contains("volley")) {
            val pennon = Path().apply {
                moveTo(headPos.x - 12f, headPos.y - span)
                lineTo(headPos.x - 26f, headPos.y - span - 5f)
                lineTo(headPos.x - 12f, headPos.y - span + 7f)
                close()
            }
            drawStitchedFill(scope, pennon, Color(0xFFB03131))
            scope.drawStitchedOutline(pennon, ThreadColor)
        }
        // Multishot: a second and third string, plainly visible.
        val strings = if (ups.contains("multishot_triple")) 2 else if (ups.contains("multishot_double")) 1 else 0
        for (n in 1..strings) {
            scope.drawLine(Color(0xFFE4D6B6),
                Offset(headPos.x - 12f - n * 3f, headPos.y - span),
                Offset(headPos.x - 12f - n * 3f, headPos.y + span), strokeWidth = 1.2f)
        }
        // Cluster charges ride on the belt as little clay pots.
        if (ups.contains("cluster")) {
            for (k in 0..1) {
                scope.drawCircle(Color(0xFF7A5230), radius = 3f,
                    center = Offset(headPos.x - 20f, headPos.y + 6f + k * 8f))
                scope.drawCircle(ThreadColor, radius = 3f,
                    center = Offset(headPos.x - 20f, headPos.y + 6f + k * 8f),
                    style = Stroke(width = 1f))
            }
        }
    }

    private fun drawWeaponHead(
        scope: DrawScope,
        headId: String,
        headColor: Color,
        headPos: Offset,
        animFrame: Float,
        swingProgress: Float,
        isAttacking: Boolean
    ) {
        when (headId) {
            "head_bare" -> {
                // skip
            }
            "head_eel" -> {
                // Smoked eel: a long grey-green body that wobbles like the dead fish it is,
                // whipping harder mid-swing.
                val wob = sin(animFrame * 6f) * 4f + (if (isAttacking) swingProgress * 10f else 0f)
                val eel = Path().apply {
                    // First point sits on the weld anchor so the tail visibly grips the weapon
                    moveTo(headPos.x - 2f, headPos.y + 2f)
                    quadraticTo(headPos.x + 14f, headPos.y - 10f - wob, headPos.x + 30f, headPos.y - 4f)
                    quadraticTo(headPos.x + 44f, headPos.y + 2f + wob, headPos.x + 56f, headPos.y - 8f)
                    quadraticTo(headPos.x + 44f, headPos.y + 8f + wob, headPos.x + 28f, headPos.y + 3f)
                    quadraticTo(headPos.x + 12f, headPos.y - 3f - wob, headPos.x - 6f, headPos.y + 9f)
                    close()
                }
                drawStitchedFill(scope, eel, headColor)
                scope.drawPath(eel, ThreadColor, style = Stroke(width = 2f))
                // Tail fin and a reproachful eye
                scope.drawLine(ThreadColor, Offset(headPos.x + 54f, headPos.y - 12f), Offset(headPos.x + 60f, headPos.y - 4f), strokeWidth = 2f)
                scope.drawCircle(Color.White, radius = 2.5f, center = Offset(headPos.x, headPos.y + 5f))
                scope.drawCircle(ThreadColor, radius = 1.2f, center = Offset(headPos.x, headPos.y + 5f))
            }
            "head_femur" -> {
                // The holy thighbone: shaft with knobbed ends, and a faint sanctity halo
                // that pulses while swung.
                scope.drawLine(headColor, Offset(headPos.x, headPos.y), Offset(headPos.x + 34f, headPos.y - 14f), strokeWidth = 7f, cap = StrokeCap.Round)
                scope.drawLine(ThreadColor, Offset(headPos.x, headPos.y), Offset(headPos.x + 34f, headPos.y - 14f), strokeWidth = 2f)
                listOf(Offset(headPos.x + 34f, headPos.y - 14f), Offset(headPos.x, headPos.y)).forEach { end ->
                    scope.drawCircle(headColor, radius = 6f, center = Offset(end.x - 2f, end.y - 3f))
                    scope.drawCircle(headColor, radius = 6f, center = Offset(end.x + 3f, end.y + 3f))
                    scope.drawCircle(ThreadColor, radius = 6f, center = Offset(end.x - 2f, end.y - 3f), style = Stroke(width = 1.5f))
                    scope.drawCircle(ThreadColor, radius = 6f, center = Offset(end.x + 3f, end.y + 3f), style = Stroke(width = 1.5f))
                }
                val holy = if (isAttacking) 0.5f + 0.5f * sin(swingProgress * 6.28f) else 0.35f + 0.15f * sin(animFrame * 2f)
                scope.drawCircle(Color(0xFFFFE9A8).copy(alpha = holy * 0.5f), radius = 22f, center = Offset(headPos.x + 17f, headPos.y - 7f))
            }
            "head_goose" -> {
                // The irate goose, held by the legs. Neck cranes, wings flap continuously —
                // frantically when swung. It has never once calmed down.
                val flap = sin(animFrame * (if (isAttacking) 14f else 5f)) * 10f
                // Body
                val body = Path().apply {
                    moveTo(headPos.x + 2f, headPos.y)
                    quadraticTo(headPos.x + 16f, headPos.y - 14f, headPos.x + 32f, headPos.y - 6f)
                    quadraticTo(headPos.x + 30f, headPos.y + 8f, headPos.x + 10f, headPos.y + 8f)
                    close()
                }
                drawStitchedFill(scope, body, headColor)
                scope.drawPath(body, ThreadColor, style = Stroke(width = 2f))
                // Flapping wing
                val wing = Path().apply {
                    moveTo(headPos.x + 16f, headPos.y - 6f)
                    quadraticTo(headPos.x + 22f, headPos.y - 18f - flap, headPos.x + 34f, headPos.y - 16f - flap)
                    quadraticTo(headPos.x + 26f, headPos.y - 6f, headPos.x + 16f, headPos.y - 6f)
                    close()
                }
                drawStitchedFill(scope, wing, Color(0xFFD8D2C2))
                scope.drawPath(wing, ThreadColor, style = Stroke(width = 1.5f))
                // Craning neck + hissing head
                val craneY = if (isAttacking) -10f * swingProgress else sin(animFrame * 2f) * 3f
                scope.drawLine(headColor, Offset(headPos.x + 30f, headPos.y - 8f), Offset(headPos.x + 46f, headPos.y - 20f + craneY), strokeWidth = 5f, cap = StrokeCap.Round)
                scope.drawCircle(headColor, radius = 5f, center = Offset(headPos.x + 48f, headPos.y - 22f + craneY))
                scope.drawCircle(ThreadColor, radius = 5f, center = Offset(headPos.x + 48f, headPos.y - 22f + craneY), style = Stroke(width = 1.5f))
                // Orange beak, open mid-hiss when attacking
                val beakGap = if (isAttacking) 3f else 1f
                scope.drawLine(Color(0xFFE07020), Offset(headPos.x + 52f, headPos.y - 23f + craneY), Offset(headPos.x + 60f, headPos.y - 22f - beakGap + craneY), strokeWidth = 2.5f)
                scope.drawLine(Color(0xFFE07020), Offset(headPos.x + 52f, headPos.y - 21f + craneY), Offset(headPos.x + 60f, headPos.y - 20f + beakGap + craneY), strokeWidth = 2.5f)
                scope.drawCircle(ThreadColor, radius = 1.2f, center = Offset(headPos.x + 47f, headPos.y - 24f + craneY))
            }
            "head_cheese" -> {
                // The great wheel, rind-banded, with a wedge cut showing the paste. It rolls
                // (rotates) through the swing like a millstone.
                // Pivot ON the weld point — a +16px centre left the wheel hovering off the weapon
                val roll = if (isAttacking) swingProgress * 180f else animFrame * 8f
                scope.withTransform({ rotate(roll, pivot = Offset(headPos.x, headPos.y)) }) {
                    val c = Offset(headPos.x + 2f, headPos.y)
                    scope.drawCircle(headColor, radius = 18f, center = c)
                    scope.drawCircle(ThreadColor, radius = 18f, center = c, style = StitchedStroke)
                    scope.drawCircle(Color(0xFFB98F2E), radius = 18f, center = c, style = Stroke(width = 4f))
                    // The cut wedge
                    val wedge = Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + 18f, c.y - 4f)
                        lineTo(c.x + 14f, c.y - 12f)
                        close()
                    }
                    scope.drawPath(wedge, Color(0xFFF2E3B0))
                    scope.drawPath(wedge, ThreadColor, style = Stroke(width = 1.5f))
                    // Eyes (the cheese kind)
                    scope.drawCircle(Color(0xFFB98F2E), radius = 2f, center = Offset(c.x - 6f, c.y + 4f))
                    scope.drawCircle(Color(0xFFB98F2E), radius = 1.5f, center = Offset(c.x + 2f, c.y + 9f))
                    scope.drawCircle(Color(0xFFB98F2E), radius = 1.5f, center = Offset(c.x - 9f, c.y - 5f))
                }
            }
            "head_spade" -> {
                // The sapper's tool. Drawn on the haft basis like the saw and the pitchfork, so
                // the blade sits in line with the pole rather than at its own angle: a squared
                // iron-shod blade with a shoulder where the socket takes the shaft, and a bright
                // worn edge across the end.
                val dx = 0.894f; val dy = -0.447f
                val px = -dy; val py = dx
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along + px * across,
                    headPos.y + dy * along + py * across
                )
                val socket = Path().apply {
                    moveTo(at(-4f, -5f).x, at(-4f, -5f).y)
                    lineTo(at(8f, -7f).x, at(8f, -7f).y)
                    lineTo(at(8f, 7f).x, at(8f, 7f).y)
                    lineTo(at(-4f, 5f).x, at(-4f, 5f).y)
                    close()
                }
                drawStitchedFill(scope, socket, Color(0xFF5E5750))
                scope.drawStitchedOutline(socket, ThreadColor)
                val blade = Path().apply {
                    moveTo(at(8f, -12f).x, at(8f, -12f).y)
                    lineTo(at(30f, -13f).x, at(30f, -13f).y)
                    // The cutting end, very slightly bellied — a spade is not a rectangle
                    quadraticTo(at(34f, 0f).x, at(34f, 0f).y, at(30f, 13f).x, at(30f, 13f).y)
                    lineTo(at(8f, 12f).x, at(8f, 12f).y)
                    close()
                }
                drawStitchedFill(scope, blade, headColor)
                scope.drawStitchedOutline(blade, ThreadColor)
                // Worn bright along the edge that does the digging
                scope.drawLine(Color(0xFFD8D2C6), at(30f, -12f), at(30f, 12f), strokeWidth = 3f)
            }
            "head_giant_tooth" -> {
                // A molar the size of a loaf, wired to the haft. Two blunt roots gripping the
                // shaft, a bulging crown, and the brown of something that was never brushed.
                val dx = 0.894f; val dy = -0.447f
                val px = -dy; val py = dx
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along + px * across,
                    headPos.y + dy * along + py * across
                )
                listOf(-7f, 7f).forEach { side ->
                    scope.drawLine(Color(0xFFCFC5A8), at(0f, side * 0.6f), at(11f, side), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                val crown = Path().apply {
                    moveTo(at(10f, -13f).x, at(10f, -13f).y)
                    quadraticTo(at(24f, -19f).x, at(24f, -19f).y, at(33f, -8f).x, at(33f, -8f).y)
                    quadraticTo(at(37f, 0f).x, at(37f, 0f).y, at(33f, 8f).x, at(33f, 8f).y)
                    quadraticTo(at(24f, 19f).x, at(24f, 19f).y, at(10f, 13f).x, at(10f, 13f).y)
                    close()
                }
                drawStitchedFill(scope, crown, headColor)
                scope.drawStitchedOutline(crown, ThreadColor)
                // Stain in the fissures, and the wire that holds it on
                scope.drawLine(Color(0xFF9B8A63), at(16f, -6f), at(29f, -2f), strokeWidth = 2f)
                scope.drawLine(Color(0xFF9B8A63), at(16f, 6f), at(29f, 2f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, at(4f, -8f), at(4f, 8f), strokeWidth = 2f)
            }
            "head_javelin" -> {
                // Light throwing spear held in hand (was missing — javelineers looked empty-handed)
                scope.drawLine(Color(0xFF6E5536), Offset(headPos.x - 40.5f, headPos.y + 23f), Offset(headPos.x + 21.5f, headPos.y - 9.5f), strokeWidth = 5f, cap = StrokeCap.Round)
                val tip = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x + 19.5f, headPos.y - 12f)
                    lineTo(headPos.x + 46f, headPos.y - 24f)
                    lineTo(headPos.x + 23f, headPos.y - 7f)
                    close()
                }
                scope.drawPath(tip, headColor)
                scope.drawStitchedOutline(tip, ThreadColor)
            }
            "head_spear" -> {
                // Broad leaf point — deliberately distinct from the pike's needle
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y)
                    lineTo(headPos.x + 18f, headPos.y - 12f)
                    lineTo(headPos.x + 35f, headPos.y - 15f) // point
                    lineTo(headPos.x + 22f, headPos.y - 3f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
            }
            "head_pike" -> {
                // Long slender needle on a collar — half again the spear's length, a fraction
                // of its width. Reads as "keep away", not "leaf on a stick".
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y - 1f)
                    lineTo(headPos.x + 12f, headPos.y - 8f)
                    lineTo(headPos.x + 58f, headPos.y - 21f) // needle point, far out
                    lineTo(headPos.x + 13f, headPos.y - 1f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
                // Iron collar where needle meets haft
                scope.drawLine(
                    headColor,
                    androidx.compose.ui.geometry.Offset(headPos.x + 2f, headPos.y + 4f),
                    androidx.compose.ui.geometry.Offset(headPos.x + 8f, headPos.y - 10f),
                    strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            "head_axe" -> {
                // A bearded Dane axe: a socket gripping the haft, a narrow neck, then a blade
                // that flares to a broad crescent edge with the beard hanging below the socket.
                // It was one lopsided quadratic blob with no socket and no neck, so it read as a
                // lump on a stick — the crescent edge and the gap behind it are the whole shape.
                val dx = 0.894f; val dy = -0.447f      // along the haft
                val px = -dy; val py = dx              // across it
                // Rotated a quarter turn from the thrusting heads: an axe blade stands out
                // SIDEWAYS from its haft, it does not point along it like a spear. Built the
                // other way up first, which put the edge where the spear's point goes.
                fun at(along: Float, across: Float): Offset {
                    val u = across            // the blade's length now runs across the haft
                    val v = along             // and its width along it
                    // +along, not -along: negated, the crescent stood out on the BACK of the
                    // haft, so the edge pointed at the wearer's own legs in both facings (the
                    // whole fighter is mirrored by scale(hFlip), so a backwards head stays
                    // backwards whichever way he turns).
                    return Offset(headPos.x + dx * u + px * v, headPos.y + dy * u + py * v)
                }
                val blade = androidx.compose.ui.graphics.Path().apply {
                    moveTo(at(-4f, -7f).x, at(-4f, -7f).y)      // socket, top of the haft
                    lineTo(at(9f, -7f).x, at(9f, -7f).y)        // narrow neck, only slightly proud
                    // Top of the blade: a modest flare, NOT a fan — the axe is asymmetric, and
                    // flaring both ways equally is what made it read as a spade.
                    quadraticTo(at(22f, -13f).x, at(22f, -13f).y, at(27f, -6f).x, at(27f, -6f).y)
                    // The cutting edge: a long crescent sweeping down and back to the beard's toe
                    quadraticTo(at(31f, 12f).x, at(31f, 12f).y, at(19f, 27f).x, at(19f, 27f).y)
                    // The beard proper — it hooks back UNDER the haft, which is the silhouette
                    // detail that names the weapon.
                    quadraticTo(at(9f, 22f).x, at(9f, 22f).y, at(1f, 12f).x, at(1f, 12f).y)
                    lineTo(at(-4f, 7f).x, at(-4f, 7f).y)        // socket, bottom of the haft
                    close()
                }
                scope.drawPath(blade, headColor)
                scope.drawStitchedOutline(blade, ThreadColor)
                // Langets: the iron straps binding the socket to the haft.
                scope.drawLine(ThreadColor, at(-4f, -7f), at(-4f, 7f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, at(4f, -8f), at(4f, 11f), strokeWidth = 1.5f)
            }
            "head_sword", "head_claymore", "head_dagger" -> {
                val isClaymore = headId == "head_claymore"
                val isDagger = headId == "head_dagger"
                val bladeLen = if (isClaymore) 80f else if (isDagger) 25f else 50f
                val bladeEnd = androidx.compose.ui.geometry.Offset(headPos.x + bladeLen * 0.8f, headPos.y - bladeLen * 0.4f)
                scope.drawLine(
                    color = headColor,
                    start = headPos,
                    end = bladeEnd,
                    strokeWidth = if (isClaymore) 8f else 5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // crossguard
                scope.drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFFCFB53B),
                    start = androidx.compose.ui.geometry.Offset(headPos.x - 6f, headPos.y - 12f),
                    end = androidx.compose.ui.geometry.Offset(headPos.x + 6f, headPos.y + 12f),
                    strokeWidth = 4f
                )
                // line down middle
                scope.drawLine(
                    color = ThreadColor,
                    start = headPos,
                    end = bladeEnd,
                    strokeWidth = 1f
                )
            }
            "head_morningstar" -> {
                scope.drawCircle(headColor, radius = 12f, center = headPos)
                scope.drawCircle(ThreadColor, radius = 12f, center = headPos, style = StitchedStroke)
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4
                    val sp = androidx.compose.ui.geometry.Offset(headPos.x + kotlin.math.cos(angle).toFloat() * 19f, headPos.y + kotlin.math.sin(angle).toFloat() * 19f)
                    scope.drawLine(ThreadColor, headPos, sp, strokeWidth = 3f)
                }
            }
            "head_mace" -> {
                // Along the haft, not along the screen axis — same defect as the broadsword and
                // the saw. The whole head, flanges included, was built flat, so it sat across the
                // shaft it is socketed onto instead of along it.
                val dx = 0.894f; val dy = -0.447f      // unit vector of (0.8, -0.4)
                val px = -dy; val py = dx              // perpendicular, across the head
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along + px * across,
                    headPos.y + dy * along + py * across
                )
                val macePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(at(-5f, -5f).x, at(-5f, -5f).y)
                    lineTo(at(15f, -12f).x, at(15f, -12f).y)
                    lineTo(at(20f, -5f).x, at(20f, -5f).y)
                    lineTo(at(20f, 5f).x, at(20f, 5f).y)
                    lineTo(at(15f, 12f).x, at(15f, 12f).y)
                    lineTo(at(-5f, 5f).x, at(-5f, 5f).y)
                    close()
                }
                scope.drawPath(macePath, headColor)
                scope.drawStitchedOutline(macePath, ThreadColor)
                // Flanges
                scope.drawLine(ThreadColor, at(0f, -8f), at(18f, -2f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, at(0f, 8f), at(18f, 2f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, at(-2f, 0f), at(20f, 0f), strokeWidth = 2.5f)
            }
            "head_broadsword" -> {
                // Along the haft, not along the screen axis — same defect the pitchfork had.
                // The blade ran to (+40,-15), about 21 degrees, while the grip it is socketed
                // into runs at 27, so the blade kinked visibly downward at the crossguard. The
                // crossguard itself was already square to the haft, which is what made the
                // mismatch read as a bent sword rather than a tilted one.
                val dx = 0.894f; val dy = -0.447f      // unit vector of (0.8, -0.4)
                val px = -dy; val py = dx              // perpendicular, across the blade
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along + px * across,
                    headPos.y + dy * along + py * across
                )
                val bladeLen = 43f                     // was |(40,-15)| = 42.7, so reach is unchanged
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(at(0f, -6f).x, at(0f, -6f).y)
                    lineTo(at(bladeLen, -6f).x, at(bladeLen, -6f).y)
                    lineTo(at(bladeLen + 10f, 0f).x, at(bladeLen + 10f, 0f).y) // tip
                    lineTo(at(bladeLen, 6f).x, at(bladeLen, 6f).y)
                    lineTo(at(0f, 6f).x, at(0f, 6f).y)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
                // Fullers
                scope.drawLine(ThreadColor, headPos, at(bladeLen, 0f), strokeWidth = 1.5f)

                // Crossguard, square across the blade
                scope.drawLine(Color(0xFFD6A420), at(0f, -15f), at(0f, 15f), strokeWidth = 5f)
            }
            "head_dagger_hilt" -> {
                // Large iron pommel strike
                scope.drawCircle(headColor, radius = 9f, center = Offset(headPos.x + 5f, headPos.y - 2f))
                scope.drawCircle(ThreadColor, radius = 9f, center = Offset(headPos.x + 5f, headPos.y - 2f), style = StitchedStroke)
                // Small crossguard
                scope.drawLine(Color(0xFFCFB53B), Offset(headPos.x, headPos.y - 8f), Offset(headPos.x, headPos.y + 8f), strokeWidth = 4f)
            }
            "head_pitchfork" -> {
                // Along the haft, not along the screen axis.
                //
                // The shaft runs to (hx + len*0.8, hy - len*0.4), i.e. about 27 degrees above
                // horizontal, and every other thrusting head is drawn tilted to match — the
                // spear's point goes (0,0) to (+35,-15). The pitchfork was the one left flat, so
                // its tines pointed 27 degrees off the pole they are lashed to, at every size
                // and on every fighter. Built from the shaft's own direction here rather than
                // hand-tilted, so it cannot drift out of agreement with it again.
                val dx = 0.894f; val dy = -0.447f      // unit vector of (0.8, -0.4)
                val px = -dy; val py = dx              // perpendicular, for the head bar
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along + px * across,
                    headPos.y + dy * along + py * across
                )
                // Head bar, square across the haft
                scope.drawLine(headColor, at(2f, -10f), at(2f, 10f), strokeWidth = 4f)
                // Three tines, running straight out along the haft's line
                val tineLen = 26f
                scope.drawLine(headColor, at(2f, -10f), at(2f + tineLen, -11f), strokeWidth = 3f)
                scope.drawLine(headColor, at(2f, 0f), at(2f + tineLen + 5f, 0f), strokeWidth = 3f)
                scope.drawLine(headColor, at(2f, 10f), at(2f + tineLen, 11f), strokeWidth = 3f)
            }
            "head_maul" -> {
                scope.drawRect(
                    color = headColor,
                    topLeft = androidx.compose.ui.geometry.Offset(headPos.x - 8f, headPos.y - 18f),
                    size = androidx.compose.ui.geometry.Size(25f, 35f)
                )
                scope.drawRect(
                    color = ThreadColor,
                    topLeft = androidx.compose.ui.geometry.Offset(headPos.x - 8f, headPos.y - 18f),
                    size = androidx.compose.ui.geometry.Size(25f, 35f),
                    style = StitchedStroke
                )
            }
            "head_bow" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x - 12f, headPos.y - 30f)
                    quadraticTo(headPos.x + 18f, headPos.y, headPos.x - 12f, headPos.y + 30f)
                }
                scope.drawPath(path, headColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                // Bowstring
                scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x - 12f, headPos.y - 30f), androidx.compose.ui.geometry.Offset(headPos.x - 12f, headPos.y + 30f), strokeWidth = 1.5f)
            }
            "head_slingshot" -> {
                // draw Y shape
                scope.drawLine(Color(0xFF8A5E38), headPos, androidx.compose.ui.geometry.Offset(headPos.x + 9.5f, headPos.y - 10.5f), strokeWidth = 4f)
                scope.drawLine(Color(0xFF8A5E38), headPos, androidx.compose.ui.geometry.Offset(headPos.x + 1f, headPos.y - 13.5f), strokeWidth = 4f)
                scope.drawCircle(ThreadColor, radius = 1f, center = headPos, style = Stroke(width = 2f))
                val square_1 = Path().apply {
                    moveTo(headPos.x - 1f, headPos.y - 2f)
                    lineTo(headPos.x + 2.5f, headPos.y - 1f)
                    lineTo(headPos.x - 3f, headPos.y + 10.5f)
                    lineTo(headPos.x - 6.5f, headPos.y + 9f)
                    close()
                }
                drawStitchedFill(scope, square_1, Color(0xFF8A5E38))
                // Outlined in its own fill, unlike every other head: the pouch strap is only a
                // few units wide, so a thread outline swallows it whole and reads as a black
                // smear. It needs no silhouette now the head stands clear of the sleeve.
                scope.drawStitchedOutline(square_1, Color(0xFF8A5E38))
}
            "head_longbow" -> {
                // Same stave length as before (114 units), hung 12 lower: it ran -69/+45 about the
                // grip, so the archer held it well down the lower limb and the bow read as riding
                // up out of his hands.
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x - 14f, headPos.y - 57f)
                    quadraticTo(headPos.x + 25f, headPos.y + 12f, headPos.x - 18f, headPos.y + 57f)
                }
                scope.drawPath(path, headColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x - 15f, headPos.y - 56.5f), androidx.compose.ui.geometry.Offset(headPos.x - 18.5f, headPos.y + 57.5f), strokeWidth = 2f)
            }
            "head_flail", "head_war_flail" -> {
                val isTwin = headId == "head_war_flail"
                val numBalls = if (isTwin) 2 else 1
                
                for (b in 0 until numBalls) {
                    val swing = swingProgress
                    val phaseOffset = if (b == 1) Math.PI.toFloat() * 0.4f else 0f
                    val chainAngle: Float = when {
                        isAttacking && swing < 0.2f ->
                            (Math.PI * 0.3 - swing * Math.PI).toFloat() + phaseOffset // windup goes up (counter-clockwise)
                        isAttacking ->
                            (Math.PI * 0.1 + ((swing - 0.2f) / 0.8f) * Math.PI * 2.2).toFloat() + phaseOffset // huge clockwise smash
                        else ->
                            (Math.PI * 0.25 + kotlin.math.sin(animFrame * 0.8f + phaseOffset) * 0.15f).toFloat()
                    }
                    val chainLength = if (b == 1) 28f else 38f
                    val ballPos = Offset(
                        headPos.x + cos(chainAngle) * chainLength,
                        headPos.y + sin(chainAngle) * chainLength
                    )
                    scope.drawLine(Color(0xFF636A6E), headPos, ballPos, strokeWidth = 4f, cap = StrokeCap.Round)
                    scope.drawLine(ThreadColor, headPos, ballPos, strokeWidth = 1.5f, cap = StrokeCap.Round)
                    scope.drawCircle(headColor, radius = 11f, center = ballPos)
                    scope.drawCircle(ThreadColor, radius = 11f, center = ballPos, style = StitchedStroke)
                    for (i in 0 until 6) {
                        val spikeAngle = chainAngle + i * (Math.PI * 2.0 / 6.0).toFloat()
                        val sp = Offset(
                            ballPos.x + cos(spikeAngle) * 17f,
                            ballPos.y + sin(spikeAngle) * 17f
                        )
                        scope.drawLine(ThreadColor, ballPos, sp, strokeWidth = 2.5f, cap = StrokeCap.Round)
                    }
                }
            }

            "head_scythe" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y)
                    quadraticTo(headPos.x + 20f, headPos.y - 36f, headPos.x + 45f, headPos.y - 5f) // outer curve
                    quadraticTo(headPos.x + 15f, headPos.y - 23f, headPos.x, headPos.y + 5f) // inner curve
                    close()
                }
                scope.drawPath(path, headColor)
            }
            "head_crossbow" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x - 10f, headPos.y + 10f)
                    lineTo(headPos.x + 30f, headPos.y - 20f)
                }
                scope.drawPath(path, androidx.compose.ui.graphics.Color(0xFF6E5536), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                val square_2 = Path().apply {
                    moveTo(headPos.x + 19f, headPos.y - 10.5f)
                    lineTo(headPos.x + 28.5f, headPos.y - 21.5f)
                    lineTo(headPos.x + 32f, headPos.y - 18.5f)
                    lineTo(headPos.x + 22f, headPos.y - 8.5f)
                    close()
                }
                drawStitchedFill(scope, square_2, Color(0xFF6B7882))
                scope.drawPath(square_2, Color(0xFF2C2219), style = Stroke(width = 0.5f))
                val square_3 = Path().apply {
                    moveTo(headPos.x + 1.5f, headPos.y - 7f)
                    lineTo(headPos.x + 17.5f, headPos.y - 18f)
                    lineTo(headPos.x + 19.5f, headPos.y - 15f)
                    lineTo(headPos.x + 3.5f, headPos.y - 4f)
                    moveTo(headPos.x + 17.5f, headPos.y - 18.5f)
                    close()
                }
                drawStitchedFill(scope, square_3, Color(0xFF8A5E38))
                scope.drawPath(square_3, Color(0xFF2C2219), style = Stroke(width = 0.5f))
                val square_4 = Path().apply {
                    moveTo(headPos.x + 17.5f, headPos.y - 18.5f)
                    lineTo(headPos.x + 25.5f, headPos.y - 20.5f)
                    lineTo(headPos.x + 20.5f, headPos.y - 15f)
                    lineTo(headPos.x + 19.5f, headPos.y - 16f)
                    moveTo(headPos.x + 1.5f, headPos.y - 7f)
                    moveTo(headPos.x + 3.5f, headPos.y - 4f)
                    lineTo(headPos.x + 1f, headPos.y - 7f)
                    moveTo(headPos.x + 9f, headPos.y - 0.5f)
                    lineTo(headPos.x + 6f, headPos.y + 5.5f)
                    lineTo(headPos.x + 3f, headPos.y + 4f)
                    close()
                }
                drawStitchedFill(scope, square_4, Color(0xFFBAC5CC))
                scope.drawPath(square_4, Color(0xFF2C2219), style = Stroke(width = 0.5f))
}
            "head_halberd" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y)
                    lineTo(headPos.x + 30f, headPos.y - 15f) // top spike
                    lineTo(headPos.x + 15f, headPos.y - 5f)
                    lineTo(headPos.x + 15f, headPos.y + 15f) // axe blade bottom
                    lineTo(headPos.x - 5f, headPos.y + 5f) // back hook
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
            }

            "head_lucerne" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y)
                    lineTo(headPos.x + 35f, headPos.y - 10f) // long piercing beak
                    lineTo(headPos.x + 8f, headPos.y + 6f)
                    lineTo(headPos.x + 12f, headPos.y + 16f) // blunt hammer back
                    lineTo(headPos.x - 5f, headPos.y + 12f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
                // Small spikes on hammer back
                scope.drawLine(ThreadColor, androidx.compose.ui.geometry.Offset(headPos.x + 8f, headPos.y + 16f), androidx.compose.ui.geometry.Offset(headPos.x + 8f, headPos.y + 22f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, androidx.compose.ui.geometry.Offset(headPos.x + 12f, headPos.y + 13f), androidx.compose.ui.geometry.Offset(headPos.x + 16f, headPos.y + 18f), strokeWidth = 2f)
            }
            "head_saber" -> {
                // Curves UP: belly below, tip rising — mirrored it read as a mining pick
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y + 10f)
                    quadraticTo(headPos.x + 60f, headPos.y + 20f, headPos.x + 80f, headPos.y - 10f) // swept belly, tip sweeping upward
                    quadraticTo(headPos.x + 50f, headPos.y, headPos.x + 5f, headPos.y - 5f) // inner edge
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
                // Crossguard
                scope.drawLine(androidx.compose.ui.graphics.Color(0xFFCFB53B), androidx.compose.ui.geometry.Offset(headPos.x + 4f, headPos.y - 11.5f), androidx.compose.ui.geometry.Offset(headPos.x - 4.5f, headPos.y + 15f), strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            "head_urumi" -> {
                val numBlades = 4
                for (b in 0 until numBlades) {
                    val phase = b * 0.7f + animFrame * 2f + swingProgress * 15f
                    val wavePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(headPos.x, headPos.y - 6f + b * 4f)
                        val endX = headPos.x + 55f + kotlin.math.cos(phase) * 15f
                        val midX = headPos.x + 30f
                        val midY = headPos.y + kotlin.math.sin(phase) * 25f
                        quadraticTo(midX, midY, endX, headPos.y - 6f + b * 4f + kotlin.math.sin(phase + 1f) * 20f)
                    }
                    scope.drawPath(wavePath, headColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
            "head_winged_axe" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y - 8f)
                    lineTo(headPos.x + 50f, headPos.y - 5.5f) // top wing spike
                    quadraticTo(headPos.x + 35f, headPos.y - 5f, headPos.x + 22f, headPos.y + 25f) // axe face
                    quadraticTo(headPos.x + 10f, headPos.y + 15f, headPos.x, headPos.y + 15f) 
                    // back wing spike
                    lineTo(headPos.x - 12f, headPos.y + 5f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
            }
            "head_spiked_mace" -> {
                scope.drawCircle(headColor, radius = 14f, center = headPos)
                scope.drawCircle(ThreadColor, radius = 14f, center = headPos, style = StitchedStroke)
                for (i in 0 until 12) {
                    val angle = i * Math.PI / 6
                    val sp = Offset(headPos.x + cos(angle).toFloat() * 22f, headPos.y + sin(angle).toFloat() * 22f)
                    scope.drawLine(headColor, headPos, sp, strokeWidth = 3f, cap = StrokeCap.Round)
                    scope.drawLine(ThreadColor, headPos, sp, strokeWidth = 1f, cap = StrokeCap.Round)
                }
            }
            "head_club" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    // Start way back down the handle
                    val startX = headPos.x - 45f * 0.894f
                    val startY = headPos.y + 45f * 0.447f
                    moveTo(startX, startY)
                    
                    // Left edge extending out (slimmed profile — full width looked ungainly)
                    quadraticTo(headPos.x - 10f, headPos.y - 9f, headPos.x + 30f, headPos.y - 21f)
                    // Bumpy top
                    lineTo(headPos.x + 45f, headPos.y - 23f)
                    quadraticTo(headPos.x + 65f, headPos.y - 12f, headPos.x + 55f, headPos.y + 6f)
                    lineTo(headPos.x + 35f, headPos.y + 15f)
                    // Right edge tapering back
                    quadraticTo(headPos.x, headPos.y + 6f, startX, startY)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
                // Knots and bumps
                scope.drawCircle(Color(0xFF3E2723), radius = 3.5f, center = Offset(headPos.x + 25f, headPos.y - 10f))
                scope.drawCircle(Color(0xFF3E2723), radius = 4.5f, center = Offset(headPos.x + 40f, headPos.y + 5f))
                scope.drawCircle(Color(0xFF3E2723), radius = 2.5f, center = Offset(headPos.x + 15f, headPos.y))
                scope.drawCircle(Color(0xFF3E2723), radius = 3.5f, center = Offset(headPos.x + 45f, headPos.y - 20f))
            }
            "head_bomb" -> {
                // A round clay pot held ready, with a cord smouldering out of the neck. Deliberately
                // small: it is the burst that is big, not the thing in your hand.
                val pot = Offset(headPos.x, headPos.y)
                scope.drawCircle(Color(0xFF4A4238), radius = 13f, center = pot)
                scope.drawCircle(ThreadColor, radius = 13f, center = pot, style = StitchedStroke)
                // A band around the belly, so it reads as fired clay rather than a cannonball
                scope.drawLine(
                    Color(0xFF6E5536), Offset(pot.x - 11f, pot.y + 2f), Offset(pot.x + 11f, pot.y + 2f),
                    strokeWidth = 2.5f
                )
                // Neck and fuse, curling up and back
                scope.drawRect(
                    Color(0xFF6E5536),
                    topLeft = Offset(pot.x - 4f, pot.y - 18f),
                    size = androidx.compose.ui.geometry.Size(8f, 7f)
                )
                val fuse = Path().apply {
                    moveTo(pot.x, pot.y - 17f)
                    quadraticTo(pot.x + 12f, pot.y - 26f, pot.x + 4f, pot.y - 34f)
                }
                scope.drawPath(fuse, Color(0xFF8C6F47), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                // The spark on the end. This is the whole tell that it is lit.
                scope.drawCircle(Color(0xFFE8A33A), radius = 4f, center = Offset(pot.x + 4f, pot.y - 35f))
                scope.drawCircle(Color(0xFFFFC34D), radius = 2f, center = Offset(pot.x + 3f, pot.y - 36f))
            }
            "head_torch" -> {
                // The Burning Brand had no case at all here, so the player's torch drew as a bare
                // haft while the enemy torch bearer (who carries a club plus flame particles) read
                // correctly as one. It is the same object in both hands now: a pitch-wrapped head
                // that is actually alight.
                val dx = 0.894f; val dy = -0.447f      // unit vector along the haft
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along - dy * across,
                    headPos.y + dy * along + dx * across
                )
                // Pitch-soaked rag bound round the head
                val wrap = Path().apply {
                    moveTo(at(-2f, -7f).x, at(-2f, -7f).y)
                    lineTo(at(26f, -8f).x, at(26f, -8f).y)
                    lineTo(at(26f, 8f).x, at(26f, 8f).y)
                    lineTo(at(-2f, 7f).x, at(-2f, 7f).y)
                    close()
                }
                drawStitchedFill(scope, wrap, Color(0xFF3E2723))
                scope.drawStitchedOutline(wrap, ThreadColor)
                // Binding cords
                for (t in listOf(4f, 12f, 20f)) {
                    scope.drawLine(ThreadColor, at(t, -8f), at(t, 8f), strokeWidth = 1.8f)
                }
                // Fire RISES. The licks used to be laid out along the haft basis, so the flame
                // shot sideways out of the end of the stick — a blowtorch, not a torch. They are
                // drawn screen-upward from the head of the wrap now, whatever angle the haft is at.
                // The sway is also 9x slower: at sin(animFrame * 9) it strobed rather than guttered.
                val tip = at(26f, 0f)
                val roar = if (isAttacking) 1f + swingProgress * 0.4f else 1f
                val sway = sin(animFrame * 2.2f) * 2.5f
                listOf(
                    Triple(0f, 36f, 7f),      // offsetX, height, half-width
                    Triple(-5f, 21f, 4f),
                    Triple(5f, 17f, 3.5f)
                ).forEachIndexed { i, (offX, h, hw) ->
                    val s = sway * (0.6f + i * 0.35f)
                    val bx = tip.x + offX
                    val by = tip.y
                    val lick = Path().apply {
                        moveTo(bx - hw, by)
                        quadraticTo(bx - hw * 1.35f, by - h * 0.55f, bx + s, by - h * roar)
                        quadraticTo(bx + hw * 1.35f, by - h * 0.55f, bx + hw, by)
                        close()
                    }
                    scope.drawPath(lick, if (i == 0) headColor else Color(0xFFE8A33D))
                    scope.drawPath(lick, ThreadColor, style = Stroke(width = 1.5f))
                }
                // Hot heart of the fire, sitting in the throat of the flame
                scope.drawCircle(
                    Color(0xFFFFE9A8).copy(alpha = 0.8f),
                    radius = 5.5f,
                    center = Offset(tip.x + sway * 0.3f, tip.y - 9f)
                )
            }
            "head_saw_1", "head_saw_2" -> {
                val isHeavy = headId == "head_saw_2"
                val length = if (isHeavy) 55f else 45f
                val height = if (isHeavy) 22f else 12f
                val teethCount = if (isHeavy) 6 else 12
                // Along the haft, not along the screen axis. Drawn flat, the blade met the pole
                // at one corner and ran off at its own angle — the "saw doesn't attach" bug. On
                // the haft basis the pole enters the middle of the blade's back edge, in line.
                val dx = 0.894f; val dy = -0.447f      // unit vector of (0.8, -0.4)
                val px = -dy; val py = dx              // perpendicular, blade back-to-teeth
                fun at(along: Float, across: Float) = Offset(
                    headPos.x + dx * along + px * across,
                    headPos.y + dy * along + py * across
                )

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(at(0f, -height / 2).x, at(0f, -height / 2).y)
                    // blunt top edge tapers down
                    lineTo(at(length, -height / 4).x, at(length, -height / 4).y)

                    // Saw teeth on bottom edge (right to left)
                    for(i in teethCount downTo 1) {
                        val fraction = i / teethCount.toFloat()
                        val nextFraction = (i - 1) / teethCount.toFloat()
                        val t = length * fraction
                        val n = length * nextFraction
                        lineTo(at(t, height / 2).x, at(t, height / 2).y) // tooth point down
                        val v = n + (t - n) / 2
                        lineTo(at(v, 0f).x, at(v, 0f).y) // valley up
                    }
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawStitchedOutline(path, ThreadColor)
            }
        }
    }

    private fun drawWeapon(scope: DrawScope, hx: Float, hy: Float, fighter: FighterState, armAngle: Float = 0f) {
        if (fighter.weaponHead.id == "head_bare" && fighter.weaponHandle.id == "handle_fists") return
        
        val isBowOrSlingshot = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")

        // Determine direction vector of the handle/pole (visually lengthen based on haft extensions)
        val handleLen = if (isBowOrSlingshot) {
            // A short standoff, not zero. Bows and slingshots have no haft, so their head used to
            // land exactly on the fist — the sleeve, the hand and the face were all drawn in the
            // same few pixels, and a slingshot is small enough to disappear into them entirely.
            // No shaft is drawn for these (every draw below is guarded on isBowOrSlingshot), so
            // this only pushes the grip clear of the hand.
            18f
        } else {
            // The table lives in GameData because FighterState.meleeReachPixels reads the same
            // numbers — the hitbox is derived from this drawing, not guessed alongside it.
            val baseLen = GameData.haftPixels(fighter.weaponHandle.id)
            // A lashed-on shaft adds a flat EXTENSION_REACH_PX of GROUND, so it is divided by
            // body size here (the whole figure is scaled by size around the feet) and divided by
            // the haft basis (the shaft rises as it goes out, so only 0.894 of it is ground).
            // It used to add 48px AND a 12% stretch, both multiplied by size, while the stat added
            // a flat 0.35m — the picture and the hitbox grew at completely different rates.
            baseLen + fighter.handleExtensionCount *
                (GameData.EXTENSION_REACH_PX / GameData.HAFT_BASIS_X / fighter.size)
        }
        // The bespoke hafts below (antler, trumpet, wheelbarrow, anchor) draw fixed paths rather
        // than a shaft of handleLen, so they take the growth as a scale or they alone would show
        // no sign of an extension the player paid a reward card for.
        val extScale = 1f + fighter.handleExtensionCount * 0.16f

        // Handle shaft (wooden)
        val shaftEnd = androidx.compose.ui.geometry.Offset(hx + handleLen * 0.8f, hy - handleLen * 0.4f)
        var headPos = shaftEnd
        if (isBowOrSlingshot) {
            // The bow's art starts at the 18px standoff above, which left it floating clear of the
            // fist with nothing joining the two. A plain wooden grip spans the gap; the bow art
            // itself is untouched, it just now has a handle reaching back into the hand.
            scope.drawLine(Color(0xFF8C6F47), Offset(hx, hy), shaftEnd, strokeWidth = 6f, cap = StrokeCap.Round)
            scope.drawLine(ThreadColor, Offset(hx, hy), shaftEnd, strokeWidth = 2f, cap = StrokeCap.Round)
        }
        val isChainHandle = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
        if (isChainHandle && !isBowOrSlingshot) {
            val swing = fighter.swingProgress
            val gripEnd = Offset(hx - 10f, hy + 5f)
            // Physics pendulum: chain head lags on windup, whips forward on release, dangles idle
            val pendulumAngle = when {
                fighter.isAttacking -> 90f + swing * 360f // Clockwise overhead smash
                else -> 90f - (fighter.velocityX * 0.1f).coerceIn(-20f, 20f)  // idle natural dangle down
            }
            val chainLen = 45f
            val angleRad = Math.toRadians(pendulumAngle.toDouble())
            // Chain head is offset from the grip end by pendulum physics
            val chainHeadX = gripEnd.x + cos(angleRad).toFloat() * chainLen
            val chainHeadY = gripEnd.y + sin(angleRad).toFloat() * chainLen
            
            val numLinks = 6
            var prevLink = gripEnd
            for (i in 1..numLinks) {
                val t = i.toFloat() / numLinks
                // Flail offset using sine wave on the time/swing
                val flail = if (fighter.isAttacking) kotlin.math.sin(swing * Math.PI * 4 + i).toFloat() * 12f else 0f
                val perpAngle = angleRad + Math.PI / 2
                val targetX = gripEnd.x + (chainHeadX - gripEnd.x) * t + cos(perpAngle).toFloat() * flail
                val targetY = gripEnd.y + (chainHeadY - gripEnd.y) * t + sin(perpAngle).toFloat() * flail
                val currLink = Offset(targetX, targetY)
                
                // Draw individual link
                scope.drawLine(Color(0xFF636A6E), prevLink, currLink, strokeWidth = 5f, cap = StrokeCap.Round)
                scope.drawLine(ThreadColor, prevLink, currLink, strokeWidth = 2f, cap = StrokeCap.Round)
                prevLink = currLink
            }
            headPos = prevLink
        } else if (!isBowOrSlingshot) {
            if (fighter.weaponHandle.id == "handle_wheel") {
                // Draw a wheel!
                val midX = (hx - 25f + shaftEnd.x) / 2
                val midY = (hy + 12f + shaftEnd.y) / 2
                val wheelCenter = androidx.compose.ui.geometry.Offset(midX, midY)
                // Open rim, not a solid disc — the gaps between spokes show the scene behind,
                // instead of an opaque pale infill.
                scope.drawCircle(fighter.weaponHandle.color, radius = 31f, center = wheelCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f))
                scope.drawCircle(ThreadColor, radius = 34f, center = wheelCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                scope.drawCircle(ThreadColor, radius = 28f, center = wheelCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                for (i in 0 until 6) {
                    val angle = (i * Math.PI / 6).toFloat()
                    val p1 = androidx.compose.ui.geometry.Offset(wheelCenter.x + kotlin.math.cos(angle)*28f, wheelCenter.y + kotlin.math.sin(angle)*28f)
                    val p2 = androidx.compose.ui.geometry.Offset(wheelCenter.x - kotlin.math.cos(angle)*28f, wheelCenter.y - kotlin.math.sin(angle)*28f)
                    scope.drawLine(fighter.weaponHandle.color, p1, p2, strokeWidth = 3f)
                }
                scope.drawCircle(fighter.weaponHandle.color, radius = 6f, center = wheelCenter) // hub
            } else if (fighter.weaponHandle.id == "handle_stump") {
                val logW = 45f
                val logL = handleLen + 40f
                val offsetX = -logL / 4f
                scope.withTransform({
                    translate(hx, hy)
                    rotate(-26.5f, pivot = androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    val stumpPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(offsetX, -logW/2)
                        lineTo(offsetX + logL - 10f, -logW/2 + 5f)
                        lineTo(offsetX + logL, -logW/2 + 15f)
                        lineTo(offsetX + logL + 5f, 0f)
                        lineTo(offsetX + logL - 5f, logW/2 - 10f)
                        lineTo(offsetX + logL - 15f, logW/2)
                        lineTo(offsetX, logW/2)
                        close()
                    }
                    scope.drawPath(stumpPath, Color(0xFF4A2F1D))
                    scope.drawPath(stumpPath, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width=2f))
                    // Wood grain
                    scope.drawLine(Color(0xFF2E1A0F), Offset(offsetX + 10f, -logW/4), Offset(offsetX + logL - 20f, -logW/4), strokeWidth = 2f)
                    scope.drawLine(Color(0xFF2E1A0F), Offset(offsetX + 5f, 0f), Offset(offsetX + logL - 10f, 0f), strokeWidth = 2f)
                    scope.drawLine(Color(0xFF2E1A0F), Offset(offsetX + 15f, logW/4), Offset(offsetX + logL - 25f, logW/4), strokeWidth = 2f)
                }
                val effL = offsetX + logL
                headPos = Offset(hx + effL * 0.894f, hy - effL * 0.447f)
            } else if (fighter.weaponHandle.id == "handle_ram") {
                val ramW = 32f
                val ramL = handleLen + 80f
                val offsetX = -ramL / 3
                scope.withTransform({
                    translate(hx, hy)
                    rotate(-26.5f, pivot = androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    scope.drawRect(Color(0xFF4A3C31), topLeft = Offset(offsetX, -ramW/2), size = androidx.compose.ui.geometry.Size(ramL, ramW))
                    scope.drawRect(ThreadColor, topLeft = Offset(offsetX, -ramW/2), size = androidx.compose.ui.geometry.Size(ramL, ramW), style = Stroke(width=2f))
                    scope.drawRect(Color(0xFF7A7A7A), topLeft = Offset(offsetX + 10f, -ramW/2), size = androidx.compose.ui.geometry.Size(8f, ramW))
                    scope.drawRect(Color(0xFF7A7A7A), topLeft = Offset(offsetX + ramL - 25f, -ramW/2), size = androidx.compose.ui.geometry.Size(8f, ramW))
                    scope.drawArc(Color(0xFF7A7A7A), startAngle = -90f, sweepAngle = 180f, useCenter = true, topLeft = Offset(offsetX + ramL - 10f, -ramW/2), size = androidx.compose.ui.geometry.Size(20f, ramW))
                }
                val effL = offsetX + ramL
                headPos = Offset(hx + effL * 0.894f, hy - effL * 0.447f)
            } else if (fighter.weaponHandle.id == "handle_plough") {
                scope.withTransform({
                    translate(hx, hy)
                    rotate(-26.5f, pivot = androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    scope.drawLine(Color(0xFF5E4933), Offset(0f, 0f), Offset(handleLen, 0f), strokeWidth = 8f)
                    scope.drawLine(ThreadColor, Offset(0f, 0f), Offset(handleLen, 0f), strokeWidth = 2f)
                    val share = Path().apply {
                        moveTo(handleLen - 10f, 0f)
                        lineTo(handleLen + 20f, 15f)
                        lineTo(handleLen + 20f, -15f)
                        close()
                    }
                    scope.drawPath(share, Color(0xFF7A7A7A))
                    scope.drawPath(share, ThreadColor, style = Stroke(width=2f))
                    scope.drawLine(Color(0xFF5E4933), Offset(30f, 0f), Offset(10f, -25f), strokeWidth = 6f)
                    scope.drawLine(Color(0xFF5E4933), Offset(30f, 0f), Offset(10f, 25f), strokeWidth = 6f)
                }
                // Weapon head welds to the visual end of the rotated shaft (-26.5° = 0.894/0.447),
                // not the generic shaftEnd, which sat ~10px short
                headPos = Offset(hx + handleLen * 0.894f, hy - handleLen * 0.447f)
            } else if (fighter.weaponHandle.id == "handle_blessed_branch") {
                scope.withTransform({ translate(hx, hy); scale(extScale, extScale, pivot = Offset.Zero) }) {
                    val path_0 = Path().apply {
                        moveTo(-10f, 20f)
                        lineTo(30f, -40f)
                        lineTo(30f, -40f)
                        lineTo(60f, -60f)
                        lineTo(40f, -40f)
                        lineTo(50f, -60f)
                        lineTo(40f, -40f)
                        lineTo(30f, -40f)
                        lineTo(30f, -70f)
                        lineTo(30f, -40f)
                        lineTo(60f, -80f)
                        close()
                    }
                    drawStitchedFill(this, path_0, Color(0xFF8A5E38))
                    drawStitchedOutline(path_0, ThreadColor)
                    val path_1 = Path().apply {
                        moveTo(-10f, 20f)
                        lineTo(-10f, 10f)
                        lineTo(20f, -40f)
                        lineTo(30f, -50f)
                        lineTo(30f, -40f)
                        lineTo(30f, -70f)
                        lineTo(40f, -50f)
                        lineTo(60f, -80f)
                        lineTo(60f, -80f)
                        lineTo(50f, -70f)
                        lineTo(50f, -70f)
                        close()
                    }
                    drawStitchedFill(this, path_1, Color(0xFF8A5E38))
                    drawStitchedOutline(path_1, ThreadColor)
                    val path_2 = Path().apply {
                        moveTo(40f, -40f)
                        lineTo(40f, -50f)
                        lineTo(30f, -50f)
                        lineTo(30f, -40f)
                        lineTo(40f, -40f)
                        close()
                    }
                    drawStitchedFill(this, path_2, Color(0xFF8A5E38))
                    drawStitchedOutline(path_2, ThreadColor)
                }
                // Weapon head welds to the branch's visual tip (60,-80 local), not the generic
                // shaftEnd — there was a visible gap otherwise
                // headPos rides extScale because the haft art beside it does. These bespoke
                // hafts draw fixed paths inside a scale(extScale) transform, but pinned the head
                // at a constant offset — so every lashed-on extension lengthened the drawn shaft
                // and left the head hanging in the air where the short shaft used to end.
                headPos = Offset(hx + 60f * extScale, hy - 80f * extScale)
            } else if (fighter.weaponHandle.id == "handle_antler") {
                // A forked antler: main beam plus two tines off the outside edge.
                scope.withTransform({ translate(hx, hy); scale(extScale, extScale, pivot = Offset.Zero) }) {
                    val beam = Path().apply {
                        moveTo(0f, 0f)
                        quadraticTo(6f, -18f, 2f, -40f)
                    }
                    drawPath(beam, fighter.weaponHandle.color, style = Stroke(width = 7f))
                    drawStitchedOutline(beam, ThreadColor)
                    drawLine(fighter.weaponHandle.color, Offset(3f, -18f), Offset(16f, -26f), strokeWidth = 5f)
                    drawLine(fighter.weaponHandle.color, Offset(2f, -30f), Offset(14f, -40f), strokeWidth = 5f)
                }
                // headPos rides extScale because the haft art beside it does. These bespoke
                // hafts draw fixed paths inside a scale(extScale) transform, but pinned the head
                // at a constant offset — so every lashed-on extension lengthened the drawn shaft
                // and left the head hanging in the air where the short shaft used to end.
                headPos = Offset(hx + 2f * extScale, hy - 40f * extScale)
            } else if (fighter.weaponHandle.id == "handle_trumpet") {
                // A herald's straight buisine, gripped at the mouthpiece with the bell swung
                // outward — the bell is the business end, so that is where the weapon head goes.
                // It used to be held by the bell, which put the widest, heaviest part of the
                // instrument in the fist and the head on the mouthpiece.
                //
                // Was a drawStitchedStrap — the same primitive that draws arms and straps — in
                // gold, with a 14px trapezoid stuck on the end. It read as a yellow stitched arm
                // because that is exactly what it was. A trumpet needs the flare, a tube that
                // tapers into it, and the ferrule bands, or it is just a stick.
                // Canted out along the haft line rather than straight up: held vertically the
                // bell, now the top end, sat squarely over the fighter's own head and read as a
                // hat. The narrow mouthpiece used to be up there, which is why it never showed.
                scope.withTransform({
                    translate(hx, hy)
                    rotate(30f, pivot = Offset.Zero)
                    scale(extScale, extScale, pivot = Offset.Zero)
                }) {
                    val brass = fighter.weaponHandle.color
                    // The bell: a real flare, widest at the far end and drawn in two steps so
                    // the rim reads as a lip rather than a straight cut.
                    val bell = Path().apply {
                        moveTo(-19f, -62f)
                        quadraticTo(-16f, -50f, -7f, -41f)
                        lineTo(7f, -41f)
                        quadraticTo(16f, -50f, 19f, -62f)
                        quadraticTo(0f, -68f, -19f, -62f)
                        close()
                    }
                    drawStitchedFill(this, bell, brass)
                    drawStitchedOutline(bell, ThreadColor)

                    // Tapering tube from the mouthpiece out to the bell throat.
                    val tube = Path().apply {
                        moveTo(-6.5f, -41f)
                        lineTo(6.5f, -41f)
                        lineTo(3.5f, -2f)
                        lineTo(-3.5f, -2f)
                        close()
                    }
                    drawStitchedFill(this, tube, brass)
                    drawStitchedOutline(tube, ThreadColor)

                    // Ferrule bands: the join collars. Two short bars are the whole difference
                    // between "brass instrument" and "yellow pole".
                    listOf(-16f, -31f).forEach { y ->
                        drawLine(ThreadColor, Offset(-6f, y), Offset(6f, y), strokeWidth = 2f)
                    }
                    // Mouthpiece cup in the fist.
                    drawCircle(brass, radius = 5f, center = Offset(0f, 0f))
                    drawCircle(ThreadColor, radius = 5f, center = Offset(0f, 0f), style = StitchedStroke)
                }
                // The head lashes to the bell rim, the end that goes into the enemy — local
                // (0,-64) carried through the 30 degree cant above.
                // headPos rides extScale because the haft art beside it does. These bespoke
                // hafts draw fixed paths inside a scale(extScale) transform, but pinned the head
                // at a constant offset — so every lashed-on extension lengthened the drawn shaft
                // and left the head hanging in the air where the short shaft used to end.
                headPos = Offset(hx + 32f * extScale, hy - 55.4f * extScale)
            } else if (fighter.weaponHandle.id == "handle_wheelbarrow") {
                // Sideways profile view: wooden shaft, tub/hopper box, front wheel & axle, support leg.
                scope.withTransform({ translate(hx, hy); scale(extScale, extScale, pivot = Offset.Zero) }) {
                    val frameColor = fighter.weaponHandle.color
                    // 1. Diagonal main wooden shaft extending towards front wheel
                    drawStitchedStrap(this, Offset(54.5f, 16.5f), Offset(119.5f, 25f), frameColor, stitched = true)

                    // 2. Support leg angled down near rear of tub
                    drawLine(Color(0xFF5D4831), Offset(-2.5f, -0.5f), Offset(41.5f, 20.5f), strokeWidth = 5f)
                    drawLine(ThreadColor, Offset(124.5f, -8.5f), Offset(47.5f, 11.5f), strokeWidth = 1.5f)

                    // 3. Side profile of the wooden tub/hopper
                    val tubPath = Path().apply {
                        moveTo(42.5f, 54.5f)
                        lineTo(137.5f, 19.5f)
                        lineTo(157.5f, -25.5f)
                        lineTo(33.5f, 15.5f)
                        close()
                    }
                    drawStitchedFill(this, tubPath, Color(0xFF8B6B4A))
                    drawStitchedOutline(tubPath, ThreadColor)
                    // Wooden plank slats inside tub
                    drawLine(ThreadColor, Offset(56.5f, 77.5f), Offset(89.5f, 32.5f), strokeWidth = 1f)
                    drawLine(ThreadColor, Offset(47.5f, 57.5f), Offset(55.5f, 79.5f), strokeWidth = 1f)

                    // 4. Front wheel (spoked wheel at front of frame)
                    val wheelCenter = Offset(129.5f, 35f)
                    val wheelRadius = 14f
                    drawCircle(Color(0xFF4A3B2C), radius = wheelRadius, center = wheelCenter)
                    drawCircle(Color(0xFF8B6B4A), radius = wheelRadius - 3.5f, center = wheelCenter)
                    drawCircle(ThreadColor, radius = wheelRadius, center = wheelCenter, style = Stroke(width = 1.5f))
                    // Spokes
                    drawLine(ThreadColor, Offset(wheelCenter.x - wheelRadius, wheelCenter.y), Offset(wheelCenter.x + wheelRadius, wheelCenter.y), strokeWidth = 1.5f)
                    drawLine(ThreadColor, Offset(wheelCenter.x, wheelCenter.y - wheelRadius), Offset(wheelCenter.x, wheelCenter.y + wheelRadius), strokeWidth = 1.5f)
                    drawCircle(Color(0xFF33261A), radius = 3.5f, center = wheelCenter)
                }
                // headPos rides extScale because the haft art beside it does. These bespoke
                // hafts draw fixed paths inside a scale(extScale) transform, but pinned the head
                // at a constant offset — so every lashed-on extension lengthened the drawn shaft
                // and left the head hanging in the air where the short shaft used to end.
                headPos = Offset(hx + 142.5f * extScale, hy + 0.5f * extScale)
            } else if (fighter.weaponHandle.id == "handle_plank") {
                // Nail-Studded Plank: wide wooden board bristling with rusted iron nails
                scope.withTransform({ translate(hx, hy); scale(extScale, extScale, pivot = Offset.Zero) }) {
                    val plankColor = Color(0xFF8A5E38)
                    val rustColor = Color(0xFF8B4513)
                    val darkRust = Color(0xFF5A2A0A)
                    
                    // Main plank board path (thick rectangular board)
                    val plankPath = Path().apply {
                        moveTo(-12f, 8f)
                        lineTo(48f, -38f)
                        lineTo(42f, -46f)
                        lineTo(-18f, 0f)
                        close()
                    }
                    drawStitchedFill(this, plankPath, plankColor)
                    drawStitchedOutline(plankPath, ThreadColor)

                    // Wood grain lines
                    drawLine(ThreadColor.copy(alpha = 0.5f), Offset(-13f, 4f), Offset(45f, -42f), strokeWidth = 1f)
                    drawLine(ThreadColor.copy(alpha = 0.5f), Offset(-16f, 2f), Offset(43f, -44f), strokeWidth = 1f)

                    // Rusted iron nails driven through the board sticking out at sharp angles
                    val nailOffsets = listOf(
                        Triple(Offset(0f, -4f), Offset(-8f, -16f), Offset(4f, 2f)),
                        Triple(Offset(12f, -13f), Offset(18f, -28f), Offset(8f, -4f)),
                        Triple(Offset(24f, -22f), Offset(14f, -34f), Offset(28f, -14f)),
                        Triple(Offset(34f, -29f), Offset(44f, -42f), Offset(28f, -22f)),
                        Triple(Offset(42f, -35f), Offset(54f, -44f), Offset(36f, -30f)),
                        Triple(Offset(6f, -8f), Offset(14f, -2f), Offset(2f, -14f))
                    )

                    nailOffsets.forEach { (boardPt, spikeEnd, headPt) ->
                        drawLine(darkRust, boardPt, spikeEnd, strokeWidth = 3f)
                        drawLine(rustColor, boardPt, spikeEnd, strokeWidth = 1.5f)
                        drawLine(darkRust, boardPt, headPt, strokeWidth = 2f)
                        drawCircle(darkRust, radius = 2f, center = headPt)
                    }
                }
                // headPos rides extScale because the haft art beside it does. These bespoke
                // hafts draw fixed paths inside a scale(extScale) transform, but pinned the head
                // at a constant offset — so every lashed-on extension lengthened the drawn shaft
                // and left the head hanging in the air where the short shaft used to end.
                headPos = Offset(hx + 45f * extScale, hy - 42f * extScale)
            } else if (fighter.weaponHandle.id == "handle_anchor") {
                // An Admiralty-pattern anchor, gripped at the ring with the crown swung outward.
                //
                // The old one was a plain bar, a thin crossbar and a shallow blob at the top —
                // it had no ring, no curved arms and no flukes, so it read as a spanner. An
                // anchor is recognised by four things and it had one of them.
                scope.withTransform({ translate(hx, hy); scale(extScale, extScale, pivot = Offset.Zero) }) {
                    val iron = fighter.weaponHandle.color

                    // Shank, thick enough to carry the crown.
                    val shank = Path().apply {
                        moveTo(-4.5f, 2f); lineTo(4.5f, 2f)
                        lineTo(3.5f, -62f); lineTo(-3.5f, -62f)
                        close()
                    }
                    drawStitchedFill(this, shank, iron)
                    drawStitchedOutline(shank, ThreadColor)

                    // The ring at the grip end — where the cable bends on, and the giveaway
                    // silhouette detail the old drawing was missing entirely.
                    drawCircle(iron, radius = 8.5f, center = Offset(0f, 8f), style = Stroke(width = 4f))
                    drawCircle(ThreadColor, radius = 8.5f, center = Offset(0f, 8f), style = StitchedStroke)

                    // Stock: the crossbar, set near the ring as on a real anchor, with the
                    // slight taper the forged bar has.
                    val stock = Path().apply {
                        moveTo(-25f, -20f); lineTo(25f, -20f)
                        lineTo(23f, -14f); lineTo(-23f, -14f)
                        close()
                    }
                    drawStitchedFill(this, stock, iron)
                    drawStitchedOutline(stock, ThreadColor)

                    // Crown and arms. The arms sweep out from the crown and curve BACK TOWARD the
                    // ring — that returning hook is what makes an anchor an anchor, and drawing
                    // them arcing away from the ring turned the whole thing into a grapnel.
                    // The ring is at +8 here, so "toward the ring" is downward in this space.
                    listOf(-1f, 1f).forEach { dir ->
                        val arm = Path().apply {
                            moveTo(dir * 4f, -62f)
                            // Outer edge: out from the crown, then hooking down toward the ring.
                            quadraticTo(dir * 27f, -63f, dir * 31f, -40f)
                            lineTo(dir * 40f, -45f)            // fluke palm, outer point
                            lineTo(dir * 26f, -55f)            // palm's inner corner
                            // Inner edge back to the crown, leaving a hollow inside the hook.
                            quadraticTo(dir * 20f, -60f, 0f, -54f)
                            close()
                        }
                        drawStitchedFill(this, arm, iron)
                        drawStitchedOutline(arm, ThreadColor)
                    }
                }
                // headPos rides extScale because the haft art beside it does. These bespoke
                // hafts draw fixed paths inside a scale(extScale) transform, but pinned the head
                // at a constant offset — so every lashed-on extension lengthened the drawn shaft
                // and left the head hanging in the air where the short shaft used to end.
                headPos = Offset(hx, hy - 80f * extScale)
            } else if (fighter.weaponHandle.id == "handle_oar") {
                // A ship's oar: a long loom with a flat blade spooned onto the far end. It rode
                // the generic straight-haft path, which draws nothing but a line — an oar with
                // no blade is a stick, and that is exactly how it read.
                //
                // handleLen already carries the extension growth, so this uses it directly the
                // way handle_plough does; extScale would double-count it.
                scope.withTransform({
                    translate(hx, hy)
                    rotate(-26.5f, pivot = Offset.Zero)
                }) {
                    // Loom, from the grip out to the throat of the blade.
                    drawLine(fighter.weaponHandle.color, Offset(-25f, 0f), Offset(handleLen - 34f, 0f), strokeWidth = 7f)
                    drawLine(ThreadColor, Offset(-25f, 0f), Offset(handleLen - 34f, 0f), strokeWidth = 2f)
                    // Blade: widens off the throat, then squares off at the tip.
                    val blade = Path().apply {
                        moveTo(handleLen - 36f, -3.5f)
                        quadraticTo(handleLen - 22f, -15f, handleLen - 6f, -13f)
                        lineTo(handleLen + 6f, -11f)
                        lineTo(handleLen + 6f, 11f)
                        lineTo(handleLen - 6f, 13f)
                        quadraticTo(handleLen - 22f, 15f, handleLen - 36f, 3.5f)
                        close()
                    }
                    drawStitchedFill(this, blade, fighter.weaponHandle.color)
                    drawStitchedOutline(blade, ThreadColor)
                    // Grain down the blade, so it reads as a shaved plank and not a paddle-shaped hole.
                    drawLine(ThreadColor.copy(alpha = 0.5f), Offset(handleLen - 30f, 0f), Offset(handleLen + 3f, 0f), strokeWidth = 1f)
                }
                headPos = Offset(hx + handleLen * 0.894f, hy - handleLen * 0.447f)
            } else if (fighter.weaponHandle.id == "handle_fists") {
                // Do nothing for fists handle
            } else {
                val startP = androidx.compose.ui.geometry.Offset(hx - 25f, hy + 12f)
                val isDouble = fighter.weaponHandle.id == "handle_double_ended"
                val trueStart = if (isDouble) androidx.compose.ui.geometry.Offset(hx - handleLen * 0.8f, hy + handleLen * 0.4f) else startP
                
                scope.drawLine(
                    color = fighter.weaponHandle.color,
                    start = trueStart, // pommel/grip end extends further back
                    end = shaftEnd,
                    strokeWidth = 5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Outline shaft
                scope.drawLine(
                    color = ThreadColor,
                    start = trueStart,
                    end = shaftEnd,
                    strokeWidth = 1.5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        val headsToDraw = mutableListOf<androidx.compose.ui.geometry.Offset>()
        headsToDraw.add(headPos)
        if (fighter.weaponHandle.id == "handle_double_ended") {
            headsToDraw.add(androidx.compose.ui.geometry.Offset(hx - handleLen * 0.8f, hy + handleLen * 0.4f))
        }

        // Dangle rotation if it's on a chain!
        val isChain = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
        val dangleAngle = if (isChain) {
            val swing = fighter.swingProgress
            when {
                fighter.isAttacking -> {
                    if (fighter.facingRight) -90f + swing * 270f else 90f - swing * 270f
                }
                else -> 90f - (fighter.velocityX * 0.1f).coerceIn(-20f, 20f)
            }
        } else 0f
        val isPick = fighter.weaponHandle.id == "handle_pick"

        for (i in headsToDraw.indices) {
            val hPos = headsToDraw[i]
            val isBackHead = i == 1
            
            scope.withTransform({
                if (isBowOrSlingshot) {
                    rotate(-armAngle, pivot = hPos) // Counter-rotate the weapon to keep it upright
                }
                if (isBackHead) {
                    rotate(180f, pivot = hPos)
                }
                if (isChain) {
                    rotate(dangleAngle, pivot = hPos)
                }
                if (isPick) {
                    rotate(80f, pivot = hPos)
                }
            }) {
                drawWeaponHead(
                    scope = this,
                    headId = fighter.weaponHead.id,
                    headColor = fighter.weaponHead.color,
                    headPos = hPos,
                    animFrame = fighter.animFrame,
                    swingProgress = fighter.swingProgress,
                    isAttacking = fighter.isAttacking
                )
                // A melee weapon visibly grows as it is upgraded — welded heads, a longer haft.
                // A bow looked identical on the last level as on the first, however much had been
                // lashed to it, so this is the same evolution for missile weapons.
                drawRangedEvolution(this, fighter, hPos)
            }
        }

        // Removed white swoosh line

        // Removed Rear-head swoosh arc for double-ended weapons

        // Bloody weapon?
        if (fighter.level > 1 && !isBowOrSlingshot) {
            scope.drawLine(androidx.compose.ui.graphics.Color(0xFF9E3624), headPos, androidx.compose.ui.geometry.Offset(headPos.x + 5f, headPos.y - 5f), strokeWidth = 2f)
        }

        // Extra attachments direct-welded in a branching organic pattern
        if (fighter.extraAttachments.isNotEmpty()) {
            // Keep track of attachment tip points for branching
            val attachmentPoints = mutableListOf<Offset>()
            attachmentPoints.add(headPos) // Start branching from main head
            
            // Add a few points along the handle as base nodes
            if (!isBowOrSlingshot) {
                if (isChainHandle) {
                    val gripEnd = Offset(hx - 10f, hy + 5f)
                    attachmentPoints.add(Offset(gripEnd.x + (headPos.x - gripEnd.x) * 0.7f, gripEnd.y + (headPos.y - gripEnd.y) * 0.7f))
                } else {
                    attachmentPoints.add(Offset(hx + handleLen * 0.7f * 0.8f, hy - handleLen * 0.7f * 0.4f))
                    attachmentPoints.add(Offset(hx + handleLen * 0.4f * 0.8f, hy - handleLen * 0.4f * 0.4f))
                }
            }
            
            fighter.extraAttachments.forEachIndexed { idx, attachment ->
                // Randomized organic angle, biased to generally point toward the enemy (forward)
                // rather than back at the wielder's own face/body.
                val hashAbs = (attachment.id.hashCode() % 1000).let { if (it < 0) it + 1000 else it }
                val angleSeed = hashAbs / 1000f // 0f..1f
                // Forward cone only — anything past ~±75° reads as sideways/upside-down (e.g. the club)
                val attachAngle = -75f + angleSeed * 150f

                val parentNodeIdx =
                    (attachment.id.hashCode() % attachmentPoints.size + attachmentPoints.size) % attachmentPoints.size
                val attachPos = attachmentPoints[parentNodeIdx]

                scope.withTransform({
                    rotate(attachAngle, pivot = attachPos)
                }) {
                    // Direct-welding (no handle/strap, drawn directly at attachPos but offset slightly so it sticks out)
                    val weldOffset = Offset(attachPos.x + 10f, attachPos.y - 5f)
                    
                    // Render the actual selected weapon head!
                    drawWeaponHead(
                        scope = this,
                        headId = attachment.id,
                        headColor = attachment.color,
                        headPos = weldOffset,
                        animFrame = fighter.animFrame,
                        swingProgress = fighter.swingProgress,
                        isAttacking = fighter.isAttacking
                    )
                }
                
                // Add the new tip as a potential branching node for future attachments
                val newAngleRads = Math.toRadians(attachAngle.toDouble())
                val tipDx = (25f * Math.cos(newAngleRads)).toFloat()
                val tipDy = (25f * Math.sin(newAngleRads)).toFloat()
                attachmentPoints.add(Offset(attachPos.x + tipDx, attachPos.y + tipDy))
            }
        }
    }

    private fun drawFrontArmAndWeapon(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        // Swing progress: rotate the arm from high to low
        val swing = fighter.swingProgress
        val isThrusting = fighter.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd", "head_dagger", "head_pitchfork")
        val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe", "head_lucerne", "head_saber")
        val isScythe = fighter.weaponHead.id == "head_scythe"
        val isBowOrSlingshot = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")
        val isChainHandle = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
        var thrustOffset = Offset.Zero
        val isLanceCompatible = fighter.weaponHead.id in listOf("head_pike", "head_spear", "head_halberd")
        val isSaw = fighter.weaponHead.id in listOf("head_saw_1", "head_saw_2")
        val isChokeSlam = fighter.weaponHandle.id == "handle_fists" && fighter.isDualWielding
        // A pike is not a spear: it is couched, braced, and driven forward off the back foot.
        val isPikeThrust = fighter.weaponHead.id == "head_pike"
        val isOverarmThrow = fighter.weaponHead.id in OVERARM_THROW_HEAD_IDS
        val armAngle = if (fighter.isDead || fighter.isDying) {
            if (fighter.isDying) {
                sin(fighter.animFrame * 1.5f) * 85f
            } else {
                45f
            }
        } else if (fighter.isAttacking) {
            if (fighter.weaponHandle.id == "handle_double_ended") {
                // Overhead whirl, clockwise. The alternate swing sometimes reverses — rolled once per
                // attack in triggerAttack, not read off a timestamp's parity every frame.
                val dir = if (fighter.whirlCounterClockwise) -1f else 1f
                360f * swing * dir
            } else if (isPikeThrust) {
                // Long wind-back, then a heavy committed drive: slow to level, then a long lunge that
                // holds at full extension before recovering.
                if (swing < 0.45f) {
                    val draw = swing / 0.45f
                    thrustOffset = Offset(-45f * draw, -6f * draw) // haul it back and level the point
                    -6f * draw
                } else {
                    val lunge = ((swing - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    // ease out hard, hold, then withdraw
                    val ext = kotlin.math.sin(lunge * Math.PI).toFloat()
                    val drive = kotlin.math.min(1f, ext * 1.6f)
                    thrustOffset = Offset(-45f + 145f * drive, -6f + 6f * drive)
                    -6f + 8f * drive
                }
            } else if (isOverarmThrow) {
                // The throw belongs on the FRONT arm — this is the hand the javelin is drawn in.
                // The overarm branch existed only on the back-arm path, so in game the throw was
                // still whatever the generic swing below happened to do.
                // Arm cocks back past the ear, hangs, then whips through and follows down.
                if (swing < 0.45f) -100f * (swing / 0.45f)
                else -100f + 180f * ((swing - 0.45f) / 0.55f)
            } else if (isSaw) {
                val sawExt = kotlin.math.sin(swing * Math.PI * 5).toFloat()
                thrustOffset = Offset(30f * sawExt, 5f * sawExt)
                -10f + 15f * sawExt
            } else if (isChokeSlam) {
                if (swing < 0.4f) {
                    -90f * (swing / 0.4f)
                } else if (swing < 0.7f) {
                    -90f
                } else {
                    -90f + 180f * ((swing - 0.7f) / 0.3f)
                }
            } else if (isHeavy || isChainHandle) {
                if (swing < 0.5f) {
                    -75f * (swing / 0.5f)
                } else {
                    -75f + 160f * ((swing - 0.5f) / 0.5f)
                }
            } else if (isThrusting) {
                if (swing < 0.3f) {
                    thrustOffset = Offset(-25f * (swing / 0.3f), 0f)
                    -10f * (swing / 0.3f)
                } else {
                    val thrustExt = sin((swing - 0.3f) / 0.7f * Math.PI).toFloat()
                    thrustOffset = Offset(55f * thrustExt, -5f * thrustExt)
                    -10f + 15f * thrustExt
                }
            } else if (isScythe) {
                if (swing < 0.3f) {
                    20f * (swing / 0.3f)
                } else {
                    20f - 100f * ((swing - 0.3f) / 0.7f)
                }
            } else if (isBowOrSlingshot) {
                if (swing < 0.4f) {
                    -35f * (swing / 0.4f)
                } else if (swing < 0.85f) {
                    -35f
                } else {
                    -35f + 45f * ((swing - 0.85f) / 0.15f)
                }
            } else {
                if (swing < 0.4f) {
                    -45f * (swing / 0.4f)
                } else {
                    -45f + 130f * ((swing - 0.4f) / 0.6f)
                }
            }
        } else if (fighter.isMounted && isLanceCompatible && kotlin.math.abs(fighter.velocityX) > 30f) {
            -25f
        } else if (isOverarmThrow) {
            // Carried cocked at the shoulder between throws, not dangling at the hip.
            -70f - sin(fighter.animFrame * 0.5f) * 6f
        } else {
            val postureOffset = if (isChokeSlam) -30f else if (isBowOrSlingshot) -15f else when (Math.abs(fighter.name.hashCode()) % 6) {
                0 -> -75f // Raised above head
                1 -> -35f // Nobly in front of face
                2 -> 15f  // Poised outwards
                3 -> 45f  // Resting down
                4 -> -15f // Held casually forward
                else -> 80f // Trailing behind downwards
            }
            postureOffset + (if (isBowOrSlingshot) 0f else sin(fighter.animFrame * 0.5f) * 10f)
        }

        if (fighter.missingArm) {
            scope.withTransform({
                rotate(armAngle, pivot = Offset(cx - 23f, cy + 25f))
            }) {
                val sleeveColor = sleeveTone(fighter, back = false)
                drawStitchedStrap(this, Offset(cx - 23f, cy + 25f), Offset(cx + 4f, cy + 28f), sleeveColor, stitched = sleeveColor != skinTone(fighter))
                scope.drawCircle(Color(0xFF9E3624), radius = 8f, center = Offset(cx + 4f, cy + 28f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 4f, center = Offset(cx + 4f, cy + 28f))
            }
            return
        }

        // Draw weapon and arm together
        scope.withTransform({
            rotate(armAngle, pivot = Offset(cx - 23f, cy + 25f))
            translate(thrustOffset.x, thrustOffset.y)
        }) {
            // Sleeve/Arm
            val sleeveColor = sleeveTone(fighter, back = false)
            drawStitchedStrap(this, Offset(cx - 23f, cy + 25f), Offset(cx + 25f, cy + 30f), sleeveColor, stitched = sleeveColor != skinTone(fighter))
            
            // Hand
            val hasGauntlets = fighter.extraArmors.any { it.id == "armor_gauntlets" }
            if (hasGauntlets) {
                scope.drawRect(Color(0xFF6B747A), topLeft = Offset(cx + 19f, cy + 24f), size = androidx.compose.ui.geometry.Size(12f, 12f))
                scope.drawRect(ThreadColor, topLeft = Offset(cx + 19f, cy + 24f), size = androidx.compose.ui.geometry.Size(12f, 12f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            } else {
                scope.drawCircle(skinTone(fighter), radius = 6f, center = Offset(cx + 25f, cy + 30f))
                scope.drawCircle(ThreadColor, radius = 6f, center = Offset(cx + 25f, cy + 30f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                drawClaws(scope, cx + 25f, cy + 30f, fighter)
            }
            if (fighter.brawlerUpgrades.contains("brass_knuckles")) {
                scope.drawRect(Color(0xFFB5A642), topLeft = Offset(cx + 27f, cy + 26f), size = androidx.compose.ui.geometry.Size(5f, 9f))
                scope.drawRect(ThreadColor, topLeft = Offset(cx + 27f, cy + 26f), size = androidx.compose.ui.geometry.Size(5f, 9f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            }
            if (fighter.brawlerUpgrades.contains("spiked_wraps")) {
                scope.drawLine(Color(0xFF5C4033), Offset(cx + 22f, cy + 26f), Offset(cx + 28f, cy + 32f), strokeWidth = 2f)
                scope.drawLine(Color(0xFF5C4033), Offset(cx + 22f, cy + 32f), Offset(cx + 28f, cy + 26f), strokeWidth = 2f)
                scope.drawLine(Color(0xFF8B4513), Offset(cx + 28f, cy + 26f), Offset(cx + 33f, cy + 24f), strokeWidth = 1.5f)
                scope.drawLine(Color(0xFF8B4513), Offset(cx + 28f, cy + 30f), Offset(cx + 34f, cy + 29f), strokeWidth = 1.5f)
                scope.drawLine(Color(0xFF8B4513), Offset(cx + 28f, cy + 34f), Offset(cx + 33f, cy + 35f), strokeWidth = 1.5f)
            }

            // Weapon (starts at hand and goes outwards) - Drop it if dead or dying
            if (!fighter.isDead && !fighter.isDying) {
                val hx = cx + 25f
                val hy = cy + 30f
                drawWeapon(this, hx, hy, fighter, armAngle)
            }
        }
    }


    private fun drawBackArmAndShield(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val shieldArmAngle = if (fighter.isDead || fighter.isDying) {
            if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
        } else {
            -sin(fighter.animFrame * 0.5f) * 10f
        }
        
        if (fighter.missingArm) {
            scope.withTransform({ rotate(shieldArmAngle, pivot = Offset(cx + 23f, cy + 25f)) }) {
                val sleeveColor = sleeveTone(fighter, back = true)
                drawStitchedStrap(this, Offset(cx + 23f, cy + 25f), Offset(cx + 20f, cy + 35f), sleeveColor, stitched = sleeveColor != skinTone(fighter))
                scope.drawCircle(Color(0xFF9E3624), radius = 7f, center = Offset(cx + 20f, cy + 35f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 3.5f, center = Offset(cx + 20f, cy + 35f))
            }
            return
        }

        val sColor = fighter.shield.color
        val hasKite = fighter.shield.id == "shield_kite"
        val hasTower = fighter.shield.id == "shield_tower"
        val hasHeater = fighter.shield.id == "shield_heater" // big round shield; the small oval is the buckler

        if (fighter.shield.id == "shield_none") {
            if (fighter.isDualWielding) {
                val swing = fighter.swingProgress
                val isChainHandle = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
                val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe", "head_lucerne", "head_saber")
                val isChokeSlam = fighter.weaponHandle.id == "handle_fists" && fighter.isDualWielding
                
                val armAngle = if (fighter.isDead || fighter.isDying) {
                    if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
                } else if (fighter.isAttacking) {
                    if (isChokeSlam) {
                        if (swing < 0.4f) -90f * (swing / 0.4f) else if (swing < 0.7f) -90f else -90f + 180f * ((swing - 0.7f) / 0.3f)
                    } else if (isChainHandle || isHeavy) {
                        if (swing < 0.5f) -75f * (swing / 0.5f) else -75f + 160f * ((swing - 0.5f) / 0.5f)
                    } else {
                        if (swing < 0.5f) -20f + 80f * (swing / 0.5f) else 60f - 80f * ((swing - 0.5f) / 0.5f)
                    }
                } else {
                    val posture = if (isChokeSlam) -30f else if (fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot", "head_crossbow")) -15f else when (Math.abs(fighter.name.hashCode()) % 3) {
                        0 -> -75f
                        1 -> -35f
                        else -> 15f
                    }
                    posture - (if (fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot", "head_crossbow")) 0f else sin(fighter.animFrame * 0.5f) * 10f)
                }

                scope.withTransform({
                    rotate(armAngle, pivot = Offset(cx + 23f, cy + 30f))
                }) {
                    val hx = cx + 35f
                    val hy = cy + 40f
                    val sleeveColor = sleeveTone(fighter, back = true)
                    drawStitchedStrap(this, Offset(cx + 23f, cy + 25f), Offset(hx, hy), sleeveColor, stitched = sleeveColor != skinTone(fighter))
                    scope.drawCircle(skinTone(fighter), radius = 5f, center = Offset(hx, hy))
                    scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                    if (fighter.brawlerUpgrades.contains("brass_knuckles")) {
                        scope.drawRect(Color(0xFFB5A642), topLeft = Offset(hx + 1f, hy - 4f), size = androidx.compose.ui.geometry.Size(4f, 8f))
                        scope.drawRect(ThreadColor, topLeft = Offset(hx + 1f, hy - 4f), size = androidx.compose.ui.geometry.Size(4f, 8f), style = Stroke(width = 1f))
                    }
                    if (fighter.brawlerUpgrades.contains("spiked_wraps")) {
                        scope.drawLine(Color(0xFF5C4033), Offset(hx - 3f, hy - 4f), Offset(hx + 3f, hy + 2f), strokeWidth = 1.5f)
                        scope.drawLine(Color(0xFF5C4033), Offset(hx - 3f, hy + 2f), Offset(hx + 3f, hy - 4f), strokeWidth = 1.5f)
                        scope.drawLine(Color(0xFF8B4513), Offset(hx + 3f, hy - 4f), Offset(hx + 7f, hy - 6f), strokeWidth = 1f)
                        scope.drawLine(Color(0xFF8B4513), Offset(hx + 3f, hy), Offset(hx + 8f, hy - 1f), strokeWidth = 1f)
                        scope.drawLine(Color(0xFF8B4513), Offset(hx + 3f, hy + 4f), Offset(hx + 7f, hy + 5f), strokeWidth = 1f)
                    }
                    
                    if (!fighter.isDead && !fighter.isDying) {
                        drawWeapon(this, hx, hy, fighter)
                    }
                }
            } else {
                val swing = fighter.swingProgress
                val isThrusting = fighter.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd", "head_dagger", "head_pitchfork")
                val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe", "head_lucerne", "head_saber")
                val isScythe = fighter.weaponHead.id == "head_scythe"
                val isSaw = fighter.weaponHead.id in listOf("head_saw_1", "head_saw_2")
                val isChainHandle = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
                val isOverarmThrow = fighter.weaponHead.id in OVERARM_THROW_HEAD_IDS

                var thrustOffset = Offset.Zero
                val armAngle = if (fighter.isDead || fighter.isDying) {
                    if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
                } else if (fighter.isAttacking) {
                    if (isSaw) {
                        val sawExt = kotlin.math.sin(swing * Math.PI * 5).toFloat()
                        thrustOffset = Offset(30f * sawExt, 5f * sawExt)
                        -10f + 15f * sawExt
                    } else if (isHeavy || isChainHandle) {
                        if (swing < 0.5f) -60f * (swing / 0.5f) else -60f + 130f * ((swing - 0.5f) / 0.5f)
                    } else if (isThrusting) {
                        if (swing < 0.3f) {
                            thrustOffset = Offset(-25f * (swing / 0.3f), 0f)
                            -5f * (swing / 0.3f)
                        } else {
                            val thrustExt = (swing - 0.3f) / 0.7f
                            thrustOffset = Offset(55f * thrustExt, -5f * thrustExt)
                            -5f + 10f * thrustExt
                        }
                    } else if (isScythe) {
                        if (swing < 0.3f) 15f * (swing / 0.3f) else 15f - 80f * ((swing - 0.3f) / 0.7f)
                    } else if (isOverarmThrow) {
                        // You do not SWING a javelin, you throw it. The arm cocks right back past
                        // the ear, hangs there a moment, then whips through and follows down across
                        // the body. Bombs use the same motion — it is the same throw.
                        if (swing < 0.45f) -100f * (swing / 0.45f)
                        else -100f + 180f * ((swing - 0.45f) / 0.55f)
                    } else {
                        val isBowOrSling = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")
                        val posture = if (isBowOrSling) 0f else when (Math.abs(fighter.name.hashCode()) % 3) {
                            0 -> -75f
                            1 -> -35f
                            else -> 15f
                        }
                        posture - sin(fighter.animFrame * 0.5f) * 10f
                    }
                } else if (isOverarmThrow) {
                    // Carried cocked at the shoulder between throws, not dangling at the hip.
                    -70f - sin(fighter.animFrame * 0.5f) * 6f
                } else {
                    val isBowOrSling = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")
                    val posture = if (isBowOrSling) 0f else when (Math.abs(fighter.name.hashCode()) % 3) {
                        0 -> -75f
                        1 -> -35f
                        else -> 15f
                    }
                    posture - sin(fighter.animFrame * 0.5f) * 10f
                }

                scope.withTransform({
                    translate(thrustOffset.x, thrustOffset.y)
                    rotate(armAngle, pivot = Offset(cx + 23f, cy + 30f)) // Moved offhand to edge of body
                }) {
                    val handleLen = when (fighter.weaponHandle.id) {
                        "handle_long"   -> 110f
                        "handle_medium" -> 55f
                        else            -> 30f
                    }
                    val frontHandX = cx + 25f
                    val frontHandY = cy + 30f
                    val gripFraction = 0.35f
                    val hx = frontHandX + handleLen * 0.8f * gripFraction
                    val hy = frontHandY - handleLen * 0.4f * gripFraction
                    
                    val sleeveColor = sleeveTone(fighter, back = true)
                    drawStitchedStrap(this, Offset(cx + 23f, cy + 25f), Offset(hx, hy), sleeveColor, stitched = sleeveColor != skinTone(fighter))
                    scope.drawCircle(skinTone(fighter), radius = 5f, center = Offset(hx, hy))
                    scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                    drawClaws(scope, hx, hy, fighter)
                }
            }
            return
        }

        scope.withTransform({
            rotate(shieldArmAngle, pivot = Offset(cx + 15f, cy + 25f))
        }) {
            val hx = cx + 18f
            val hy = cy + 45f
            val sleeveColor = sleeveTone(fighter, back = true)
            drawStitchedStrap(this, Offset(cx + 15f, cy + 25f), Offset(hx, hy), sleeveColor, stitched = sleeveColor != skinTone(fighter))
            scope.drawCircle(skinTone(fighter), radius = 5f, center = Offset(hx, hy))
            scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
        }

        val shx = cx + 18f
        val shy = cy + 45f
        
        val shieldFlailAngle = if (fighter.isDying) {
            sin(fighter.animFrame * 1.2f) * 35f
        } else if (fighter.isDead) {
            15f // tilted limp
        } else {
            0f
        }

        val shieldPath = Path().apply {
            if (hasKite) {
                moveTo(shx - 20f, shy - 30f)
                lineTo(shx + 20f, shy - 30f)
                quadraticTo(shx + 20f, shy + 20f, shx, shy + 65f)
                quadraticTo(shx - 20f, shy + 20f, shx - 20f, shy - 30f)
                close()
            } else if (hasTower) {
                moveTo(shx - 25f, shy - 45f)
                lineTo(shx + 25f, shy - 45f)
                lineTo(shx + 25f, shy + 45f)
                lineTo(shx - 25f, shy + 45f)
                close()
            } else if (hasHeater) {
                // Big round shield — a proper cavalry heater, not a tiny buckler.
                addOval(androidx.compose.ui.geometry.Rect(shx - 30f, shy - 32f, shx + 30f, shy + 34f))
            } else {
                addOval(androidx.compose.ui.geometry.Rect(shx - 18f, shy - 18f, shx + 18f, shy + 18f))
            }
        }

        scope.withTransform({
            rotate(shieldFlailAngle, pivot = Offset(shx, shy))
        }) {
            drawStitchedFill(scope, shieldPath, sColor)

            withTransform({ clipPath(shieldPath) }) {
                if (hasKite) {
                    val crossCol = if (sColor == Color(0xFF9E3624)) Color(0xFFB08221) else Color(0xFF9E3624)
                    drawLine(crossCol, Offset(shx - 30f, shy - 10f), Offset(shx + 30f, shy - 10f), strokeWidth = 10f)
                    drawLine(crossCol, Offset(shx, shy - 30f), Offset(shx, shy + 50f), strokeWidth = 10f)
                    drawCircle(ThreadColor, radius = 2f, center = Offset(shx - 10f, shy - 10f))
                    drawCircle(ThreadColor, radius = 2f, center = Offset(shx + 10f, shy - 10f))
                    drawCircle(ThreadColor, radius = 2f, center = Offset(shx, shy - 20f))
                    drawCircle(ThreadColor, radius = 2f, center = Offset(shx, shy + 10f))
                } else if (hasTower) {
                    val checkerCol = Color(0xFF382F22)
                    drawRect(checkerCol, Offset(shx - 25f, shy - 45f), Size(25f, 45f))
                    drawRect(checkerCol, Offset(shx, shy), Size(25f, 45f))
                    drawLine(ThreadColor, Offset(shx - 25f, shy - 45f), Offset(shx + 25f, shy - 45f), strokeWidth = 4f)
                    drawLine(ThreadColor, Offset(shx - 25f, shy + 45f), Offset(shx + 25f, shy + 45f), strokeWidth = 4f)
                    drawLine(ThreadColor, Offset(shx - 25f, shy - 45f), Offset(shx - 25f, shy + 45f), strokeWidth = 4f)
                    drawLine(ThreadColor, Offset(shx + 25f, shy - 45f), Offset(shx + 25f, shy + 45f), strokeWidth = 4f)
                } else if (hasHeater) {
                    // Big round: iron rim, central boss, and radial spokes.
                    drawCircle(Color(0xFF727A80), radius = 30f, center = Offset(shx, shy), style = Stroke(width = 4f))
                    for (i in 0 until 8) {
                        val a = i * Math.PI.toFloat() / 4f
                        drawLine(ThreadColor.copy(alpha = 0.35f), Offset(shx, shy),
                            Offset(shx + cos(a) * 30f, shy + sin(a) * 32f), strokeWidth = 1.5f)
                    }
                    drawCircle(Color(0xFF6B7882), radius = 9f, center = Offset(shx, shy))
                    drawCircle(ThreadColor, radius = 9f, center = Offset(shx, shy), style = StitchedStroke)
                } else {
                    drawCircle(Color(0xFF6B7882), radius = 6f, center = Offset(shx, shy))
                    drawCircle(ThreadColor, radius = 6f, center = Offset(shx, shy), style = StitchedStroke)
                    drawLine(ThreadColor.copy(alpha=0.4f), Offset(shx - 10f, shy - 16f), Offset(shx - 10f, shy + 16f), strokeWidth = 1f)
                    drawLine(ThreadColor.copy(alpha=0.4f), Offset(shx + 10f, shy - 16f), Offset(shx + 10f, shy + 16f), strokeWidth = 1f)
                    drawLine(ThreadColor.copy(alpha=0.4f), Offset(shx - 4f, shy - 18f), Offset(shx - 4f, shy + 18f), strokeWidth = 1f)
                    drawLine(ThreadColor.copy(alpha=0.4f), Offset(shx + 4f, shy - 18f), Offset(shx + 4f, shy + 18f), strokeWidth = 1f)
                    drawCircle(Color(0xFF727A80), radius = 17f, center = Offset(shx, shy), style = Stroke(width = 3f))
                }

                if (fighter.level > 3) {
                    val numArrows = ((fighter.level - 3) / 2).coerceAtMost(4)
                    for (i in 0 until numArrows) {
                        val ax = shx + 5f + (i * 4f)
                        val ay = shy - 10f + (i * 12f % 30f)
                        drawLine(Color(0xFF8A5E38), Offset(ax - 25f, ay - 8f), Offset(ax, ay), strokeWidth = 2f)
                        drawLine(Color.White, Offset(ax - 25f, ay - 8f), Offset(ax - 30f, ay - 12f), strokeWidth = 1.5f)
                        drawCircle(Color(0xFF2C2219), radius = 1.5f, center = Offset(ax, ay))
                    }
                }
                
                // Shield Upgrades
                if (fighter.shieldUpgrades.contains("oak_reinforcing")) {
                    val oakColor = Color(0xFF4A331A)
                    drawLine(oakColor, Offset(shx - 15f, shy - 40f), Offset(shx - 15f, shy + 60f), strokeWidth = 8f)
                    drawLine(oakColor, Offset(shx, shy - 40f), Offset(shx, shy + 60f), strokeWidth = 8f)
                    drawLine(oakColor, Offset(shx + 15f, shy - 40f), Offset(shx + 15f, shy + 60f), strokeWidth = 8f)
                    // Stitching for the planks
                    drawLine(ThreadColor, Offset(shx - 15f, shy - 40f), Offset(shx - 15f, shy + 60f), strokeWidth = StitchedStroke.width, pathEffect = StitchedStroke.pathEffect)
                    drawLine(ThreadColor, Offset(shx, shy - 40f), Offset(shx, shy + 60f), strokeWidth = StitchedStroke.width, pathEffect = StitchedStroke.pathEffect)
                    drawLine(ThreadColor, Offset(shx + 15f, shy - 40f), Offset(shx + 15f, shy + 60f), strokeWidth = StitchedStroke.width, pathEffect = StitchedStroke.pathEffect)
                }
                
                if (fighter.shieldUpgrades.contains("iron_plating")) {
                    val ironColor = Color(0xFF6B7882)
                    drawCircle(ironColor, radius = 25f, center = Offset(shx, shy), style = Stroke(width = 12f))
                    for (i in 0 until 8) {
                        val angle = i * Math.PI / 4
                        val ax = shx + cos(angle).toFloat() * 25f
                        val ay = shy + sin(angle).toFloat() * 25f
                        drawCircle(Color(0xFF4A5560), radius = 3f, center = Offset(ax, ay))
                    }
                }
            }
            drawStitchedOutline(shieldPath, ThreadColor)
            
            if (fighter.shieldUpgrades.contains("shield_helmet")) {
                val helmColor = Color(0xFF869299)
                val helmPath = Path().apply {
                    moveTo(shx - 12f, shy + 5f)
                    lineTo(shx - 12f, shy - 5f)
                    quadraticTo(shx, shy - 20f, shx + 12f, shy - 5f)
                    lineTo(shx + 12f, shy + 5f)
                    close()
                }
                drawPath(helmPath, helmColor)
                drawStitchedOutline(helmPath, ThreadColor)
                // Nose guard
                drawLine(helmColor, Offset(shx, shy - 2f), Offset(shx, shy + 12f), strokeWidth = 4f, cap = StrokeCap.Round)
                drawLine(ThreadColor, Offset(shx, shy - 2f), Offset(shx, shy + 12f), strokeWidth = 1f)
            }
        }
    }
    private fun drawDamageFlurry(scope: DrawScope, cx: Float, cy: Float) {
        // Dynamic blood-stitch threads sprouting out comedicly!
        for (i in 0 until 5) {
            val start = Offset(cx + Random.nextInt(-15, 15), cy + 45f + Random.nextInt(-25, 25))
            val end = Offset(start.x + Random.nextInt(-40, 40), start.y + Random.nextInt(-40, 40))
            scope.drawLine(
                color = Color(0xFF9E3624), // Tapestry red blood-thread
                start = start,
                end = end,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * Stitched Fill shader: Emulates dense parallel wool yarn couching inside tapestry shapes.
     */
    // drawAncillaries entourage order, cached per unlocked-list instance (see below)
    private var entourageCacheKey: List<com.example.game.Ancillary>? = null
    private var entourageCache: List<IndexedValue<com.example.game.Ancillary>> = emptyList()

    fun drawAncillaries(
        drawScope: DrawScope,
        unlockedAncillaries: List<com.example.game.Ancillary>,
        playerFighter: FighterState,
        scale: Float = 1.0f
    ) {
        // One shared list (SimulationModels) — a locally maintained copy kept falling behind every
        // time a follower gained his own on-field body, leaving grey parade twins.
        val excludedAncillaries = com.example.game.NON_PARADE_ANCILLARIES
        // The filter/sort/re-sort chain allocated four lists per frame and scaled with follower
        // count. The result only changes when the unlocked list itself changes, so cache by it.
        val drawOrder = if (unlockedAncillaries == entourageCacheKey) entourageCache else {
            val sorted = unlockedAncillaries.filter { it !in excludedAncillaries }.sortedBy { it.name }
            entourageCacheKey = unlockedAncillaries
            entourageCache = sorted.withIndex().sortedByDescending { it.index / 5 }
            entourageCache
        }
        if (drawOrder.isEmpty()) return

        val normScale = scale

        drawOrder.forEach { indexedAnc ->
            val index = indexedAnc.index
            val anc = indexedAnc.value
            val appearanceSeed = (anc.id.hashCode() * 31 + index * 1013) and Int.MAX_VALUE
            val offsetSign = if (playerFighter.facingRight) -1f else 1f
            val row = if (index >= 5) 1 else 0
            val column = index % 5
            
            var dynamicWalkOffset = 0f
            if (anc == com.example.game.Ancillary.CUPBEARER && playerFighter.hp < playerFighter.maxHp) {
                val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                dynamicWalkOffset = -60f * offsetSign * kotlin.math.sin(attackAnim * Math.PI).toFloat()
            }
            
            // On a hill the entourage climbs in a diagonal file — each one a little further left and
            // lower down the slope than the last, instead of the flat parade line behind the player.
            val hillClimb = playerFighter.terrainLiftY < -1f
            // Each follower stands behind the player
            val baseOffsetX = if (anc == com.example.game.Ancillary.LIL_GUY) {
                90f * offsetSign // Piggyback position!
            } else if (hillClimb) {
                (index + 1) * 66f * offsetSign + dynamicWalkOffset
            } else {
                (column + 1) * 110f * offsetSign + dynamicWalkOffset
            }
            val cx = playerFighter.posX + baseOffsetX
            val mountOffsetY = if (playerFighter.isChariot) -15f else if (playerFighter.isMounted && playerFighter.isLord) -20f else if (playerFighter.isMounted && playerFighter.isStilts) -STILTS_LIFT_PX else if (playerFighter.isMounted) -35f else 0f
            val cy = if (anc == com.example.game.Ancillary.LIL_GUY) {
                90f + mountOffsetY
            } else if (hillClimb) {
                // Just downhill of the player's (lifted) feet: each rank a touch lower, trailing
                // down-left along the slope.
                200f + playerFighter.terrainLiftY + (index + 1) * 24f
            } else {
                200f + row * 70f
            }
            val roleScale = when (anc) {
                com.example.game.Ancillary.LIL_GUY -> normScale * 0.8f
                com.example.game.Ancillary.SQUIRE -> normScale * 0.85f
                else -> normScale
            }
            val buildScale = when (appearanceSeed % 3) {
                0 -> 0.94f
                1 -> 1f
                else -> 1.06f
            }
            val finalScale = roleScale * buildScale

            // Legs walk in sync but slightly phase-shifted for hilarious visual desync
            val phaseShift = index * 1.5f
            val walkAnim = if (playerFighter.isDead || playerFighter.isDying) 0f else playerFighter.animFrame + phaseShift
            val angleL = if (playerFighter.isDead || playerFighter.isDying) 0f else sin(walkAnim) * 0.45f
            val angleR = if (playerFighter.isDead || playerFighter.isDying) 0f else -sin(walkAnim) * 0.45f

            // Determine if they are wrecked (when the player is dead or dying)
            var rotationAngle = 0f
            var wreckOffsetX = 0f
            var wreckOffsetY = 0f
            var scaleY = 1f

            if (playerFighter.isDead || playerFighter.isDying) {
                // Get comically wrecked!
                val progress = if (playerFighter.isDying) (playerFighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                
                // Different hilarious death trajectories based on index!
                when (index % 4) {
                    0 -> { // Launch high and spin
                        rotationAngle = progress * 360f
                        wreckOffsetX = offsetSign * 80f * progress
                        wreckOffsetY = 70f * progress - 120f * sin(progress * Math.PI).toFloat()
                    }
                    1 -> { // Faceplant flat forward
                        rotationAngle = if (playerFighter.facingRight) 90f * progress else -90f * progress
                        wreckOffsetY = 75f * progress
                        wreckOffsetX = offsetSign * 30f * progress
                    }
                    2 -> { // Pancaked/Squashed flat
                        scaleY = (1f - 0.85f * progress).coerceIn(0.1f, 1f)
                        wreckOffsetY = 65f * progress
                    }
                    3 -> { // Fly far back and roll
                        rotationAngle = -offsetSign * 180f * progress
                        wreckOffsetX = -offsetSign * 110f * progress
                        wreckOffsetY = 75f * progress - 40f * sin(progress * Math.PI).toFloat()
                    }
                }
            }

            drawScope.withTransform({
                // Mirror based on player facing direction, scale appropriately
                val hFlip = if (playerFighter.facingRight) 1f else -1f
                scale(hFlip * finalScale, finalScale, pivot = Offset(cx, cy))
                translate(wreckOffsetX, wreckOffsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                // 1 & 2. Legs + tunic + heraldry live in drawFollowerBody so the
                // magazine can edit the shared body without the item pile.
                val tunicColor = drawFollowerBody(this, anc, appearanceSeed, angleL, angleR, cx, cy)

                // 3. Draw Back Arm holding something (ancillary items!)
                // Most ancillaries hold their item, so we draw their arm first
                val armAngle = when (anc) {
                    com.example.game.Ancillary.TRUMPETER -> -25f
                    com.example.game.Ancillary.CROSSBOWMAN -> {
                        val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                        if (attackAnim < 0.4f) -15f else if (attackAnim < 0.6f) -15f - 10f * ((attackAnim - 0.4f)/0.2f) else -25f + 10f * ((attackAnim - 0.6f)/0.4f)
                    }
                    com.example.game.Ancillary.ARCHER -> {
                        val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                        if (attackAnim < 0.4f) -30f else if (attackAnim < 0.6f) -30f - 15f * ((attackAnim - 0.4f)/0.2f) else -45f + 15f * ((attackAnim - 0.6f)/0.4f)
                    }
                    com.example.game.Ancillary.CUPBEARER -> {
                        val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                        if (attackAnim > 0f && playerFighter.hp < playerFighter.maxHp) -45f * sin(attackAnim * Math.PI).toFloat() else 0f
                    }
                    com.example.game.Ancillary.LIL_GUY -> {
                        // His own throw cycle, not the carrier's walk cycle: wind back slowly,
                        // whip through, and release at the forward extreme where the sim spawns
                        // the dart (both read FighterState.lilGuyThrowPhase).
                        val p = playerFighter.lilGuyThrowPhase
                        if (p < 0.6f) -20f - 90f * (p / 0.6f)
                        else -110f + 130f * ((p - 0.6f) / 0.4f)
                    }
                    else -> 0f
                }
                
                withTransform({
                    rotate(armAngle, pivot = Offset(cx, cy + 50f))
                }) {
                    // Draw the arm!
                    val armHx = cx + 15f
                    val armHy = cy + 40f
                    drawStitchedStrap(this, Offset(cx - 5f, cy + 55f), Offset(armHx, armHy), tunicColor)
                    drawCircle(Color(0xFFE8C5A4), radius = 4f, center = Offset(armHx, armHy))
                    drawCircle(ThreadColor, radius = 4f, center = Offset(armHx, armHy), style = Stroke(width = 1.5f))

                    when (anc) {
                        com.example.game.Ancillary.SQUIRE -> {
                        // Drawing spare folded tunics on his back at shoulder height
                        val tunicStack = Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                rect = androidx.compose.ui.geometry.Rect(cx - 22f, cy + 65f, cx - 2f, cy + 88f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                            ))
                        }
                        drawStitchedFill(this, tunicStack, Color(0xFF1E3F4F))
                        drawPath(tunicStack, ThreadColor, style = Stroke(width = 2f))
                    }
                    com.example.game.Ancillary.HERALD -> {
                        // Standing high with a custom banner of arms!
                        // Banner pole
                        drawLine(Color(0xFF8B5A2B), Offset(cx + 18f, cy + 10f), Offset(cx + 18f, cy + 110f), strokeWidth = 3f)
                        // Banner flag (Bayeux red & gold lion or crest!)
                        val flagPath = Path().apply {
                            moveTo(cx + 18f, cy + 10f)
                            lineTo(cx + 65f, cy + 10f)
                            lineTo(cx + 55f, cy + 28f)
                            lineTo(cx + 65f, cy + 45f)
                            lineTo(cx + 18f, cy + 45f)
                            close()
                        }
                        drawStitchedFill(this, flagPath, Color(0xFFD6A420))
                        drawPath(flagPath, ThreadColor, style = Stroke(width = 2f))
                        // Red cross embroidery inside banner flag
                        drawLine(Color(0xFFB03131), Offset(cx + 18f, cy + 28f), Offset(cx + 55f, cy + 28f), strokeWidth = 2.5f)
                    }
                        com.example.game.Ancillary.TRUMPETER -> {
                            // Long straight heraldic trompette
                            val trumpetLen = 50f
                            val startX = cx + 5f
                            val startY = cy + 40f
                            val endX = cx + trumpetLen
                            val endY = cy + 25f
                            // Main tube
                            drawLine(Color(0xFFE5C158), Offset(startX, startY), Offset(endX, endY), strokeWidth = 5f)
                            drawLine(ThreadColor, Offset(startX, startY), Offset(endX, endY), strokeWidth = 1.5f)
                            
                            // Flared bell
                            val bellPath = Path().apply {
                                moveTo(endX, endY - 8f)
                                lineTo(endX + 15f, endY - 15f)
                                lineTo(endX + 12f, endY + 15f)
                                lineTo(endX, endY + 8f)
                                close()
                            }
                            drawStitchedFill(this, bellPath, Color(0xFFE5C158))
                            drawPath(bellPath, ThreadColor, style = Stroke(width = 1.5f))
                            
                            // Hanging banner!
                            val bannerPath = Path().apply {
                                moveTo(startX + 20f, startY - 6f)
                                lineTo(startX + 40f, startY - 12f)
                                lineTo(startX + 35f, startY + 15f)
                                lineTo(startX + 25f, startY + 10f)
                                lineTo(startX + 15f, startY + 20f)
                                close()
                            }
                            drawStitchedFill(this, bannerPath, Color(0xFFB03131)) // Red banner
                            drawPath(bannerPath, ThreadColor, style = Stroke(width = 1f))
                            // Gold cross on banner
                            drawLine(Color(0xFFD6A420), Offset(startX + 20f, startY + 5f), Offset(startX + 35f, startY + 2f), strokeWidth = 2f)
                            drawLine(Color(0xFFD6A420), Offset(startX + 28f, startY - 5f), Offset(startX + 26f, startY + 15f), strokeWidth = 2f)
                        }
                        com.example.game.Ancillary.CROSSBOWMAN -> {
                            // stock
                            drawLine(Color(0xFF5C4033), Offset(cx - 10f, cy + 40f), Offset(cx + 40f, cy + 30f), strokeWidth = 5f)
                            // bow limbs
                            val bowPath = Path().apply {
                                moveTo(cx + 35f, cy + 10f)
                                quadraticTo(cx + 45f, cy + 30f, cx + 35f, cy + 50f)
                            }
                            drawPath(bowPath, Color(0xFF2E2E2E), style = Stroke(width = 4f))
                            // string
                            val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                            val stringPull = if (attackAnim > 0.1f && attackAnim < 0.8f) 20f else 0f
                            drawLine(Color(0xFFDDDDDD), Offset(cx + 35f, cy + 10f), Offset(cx + 10f - stringPull, cy + 35f), strokeWidth = 1.5f)
                            drawLine(Color(0xFFDDDDDD), Offset(cx + 35f, cy + 50f), Offset(cx + 10f - stringPull, cy + 35f), strokeWidth = 1.5f)
                        }
                        com.example.game.Ancillary.ARCHER -> {
                            // Longbow
                            val bowPath = Path().apply {
                                moveTo(cx + 30f, cy - 10f)
                                quadraticTo(cx + 45f, cy + 40f, cx + 30f, cy + 90f)
                            }
                            drawPath(bowPath, Color(0xFF6E5536), style = Stroke(width = 4.5f))
                            val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                            val stringPull = if (attackAnim > 0.1f && attackAnim < 0.8f) 25f else 0f
                            val stringPath = Path().apply {
                                moveTo(cx + 30f, cy - 10f)
                                lineTo(cx + 30f - stringPull, cy + 40f)
                                lineTo(cx + 30f, cy + 90f)
                            }
                            drawPath(stringPath, Color(0xFFDDDDDD), style = Stroke(width = 1f))
                        }
                        com.example.game.Ancillary.CUPBEARER -> {
                            // Small golden pitcher or goblet
                            val gobletPath = Path().apply {
                                moveTo(cx + 10f, cy + 30f)
                                lineTo(cx + 25f, cy + 30f)
                                lineTo(cx + 22f, cy + 45f)
                                lineTo(cx + 18f, cy + 55f)
                                lineTo(cx + 23f, cy + 60f)
                                lineTo(cx + 12f, cy + 60f)
                                lineTo(cx + 17f, cy + 55f)
                                lineTo(cx + 13f, cy + 45f)
                                close()
                            }
                            drawStitchedFill(this, gobletPath, Color(0xFFE5C158))
                            drawPath(gobletPath, ThreadColor, style = Stroke(width = 2f))
                        }
                        com.example.game.Ancillary.LIL_GUY -> {
                            // Small stone clutched in his throwing hand
                            drawCircle(Color(0xFF7A7A7A), radius = 5f, center = Offset(armHx, armHy))
                            drawCircle(ThreadColor, radius = 5f, center = Offset(armHx, armHy), style = Stroke(width = 1.5f))
                        }
                        com.example.game.Ancillary.MONK -> {
                            val monkArmAngle = -70f + kotlin.math.cos(playerFighter.animFrame * 1.5).toFloat() * 30f
                            withTransform({ rotate(monkArmAngle, pivot = Offset(cx, cy + 50f)) }) {
                                // Draw glass perfume bottle
                                val bottle = Path().apply {
                                    moveTo(cx + 15f, cy + 40f) // neck
                                    lineTo(cx + 15f, cy + 35f)
                                    lineTo(cx + 25f, cy + 35f)
                                    lineTo(cx + 25f, cy + 40f)
                                    // bulbous body
                                    quadraticTo(cx + 35f, cy + 45f, cx + 30f, cy + 55f)
                                    lineTo(cx + 10f, cy + 55f)
                                    quadraticTo(cx + 5f, cy + 45f, cx + 15f, cy + 40f)
                                    close()
                                }
                                drawStitchedFill(this, bottle, Color(0xFF8CD3C7)) // Glass bottle
                                drawPath(bottle, ThreadColor, style = Stroke(width = 1.5f))
                                // cork
                                drawRect(Color(0xFF8B5A2B), Offset(cx + 17f, cy + 30f), androidx.compose.ui.geometry.Size(6f, 5f))
                            }
                        }
                        else -> {}
                    }
                }

                // 4. Head, hair & face live in drawFollowerHead so the magazine can edit them.
                drawFollowerHead(this, anc, appearanceSeed, playerFighter.isDead || playerFighter.isDying, cx, cy)

                // Front Arm (Properly stitched 2-handed grip)
                withTransform({
                    rotate(armAngle, pivot = Offset(cx, cy + 50f))
                }) {
                    val fArmHx = cx + 20f
                    val fArmHy = cy + 45f // Slightly forward to overlap the item naturally
                    drawStitchedStrap(this, Offset(cx + 5f, cy + 60f), Offset(fArmHx, fArmHy), tunicColor)
                    drawCircle(Color(0xFFE8C5A4), radius = 4f, center = Offset(fArmHx, fArmHy))
                    drawCircle(ThreadColor, radius = 4f, center = Offset(fArmHx, fArmHy), style = Stroke(width = 1.5f))
                }
            }
        }
    }

    /** Shared parade-follower body: legs, tunic, heraldry. Returns the tunic colour so the arm strap matches. */
    private fun drawFollowerBody(scope: DrawScope, anc: com.example.game.Ancillary, appearanceSeed: Int, angleL: Float, angleR: Float, cx: Float, cy: Float): Color {
        with(scope) {
            // 1. Draw Legs (Walking cycle) only if not Lil Guy
            if (anc != com.example.game.Ancillary.LIL_GUY) {
                // Left leg (back)
                withTransform({
                    rotate(radToDeg(angleL), pivot = Offset(cx - 5f, cy + 90f))
                }) {
                    drawStitchedStrap(this, Offset(cx - 5f, cy + 90f), Offset(cx - 10f, cy + 140f), Color(0xFF382F22))
                    // Small shoe
                    val shoeL = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(cx - 15f, cy + 135f, cx + 2f, cy + 143f))
                    }
                    drawStitchedFill(this, shoeL, Color(0xFF1E1C1A))
                }
                // Right leg (front)
                withTransform({
                    rotate(radToDeg(angleR), pivot = Offset(cx + 5f, cy + 90f))
                }) {
                    drawStitchedStrap(this, Offset(cx + 5f, cy + 90f), Offset(cx + 10f, cy + 140f), Color(0xFF382F22))
                    // Small shoe
                    val shoeR = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(cx + 2f, cy + 135f, cx + 18f, cy + 143f))
                    }
                    drawStitchedFill(this, shoeR, Color(0xFF1E1C1A))
                }
            }

            // 2. Draw Tiny Torso / Tunic
            val tunicPalette = when (anc) {
                com.example.game.Ancillary.SQUIRE -> listOf(Color(0xFF3F7650), Color(0xFF539462), Color(0xFF6A8545))
                com.example.game.Ancillary.HERALD -> listOf(Color(0xFF8E2C32), Color(0xFFB03131), Color(0xFF7A3D54))
                com.example.game.Ancillary.TRUMPETER -> listOf(Color(0xFFB78319), Color(0xFFD6A420), Color(0xFFC47832))
                com.example.game.Ancillary.CUPBEARER -> listOf(Color(0xFF4D286D), Color(0xFF632873), Color(0xFF75405E))
                com.example.game.Ancillary.MONK -> listOf(Color(0xFF6E5942), Color(0xFF8B7355), Color(0xFF78694F))
                com.example.game.Ancillary.LIL_GUY -> listOf(Color(0xFFA86632), Color(0xFFC78440), Color(0xFF9B704A))
                else -> listOf(Color(0xFF465A63), Color(0xFF5F6E75), Color(0xFF6B6257))
            }
            val tunicColor = tunicPalette[(appearanceSeed / 3) % tunicPalette.size]
            val shoulderHalf = when (appearanceSeed % 3) {
                0 -> 13f
                1 -> 15f
                else -> 18f
            }
            val hemHalf = when ((appearanceSeed / 5) % 3) {
                0 -> 17f
                1 -> 20f
                else -> 23f
            }

            val torsoPath = Path().apply {
                moveTo(cx - shoulderHalf, cy + 45f)
                lineTo(cx + shoulderHalf, cy + 45f)
                lineTo(cx + hemHalf, cy + 95f)
                lineTo(cx - hemHalf, cy + 95f)
                close()
            }
            drawStitchedFill(this, torsoPath, tunicColor)
            drawPath(torsoPath, ThreadColor, style = Stroke(width = 2.5f))

            // If Lil Guy, draw the backpack enclosing his lower torso
            if (anc == com.example.game.Ancillary.LIL_GUY) {
                val backpack = Path().apply {
                    moveTo(cx - 23.5f, cy + 69f)
                    lineTo(cx + 22.5f, cy + 68.5f)
                    lineTo(cx + 15.5f, cy + 117.5f)
                    lineTo(cx - 13.5f, cy + 118.5f)
                    close()
                }
                drawStitchedFill(this, backpack, Color(0xFF5C4033)) // brown leather
                drawPath(backpack, ThreadColor, style = Stroke(width = 2.5f))
                // Straps going forward to the player's body
                val strapTargetX = cx + 65f
                val strapTargetY1 = cy + 50f // over shoulder
                val strapTargetY2 = cy + 90f // under arm
                drawLine(Color(0xFF382F22), Offset(cx + 20f, cy + 70f), Offset(strapTargetX - 2f, strapTargetY1 + 28f), strokeWidth = 6f)
                drawLine(Color(0xFF382F22), Offset(cx - 20f, cy + 90f), Offset(strapTargetX - 1.5f, strapTargetY2 - 2f), strokeWidth = 6f)
            }

            // Per-copy heraldry keeps duplicate followers from becoming palette-swapped clones.
            val stripeColor = Color(0xFFFAF6EB)
            if (anc != com.example.game.Ancillary.LIL_GUY) when ((appearanceSeed / 7) % 3) {
                0 -> {
                    drawLine(stripeColor, Offset(cx, cy + 45f), Offset(cx, cy + 95f), strokeWidth = 2.5f)
                    drawLine(stripeColor, Offset(cx - hemHalf + 2f, cy + 70f), Offset(cx + hemHalf - 2f, cy + 70f), strokeWidth = 2f)
                }
                1 -> {
                    drawLine(stripeColor, Offset(cx - shoulderHalf, cy + 49f), Offset(cx + hemHalf - 2f, cy + 91f), strokeWidth = 4f)
                    drawCircle(stripeColor, radius = 3f, center = Offset(cx + 5f, cy + 69f))
                }
                else -> {
                    drawLine(stripeColor, Offset(cx - hemHalf + 2f, cy + 79f), Offset(cx + hemHalf - 2f, cy + 79f), strokeWidth = 4f)
                    drawLine(stripeColor, Offset(cx - 7f, cy + 47f), Offset(cx, cy + 57f), strokeWidth = 2f)
                    drawLine(stripeColor, Offset(cx + 7f, cy + 47f), Offset(cx, cy + 57f), strokeWidth = 2f)
                }
            } else {
                drawLine(stripeColor, Offset(cx - 18f, cy + 70f), Offset(cx + 18f, cy + 70f), strokeWidth = 2f)
            }
            return tunicColor
        }
    }

    /** Parade-follower head, hair and face — seed-varied so duplicate followers look distinct. */
    private fun drawFollowerHead(scope: DrawScope, anc: com.example.game.Ancillary, appearanceSeed: Int, wrecked: Boolean, cx: Float, cy: Float) {
        with(scope) {
            val headRadius = when ((appearanceSeed / 11) % 3) {
                0 -> 12.5f
                1 -> 14f
                else -> 15.5f
            }
            val hx = cx
            val hy = cy + 25f + if (headRadius > 14f) 1f else 0f
            val faceSeed = appearanceSeed
            // The same head every other man in the game wears, scaled to the follower's size.
            // These were plain circles, which made the retinue read as a different species walking
            // beside the host. The seed picks a brow, nose and jaw exactly as it does for a Saxon.
            val headScale = headRadius / 15f

            // Hair: color and style picked from the seed
            val hairCol = if (anc == com.example.game.Ancillary.LIL_GUY) {
                Color(0xFF8B5A2B)
            } else {
                listOf(
                    Color(0xFF8B5A2B), Color(0xFF2C2219), Color(0xFFC08030), Color(0xFF888888)
                )[faceSeed % 4]
            }
            // Four proper haircuts, every one a closed filled shape — the balding style used to
            // be two stray 4px strokes, which is the "strange lines" in the parade line.
            val hairStyle = if (anc == com.example.game.Ancillary.LIL_GUY) 0 else (faceSeed / 5) % 4

            withTransform({ scale(headScale, headScale, pivot = Offset(hx, hy)) }) {
                val profile = headProfilePath(
                    hx, hy, (faceSeed / 3) % 6, (faceSeed / 7) % 6, (faceSeed / 13) % 6
                )
                drawStitchedFill(this, profile, Color(0xFFE8C5A4))
                drawStitchedOutline(profile, ThreadColor)

                // The bowl crop every style but the tonsure starts from.
                //
                // Every shape here is SOLID, not a hairline crescent. StitchedStroke is 4f wide and
                // centred on the path, so it eats 2 units inward from each edge: a 4-unit-thick
                // crescent is entirely swallowed but for a 1-unit ribbon of fill floating inside
                // the black outline, which reads as a second haircut sitting over the first.
                // Nothing here goes below ~8 units thick.
                val cap = Path().apply {
                    moveTo(hx - 13f, hy + 3f)
                    quadraticTo(hx - 15f, hy - 10f, hx - 2f, hy - 10f)
                    quadraticTo(hx + 8f, hy - 10f, hx + 10f, hy - 1f)
                    lineTo(hx + 6f, hy + 1f)
                    lineTo(hx - 10f, hy + 3f)
                    close()
                }
                if (hairStyle == 2) {
                    // A brother's tonsure: bare crown, a ring of hair round the back and a fringe.
                    val ring = Path().apply {
                        moveTo(hx - 13f, hy + 2f)
                        quadraticTo(hx - 15f, hy - 8f, hx - 7f, hy - 8f)
                        lineTo(hx - 5f, hy - 1f)
                        quadraticTo(hx - 10f, hy - 1f, hx - 9f, hy + 3f)
                        close()
                    }
                    drawStitchedFill(this, ring, hairCol)
                    drawStitchedOutline(ring, ThreadColor)
                    val fringe = Path().apply {
                        moveTo(hx + 1f, hy - 8f)
                        quadraticTo(hx + 9f, hy - 8f, hx + 11f, hy - 1f)
                        lineTo(hx + 6f, hy + 2f)
                        quadraticTo(hx + 5f, hy - 2f, hx + 1f, hy - 2f)
                        close()
                    }
                    drawStitchedFill(this, fringe, hairCol)
                    drawStitchedOutline(fringe, ThreadColor)
                } else if (hairStyle == 3) {
                    // Balding: a receding fringe, and nothing on the crown at all.
                    val fringe = Path().apply {
                        moveTo(hx - 13f, hy + 2f)
                        quadraticTo(hx - 14f, hy - 8f, hx - 4f, hy - 8f)
                        lineTo(hx - 3f, hy - 1f)
                        quadraticTo(hx - 9f, hy - 1f, hx - 8f, hy + 3f)
                        close()
                    }
                    drawStitchedFill(this, fringe, hairCol)
                    drawStitchedOutline(fringe, ThreadColor)
                } else {
                    drawStitchedFill(this, cap, hairCol)
                    drawStitchedOutline(cap, ThreadColor)
                }
                if (hairStyle == 1) {
                    // Hair down the nape, HUGGING the back of the skull.
                    //
                    // This used to be three unclosed lines behind the head. The old comment claimed
                    // leaving out close() avoided a filled triangle — but drawStitchedFill closes
                    // any open subpath implicitly, so it filled anyway, and every follower wore a
                    // spike of hair jutting out of the back of his head like a mullet.
                    val locks = Path().apply {
                        moveTo(hx - 13f, hy - 1f)
                        quadraticTo(hx - 18f, hy + 7f, hx - 13f, hy + 15f)
                        lineTo(hx - 4f, hy + 14f)
                        quadraticTo(hx - 8f, hy + 6f, hx - 5f, hy - 1f)
                        close()
                    }
                    drawStitchedFill(this, locks, hairCol)
                    drawStitchedOutline(locks, ThreadColor)
                }

                // The same embroidered eye every other figure in the game wears: an almond of
                // white, a dark pupil, a brow above it. A single flat dot was why the retinue
                // read as faceless walking beside the host.
                if (wrecked) {
                    drawLine(ThreadColor, Offset(hx + 2f, hy + 1f), Offset(hx + 8f, hy + 7f), strokeWidth = 2f)
                    drawLine(ThreadColor, Offset(hx + 8f, hy + 1f), Offset(hx + 2f, hy + 7f), strokeWidth = 2f)
                } else {
                    val sclera = Path().apply {
                        moveTo(hx + 1f, hy + 4f)
                        quadraticTo(hx + 4.5f, hy + 1f, hx + 8f, hy + 4f)
                        quadraticTo(hx + 4.5f, hy + 7f, hx + 1f, hy + 4f)
                        close()
                    }
                    drawPath(sclera, Color(0xFFF6EFE2))
                    drawPath(sclera, ThreadColor, style = Stroke(width = 1.2f))
                    drawCircle(ThreadColor, radius = 1.7f, center = Offset(hx + 5f, hy + 4f))
                    // Brow, tilted off the seed so they are not all placid.
                    val browTilt = if (faceSeed % 3 == 0) -2f else if (faceSeed % 3 == 1) 0f else 2f
                    drawLine(
                        hairCol, Offset(hx, hy - 1f), Offset(hx + 8f, hy - 1f + browTilt),
                        strokeWidth = 1.8f, cap = StrokeCap.Round
                    )
                    // A moustache on some of them, in the shape every other face now uses: back
                    // from under the nose along the lip, with the fall at the corner.
                    if ((faceSeed / 11) % 3 == 1) {
                        val tache = Path().apply {
                            moveTo(hx + 12f, hy + 10f)
                            quadraticTo(hx + 6f, hy + 10f, hx + 2f, hy + 16f)
                            quadraticTo(hx + 6f, hy + 13f, hx + 12f, hy + 13f)
                            close()
                        }
                        drawStitchedFill(this, tache, hairCol)
                        drawPath(tache, hairCol, style = Stroke(width = 0.8f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }

}
