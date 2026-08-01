package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
// Shared Bayeux-tapestry stitch style: thread colour, stroke styles, stitched fills and cloth textures
    // Style configuration
internal val ThreadColor = Color(0xFF2C2219) // Dark charcoal/brown wool thread outline

/**
 * The hand-worked line.
 *
 * The real tapestry has no straight edges in it: every outline is wool pulled through linen by a
 * person, so it wanders. Drawing mathematically perfect curves is the single biggest reason the art
 * reads as "flat medieval cartoon" rather than as the artefact. [jitteredPath] roughens outlines,
 * and every outline in the game goes through [drawStitchedOutline], so one dial moves all of them.
 *
 * Set [LINE_WOBBLE_DEVIATION] to 0f for the old perfectly-smooth line.
 */
internal const val LINE_WOBBLE_SEGMENT = 7f
internal const val LINE_WOBBLE_DEVIATION = 0.45f

internal val StitchedStroke = Stroke(
        width = 4f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
        // Deliberately NO pathEffect. android's DiscretePathEffect would do this in one line, but
        // it takes no seed: it re-rolls its deviations on every single draw, so every outline in
        // the game would crawl and boil at 60fps. See jitteredPath for what replaced it.
    )

/**
 * Deterministic jitter offsets, in units of ±1, keyed by contour length and sample count.
 *
 * Both keys are translation-invariant, which is the whole trick: a fighter walking across the
 * field hands us paths whose coordinates change every frame, but whose LENGTH does not — so the
 * same contour draws with the same deviations wherever it is on screen, and nothing wavers.
 * Rotation is safe for the same reason; limb rotations happen in the canvas matrix, so the path
 * we are handed is unrotated.
 *
 * ponytail: unbounded map, but keys are quantised contour lengths — a few hundred entries at most,
 * the same bound the stitch cache above lives with.
 */
private val jitterOffsets = HashMap<Int, FloatArray>()

private fun offsetsFor(lengthKey: Int, samples: Int): FloatArray =
    jitterOffsets.getOrPut(lengthKey * 31 + samples) {
        var s = (lengthKey.toLong() * 2654435761L) xor (samples.toLong() * 40503L)
        FloatArray(samples * 2) {
            s = s * 6364136223846793005L + 1442695040888963407L
            (((s ushr 33).toInt() % 2001) - 1000) / 1000f
        }
    }

/**
 * A copy of [source] with its outline nudged off true, the way a stitched line wanders.
 *
 * Walks each contour at [LINE_WOBBLE_SEGMENT] intervals and displaces every sample by a fixed
 * amount drawn from [offsetsFor]. The displacement depends only on the contour's own geometry, so
 * it is identical every frame for the same shape — this is what makes it safe to animate.
 */
internal fun jitteredPath(source: Path): Path {
    if (LINE_WOBBLE_DEVIATION <= 0f) return source
    val measure = android.graphics.PathMeasure(source.asAndroidPath(), false)
    val out = android.graphics.Path()
    val point = FloatArray(2)
    do {
        val length = measure.length
        if (length <= LINE_WOBBLE_SEGMENT) continue
        val samples = (length / LINE_WOBBLE_SEGMENT).toInt() + 1
        val offsets = offsetsFor((length * 4f).toInt(), samples)
        for (i in 0 until samples) {
            val distance = (i * LINE_WOBBLE_SEGMENT).coerceAtMost(length)
            measure.getPosTan(distance, point, null)
            val x = point[0] + offsets[i * 2] * LINE_WOBBLE_DEVIATION
            val y = point[1] + offsets[i * 2 + 1] * LINE_WOBBLE_DEVIATION
            if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
        }
        // Shapes here are closed outlines; join the last stitch back to the first so the wobble
        // cannot leave a notch at the seam.
        if (measure.isClosed) out.close()
    } while (measure.nextContour())
    return if (out.isEmpty) source else out.asComposePath()
}

/**
 * The one door every stitched outline in the game goes through, so the hand-worked line can never
 * be applied inconsistently and can never be applied twice.
 */
internal fun DrawScope.drawStitchedOutline(path: Path, color: Color = ThreadColor) {
    drawPath(jitteredPath(path), color, style = StitchedStroke)
}
internal val FillLineStroke = Stroke(
        width = 2f,
        cap = StrokeCap.Round
    )

internal data class StitchSegment(val start: Offset, val end: Offset)

internal data class StitchGeometry(
    val horizontal: List<StitchSegment>,
    val anchors: List<StitchSegment>
)

// Stitch texture depends only on the shape's SIZE, so pre-built origin-anchored stitch paths are
// shared across every shape in the same 8px size bucket and merely translated into place. This
// removes the per-call segment-list allocation and path rebuild that made stitched fills scale
// with body count (each follower is ~8 stitched shapes, every frame).
// ponytail: unbounded map, but keys are quantized on-screen sizes — a few hundred entries max.
private val stitchPathCache = HashMap<Int, Pair<Path, Path>>()
private val stitchFillStroke = Stroke(width = 1.5f, cap = StrokeCap.Round)
private val stitchAnchorStroke = Stroke(width = 1f)

internal inline fun appendStitchSegments(
    segments: List<StitchSegment>,
    moveTo: (Float, Float) -> Unit,
    lineTo: (Float, Float) -> Unit
) {
    segments.forEach { segment ->
        moveTo(segment.start.x, segment.start.y)
        lineTo(segment.end.x, segment.end.y)
    }
}

internal fun stitchSegments(bounds: Rect): StitchGeometry {
    val horizontal = mutableListOf<StitchSegment>()
    val anchors = mutableListOf<StitchSegment>()
    val rowStep = kotlin.math.max(3.5f, bounds.height / 40f)
    val segmentLen = kotlin.math.max(8f, bounds.width / 30f)
    var row = 0
    var y = bounds.top
    while (y < bounds.bottom) {
        var currX = bounds.left - 5f
        val endX = bounds.right + 5f
        var seg = 0
        var prevJitter = ((row * 7 + 3) % 5) * 0.3f - 0.6f
        while (currX < endX) {
            val nextX = currX + segmentLen
            val jitter = ((row * 31 + seg * 17) % 7) * 0.25f - 0.75f
            horizontal += StitchSegment(
                start = Offset(currX, y + prevJitter),
                end = Offset(nextX, y + jitter)
            )
            prevJitter = jitter
            currX = nextX
            seg++
        }

        var vx = bounds.left + ((row * 13) % 7)
        val vStep = segmentLen * 2.5f
        while (vx < bounds.right) {
            anchors += StitchSegment(
                start = Offset(vx, y - 2f),
                end = Offset(vx, y + 2f)
            )
            vx += vStep
        }
        y += rowStep
        row++
    }
    return StitchGeometry(horizontal, anchors)
}

internal fun drawStitchedFill(scope: DrawScope, path: Path, color: Color) {
        // Base fill color (solid but soft)
        scope.drawPath(path, color)

        // Add dense parallel lines to look like embroidered thread couching.
        // Density is capped so huge shapes (buildings, forts, the ship) don't
        // issue thousands of drawLine calls per frame; small shapes keep the
        // original fine stitching. Jitter is deterministic (cheap hash), and the
        // resulting stitch paths are cached per quantized size (see stitchPathCache).
        val bounds = path.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) return
        val qw = (bounds.width / 8f).toInt() + 1
        val qh = (bounds.height / 8f).toInt() + 1
        val (hPath, aPath) = stitchPathCache.getOrPut(qw * 4096 + qh) {
            val geometry = stitchSegments(Rect(0f, 0f, qw * 8f, qh * 8f))
            val h = Path()
            appendStitchSegments(geometry.horizontal, h::moveTo, h::lineTo)
            val a = Path()
            appendStitchSegments(geometry.anchors, a::moveTo, a::lineTo)
            h to a
        }
        scope.withTransform({
            clipPath(path)
            translate(bounds.left, bounds.top)
        }) {
            scope.drawPath(hPath, color = color.copy(alpha = 0.35f), style = stitchFillStroke)
            scope.drawPath(aPath, color = ThreadColor.copy(alpha = 0.15f), style = stitchAnchorStroke)
        }
    }

internal fun drawStitchedStrap(scope: DrawScope, from: Offset, to: Offset, color: Color, stitched: Boolean = true) {
        if (!stitched) {
            // Bare limb (skin-coloured sleeve): a plain fleshy arm with a soft edge, no clothing
            // thread outline or embroidery — a naked man's arm isn't hemmed like a sleeve.
            scope.drawLine(color = androidx.compose.ui.graphics.lerp(color, androidx.compose.ui.graphics.Color.Black, 0.22f),
                start = from, end = to, strokeWidth = 15f, cap = StrokeCap.Round)
            scope.drawLine(color = color, start = from, end = to, strokeWidth = 12f, cap = StrokeCap.Round)
            return
        }
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

internal fun drawChainmailTexture(scope: DrawScope, x: Float, y: Float, width: Float, height: Float) {
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

internal fun drawGambesonTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
        // Draw diagonal quilted stitch lines
        scope.drawLine(ThreadColor.copy(alpha = 0.25f), Offset(cx - width/2, cy), Offset(cx + width/2, cy + height), strokeWidth = 1.5f)
        scope.drawLine(ThreadColor.copy(alpha = 0.25f), Offset(cx + width/2, cy), Offset(cx - width/2, cy + height), strokeWidth = 1.5f)
    }

internal fun drawScaleTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
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

internal fun drawLamellarTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
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

internal fun drawFurTexture(scope: DrawScope, cx: Float, cy: Float, width: Float, height: Float) {
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
