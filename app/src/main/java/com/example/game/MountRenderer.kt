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

/**
 * Four legs on the same swing the horse uses, so every mount walks in one rhythm. Kept as a helper
 * rather than copied three more times — the hoof colour and stride length are all that ever differ.
 */
private fun drawBeastLegs(
    scope: DrawScope, cx: Float, cy: Float, legSwing: Float,
    legColor: Color, hoofColor: Color, topY: Float, length: Float, width: Float, spread: Float
) {
    val legs = listOf(
        Triple(cx + spread - 10f, topY, legSwing),
        Triple(cx + spread, topY, -legSwing),
        Triple(cx - spread + 10f, topY, -legSwing * 0.8f),
        Triple(cx - spread, topY, legSwing * 0.8f)
    )
    legs.forEach { (lx, ly, angle) ->
        scope.withTransform({ rotate(angle, pivot = Offset(lx, ly)) }) {
            val ex = lx + (if (lx > cx) 2f else -2f)
            val ey = ly + length
            scope.drawLine(legColor, Offset(lx, ly), Offset(ex, ey), strokeWidth = width, cap = StrokeCap.Round)
            scope.drawLine(ThreadColor, Offset(lx, ly), Offset(ex, ey), strokeWidth = 2f, cap = StrokeCap.Round)
            scope.drawCircle(hoofColor, radius = width * 0.7f, center = Offset(ex, ey))
        }
    }
}

/** A plough ox: heavy barrel body, short legs, low head, horns. Slow, and hard to kill. */
internal fun drawWarOx(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
    val anim = fighter.animFrame
    val walking = !fighter.isDead && !fighter.isDying
    val legSwing = if (walking) sin(anim) * 11f else 0f // a shorter stride than a horse: it plods
    val hide = Color(0xFF6B5B4A)
    val legColor = Color(0xFF4E4136)

    // Barrel body, wider and lower than the horse's oval.
    val bodyPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - 62f, cy + 58f, cx + 58f, cy + 132f))
    }
    drawStitchedFill(scope, bodyPath, hide)
    scope.drawPath(bodyPath, ThreadColor, style = StitchedStroke)

    // Shoulder hump — the ox's silhouette read at a glance.
    val humpPath = Path().apply {
        moveTo(cx + 10f, cy + 62f)
        quadraticTo(cx + 30f, cy + 38f, cx + 48f, cy + 66f)
        close()
    }
    drawStitchedFill(scope, humpPath, hide)
    scope.drawPath(humpPath, ThreadColor, style = StitchedStroke)

    val tailPath = Path().apply {
        moveTo(cx - 58f, cy + 72f)
        quadraticTo(cx - 74f, cy + 84f, cx - 70f, cy + 116f + legSwing)
        quadraticTo(cx - 62f, cy + 108f, cx - 60f, cy + 88f)
        close()
    }
    drawStitchedFill(scope, tailPath, Color(0xFF2C2219))
    scope.drawPath(tailPath, ThreadColor, style = StitchedStroke)

    // Head hangs low, as a yoked beast carries it.
    val headPath = Path().apply {
        moveTo(cx + 48f, cy + 62f)
        lineTo(cx + 86f, cy + 64f)
        lineTo(cx + 90f, cy + 84f)
        lineTo(cx + 66f, cy + 92f)
        lineTo(cx + 46f, cy + 84f)
        close()
    }
    drawStitchedFill(scope, headPath, hide)
    scope.drawPath(headPath, ThreadColor, style = StitchedStroke)
    scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(cx + 74f, cy + 72f))

    // Horns, one sweeping each way off the poll. Drawn long and pale on purpose: they are the whole
    // silhouette cue that this is an ox and not a dark horse, and stubby ones vanish at play scale.
    val horn = Color(0xFFEFE6D4)
    listOf(
        Triple(Offset(cx + 56f, cy + 64f), Offset(cx + 34f, cy + 40f), Offset(cx + 40f, cy + 52f)),
        Triple(Offset(cx + 74f, cy + 64f), Offset(cx + 96f, cy + 40f), Offset(cx + 90f, cy + 52f))
    ).forEach { (from, to, ctrl) ->
        val h = Path().apply {
            moveTo(from.x, from.y)
            quadraticTo(ctrl.x, ctrl.y, to.x, to.y)
        }
        scope.drawPath(h, horn, style = Stroke(width = 8f, cap = StrokeCap.Round))
        scope.drawPath(h, ThreadColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }

    drawBeastLegs(scope, cx, cy, legSwing, legColor, Color(0xFF2C2219),
        topY = cy + 124f, length = 46f, width = 12f, spread = 44f)

    val saddlePath = Path().apply {
        moveTo(cx - 20f, cy + 62f)
        lineTo(cx + 18f, cy + 58f)
        lineTo(cx + 18f, cy + 90f)
        lineTo(cx - 20f, cy + 94f)
        close()
    }
    drawStitchedFill(scope, saddlePath, if (fighter.isPlayer) Color(0xFF9E3624) else Color(0xFF4C613D))
    scope.drawPath(saddlePath, ThreadColor, style = StitchedStroke)

    // Yoke rope to the muzzle, in place of reins.
    scope.drawPath(Path().apply {
        moveTo(cx + 80f, cy + 84f)
        quadraticTo(cx + 50f, cy + 84f, cx + 2f, cy + 74f)
    }, Color(0xFF382F22), style = Stroke(width = 3f))
}

/** A baggage mule: smaller than a horse, long ears, panniers slung either side. */
internal fun drawPackMule(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
    val anim = fighter.animFrame
    val walking = !fighter.isDead && !fighter.isDying
    val legSwing = if (walking) sin(anim) * 14f else 0f
    val hide = Color(0xFF8A7156)
    val legColor = Color(0xFF6B573F)

    val bodyPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - 44f, cy + 72f, cx + 44f, cy + 128f))
    }
    drawStitchedFill(scope, bodyPath, hide)
    scope.drawPath(bodyPath, ThreadColor, style = StitchedStroke)

    val tailPath = Path().apply {
        moveTo(cx - 40f, cy + 82f)
        quadraticTo(cx - 54f, cy + 90f, cx - 52f, cy + 112f + legSwing)
        quadraticTo(cx - 44f, cy + 104f, cx - 42f, cy + 92f)
        close()
    }
    drawStitchedFill(scope, tailPath, Color(0xFF2C2219))
    scope.drawPath(tailPath, ThreadColor, style = StitchedStroke)

    val neckPath = Path().apply {
        moveTo(cx + 20f, cy + 90f)
        lineTo(cx + 42f, cy + 52f)
        lineTo(cx + 55f, cy + 56f)
        lineTo(cx + 36f, cy + 98f)
        close()
    }
    drawStitchedFill(scope, neckPath, hide)
    scope.drawPath(neckPath, ThreadColor, style = StitchedStroke)

    val headPath = Path().apply {
        moveTo(cx + 38f, cy + 44f)
        lineTo(cx + 66f, cy + 40f)
        lineTo(cx + 70f, cy + 52f)
        lineTo(cx + 56f, cy + 64f)
        lineTo(cx + 36f, cy + 60f)
        close()
    }
    drawStitchedFill(scope, headPath, hide)
    scope.drawPath(headPath, ThreadColor, style = StitchedStroke)
    scope.drawCircle(ThreadColor, radius = 2.5f, center = Offset(cx + 56f, cy + 48f))

    // The ears are the whole joke — absurdly long, and the mule's only dignity.
    scope.drawLine(hide, Offset(cx + 44f, cy + 42f), Offset(cx + 36f, cy + 10f), strokeWidth = 7f, cap = StrokeCap.Round)
    scope.drawLine(ThreadColor, Offset(cx + 44f, cy + 42f), Offset(cx + 36f, cy + 10f), strokeWidth = 2f, cap = StrokeCap.Round)
    scope.drawLine(hide, Offset(cx + 52f, cy + 42f), Offset(cx + 52f, cy + 8f), strokeWidth = 7f, cap = StrokeCap.Round)
    scope.drawLine(ThreadColor, Offset(cx + 52f, cy + 42f), Offset(cx + 52f, cy + 8f), strokeWidth = 2f, cap = StrokeCap.Round)

    drawBeastLegs(scope, cx, cy, legSwing, legColor, Color(0xFF2C2219),
        topY = cy + 120f, length = 42f, width = 7f, spread = 30f)

    // Panniers: this is a baggage animal pressed into service, and it should look like one.
    val pannier = Color(0xFF7A654C)
    listOf(-1f, 1f).forEach { side ->
        val p = Path().apply {
            moveTo(cx + side * 34f - 12f, cy + 88f)
            lineTo(cx + side * 34f + 12f, cy + 88f)
            lineTo(cx + side * 34f + 9f, cy + 116f)
            lineTo(cx + side * 34f - 9f, cy + 116f)
            close()
        }
        drawStitchedFill(scope, p, pannier)
        scope.drawPath(p, ThreadColor, style = StitchedStroke)
    }

    val saddlePath = Path().apply {
        moveTo(cx - 14f, cy + 76f)
        lineTo(cx + 16f, cy + 73f)
        lineTo(cx + 16f, cy + 96f)
        lineTo(cx - 14f, cy + 99f)
        close()
    }
    drawStitchedFill(scope, saddlePath, if (fighter.isPlayer) Color(0xFF9E3624) else Color(0xFF4C613D))
    scope.drawPath(saddlePath, ThreadColor, style = StitchedStroke)

    scope.drawPath(Path().apply {
        moveTo(cx + 60f, cy + 58f)
        quadraticTo(cx + 40f, cy + 80f, cx + 2f, cy + 82f)
    }, Color(0xFF382F22), style = Stroke(width = 3f))
}

/** A muzzled bear: broad shoulders, heavy forelimbs, a strapped muzzle. Fast and unreliable. */
internal fun drawWarBear(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
    val anim = fighter.animFrame
    val walking = !fighter.isDead && !fighter.isDying
    val legSwing = if (walking) sin(anim) * 20f else 0f // a loping, faster gait
    val fur = Color(0xFF3A2E24)
    val legColor = Color(0xFF2E241C)

    val bodyPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - 56f, cy + 56f, cx + 52f, cy + 126f))
    }
    drawStitchedFill(scope, bodyPath, fur)
    scope.drawPath(bodyPath, ThreadColor, style = StitchedStroke)

    // Shoulder mass, forward and high — the bear leads with it.
    val shoulderPath = Path().apply {
        moveTo(cx + 4f, cy + 60f)
        quadraticTo(cx + 26f, cy + 30f, cx + 46f, cy + 64f)
        close()
    }
    drawStitchedFill(scope, shoulderPath, fur)
    scope.drawPath(shoulderPath, ThreadColor, style = StitchedStroke)

    // A stub tail, not a horse's fall of hair.
    scope.drawCircle(Color(0xFF2C2219), radius = 7f, center = Offset(cx - 54f, cy + 84f))

    val headPath = Path().apply {
        moveTo(cx + 40f, cy + 40f)
        lineTo(cx + 74f, cy + 38f)
        lineTo(cx + 84f, cy + 56f)
        lineTo(cx + 64f, cy + 70f)
        lineTo(cx + 40f, cy + 64f)
        close()
    }
    drawStitchedFill(scope, headPath, fur)
    scope.drawPath(headPath, ThreadColor, style = StitchedStroke)
    scope.drawCircle(Color(0xFFEFE6D4), radius = 3f, center = Offset(cx + 62f, cy + 48f))
    scope.drawCircle(ThreadColor, radius = 1.5f, center = Offset(cx + 62f, cy + 48f))

    // Small round ears.
    scope.drawCircle(fur, radius = 7f, center = Offset(cx + 46f, cy + 32f))
    scope.drawCircle(ThreadColor, radius = 7f, center = Offset(cx + 46f, cy + 32f), style = Stroke(width = 2f))

    // The muzzle straps: the only reason anyone would sit on this animal.
    val iron = Color(0xFF5D666B)
    scope.drawLine(iron, Offset(cx + 66f, cy + 40f), Offset(cx + 74f, cy + 66f), strokeWidth = 3f)
    scope.drawLine(iron, Offset(cx + 58f, cy + 44f), Offset(cx + 80f, cy + 50f), strokeWidth = 3f)

    drawBeastLegs(scope, cx, cy, legSwing, legColor, Color(0xFF1F1913),
        topY = cy + 118f, length = 40f, width = 13f, spread = 40f)

    val saddlePath = Path().apply {
        moveTo(cx - 18f, cy + 60f)
        lineTo(cx + 16f, cy + 56f)
        lineTo(cx + 16f, cy + 88f)
        lineTo(cx - 18f, cy + 92f)
        close()
    }
    drawStitchedFill(scope, saddlePath, if (fighter.isPlayer) Color(0xFF9E3624) else Color(0xFF4C613D))
    scope.drawPath(saddlePath, ThreadColor, style = StitchedStroke)

    scope.drawPath(Path().apply {
        moveTo(cx + 78f, cy + 56f)
        quadraticTo(cx + 46f, cy + 70f, cx + 2f, cy + 72f)
    }, Color(0xFF382F22), style = Stroke(width = 3f))
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
            lineTo(cx + 2.5f, cy + 47.5f)
            lineTo(cx + 30f, cy + 100f)
            moveTo(cx - 30f, cy + 90f)
            lineTo(cx + 30f, cy + 90f)
        }
        scope.drawPath(tPath, wColor, style = Stroke(width = 12f))
        
        // Add horizontal pole for pallbearers
        val polePath = Path().apply {
            moveTo(cx - 95.5f, cy + 94f) // Reaches the back pallbearers
            lineTo(cx + 89f, cy + 94f) // Reaches the front pallbearers
        }
        scope.drawPath(polePath, wColor, style = Stroke(width = 10f))

        // Add gold trim
        val trimPath = Path().apply {
            moveTo(cx - 13f, cy + 34.5f)
            lineTo(cx + 6.5f, cy + 44f)
        }
        scope.drawPath(trimPath, Color(0xFFB08221), style = Stroke(width = 6f))
    }

/**
 * Top of the poles, in the body's own local space: the sole of the boot drawLegs draws at
 * cy + 155f, nudged up a touch so the footrest sits under the foot rather than through it.
 */
internal fun stiltTopY(cy: Float): Float = cy + 150f

/**
 * Bottom of the poles. The body is lifted STILTS_LIFT_PX *screen* pixels, which in this local
 * space (scaled by effectiveSize) is that much divided by the size. Anything else and a small
 * man's poles stop short of the ground.
 */
internal fun stiltGroundY(cy: Float, effectiveSize: Float): Float =
    cy + 155f + STILTS_LIFT_PX / effectiveSize

internal fun drawStilts(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState, effectiveSize: Float) {
        val wColor = Color(0xFF8B7355) // Wood
        // Match the leg anim exactly (animFrame * 0.45f) — same pivots drawLegs uses, so the poles
        // swing with the legs instead of being hand-matched to them.
        val angleL = if (fighter.isDead || fighter.isDying) 0f else kotlin.math.sin(fighter.animFrame) * 0.45f
        val angleR = if (fighter.isDead || fighter.isDying) 0f else -kotlin.math.sin(fighter.animFrame) * 0.45f

        val topY = stiltTopY(cy)
        val groundY = stiltGroundY(cy, effectiveSize)

        scope.withTransform({ rotate(radToDeg(angleL), pivot = Offset(cx - 10f, cy + 90f)) }) {
            drawLine(wColor, Offset(cx - 18f, topY), Offset(cx - 18f, groundY), strokeWidth = 8f)
            drawLine(Color(0xFF4A4A4A), Offset(cx - 25f, topY + 5f), Offset(cx - 5f, topY + 5f), strokeWidth = 4f)
        }
        scope.withTransform({ rotate(radToDeg(angleR), pivot = Offset(cx + 10f, cy + 90f)) }) {
            drawLine(wColor, Offset(cx + 18f, topY), Offset(cx + 18f, groundY), strokeWidth = 8f)
            drawLine(Color(0xFF4A4A4A), Offset(cx + 10f, topY + 5f), Offset(cx + 35f, topY + 5f), strokeWidth = 4f)
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
