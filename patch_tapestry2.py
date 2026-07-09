import sys
import re

file_path = "C:/Users/Josh/bayeux-brawler/app/src/main/java/com/example/game/TapestryRenderer.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 2.8 Draw horse
old_horse = """    private fun drawHorse(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val anim = fighter.animFrame
        val horseColor = Color(0xFF452E1B)
        
        val horseBodyPath = Path().apply {
            moveTo(cx - 50f, cy + 40f)
            lineTo(cx + 40f, cy + 40f)
            lineTo(cx + 50f, cy + 90f)
            lineTo(cx - 50f, cy + 90f)
            close()
        }
        drawStitchedFill(scope, horseBodyPath, horseColor)
        scope.drawPath(horseBodyPath, ThreadColor, style = StitchedStroke)
        
        // Head
        val headAngle = if (fighter.isDead || fighter.isDying) 60f else sin(anim * 2f) * 10f
        scope.withTransform({ rotate(headAngle, pivot = Offset(cx + 40f, cy + 40f)) }) {
            val headPath = Path().apply {
                moveTo(cx + 40f, cy + 40f)
                lineTo(cx + 70f, cy - 10f)
                lineTo(cx + 85f, cy + 10f)
                lineTo(cx + 50f, cy + 60f)
                close()
            }
            drawStitchedFill(scope, headPath, horseColor)
            scope.drawPath(headPath, ThreadColor, style = StitchedStroke)
            scope.drawCircle(Color.White, radius = 4f, center = Offset(cx + 65f, cy))
            scope.drawCircle(if (fighter.isDead || fighter.isDying) Color.Black else ThreadColor, radius = 1.5f, center = Offset(cx + 66f, cy))
        }
        
        val legSwing = if (fighter.isDead || fighter.isDying) 0f else sin(anim * 4f) * 20f
        scope.drawLine(horseColor, Offset(cx + 30f, cy + 90f), Offset(cx + 30f + legSwing, cy + 150f), strokeWidth = 12f)
        scope.drawLine(ThreadColor, Offset(cx + 30f, cy + 90f), Offset(cx + 30f + legSwing, cy + 150f), strokeWidth = 2f)
        scope.drawLine(horseColor, Offset(cx - 30f, cy + 90f), Offset(cx - 30f - legSwing, cy + 150f), strokeWidth = 12f)
        scope.drawLine(ThreadColor, Offset(cx - 30f, cy + 90f), Offset(cx - 30f - legSwing, cy + 150f), strokeWidth = 2f)
    }"""
new_horse = """    private fun drawHorse(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val anim = fighter.animFrame
        val walking = !fighter.isDead && !fighter.isDying
        val legSwing = if (walking) sin(anim) * 18f else 0f
        val horseColor = Color(0xFF6B4F2E)
        val legColor   = Color(0xFF5A3F22)

        val bodyPath = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(cx - 55f, cy + 60f, cx + 55f, cy + 130f))
        }
        drawStitchedFill(scope, bodyPath, horseColor)
        scope.drawPath(bodyPath, ThreadColor, style = StitchedStroke)

        val neckPath = Path().apply {
            moveTo(cx + 35f, cy + 65f)
            lineTo(cx + 55f, cy + 30f)
            lineTo(cx + 70f, cy + 35f)
            lineTo(cx + 55f, cy + 75f)
            close()
        }
        drawStitchedFill(scope, neckPath, Color(0xFF7A5A34))
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
    }"""
content = content.replace(old_horse, new_horse)

# 2.2, 2.3, 2.4, 2.5: drawBackArmAndShield completely replaced
pattern = r"    private fun drawBackArmAndShield\(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState\) \{.*?(?=    private fun drawDamageFlurry)"
new_back_arm = """    private fun drawBackArmAndShield(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val shieldArmAngle = if (fighter.isDead || fighter.isDying) {
            if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
        } else {
            -sin(fighter.animFrame * 0.5f) * 10f
        }
        
        if (fighter.missingArm) {
            scope.withTransform({ rotate(shieldArmAngle, pivot = Offset(cx - 5f, cy + 25f)) }) {
                val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(cx - 10f, cy + 35f), sleeveColor)
                scope.drawCircle(Color(0xFF9E3624), radius = 7f, center = Offset(cx - 10f, cy + 35f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 3.5f, center = Offset(cx - 10f, cy + 35f))
            }
            return
        }

        val sColor = fighter.shield.color
        val hasKite = fighter.shield.id == "shield_kite"
        val hasTower = fighter.shield.id == "shield_tower"

        if (fighter.shield.id == "shield_none") {
            if (fighter.isDualWielding) {
                val swing = fighter.swingProgress
                val armAngle = if (fighter.isDead || fighter.isDying) {
                    if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
                } else if (fighter.isAttacking) {
                    if (swing < 0.5f) -20f + 80f * (swing / 0.5f) else 60f - 80f * ((swing - 0.5f) / 0.5f)
                } else {
                    -sin(fighter.animFrame * 0.5f) * 10f
                }

                scope.withTransform({
                    rotate(armAngle, pivot = Offset(cx - 5f, cy + 30f))
                }) {
                    val hx = cx - 30f
                    val hy = cy + 40f
                    val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                    drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)
                    scope.drawCircle(Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
                    scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                    
                    if (!fighter.isDead && !fighter.isDying) {
                        drawWeapon(this, hx, hy, fighter)
                    }
                }
            } else {
                val isTwoHanded = fighter.weaponHead.id in listOf("head_claymore", "head_longbow", "head_halberd", "head_pike", "head_scythe", "head_bow")
                if (isTwoHanded) {
                    val swing = fighter.swingProgress
                    val isThrusting = fighter.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd", "head_dagger")
                    val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe")
                    val isScythe = fighter.weaponHead.id == "head_scythe"

                    var thrustOffset = Offset.Zero
                    val armAngle = if (fighter.isDead || fighter.isDying) {
                        if (fighter.isDying) -cos(fighter.animFrame * 1.5f) * 85f else -30f
                    } else if (fighter.isAttacking) {
                        if (isThrusting) {
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
                        } else if (isHeavy) {
                            if (swing < 0.5f) -60f * (swing / 0.5f) else -60f + 130f * ((swing - 0.5f) / 0.5f)
                        } else {
                            if (swing < 0.4f) -35f * (swing / 0.4f) else -35f + 100f * ((swing - 0.4f) / 0.6f)
                        }
                    } else {
                        -sin(fighter.animFrame * 0.5f) * 15f
                    }

                    scope.withTransform({
                        translate(thrustOffset.x, thrustOffset.y)
                        rotate(armAngle, pivot = Offset(cx - 5f, cy + 30f))
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
                        drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)
                        scope.drawCircle(Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
                        scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                    }
                } else {
                    val armAngle = shieldArmAngle
                    scope.withTransform({
                        rotate(armAngle, pivot = Offset(cx - 5f, cy + 30f))
                    }) {
                        val hx = cx - 5f
                        val hy = cy + 45f
                        val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                        drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)
                        scope.drawCircle(Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
                        scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
                    }
                }
            }
            return
        }

        scope.withTransform({
            rotate(shieldArmAngle, pivot = Offset(cx - 5f, cy + 25f))
        }) {
            val hx = cx - 8f
            val hy = cy + 10f
            val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
            drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)
            scope.drawCircle(Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
            scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
        }

        val shx = cx - 8f
        val shy = cy + 10f
        
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
            }
            drawPath(shieldPath, ThreadColor, style = StitchedStroke)
        }
    }
"""
content = re.sub(pattern, new_back_arm, content, flags=re.DOTALL)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated TapestryRenderer.kt part 2")
