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

        val key = "${bg.type}_${bg.seed}_${bg.hp.toInt()}_${bg.isDestroyed}_${bg.stuckArrowsFromLeft}_${bg.stuckArrowsFromRight}"
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
        val isShip = bg.type == BackgroundObjectType.SHIP
        val bgScale = if (isShip) 1.25f else 1.7f
        val lift = if (isShip) 0f else 14f
        scope.withTransform({
            scale(scaleFactor * bgScale, scaleFactor * bgScale, pivot = Offset(cx, cy))
        }) {
            scope.drawImage(bitmap, topLeft = Offset(cx - BG_BMP_CX, cy - BG_BMP_CY - lift))
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
            BackgroundObjectType.BROKEN_CHARIOT -> {
                // Just draw a broken chariot
                val dummy = FighterState(id="dummy", name="dummy", isPlayer=true, maxHp=1f, hp=1f, weaponHead=GameData.WEAPON_HEADS[0], weaponHandle=GameData.WEAPON_HANDLES[0], shield=GameData.SHIELDS[0], armor=GameData.ARMOR_PIECES[0], headgear=GameData.HEADGEAR_PIECES[0], posX=0f, targetX=0f)
                scope.withTransform({
                    translate(cx, cy)
                }) {
                    drawChariot(this, 0f, 0f, dummy)
                }
            }
        }

        if (bg.stuckArrowsFromLeft + bg.stuckArrowsFromRight > 0) {
            drawDamageDecals(scope, cx, cy, bg)
        }
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
        // Palisade: solid plank wall along the plateau edge with pointed tips and rails
        val palTop = cy - 92f
        val palBase = cy - 60f
        val wall = Path().apply {
            moveTo(cx - 62f, palBase)
            lineTo(cx - 62f, palTop)
            var px = cx - 62f
            while (px < cx + 62f) {
                lineTo(px + 6f, palTop - 8f) // pointed plank tip
                lineTo(px + 12f, palTop)
                px += 12f
            }
            lineTo(cx + 62f, palBase)
            close()
        }
        drawStitchedFill(scope, wall, Color(0xFF8C6F47))
        scope.drawPath(wall, ThreadColor, style = StitchedStroke)
        // Plank seams + horizontal rail
        for (i in 1 until 10) {
            val px = cx - 62f + i * 12.4f
            scope.drawLine(ThreadColor.copy(alpha = 0.5f), Offset(px, palTop), Offset(px, palBase), strokeWidth = 1.5f)
        }
        scope.drawLine(Color(0xFF5D4831), Offset(cx - 62f, palBase - 10f), Offset(cx + 62f, palBase - 10f), strokeWidth = 4f)
        // Keep
        val keep = Path().apply {
            moveTo(cx - 34f, cy - 84f)
            lineTo(cx + 34f, cy - 84f)
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
        scope.drawRect(Color(0xFF2C2219), topLeft = Offset(cx - 7f, cy - 108f), size = androidx.compose.ui.geometry.Size(14f, 24f))
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
        // Motte (Earthen Hill)
        val hillPath = Path().apply {
            moveTo(cx - 150f, cy + 20f)
            quadraticTo(cx - 30f, cy + 20f, cx, cy - 70f)
            quadraticTo(cx + 30f, cy + 20f, cx + 150f, cy + 20f)
            close()
        }
        drawStitchedFill(scope, hillPath, Color(0xFF5E4B3C))
        scope.drawPath(hillPath, ThreadColor, style = StitchedStroke)
        
        // Palisade
        for (i in 0 until 8) {
            val logX = cx - 40f + (i * 10f)
            val logPath = Path().apply {
                moveTo(logX, cy - 60f)
                lineTo(logX + 5f, cy - 120f) // Spike
                lineTo(logX + 10f, cy - 60f)
                close()
            }
            drawStitchedFill(scope, logPath, Color(0xFF8C6F47))
            scope.drawPath(logPath, ThreadColor, style = StitchedStroke)
        }
        
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

internal fun drawBuildingManor(scope: DrawScope, cx: Float, cy: Float, bg: BackgroundObject) {
        // Timber Manor (Mead Hall)
        val wallPath = Path().apply {
            moveTo(cx - 100f, cy + 20f)
            lineTo(cx - 100f, cy - 80f)
            lineTo(cx, cy - 120f)
            lineTo(cx + 100f, cy - 80f)
            lineTo(cx + 100f, cy + 20f)
            close()
        }
        drawStitchedFill(scope, wallPath, Color(0xFF6B513C))
        scope.drawPath(wallPath, ThreadColor, style = StitchedStroke)
        
        // Vertical planks
        for (i in -90..90 step 15) {
            scope.drawLine(ThreadColor, Offset(cx + i, cy + 20f), Offset(cx + i, cy - 80f), strokeWidth = 2f)
        }
        
        // Crossed Gables (Dragon heads)
        val gable1 = Path().apply {
            moveTo(cx - 120f, cy - 70f)
            lineTo(cx + 20f, cy - 140f)
            lineTo(cx + 15f, cy - 150f)
            lineTo(cx - 125f, cy - 80f)
            close()
        }
        drawStitchedFill(scope, gable1, Color(0xFF382F22))
        scope.drawPath(gable1, ThreadColor, style = StitchedStroke)
        
        val gable2 = Path().apply {
            moveTo(cx + 120f, cy - 70f)
            lineTo(cx - 20f, cy - 140f)
            lineTo(cx - 15f, cy - 150f)
            lineTo(cx + 125f, cy - 80f)
            close()
        }
        drawStitchedFill(scope, gable2, Color(0xFF382F22))
        scope.drawPath(gable2, ThreadColor, style = StitchedStroke)
        
        // Manor Door
        val doorPath = Path().apply { addRect(androidx.compose.ui.geometry.Rect(cx - 30f, cy - 40f, cx + 30f, cy + 20f)) }
        drawStitchedFill(scope, doorPath, Color(0xFF2C2219))
        scope.drawPath(doorPath, ThreadColor, style = StitchedStroke)
    }
