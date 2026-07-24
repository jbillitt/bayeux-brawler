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
// Background structures: ship, forts, buildings, with a per-object bitmap cache
internal val bgBitmapCache = HashMap<String, Pair<String, androidx.compose.ui.graphics.ImageBitmap>>()
internal const val BG_BMP_W = 620
internal const val BG_BMP_H = 540
internal const val BG_BMP_CX = 310f // local anchor inside the bitmap
internal const val BG_BMP_CY = 400f

internal fun BackgroundObjectType.isLiveBackgroundObject(): Boolean =
    this == BackgroundObjectType.CASTLE_GATE

internal enum class CastleGateDamageState { INTACT, SPLINTERED, SHATTERED, BREACHED }

internal fun castleGateDamageState(hp: Float, maxHp: Float): CastleGateDamageState {
    if (maxHp <= 0f || hp <= 0f) return CastleGateDamageState.BREACHED
    val fraction = (hp / maxHp).coerceIn(0f, 1f)
    return when {
        fraction > 0.66f -> CastleGateDamageState.INTACT
        fraction > 0.33f -> CastleGateDamageState.SPLINTERED
        else -> CastleGateDamageState.SHATTERED
    }
}

internal fun drawBackgroundObject(
        scope: DrawScope,
        bg: BackgroundObject,
        scaledPosX: Float,
        scaleFactor: Float
    ) {
        val cx = scaledPosX
        // Fighters' feet sit at 200 + 158*scaleFactor in screen space; these structures bottom
        // out ~20px below their own cy (scaled about cy), so solve cy + 20*scale = feet line.
        // Anchoring to the same line keeps buildings/ship grounded at any canvas size.
        val cy = 200f + 138f * scaleFactor

        val key = "${bg.type}_${bg.seed}_${bg.artId}"
        val cached = bgBitmapCache[bg.id]
        val bitmap = if (cached != null && cached.first == key) cached.second else {
            val bmp = androidx.compose.ui.graphics.ImageBitmap(BG_BMP_W, BG_BMP_H)
            val canvas = androidx.compose.ui.graphics.Canvas(bmp)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                canvas,
                androidx.compose.ui.geometry.Size(BG_BMP_W.toFloat(), BG_BMP_H.toFloat())
            ) {
                renderBackgroundObject(this, bg, BG_BMP_CX, BG_BMP_CY)
            }
            bgBitmapCache[bg.id] = key to bmp
            bmp
        }

        // Buildings/forts draw bigger than fighters (they towered under them otherwise) and sit
        // slightly up the canvas for background perspective. The ship stays 1:1 on the ground.
        val isLowLandscape = bg.type == BackgroundObjectType.SHIP ||
            bg.type == BackgroundObjectType.FLEET_CROSSING
        val isSiegeStructure = bg.type == BackgroundObjectType.CASTLE_WALL ||
            bg.type == BackgroundObjectType.CASTLE_GATE
        val bgScale = if (isLowLandscape) 1.25f else 1.7f
        val verticalScale = if (isSiegeStructure) 1f else bgScale
        val lift = if (isLowLandscape || isSiegeStructure) 0f else 14f
        scope.withTransform({
            scale(scaleFactor * bgScale, scaleFactor * verticalScale, pivot = Offset(cx, cy))
        }) {
            scope.drawImage(bitmap, topLeft = Offset(cx - BG_BMP_CX, cy - BG_BMP_CY - lift))
            if (bg.stuckArrowsFromLeft + bg.stuckArrowsFromRight > 0) {
                drawDamageDecals(scope, cx, cy - lift, bg)
            }
        }
    }

/** Draws immutable scenery directly into the final-resolution world bitmap. */
internal fun drawStaticBackgroundObject(
    scope: DrawScope,
    bg: BackgroundObject,
    scaledPosX: Float,
    scaleFactor: Float
) {
    val cy = 200f + 138f * scaleFactor
    val isLowLandscape = bg.type == BackgroundObjectType.SHIP ||
        bg.type == BackgroundObjectType.FLEET_CROSSING
    val isSiegeStructure = bg.type == BackgroundObjectType.CASTLE_WALL ||
        bg.type == BackgroundObjectType.CASTLE_GATE
    val bgScale = if (isLowLandscape) 1.25f else 1.7f
    val verticalScale = if (isSiegeStructure) 1f else bgScale
    val lift = if (isLowLandscape || isSiegeStructure) 0f else 14f
    scope.withTransform({
        scale(scaleFactor * bgScale, scaleFactor * verticalScale, pivot = Offset(scaledPosX, cy))
    }) {
        renderBackgroundObject(scope, bg, scaledPosX, cy - lift)
    }
}

/** Live arrow damage, deliberately outside the immutable world cache. */
internal fun drawBackgroundDamageDecals(
    scope: DrawScope,
    bg: BackgroundObject,
    scaledPosX: Float,
    scaleFactor: Float
) {
    if (bg.stuckArrowsFromLeft + bg.stuckArrowsFromRight == 0) return
    val cy = 200f + 138f * scaleFactor
    val isLowLandscape = bg.type == BackgroundObjectType.SHIP ||
        bg.type == BackgroundObjectType.FLEET_CROSSING
    val isSiegeStructure = bg.type == BackgroundObjectType.CASTLE_WALL ||
        bg.type == BackgroundObjectType.CASTLE_GATE
    val bgScale = if (isLowLandscape) 1.25f else 1.7f
    val verticalScale = if (isSiegeStructure) 1f else bgScale
    val lift = if (isLowLandscape || isSiegeStructure) 0f else 14f
    scope.withTransform({
        scale(scaleFactor * bgScale, scaleFactor * verticalScale, pivot = Offset(scaledPosX, cy))
    }) {
        drawDamageDecals(scope, scaledPosX, cy - lift, bg)
    }
}

internal fun renderBackgroundObject(scope: DrawScope, bg: BackgroundObject, cx: Float, cy: Float) {
    when (bg.type) {
        BackgroundObjectType.SHIP -> drawShip(scope, cx, cy, bg)
        BackgroundObjectType.FORT_PALACE -> drawFortPalace(scope, cx, cy, bg)
        BackgroundObjectType.FORT_DINAN -> drawFortDinan(scope, cx, cy, bg)
        BackgroundObjectType.BUILDING_BOSHAM -> drawBuildingBosham(scope, cx, cy, bg)
        BackgroundObjectType.BUILDING_MANOR -> drawBuildingManor(scope, cx, cy, bg)
        BackgroundObjectType.FORT_TOWER -> drawFortTower(scope, cx, cy, bg)
        BackgroundObjectType.FORT_MOTTE -> drawFortMotte(scope, cx, cy, bg)
        BackgroundObjectType.BUILDING_BAYEUX -> drawBayeuxBuilding(scope, cx, cy, bg)
        BackgroundObjectType.TOWER_SPIRAL -> drawSpiralTower(scope, cx, cy, bg)
        BackgroundObjectType.CASTLE_WALL -> drawCastleWall(scope, cx, cy, bg)
        BackgroundObjectType.CASTLE_GATE -> drawCastleGate(scope, cx, cy, bg.hp, bg.maxHp)
        BackgroundObjectType.MOTTE -> drawSiegeMotte(scope, cx, cy, bg)
        BackgroundObjectType.FEASTING_HALL -> drawFeastingHall(scope, cx, cy, bg)
        BackgroundObjectType.INTERIOR_KITCHEN -> drawInteriorKitchen(scope, cx, cy, bg)
        BackgroundObjectType.INTERIOR_CHAMBER -> drawInteriorChamber(scope, cx, cy, bg)
        BackgroundObjectType.FLEET_CROSSING -> drawFleetCrossing(scope, cx, cy, bg)
        BackgroundObjectType.MONT_SAINT_MICHEL -> drawMontSaintMichel(scope, cx, cy, bg)
        BackgroundObjectType.STAMFORD_BRIDGE -> drawStamfordBridge(scope, cx, cy, bg)
        BackgroundObjectType.FIELD_TREE -> drawFieldTree(scope, cx, cy, bg)
        BackgroundObjectType.FIELD_GRASS -> drawFieldGrass(scope, cx, cy, bg)
        BackgroundObjectType.HILL_SLOPE -> {} // drawn full-width in the render loop, never as a bitmap
        BackgroundObjectType.VECTOR -> {
            bg.artId?.let { id ->
                VectorAsset.cached(id)?.let { asset ->
                    scope.drawVectorAsset(asset, cx, cy)
                    asset.anchors["palisade"]?.let { a ->
                        drawPalisadeRun(scope, cx + a.x0, cx + a.x1, cy + a.y, 40f, bg.seed)
                    }
                }
            }
        }
        BackgroundObjectType.BROKEN_CHARIOT -> {
            scope.withTransform({ translate(cx, cy) }) {
                drawChariot(this, 0f, 0f, null, isCollapsed = true)
            }
        }
    }

}

internal fun drawLiveCastleGate(
    scope: DrawScope,
    bg: BackgroundObject,
    scaledPosX: Float,
    scaleFactor: Float,
    hp: Float,
    maxHp: Float
) {
    val cy = 200f + 138f * scaleFactor
    scope.withTransform({
        scale(scaleFactor * 1.7f, scaleFactor, pivot = Offset(scaledPosX, cy))
    }) {
        drawCastleGate(scope, scaledPosX, cy, hp, maxHp)
    }
}

internal fun drawSiegeLadder(scope: DrawScope, scaledPosX: Float, scaleFactor: Float) {
    val ground = 200f + SiegeRules.GROUND_FEET_OFFSET * scaleFactor
    val top = SiegeRules.parapetFeetY(scaleFactor)
    val horizontalScale = scaleFactor * 1.7f
    val leftBottom = scaledPosX - 54f * horizontalScale
    val rightBottom = scaledPosX - 31f * horizontalScale
    val leftTop = scaledPosX - 18f * horizontalScale
    val rightTop = scaledPosX + 5f * horizontalScale
    scope.drawLine(Color(0xFF6B4B2D), Offset(leftBottom, ground), Offset(leftTop, top), strokeWidth = 6f * scaleFactor)
    scope.drawLine(Color(0xFF6B4B2D), Offset(rightBottom, ground), Offset(rightTop, top), strokeWidth = 6f * scaleFactor)
    for (i in 0..7) {
        val t = i / 7f
        val lx = leftBottom + (leftTop - leftBottom) * t
        val rx = rightBottom + (rightTop - rightBottom) * t
        val y = ground + (top - ground) * t
        scope.drawLine(ThreadColor, Offset(lx - 4f * scaleFactor, y), Offset(rx + 4f * scaleFactor, y), strokeWidth = 4f * scaleFactor)
    }
    scope.drawLine(Color(0xFFD1B878), Offset(leftBottom, ground), Offset(leftTop, top), strokeWidth = 1.2f * scaleFactor)
    scope.drawLine(Color(0xFFD1B878), Offset(rightBottom, ground), Offset(rightTop, top), strokeWidth = 1.2f * scaleFactor)
}

internal fun drawDamageDecals(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {

        val random = kotlin.random.Random(bg.seed)
        // Only arrow-type hits leave shafts; melee/stones/mud leave no marks.
        // Arrows embed pointing the way they were actually fired.
        val fromLeftCount = bg.stuckArrowsFromLeft.coerceAtMost(8)
        val fromRightCount = bg.stuckArrowsFromRight.coerceAtMost(8)
        val numDecals = fromLeftCount + fromRightCount

        for (i in 0 until numDecals) {
            val dx = cx + random.nextFloat() * bg.width - (bg.width / 2f)
            val dy = cy - 20f - random.nextFloat() * 150f

            // Shaft angled as if shot in from the side, slight downward arc
            val fromLeft = i < fromLeftCount
            val dirX = (if (fromLeft) 1f else -1f) * (0.85f + random.nextFloat() * 0.1f)
            val dirY = 0.35f + random.nextFloat() * 0.25f
            val mag = kotlin.math.hypot(dirX.toDouble(), dirY.toDouble()).toFloat()
            val nx = dirX / mag; val ny = dirY / mag
            val tailX = dx - nx * 30f; val tailY = dy - ny * 30f

            scope.drawLine(Color(0xFF8A5E38), Offset(tailX, tailY), Offset(dx, dy), strokeWidth = 4.5f)
            // Fletching at the tail
            val perpX = -ny; val perpY = nx
            scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX - nx * 7f + perpX * 6f, tailY - ny * 7f + perpY * 6f), strokeWidth = 3f)
            scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX - nx * 7f - perpX * 6f, tailY - ny * 7f - perpY * 6f), strokeWidth = 3f)
        }
    }
    
internal fun drawShip(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Deep hull with slight curve
        val hullPath = Path().apply {
            moveTo(cx - 150f, cy - 80f) // Back
            lineTo(cx - 120f, cy + 30f) // Steep back
            quadraticTo(cx, cy + 50f, cx + 140f, cy + 30f) // Curved bottom
            lineTo(cx + 190f, cy - 90f) // Prow base
            lineTo(cx + 160f, cy - 70f)
            lineTo(cx - 120f, cy - 60f) // Inner deck
            close()
        }
        drawShipBody(scope, cx, cy, hullPath)
    }

internal fun drawCastleWall(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val stone = Color(0xFFC8B58D)
        val shadow = Color(0xFF8B7658)
        val wall = Path().apply {
            moveTo(cx - 230f, cy + 20f)
            lineTo(cx - 230f, cy - 142f)
            for (i in 0 until 12) {
                val x = cx - 230f + i * 40f
                lineTo(x, cy - 162f)
                lineTo(x + 20f, cy - 162f)
                lineTo(x + 20f, cy - 142f)
                lineTo(x + 40f, cy - 142f)
            }
            lineTo(cx + 230f, cy + 20f)
            close()
        }
        drawStitchedFill(scope, wall, stone)
        scope.drawPath(wall, ThreadColor, style = StitchedStroke)

        val parapetY = cy + SiegeRules.GROUND_FEET_OFFSET +
            SiegeRules.PARAPET_ELEVATION_OFFSET - 138f
        scope.drawRect(shadow, Offset(cx - 230f, parapetY), androidx.compose.ui.geometry.Size(460f, 12f))
        scope.drawLine(ThreadColor, Offset(cx - 230f, parapetY + 12f), Offset(cx + 230f, parapetY + 12f), strokeWidth = 2.5f)
        for (row in 0..4) {
            val y = cy - 75f + row * 23f
            scope.drawLine(Color(0x55382F22), Offset(cx - 225f, y), Offset(cx + 225f, y), strokeWidth = 1.5f)
            val offset = if (row % 2 == 0) 0f else 22f
            var x = cx - 210f + offset
            while (x < cx + 220f) {
                scope.drawLine(Color(0x55382F22), Offset(x, y - 22f), Offset(x, y), strokeWidth = 1.2f)
                x += 44f
            }
        }

        for (side in listOf(-1f, 1f)) {
            val tx = cx + side * 196f
            val tower = Path().apply {
                moveTo(tx - 34f, cy + 20f)
                lineTo(tx - 34f, cy - 178f)
                lineTo(tx - 18f, cy - 178f)
                lineTo(tx - 18f, cy - 198f)
                lineTo(tx + 2f, cy - 198f)
                lineTo(tx + 2f, cy - 178f)
                lineTo(tx + 34f, cy - 178f)
                lineTo(tx + 34f, cy + 20f)
                close()
            }
            drawStitchedFill(scope, tower, if (side < 0) Color(0xFFD6C49C) else Color(0xFFC1AD83))
            scope.drawPath(tower, ThreadColor, style = StitchedStroke)
            scope.drawCircle(Color(0xFF4B3A2A), 7f, Offset(tx, cy - 150f))
            scope.drawLine(Color(0xFFD6A420), Offset(tx, cy - 198f), Offset(tx, cy - 232f), strokeWidth = 2.5f)
            val pennant = Path().apply {
                moveTo(tx, cy - 232f); lineTo(tx + side * 30f, cy - 223f); lineTo(tx, cy - 214f); close()
            }
            drawStitchedFill(scope, pennant, if (side < 0) Color(0xFF9E3624) else Color(0xFF265063))
            scope.drawPath(pennant, ThreadColor, style = Stroke(1.5f))
        }
}

internal fun drawCastleGate(scope: DrawScope, cx: Float, cy: Float, hp: Float, maxHp: Float) {
        val state = castleGateDamageState(hp, maxHp)
        val arch = Path().apply {
            moveTo(cx - 58f, cy + 20f)
            lineTo(cx - 58f, cy - 58f)
            quadraticTo(cx - 58f, cy - 112f, cx, cy - 118f)
            quadraticTo(cx + 58f, cy - 112f, cx + 58f, cy - 58f)
            lineTo(cx + 58f, cy + 20f)
            close()
        }
        drawStitchedFill(scope, arch, Color(0xFF6D5941))
        scope.drawPath(arch, ThreadColor, style = Stroke(5f))

        if (state == CastleGateDamageState.BREACHED) {
            listOf(-47f, -27f, 31f, 49f).forEachIndexed { index, x ->
                scope.drawLine(
                    Color(0xFF5D3D25),
                    Offset(cx + x, cy + 10f),
                    Offset(cx + x + if (index % 2 == 0) 7f else -6f, cy - 52f - index * 5f),
                    strokeWidth = 10f
                )
            }
            for (i in 0..6) {
                val x = cx - 58f + i * 19f
                scope.drawLine(Color(0xFF806344), Offset(x, cy + 17f), Offset(x + 13f, cy + 7f), strokeWidth = 7f)
            }
            return
        }

        val timber = if (state == CastleGateDamageState.SHATTERED) Color(0xFF70492D) else Color(0xFF825D37)
        for (i in 0 until 8) {
            val x = cx - 49f + i * 14f
            val top = cy - 73f - (52f - kotlin.math.abs(x - cx)) * 0.62f
            scope.drawLine(timber, Offset(x, cy + 14f), Offset(x, top), strokeWidth = 11f)
            scope.drawLine(Color(0xFFB48A55), Offset(x - 2f, cy + 8f), Offset(x - 2f, top + 5f), strokeWidth = 1.4f)
        }
        listOf(cy - 25f, cy - 58f).forEach { y ->
            scope.drawLine(Color(0xFF40362D), Offset(cx - 51f, y), Offset(cx + 51f, y), strokeWidth = 7f)
        }
        scope.drawCircle(Color(0xFFC29B3D), 6f, Offset(cx + 17f, cy - 39f))

        val cracks = when (state) {
            CastleGateDamageState.INTACT -> 0
            CastleGateDamageState.SPLINTERED -> 3
            CastleGateDamageState.SHATTERED -> 7
            CastleGateDamageState.BREACHED -> 0
        }
        repeat(cracks) { i ->
            val x = cx - 40f + (i * 17f) % 77f
            val y = cy - 18f - (i * 23f) % 70f
            val crack = Path().apply {
                moveTo(x, y)
                lineTo(x + if (i % 2 == 0) 9f else -8f, y + 8f)
                lineTo(x + if (i % 2 == 0) 3f else -2f, y + 18f)
            }
            scope.drawPath(crack, Color(0xFF2F251C), style = Stroke(2.5f))
        }
}

internal fun drawSiegeMotte(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val mound = Path().apply {
            moveTo(cx - 170f, cy + 24f)
            quadraticTo(cx - 130f, cy - 34f, cx - 78f, cy - 72f)
            lineTo(cx + 72f, cy - 72f)
            quadraticTo(cx + 128f, cy - 32f, cx + 170f, cy + 24f)
            close()
        }
        drawStitchedFill(scope, mound, Color(0xFF74804D))
        scope.drawPath(mound, ThreadColor, style = StitchedStroke)
        for (i in 0..5) {
            val y = cy - 4f - i * 10f
            scope.drawLine(Color(0x555D4831), Offset(cx - 118f + i * 8f, y), Offset(cx + 120f - i * 10f, y), 1.5f)
        }
        drawPalisadeRun(scope, cx - 72f, cx + 72f, cy - 70f, 42f, bg.seed)
        val keep = Path().apply {
            moveTo(cx - 42f, cy - 110f); lineTo(cx - 34f, cy - 176f)
            lineTo(cx + 34f, cy - 176f); lineTo(cx + 42f, cy - 110f); close()
        }
        drawStitchedFill(scope, keep, Color(0xFF795B38))
        scope.drawPath(keep, ThreadColor, style = StitchedStroke)
        scope.drawLine(ThreadColor, Offset(cx - 38f, cy - 112f), Offset(cx + 32f, cy - 174f), 2f)
        scope.drawLine(ThreadColor, Offset(cx + 38f, cy - 112f), Offset(cx - 32f, cy - 174f), 2f)
        val roof = Path().apply {
            moveTo(cx - 48f, cy - 174f); lineTo(cx, cy - 214f); lineTo(cx + 48f, cy - 174f); close()
        }
        drawStitchedFill(scope, roof, Color(0xFF265063))
        scope.drawPath(roof, ThreadColor, style = StitchedStroke)
}

internal fun drawFeastingHall(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val rng = Random(bg.seed)
        val beam = Color(0xFF5C4029)
        val plaster = Color(0xFFD8C49B)
        val back = Path().apply {
            moveTo(cx - 235f, cy + 28f); lineTo(cx - 235f, cy - 145f)
            lineTo(cx, cy - 214f); lineTo(cx + 235f, cy - 145f); lineTo(cx + 235f, cy + 28f); close()
        }
        drawStitchedFill(scope, back, plaster)
        scope.drawPath(back, ThreadColor, style = StitchedStroke)
        scope.drawLine(beam, Offset(cx - 230f, cy - 143f), Offset(cx, cy - 208f), 8f)
        scope.drawLine(beam, Offset(cx, cy - 208f), Offset(cx + 230f, cy - 143f), 8f)
        scope.drawLine(beam, Offset(cx, cy - 205f), Offset(cx, cy + 20f), 7f)
        for (i in -2..2) {
            val sx = cx + i * 75f
            val shieldColor = if ((i + rng.nextInt(2)) % 2 == 0) Color(0xFF9E3624) else Color(0xFF265063)
            scope.drawCircle(shieldColor, 22f, Offset(sx, cy - 113f))
            scope.drawCircle(ThreadColor, 22f, Offset(sx, cy - 113f), style = StitchedStroke)
            scope.drawCircle(Color(0xFFD6A420), 6f, Offset(sx, cy - 113f))
        }
        val table = Path().apply {
            moveTo(cx - 190f, cy - 27f); lineTo(cx + 190f, cy - 27f)
            lineTo(cx + 165f, cy + 5f); lineTo(cx - 165f, cy + 5f); close()
        }
        drawStitchedFill(scope, table, Color(0xFF8B6037))
        scope.drawPath(table, ThreadColor, style = StitchedStroke)
        listOf(-145f, 145f).forEach { x ->
            scope.drawLine(
                beam,
                Offset(cx + x, cy + 2f),
                Offset(cx + x + if (x < 0f) 10f else -10f, cy + 36f),
                8f
            )
        }
        for (i in -3..3) {
            val px = cx + i * 48f
            scope.drawOval(
                Color(0xFFC49A43),
                Offset(px - 13f, cy - 36f),
                androidx.compose.ui.geometry.Size(26f, 9f)
            )
            if (i % 2 == 0) {
                scope.drawLine(Color(0xFF8F3328), Offset(px, cy - 39f), Offset(px + 8f, cy - 53f), 4f)
            }
        }
}

/** Shared interior shell: plastered gable walls and roof beams, same footprint as the feast hall. */
private fun drawInteriorShell(scope: DrawScope, cx: Float, cy: Float) {
    val beam = Color(0xFF5C4029)
    val plaster = Color(0xFFD8C49B)
    val back = Path().apply {
        moveTo(cx - 235f, cy + 28f); lineTo(cx - 235f, cy - 145f)
        lineTo(cx, cy - 214f); lineTo(cx + 235f, cy - 145f); lineTo(cx + 235f, cy + 28f); close()
    }
    drawStitchedFill(scope, back, plaster)
    scope.drawPath(back, ThreadColor, style = StitchedStroke)
    scope.drawLine(beam, Offset(cx - 230f, cy - 143f), Offset(cx, cy - 208f), 8f)
    scope.drawLine(beam, Offset(cx, cy - 208f), Offset(cx + 230f, cy - 143f), 8f)
}

internal fun drawInteriorKitchen(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val rng = Random(bg.seed)
    val beam = Color(0xFF5C4029)
    drawInteriorShell(scope, cx, cy)
    // Great hearth on the left: stone arch, fire, and a hanging cauldron
    val hearth = Path().apply {
        moveTo(cx - 210f, cy + 25f); lineTo(cx - 210f, cy - 85f)
        lineTo(cx - 95f, cy - 85f); lineTo(cx - 95f, cy + 25f); close()
    }
    drawStitchedFill(scope, hearth, Color(0xFF8A8478))
    scope.drawPath(hearth, ThreadColor, style = StitchedStroke)
    scope.drawOval(Color(0xFF2C2219), Offset(cx - 190f, cy - 55f), androidx.compose.ui.geometry.Size(75f, 75f))
    scope.drawOval(Color(0xFFE07020), Offset(cx - 178f, cy - 20f), androidx.compose.ui.geometry.Size(50f, 40f))
    scope.drawOval(Color(0xFFFFC34D), Offset(cx - 165f, cy - 8f), androidx.compose.ui.geometry.Size(24f, 24f))
    scope.drawLine(beam, Offset(cx - 152f, cy - 85f), Offset(cx - 152f, cy - 48f), 3f)
    scope.drawArc(Color(0xFF4C5154), 0f, 180f, false, Offset(cx - 172f, cy - 60f), androidx.compose.ui.geometry.Size(40f, 34f))
    // Hanging pots and herbs along the ridge
    for (i in 0..4) {
        val px = cx - 40f + i * 55f
        scope.drawLine(beam, Offset(px, cy - 160f + i % 2 * 8f), Offset(px, cy - 128f), 2f)
        if (i % 2 == 0) {
            scope.drawArc(Color(0xFF636A6E), 0f, 180f, true, Offset(px - 14f, cy - 132f), androidx.compose.ui.geometry.Size(28f, 22f))
        } else {
            scope.drawCircle(Color(0xFF4C613D), 10f, Offset(px, cy - 122f))
        }
    }
    // Prep table on the right with loaves, a cleaver and a shelf above
    val table = Path().apply {
        moveTo(cx + 30f, cy - 27f); lineTo(cx + 205f, cy - 27f)
        lineTo(cx + 190f, cy + 5f); lineTo(cx + 45f, cy + 5f); close()
    }
    drawStitchedFill(scope, table, Color(0xFF8B6037))
    scope.drawPath(table, ThreadColor, style = StitchedStroke)
    scope.drawLine(beam, Offset(cx + 60f, cy + 2f), Offset(cx + 66f, cy + 36f), 8f)
    scope.drawLine(beam, Offset(cx + 178f, cy + 2f), Offset(cx + 172f, cy + 36f), 8f)
    for (i in 0..2) {
        scope.drawOval(Color(0xFFC49A43), Offset(cx + 55f + i * 45f, cy - 40f), androidx.compose.ui.geometry.Size(30f, 12f))
    }
    scope.drawLine(Color(0xFF8C969E), Offset(cx + 150f, cy - 42f), Offset(cx + 180f, cy - 32f), 5f)
    scope.drawLine(beam, Offset(cx + 40f, cy - 95f), Offset(cx + 200f, cy - 95f), 6f)
    for (i in 0..3) {
        val jarCol = if (rng.nextBoolean()) Color(0xFF265063) else Color(0xFF9E3624)
        scope.drawRect(jarCol, Offset(cx + 55f + i * 38f, cy - 118f), androidx.compose.ui.geometry.Size(16f, 22f))
    }
}

internal fun drawInteriorChamber(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val rng = Random(bg.seed)
    val beam = Color(0xFF5C4029)
    drawInteriorShell(scope, cx, cy)
    // A wall tapestry (of course) between two candle sconces
    val tap = Path().apply {
        moveTo(cx - 70f, cy - 165f); lineTo(cx + 70f, cy - 165f)
        lineTo(cx + 70f, cy - 90f); lineTo(cx - 70f, cy - 90f); close()
    }
    drawStitchedFill(scope, tap, if (rng.nextBoolean()) Color(0xFF9E3624) else Color(0xFF265063))
    scope.drawPath(tap, ThreadColor, style = StitchedStroke)
    scope.drawLine(Color(0xFFD6A420), Offset(cx - 45f, cy - 140f), Offset(cx + 45f, cy - 140f), 4f)
    scope.drawLine(Color(0xFFD6A420), Offset(cx - 45f, cy - 118f), Offset(cx + 45f, cy - 118f), 4f)
    listOf(-110f, 110f).forEach { x ->
        scope.drawLine(beam, Offset(cx + x, cy - 120f), Offset(cx + x, cy - 100f), 4f)
        scope.drawCircle(Color(0xFFFFC34D), 7f, Offset(cx + x, cy - 128f))
    }
    // Curtained bed on the left
    val bed = Path().apply {
        moveTo(cx - 215f, cy + 20f); lineTo(cx - 215f, cy - 30f)
        lineTo(cx - 80f, cy - 30f); lineTo(cx - 80f, cy + 20f); close()
    }
    drawStitchedFill(scope, bed, Color(0xFF632873))
    scope.drawPath(bed, ThreadColor, style = StitchedStroke)
    scope.drawOval(Color(0xFFEFE6D4), Offset(cx - 205f, cy - 42f), androidx.compose.ui.geometry.Size(42f, 20f))
    scope.drawLine(beam, Offset(cx - 215f, cy - 30f), Offset(cx - 215f, cy - 95f), 6f)
    scope.drawLine(beam, Offset(cx - 80f, cy - 30f), Offset(cx - 80f, cy - 95f), 6f)
    scope.drawLine(beam, Offset(cx - 218f, cy - 95f), Offset(cx - 77f, cy - 95f), 5f)
    // Chairs and a small table on the right
    val table = Path().apply {
        moveTo(cx + 55f, cy - 20f); lineTo(cx + 150f, cy - 20f)
        lineTo(cx + 140f, cy + 5f); lineTo(cx + 65f, cy + 5f); close()
    }
    drawStitchedFill(scope, table, Color(0xFF8B6037))
    scope.drawPath(table, ThreadColor, style = StitchedStroke)
    scope.drawLine(beam, Offset(cx + 75f, cy + 2f), Offset(cx + 78f, cy + 34f), 7f)
    scope.drawLine(beam, Offset(cx + 130f, cy + 2f), Offset(cx + 127f, cy + 34f), 7f)
    listOf(20f, 175f).forEach { x ->
        scope.drawLine(beam, Offset(cx + x, cy + 34f), Offset(cx + x, cy - 45f), 6f) // chair back
        scope.drawLine(beam, Offset(cx + x, cy - 2f), Offset(cx + x + 26f, cy - 2f), 6f) // seat
        scope.drawLine(beam, Offset(cx + x + 26f, cy - 2f), Offset(cx + x + 26f, cy + 34f), 6f)
    }
    scope.drawCircle(Color(0xFFD6A420), 8f, Offset(cx + 100f, cy - 30f)) // goblet on the table
}

internal fun drawFleetCrossing(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val sea = Path().apply {
            moveTo(cx - 270f, cy - 10f)
            for (i in 0..18) {
                val x = cx - 270f + i * 30f
                lineTo(x, cy - 10f + sin((i + bg.seed % 5) * 1.3f) * 7f)
            }
            lineTo(cx + 270f, cy + 78f); lineTo(cx - 270f, cy + 78f); close()
        }
        drawStitchedFill(scope, sea, Color(0xFF315F70))
        scope.drawPath(sea, ThreadColor, style = StitchedStroke)
        for (row in 0..3) {
            val y = cy + 8f + row * 17f
            for (i in 0..8) {
                val x = cx - 250f + i * 63f + if (row % 2 == 0) 0f else 25f
                scope.drawLine(Color(0xFFB6D0CB), Offset(x, y), Offset(x + 29f, y + sin(i.toFloat()) * 3f), 2f)
            }
        }
        scope.withTransform({ scale(0.52f, 0.52f, Offset(cx - 105f, cy - 17f)) }) {
            drawShip(scope, cx - 105f, cy - 17f, bg.copy(id = "${bg.id}_near"))
        }
        scope.withTransform({ scale(0.38f, 0.38f, Offset(cx + 135f, cy - 44f)) }) {
            drawShip(scope, cx + 135f, cy - 44f, bg.copy(id = "${bg.id}_far", seed = bg.seed + 1))
        }
}

/**
 * The hill slope, drawn full-width in the same transformed space as the fighters so its crest sits
 * exactly under their lifted feet ([HillField.liftAt] drives both). Called from the battle render
 * loop, not the per-object bitmap path.
 */
internal fun drawHillTerrain(
    scope: DrawScope,
    hill: HillState,
    levelWidth: Float,
    playerScaleX: Float,
    scaleFactor: Float
) {
    val feetY = 200f + 158f * scaleFactor
    val bottomY = feetY + 600f
    val steps = 48
    val slope = Path().apply {
        moveTo(0f, bottomY)
        for (i in 0..steps) {
            val wx = levelWidth * i / steps
            lineTo(wx * playerScaleX, feetY + HillField.liftAt(wx, hill) * scaleFactor)
        }
        lineTo(levelWidth * playerScaleX, bottomY)
        close()
    }
    drawStitchedFill(scope, slope, Color(0xFF6E8A4E))
    scope.drawPath(slope, ThreadColor, style = StitchedStroke)
    // A lighter ridge line along the crown for depth
    val ridge = Path().apply {
        for (i in 0..steps) {
            val wx = levelWidth * i / steps
            val y = feetY + HillField.liftAt(wx, hill) * scaleFactor
            if (i == 0) moveTo(wx * playerScaleX, y) else lineTo(wx * playerScaleX, y)
        }
    }
    scope.drawPath(ridge, Color(0x55FFF4D0), style = Stroke(3f))
}

internal fun drawFieldTree(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val rng = kotlin.random.Random(bg.seed)
    val leaf = listOf(Color(0xFF4C613D), Color(0xFF5E7A46), Color(0xFF3F5233), Color(0xFF6E8A4E))[rng.nextInt(4)]
    val trunkCol = Color(0xFF6B4B2D)
    fun trunk(topY: Float, halfBase: Float) {
        val t = Path().apply {
            moveTo(cx - halfBase, cy + 20f)
            lineTo(cx - halfBase * 0.55f, topY)
            lineTo(cx + halfBase * 0.55f, topY)
            lineTo(cx + halfBase, cy + 20f)
            close()
        }
        drawStitchedFill(scope, t, trunkCol)
        scope.drawPath(t, ThreadColor, style = StitchedStroke)
    }
    when (rng.nextInt(3)) {
        0 -> {
            // Round-canopy oak: overlapping lobes
            trunk(cy - 70f, 10f)
            val lobes = 5 + rng.nextInt(3)
            for (i in 0 until lobes) {
                val a = (i / lobes.toFloat()) * (2f * Math.PI).toFloat()
                scope.drawCircle(leaf, 42f + rng.nextFloat() * 14f,
                    Offset(cx + kotlin.math.cos(a) * 34f, cy - 96f + kotlin.math.sin(a) * 30f))
            }
            scope.drawCircle(leaf, 52f, Offset(cx, cy - 100f))
        }
        1 -> {
            // Tall conifer: stacked triangles
            trunk(cy - 40f, 8f)
            for (tier in 0 until 4) {
                val ty = cy - 40f - tier * 34f
                val hw = 56f - tier * 11f
                val tri = Path().apply {
                    moveTo(cx - hw, ty); lineTo(cx, ty - 46f); lineTo(cx + hw, ty); close()
                }
                drawStitchedFill(scope, tri, leaf)
                scope.drawPath(tri, ThreadColor, style = StitchedStroke)
            }
        }
        else -> {
            // Broad bushy pollard: wide low crown
            trunk(cy - 50f, 12f)
            listOf(-46f, 0f, 46f).forEachIndexed { i, dx ->
                scope.drawCircle(leaf, if (i == 1) 56f else 44f, Offset(cx + dx, cy - 78f - if (i == 1) 14f else 0f))
            }
        }
    }
}

internal fun drawFieldGrass(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val rng = kotlin.random.Random(bg.seed)
    val blades = 6 + rng.nextInt(5)
    val col = listOf(Color(0xFF5E7A46), Color(0xFF4C613D), Color(0xFF6E8A4E))[rng.nextInt(3)]
    for (i in 0 until blades) {
        val bx = cx - 40f + rng.nextFloat() * 80f
        val h = 26f + rng.nextFloat() * 26f
        val lean = -10f + rng.nextFloat() * 20f
        val blade = Path().apply {
            moveTo(bx, cy + 20f)
            quadraticTo(bx + lean * 0.5f, cy + 20f - h * 0.6f, bx + lean, cy + 20f - h)
        }
        scope.drawPath(blade, col, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

internal fun drawStamfordBridge(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val water = Path().apply {
        moveTo(cx - 280f, cy + 22f)
        for (i in 0..14) {
            val x = cx - 280f + i * 40f
            lineTo(x, cy + 22f + sin((i + bg.seed % 7) * 1.2f) * 6f)
        }
        lineTo(cx + 280f, cy + 78f)
        lineTo(cx - 280f, cy + 78f)
        close()
    }
    drawStitchedFill(scope, water, Color(0xFF315F70))
    scope.drawPath(water, ThreadColor, style = StitchedStroke)

    val bridge = Path().apply {
        moveTo(cx - 270f, cy - 12f)
        lineTo(cx + 270f, cy - 12f)
        lineTo(cx + 245f, cy + 24f)
        lineTo(cx - 245f, cy + 24f)
        close()
    }
    drawStitchedFill(scope, bridge, Color(0xFF8B6037))
    scope.drawPath(bridge, ThreadColor, style = StitchedStroke)
    for (i in -5..5) {
        val x = cx + i * 46f
        scope.drawLine(Color(0xFF5C4029), Offset(x, cy - 9f), Offset(x - 4f, cy + 18f), 4f)
    }
    listOf(-210f, 210f).forEach { x ->
        scope.drawLine(Color(0xFF5C4029), Offset(cx + x, cy + 18f), Offset(cx + x, cy + 70f), 10f)
    }
}

internal fun drawMontSaintMichel(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val sand = Path().apply {
            moveTo(cx - 270f, cy + 2f)
            for (i in 0..18) {
                val x = cx - 270f + i * 30f
                lineTo(x, cy + 2f + sin((i + bg.seed % 7) * 0.9f) * 5f)
            }
            lineTo(cx + 270f, cy + 72f); lineTo(cx - 270f, cy + 72f); close()
        }
        drawStitchedFill(scope, sand, Color(0xFFB39B68))
        scope.drawPath(sand, ThreadColor, style = StitchedStroke)
        for (i in 0..8) {
            val x = cx - 235f + i * 58f
            val y = cy + 20f + (i % 3) * 12f
            val swirl = Path().apply {
                moveTo(x - 18f, y)
                quadraticTo(x, y - 9f, x + 18f, y)
                quadraticTo(x, y + 8f, x - 10f, y + 2f)
            }
            scope.drawPath(swirl, Color(0xFF6E7454), style = Stroke(2.2f))
        }
        val mount = Path().apply {
            moveTo(cx - 170f, cy + 4f)
            quadraticTo(cx - 130f, cy - 50f, cx - 78f, cy - 85f)
            quadraticTo(cx - 35f, cy - 132f, cx, cy - 142f)
            quadraticTo(cx + 45f, cy - 116f, cx + 83f, cy - 82f)
            quadraticTo(cx + 136f, cy - 48f, cx + 170f, cy + 4f)
            close()
        }
        drawStitchedFill(scope, mount, Color(0xFF73764E))
        scope.drawPath(mount, ThreadColor, style = StitchedStroke)
        for (tier in 0..2) {
            val half = 68f - tier * 17f
            val bottom = cy - 80f - tier * 31f
            val abbey = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(cx - half, bottom - 32f, cx + half, bottom))
            }
            drawStitchedFill(scope, abbey, if (tier % 2 == 0) Color(0xFFD3C29D) else Color(0xFFC0AD87))
            scope.drawPath(abbey, ThreadColor, style = StitchedStroke)
            for (i in -2..2) {
                scope.drawCircle(Color(0xFF4A4438), 3.2f, Offset(cx + i * half / 3f, bottom - 16f))
            }
        }
        val spire = Path().apply {
            moveTo(cx - 14f, cy - 173f); lineTo(cx, cy - 226f); lineTo(cx + 14f, cy - 173f); close()
        }
        drawStitchedFill(scope, spire, Color(0xFF5D666B))
        scope.drawPath(spire, ThreadColor, style = StitchedStroke)
        scope.drawLine(Color(0xFFD6A420), Offset(cx, cy - 226f), Offset(cx, cy - 240f), 2f)
}

private fun drawShipBody(scope: DrawScope, cx: Float, cy: Float, hullPath: Path) {
        drawStitchedFill(scope, hullPath, Color(0xFF5D4831))
        scope.drawPath(hullPath, ThreadColor, style = StitchedStroke)
        
        // Planking lines
        for (i in 0 until 3) {
            val yOffset = cy - 40f + (i * 20f)
            scope.drawLine(Color(0x55000000), Offset(cx - 120f, yOffset), Offset(cx + 140f, yOffset), strokeWidth = 2f)
        }

        // Dragon Prow
        val prowPath = Path().apply {
            moveTo(cx + 190f, cy - 90f)
            quadraticTo(cx + 220f, cy - 120f, cx + 230f, cy - 140f) // Neck
            quadraticTo(cx + 250f, cy - 150f, cx + 240f, cy - 130f) // Snout
            quadraticTo(cx + 220f, cy - 110f, cx + 210f, cy - 80f) // Back of neck
        }
        drawStitchedFill(scope, prowPath, Color(0xFF8C6F47))
        scope.drawPath(prowPath, ThreadColor, style = StitchedStroke)
        scope.drawCircle(Color.Red, radius = 4f, center = Offset(cx + 235f, cy - 140f)) // Eye

        // Mast and Square Sail
        val mastPath = Path().apply {
            moveTo(cx + 20f, cy - 160f)
            lineTo(cx + 30f, cy - 160f)
            lineTo(cx + 30f, cy - 60f)
            lineTo(cx + 20f, cy - 60f)
            close()
        }
        drawStitchedFill(scope, mastPath, Color(0xFF382F22))
        scope.drawPath(mastPath, ThreadColor, style = StitchedStroke)

        val sailPath = Path().apply {
            moveTo(cx - 60f, cy - 150f)
            quadraticTo(cx + 25f, cy - 170f, cx + 110f, cy - 150f) // Top yard
            lineTo(cx + 90f, cy - 70f)
            quadraticTo(cx + 25f, cy - 50f, cx - 40f, cy - 70f) // Billowing bottom
            close()
        }
        drawStitchedFill(scope, sailPath, Color(0xFF9E3624))
        
        // Striped sail pattern
        scope.withTransform({ clipPath(sailPath) }) {
            val stripePath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(cx - 20f, cy - 180f, cx + 10f, cy - 40f))
                addRect(androidx.compose.ui.geometry.Rect(cx + 50f, cy - 180f, cx + 80f, cy - 40f))
            }
            drawStitchedFill(scope, stripePath, Color(0xFFD6A420))
        }
        scope.drawPath(sailPath, ThreadColor, style = StitchedStroke)
        
        // Overlapping shields on gunwale
        for (i in 0 until 8) {
            val sx = cx - 100f + (i * 32f)
            val sy = cy - 65f + sin(i * 0.4f) * 5f
            scope.drawCircle(if (i % 2 == 0) Color(0xFFD6A420) else Color(0xFF9E3624), radius = 15f, center = Offset(sx, sy))
            scope.drawCircle(ThreadColor, radius = 15f, center = Offset(sx, sy), style = StitchedStroke)
            scope.drawCircle(Color(0xFF9EA3A8), radius = 5f, center = Offset(sx, sy)) // Boss
        }
        
        // Water path overlapping hull bottom for submerged perspective
        val waterPath = Path().apply {
            moveTo(cx - 200f, cy + 20f)
            for (i in 0..400 step 20) {
                lineTo(cx - 200f + i, cy + 20f + sin(i * 0.1f) * 6f)
            }
            lineTo(cx + 200f, cy + 100f)
            lineTo(cx - 200f, cy + 100f)
            close()
        }
        drawStitchedFill(scope, waterPath, Color(0xFF265063))
        scope.drawPath(waterPath, ThreadColor, style = StitchedStroke)
    }
    
internal fun drawFortTower(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Tall timber watchtower on stilted legs with a lookout platform
        val wood = Color(0xFF8C6F47)
        val darkWood = Color(0xFF5D4831)
        // Legs
        scope.drawLine(darkWood, Offset(cx - 45f, cy + 20f), Offset(cx - 25f, cy - 110f), strokeWidth = 9f)
        scope.drawLine(darkWood, Offset(cx + 45f, cy + 20f), Offset(cx + 25f, cy - 110f), strokeWidth = 9f)
        // Cross-brace
        scope.drawLine(darkWood, Offset(cx - 38f, cy - 30f), Offset(cx + 38f, cy - 65f), strokeWidth = 5f)
        scope.drawLine(darkWood, Offset(cx + 38f, cy - 30f), Offset(cx - 38f, cy - 65f), strokeWidth = 5f)
        // Cabin
        val cabin = Path().apply {
            moveTo(cx - 40f, cy - 110f)
            lineTo(cx + 40f, cy - 110f)
            lineTo(cx + 34f, cy - 165f)
            lineTo(cx - 34f, cy - 165f)
            close()
        }
        drawStitchedFill(scope, cabin, wood)
        scope.drawPath(cabin, ThreadColor, style = StitchedStroke)
        // Battlement notches
        for (i in 0 until 4) {
            val bx = cx - 30f + i * 20f
            scope.drawRect(darkWood, topLeft = Offset(bx, cy - 178f), size = androidx.compose.ui.geometry.Size(10f, 13f))
        }
        // Pointed roof with pennant
        val roof = Path().apply {
            moveTo(cx - 38f, cy - 165f)
            lineTo(cx + 38f, cy - 165f)
            lineTo(cx, cy - 210f)
            close()
        }
        drawStitchedFill(scope, roof, Color(0xFF9E3624))
        scope.drawPath(roof, ThreadColor, style = StitchedStroke)
        scope.drawLine(darkWood, Offset(cx, cy - 210f), Offset(cx, cy - 235f), strokeWidth = 3f)
        val pennant = Path().apply {
            moveTo(cx, cy - 235f); lineTo(cx + 26f, cy - 228f); lineTo(cx, cy - 221f); close()
        }
        scope.drawPath(pennant, Color(0xFFD6A420))
        // Window
        scope.drawRect(Color(0xFF2C2219), topLeft = Offset(cx - 8f, cy - 150f), size = androidx.compose.ui.geometry.Size(16f, 22f))
    }

/**
 * A run of palisade: separate sharpened timber posts with gaps you can see daylight through, lashed
 * to two rails. Both forts share it.
 *
 * The old palisades were a single lumpy silhouette with spikes along the top, which is what made
 * them read as termite mounds rather than timber. Posts are jittered from [seed] so each fort's wall
 * is its own, but the same fort draws the same wall every frame (the bg bitmap cache demands it).
 */
internal fun drawPalisadeRun(scope: DrawScope, x0: Float, x1: Float, baseY: Float, height: Float, seed: Int) {
    val rng = Random(seed)
    val postW = 9f
    val gap = 3.5f

    var x = x0
    while (x < x1) {
        val h = height * (0.86f + rng.nextFloat() * 0.28f)  // uneven heights — no two trees alike
        val lean = (rng.nextFloat() - 0.5f) * 3.5f          // a few posts sit crooked
        val topY = baseY - h
        val post = Path().apply {
            moveTo(x, baseY)
            lineTo(x + lean, topY + 7f)
            lineTo(x + postW / 2f + lean, topY) // sharpened tip
            lineTo(x + postW + lean, topY + 7f)
            lineTo(x + postW, baseY)
            close()
        }
        drawStitchedFill(scope, post, if (rng.nextBoolean()) Color(0xFF735835) else Color(0xFF8C6F47))
        scope.drawPath(post, ThreadColor, style = StitchedStroke)
        // A single grain line down the middle of the trunk
        scope.drawLine(
            ThreadColor.copy(alpha = 0.35f),
            Offset(x + postW * 0.5f, topY + 11f),
            Offset(x + postW * 0.5f, baseY - 4f),
            strokeWidth = 1f
        )
        x += postW + gap
    }

    // Two horizontal lashing rails holding the run together
    listOf(baseY - height * 0.34f, baseY - height * 0.70f).forEach { railY ->
        scope.drawLine(Color(0xFF5D4831), Offset(x0 - 3f, railY), Offset(x1 + 3f, railY), strokeWidth = 3.5f)
    }
}

internal fun drawFortMotte(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Motte-and-bailey: broad flat-topped earthwork with a plank palisade and squat keep
        val mound = Path().apply {
            moveTo(cx - 130f, cy + 20f)
            quadraticTo(cx - 100f, cy - 55f, cx - 70f, cy - 62f) // curved left slope
            lineTo(cx + 70f, cy - 62f)                           // flat plateau
            quadraticTo(cx + 100f, cy - 55f, cx + 130f, cy + 20f) // curved right slope
            close()
        }
        drawStitchedFill(scope, mound, Color(0xFF6B7C4A))
        scope.drawPath(mound, ThreadColor, style = StitchedStroke)
        // Winding path up the right slope
        scope.drawLine(Color(0xFFB09A6C), Offset(cx + 100f, cy + 14f), Offset(cx + 55f, cy - 58f), strokeWidth = 8f)
        // Palisade of individual timbers around the plateau edge
        drawPalisadeRun(scope, cx - 62f, cx + 62f, baseY = cy - 60f, height = 36f, seed = bg.id.hashCode())
        // Keep — its base sits on the plateau (cy-62), standing behind the palisade rather than
        // floating on top of the fence.
        val keep = Path().apply {
            moveTo(cx - 34f, cy - 62f)
            lineTo(cx + 34f, cy - 62f)
            lineTo(cx + 28f, cy - 150f)
            lineTo(cx - 28f, cy - 150f)
            close()
        }
        drawStitchedFill(scope, keep, Color(0xFF8C6F47))
        scope.drawPath(keep, ThreadColor, style = StitchedStroke)
        // Keep roof
        val roof = Path().apply {
            moveTo(cx - 32f, cy - 150f)
            lineTo(cx + 32f, cy - 150f)
            lineTo(cx, cy - 188f)
            close()
        }
        drawStitchedFill(scope, roof, Color(0xFF265063))
        scope.drawPath(roof, ThreadColor, style = StitchedStroke)
        // Door + window
        scope.drawRect(Color(0xFF2C2219), topLeft = Offset(cx - 7f, cy - 86f), size = androidx.compose.ui.geometry.Size(14f, 24f))
        scope.drawRect(Color(0xFF2C2219), topLeft = Offset(cx - 5f, cy - 140f), size = androidx.compose.ui.geometry.Size(10f, 12f))
    }

internal fun drawFortPalace(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Stone Keep
        val keepPath = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(cx - 100f, cy - 150f, cx + 100f, cy + 20f))
        }
        drawStitchedFill(scope, keepPath, Color(0xFF8C969E))
        scope.drawPath(keepPath, ThreadColor, style = StitchedStroke)
        
        // Crenellations
        for (i in 0 until 5) {
            val creX = cx - 100f + (i * 40f)
            val crePath = Path().apply { addRect(androidx.compose.ui.geometry.Rect(creX, cy - 170f, creX + 20f, cy - 150f)) }
            drawStitchedFill(scope, crePath, Color(0xFF7A868C))
            scope.drawPath(crePath, ThreadColor, style = StitchedStroke)
        }
        
        // Arched Gate
        val gatePath = Path().apply {
            moveTo(cx - 30f, cy + 20f)
            lineTo(cx - 30f, cy - 50f)
            arcTo(androidx.compose.ui.geometry.Rect(cx - 30f, cy - 80f, cx + 30f, cy - 20f), 180f, 180f, false)
            lineTo(cx + 30f, cy + 20f)
            close()
        }
        drawStitchedFill(scope, gatePath, Color(0xFF452E1B))
        scope.drawPath(gatePath, ThreadColor, style = StitchedStroke)
        
        // Hinges
        scope.drawLine(ThreadColor, Offset(cx - 30f, cy - 30f), Offset(cx + 30f, cy - 30f), strokeWidth = 4f)
        scope.drawLine(ThreadColor, Offset(cx - 30f, cy), Offset(cx + 30f, cy), strokeWidth = 4f)
        
        // Masonry lines
        for (y in -130..10 step 30) {
            scope.drawLine(Color(0x33000000), Offset(cx - 100f, cy + y.toFloat()), Offset(cx + 100f, cy + y.toFloat()), strokeWidth = 2f)
        }
    }
    
internal fun drawFortDinan(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Motte: a flat-topped earthwork, not a smooth cone — the cone was the other half of the
        // "termite mound" look, since it gave the spikes nothing to stand on
        val hillPath = Path().apply {
            moveTo(cx - 150f, cy + 20f)
            quadraticTo(cx - 95f, cy - 30f, cx - 66f, cy - 58f)
            lineTo(cx + 66f, cy - 58f) // plateau for the wall to stand on
            quadraticTo(cx + 95f, cy - 30f, cx + 150f, cy + 20f)
            close()
        }
        drawStitchedFill(scope, hillPath, Color(0xFF5E4B3C))
        scope.drawPath(hillPath, ThreadColor, style = StitchedStroke)

        // Palisade of individual timbers along the plateau
        drawPalisadeRun(scope, cx - 60f, cx + 60f, baseY = cy - 58f, height = 48f, seed = bg.id.hashCode())
        
        // Wooden Tower
        val towerPath = Path().apply {
            moveTo(cx - 20f, cy - 100f)
            lineTo(cx - 10f, cy - 170f)
            lineTo(cx + 10f, cy - 170f)
            lineTo(cx + 20f, cy - 100f)
            close()
        }
        drawStitchedFill(scope, towerPath, Color(0xFF735835))
        scope.drawPath(towerPath, ThreadColor, style = StitchedStroke)
        
        // Crossbeams
        scope.drawLine(ThreadColor, Offset(cx - 20f, cy - 100f), Offset(cx + 10f, cy - 170f), strokeWidth = 2f)
        scope.drawLine(ThreadColor, Offset(cx + 20f, cy - 100f), Offset(cx - 10f, cy - 170f), strokeWidth = 2f)
        
        // Tower Roof
        val roofPath = Path().apply {
            moveTo(cx - 25f, cy - 165f)
            lineTo(cx, cy - 195f)
            lineTo(cx + 25f, cy - 165f)
            close()
        }
        drawStitchedFill(scope, roofPath, Color(0xFF9E3624))
        scope.drawPath(roofPath, ThreadColor, style = StitchedStroke)
    }

/** The tapestry's building palette — every procedural structure picks its walls from these. */
private val BAYEUX_WALLS = listOf(
    Color(0xFFEFE6D4), // plaster
    Color(0xFFE0CFA8), // ochre wash
    Color(0xFFD8C3A5), // pale clay
    Color(0xFFC9B48E)  // sand
)
private val BAYEUX_ROOFS = listOf(
    Color(0xFF636A6E), // slate
    Color(0xFF8C4A3A), // red tile
    Color(0xFF5D666B), // dark slate
    Color(0xFF7A6A4F)  // thatch-brown
)

/**
 * A Bayeux hall: arcade of arches on pillars, a pillared upper storey, and a roof of overlapping
 * scallop rows. Every dimension comes off bg.seed, so no two are the same building.
 */
internal fun drawBayeuxBuilding(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val rng = Random(bg.seed)
    val wall = BAYEUX_WALLS[rng.nextInt(BAYEUX_WALLS.size)]
    val roofCol = BAYEUX_ROOFS[rng.nextInt(BAYEUX_ROOFS.size)]
    val pillarCol = Color(0xFF7D838A)

    val halfW = 60f + rng.nextFloat() * 30f      // 60..90
    val bodyTop = cy - (100f + rng.nextFloat() * 40f) // -100..-140
    val arches = 2 + rng.nextInt(3)             // 2..4 openings

    // Body
    val body = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(cx - halfW, bodyTop, cx + halfW, cy + 20f))
    }
    drawStitchedFill(scope, body, wall)
    scope.drawPath(body, ThreadColor, style = StitchedStroke)

    // Ground-floor arcade: slim round-headed openings with plenty of wall between them, so the
    // arcade reads as arches rather than a row of dark tombstones
    val bay = (halfW * 2f - 20f) / arches
    for (i in 0 until arches) {
        val archSpan = bay * 0.5f
        val ax = cx - halfW + 10f + i * bay + (bay - archSpan) / 2f
        val archTop = cy - 14f - rng.nextFloat() * 10f
        val opening = Path().apply {
            moveTo(ax, cy + 20f)
            lineTo(ax, archTop)
            quadraticTo(ax + archSpan / 2f, archTop - archSpan * 0.8f, ax + archSpan, archTop)
            lineTo(ax + archSpan, cy + 20f)
            close()
        }
        drawStitchedFill(scope, opening, Color(0xFF6B5B48)) // shadowed, not black
        scope.drawPath(opening, ThreadColor, style = StitchedStroke)

        // The arch ring, picked out over the shadow
        val ring = Path().apply {
            moveTo(ax, archTop)
            quadraticTo(ax + archSpan / 2f, archTop - archSpan * 0.8f, ax + archSpan, archTop)
        }
        scope.drawPath(ring, ThreadColor, style = Stroke(width = 2.5f))

        // Squat pillar flanking the opening
        val pPath = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(ax - 7f, cy - 6f, ax - 1f, cy + 20f))
        }
        drawStitchedFill(scope, pPath, pillarCol)
        scope.drawPath(pPath, ThreadColor, style = StitchedStroke)
    }

    // Upper storey: a colonnade of slim pillars under the eaves
    val upperCount = arches + 1
    val upperGap = (halfW * 2f - 16f) / upperCount
    for (i in 0 until upperCount) {
        val px = cx - halfW + 8f + i * upperGap
        val col = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(px, bodyTop + 12f, px + 8f, cy - 42f))
        }
        drawStitchedFill(scope, col, pillarCol)
        scope.drawPath(col, ThreadColor, style = StitchedStroke)
        // Little round-headed arch over each
        val arc = Path().apply {
            moveTo(px - 4f, bodyTop + 12f)
            quadraticTo(px + 4f, bodyTop - 6f, px + 12f, bodyTop + 12f)
        }
        scope.drawPath(arc, ThreadColor, style = Stroke(width = 2.5f))
    }

    // Pitched roof of scallop rows
    val ridge = bodyTop - (55f + rng.nextFloat() * 30f)
    val eaveW = halfW + 16f
    val roof = Path().apply {
        moveTo(cx - eaveW, bodyTop)
        lineTo(cx, ridge)
        lineTo(cx + eaveW, bodyTop)
        close()
    }
    drawStitchedFill(scope, roof, roofCol)
    scope.drawPath(roof, ThreadColor, style = StitchedStroke)

    val rows = ((bodyTop - ridge) / 15f).toInt().coerceIn(2, 6)
    for (r in 0 until rows) {
        val ry = ridge + 12f + r * 15f
        val spanAtY = (ry - ridge) / (bodyTop - ridge) * eaveW
        var sx = -spanAtY + (if (r % 2 == 0) 0f else 9f) // offset alternate rows so tiles overlap
        while (sx < spanAtY) {
            scope.drawCircle(roofCol.copy(alpha = 0.55f), radius = 7f, center = Offset(cx + sx, ry))
            scope.drawCircle(ThreadColor, radius = 7f, center = Offset(cx + sx, ry), style = Stroke(width = 1.4f))
            sx += 18f
        }
    }
}

/**
 * The tapestry's Rapunzel tower: tall and narrow, tiled bands spiralling up it, arched windows and
 * a conical cap with a pennant.
 */
internal fun drawSpiralTower(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    val rng = Random(bg.seed)
    val wall = BAYEUX_WALLS[rng.nextInt(BAYEUX_WALLS.size)]
    val roofCol = BAYEUX_ROOFS[rng.nextInt(BAYEUX_ROOFS.size)]

    val halfW = 26f + rng.nextFloat() * 10f
    val topY = cy - (200f + rng.nextFloat() * 90f) // 2-3x a building's height
    val taper = halfW * 0.75f                      // narrows toward the top

    val shaft = Path().apply {
        moveTo(cx - halfW, cy + 20f)
        lineTo(cx - taper, topY)
        lineTo(cx + taper, topY)
        lineTo(cx + halfW, cy + 20f)
        close()
    }
    drawStitchedFill(scope, shaft, wall)
    scope.drawPath(shaft, ThreadColor, style = StitchedStroke)

    // Tiled bands, each row's scallops shifted so they read as a spiral climbing the tower
    val bandStep = 22f
    var by = cy + 6f
    var row = 0
    val phase = rng.nextFloat() * 10f
    while (by > topY + 12f) {
        val t = (by - topY) / (cy + 20f - topY)
        val w = taper + (halfW - taper) * t
        var sx = -w + ((row * 7f + phase) % 14f) // phase shift per row = the spiral
        while (sx < w - 3f) {
            scope.drawCircle(roofCol.copy(alpha = 0.5f), radius = 5.5f, center = Offset(cx + sx, by))
            scope.drawCircle(ThreadColor.copy(alpha = 0.7f), radius = 5.5f, center = Offset(cx + sx, by), style = Stroke(width = 1.2f))
            sx += 14f
        }
        by -= bandStep
        row++
    }

    // Arched windows up the shaft
    val windows = 2 + rng.nextInt(2)
    for (i in 0 until windows) {
        val wy = topY + 40f + i * ((cy - topY) / (windows + 1f))
        val win = Path().apply {
            moveTo(cx - 6f, wy)
            lineTo(cx - 6f, wy - 10f)
            quadraticTo(cx, wy - 20f, cx + 6f, wy - 10f)
            lineTo(cx + 6f, wy)
            close()
        }
        drawStitchedFill(scope, win, Color(0xFF3B332A))
        scope.drawPath(win, ThreadColor, style = StitchedStroke)
    }

    // Conical cap
    val cap = Path().apply {
        moveTo(cx - taper - 8f, topY)
        lineTo(cx, topY - 46f)
        lineTo(cx + taper + 8f, topY)
        close()
    }
    drawStitchedFill(scope, cap, roofCol)
    scope.drawPath(cap, ThreadColor, style = StitchedStroke)

    // Pennant
    scope.drawLine(ThreadColor, Offset(cx, topY - 46f), Offset(cx, topY - 74f), strokeWidth = 2.5f)
    val flag = Path().apply {
        moveTo(cx, topY - 74f)
        lineTo(cx + 26f, topY - 67f)
        lineTo(cx, topY - 58f)
        close()
    }
    drawStitchedFill(scope, flag, Color(0xFF9E3624))
    scope.drawPath(flag, ThreadColor, style = StitchedStroke)
}

internal fun drawBuildingBosham(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Pillared Hall (Bosham church style)
        val buildingPath = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(cx - 80f, cy - 120f, cx + 80f, cy + 20f))
        }
        drawStitchedFill(scope, buildingPath, Color(0xFFEFE6D4)) // Plaster
        scope.drawPath(buildingPath, ThreadColor, style = StitchedStroke)
        
        // Pillars and arches
        val pillarColor = Color(0xFF7D838A)
        for (i in 0 until 3) {
            val px = cx - 60f + (i * 50f)
            val pPath = Path().apply { addRect(androidx.compose.ui.geometry.Rect(px, cy - 100f, px + 20f, cy + 20f)) }
            drawStitchedFill(scope, pPath, pillarColor)
            scope.drawPath(pPath, ThreadColor, style = StitchedStroke)
            
            // Arch above pillars
            val archPath = Path().apply {
                moveTo(px - 15f, cy - 100f)
                quadraticTo(px + 10f, cy - 140f, px + 35f, cy - 100f)
            }
            scope.drawPath(archPath, ThreadColor, style = Stroke(width = 4f))
        }
        
        // Shingle Roof
        val roofPath = Path().apply {
            moveTo(cx - 100f, cy - 120f)
            lineTo(cx, cy - 200f)
            lineTo(cx + 100f, cy - 120f)
            close()
        }
        drawStitchedFill(scope, roofPath, Color(0xFF636A6E))
        scope.drawPath(roofPath, ThreadColor, style = StitchedStroke)
        
        // Roof Tiles
        for (y in -190..-130 step 15) {
            val widthAtY = (y + 200f) * 1.25f
            for (x in (-widthAtY.toInt()..widthAtY.toInt() step 20)) {
                scope.drawCircle(Color(0xFF5D666B), radius = 8f, center = Offset(cx + x.toFloat(), cy + y.toFloat()))
                scope.drawCircle(ThreadColor, radius = 8f, center = Offset(cx + x.toFloat(), cy + y.toFloat()), style = Stroke(width = 1.5f))
            }
        }
    }

// Timber Manor (Mead Hall) — art now lives in assets/art/building_manor.json, editable in the
// vector builder. Nothing about it is seeded, so it ports cleanly with no code left behind.
internal fun drawBuildingManor(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        VectorAsset.cached("building_manor")?.let { scope.drawVectorAsset(it, cx, cy) }
    }
