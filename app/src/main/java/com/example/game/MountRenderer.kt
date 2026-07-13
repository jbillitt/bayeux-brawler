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
// Mounts, decoys and beasts: horse, chariot, throne, stilts, trojan horse, wardog, raven

/** How high the stilts hold the rider above the ground. Shared by the mount art and the body offset. */
internal const val STILTS_LIFT_PX = 90f
internal fun drawHorse(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState, withStirrups: Boolean = true) {
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

        // Stirrups (leather strap + iron loop), swinging with the rider's leg. A chariot horse is
        // driven from a cart, not ridden, so it hangs none.
        val strapColor = Color(0xFF382F22) // dark leather (also the reins, below)
        if (withStirrups) {
            val ironColor = Color(0xFF5D666B)
            val stirrupSwing = if (fighter.isDead || fighter.isDying) 0f else kotlin.math.sin(fighter.animFrame) * 0.45f
            val stirrupX = cx + 5f + stirrupSwing * 40f
            val stirrupY = cy + 123f - kotlin.math.abs(stirrupSwing) * 10f
            scope.drawLine(strapColor, Offset(cx + 5f, cy + 92f), Offset(stirrupX, stirrupY), strokeWidth = 3f)
            scope.drawLine(ironColor, Offset(stirrupX - 5f, stirrupY), Offset(stirrupX + 5f, stirrupY), strokeWidth = 4f)
            scope.drawCircle(ironColor, radius = 5f, center = Offset(stirrupX, stirrupY + 3f), style = Stroke(width = 3f))
        }

        // Draw Reigns (leather strap from snout to saddle/rider hands)
        val reignPath = Path().apply {
            moveTo(cx + 75f, cy + 38f) // near mouth/snout
            quadraticTo(cx + 50f, cy + 70f, cx + 5f, cy + 75f) // curves down to rider's lap
        }
        scope.drawPath(reignPath, strapColor, style = Stroke(width = 3f))
    }

    // The great wooden decoy: planked angular horse on a wheeled platform
internal fun drawTrojanHorse(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val wood = Color(0xFF8B5A2B)
        val darkWood = Color(0xFF5C4033)

        // Wheeled platform (wheel bottoms sit on the ground line at cy + 175)
        val platTop = cy + 130f
        scope.drawRect(darkWood, topLeft = Offset(cx - 75f, platTop), size = androidx.compose.ui.geometry.Size(150f, 18f))
        scope.drawRect(ThreadColor, topLeft = Offset(cx - 75f, platTop), size = androidx.compose.ui.geometry.Size(150f, 18f), style = StitchedStroke)
        val wheelRot = fighter.posX * 2.5f
        for (wx in listOf(cx - 50f, cx + 50f)) {
            val wc = Offset(wx, cy + 158f)
            scope.withTransform({ rotate(wheelRot, wc) }) {
                drawCircle(darkWood, radius = 17f, center = wc)
                drawCircle(ThreadColor, radius = 17f, center = wc, style = Stroke(width = 3f))
                drawLine(ThreadColor, Offset(wc.x - 17f, wc.y), Offset(wc.x + 17f, wc.y), strokeWidth = 3f)
                drawLine(ThreadColor, Offset(wc.x, wc.y - 17f), Offset(wc.x, wc.y + 17f), strokeWidth = 3f)
            }
        }

        // Stiff plank legs
        for (lx in listOf(cx - 45f, cx - 25f, cx + 25f, cx + 45f)) {
            scope.drawLine(wood, Offset(lx, cy + 65f), Offset(lx, platTop), strokeWidth = 12f)
            scope.drawLine(ThreadColor, Offset(lx, cy + 65f), Offset(lx, platTop), strokeWidth = 2f)
        }

        // Boxy planked body
        val bodyPath = Path().apply {
            moveTo(cx - 62f, cy + 30f)
            lineTo(cx + 55f, cy + 25f)
            lineTo(cx + 62f, cy + 75f)
            lineTo(cx - 55f, cy + 80f)
            close()
        }
        drawStitchedFill(scope, bodyPath, wood)
        scope.drawPath(bodyPath, ThreadColor, style = StitchedStroke)
        // Plank seams
        scope.drawLine(darkWood, Offset(cx - 60f, cy + 47f), Offset(cx + 58f, cy + 42f), strokeWidth = 3f)
        scope.drawLine(darkWood, Offset(cx - 58f, cy + 64f), Offset(cx + 60f, cy + 59f), strokeWidth = 3f)
        // Belly hatch the soldiers burst from
        scope.drawRect(darkWood, topLeft = Offset(cx - 14f, cy + 48f), size = androidx.compose.ui.geometry.Size(28f, 30f))
        scope.drawRect(ThreadColor, topLeft = Offset(cx - 14f, cy + 48f), size = androidx.compose.ui.geometry.Size(28f, 30f), style = StitchedStroke)

        // Angular neck and head
        val neckPath = Path().apply {
            moveTo(cx + 30f, cy + 35f)
            lineTo(cx + 58f, cy - 45f)
            lineTo(cx + 76f, cy - 42f)
            lineTo(cx + 52f, cy + 40f)
            close()
        }
        drawStitchedFill(scope, neckPath, wood)
        scope.drawPath(neckPath, ThreadColor, style = StitchedStroke)
        val headPath = Path().apply {
            moveTo(cx + 56f, cy - 55f)
            lineTo(cx + 98f, cy - 48f)
            lineTo(cx + 94f, cy - 32f)
            lineTo(cx + 60f, cy - 30f)
            close()
        }
        drawStitchedFill(scope, headPath, wood)
        scope.drawPath(headPath, ThreadColor, style = StitchedStroke)
        scope.drawCircle(ThreadColor, radius = 3f, center = Offset(cx + 78f, cy - 44f))
        // Plank mane ridge and rope tail
        scope.drawLine(darkWood, Offset(cx + 40f, cy + 30f), Offset(cx + 64f, cy - 44f), strokeWidth = 5f)
        scope.drawLine(Color(0xFF9C7D58), Offset(cx - 58f, cy + 38f), Offset(cx - 80f, cy + 75f), strokeWidth = 5f)
    }

internal fun drawChariot(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState?, isCollapsed: Boolean = false) {
        if (isCollapsed) {
            // Draw broken chariot (snapped axle, tilted body)
            val cartW = 110f
            val cartH = 90f
            val cartTopY = cy + 65f
            
            scope.withTransform({ rotate(15f, Offset(cx, cy)) }) {
                scope.drawRect(Color(0xFF8B5A2B), topLeft = Offset(cx - cartW/2, cartTopY), size = androidx.compose.ui.geometry.Size(cartW, cartH))
                scope.drawRect(ThreadColor, topLeft = Offset(cx - cartW/2, cartTopY), size = androidx.compose.ui.geometry.Size(cartW, cartH), style = StitchedStroke)
                scope.drawLine(Color(0xFF5C4033), Offset(cx - cartW/2, cartTopY + 30f), Offset(cx + cartW/2, cartTopY + 30f), strokeWidth = 3f)
                scope.drawLine(Color(0xFF5C4033), Offset(cx - cartW/2, cartTopY + 60f), Offset(cx + cartW/2, cartTopY + 60f), strokeWidth = 3f)
            }
            
            // Broken wheel detached
            val wheelRadius = 45f
            val wheelCenter = Offset(cx + 40f, cy + 140f)
            scope.withTransform({ rotate(45f, wheelCenter) }) {
                scope.drawCircle(Color(0xFF5C4033), radius = wheelRadius, center = wheelCenter)
                scope.drawCircle(ThreadColor, radius = wheelRadius, center = wheelCenter, style = Stroke(width = 4f))
                scope.drawLine(ThreadColor, Offset(wheelCenter.x - wheelRadius, wheelCenter.y), Offset(wheelCenter.x + wheelRadius, wheelCenter.y), strokeWidth = 4f)
                scope.drawLine(ThreadColor, Offset(wheelCenter.x, wheelCenter.y - wheelRadius), Offset(wheelCenter.x, wheelCenter.y + wheelRadius), strokeWidth = 4f)
            }

            // Snapped axle
            scope.drawLine(Color(0xFF5C4033), Offset(cx - 30f, cy + 120f), Offset(cx + 60f, cy + 130f), strokeWidth = 8f)
            return
        }

        val fighterSafe = fighter!!
        // Draw horse further ahead
        val horseOffsetX = 120f
        scope.withTransform({ translate(horseOffsetX, 0f) }) {
            drawHorse(this, cx, cy, fighterSafe, withStirrups = false) // driven, not ridden
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
        val wheelRot = fighterSafe.posX * 2f // spin based on position
        scope.withTransform({ rotate(wheelRot, wheelCenter) }) {
            scope.drawCircle(Color(0xFF5C4033), radius = wheelRadius, center = wheelCenter)
            scope.drawCircle(ThreadColor, radius = wheelRadius, center = wheelCenter, style = Stroke(width = 4f))
            scope.drawLine(ThreadColor, Offset(wheelCenter.x - wheelRadius, wheelCenter.y), Offset(wheelCenter.x + wheelRadius, wheelCenter.y), strokeWidth = 4f)
            scope.drawLine(ThreadColor, Offset(wheelCenter.x, wheelCenter.y - wheelRadius), Offset(wheelCenter.x, wheelCenter.y + wheelRadius), strokeWidth = 4f)
        }
    }

internal fun radToDeg(rad: Float): Float {
        return (rad * 180f / Math.PI).toFloat()
    }

internal fun drawThrone(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState, isBattleActive: Boolean) {
        val wColor = Color(0xFF5C4033) // Dark wood
        
        // If not in battle (e.g. preview pane), we need to draw fake pallbearers
        if (!isBattleActive) {
            val pColor = Color(0xFF4A4A4A)
            val pallbearers = listOf(
                Pair(cx - 45f, cy + 90f),
                Pair(cx + 45f, cy + 90f)
            )
            pallbearers.forEach { (px, py) ->
                // Basic peasant shape for preview
                scope.drawLine(pColor, Offset(px, py), Offset(px, py + 40f), strokeWidth = 12f, cap = StrokeCap.Round) // body
                scope.drawCircle(pColor, radius = 10f, center = Offset(px, py - 10f)) // head
                scope.drawLine(pColor, Offset(px, py + 40f), Offset(px - 10f, py + 80f), strokeWidth = 6f) // leg
                scope.drawLine(pColor, Offset(px, py + 40f), Offset(px + 10f, py + 80f), strokeWidth = 6f) // leg
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

internal fun drawStilts(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val wColor = Color(0xFF8B7355) // Wood
        // Match the leg anim exactly (animFrame * 0.45f)
        val angleL = if (fighter.isDead || fighter.isDying) 0f else kotlin.math.sin(fighter.animFrame) * 0.45f
        val angleR = if (fighter.isDead || fighter.isDying) 0f else -kotlin.math.sin(fighter.animFrame) * 0.45f

        // The rider sits STILTS_LIFT_PX higher (mountOffsetY), so his feet are at cy + 105 - lift.
        // The poles run from there down to the ground line at cy + 220.
        val footY = cy + 105f - (STILTS_LIFT_PX - 45f)

        // Draw left stilt (attaches to left foot at cx - 18)
        scope.withTransform({ rotate(radToDeg(angleL), pivot = Offset(cx - 10f, footY)) }) {
            drawLine(wColor, Offset(cx - 18f, footY), Offset(cx - 18f, cy + 220f), strokeWidth = 8f)
            // Footrest
            drawLine(Color(0xFF4A4A4A), Offset(cx - 25f, footY + 5f), Offset(cx - 5f, footY + 5f), strokeWidth = 4f)
        }
        // Draw right stilt (attaches to right foot at cx + 18)
        scope.withTransform({ rotate(radToDeg(angleR), pivot = Offset(cx + 10f, footY)) }) {
            drawLine(wColor, Offset(cx + 18f, footY), Offset(cx + 18f, cy + 220f), strokeWidth = 8f)
            // Footrest
            drawLine(Color(0xFF4A4A4A), Offset(cx + 10f, footY + 5f), Offset(cx + 35f, footY + 5f), strokeWidth = 4f)
        }
    }

internal fun drawWardog(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val anim = fighter.animFrame
        val angleF = if (fighter.isDead || fighter.isDying) 0f else Math.sin(anim.toDouble()).toFloat() * 15f
        val angleB = if (fighter.isDead || fighter.isDying) 0f else -Math.sin(anim.toDouble()).toFloat() * 15f

        val dogColor = androidx.compose.ui.graphics.Color(0xFF452E1B)
        
        scope.withTransform({
            translate(0f, 130f)
        }) {
            // Legs
            scope.withTransform({ rotate(angleB, pivot = androidx.compose.ui.geometry.Offset(cx - 20f, cy)) }) {
                scope.drawLine(dogColor, androidx.compose.ui.geometry.Offset(cx - 20f, cy), androidx.compose.ui.geometry.Offset(cx - 25f, cy + 25f), strokeWidth = 8f, cap = StrokeCap.Round)
            }
            scope.withTransform({ rotate(angleF, pivot = androidx.compose.ui.geometry.Offset(cx + 20f, cy)) }) {
                scope.drawLine(dogColor, androidx.compose.ui.geometry.Offset(cx + 20f, cy), androidx.compose.ui.geometry.Offset(cx + 25f, cy + 25f), strokeWidth = 8f, cap = StrokeCap.Round)
            }
            // Body
            scope.drawLine(dogColor, androidx.compose.ui.geometry.Offset(cx - 25f, cy - 5f), androidx.compose.ui.geometry.Offset(cx + 25f, cy - 5f), strokeWidth = 24f, cap = StrokeCap.Round)
            // Head
            scope.drawLine(dogColor, androidx.compose.ui.geometry.Offset(cx + 20f, cy - 10f), androidx.compose.ui.geometry.Offset(cx + 35f, cy - 15f), strokeWidth = 18f, cap = StrokeCap.Round)
            // Snout
            scope.drawLine(dogColor, androidx.compose.ui.geometry.Offset(cx + 30f, cy - 15f), androidx.compose.ui.geometry.Offset(cx + 45f, cy - 10f), strokeWidth = 10f, cap = StrokeCap.Round)
            // Ear
            scope.drawLine(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.geometry.Offset(cx + 25f, cy - 15f), androidx.compose.ui.geometry.Offset(cx + 20f, cy - 25f), strokeWidth = 6f, cap = StrokeCap.Round)
            // Tail
            scope.drawLine(dogColor, androidx.compose.ui.geometry.Offset(cx - 25f, cy - 5f), androidx.compose.ui.geometry.Offset(cx - 40f, cy - 15f), strokeWidth = 6f, cap = StrokeCap.Round)
        }
    }

internal fun drawRaven(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val anim = fighter.animFrame
        val wingY = if (fighter.isDead || fighter.isDying) 0f else Math.sin(anim.toDouble() * 2.0).toFloat() * 15f

        val ravenColor = androidx.compose.ui.graphics.Color(0xFF111111)
        
        scope.withTransform({
            translate(0f, -120f)
        }) {
            // Body
            scope.drawLine(ravenColor, androidx.compose.ui.geometry.Offset(cx - 10f, cy), androidx.compose.ui.geometry.Offset(cx + 15f, cy), strokeWidth = 14f, cap = StrokeCap.Round)
            // Wing back
            scope.drawLine(ravenColor, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx - 15f, cy - wingY), strokeWidth = 8f, cap = StrokeCap.Round)
            // Wing front
            scope.drawLine(ravenColor, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx - 10f, cy + wingY), strokeWidth = 8f, cap = StrokeCap.Round)
            // Head
            scope.drawCircle(ravenColor, radius = 8f, center = androidx.compose.ui.geometry.Offset(cx + 15f, cy - 4f))
            // Beak
            scope.drawLine(androidx.compose.ui.graphics.Color.Yellow, androidx.compose.ui.geometry.Offset(cx + 18f, cy - 4f), androidx.compose.ui.geometry.Offset(cx + 30f, cy), strokeWidth = 4f, cap = StrokeCap.Round)
            // Eye
            scope.drawCircle(androidx.compose.ui.graphics.Color.Red, radius = 2f, center = androidx.compose.ui.geometry.Offset(cx + 15f, cy - 5f))
        }
    }
    
    // Background objects are static between damage events, but their stitched fills cost
    // thousands of drawLine calls. Render each to an offscreen bitmap once and blit per frame.
    // Key includes hp/destroyed so damage decals re-render when they change.
