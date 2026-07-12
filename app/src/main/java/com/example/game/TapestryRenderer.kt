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

object TapestryRenderer {

    // Style configuration
    private val ThreadColor = Color(0xFF2C2219) // Dark charcoal/brown wool thread outline
    private val StitchedStroke = Stroke(
        width = 4f, 
        cap = StrokeCap.Round, 
        join = StrokeJoin.Round
    )
    private val FillLineStroke = Stroke(
        width = 2f,
        cap = StrokeCap.Round
    )

    /**
     * Draw a character (Player Norman Knight or Saxon Enemy)
     */
    fun drawCharacter(
        drawScope: DrawScope,
        fighter: FighterState,
        scale: Float = 1.0f
    ) {
        drawScope.withTransform({
            // Apply scale (e.g. for flip/facing and overall sizing)
            val hFlip = if (fighter.facingRight) 1f else -1f
            scale(hFlip * scale * fighter.size, scale * fighter.size, pivot = Offset(fighter.posX, 200f))
        }) {
            val cx = fighter.posX
            val cy = 200f

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
            var offsetX = 0f
            var offsetY = 0f
            var scaleY = 1f
            if (fighter.isCrumpled && !fighter.isDead && !fighter.isDying) {
                // Smashed down into a squashed pancake, but still alive? Or normally it happens on death?
                // Let's make them squashed if they got a huge blunt hit
                scaleY = 0.6f
                offsetY = 30f
            }
            if (fighter.isDead || fighter.isDying) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                
                if (fighter.isMounted) {
                    rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                    offsetY = 30f * progress
                    offsetX = if (fighter.facingRight) -30f * progress else 30f * progress
                } else when (fighter.deathType) {
                    0 -> { // Fall backwards
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                    1 -> { // Faceplant forward
                        rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                        offsetY = 65f * progress
                    }
                    2 -> { // Cartwheel of death
                        rotationAngle = if (fighter.facingRight) 630f * progress else -630f * progress
                        offsetY = 65f * progress
                    }
                    3 -> { // Squashed pancake falling over
                        rotationAngle = if (fighter.facingRight) -85f * progress else 85f * progress
                        scaleY = (if (fighter.isCrumpled) 0.6f else 1f) - 0.7f * progress
                        offsetY = 65f * progress
                    }
                    4 -> { // Knocked flying backwards landing flat
                        val flyDir = if (fighter.facingRight) -1f else 1f
                        rotationAngle = flyDir * 270f * progress
                        offsetX = flyDir * 150f * progress
                        offsetY = 65f * progress - 100f * sin(progress * Math.PI).toFloat()
                    }
                    else -> { // Fall flat
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                }
            }

            if (fighter.isMounted || fighter.isChariot) {
                var horseRot = 0f
                if (fighter.isDead || fighter.isDying) {
                    val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                    horseRot = if (fighter.facingRight) -15f * progress else 15f * progress
                }
                withTransform({ 
                    rotate(horseRot, pivot = Offset(cx, cy + 80f)) 
                    val rng = kotlin.random.Random(fighter.id.hashCode())
                    val horseScale = 0.9f + rng.nextFloat() * 0.2f
                    scale(horseScale / fighter.size, horseScale / fighter.size, Offset(cx, cy + 80f))
                }) {
                    if (fighter.isChariot) {
                        drawChariot(this, cx, cy, fighter)
                    } else if (fighter.isLord) {
                        drawThrone(this, cx, cy, fighter)
                    } else if (fighter.mountHp == 40f) {
                        drawStilts(this, cx, cy, fighter)
                    } else {
                        drawHorse(this, cx, cy, fighter)
                    }
                }
            }

            // Draw blood pool and stream BEFORE the ragdoll transform so it stays flat on the floor AND underneath the body!
            if ((fighter.isDead || fighter.isDying) && fighter.deathType == 5) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                val fountainProgress = progress.coerceIn(0f, 1f)
                if (fountainProgress > 0.05f) {
                    val flyDir = if (fighter.facingRight) -1f else 1f
                    
                    // The pool is where the neck ultimately lands, but clamped to the floor level (cy+150f is feet)
                    val poolCenterX = cx + flyDir * 70f * fighter.size
                    val poolCenterY = cy + 150f 
                    
                    // 2. Draw Pool
                    val poolProgress = ((fountainProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    if (poolProgress > 0f) {
                        val poolW = 45f * poolProgress * fighter.size
                        val poolH = 15f * poolProgress * fighter.size
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
            if (fighter.isDead && fighter.deathType != 5) {
                // Time since death drives pool expansion
                val timeSinceDeath = (System.currentTimeMillis() - fighter.deathTime) / 1000f
                val progress = (timeSinceDeath * 0.5f).coerceIn(0f, 1f)
                if (progress > 0f) {
                    val poolProgress = progress
                    val groundY = cy + 155f
                    val fallOffset = if (fighter.facingRight) -50f else 50f
                    val poolW = 55f * poolProgress * fighter.size
                    val poolH = 12f * poolProgress * fighter.size
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
                val mountOffsetY = if (fighter.isChariot) -15f else if (fighter.isMounted && fighter.isLord) -20f else if (fighter.isMounted && fighter.mountHp == 40f) -45f else if (fighter.isMounted) -35f else 0f
                withTransform({ translate(0f, mountOffsetY) }) {
                    if (!fighter.isChariot) {
                        drawLegs(this, cx, cy, fighter)
                    }
                    drawTorso(this, cx, cy, fighter)
                    drawHead(this, cx, cy, fighter)
                    drawBackArmAndShield(this, cx, cy, fighter)
                    drawFrontArmAndWeapon(this, cx, cy, fighter)
                }
            }
        }
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

        // Left Leg (Back leg) - don't draw if mounted (hidden behind horse)
        if (!fighter.isMounted) {
            scope.withTransform({
                rotate(radToDeg(angleL), pivot = Offset(cx - 10f, cy + 90f))
            }) {
                drawStitchedStrap(this, Offset(cx - 10f, cy + 90f), Offset(cx - 18f, cy + 150f), legColorL)
                val bootPathL = Path().apply {
                    moveTo(cx - 20f, cy + 145f) // back heel
                    lineTo(cx - 15f, cy + 155f) // bottom heel
                    lineTo(cx + 5f, cy + 155f)  // bottom toe
                    lineTo(cx + 8f, cy + 150f)  // top toe
                    lineTo(cx - 10f, cy + 145f) // front ankle
                    close()
                }
                val hasBoots = fighter.extraArmors.any { it.id == "armor_boots" }
                val bootColorL = if (hasBoots) Color(0xFF5D666B) else Color(0xFF382F22)
                drawStitchedFill(this, bootPathL, bootColorL)
                if (hasBoots) {
                    drawLine(Color(0xFF9EA3A8), Offset(cx - 10f, cy + 145f), Offset(cx - 20f, cy + 145f), strokeWidth = 3f)
                }
            }
        }

        // Right Leg (Front leg)
        scope.withTransform({
            rotate(radToDeg(angleR), pivot = Offset(cx + 10f, cy + 90f))
        }) {
            drawStitchedStrap(this, Offset(cx + 10f, cy + 90f), Offset(cx + 18f, cy + 150f), legColorR)
            val bootPathR = Path().apply {
                moveTo(cx + 14f, cy + 145f) // back heel
                lineTo(cx + 18f, cy + 155f) // bottom heel
                lineTo(cx + 38f, cy + 155f)  // bottom toe
                lineTo(cx + 40f, cy + 150f)  // top toe
                lineTo(cx + 26f, cy + 145f) // front ankle
                close()
            }
            val hasBoots = fighter.extraArmors.any { it.id == "armor_boots" }
            val bootColorR = if (hasBoots) Color(0xFF5D666B) else Color(0xFF382F22)
            drawStitchedFill(this, bootPathR, bootColorR)
            if (hasBoots) {
                drawLine(Color(0xFF9EA3A8), Offset(cx + 14f, cy + 145f), Offset(cx + 26f, cy + 145f), strokeWidth = 3f)
            }
        }
    }

    private fun drawTorso(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val tunicPath = Path().apply {
            moveTo(cx - 25f, cy + 15f)
            lineTo(cx + 25f, cy + 15f)
            lineTo(cx + 30f, cy + 95f)
            lineTo(cx - 30f, cy + 95f)
            close()
        }

        // Color theme derived directly from the armor's designated color
        val fillCol = fighter.armor.color

        // Fill with textured stitching
        drawStitchedFill(scope, tunicPath, fillCol)

        // Add a traditional embroidered pattern trim at the bottom hem
        val hemPath = Path().apply {
            moveTo(cx - 29f, cy + 85f)
            lineTo(cx + 29f, cy + 85f)
            lineTo(cx + 30f, cy + 95f)
            lineTo(cx - 30f, cy + 95f)
            close()
        }
        drawStitchedFill(scope, hemPath, Color(0xFFB08221)) // Gold/mustard border
        scope.drawPath(hemPath, ThreadColor, style = StitchedStroke)

        // Torso outline
        scope.drawPath(tunicPath, ThreadColor, style = StitchedStroke)

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
            if (extraArmor.id in listOf("armor_gauntlets", "armor_boots", "armor_coif")) return@forEachIndexed
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
            scope.drawPath(extraTunicPath, ThreadColor, style = StitchedStroke)
            
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

            val length = 35f * proj.size
            val tailX = ax + (dirX * length)
            val tailY = ay + (dirY * length)
            
            // Stick out the back (penetration)
            val penetration = if (proj.inShield) 8f else 20f * proj.size
            val headX = ax - (dirX * penetration)
            val headY = ay - (dirY * penetration)

            // Draw shaft
            val strokeW = if (proj.type == "javelin") 6f else 4.5f
            scope.drawLine(Color(0xFF8A5E38), Offset(tailX, tailY), Offset(headX, headY), strokeWidth = strokeW)

            // Fletching (only for arrows/bolts)
            if (proj.type == "arrow" || proj.type == "bolt") {
                val fletchW = 7f
                val perpX = -dirY
                val perpY = dirX
                scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX + dirX*7f + perpX * fletchW, tailY + dirY*7f + perpY * fletchW), strokeWidth = 3f)
                scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX + dirX*7f - perpX * fletchW, tailY + dirY*7f - perpY * fletchW), strokeWidth = 3f)
            }
            
            // Projectile head sticking out the other side
            if (proj.type != "stone") {
                val headSize = if (proj.type == "javelin") 8f else 5f
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
        if (!fighter.isDead && !fighter.isDying && (fighter.missingArm || hpRatioTorso < 0.5f)) {
            val groundY = cy + 158f
            val poolAlpha = if (fighter.missingArm) 0.75f else (0.5f - hpRatioTorso).coerceIn(0f, 0.5f) * 1.5f
            scope.drawOval(
                color = Color(0x88800000).copy(alpha = poolAlpha),
                topLeft = Offset(cx - 20f, groundY - 5f),
                size = androidx.compose.ui.geometry.Size(40f, 10f)
            )
        }
    }

    private fun drawHead(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        var headOffsetX = 0f
        var headOffsetY = 0f
        var headRot = 0f

        if ((fighter.isDead || fighter.isDying) && fighter.deathType == 5) {
            val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
            // Head flies up and back
            val flyDir = if (fighter.facingRight) -1f else 1f
            headOffsetX = flyDir * 120f * progress
            headOffsetY = -150f * sin(progress * Math.PI).toFloat() + 50f * progress
            headRot = flyDir * 360f * progress * 2f
            
            // Blood fountain is now drawn in world-space in drawCharacter() so it connects properly to the pool!
        }

        scope.withTransform({
            translate(headOffsetX, headOffsetY)
            rotate(headRot, pivot = Offset(cx, cy - 25f))
        }) {
            val hx = cx
            val hy = cy - 25f

            // 1. Draw Neck
            val neckPath = Path().apply {
                moveTo(cx - 10f, cy + 15f)
                lineTo(cx - 8f, cy - 10f)
                lineTo(cx + 8f, cy - 10f)
                lineTo(cx + 10f, cy + 15f)
                close()
            }
            val skinColor = if (fighter.poisonDuration > 0f) Color(0xFF8CAF8A) else Color(0xFFE8C5A4)
        drawStitchedFill(scope, neckPath, skinColor)
        scope.drawPath(neckPath, ThreadColor, style = StitchedStroke)

        // 2. Head Profile
        val fhX = if (fighter.faceForehead == 1) 14f else if (fighter.faceForehead == 2) 4f else 8f
        val fhY = if (fighter.faceForehead == 1) -6f else -2f
        val headPath = Path().apply {
            moveTo(hx - 12f, hy)
            // Forehead
            lineTo(hx + fhX, hy + fhY)
            lineTo(hx + fhX + 1f, hy + 2f) // Brow indentation
            
            // Nose tip and nostril
            var noseTipX = hx + 23f
            var noseTipY = hy + 6f
            var nostrilX = hx + 10f
            if (fighter.faceNoseShape == 1) { // hook
                noseTipY += 4f; noseTipX -= 2f
            } else if (fighter.faceNoseShape == 2) { // bulbous
                noseTipX = hx + 16f; nostrilX = hx + 12f
            } else if (fighter.faceNoseShape == 3) { // pointy
                noseTipX = hx + 25f
            } else { // normal
                noseTipX = hx + 18f
            }
            lineTo(noseTipX, noseTipY) // Nose tip
            lineTo(nostrilX, hy + 9f) // Nostril/lip fold
            
            // Jaw / Bite
            var lipX = hx + 12f
            var chinX = hx + 9f
            if (fighter.faceBiteShape == 1) { // underbite
                lipX += 3f; chinX += 10f
            } else if (fighter.faceBiteShape == 2) { // overbite
                lipX -= 4f; chinX -= 8f
            } else if (fighter.faceBiteShape == 3) { // lantern jaw
                chinX += 14f
            }
            lineTo(lipX, hy + 13f) // Lip crease
            val chinY = if (fighter.faceBiteShape == 3) hy + 24f else hy + 18f
            lineTo(chinX, chinY) // Chin
            lineTo(hx - 12f, chinY)
            close()
        }
        drawStitchedFill(scope, headPath, skinColor)
        scope.drawPath(headPath, ThreadColor, style = StitchedStroke)

        // Face RNG for scars/eyepatches
        val faceRng = kotlin.random.Random(fighter.id.hashCode())
        val hasEyepatch = !fighter.isPlayer && faceRng.nextFloat() < 0.2f
        val hasScars = fighter.level > 1 && faceRng.nextFloat() < 0.5f
        val hasBandage = fighter.level > 3 && faceRng.nextFloat() < 0.4f
        val hasTiredEyes = faceRng.nextFloat() < 0.2f
        val numWarts = if (faceRng.nextFloat() < 0.2f) faceRng.nextInt(1, 4) else 0
        val hasBeautySpot = faceRng.nextFloat() < 0.1f
        val hasJuttingTooth = faceRng.nextFloat() < 0.15f
        val hasLongBeard = !fighter.isPlayer && faceRng.nextFloat() < 0.15f

        // 3. Embroidered eye and facial features
        if (hasEyepatch) {
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
        
        // Blocky, heavy embroidered eyebrow
        scope.drawLine(ThreadColor, Offset(hx, hy), Offset(hx + 8f, hy + 1f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        
        // Stylized mouth crease
        scope.drawLine(ThreadColor, Offset(hx + 6f, hy + 13f), Offset(hx + 11f, hy + 13f), strokeWidth = 1.5f, cap = StrokeCap.Round)

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

        // Dynamic Encounter-based Battle Scars
        val maxHp = fighter.maxHp.coerceAtLeast(1f)
        val damageTaken = (maxHp - fighter.hp).coerceAtLeast(0f)
        val dmgSeed = (damageTaken / 10f).toInt()
        val dmgRng = kotlin.random.Random(fighter.id.hashCode() + dmgSeed * 37 + fighter.stuckProjectiles.size * 13)

        if (damageTaken > 0f) {
            val numDynamicScars = dmgRng.nextInt(1, 4)
            for (i in 0 until numDynamicScars) {
                val scarType = dmgRng.nextInt(6)
                when (scarType) {
                    0, 1 -> { // Cut with optional stitches
                        val sx = hx - 5f + dmgRng.nextFloat() * 15f
                        val sy = hy + 2f + dmgRng.nextFloat() * 12f
                        scope.drawLine(Color(0xFF8B0000), Offset(sx, sy), Offset(sx + 8f, sy + 4f), strokeWidth = 2f)
                        if (dmgRng.nextBoolean()) {
                            scope.drawLine(ThreadColor, Offset(sx + 2f, sy), Offset(sx + 2f, sy + 6f), strokeWidth = 1f)
                            scope.drawLine(ThreadColor, Offset(sx + 5f, sy + 1f), Offset(sx + 5f, sy + 7f), strokeWidth = 1f)
                        }
                    }
                    2 -> { // Angled Bandage
                        val by = hy - 4f + dmgRng.nextFloat() * 14f
                        val ang = -15f + dmgRng.nextFloat() * 30f
                        scope.withTransform({ rotate(ang, pivot = Offset(hx, by)) }) {
                            scope.drawRect(Color(0xFFF5F0E8), topLeft = Offset(hx - 12f, by - 3f), size = androidx.compose.ui.geometry.Size(24f, 6f))
                            scope.drawLine(Color(0xFFA8906A), Offset(hx - 4f, by - 3f), Offset(hx - 4f, by + 3f), strokeWidth = 1.5f)
                            scope.drawLine(Color(0xFFA8906A), Offset(hx + 4f, by - 3f), Offset(hx + 4f, by + 3f), strokeWidth = 1.5f)
                        }
                    }
                    3 -> { // Eyepatch
                        scope.drawPath(Path().apply {
                            moveTo(hx - 1f, hy + 2f)
                            quadraticTo(hx + 4f, hy, hx + 10f, hy + 4f)
                            quadraticTo(hx + 8f, hy + 10f, hx, hy + 7f)
                            close()
                        }, Color(0xFF1E1A17))
                        scope.drawLine(Color(0xFF1E1A17), Offset(hx - 10f, hy - 2f), Offset(hx + 12f, hy + 8f), strokeWidth = 2.5f)
                    }
                    4 -> { // Puffy/Tears Eye
                        val puffPath = androidx.compose.ui.graphics.Path().apply {
                            addArc(androidx.compose.ui.geometry.Rect(hx + 1f, hy + 3f, hx + 11f, hy + 10f), 0f, 180f)
                        }
                        scope.drawPath(puffPath, Color(0xFFD4624A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }
                    5 -> { // Blood smear
                         scope.drawCircle(Color(0x888B0000), radius = 3f + dmgRng.nextFloat() * 4f, center = Offset(hx + dmgRng.nextFloat() * 10f, hy + 5f + dmgRng.nextFloat() * 10f))
                    }
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
            scope.drawPath(hairPath, ThreadColor, style = StitchedStroke)

            // Draw facial hair
            val mustache = Path().apply {
                if (hasLongBeard) {
                    moveTo(hx + 4f, hy + 14f)
                    lineTo(hx + 12f, hy + 14f)
                    lineTo(hx + 8f, hy + 40f + faceRng.nextFloat() * 20f) // Long wizard beard!
                    lineTo(hx - 8f, hy + 35f)
                    lineTo(hx - 12f, hy + 18f)
                    close()
                } else if (fighter.faceMustache == 0) { // Handlebar
                    moveTo(hx + 10f, hy + 10f)
                    quadraticTo(hx + 16f, hy + 9f, hx + 18f, hy + 13f)
                } else if (fighter.faceMustache == 1) { // Drooping
                    moveTo(hx + 10f, hy + 11f)
                    quadraticTo(hx + 14f, hy + 14f, hx + 12f, hy + 18f)
                } else if (fighter.faceMustache == 2) { // Bushy
                    moveTo(hx + 8f, hy + 11f)
                    lineTo(hx + 18f, hy + 12f)
                    lineTo(hx + 14f, hy + 15f)
                    close()
                } else { // Classic Norman chevron
                    moveTo(hx + 10f, hy + 10f)
                    lineTo(hx + 16f, hy + 11f)
                    lineTo(hx + 14f, hy + 14f)
                    close()
                }
            }
            if (hasLongBeard || fighter.faceMustache == 2) {
                drawStitchedFill(scope, mustache, fighter.hairColor)
                scope.drawPath(mustache, ThreadColor, style = StitchedStroke)
            } else {
                scope.drawPath(mustache, fighter.hairColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            }

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
            scope.drawPath(coifPath, ThreadColor, style = StitchedStroke)
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
            scope.drawPath(helmPath, ThreadColor, style = StitchedStroke)

            // Nasal Guard (iron strip protecting the nose)
            val nasalPath = Path().apply {
                moveTo(hx + 9f, hy)
                lineTo(hx + 11f, hy + 14f)
                lineTo(hx + 6f, hy + 14f)
                lineTo(hx + 6f, hy)
                close()
            }
            drawStitchedFill(scope, nasalPath, Color(0xFF727A80))
            scope.drawPath(nasalPath, ThreadColor, style = StitchedStroke)
        } else if (helmId == "helm_cervelliere") {
            val skullCap = Path().apply {
                moveTo(hx - 14f, hy)
                quadraticTo(hx, hy - 20f, hx + 14f, hy)
                close()
            }
            drawStitchedFill(scope, skullCap, Color(0xFF9EA3A8))
            scope.drawPath(skullCap, ThreadColor, style = StitchedStroke)
        } else if (helmId == "helm_spangen") {
            val spangen = Path().apply {
                moveTo(hx - 16f, hy + 2f)
                lineTo(hx - 16f, hy - 4f)
                quadraticTo(hx, hy - 24f, hx + 16f, hy - 4f)
                lineTo(hx + 16f, hy + 2f)
                close()
            }
            drawStitchedFill(scope, spangen, Color(0xFF7A8389))
            scope.drawPath(spangen, ThreadColor, style = StitchedStroke)
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
            scope.drawPath(kettle, ThreadColor, style = StitchedStroke)
            // Wide brim
            val brim = Path().apply {
                moveTo(hx - 22f, hy)
                quadraticTo(hx, hy - 6f, hx + 22f, hy)
                lineTo(hx + 20f, hy + 4f)
                quadraticTo(hx, hy - 2f, hx - 20f, hy + 4f)
                close()
            }
            drawStitchedFill(scope, brim, Color(0xFF8B9298))
            scope.drawPath(brim, ThreadColor, style = StitchedStroke)
        } else if (helmId == "helm_mask") {
            val mask = Path().apply {
                moveTo(hx - 16f, hy - 20f)
                quadraticTo(hx, hy - 26f, hx + 16f, hy - 20f)
                lineTo(hx + 18f, hy + 18f)
                quadraticTo(hx, hy + 22f, hx - 16f, hy + 18f)
                close()
            }
            drawStitchedFill(scope, mask, Color(0xFF7B858B))
            scope.drawPath(mask, ThreadColor, style = StitchedStroke)
            // Eye slits
            scope.drawLine(Color(0xFF222222), Offset(hx + 2f, hy - 2f), Offset(hx + 14f, hy + 2f), strokeWidth = 2.5f)
            scope.drawLine(Color(0xFF222222), Offset(hx - 12f, hy + 2f), Offset(hx - 2f, hy - 2f), strokeWidth = 2.5f)
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
            scope.drawPath(bucketPath, ThreadColor, style = StitchedStroke)
            
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
            scope.drawPath(hatPath, ThreadColor, style = StitchedStroke)
            // Bells
            scope.drawCircle(Color(0xFFFFD700), radius = 4f, center = Offset(hx - 30f, hy - 10f))
            scope.drawCircle(Color(0xFFFFD700), radius = 4f, center = Offset(hx, hy - 30f))
            scope.drawCircle(Color(0xFFFFD700), radius = 4f, center = Offset(hx + 30f, hy - 10f))
        }

        if (fighter.isLord) {
            val crownPath = Path().apply {
                moveTo(hx - 12f, hy - 10f)
                lineTo(hx - 15f, hy - 25f)
                lineTo(hx - 5f, hy - 15f)
                lineTo(hx, hy - 30f)
                lineTo(hx + 5f, hy - 15f)
                lineTo(hx + 15f, hy - 25f)
                lineTo(hx + 12f, hy - 10f)
                close()
            }
            drawStitchedFill(scope, crownPath, Color(0xFFFFD700)) // Gold
            scope.drawPath(crownPath, ThreadColor, style = StitchedStroke)
            // Jewels
            scope.drawCircle(Color(0xFFBF3030), radius = 2f, center = Offset(hx - 10f, hy - 15f))
            scope.drawCircle(Color(0xFF305ABF), radius = 2f, center = Offset(hx, hy - 20f))
            scope.drawCircle(Color(0xFF30BF5A), radius = 2f, center = Offset(hx + 10f, hy - 15f))
        }
        } // Close withTransform
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
            "head_pike", "head_spear" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y)
                    lineTo(headPos.x + 18f, headPos.y - 12f)
                    lineTo(headPos.x + 35f, headPos.y - 15f) // point
                    lineTo(headPos.x + 22f, headPos.y - 3f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
            }
            "head_axe" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y - 5f)
                    lineTo(headPos.x + 15f, headPos.y - 20f)
                    quadraticTo(headPos.x + 28f, headPos.y - 5f, headPos.x + 18f, headPos.y + 25f) // bearded curved edge
                    quadraticTo(headPos.x + 10f, headPos.y + 15f, headPos.x, headPos.y + 15f) // hollow of the beard back to haft
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
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
                val macePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x - 5f, headPos.y - 5f)
                    lineTo(headPos.x + 15f, headPos.y - 12f)
                    lineTo(headPos.x + 20f, headPos.y - 5f)
                    lineTo(headPos.x + 20f, headPos.y + 5f)
                    lineTo(headPos.x + 15f, headPos.y + 12f)
                    lineTo(headPos.x - 5f, headPos.y + 5f)
                    close()
                }
                scope.drawPath(macePath, headColor)
                scope.drawPath(macePath, ThreadColor, style = StitchedStroke)
                // Flanges
                scope.drawLine(ThreadColor, Offset(headPos.x, headPos.y - 8f), Offset(headPos.x + 18f, headPos.y - 2f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, Offset(headPos.x, headPos.y + 8f), Offset(headPos.x + 18f, headPos.y + 2f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, Offset(headPos.x - 2f, headPos.y), Offset(headPos.x + 20f, headPos.y), strokeWidth = 2.5f)
            }
            "head_broadsword" -> {
                val bladeEnd = Offset(headPos.x + 40f, headPos.y - 15f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y - 6f)
                    lineTo(bladeEnd.x, bladeEnd.y - 6f)
                    lineTo(bladeEnd.x + 10f, bladeEnd.y) // tip
                    lineTo(bladeEnd.x, bladeEnd.y + 6f)
                    lineTo(headPos.x, headPos.y + 6f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
                // Fullers
                scope.drawLine(ThreadColor, headPos, bladeEnd, strokeWidth = 1.5f)
                
                // Crossguard
                scope.drawLine(Color(0xFF8A5E38), Offset(headPos.x - 8f, headPos.y - 14f), Offset(headPos.x + 8f, headPos.y + 14f), strokeWidth = 5f)
            }
            "head_dagger_hilt" -> {
                // Large iron pommel strike
                scope.drawCircle(headColor, radius = 9f, center = Offset(headPos.x + 5f, headPos.y - 2f))
                scope.drawCircle(ThreadColor, radius = 9f, center = Offset(headPos.x + 5f, headPos.y - 2f), style = StitchedStroke)
                // Small crossguard
                scope.drawLine(Color(0xFFCFB53B), Offset(headPos.x, headPos.y - 8f), Offset(headPos.x, headPos.y + 8f), strokeWidth = 4f)
            }
            "head_pitchfork" -> {
                // Base bar
                scope.drawLine(headColor, Offset(headPos.x + 2f, headPos.y - 10f), Offset(headPos.x + 2f, headPos.y + 10f), strokeWidth = 4f)
                // Three tines
                val tineLen = 25f
                scope.drawLine(headColor, Offset(headPos.x + 2f, headPos.y - 10f), Offset(headPos.x + 2f + tineLen, headPos.y - 12f), strokeWidth = 3f)
                scope.drawLine(headColor, Offset(headPos.x + 2f, headPos.y), Offset(headPos.x + 2f + tineLen + 5f, headPos.y), strokeWidth = 3f)
                scope.drawLine(headColor, Offset(headPos.x + 2f, headPos.y + 10f), Offset(headPos.x + 2f + tineLen, headPos.y + 12f), strokeWidth = 3f)
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
                scope.drawLine(headColor, headPos, androidx.compose.ui.geometry.Offset(headPos.x + 10f, headPos.y - 15f), strokeWidth = 4f)
                scope.drawLine(headColor, headPos, androidx.compose.ui.geometry.Offset(headPos.x + 10f, headPos.y + 15f), strokeWidth = 4f)
                scope.drawCircle(ThreadColor, radius = 3f, center = headPos)
            }
            "head_longbow" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x - 18f, headPos.y - 45f)
                    quadraticTo(headPos.x + 25f, headPos.y, headPos.x - 18f, headPos.y + 45f)
                }
                scope.drawPath(path, headColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x - 18f, headPos.y - 45f), androidx.compose.ui.geometry.Offset(headPos.x - 18f, headPos.y + 45f), strokeWidth = 2f)
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
                        val spikeAngle = chainAngle + i * (Math.PI * 2.0 / 6.0).toFloat() + animFrame // rotate slightly
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
                    quadraticTo(headPos.x + 20f, headPos.y - 40f, headPos.x + 40f, headPos.y - 20f) // outer curve
                    quadraticTo(headPos.x + 15f, headPos.y - 20f, headPos.x, headPos.y + 5f) // inner curve
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
                val bowPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x + 20f, headPos.y - 35f)
                    quadraticTo(headPos.x + 35f, headPos.y - 15f, headPos.x + 40f, headPos.y - 5f)
                }
                scope.drawPath(bowPath, androidx.compose.ui.graphics.Color.DarkGray, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
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
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
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
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
                // Small spikes on hammer back
                scope.drawLine(ThreadColor, androidx.compose.ui.geometry.Offset(headPos.x + 8f, headPos.y + 16f), androidx.compose.ui.geometry.Offset(headPos.x + 8f, headPos.y + 22f), strokeWidth = 2f)
                scope.drawLine(ThreadColor, androidx.compose.ui.geometry.Offset(headPos.x + 12f, headPos.y + 13f), androidx.compose.ui.geometry.Offset(headPos.x + 16f, headPos.y + 18f), strokeWidth = 2f)
            }
            "head_saber" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y - 10f)
                    quadraticTo(headPos.x + 60f, headPos.y - 20f, headPos.x + 80f, headPos.y + 10f) // swept back curve
                    quadraticTo(headPos.x + 50f, headPos.y, headPos.x + 5f, headPos.y + 5f) // inner edge
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
                // Crossguard
                scope.drawLine(androidx.compose.ui.graphics.Color(0xFFCFB53B), androidx.compose.ui.geometry.Offset(headPos.x - 5f, headPos.y - 12f), androidx.compose.ui.geometry.Offset(headPos.x + 10f, headPos.y + 15f), strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
                    lineTo(headPos.x + 18f, headPos.y - 28f) // top wing spike
                    quadraticTo(headPos.x + 35f, headPos.y - 5f, headPos.x + 22f, headPos.y + 25f) // axe face
                    quadraticTo(headPos.x + 10f, headPos.y + 15f, headPos.x, headPos.y + 15f) 
                    // back wing spike
                    lineTo(headPos.x - 12f, headPos.y + 5f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
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
                    moveTo(headPos.x - 5f, headPos.y - 8f)
                    lineTo(headPos.x + 35f, headPos.y - 14f)
                    lineTo(headPos.x + 38f, headPos.y + 14f)
                    lineTo(headPos.x - 5f, headPos.y + 8f)
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
                // Draw some wood grain lines
                scope.drawLine(Color(0xFF3E2723), Offset(headPos.x + 5f, headPos.y - 2f), Offset(headPos.x + 30f, headPos.y - 4f), strokeWidth = 2f)
                scope.drawLine(Color(0xFF3E2723), Offset(headPos.x + 8f, headPos.y + 3f), Offset(headPos.x + 25f, headPos.y + 5f), strokeWidth = 2f)
            }
            "head_saw_1", "head_saw_2" -> {
                val isHeavy = headId == "head_saw_2"
                val length = if (isHeavy) 55f else 45f
                val height = if (isHeavy) 22f else 12f
                val teethCount = if (isHeavy) 6 else 12
                
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headPos.x, headPos.y - height / 2)
                    lineTo(headPos.x + length, headPos.y - height / 4) // blunt top edge tapers down
                    
                    // Saw teeth on bottom edge (right to left)
                    for(i in teethCount downTo 1) {
                        val fraction = i / teethCount.toFloat()
                        val nextFraction = (i - 1) / teethCount.toFloat()
                        val tx = headPos.x + length * fraction
                        val nx = headPos.x + length * nextFraction
                        lineTo(tx, headPos.y + height / 2) // tooth point down
                        lineTo(nx + (tx - nx) / 2, headPos.y) // valley up
                    }
                    close()
                }
                scope.drawPath(path, headColor)
                scope.drawPath(path, ThreadColor, style = StitchedStroke)
            }
        }
    }

    private fun drawWeapon(scope: DrawScope, hx: Float, hy: Float, fighter: FighterState, armAngle: Float = 0f) {
        if (fighter.weaponHead.id == "head_bare" && fighter.weaponHandle.id == "handle_fists") return
        
        val isBowOrSlingshot = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")

        // Determine direction vector of the handle/pole (visually lengthen based on haft extensions)
        val handleLen = if (isBowOrSlingshot) {
            0f
        } else {
            val baseLen = when (fighter.weaponHandle.id) {
                "handle_long" -> 110f
                "handle_medium" -> 55f
                "handle_chain", "handle_flail_chain" -> 60f
                "handle_double_ended" -> 80f
                "handle_blessed_branch" -> 70f
                else -> 30f // short, iron, wheel, pick, fists
            }
            baseLen + fighter.handleExtensionCount * 25f
        }

        // Handle shaft (wooden)
        val shaftEnd = androidx.compose.ui.geometry.Offset(hx + handleLen * 0.8f, hy - handleLen * 0.4f)
        var headPos = shaftEnd
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
                scope.drawCircle(fighter.weaponHandle.color, radius = 22f, center = wheelCenter)
                scope.drawCircle(ThreadColor, radius = 22f, center = wheelCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                scope.drawCircle(androidx.compose.ui.graphics.Color(0xFFE4D6B6), radius = 18f, center = wheelCenter)
                for (i in 0 until 4) {
                    val angle = (i * Math.PI / 4).toFloat()
                    val p1 = androidx.compose.ui.geometry.Offset(wheelCenter.x + kotlin.math.cos(angle)*18f, wheelCenter.y + kotlin.math.sin(angle)*18f)
                    val p2 = androidx.compose.ui.geometry.Offset(wheelCenter.x - kotlin.math.cos(angle)*18f, wheelCenter.y - kotlin.math.sin(angle)*18f)
                    scope.drawLine(fighter.weaponHandle.color, p1, p2, strokeWidth = 3f)
                }
            } else if (fighter.weaponHandle.id == "handle_blessed_branch") {
                scope.withTransform({ translate(hx, hy) }) {
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
                    drawPath(path_0, ThreadColor, style = StitchedStroke)
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
                    drawPath(path_1, ThreadColor, style = StitchedStroke)
                    val path_2 = Path().apply {
                        moveTo(40f, -40f)
                        lineTo(40f, -50f)
                        lineTo(30f, -50f)
                        lineTo(30f, -40f)
                        lineTo(40f, -40f)
                        close()
                    }
                    drawStitchedFill(this, path_2, Color(0xFF8A5E38))
                    drawPath(path_2, ThreadColor, style = StitchedStroke)
                }
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
        val isChain = fighter.weaponHandle.id == "handle_chain"
        val dangleAngle = if (isChain) {
            val swing = fighter.swingProgress
            when {
                fighter.isAttacking -> 90f - swing * 360f
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
                // Pick a parent node to attach to (branching)
                val parentNodeIdx = (attachment.id.hashCode() % attachmentPoints.size + attachmentPoints.size) % attachmentPoints.size
                val attachPos = attachmentPoints[parentNodeIdx]

                // Randomized organic angles
                val deterministicOffset = (attachment.id.hashCode() % 360).toFloat()
                val attachAngle = deterministicOffset

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
        val armAngle = if (fighter.isDead || fighter.isDying) {
            if (fighter.isDying) {
                sin(fighter.animFrame * 1.5f) * 85f
            } else {
                45f
            }
        } else if (fighter.isAttacking) {
            if (fighter.weaponHandle.id == "handle_double_ended") {
                -360f * swing
            } else if (isSaw) {
                val sawExt = kotlin.math.sin(swing * Math.PI * 5).toFloat()
                thrustOffset = Offset(30f * sawExt, 5f * sawExt)
                -10f + 15f * sawExt
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
        } else {
            val postureOffset = if (isBowOrSlingshot) -15f else when (Math.abs(fighter.name.hashCode()) % 6) {
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
                val sleeveColor = if (fighter.isPlayer) Color(0xFF265063) else Color(0xFF9E3624)
                drawStitchedStrap(this, Offset(cx - 23f, cy + 25f), Offset(cx + 4f, cy + 28f), sleeveColor)
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
            val sleeveColor = if (fighter.isPlayer) Color(0xFF265063) else Color(0xFF9E3624)
            drawStitchedStrap(this, Offset(cx - 23f, cy + 25f), Offset(cx + 25f, cy + 30f), sleeveColor)
            
            // Hand
            val hasGauntlets = fighter.extraArmors.any { it.id == "armor_gauntlets" }
            if (hasGauntlets) {
                scope.drawRect(Color(0xFF6B747A), topLeft = Offset(cx + 19f, cy + 24f), size = androidx.compose.ui.geometry.Size(12f, 12f))
                scope.drawRect(ThreadColor, topLeft = Offset(cx + 19f, cy + 24f), size = androidx.compose.ui.geometry.Size(12f, 12f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            } else {
                scope.drawCircle(if (fighter.poisonDuration > 0f) Color(0xFF8CAF8A) else Color(0xFFE8C5A4), radius = 6f, center = Offset(cx + 25f, cy + 30f))
                scope.drawCircle(ThreadColor, radius = 6f, center = Offset(cx + 25f, cy + 30f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
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
                val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                drawStitchedStrap(this, Offset(cx + 23f, cy + 25f), Offset(cx + 20f, cy + 35f), sleeveColor)
                scope.drawCircle(Color(0xFF9E3624), radius = 7f, center = Offset(cx + 20f, cy + 35f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 3.5f, center = Offset(cx + 20f, cy + 35f))
            }
            return
        }

        val sColor = fighter.shield.color
        val hasKite = fighter.shield.id == "shield_kite"
        val hasTower = fighter.shield.id == "shield_tower"

        if (fighter.shield.id == "shield_none") {
            if (fighter.isDualWielding) {
                val swing = fighter.swingProgress
                val isChainHandle = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
                val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe", "head_lucerne", "head_saber")
                
                val armAngle = if (fighter.isDead || fighter.isDying) {
                    if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
                } else if (fighter.isAttacking) {
                    if (isChainHandle || isHeavy) {
                        if (swing < 0.5f) -75f * (swing / 0.5f) else -75f + 160f * ((swing - 0.5f) / 0.5f)
                    } else {
                        if (swing < 0.5f) -20f + 80f * (swing / 0.5f) else 60f - 80f * ((swing - 0.5f) / 0.5f)
                    }
                } else {
                    val posture = if (fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot", "head_crossbow")) -15f else when (Math.abs(fighter.name.hashCode()) % 3) {
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
                    val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                    drawStitchedStrap(this, Offset(cx + 23f, cy + 25f), Offset(hx, hy), sleeveColor)
                    scope.drawCircle(if (fighter.poisonDuration > 0f) Color(0xFF8CAF8A) else Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
                    scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                    
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
                    } else {
                        val isBowOrSling = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")
                        val posture = if (isBowOrSling) 0f else when (Math.abs(fighter.name.hashCode()) % 3) {
                            0 -> -75f
                            1 -> -35f
                            else -> 15f
                        }
                        posture - sin(fighter.animFrame * 0.5f) * 10f
                    }
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
                    
                    val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                    drawStitchedStrap(this, Offset(cx + 23f, cy + 25f), Offset(hx, hy), sleeveColor)
                    scope.drawCircle(if (fighter.poisonDuration > 0f) Color(0xFF8CAF8A) else Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
                    scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                }
            }
            return
        }

        scope.withTransform({
            rotate(shieldArmAngle, pivot = Offset(cx + 15f, cy + 25f))
        }) {
            val hx = cx + 18f
            val hy = cy + 45f
            val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
            drawStitchedStrap(this, Offset(cx + 15f, cy + 25f), Offset(hx, hy), sleeveColor)
            scope.drawCircle(if (fighter.poisonDuration > 0f) Color(0xFF8CAF8A) else Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
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
            drawPath(shieldPath, ThreadColor, style = StitchedStroke)
            
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
                drawPath(helmPath, ThreadColor, style = StitchedStroke)
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
    private fun drawStitchedFill(scope: DrawScope, path: Path, color: Color) {
        // Base fill color (solid but soft)
        scope.drawPath(path, color)

        // Add dense parallel lines to look like embroidered thread couching
        scope.withTransform({
            clipPath(path)
        }) {
            val bounds = path.getBounds()
            var y = bounds.top
            while (y < bounds.bottom) {
                // Horizontal couching threads with slight zigzag/noise for wool texture
                val startX = bounds.left - 5f
                val endX = bounds.right + 5f
                
                // Draw multiple segmented lines with slight vertical jitter
                var currX = startX
                val segmentLen = 8f
                while(currX < endX) {
                    val nextX = currX + segmentLen
                    val jitterY = y + (Math.random().toFloat() * 1.5f - 0.75f)
                    val nextJitterY = y + (Math.random().toFloat() * 1.5f - 0.75f)
                    
                    scope.drawLine(
                        color = color.copy(alpha = 0.35f), // subtle variance for thread definition
                        start = Offset(currX, jitterY),
                        end = Offset(nextX, nextJitterY),
                        strokeWidth = 1.5f,
                        cap = StrokeCap.Round
                    )
                    currX += segmentLen
                }
                
                // Vertical anchor stitches (every few pixels)
                var vx = bounds.left
                while (vx < bounds.right) {
                    if (Math.random() > 0.8) {
                        scope.drawLine(
                            color = ThreadColor.copy(alpha = 0.15f),
                            start = Offset(vx, y - 2f),
                            end = Offset(vx, y + 2f),
                            strokeWidth = 1f
                        )
                    }
                    vx += 4f
                }
                y += 3.5f
            }
        }
    }

    private fun drawStitchedStrap(scope: DrawScope, from: Offset, to: Offset, color: Color) {
        // Draw outline underneath
        scope.drawLine(color = ThreadColor, start = from, end = to, strokeWidth = 17f, cap = StrokeCap.Round)
        // Draw fill on top
        scope.drawLine(color = color, start = from, end = to, strokeWidth = 13f, cap = StrokeCap.Round)
        // Draw embroidery texture lines along the strap
        scope.drawLine(color = color.copy(alpha = 0.4f), start = from, end = to, strokeWidth = 13f, cap = StrokeCap.Round, 
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 2f), 0f))
        scope.drawLine(color = ThreadColor.copy(alpha=0.2f), start = from, end = to, strokeWidth = 3f, cap = StrokeCap.Round, 
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f))
    }

    fun drawAncillaries(
        drawScope: DrawScope,
        unlockedAncillaries: Set<com.example.game.Ancillary>,
        playerFighter: FighterState,
        scale: Float = 1.0f
    ) {
        val sortedAncs = unlockedAncillaries.toList().filter { it != com.example.game.Ancillary.WARHORSE && it != com.example.game.Ancillary.CHARIOT && it != com.example.game.Ancillary.STILTS }.sortedBy { it.name }
        if (sortedAncs.isEmpty()) return

        val normScale = scale * playerFighter.size

        sortedAncs.forEachIndexed { index, anc ->
            val offsetSign = if (playerFighter.facingRight) -1f else 1f
            
            var dynamicWalkOffset = 0f
            if (anc == com.example.game.Ancillary.CUPBEARER && playerFighter.hp < playerFighter.maxHp) {
                val attackAnim = if (playerFighter.isAttacking) playerFighter.swingProgress else 0f
                dynamicWalkOffset = -60f * offsetSign * sin(attackAnim * Math.PI).toFloat()
            }
            
            // Each follower stands behind the player
            val baseOffsetX = if (anc == com.example.game.Ancillary.LIL_GUY) {
                15f * offsetSign // Piggyback position!
            } else {
                (index + 1) * 110f * offsetSign + dynamicWalkOffset
            }
            val cx = playerFighter.posX + baseOffsetX
            val cy = if (anc == com.example.game.Ancillary.LIL_GUY) 110f else 200f
            val finalScale = if (anc == com.example.game.Ancillary.LIL_GUY) normScale * 0.45f else normScale

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
                // 1. Draw Legs (Walking cycle)
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

                // 2. Draw Tiny Torso / Tunic
                val tunicColor = when (anc) {
                    com.example.game.Ancillary.SQUIRE -> Color(0xFF539462) // Baldrick's green
                    com.example.game.Ancillary.HERALD -> Color(0xFFB03131) // Herald's red
                    com.example.game.Ancillary.TRUMPETER -> Color(0xFFD6A420) // Trumpeter's gold
                    com.example.game.Ancillary.CUPBEARER -> Color(0xFF632873) // Cupbearer's violet
                    com.example.game.Ancillary.MONK -> Color(0xFF8B7355) // Hessian sack
                    com.example.game.Ancillary.LIL_GUY -> Color(0xFFC78440)
                    else -> Color(0xFF5F6E75)
                }

                val torsoPath = Path().apply {
                    moveTo(cx - 15f, cy + 45f)
                    lineTo(cx + 15f, cy + 45f)
                    lineTo(cx + 20f, cy + 95f)
                    lineTo(cx - 20f, cy + 95f)
                    close()
                }
                drawStitchedFill(this, torsoPath, tunicColor)
                drawPath(torsoPath, ThreadColor, style = Stroke(width = 2.5f))

                // Decorate the tunic with embroidery
                val stripeColor = Color(0xFFFAF6EB)
                drawLine(stripeColor, Offset(cx, cy + 45f), Offset(cx, cy + 95f), strokeWidth = 2.5f)
                drawLine(stripeColor, Offset(cx - 18f, cy + 70f), Offset(cx + 18f, cy + 70f), strokeWidth = 2f)

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
                        // Drawing spare folded tunics on his shoulder!
                        val tunicStack = Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                rect = androidx.compose.ui.geometry.Rect(cx - 22f, cy + 25f, cx - 2f, cy + 45f),
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

                // 4. Draw Head
                val headRadius = 14f
                val hx = cx
                val hy = cy + 25f
                drawCircle(Color(0xFFE8C5A4), radius = headRadius, center = Offset(hx, hy))
                drawCircle(ThreadColor, radius = headRadius, center = Offset(hx, hy), style = Stroke(width = 2.5f))

                // Funny medieval hair/cap
                val capPath = Path().apply {
                    addArc(androidx.compose.ui.geometry.Rect(hx - headRadius, hy - headRadius, hx + headRadius, hy), 180f, 180f)
                }
                drawStitchedFill(this, capPath, Color(0xFF8B5A2B))
                drawPath(capPath, ThreadColor, style = Stroke(width = 2f))

                // Ridiculous eyes: "X" eyes if dead/dying, otherwise standard side-facing dot eye
                if (playerFighter.isDead || playerFighter.isDying) {
                    // Left X
                    drawLine(Color(0xFF382F22), Offset(hx + 3f, hy - 4f), Offset(hx + 9f, hy + 2f), strokeWidth = 2f)
                    drawLine(Color(0xFF382F22), Offset(hx + 9f, hy - 4f), Offset(hx + 3f, hy + 2f), strokeWidth = 2f)
                } else {
                    drawCircle(Color(0xFF382F22), radius = 2.5f, center = Offset(hx + 6f, hy - 2f))
                }

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

    private fun drawChainmailTexture(scope: DrawScope, x: Float, y: Float, width: Float, height: Float) {
        // Draw tiny ring rows
        var currY = y
        while (currY < y + height) {
            var currX = x
            while (currX < x + width) {
                scope.drawCircle(
                    color = ThreadColor.copy(alpha = 0.35f),
                    radius = 3f,
                    center = Offset(currX + (currY % 6), currY),
                    style = Stroke(width = 1f)
                )
                currX += 7f
            }
            currY += 5f
        }
    }

    private fun drawGambesonTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
        // Draw diagonal quilted stitch lines
        scope.drawLine(ThreadColor.copy(alpha = 0.25f), Offset(cx - width/2, cy), Offset(cx + width/2, cy + height), strokeWidth = 1.5f)
        scope.drawLine(ThreadColor.copy(alpha = 0.25f), Offset(cx + width/2, cy), Offset(cx - width/2, cy + height), strokeWidth = 1.5f)
    }

    private fun drawScaleTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
        var currY = cy + 5f
        while (currY < cy + height - 5f) {
            var currX = cx - width / 2 + 5f
            while (currX < cx + width / 2 - 5f) {
                scope.drawArc(
                    color = ThreadColor.copy(alpha = 0.4f),
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(currX, currY), size = androidx.compose.ui.geometry.Size(6f, 6f),
                    style = Stroke(width = 1.2f)
                )
                currX += 6f
            }
            currY += 4f
        }
    }

    private fun drawLamellarTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
        var currY = cy + 5f
        while (currY < cy + height - 5f) {
            var currX = cx - width / 2 + 5f
            while (currX < cx + width / 2 - 5f) {
                scope.drawRect(
                    color = ThreadColor.copy(alpha = 0.4f),
                    topLeft = Offset(currX, currY), size = androidx.compose.ui.geometry.Size(4f, 6f),
                    style = Stroke(width = 1f)
                )
                currX += 5f
            }
            currY += 7f
        }
    }

    private fun drawFurTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
        val rng = kotlin.random.Random((cx + cy).toInt())
        for (i in 0..20) {
            val sx = cx - width / 2 + rng.nextFloat() * width
            val sy = cy + rng.nextFloat() * height
            scope.drawLine(
                color = Color(0xFF382F22).copy(alpha = 0.5f),
                start = Offset(sx, sy),
                end = Offset(sx + rng.nextFloat() * 4f - 2f, sy + 4f + rng.nextFloat() * 4f),
                strokeWidth = 1.5f
            )
        }
    }

    private fun drawHorse(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val anim = fighter.animFrame
        val walking = !fighter.isDead && !fighter.isDying
        val legSwing = if (walking) sin(anim) * 18f else 0f
        
        val rng = kotlin.random.Random(fighter.id.hashCode())
        val horseColors = listOf(Color(0xFF6B4F2E), Color(0xFF8B7355), Color(0xFF2C2219), Color(0xFFE4D6B6), Color(0xFF4A4A4A))
        val horseColor = horseColors[rng.nextInt(horseColors.size)]
        val hasSpots = rng.nextFloat() > 0.7f
        val legColor   = Color(0xFF5A3F22)

        val bodyPath = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(cx - 55f, cy + 60f, cx + 55f, cy + 130f))
        }
        drawStitchedFill(scope, bodyPath, horseColor)
        
        if (hasSpots) {
            val spotRng = kotlin.random.Random(fighter.id.hashCode() + 1)
            for (i in 0 until 5) {
                val sx = cx - 40f + spotRng.nextFloat() * 80f
                val sy = cy + 70f + spotRng.nextFloat() * 40f
                scope.drawCircle(Color(0xFFEFE6D4).copy(alpha = 0.5f), radius = 6f + spotRng.nextFloat() * 4f, center = Offset(sx, sy))
            }
        }
        scope.drawPath(bodyPath, ThreadColor, style = StitchedStroke)

        // Draw tail
        val tailPath = Path().apply {
            moveTo(cx - 50f, cy + 75f)
            quadraticTo(cx - 70f, cy + 80f, cx - 75f, cy + 110f + legSwing)
            quadraticTo(cx - 60f, cy + 110f, cx - 55f, cy + 90f)
            close()
        }
        drawStitchedFill(scope, tailPath, Color(0xFF2C2219))
        scope.drawPath(tailPath, ThreadColor, style = StitchedStroke)

        val neckPath = Path().apply {
            moveTo(cx + 25f, cy + 85f)
            lineTo(cx + 55f, cy + 30f)
            lineTo(cx + 70f, cy + 35f)
            lineTo(cx + 45f, cy + 95f)
            close()
        }
        drawStitchedFill(scope, neckPath, horseColor)
        scope.drawPath(neckPath, ThreadColor, style = StitchedStroke)

        val headPath = Path().apply {
            moveTo(cx + 50f, cy + 20f)
            lineTo(cx + 82f, cy + 14f)
            lineTo(cx + 87f, cy + 26f)
            lineTo(cx + 72f, cy + 42f)
            lineTo(cx + 48f, cy + 39f)
            close()
        }
        drawStitchedFill(scope, headPath, horseColor)
        scope.drawPath(headPath, ThreadColor, style = StitchedStroke)
        scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(cx + 70f, cy + 22f))

        scope.drawLine(Color(0xFF2C2219), Offset(cx + 42f, cy + 26f), Offset(cx + 56f, cy + 31f), strokeWidth = 5f, cap = StrokeCap.Round)
        scope.drawLine(Color(0xFF2C2219), Offset(cx + 48f, cy + 31f), Offset(cx + 62f, cy + 36f), strokeWidth = 4f, cap = StrokeCap.Round)

        val legs = listOf(
            Triple(cx + 32f, cy + 120f, legSwing),
            Triple(cx + 42f, cy + 120f, -legSwing),
            Triple(cx - 32f, cy + 120f, -legSwing * 0.8f),
            Triple(cx - 42f, cy + 120f, legSwing * 0.8f)
        )
        legs.forEach { (lx, ly, angle) ->
            scope.withTransform({ rotate(angle, pivot = Offset(lx, ly)) }) {
                val ex = lx + (if (lx > cx) 2f else -2f)
                val ey = ly + 55f
                scope.drawLine(legColor, Offset(lx, ly), Offset(ex, ey), strokeWidth = 9f, cap = StrokeCap.Round)
                scope.drawLine(ThreadColor, Offset(lx, ly), Offset(ex, ey), strokeWidth = 2f, cap = StrokeCap.Round)
                scope.drawCircle(Color(0xFF2C2219), radius = 6f, center = Offset(ex, ey))
            }
        }

        val saddlePath = Path().apply {
            moveTo(cx - 18f, cy + 65f)
            lineTo(cx + 22f, cy + 60f)
            lineTo(cx + 22f, cy + 92f)
            lineTo(cx - 18f, cy + 97f)
            close()
        }
        drawStitchedFill(scope, saddlePath, if (fighter.isPlayer) Color(0xFF9E3624) else Color(0xFF4C613D))
        scope.drawPath(saddlePath, ThreadColor, style = StitchedStroke)
    }

    private fun drawChariot(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        // Draw horse further ahead
        val horseOffsetX = 120f
        scope.withTransform({ translate(horseOffsetX, 0f) }) {
            drawHorse(this, cx, cy, fighter)
        }
        
        // Draw wooden traces connecting horse to chariot
        scope.drawLine(Color(0xFF5C4033), Offset(cx - 30f, cy + 90f), Offset(cx + horseOffsetX + 20f, cy + 90f), strokeWidth = 8f)

        // Draw chariot cart box (around the fighter's legs)
        val cartW = 110f
        val cartH = 90f
        val cartTopY = cy + 35f
        scope.drawRect(Color(0xFF8B5A2B), topLeft = Offset(cx - cartW/2, cartTopY), size = androidx.compose.ui.geometry.Size(cartW, cartH))
        scope.drawRect(ThreadColor, topLeft = Offset(cx - cartW/2, cartTopY), size = androidx.compose.ui.geometry.Size(cartW, cartH), style = StitchedStroke)
        
        // Horizontal wooden slats on chariot
        scope.drawLine(Color(0xFF5C4033), Offset(cx - cartW/2, cartTopY + 30f), Offset(cx + cartW/2, cartTopY + 30f), strokeWidth = 3f)
        scope.drawLine(Color(0xFF5C4033), Offset(cx - cartW/2, cartTopY + 60f), Offset(cx + cartW/2, cartTopY + 60f), strokeWidth = 3f)
        
        // Draw wheel
        val wheelRadius = 45f
        val wheelCenter = Offset(cx, cy + 130f) // bottom is cy + 175f
        val wheelRot = fighter.posX * 2f // spin based on position
        scope.withTransform({ rotate(wheelRot, wheelCenter) }) {
            scope.drawCircle(Color(0xFF5C4033), radius = wheelRadius, center = wheelCenter)
            scope.drawCircle(ThreadColor, radius = wheelRadius, center = wheelCenter, style = Stroke(width = 4f))
            scope.drawLine(ThreadColor, Offset(wheelCenter.x - wheelRadius, wheelCenter.y), Offset(wheelCenter.x + wheelRadius, wheelCenter.y), strokeWidth = 4f)
            scope.drawLine(ThreadColor, Offset(wheelCenter.x, wheelCenter.y - wheelRadius), Offset(wheelCenter.x, wheelCenter.y + wheelRadius), strokeWidth = 4f)
        }
    }

    private fun radToDeg(rad: Float): Float {
        return (rad * 180f / Math.PI).toFloat()
    }

    private fun drawThrone(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val wColor = Color(0xFF5C4033) // Dark wood
        
        // Anim state for walking pallbearers
        val walkAnim = if (fighter.isDead || fighter.isDying) 0f else fighter.animFrame * 1.5f
        val angleL = if (fighter.isDead || fighter.isDying) 0f else kotlin.math.sin(walkAnim) * 0.45f
        val angleR = if (fighter.isDead || fighter.isDying) 0f else -kotlin.math.sin(walkAnim) * 0.45f
        
        // Draw 2 visible side Pallbearers (representing 4 holding the poles)
        listOf(-45f, 45f).forEach { bx ->
            val px = cx + bx
            val py = cy + 120f // shoulders at pole height
            
            // Draw Legs
            scope.withTransform({ rotate(radToDeg(angleL), pivot = Offset(px, py + 30f)) }) {
                drawStitchedStrap(this, Offset(px - 5f, py + 30f), Offset(px - 10f, py + 80f), Color(0xFF382F22))
            }
            scope.withTransform({ rotate(radToDeg(angleR), pivot = Offset(px, py + 30f)) }) {
                drawStitchedStrap(this, Offset(px + 5f, py + 30f), Offset(px + 10f, py + 80f), Color(0xFF382F22))
            }
            
            // Torso (Peasant Tunic)
            val torso = Path().apply {
                moveTo(px - 12f, py - 5f)
                lineTo(px + 12f, py - 5f)
                lineTo(px + 15f, py + 40f)
                lineTo(px - 15f, py + 40f)
                close()
            }
            drawStitchedFill(scope, torso, Color(0xFFFAF6EB))
            scope.drawPath(torso, ThreadColor, style = StitchedStroke)
            
            // Head
            scope.drawCircle(Color(0xFFE8C5A4), radius = 12f, center = Offset(px, py - 18f))
            scope.drawCircle(ThreadColor, radius = 12f, center = Offset(px, py - 18f), style = Stroke(width = 1.5f))
            // Coif
            val coif = Path().apply {
                moveTo(px - 14f, py - 18f)
                arcTo(androidx.compose.ui.geometry.Rect(px - 14f, py - 32f, px + 14f, py - 4f), 180f, 180f, false)
                lineTo(px + 16f, py - 5f)
                lineTo(px - 16f, py - 5f)
                close()
            }
            drawStitchedFill(scope, coif, Color(0xFF868C91))
            
            // Arm holding the pole
            scope.withTransform({ rotate(45f, pivot = Offset(px, py + 5f)) }) {
                drawStitchedStrap(this, Offset(px, py + 5f), Offset(px, py + 25f), Color(0xFFFAF6EB))
            }
        }

        // Draw a basic wooden throne
        val tPath = Path().apply {
            moveTo(cx - 30f, cy + 100f)
            lineTo(cx - 30f, cy + 30f)
            lineTo(cx + 30f, cy + 30f)
            lineTo(cx + 30f, cy + 100f)
            moveTo(cx - 30f, cy + 90f)
            lineTo(cx + 30f, cy + 90f)
        }
        scope.drawPath(tPath, wColor, style = Stroke(width = 12f))
        
        // Add horizontal pole for pallbearers
        val polePath = Path().apply {
            moveTo(cx - 70f, cy + 120f) // Reaches the back pallbearers
            lineTo(cx + 70f, cy + 120f) // Reaches the front pallbearers
        }
        scope.drawPath(polePath, wColor, style = Stroke(width = 10f))

        // Add gold trim
        val trimPath = Path().apply {
            moveTo(cx - 30f, cy + 30f)
            lineTo(cx + 30f, cy + 30f)
        }
        scope.drawPath(trimPath, Color(0xFFB08221), style = Stroke(width = 6f))
    }

    private fun drawStilts(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val wColor = Color(0xFF8B7355) // Wood
        val angleL = if (fighter.isDead || fighter.isDying) 0f else kotlin.math.sin(fighter.animFrame) * 0.45f
        val angleR = if (fighter.isDead || fighter.isDying) 0f else -kotlin.math.sin(fighter.animFrame) * 0.45f

        // Draw left stilt
        scope.withTransform({ rotate(radToDeg(angleL), pivot = Offset(cx - 10f, cy + 90f)) }) {
            drawLine(wColor, Offset(cx - 10f, cy + 140f), Offset(cx - 10f, cy + 200f), strokeWidth = 8f)
            drawLine(Color(0xFF4A4A4A), Offset(cx - 20f, cy + 150f), Offset(cx, cy + 150f), strokeWidth = 4f)
        }
        // Draw right stilt
        scope.withTransform({ rotate(radToDeg(angleR), pivot = Offset(cx + 10f, cy + 90f)) }) {
            drawLine(wColor, Offset(cx + 10f, cy + 140f), Offset(cx + 10f, cy + 200f), strokeWidth = 8f)
            drawLine(Color(0xFF4A4A4A), Offset(cx, cy + 150f), Offset(cx + 20f, cy + 150f), strokeWidth = 4f)
        }
    }
}
