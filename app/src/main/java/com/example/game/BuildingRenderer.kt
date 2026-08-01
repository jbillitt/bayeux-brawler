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
        BackgroundObjectType.DOMED_TOWER -> drawDomedTower(scope, cx, cy)
        BackgroundObjectType.ABBEY_NAVE -> drawAbbeyNave(scope, cx, cy)
        BackgroundObjectType.ECCLESIA -> drawEcclesia(scope, cx, cy)
        BackgroundObjectType.PALACE_ARCH -> drawPalaceArch(scope, cx, cy)
        BackgroundObjectType.BELL_TOWER -> drawBellTower(scope, cx, cy)
        BackgroundObjectType.CLOISTER_WALK -> drawCloisterWalk(scope, cx, cy)
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
        // Rungs are lashed on, not jointed in — a pair of binding turns at each end
        scope.drawLine(Color(0xFFD1B878), Offset(lx - 2f * scaleFactor, y - 2f * scaleFactor), Offset(lx + 3f * scaleFactor, y + 2f * scaleFactor), strokeWidth = 1.2f * scaleFactor)
        scope.drawLine(Color(0xFFD1B878), Offset(rx - 3f * scaleFactor, y - 2f * scaleFactor), Offset(rx + 2f * scaleFactor, y + 2f * scaleFactor), strokeWidth = 1.2f * scaleFactor)
    }
    scope.drawLine(Color(0xFFD1B878), Offset(leftBottom, ground), Offset(leftTop, top), strokeWidth = 1.2f * scaleFactor)
    scope.drawLine(Color(0xFFD1B878), Offset(rightBottom, ground), Offset(rightTop, top), strokeWidth = 1.2f * scaleFactor)
    // Iron hooks over the parapet, so the ladder grips the wall rather than leaning on air
    listOf(leftTop to leftBottom, rightTop to rightBottom).forEach { (tx, bx) ->
        val dx = (tx - bx)
        val len = kotlin.math.hypot(dx.toDouble(), (top - ground).toDouble()).toFloat()
        val ux = if (len == 0f) 0f else dx / len
        val uy = if (len == 0f) -1f else (top - ground) / len
        val hook = Path().apply {
            moveTo(tx, top)
            lineTo(tx + ux * 9f * scaleFactor, top + uy * 9f * scaleFactor)
            quadraticTo(
                tx + ux * 14f * scaleFactor + 7f * scaleFactor, top + uy * 14f * scaleFactor,
                tx + ux * 9f * scaleFactor + 9f * scaleFactor, top + uy * 4f * scaleFactor
            )
        }
        scope.drawPath(hook, Color(0xFF4C5154), style = Stroke(width = 3.5f * scaleFactor, cap = StrokeCap.Round))
    }
}

internal fun drawDamageDecals(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
    if (bg.stuckBuildingArrows.isNotEmpty()) {
        for (arrow in bg.stuckBuildingArrows) {
            val dx = cx + arrow.offsetX
            val dy = cy + arrow.offsetY
            val nx = kotlin.math.cos(arrow.angle.toDouble()).toFloat()
            val ny = kotlin.math.sin(arrow.angle.toDouble()).toFloat()

            // Embed at the size it flew at. Every shaft was drawn full arrow size, so a Lil Guy
            // dart (0.5) that read as a needle in the air stuck out of the wall as a full arrow.
            val s = arrow.sizeMultiplier.coerceIn(0.35f, 2.5f)

            val tailX = dx - nx * 32f * s
            val tailY = dy - ny * 32f * s
            val headX = dx + nx * 10f * s
            val headY = dy + ny * 10f * s

            scope.drawLine(Color(0xFF8A5E38), Offset(tailX, tailY), Offset(headX, headY), strokeWidth = 4.5f * s)
            scope.drawLine(ThreadColor, Offset(tailX, tailY), Offset(headX, headY), strokeWidth = 1.5f * s)

            // Fletching at the tail
            val perpX = -ny; val perpY = nx
            scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX - (nx * 8f - perpX * 6f) * s, tailY - (ny * 8f - perpY * 6f) * s), strokeWidth = 3f * s)
            scope.drawLine(Color.White, Offset(tailX, tailY), Offset(tailX - (nx * 8f + perpX * 6f) * s, tailY - (ny * 8f + perpY * 6f) * s), strokeWidth = 3f * s)
        }
        return
    }

    val random = kotlin.random.Random(bg.seed)
    // Only arrow-type hits leave shafts; melee/stones/mud leave no marks.
    // Arrows embed pointing the way they were actually fired (shallow flight arc within tight distribution).
    val fromLeftCount = bg.stuckArrowsFromLeft.coerceAtMost(8)
    val fromRightCount = bg.stuckArrowsFromRight.coerceAtMost(8)
    val numDecals = fromLeftCount + fromRightCount

    for (i in 0 until numDecals) {
        val dx = cx + random.nextFloat() * bg.width - (bg.width / 2f)
        val dy = cy - 20f - random.nextFloat() * 150f

        val fromLeft = i < fromLeftCount
        val dirX = if (fromLeft) 0.96f else -0.96f
        val dirY = 0.12f + (random.nextFloat() - 0.5f) * 0.15f
        val mag = kotlin.math.hypot(dirX.toDouble(), dirY.toDouble()).toFloat()
        val nx = dirX / mag; val ny = dirY / mag
        val tailX = dx - nx * 30f; val tailY = dy - ny * 30f
        val headX = dx + nx * 8f; val headY = dy + ny * 8f

        scope.drawLine(Color(0xFF8A5E38), Offset(tailX, tailY), Offset(headX, headY), strokeWidth = 4.5f)
        scope.drawLine(ThreadColor, Offset(tailX, tailY), Offset(headX, headY), strokeWidth = 1.5f)
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
        scope.drawStitchedOutline(wall, ThreadColor)

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
            scope.drawStitchedOutline(tower, ThreadColor)
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
        scope.drawStitchedOutline(mound, ThreadColor)
        for (i in 0..5) {
            val y = cy - 4f - i * 10f
            scope.drawLine(Color(0x555D4831), Offset(cx - 118f + i * 8f, y), Offset(cx + 120f - i * 10f, y), 1.5f)
        }
        // No palisade. The keep used to start 38px above the plateau and a procedural fence was
        // run across the gap to hide the float — which is why the siege motte always wore a
        // wooden collar that belonged to nothing. The keep stands on the earth it is built on.
        val stone = Color(0xFFE8DCC0)
        val band = Color(0xFFB08221)
        val dark = Color(0xFF3B332A)
        val keep = Path().apply {
            moveTo(cx - 46f, cy - 72f); lineTo(cx - 38f, cy - 178f)
            lineTo(cx + 38f, cy - 178f); lineTo(cx + 46f, cy - 72f); close()
        }
        drawStitchedFill(scope, keep, stone)
        scope.drawStitchedOutline(keep, ThreadColor)
        // String courses divide the tower into storeys, as the tapestry's towers are drawn
        listOf(cy - 108f, cy - 146f).forEach { y ->
            scope.drawLine(band, Offset(cx - 44f, y), Offset(cx + 44f, y), strokeWidth = 4f)
            scope.drawLine(ThreadColor, Offset(cx - 44f, y), Offset(cx + 44f, y), strokeWidth = 1.5f)
        }
        // Round-arched door, and a pair of openings in the upper storey
        val door = Path().apply {
            moveTo(cx - 13f, cy - 72f); lineTo(cx - 13f, cy - 96f)
            quadraticTo(cx, cy - 116f, cx + 13f, cy - 96f)
            lineTo(cx + 13f, cy - 72f); close()
        }
        drawStitchedFill(scope, door, dark)
        scope.drawStitchedOutline(door, ThreadColor)
        for (i in 0 until 2) {
            val wx = cx - 16f + i * 32f
            val win = Path().apply {
                moveTo(wx - 7f, cy - 150f); lineTo(wx - 7f, cy - 164f)
                quadraticTo(wx, cy - 174f, wx + 7f, cy - 164f)
                lineTo(wx + 7f, cy - 150f); close()
            }
            drawStitchedFill(scope, win, dark)
            scope.drawStitchedOutline(win, ThreadColor)
        }
        // Hipped roof with a lozenge lattice, matching the abbey and the palace
        val roof = Path().apply {
            moveTo(cx - 54f, cy - 178f); lineTo(cx, cy - 222f); lineTo(cx + 54f, cy - 178f); close()
        }
        drawStitchedFill(scope, roof, Color(0xFF6B7882))
        scope.drawStitchedOutline(roof, ThreadColor)
        // No lattice on this one: the hatch lines ran past the two top corners of the gable and
        // stuck out as grey whiskers against the sky. A small hipped roof reads fine plain.
        scope.drawLine(ThreadColor, Offset(cx, cy - 222f), Offset(cx, cy - 244f), strokeWidth = 3f)
        val pennant = Path().apply {
            moveTo(cx, cy - 244f); lineTo(cx + 26f, cy - 237f); lineTo(cx, cy - 230f); close()
        }
        drawStitchedFill(scope, pennant, Color(0xFF9E3624))
        scope.drawStitchedOutline(pennant, ThreadColor)
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
        scope.drawStitchedOutline(back, ThreadColor)
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
        scope.drawStitchedOutline(table, ThreadColor)
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
/**
 * The room every interior is staged in: back wall, roof timbers, floor. Shared, so an improvement
 * here lifts the kitchen and the chamber together rather than being pasted into each.
 *
 * It was a bare plaster pentagon with two sloping beams, which gave the furniture nothing to sit
 * against — the contents read as loose objects floating on a wall. It now has a floor line to
 * stand on, a blind arcade along the back, and a proper truss, so the props have a room.
 */
internal fun drawInteriorShell(scope: DrawScope, cx: Float, cy: Float) {
    val beam = Color(0xFF5C4029)
    val plaster = Color(0xFFD8C49B)
    val stone = Color(0xFFE8DCC0)
    val band = Color(0xFFB08221)
    val floorY = cy + 6f

    val back = Path().apply {
        moveTo(cx - 235f, cy + 28f); lineTo(cx - 235f, cy - 145f)
        lineTo(cx, cy - 214f); lineTo(cx + 235f, cy - 145f); lineTo(cx + 235f, cy + 28f); close()
    }
    drawStitchedFill(scope, back, plaster)
    scope.drawStitchedOutline(back, ThreadColor)

    // Blind arcade along the back wall — the tapestry never leaves an interior wall blank, and it
    // gives the eye a depth cue behind the furniture.
    for (i in 0 until 9) {
        val ax = cx - 208f + i * 52f
        val blind = Path().apply {
            moveTo(ax - 18f, floorY); lineTo(ax - 18f, cy - 60f)
            quadraticTo(ax, cy - 96f, ax + 18f, cy - 60f)
            lineTo(ax + 18f, floorY); close()
        }
        drawStitchedFill(scope, blind, stone)
        scope.drawPath(blind, ThreadColor.copy(alpha = 0.55f), style = Stroke(width = 1.5f))
    }

    // Floor: a band of boards the props stand on, so nothing floats
    val floor = Path().apply {
        moveTo(cx - 235f, floorY); lineTo(cx + 235f, floorY)
        lineTo(cx + 235f, cy + 28f); lineTo(cx - 235f, cy + 28f); close()
    }
    drawStitchedFill(scope, floor, Color(0xFF8B6037))
    scope.drawStitchedOutline(floor, ThreadColor)
    for (i in 0 until 10) {
        val x = cx - 212f + i * 47f
        scope.drawLine(ThreadColor.copy(alpha = 0.3f), Offset(x, floorY + 2f), Offset(x - 8f, cy + 26f), strokeWidth = 1.5f)
    }

    // Roof truss: rafters, a ridge, a tie beam and a king post
    scope.drawLine(beam, Offset(cx - 230f, cy - 143f), Offset(cx, cy - 208f), 8f)
    scope.drawLine(beam, Offset(cx, cy - 208f), Offset(cx + 230f, cy - 143f), 8f)
    scope.drawLine(beam, Offset(cx - 226f, cy - 150f), Offset(cx + 226f, cy - 150f), 7f)   // tie beam
    scope.drawLine(beam, Offset(cx, cy - 150f), Offset(cx, cy - 206f), 6f)                 // king post
    listOf(-1f, 1f).forEach { s ->
        scope.drawLine(beam, Offset(cx + s * 8f, cy - 176f), Offset(cx + s * 96f, cy - 150f), 5f) // braces
    }
    // Wall plate the whole truss lands on
    scope.drawLine(band, Offset(cx - 235f, cy - 145f), Offset(cx + 235f, cy - 145f), 4f)
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
    scope.drawStitchedOutline(hearth, ThreadColor)
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
    scope.drawStitchedOutline(table, ThreadColor)
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
    scope.drawStitchedOutline(tap, ThreadColor)
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
    scope.drawStitchedOutline(bed, ThreadColor)
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
    scope.drawStitchedOutline(table, ThreadColor)
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
        scope.drawStitchedOutline(sea, ThreadColor)
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
    scope.drawStitchedOutline(slope, ThreadColor)
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
        scope.drawStitchedOutline(t, ThreadColor)
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
                scope.drawStitchedOutline(tri, ThreadColor)
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
    scope.drawStitchedOutline(water, ThreadColor)

    val bridge = Path().apply {
        moveTo(cx - 270f, cy - 12f)
        lineTo(cx + 270f, cy - 12f)
        lineTo(cx + 245f, cy + 24f)
        lineTo(cx - 245f, cy + 24f)
        close()
    }
    drawStitchedFill(scope, bridge, Color(0xFF8B6037))
    scope.drawStitchedOutline(bridge, ThreadColor)
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
        scope.drawStitchedOutline(sand, ThreadColor)
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
        scope.drawStitchedOutline(mount, ThreadColor)
        // Rock strata across the mount, so it reads as a crag and not a green hill
        for (i in 0..4) {
            val y = cy - 14f - i * 22f
            val half = 150f - i * 24f
            scope.drawLine(ThreadColor.copy(alpha = 0.3f), Offset(cx - half, y), Offset(cx + half - 20f, y - 6f), strokeWidth = 1.5f)
        }

        val stone = Color(0xFFE8DCC0)
        val slate = Color(0xFF6B7882)
        val band = Color(0xFFB08221)
        val dark = Color(0xFF3B332A)

        // The abbey climbs the rock in three arcaded ranges. It used to be three plain rectangles
        // with five dots punched in each and a grey triangle on top — the only structure in the set
        // with no arch, no roof and no string course, which is why it read as a wedding cake.
        // Ranges are STAGGERED, not stacked concentrically. Three centred boxes of decreasing
        // width is a wedding cake whatever you decorate it with; the island reads as a town on a
        // rock because its ranges step sideways as they climb.
        // (half-width, floor, bays, x-offset)
        data class Range(val half: Float, val floorY: Float, val bays: Int, val dx: Float)
        val tiers = listOf(
            Range(78f, cy - 70f, 6, -14f),
            Range(54f, cy - 112f, 4, 22f),
            Range(38f, cy - 150f, 3, -10f)
        )
        tiers.forEachIndexed { tier, r ->
            val mx = cx + r.dx
            val wallTop = r.floorY - 30f
            val roofTop = wallTop - 14f
            val range = Path().apply {
                moveTo(mx - r.half, r.floorY); lineTo(mx - r.half, wallTop)
                lineTo(mx + r.half, wallTop); lineTo(mx + r.half, r.floorY); close()
            }
            drawStitchedFill(scope, range, stone)
            scope.drawStitchedOutline(range, ThreadColor)
            // Arcade of round-arched openings along the range
            for (i in 0 until r.bays) {
                val ax = mx - r.half + (2f * r.half) * (i + 0.5f) / r.bays
                val op = Path().apply {
                    moveTo(ax - 6f, r.floorY); lineTo(ax - 6f, wallTop + 14f)
                    quadraticTo(ax, wallTop + 2f, ax + 6f, wallTop + 14f)
                    lineTo(ax + 6f, r.floorY); close()
                }
                drawStitchedFill(scope, op, dark)
                scope.drawPath(op, ThreadColor.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
            }
            // Lean roof over each range, with a gold eaves course under it. The hatch runs only
            // between the interior bay lines — carried to the ends it overshot the roof's top
            // corners and stuck out as whiskers.
            val roof = Path().apply {
                moveTo(mx - r.half - 7f, wallTop); lineTo(mx - r.half + 4f, roofTop)
                lineTo(mx + r.half - 4f, roofTop); lineTo(mx + r.half + 7f, wallTop); close()
            }
            drawStitchedFill(scope, roof, slate)
            scope.drawStitchedOutline(roof, ThreadColor)
            for (i in 1..r.bays) {
                val x = mx - r.half + (2f * r.half) * i / (r.bays + 1f)
                scope.drawLine(ThreadColor.copy(alpha = 0.45f), Offset(x, wallTop), Offset(x + 8f, roofTop), strokeWidth = 1.3f)
            }
            scope.drawLine(band, Offset(mx - r.half - 8f, wallTop), Offset(mx + r.half + 8f, wallTop), strokeWidth = 3.5f)
            if (tier == 0) {
                // Gate at the foot of the climb
                val gate = Path().apply {
                    moveTo(mx - 13f, r.floorY); lineTo(mx - 13f, r.floorY - 20f)
                    quadraticTo(mx, r.floorY - 38f, mx + 13f, r.floorY - 20f)
                    lineTo(mx + 13f, r.floorY); close()
                }
                drawStitchedFill(scope, gate, dark)
                scope.drawStitchedOutline(gate, ThreadColor)
            }
        }

        // The church on the summit, set off-centre over the topmost range
        val sx0 = cx - 10f
        val naveTop = cy - 206f
        val nave = Path().apply {
            moveTo(sx0 - 26f, cy - 164f); lineTo(sx0 - 26f, naveTop + 16f)
            lineTo(sx0, naveTop); lineTo(sx0 + 26f, naveTop + 16f)
            lineTo(sx0 + 26f, cy - 164f); close()
        }
        drawStitchedFill(scope, nave, stone)
        scope.drawStitchedOutline(nave, ThreadColor)
        for (i in 0 until 2) {
            val wx = sx0 - 11f + i * 22f
            val win = Path().apply {
                moveTo(wx - 5f, cy - 168f); lineTo(wx - 5f, cy - 184f)
                quadraticTo(wx, cy - 193f, wx + 5f, cy - 184f)
                lineTo(wx + 5f, cy - 168f); close()
            }
            drawStitchedFill(scope, win, dark)
            scope.drawStitchedOutline(win, ThreadColor)
        }
        val spire = Path().apply {
            moveTo(sx0 - 20f, naveTop + 4f); lineTo(sx0, cy - 284f); lineTo(sx0 + 20f, naveTop + 4f); close()
        }
        drawStitchedFill(scope, spire, slate)
        scope.drawStitchedOutline(spire, ThreadColor)
        // Scale courses up the spire, the same fish-scale the towers wear
        for (row in 1 until 4) {
            val t = row / 4f
            val sy = naveTop + 4f - (naveTop + 4f - (cy - 284f)) * t
            val halfW = 20f * (1f - t)
            scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(sx0 - halfW, sy), Offset(sx0 + halfW, sy), strokeWidth = 1.3f)
        }
        // St Michael himself, in gold, as he stands on the real spire
        scope.drawLine(band, Offset(sx0, cy - 284f), Offset(sx0, cy - 302f), strokeWidth = 3f)
        val michael = Path().apply {
            moveTo(sx0, cy - 302f); lineTo(sx0 + 13f, cy - 296f); lineTo(sx0, cy - 290f); close()
        }
        drawStitchedFill(scope, michael, band)
        scope.drawStitchedOutline(michael, ThreadColor)
}

private fun drawShipBody(scope: DrawScope, cx: Float, cy: Float, hullPath: Path) {
        drawStitchedFill(scope, hullPath, Color(0xFF5D4831))
        scope.drawStitchedOutline(hullPath, ThreadColor)
        
        // Planking lines
        for (i in 0 until 3) {
            val yOffset = cy - 40f + (i * 20f)
            scope.drawLine(Color(0x55000000), Offset(cx - 120f, yOffset), Offset(cx + 140f, yOffset), strokeWidth = 2f)
        }

        // Dragon Prow. On a real ship the beast's neck IS the stempost — one continuous carved
        // timber rising out of the keel — so it must grow out of the planking, not perch on it.
        //
        // This used to start at the hull's topmost point and come back down to (+210, -80), a foot
        // hanging in open air beyond the hull with a neck two lines wide between them. It read as
        // a head balanced on a wire. The foot is now buried well down inside the hull, between the
        // waterline and the deck, and the closing edge runs back through the planking — the hull
        // is drawn first, so the overlap merges instead of butting.
        val prowPath = Path().apply {
            moveTo(cx + 148f, cy + 12f)                             // foot, deep in the planking
            quadraticTo(cx + 178f, cy - 60f, cx + 206f, cy - 116f)  // back of the neck, sweeping up
            quadraticTo(cx + 224f, cy - 142f, cx + 252f, cy - 136f) // crown of the head
            lineTo(cx + 268f, cy - 128f)                            // snout
            quadraticTo(cx + 246f, cy - 112f, cx + 234f, cy - 100f) // under the jaw
            quadraticTo(cx + 220f, cy - 60f, cx + 200f, cy - 16f)   // front of the neck, coming down
            close()                                                 // broad base through the hull
        }
        drawStitchedFill(scope, prowPath, Color(0xFF8C6F47))
        scope.drawStitchedOutline(prowPath, ThreadColor)
        scope.drawCircle(Color.Red, radius = 4f, center = Offset(cx + 240f, cy - 124f)) // Eye

        // Mast and Square Sail
        val mastPath = Path().apply {
            moveTo(cx + 20f, cy - 160f)
            lineTo(cx + 30f, cy - 160f)
            lineTo(cx + 30f, cy - 60f)
            lineTo(cx + 20f, cy - 60f)
            close()
        }
        drawStitchedFill(scope, mastPath, Color(0xFF382F22))
        scope.drawStitchedOutline(mastPath, ThreadColor)

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
        scope.drawStitchedOutline(sailPath, ThreadColor)
        
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
        scope.drawStitchedOutline(waterPath, ThreadColor)
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
        scope.drawStitchedOutline(cabin, ThreadColor)
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
        scope.drawStitchedOutline(roof, ThreadColor)
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
        scope.drawStitchedOutline(post, ThreadColor)
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
        scope.drawStitchedOutline(mound, ThreadColor)
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
        scope.drawStitchedOutline(keep, ThreadColor)
        // Keep roof
        val roof = Path().apply {
            moveTo(cx - 32f, cy - 150f)
            lineTo(cx + 32f, cy - 150f)
            lineTo(cx, cy - 188f)
            close()
        }
        drawStitchedFill(scope, roof, Color(0xFF265063))
        scope.drawStitchedOutline(roof, ThreadColor)
        // Door + window
        scope.drawRect(Color(0xFF2C2219), topLeft = Offset(cx - 7f, cy - 86f), size = androidx.compose.ui.geometry.Size(14f, 24f))
        scope.drawRect(Color(0xFF2C2219), topLeft = Offset(cx - 5f, cy - 140f), size = androidx.compose.ui.geometry.Size(10f, 12f))
    }

/**
 * The royal palace, as the tapestry draws one: a hall between two turrets, not a grey box with a
 * hole in it. It was previously a flat rectangle of masonry hatching with five square merlons and a
 * plank door — the only building left in the set with no round arch anywhere on it, which made it
 * read as a cartoon castle beside the traced work.
 *
 * Seedless and literal, like the rest of the traced set, so every number here can be dragged in
 * the vector builder.
 */
internal fun drawFortPalace(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val stone = Color(0xFFE8DCC0)
        val slate = Color(0xFF6B7882)
        val band = Color(0xFFB08221)
        val dark = Color(0xFF3B332A)
        val domeCol = Color(0xFF9E3624)
        val hallTop = cy - 118f
        val roofTop = cy - 156f

        // Hall block between the turrets
        val hall = Path().apply {
            moveTo(cx - 96f, cy + 20f); lineTo(cx - 96f, hallTop)
            lineTo(cx + 96f, hallTop); lineTo(cx + 96f, cy + 20f); close()
        }
        drawStitchedFill(scope, hall, stone)
        scope.drawStitchedOutline(hall, ThreadColor)

        // Lozenge-tiled roof band over the hall
        val roof = Path().apply {
            moveTo(cx - 106f, hallTop); lineTo(cx - 106f, roofTop)
            lineTo(cx + 106f, roofTop); lineTo(cx + 106f, hallTop); close()
        }
        drawStitchedFill(scope, roof, slate)
        scope.drawStitchedOutline(roof, ThreadColor)
        for (i in 0 until 7) {
            val x0 = cx - 106f + i * 30f
            scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(x0, hallTop), Offset(x0 + 30f, roofTop), strokeWidth = 1.5f)
            scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(x0 + 30f, hallTop), Offset(x0, roofTop), strokeWidth = 1.5f)
        }
        scope.drawLine(band, Offset(cx - 108f, hallTop), Offset(cx + 108f, hallTop), strokeWidth = 5f)
        scope.drawLine(ThreadColor, Offset(cx - 108f, hallTop), Offset(cx + 108f, hallTop), strokeWidth = 1.5f)

        // The great round-arched gate, its arch picked out in a gold order
        val gate = Path().apply {
            moveTo(cx - 30f, cy + 20f); lineTo(cx - 30f, cy - 34f)
            quadraticTo(cx, cy - 84f, cx + 30f, cy - 34f)
            lineTo(cx + 30f, cy + 20f); close()
        }
        drawStitchedFill(scope, gate, dark)
        scope.drawStitchedOutline(gate, ThreadColor)
        val order = Path().apply {
            moveTo(cx - 38f, cy - 32f)
            quadraticTo(cx, cy - 94f, cx + 38f, cy - 32f)
        }
        scope.drawPath(order, band, style = Stroke(width = 5f))
        scope.drawPath(order, ThreadColor, style = Stroke(width = 1.5f))

        // One blind arch either side of the gate. Two a side overlapped the gate's gold order,
        // which read as an arch growing out of the doorway: the wall only has 58px of clear
        // masonry between the order and the turret, and that is one bay, not two.
        listOf(-67f, 67f).forEach { dx ->
            val ax = cx + dx
            val blind = Path().apply {
                moveTo(ax - 19f, cy + 20f); lineTo(ax - 19f, cy - 24f)
                quadraticTo(ax, cy - 58f, ax + 19f, cy - 24f)
                lineTo(ax + 19f, cy + 20f); close()
            }
            drawStitchedFill(scope, blind, slate)
            scope.drawStitchedOutline(blind, ThreadColor)
        }
        // Upper windows, above the arcade and below the eaves
        for (i in 0 until 5) {
            val wx = cx - 68f + i * 34f
            val win = Path().apply {
                moveTo(wx - 7f, cy - 68f); lineTo(wx - 7f, cy - 88f)
                quadraticTo(wx, cy - 100f, wx + 7f, cy - 88f)
                lineTo(wx + 7f, cy - 68f); close()
            }
            drawStitchedFill(scope, win, dark)
            scope.drawStitchedOutline(win, ThreadColor)
        }

        // Two turrets under scaled domes, one at each end
        listOf(-1f, 1f).forEach { side ->
            val tx = cx + side * 118f
            val topY = cy - 178f
            val turret = Path().apply {
                moveTo(tx - 30f, cy + 20f); lineTo(tx - 25f, topY)
                lineTo(tx + 25f, topY); lineTo(tx + 30f, cy + 20f); close()
            }
            drawStitchedFill(scope, turret, stone)
            scope.drawStitchedOutline(turret, ThreadColor)
            listOf(cy - 60f, cy - 126f).forEach { y ->
                scope.drawLine(band, Offset(tx - 28f, y), Offset(tx + 28f, y), strokeWidth = 4f)
                scope.drawLine(ThreadColor, Offset(tx - 28f, y), Offset(tx + 28f, y), strokeWidth = 1.5f)
            }
            val op = Path().apply {
                moveTo(tx - 8f, cy - 90f); lineTo(tx - 8f, cy - 108f)
                quadraticTo(tx, cy - 120f, tx + 8f, cy - 108f)
                lineTo(tx + 8f, cy - 90f); close()
            }
            drawStitchedFill(scope, op, dark)
            scope.drawStitchedOutline(op, ThreadColor)
            // Fish-scale dome, the tapestry's standard cap for a tower of any importance
            val dome = Path().apply {
                moveTo(tx - 32f, topY)
                quadraticTo(tx - 29f, topY - 28f, tx, topY - 34f)
                quadraticTo(tx + 29f, topY - 28f, tx + 32f, topY)
                close()
            }
            drawStitchedFill(scope, dome, domeCol)
            scope.drawStitchedOutline(dome, ThreadColor)
            for (row in 0 until 2) {
                val sy = topY - 5f - row * 10f
                val halfW = 26f - row * 11f
                val count = 4 - row
                for (i in 0 until count) {
                    val sx = tx - halfW + i * (2f * halfW / (count - 1))
                    val scaleArc = Path().apply {
                        moveTo(sx - 6f, sy)
                        quadraticTo(sx, sy - 9f, sx + 6f, sy)
                    }
                    scope.drawPath(scaleArc, ThreadColor.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
                }
            }
            scope.drawLine(ThreadColor, Offset(tx, topY - 34f), Offset(tx, topY - 56f), strokeWidth = 3f)
            val pennant = Path().apply {
                moveTo(tx, topY - 56f); lineTo(tx + side * 24f, topY - 49f); lineTo(tx, topY - 42f); close()
            }
            drawStitchedFill(scope, pennant, band)
            scope.drawStitchedOutline(pennant, ThreadColor)
        }
    }
    
internal fun drawFortDinan(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Motte: a flat-topped earthwork, not a smooth cone — the cone was the other half of the
        // "termite mound" look, since it gave the spikes nothing to stand on
        val hillPath = Path().apply {
            moveTo(cx - 150f, cy + 20f)
            quadraticTo(cx - 95f, cy - 30f, cx - 66f, cy - 58f)
            lineTo(cx + 69.5f, cy - 59f) // plateau for the wall to stand on
            quadraticTo(cx + 95f, cy - 30f, cx + 150f, cy + 20f)
            close()
        }
        // Olive with ochre strata, not one flat brown. The tapestry never draws bare earth as a
        // solid slab — its mottes are built of stacked coloured bands, and a single dark brown
        // fill is what made this read as a floor with a spike on it rather than a hill.
        drawStitchedFill(scope, hillPath, Color(0xFF4C613D))
        // Half-widths taken off the hill's own taper (±150 at the base, ±66 at the plateau 78px
        // up) rather than guessed fractions, so no stratum pokes out past the silhouette. The
        // real edges bulge outward from these, which only ever leaves a little margin.
        listOf(-40f to 80f, -20f to 102f, 2f to 125f).forEach { (dy, halfW) ->
            scope.drawLine(
                Color(0xFF8A6A2E).copy(alpha = 0.55f),
                Offset(cx - halfW, cy + dy), Offset(cx + halfW, cy + dy),
                strokeWidth = 3f
            )
        }
        scope.drawStitchedOutline(hillPath, ThreadColor)

        // Palisade of individual timbers along the plateau
        drawPalisadeRun(scope, cx - 60f, cx + 60f, baseY = cy - 58f, height = 48f, seed = bg.id.hashCode())
        
        // Wooden Tower
        val towerPath = Path().apply {
            moveTo(cx - 11f, cy - 57.5f)
            lineTo(cx - 10f, cy - 170f)
            lineTo(cx + 14f, cy - 171f)
            lineTo(cx + 15.5f, cy - 58f)
            close()
        }
        drawStitchedFill(scope, towerPath, Color(0xFFBAC5CC))
        scope.drawStitchedOutline(towerPath, ThreadColor)
        
        // Crossbeams
        scope.drawLine(ThreadColor, Offset(cx - 10f, cy - 142f), Offset(cx + 12f, cy - 164f), strokeWidth = 2f)
        scope.drawLine(ThreadColor, Offset(cx + 13.5f, cy - 138f), Offset(cx - 10.5f, cy - 107f), strokeWidth = 2f)
        
        // Tower Roof
        val roofPath = Path().apply {
            moveTo(cx, cy - 215f)
            lineTo(cx - 16.5f, cy - 168.5f)
            lineTo(cx + 16.5f, cy - 169.5f)
            close()
        }
        drawStitchedFill(scope, roofPath, Color(0xFF265063))
        scope.drawStitchedOutline(roofPath, ThreadColor)
        val square_4 = Path().apply {
            moveTo(cx - 39f, cy - 156f)
            lineTo(cx - 14.5f, cy - 156f)
            lineTo(cx - 11f, cy - 58f)
            lineTo(cx - 38f, cy - 58f)
            close()
        }
        drawStitchedFill(scope, square_4, Color(0xFFBAC5CC))
        scope.drawStitchedOutline(square_4, Color(0xFF2C2219))
        val triangle_5 = Path().apply {
            moveTo(cx - 24.5f, cy - 201.5f)
            lineTo(cx - 12.5f, cy - 155f)
            lineTo(cx - 41f, cy - 154.5f)
            close()
        }
        drawStitchedFill(scope, triangle_5, Color(0xFF265063))
        scope.drawStitchedOutline(triangle_5, Color(0xFF2C2219))
        val square_6 = Path().apply {
            moveTo(cx - 30.5f, cy - 141f)
            lineTo(cx - 21.5f, cy - 142f)
            lineTo(cx - 21f, cy - 117.5f)
            lineTo(cx - 31f, cy - 117f)
            close()
        }
        drawStitchedFill(scope, square_6, Color(0xFF2C2219))
        scope.drawStitchedOutline(square_6, Color(0xFF2C2219))
        val square_7 = Path().apply {
            moveTo(cx - 5.5f, cy - 143.5f)
            lineTo(cx + 7f, cy - 144.5f)
            lineTo(cx + 7f, cy - 124.5f)
            lineTo(cx - 5f, cy - 122.5f)
            close()
        }
        drawStitchedFill(scope, square_7, Color(0xFF2C2219))
        scope.drawStitchedOutline(square_7, Color(0xFF2C2219))
        val square_8 = Path().apply {
            moveTo(cx + 17f, cy - 153f)
            lineTo(cx + 42f, cy - 153f)
            lineTo(cx + 42f, cy - 58.5f)
            lineTo(cx + 18f, cy - 58.5f)
            close()
        }
        drawStitchedFill(scope, square_8, Color(0xFFBAC5CC))
        scope.drawStitchedOutline(square_8, Color(0xFF2C2219))
        val triangle_9 = Path().apply {
            moveTo(cx + 30f, cy - 201.5f)
            lineTo(cx + 42.5f, cy - 151f)
            lineTo(cx + 19f, cy - 152.5f)
            close()
        }
        drawStitchedFill(scope, triangle_9, Color(0xFF265063))
        scope.drawStitchedOutline(triangle_9, Color(0xFF2C2219))
        val square_10 = Path().apply {
            moveTo(cx + 26.5f, cy - 130f)
            lineTo(cx + 37.5f, cy - 132f)
            lineTo(cx + 37.5f, cy - 112.5f)
            lineTo(cx + 27f, cy - 112f)
            close()
        }
        drawStitchedFill(scope, square_10, Color(0xFF2C2219))
        scope.drawStitchedOutline(square_10, Color(0xFF2C2219))
        val square_11 = Path().apply {
            moveTo(cx - 6f, cy - 74.5f)
            lineTo(cx + 10f, cy - 74.5f)
            lineTo(cx + 10f, cy - 58.5f)
            lineTo(cx - 6f, cy - 58.5f)
            close()
        }
        drawStitchedFill(scope, square_11, Color(0xFF8A5E38))
        scope.drawStitchedOutline(square_11, Color(0xFF8A5E38))
        scope.drawCircle(Color(0xFF8A5E38), radius = 8f, center = Offset(cx + 2f, cy - 71.5f))
        scope.drawCircle(Color(0xFF8A5E38), radius = 8f, center = Offset(cx + 2f, cy - 71.5f), style = StitchedStroke)
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
    scope.drawStitchedOutline(body, ThreadColor)

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
        scope.drawStitchedOutline(opening, ThreadColor)

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
        scope.drawStitchedOutline(pPath, ThreadColor)
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
        scope.drawStitchedOutline(col, ThreadColor)
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
    scope.drawStitchedOutline(roof, ThreadColor)

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
/**
 * Bosham church, from the ECCLESIA panel: a steep hatched roof with a cross at the apex, a turret
 * at each shoulder, and a deep stepped doorway. Literal and seedless, so it opens in the editor.
 */
internal fun drawEcclesia(scope: DrawScope, cx: Float, cy: Float) {
    val stone = Color(0xFFE8DCC0)
    val slate = Color(0xFF6B7882)
    val band = Color(0xFFB08221)
    val dark = Color(0xFF3B332A)
    val apexY = cy - 152f
    val eaveY = cy - 88f

    // Nave body.
    val body = Path().apply {
        moveTo(cx - 66f, cy); lineTo(cx - 66f, eaveY)
        lineTo(cx + 66f, eaveY); lineTo(cx + 66f, cy)
        close()
    }
    drawStitchedFill(scope, body, stone)
    scope.drawStitchedOutline(body, ThreadColor)

    // The steep gable. Bosham's roof is the tallest thing in its panel and it is what makes the
    // building read as a church rather than a hall.
    val roof = Path().apply {
        moveTo(cx - 80f, eaveY); lineTo(cx, apexY); lineTo(cx + 80f, eaveY)
        close()
    }
    drawStitchedFill(scope, roof, slate)
    scope.drawStitchedOutline(roof, ThreadColor)
    // Courses running with the pitch, the fine hatch the tapestry uses on a shingled roof.
    for (i in 1 until 7) {
        val t = i / 7f
        val y = eaveY + (apexY - eaveY) * t
        val halfW = 80f * (1f - t)
        scope.drawLine(ThreadColor.copy(alpha = 0.45f), Offset(cx - halfW, y), Offset(cx + halfW, y), strokeWidth = 1.5f)
    }
    scope.drawLine(band, Offset(cx - 82f, eaveY), Offset(cx + 82f, eaveY), strokeWidth = 5f)
    scope.drawLine(ThreadColor, Offset(cx - 82f, eaveY), Offset(cx + 82f, eaveY), strokeWidth = 1.5f)

    // Apex cross.
    scope.drawLine(ThreadColor, Offset(cx, apexY), Offset(cx, apexY - 26f), strokeWidth = 3f)
    scope.drawLine(ThreadColor, Offset(cx - 8f, apexY - 18f), Offset(cx + 8f, apexY - 18f), strokeWidth = 3f)

    // A turret at each shoulder, each with its own little cross.
    for (i in 0 until 2) {
        val tx = cx - 78f + i * 156f
        val turret = Path().apply {
            moveTo(tx - 13f, cy); lineTo(tx - 11f, cy - 104f)
            lineTo(tx + 11f, cy - 104f); lineTo(tx + 13f, cy)
            close()
        }
        drawStitchedFill(scope, turret, stone)
        scope.drawStitchedOutline(turret, ThreadColor)
        val cap = Path().apply {
            moveTo(tx - 16f, cy - 104f); lineTo(tx, cy - 132f); lineTo(tx + 16f, cy - 104f)
            close()
        }
        drawStitchedFill(scope, cap, slate)
        scope.drawStitchedOutline(cap, ThreadColor)
        scope.drawLine(ThreadColor, Offset(tx, cy - 132f), Offset(tx, cy - 148f), strokeWidth = 2.5f)
        scope.drawLine(ThreadColor, Offset(tx - 6f, cy - 142f), Offset(tx + 6f, cy - 142f), strokeWidth = 2.5f)
        // A single slit window, so the turrets are not blank posts.
        scope.drawLine(dark, Offset(tx, cy - 58f), Offset(tx, cy - 76f), strokeWidth = 5f)
    }

    // Stepped doorway: three concentric round arches, the moulded order Bosham is drawn with.
    for (i in 0 until 3) {
        val w = 34f - i * 7f
        val h = 52f - i * 7f
        val arch = Path().apply {
            moveTo(cx - w, cy); lineTo(cx - w, cy - h * 0.55f)
            quadraticTo(cx, cy - h - 10f, cx + w, cy - h * 0.55f)
            lineTo(cx + w, cy)
            close()
        }
        drawStitchedFill(scope, arch, if (i == 2) dark else if (i == 1) band else stone)
        scope.drawStitchedOutline(arch, ThreadColor)
    }
}

/**
 * Harold's hall from the comet panel: a round arch on two columns under a chequered cornice, with
 * a bird finial at each corner — the diapered band and the beast finials are pure tapestry.
 */
internal fun drawPalaceArch(scope: DrawScope, cx: Float, cy: Float) {
    val stone = Color(0xFFE8DCC0)
    val band = Color(0xFFB08221)
    val green = Color(0xFF4C613D)
    val dark = Color(0xFF3B332A)
    val capY = cy - 112f

    // The opening behind the arch, so the structure reads as a doorway and not a slab.
    val voidPath = Path().apply {
        moveTo(cx - 56f, cy); lineTo(cx - 56f, capY)
        quadraticTo(cx, capY - 62f, cx + 56f, capY)
        lineTo(cx + 56f, cy)
        close()
    }
    drawStitchedFill(scope, voidPath, dark)
    scope.drawStitchedOutline(voidPath, ThreadColor)

    // Columns, with the heavy capital and base the tapestry always gives them.
    for (i in 0 until 2) {
        val px = cx - 70f + i * 140f
        val col = Path().apply {
            moveTo(px - 14f, cy); lineTo(px - 11f, capY)
            lineTo(px + 11f, capY); lineTo(px + 14f, cy)
            close()
        }
        drawStitchedFill(scope, col, stone)
        scope.drawStitchedOutline(col, ThreadColor)
        listOf(cy - 6f, capY + 6f).forEach { by ->
            scope.drawLine(band, Offset(px - 17f, by), Offset(px + 17f, by), strokeWidth = 7f)
            scope.drawLine(ThreadColor, Offset(px - 17f, by), Offset(px + 17f, by), strokeWidth = 1.5f)
        }
    }

    // The arch band spanning the columns.
    val archBand = Path().apply {
        moveTo(cx - 84f, capY)
        quadraticTo(cx, capY - 92f, cx + 84f, capY)
        lineTo(cx + 56f, capY)
        quadraticTo(cx, capY - 62f, cx - 56f, capY)
        close()
    }
    drawStitchedFill(scope, archBand, stone)
    scope.drawStitchedOutline(archBand, ThreadColor)

    // Chequered cornice: alternating squares, the diapered band that runs over every hall in the
    // tapestry. Two colours alternating is the whole trick, and nothing we had did it.
    // A quadratic's apex is halfway to its control point, so the outer arch tops out at capY-46,
    // not the capY-92 the control reads as. Sat at -96 the cornice floated clear of the building.
    val corniceY = capY - 62f
    for (i in 0 until 12) {
        val sx = cx - 90f + i * 15f
        val sq = Path().apply {
            moveTo(sx, corniceY); lineTo(sx + 15f, corniceY)
            lineTo(sx + 15f, corniceY + 16f); lineTo(sx, corniceY + 16f)
            close()
        }
        drawStitchedFill(scope, sq, if (i % 2 == 0) green else band)
        scope.drawPath(sq, ThreadColor, style = Stroke(width = 1.5f))
    }
    scope.drawLine(ThreadColor, Offset(cx - 90f, corniceY), Offset(cx + 90f, corniceY), strokeWidth = 2f)
    scope.drawLine(ThreadColor, Offset(cx - 90f, corniceY + 16f), Offset(cx + 90f, corniceY + 16f), strokeWidth = 2f)

    // A bird finial on each corner — the tapestry perches beasts on its rooflines constantly.
    for (i in 0 until 2) {
        val bx = cx - 82f + i * 164f
        val bird = Path().apply {
            moveTo(bx - 9f, corniceY); lineTo(bx - 4f, corniceY - 16f)
            lineTo(bx + 5f, corniceY - 20f); lineTo(bx + 11f, corniceY - 14f)
            lineTo(bx + 3f, corniceY - 11f); lineTo(bx + 7f, corniceY)
            close()
        }
        drawStitchedFill(scope, bird, Color(0xFF8A5E38))
        scope.drawStitchedOutline(bird, ThreadColor)
    }
}

/**
 * Westminster, from the funeral scene (HIC PORTATUR CORPUS EADWARDI REGIS): a long arcaded nave
 * under a lattice-hatched roof, with a central tower and a weathercock.
 *
 * The one silhouette we had nothing like — everything else in the set is a tower or a hut, and the
 * tapestry's grandest building is long and low. Same house rules as the domed tower: literal
 * coordinates, counted loops, no seeded randomness, so it opens in the vector builder.
 */
internal fun drawAbbeyNave(scope: DrawScope, cx: Float, cy: Float) {
    val stone = Color(0xFFE8DCC0)
    val slate = Color(0xFF6B7882)
    val band = Color(0xFFB08221)
    val dark = Color(0xFF3B332A)
    val domeCol = Color(0xFF9E3624)
    val naveTop = cy - 95f
    val roofTop = cy - 138f

    // Nave wall.
    val nave = Path().apply {
        moveTo(cx - 150f, cy); lineTo(cx - 150f, naveTop)
        lineTo(cx + 150f, naveTop); lineTo(cx + 150f, cy)
        close()
    }
    drawStitchedFill(scope, nave, stone)
    scope.drawStitchedOutline(nave, ThreadColor)

    // The roof as a long hatched band, which is how the tapestry draws a great tiled roof in
    // elevation — a lozenge lattice, not a flat slab of colour.
    val roof = Path().apply {
        moveTo(cx - 160f, naveTop); lineTo(cx - 160f, roofTop)
        lineTo(cx + 160f, roofTop); lineTo(cx + 160f, naveTop)
        close()
    }
    drawStitchedFill(scope, roof, slate)
    scope.drawStitchedOutline(roof, ThreadColor)
    // 10, not 11: the span is 320 wide in steps of 32, and each cross reaches a further 32 to the
    // right, so an eleventh hung a lozenge out past the eaves in mid-air.
    for (i in 0 until 10) {
        val x0 = cx - 160f + i * 32f
        scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(x0, naveTop), Offset(x0 + 32f, roofTop), strokeWidth = 1.5f)
        scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(x0 + 32f, naveTop), Offset(x0, roofTop), strokeWidth = 1.5f)
    }
    // Eaves course, so the roof sits ON the wall rather than floating above it.
    scope.drawLine(band, Offset(cx - 162f, naveTop), Offset(cx + 162f, naveTop), strokeWidth = 5f)
    scope.drawLine(ThreadColor, Offset(cx - 162f, naveTop), Offset(cx + 162f, naveTop), strokeWidth = 1.5f)

    // The ground-floor arcade: seven round arches on piers. The single most recognisable feature
    // of the building, and the reason it reads as an abbey rather than a barn.
    for (i in 0 until 7) {
        val ax = cx - 126f + i * 42f
        val arch = Path().apply {
            moveTo(ax - 15f, cy); lineTo(ax - 15f, cy - 28f)
            quadraticTo(ax, cy - 54f, ax + 15f, cy - 28f)
            lineTo(ax + 15f, cy)
            close()
        }
        drawStitchedFill(scope, arch, dark)
        scope.drawStitchedOutline(arch, ThreadColor)
    }

    // Clerestory: the upper row of small windows, offset from the arcade below it.
    for (i in 0 until 6) {
        val wx = cx - 105f + i * 42f
        val win = Path().apply {
            moveTo(wx - 7f, cy - 62f); lineTo(wx - 7f, cy - 76f)
            quadraticTo(wx, cy - 88f, wx + 7f, cy - 76f)
            lineTo(wx + 7f, cy - 62f)
            close()
        }
        drawStitchedFill(scope, win, slate)
        scope.drawStitchedOutline(win, ThreadColor)
    }

    // Central tower, rising through the roof.
    val towerTop = cy - 212f
    val tower = Path().apply {
        moveTo(cx - 32f, roofTop); lineTo(cx - 26f, towerTop)
        lineTo(cx + 26f, towerTop); lineTo(cx + 32f, roofTop)
        close()
    }
    drawStitchedFill(scope, tower, stone)
    scope.drawStitchedOutline(tower, ThreadColor)
    scope.drawLine(band, Offset(cx - 29f, cy - 176f), Offset(cx + 29f, cy - 176f), strokeWidth = 4f)
    // Twin belfry openings, the tower's own arcade in miniature.
    for (i in 0 until 2) {
        val bx = cx - 12f + i * 24f
        val op = Path().apply {
            moveTo(bx - 7f, cy - 186f); lineTo(bx - 7f, cy - 198f)
            quadraticTo(bx, cy - 208f, bx + 7f, cy - 198f)
            lineTo(bx + 7f, cy - 186f)
            close()
        }
        drawStitchedFill(scope, op, dark)
        scope.drawStitchedOutline(op, ThreadColor)
    }

    // Scaled cupola and the weathercock that crowns the whole scene in the tapestry.
    val cap = Path().apply {
        moveTo(cx - 36f, towerTop)
        quadraticTo(cx - 33f, towerTop - 30f, cx, towerTop - 36f)
        quadraticTo(cx + 33f, towerTop - 30f, cx + 36f, towerTop)
        close()
    }
    drawStitchedFill(scope, cap, domeCol)
    scope.drawStitchedOutline(cap, ThreadColor)
    for (row in 0 until 2) {
        val sy = towerTop - 5f - row * 10f
        val halfW = 30f - row * 12f
        val count = 5 - row
        for (i in 0 until count) {
            val sx = cx - halfW + i * (2f * halfW / (count - 1))
            val scale = Path().apply {
                moveTo(sx - 6f, sy)
                quadraticTo(sx, sy - 10f, sx + 6f, sy)
            }
            scope.drawPath(scale, ThreadColor.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
        }
    }
    scope.drawLine(ThreadColor, Offset(cx, towerTop - 36f), Offset(cx, towerTop - 56f), strokeWidth = 3f)
    val cock = Path().apply {
        moveTo(cx, towerTop - 58f); lineTo(cx + 16f, towerTop - 52f)
        lineTo(cx, towerTop - 46f)
        close()
    }
    drawStitchedFill(scope, cock, band)
    scope.drawStitchedOutline(cock, ThreadColor)
}

/**
 * The domed watchtower from the comet scene (ISTI MIRANT STELLAM): a banded shaft under a
 * fish-scale dome, with a round-arched window and door.
 *
 * Deliberately literal and seedless, unlike most of the older buildings. Anything driven by
 * Random(bg.seed) is invisible in the vector builder — the interpreter unrolls loops but cannot
 * follow a seeded rng — so the buildings that most needed redrawing were the ones you could not
 * open. Every coordinate here is a number the editor can show you and drag.
 */
internal fun drawDomedTower(scope: DrawScope, cx: Float, cy: Float) {
    val stone = Color(0xFFE8DCC0)
    val slate = Color(0xFF6B7882)
    val band = Color(0xFFB08221)
    val domeCol = Color(0xFF9E3624)
    val topY = cy - 152f

    // Shaft, tapering slightly the way the tapestry draws its towers.
    val shaft = Path().apply {
        moveTo(cx - 32f, cy)
        lineTo(cx - 26f, topY)
        lineTo(cx + 26f, topY)
        lineTo(cx + 32f, cy)
        close()
    }
    drawStitchedFill(scope, shaft, stone)
    scope.drawStitchedOutline(shaft, ThreadColor)

    // Storey bands. Two courses of ochre, the horizontals that stop the shaft reading as a post.
    listOf(-104f to 29f, -56f to 30.5f).forEach { (dy, halfW) ->
        scope.drawLine(band, Offset(cx - halfW, cy + dy), Offset(cx + halfW, cy + dy), strokeWidth = 5f)
        scope.drawLine(ThreadColor, Offset(cx - halfW, cy + dy), Offset(cx + halfW, cy + dy), strokeWidth = 1.5f)
    }

    // The dome: a true half-round cap, wider than the shaft so it overhangs like a cupola.
    val dome = Path().apply {
        moveTo(cx - 38f, topY)
        quadraticTo(cx - 36f, topY - 34f, cx, topY - 40f)
        quadraticTo(cx + 36f, topY - 34f, cx + 38f, topY)
        close()
    }
    drawStitchedFill(scope, dome, domeCol)
    scope.drawStitchedOutline(dome, ThreadColor)

    // Fish-scale tiling on the dome — overlapping scallops, offset row to row. This is the
    // texture the whole tapestry uses for a roof, and none of our buildings had it.
    // Counted `for`, not `while`: the editor's interpreter unrolls a counted loop but cannot
    // follow one whose bound is a running variable, so a while-loop here left six of the
    // scallops invisible in the vector builder — the exact trap that hides the older buildings.
    for (row in 0 until 3) {
        val sy = topY - 4f - row * 10f
        val halfW = 34f - row * 9f
        val count = 6 - row
        for (i in 0 until count) {
            val sx = cx - halfW + i * (2f * halfW / (count - 1))
            val scale = Path().apply {
                moveTo(sx - 6f, sy)
                quadraticTo(sx, sy - 10f, sx + 6f, sy)
            }
            scope.drawPath(scale, ThreadColor.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
        }
    }

    // Finial: the ball and cross that tops every tower in the tapestry.
    scope.drawLine(ThreadColor, Offset(cx, topY - 40f), Offset(cx, topY - 58f), strokeWidth = 3f)
    scope.drawLine(ThreadColor, Offset(cx - 7f, topY - 50f), Offset(cx + 7f, topY - 50f), strokeWidth = 3f)
    scope.drawCircle(band, radius = 4f, center = Offset(cx, topY - 40f))
    scope.drawCircle(ThreadColor, radius = 4f, center = Offset(cx, topY - 40f), style = Stroke(width = 1.5f))

    // Round-arched window on the upper storey, and the door below. Both are the same shape at
    // two sizes — the round arch is the single most recognisable thing about these buildings.
    val window = Path().apply {
        moveTo(cx - 9f, cy - 62f)
        lineTo(cx - 9f, cy - 84f)
        quadraticTo(cx, cy - 98f, cx + 9f, cy - 84f)
        lineTo(cx + 9f, cy - 62f)
        close()
    }
    drawStitchedFill(scope, window, slate)
    scope.drawStitchedOutline(window, ThreadColor)

    val door = Path().apply {
        moveTo(cx - 13f, cy)
        lineTo(cx - 13f, cy - 26f)
        quadraticTo(cx, cy - 44f, cx + 13f, cy - 26f)
        lineTo(cx + 13f, cy)
        close()
    }
    drawStitchedFill(scope, door, Color(0xFF3B332A))
    scope.drawStitchedOutline(door, ThreadColor)
}

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
    scope.drawStitchedOutline(shaft, ThreadColor)

    // A genuinely twisted shaft: bands that wrap the column diagonally, the way the tapestry
    // draws its towers. This used to be rows of scallops with each row nudged sideways, which
    // at any real size read as fish-scale tiling — the same texture the domes use — and not as
    // a spiral at all.
    //
    // Each band's corners are placed ON the shaft edges rather than clipped to them: the shaft
    // is a straight-sided taper, so the width at any height is exact and the band can never
    // spill past the wall.
    val baseY = cy + 20f
    fun halfAt(y: Float) = taper + (halfW - taper) * ((y - topY) / (baseY - topY)).coerceIn(0f, 1f)
    val drop = halfW * 1.5f        // how far the band falls across the width = pitch of the twist
    val bandStep = 26f
    var yL = baseY + drop
    while (yL > topY - drop) {
        val yR = yL - drop
        val thick = 11f
        // Clamped into the shaft's own height, or the bands at each end of the run hang in the
        // air past the cap and below the base instead of being cut off at the wall.
        val aY = yL.coerceIn(topY, baseY); val bY = yR.coerceIn(topY, baseY)
        val aY2 = (yL - thick).coerceIn(topY, baseY); val bY2 = (yR - thick).coerceIn(topY, baseY)
        val band = Path().apply {
            moveTo(cx - halfAt(aY), aY)
            lineTo(cx + halfAt(bY), bY)
            lineTo(cx + halfAt(bY2), bY2)
            lineTo(cx - halfAt(aY2), aY2)
            close()
        }
        drawStitchedFill(scope, band, roofCol)
        scope.drawPath(band, ThreadColor.copy(alpha = 0.7f), style = Stroke(width = 1.5f))
        yL -= bandStep
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
        scope.drawStitchedOutline(win, ThreadColor)
    }

    // Conical cap
    val cap = Path().apply {
        moveTo(cx - taper - 8f, topY)
        lineTo(cx, topY - 46f)
        lineTo(cx + taper + 8f, topY)
        close()
    }
    drawStitchedFill(scope, cap, roofCol)
    scope.drawStitchedOutline(cap, ThreadColor)

    // Pennant
    scope.drawLine(ThreadColor, Offset(cx, topY - 46f), Offset(cx, topY - 74f), strokeWidth = 2.5f)
    val flag = Path().apply {
        moveTo(cx, topY - 74f)
        lineTo(cx + 26f, topY - 67f)
        lineTo(cx, topY - 58f)
        close()
    }
    drawStitchedFill(scope, flag, Color(0xFF9E3624))
    scope.drawStitchedOutline(flag, ThreadColor)
}

/**
 * Bosham's feast hall — the right-hand building of the AD BOSHAM panel, where Harold dines before
 * he sails: an open upper storey carried on a round-arched arcade, under a broad hipped roof of
 * green and gold chequer.
 *
 * It used to be a plaster box with three grey posts under a steep grey gable stippled with rows of
 * overlapping circles, which read as a pile of grapes rather than shingles, and it duplicated
 * ECCLESIA's silhouette besides. The chequered hip and the open colonnade are what make this
 * building recognisable, so those are what it is drawn from now. Seedless and literal.
 */
internal fun drawBuildingBosham(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        val stone = Color(0xFFE8DCC0)
        val band = Color(0xFFB08221)
        val dark = Color(0xFF3B332A)
        val tileGreen = Color(0xFF4C613D)
        val tileGold = Color(0xFFC9A227)
        val deckY = cy - 74f       // floor of the open upper storey
        val capY = cy - 150f       // top of the upper columns
        val eaveY = cy - 162f

        // Lower arcade: four round arches on piers, carrying the hall
        for (i in 0 until 4) {
            val ax = cx - 84f + i * 56f
            val arch = Path().apply {
                moveTo(ax - 21f, cy + 20f); lineTo(ax - 21f, cy - 20f)
                quadraticTo(ax, cy - 62f, ax + 21f, cy - 20f)
                lineTo(ax + 21f, cy + 20f); close()
            }
            drawStitchedFill(scope, arch, dark)
            scope.drawStitchedOutline(arch, ThreadColor)
        }
        // Piers between and either side of the arches
        for (i in 0 until 5) {
            val px = cx - 112f + i * 56f
            val pier = Path().apply {
                moveTo(px - 7f, cy + 20f); lineTo(px - 7f, deckY)
                lineTo(px + 7f, deckY); lineTo(px + 7f, cy + 20f); close()
            }
            drawStitchedFill(scope, pier, stone)
            scope.drawStitchedOutline(pier, ThreadColor)
        }

        // The deck the feast sits on, a gold-edged sill running the full width
        val deck = Path().apply {
            moveTo(cx - 122f, deckY); lineTo(cx + 122f, deckY)
            lineTo(cx + 122f, deckY + 12f); lineTo(cx - 122f, deckY + 12f); close()
        }
        drawStitchedFill(scope, deck, band)
        scope.drawStitchedOutline(deck, ThreadColor)

        // Open upper storey: arched bays first, so the dark of the hall behind shows through, then
        // the columns over them. Drawn as filled openings rather than stroked curves — as bare
        // strokes the arches rose past the eaves and read as a row of little bows above the roof.
        for (i in 0 until 4) {
            val ax = cx - 72f + i * 48f
            val bay = Path().apply {
                moveTo(ax - 24f, deckY); lineTo(ax - 24f, capY + 30f)
                quadraticTo(ax, capY - 4f, ax + 24f, capY + 30f)
                lineTo(ax + 24f, deckY); close()
            }
            drawStitchedFill(scope, bay, dark)
            scope.drawStitchedOutline(bay, ThreadColor)
        }
        // Five slender columns with capitals, the hall standing open between them
        for (i in 0 until 5) {
            val px = cx - 96f + i * 48f
            val col = Path().apply {
                moveTo(px - 7f, deckY); lineTo(px - 6f, capY + 8f)
                lineTo(px + 6f, capY + 8f); lineTo(px + 7f, deckY); close()
            }
            drawStitchedFill(scope, col, stone)
            scope.drawStitchedOutline(col, ThreadColor)
            // Capital and base blocks
            listOf(capY, deckY - 8f).forEach { y ->
                val blk = Path().apply {
                    moveTo(px - 11f, y); lineTo(px + 11f, y)
                    lineTo(px + 11f, y + 8f); lineTo(px - 11f, y + 8f); close()
                }
                drawStitchedFill(scope, blk, band)
                scope.drawStitchedOutline(blk, ThreadColor)
            }
        }

        // Broad hipped roof of green-and-gold chequer, the panel's most distinctive passage
        val roof = Path().apply {
            moveTo(cx - 138f, eaveY); lineTo(cx - 76f, cy - 214f)
            lineTo(cx + 76f, cy - 214f); lineTo(cx + 138f, eaveY); close()
        }
        drawStitchedFill(scope, roof, stone)
        scope.drawStitchedOutline(roof, ThreadColor)
        // Chequer, laid in courses that narrow with the hip
        for (row in 0 until 4) {
            val t0 = row / 4f
            val t1 = (row + 1) / 4f
            val yTop = eaveY + (cy - 214f - eaveY) * t1
            val yBot = eaveY + (cy - 214f - eaveY) * t0
            val halfTop = 138f - (138f - 76f) * t1
            val halfBot = 138f - (138f - 76f) * t0
            val cells = 8 - row
            for (i in 0 until cells) {
                val u0 = i / cells.toFloat()
                val u1 = (i + 1) / cells.toFloat()
                val cell = Path().apply {
                    moveTo(cx - halfBot + 2f * halfBot * u0, yBot)
                    lineTo(cx - halfBot + 2f * halfBot * u1, yBot)
                    lineTo(cx - halfTop + 2f * halfTop * u1, yTop)
                    lineTo(cx - halfTop + 2f * halfTop * u0, yTop)
                    close()
                }
                if ((i + row) % 2 == 0) {
                    drawStitchedFill(scope, cell, tileGreen)
                } else {
                    drawStitchedFill(scope, cell, tileGold)
                }
                scope.drawPath(cell, ThreadColor.copy(alpha = 0.55f), style = Stroke(width = 1.2f))
            }
        }
        scope.drawStitchedOutline(roof, ThreadColor)
        // Ridge, with a beast-head finial at each end — the tapestry's habitual roof furniture
        scope.drawLine(band, Offset(cx - 78f, cy - 214f), Offset(cx + 78f, cy - 214f), strokeWidth = 5f)
        scope.drawLine(ThreadColor, Offset(cx - 78f, cy - 214f), Offset(cx + 78f, cy - 214f), strokeWidth = 1.5f)
        listOf(-1f, 1f).forEach { side ->
            val hx = cx + side * 78f
            val beast = Path().apply {
                moveTo(hx, cy - 214f)
                quadraticTo(hx + side * 4f, cy - 234f, hx + side * 18f, cy - 236f)
                quadraticTo(hx + side * 10f, cy - 226f, hx + side * 10f, cy - 214f)
                close()
            }
            drawStitchedFill(scope, beast, Color(0xFF8B6037))
            scope.drawStitchedOutline(beast, ThreadColor)
        }
    }

/**
 * The campanile from the comet panel (ISTI MIRANT STELLAM): a tall, narrow shaft in three stages,
 * each stage a little narrower than the one below, under a fish-scale lantern.
 *
 * The set had no tall thin silhouette — every tower in it was squat — so a skyline of them all read
 * as one repeated height. Seedless and literal.
 */
internal fun drawBellTower(scope: DrawScope, cx: Float, cy: Float) {
    val stone = Color(0xFFE8DCC0)
    val band = Color(0xFFB08221)
    val dark = Color(0xFF3B332A)
    val domeCol = Color(0xFF9E3624)
    val slate = Color(0xFF6B7882)

    // Three stages, each stepping in. Widths and tops read straight down the tower.
    val stages = listOf(
        Triple(46f, cy + 20f, cy - 84f),
        Triple(38f, cy - 84f, cy - 168f),
        Triple(31f, cy - 168f, cy - 238f)
    )
    stages.forEach { (halfW, baseY, topY) ->
        val shaft = Path().apply {
            moveTo(cx - halfW, baseY); lineTo(cx - halfW, topY)
            lineTo(cx + halfW, topY); lineTo(cx + halfW, baseY); close()
        }
        drawStitchedFill(scope, shaft, stone)
        scope.drawStitchedOutline(shaft, ThreadColor)
        // The string course that caps each stage, which is what makes it read as stacked
        scope.drawLine(band, Offset(cx - halfW - 5f, topY), Offset(cx + halfW + 5f, topY), strokeWidth = 5f)
        scope.drawLine(ThreadColor, Offset(cx - halfW - 5f, topY), Offset(cx + halfW + 5f, topY), strokeWidth = 1.5f)
    }

    // Round-arched door at the foot
    val door = Path().apply {
        moveTo(cx - 14f, cy + 20f); lineTo(cx - 14f, cy - 20f)
        quadraticTo(cx, cy - 44f, cx + 14f, cy - 20f)
        lineTo(cx + 14f, cy + 20f); close()
    }
    drawStitchedFill(scope, door, dark)
    scope.drawStitchedOutline(door, ThreadColor)

    // A single tall light in the middle stage, twin belfry openings in the top one
    val light = Path().apply {
        moveTo(cx - 9f, cy - 104f); lineTo(cx - 9f, cy - 138f)
        quadraticTo(cx, cy - 152f, cx + 9f, cy - 138f)
        lineTo(cx + 9f, cy - 104f); close()
    }
    drawStitchedFill(scope, light, slate)
    scope.drawStitchedOutline(light, ThreadColor)
    for (i in 0 until 2) {
        val bx = cx - 13f + i * 26f
        val op = Path().apply {
            moveTo(bx - 8f, cy - 186f); lineTo(bx - 8f, cy - 210f)
            quadraticTo(bx, cy - 222f, bx + 8f, cy - 210f)
            lineTo(bx + 8f, cy - 186f); close()
        }
        drawStitchedFill(scope, op, dark)
        scope.drawStitchedOutline(op, ThreadColor)
    }

    // Fish-scale lantern and cross
    val lantern = Path().apply {
        moveTo(cx - 36f, cy - 238f)
        quadraticTo(cx - 33f, cy - 268f, cx, cy - 276f)
        quadraticTo(cx + 33f, cy - 268f, cx + 36f, cy - 238f)
        close()
    }
    drawStitchedFill(scope, lantern, domeCol)
    scope.drawStitchedOutline(lantern, ThreadColor)
    for (row in 0 until 2) {
        val sy = cy - 243f - row * 11f
        val halfW = 29f - row * 12f
        val count = 5 - row
        for (i in 0 until count) {
            val sx = cx - halfW + i * (2f * halfW / (count - 1))
            val scaleArc = Path().apply {
                moveTo(sx - 6f, sy)
                quadraticTo(sx, sy - 10f, sx + 6f, sy)
            }
            scope.drawPath(scaleArc, ThreadColor.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
        }
    }
    scope.drawLine(ThreadColor, Offset(cx, cy - 276f), Offset(cx, cy - 302f), strokeWidth = 3f)
    scope.drawLine(ThreadColor, Offset(cx - 9f, cy - 293f), Offset(cx + 9f, cy - 293f), strokeWidth = 3f)
}

/**
 * A monastic cloister walk: a long lean-to roof carried on paired columns, with the blank garth
 * wall behind it. Wide and low, to break up a skyline that was otherwise all towers.
 */
internal fun drawCloisterWalk(scope: DrawScope, cx: Float, cy: Float) {
    val stone = Color(0xFFE8DCC0)
    val slate = Color(0xFF6B7882)
    val band = Color(0xFFB08221)
    val dark = Color(0xFF3B332A)
    val wallTop = cy - 104f
    val arcadeTop = cy - 74f

    // Garth wall behind the walk
    val wall = Path().apply {
        moveTo(cx - 170f, cy + 20f); lineTo(cx - 170f, wallTop)
        lineTo(cx + 170f, wallTop); lineTo(cx + 170f, cy + 20f); close()
    }
    drawStitchedFill(scope, wall, stone)
    scope.drawStitchedOutline(wall, ThreadColor)

    // Lean-to roof, pitched forward over the walk
    val roof = Path().apply {
        moveTo(cx - 182f, arcadeTop - 6f); lineTo(cx - 174f, wallTop - 26f)
        lineTo(cx + 174f, wallTop - 26f); lineTo(cx + 182f, arcadeTop - 6f); close()
    }
    drawStitchedFill(scope, roof, slate)
    scope.drawStitchedOutline(roof, ThreadColor)
    for (i in 0 until 12) {
        val x = cx - 176f + i * 30f
        scope.drawLine(ThreadColor.copy(alpha = 0.45f), Offset(x, wallTop - 26f), Offset(x - 6f, arcadeTop - 6f), strokeWidth = 1.5f)
    }
    scope.drawLine(band, Offset(cx - 184f, arcadeTop - 6f), Offset(cx + 184f, arcadeTop - 6f), strokeWidth = 5f)
    scope.drawLine(ThreadColor, Offset(cx - 184f, arcadeTop - 6f), Offset(cx + 184f, arcadeTop - 6f), strokeWidth = 1.5f)

    // The walk itself: seven tall round arches cut straight through the wall, the same way the
    // abbey's arcade is drawn. Paired shafts at nine bays read as a dark band behind a picket
    // fence at this size — the arch has to be big enough to be an arch.
    for (i in 0 until 7) {
        val ax = cx - 144f + i * 48f
        val bay = Path().apply {
            moveTo(ax - 19f, cy + 20f); lineTo(ax - 19f, cy - 26f)
            quadraticTo(ax, cy - 72f, ax + 19f, cy - 26f)
            lineTo(ax + 19f, cy + 20f); close()
        }
        drawStitchedFill(scope, bay, dark)
        scope.drawStitchedOutline(bay, ThreadColor)
        // Gold impost blocks where each arch springs — the one detail worth keeping from the shafts
        listOf(ax - 19f, ax + 19f).forEach { sx ->
            val imp = Path().apply {
                moveTo(sx - 5f, cy - 26f); lineTo(sx + 5f, cy - 26f)
                lineTo(sx + 5f, cy - 33f); lineTo(sx - 5f, cy - 33f); close()
            }
            drawStitchedFill(scope, imp, band)
            scope.drawPath(imp, ThreadColor, style = Stroke(width = 1.2f))
        }
    }
}

// Timber Manor (Mead Hall) — art now lives in assets/art/building_manor.json, editable in the
// vector builder. Nothing about it is seeded, so it ports cleanly with no code left behind.
internal fun drawBuildingManor(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        VectorAsset.cached("building_manor")?.let { scope.drawVectorAsset(it, cx, cy) }
    }
