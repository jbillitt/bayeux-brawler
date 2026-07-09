import re

with open("old_draw_weapon.txt", "r") as f:
    old_code = f.read()

new_code = """    private fun drawWeapon(scope: DrawScope, hx: Float, hy: Float, fighter: FighterState) {
        if (fighter.weaponHead.id == "head_bare" && fighter.weaponHandle.id == "handle_fists") return
        
        val isBowOrSlingshot = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")

        // Determine direction vector of the handle/pole
        val handleLen = if (isBowOrSlingshot) {
            0f
        } else {
            when (fighter.weaponHandle.id) {
                "handle_long" -> 110f
                "handle_medium" -> 55f
                "handle_chain" -> 60f
                "handle_double_ended" -> 80f
                else -> 30f // short, iron, wheel, pick, fists
            }
        }

        // Handle shaft (wooden)
        val shaftEnd = androidx.compose.ui.geometry.Offset(hx + handleLen * 0.8f, hy - handleLen * 0.4f)
        var headPos = shaftEnd
        if (!isBowOrSlingshot) {
            if (fighter.weaponHandle.id == "handle_chain") {
                // Draw a flailing chain
                val timeSecs = System.currentTimeMillis() / 1000f
                val flailAmount = kotlin.math.sin(timeSecs * 10f + fighter.posX) * 15f
                val flailAmountY = kotlin.math.cos(timeSecs * 12f + fighter.posX) * 10f
                
                var cx = hx - 10f
                var cy = hy + 5f
                val linkCount = 8
                val dx = (shaftEnd.x - cx) / linkCount
                val dy = (shaftEnd.y - cy) / linkCount
                for (i in 0..linkCount) {
                    val progress = i.toFloat() / linkCount
                    val pX = cx + dx * i + (flailAmount * progress)
                    val pY = cy + dy * i + (flailAmountY * progress) + (progress * 15f) // sag
                    scope.drawCircle(
                        color = fighter.weaponHandle.color,
                        radius = 3f,
                        center = androidx.compose.ui.geometry.Offset(pX, pY)
                    )
                    scope.drawCircle(
                        color = ThreadColor,
                        radius = 3f,
                        center = androidx.compose.ui.geometry.Offset(pX, pY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                    if (i == linkCount) {
                        headPos = androidx.compose.ui.geometry.Offset(pX, pY)
                    }
                }
            } else if (fighter.weaponHandle.id == "handle_wheel") {
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
        val timeSecs = System.currentTimeMillis() / 1000f
        val dangleAngle = if (isChain) kotlin.math.sin(timeSecs * 5f + fighter.posX) * 20f + 70f else 0f
        val isPick = fighter.weaponHandle.id == "handle_pick"

        for (i in headsToDraw.indices) {
            val hPos = headsToDraw[i]
            val isBackHead = i == 1
            
            scope.withTransform({
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
                val headPos = hPos
                when (fighter.weaponHead.id) {
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
                        scope.drawPath(path, fighter.weaponHead.color)
                        scope.drawPath(path, ThreadColor, style = StitchedStroke)
                    }
                    "head_axe" -> {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x - 5f, headPos.y + 2f)
                            lineTo(headPos.x + 20f, headPos.y - 25f) // top corner
                            lineTo(headPos.x + 22f, headPos.y - 5f)  // blade edge
                            lineTo(headPos.x + 8f, headPos.y + 12f)  // beard hanging down
                            close()
                        }
                        scope.drawPath(path, fighter.weaponHead.color)
                        scope.drawPath(path, ThreadColor, style = StitchedStroke)
                    }
                    "head_sword", "head_claymore", "head_dagger" -> {
                        val isClaymore = fighter.weaponHead.id == "head_claymore"
                        val isDagger = fighter.weaponHead.id == "head_dagger"
                        val bladeLen = if (isClaymore) 80f else if (isDagger) 25f else 50f
                        val bladeEnd = androidx.compose.ui.geometry.Offset(headPos.x + bladeLen * 0.8f, headPos.y - bladeLen * 0.4f)
                        scope.drawLine(
                            color = fighter.weaponHead.color,
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
                        scope.drawCircle(fighter.weaponHead.color, radius = 12f, center = headPos)
                        scope.drawCircle(ThreadColor, radius = 12f, center = headPos, style = StitchedStroke)
                        for (i in 0 until 8) {
                            val angle = i * Math.PI / 4
                            val sp = androidx.compose.ui.geometry.Offset(headPos.x + kotlin.math.cos(angle).toFloat() * 19f, headPos.y + kotlin.math.sin(angle).toFloat() * 19f)
                            scope.drawLine(ThreadColor, headPos, sp, strokeWidth = 3f)
                        }
                    }
                    "head_maul" -> {
                        scope.drawRect(
                            color = fighter.weaponHead.color,
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
                        scope.drawPath(path, fighter.weaponHead.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                        scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                        // Bowstring
                        scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x - 12f, headPos.y - 30f), androidx.compose.ui.geometry.Offset(headPos.x - 12f, headPos.y + 30f), strokeWidth = 1.5f)
                    }
                    "head_slingshot" -> {
                        // draw Y shape
                        scope.drawLine(fighter.weaponHead.color, headPos, androidx.compose.ui.geometry.Offset(headPos.x + 10f, headPos.y - 15f), strokeWidth = 4f)
                        scope.drawLine(fighter.weaponHead.color, headPos, androidx.compose.ui.geometry.Offset(headPos.x + 10f, headPos.y + 15f), strokeWidth = 4f)
                        scope.drawCircle(ThreadColor, radius = 3f, center = headPos)
                    }
                    "head_longbow" -> {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x - 18f, headPos.y - 45f)
                            quadraticTo(headPos.x + 25f, headPos.y, headPos.x - 18f, headPos.y + 45f)
                        }
                        scope.drawPath(path, fighter.weaponHead.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                        scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                        scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x - 18f, headPos.y - 45f), androidx.compose.ui.geometry.Offset(headPos.x - 18f, headPos.y + 45f), strokeWidth = 2f)
                    }
                    "head_flail" -> {
                        val ballPos = androidx.compose.ui.geometry.Offset(headPos.x + 20f, headPos.y + 25f)
                        scope.drawLine(ThreadColor, headPos, ballPos, strokeWidth = 3f)
                        scope.drawCircle(fighter.weaponHead.color, radius = 10f, center = ballPos)
                    }
                    "head_scythe" -> {
                        val bladeLen = 60f
                        val bladeEnd = androidx.compose.ui.geometry.Offset(headPos.x + bladeLen * 0.8f, headPos.y - bladeLen * 0.4f)
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x, headPos.y)
                            quadraticTo(headPos.x + 20f, headPos.y - 40f, headPos.x + 40f, headPos.y - 20f) // outer curve
                            quadraticTo(headPos.x + 15f, headPos.y - 20f, headPos.x, headPos.y + 5f) // inner curve
                            close()
                        }
                        scope.drawPath(path, fighter.weaponHead.color)
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
                        scope.drawPath(path, fighter.weaponHead.color)
                        scope.drawPath(path, ThreadColor, style = StitchedStroke)
                    }
                }

                // If thrusting or swinging, draw swoosh lines
                if (fighter.isAttacking && fighter.swingProgress > 0.2f && fighter.swingProgress < 0.8f && !isBowOrSlingshot && !isBackHead) {
                    val angle = if (fighter.swingProgress < 0.5f) -20f else 20f
                    scope.withTransform({
                        rotate(angle, pivot = headPos)
                    }) {
                        val endP = androidx.compose.ui.geometry.Offset(headPos.x + 25f, headPos.y - 15f)
                        scope.drawLine(
                            color = androidx.compose.ui.graphics.Color(0x44FFFFFF),
                            start = headPos,
                            end = endP,
                            strokeWidth = 3f
                        )
                    }
                }

                // Bloody weapon?
                if (fighter.damageDealt > 0 && !isBowOrSlingshot) {
                    scope.drawLine(androidx.compose.ui.graphics.Color(0xFF9E3624), headPos, androidx.compose.ui.geometry.Offset(headPos.x + 5f, headPos.y - 5f), strokeWidth = 2f)
                }
            }
        }
    }
"""

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    full_code = f.read()

# Replace old_code with new_code
if old_code in full_code:
    full_code = full_code.replace(old_code, new_code + "\n")
    with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
        f.write(full_code)
    print("Replaced successfully")
else:
    print("Could not find old_code in full_code")
