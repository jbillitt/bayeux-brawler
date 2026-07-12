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
// Shared Bayeux-tapestry stitch style: thread colour, stroke styles, stitched fills and cloth textures
    // Style configuration
internal val ThreadColor = Color(0xFF2C2219) // Dark charcoal/brown wool thread outline
internal val StitchedStroke = Stroke(
        width = 4f, 
        cap = StrokeCap.Round, 
        join = StrokeJoin.Round
    )
internal val FillLineStroke = Stroke(
        width = 2f,
        cap = StrokeCap.Round
    )

internal fun drawStitchedFill(scope: DrawScope, path: Path, color: Color) {
        // Base fill color (solid but soft)
        scope.drawPath(path, color)

        // Add dense parallel lines to look like embroidered thread couching.
        // Density is capped so huge shapes (buildings, forts, the ship) don't
        // issue thousands of drawLine calls per frame; small shapes keep the
        // original fine stitching. Jitter is deterministic (cheap hash), not
        // Math.random() — that alone was ~16k synchronized calls/frame per building.
        scope.withTransform({
            clipPath(path)
        }) {
            val bounds = path.getBounds()
            val rowStep = kotlin.math.max(3.5f, bounds.height / 40f)
            val segmentLen = kotlin.math.max(8f, bounds.width / 30f)
            val stitchColor = color.copy(alpha = 0.35f)
            val anchorColor = ThreadColor.copy(alpha = 0.15f)
            var row = 0
            var y = bounds.top
            while (y < bounds.bottom) {
                val startX = bounds.left - 5f
                val endX = bounds.right + 5f

                var currX = startX
                var seg = 0
                var prevJitter = ((row * 7 + 3) % 5) * 0.3f - 0.6f
                while (currX < endX) {
                    val nextX = currX + segmentLen
                    val jitter = ((row * 31 + seg * 17) % 7) * 0.25f - 0.75f
                    scope.drawLine(
                        color = stitchColor,
                        start = Offset(currX, y + prevJitter),
                        end = Offset(nextX, y + jitter),
                        strokeWidth = 1.5f,
                        cap = StrokeCap.Round
                    )
                    prevJitter = jitter
                    currX = nextX
                    seg++
                }

                // Sparse vertical anchor stitches
                var vx = bounds.left + ((row * 13) % 7)
                val vStep = segmentLen * 2.5f
                while (vx < bounds.right) {
                    scope.drawLine(
                        color = anchorColor,
                        start = Offset(vx, y - 2f),
                        end = Offset(vx, y + 2f),
                        strokeWidth = 1f
                    )
                    vx += vStep
                }
                y += rowStep
                row++
            }
        }
    }

internal fun drawStitchedStrap(scope: DrawScope, from: Offset, to: Offset, color: Color) {
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

