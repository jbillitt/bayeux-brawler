import re

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Fix mount offset
code = code.replace('val mountOffsetY = if (fighter.isMounted) -68f else 0f', 'val mountOffsetY = if (fighter.isMounted) -35f else 0f')

# 2. Draw Embedded Projectiles in drawTorso
old_torso = '''        // Basic body base
        val torsoPath = Path().apply {
            moveTo(cx - 20f, cy + 40f)
            lineTo(cx + 20f, cy + 40f)
            lineTo(cx + 25f, cy + 95f)
            lineTo(cx - 25f, cy + 95f)
            close()
        }'''
new_torso = '''        // Draw Embedded Projectiles underneath the torso layers, or on top? 
        // We'll draw them on top of the torso later in this function.
        // Basic body base
        val torsoPath = Path().apply {
            moveTo(cx - 20f, cy + 40f)
            lineTo(cx + 20f, cy + 40f)
            lineTo(cx + 25f, cy + 95f)
            lineTo(cx - 25f, cy + 95f)
            close()
        }'''
code = code.replace(old_torso, new_torso)

# Actually, the best place to draw embedded projectiles is at the END of drawTorso so they sit on top of armor.
old_torso_end = '''            drawStitchedFill(scope, scaleArmorPath, Color(0xFF6E7880))
            scope.drawPath(scaleArmorPath, ThreadColor, style = StitchedStroke)
        }
    }'''
new_torso_end = '''            drawStitchedFill(scope, scaleArmorPath, Color(0xFF6E7880))
            scope.drawPath(scaleArmorPath, ThreadColor, style = StitchedStroke)
        }

        // Draw Embedded Projectiles
        for (ep in fighter.embeddedProjectiles) {
            val ex = cx + ep.offsetX
            val ey = cy + 65f + ep.offsetY
            scope.withTransform({
                rotate(ep.angle, pivot = androidx.compose.ui.geometry.Offset(ex, ey))
            }) {
                if (ep.type == "javelin") {
                    drawLine(Color(0xFF6E5536), androidx.compose.ui.geometry.Offset(ex, ey), androidx.compose.ui.geometry.Offset(ex - 80f, ey + 4f), strokeWidth = 7f)
                    val tp = Path().apply {
                        moveTo(ex, ey)
                        lineTo(ex + 18f, ey - 5f)
                        lineTo(ex + 24f, ey)
                        lineTo(ex + 18f, ey + 5f)
                        close()
                    }
                    drawPath(tp, Color(0xFF8C969E))
                    drawPath(tp, TapestryDark, style = Stroke(width = 1.5f))
                } else {
                    // Arrow or ballista
                    val shaftColor = if (ep.isBallista) Color(0xFF8A7156) else TapestryDark
                    val strokeW = if (ep.isBallista) 9f else 5.5f
                    val length = if (ep.isBallista) 80f else 50f
                    
                    drawLine(shaftColor, androidx.compose.ui.geometry.Offset(ex, ey), androidx.compose.ui.geometry.Offset(ex - length, ey), strokeWidth = strokeW)
                    drawCircle(Color(0xFF868C91), radius = if (ep.isBallista) 8f else 5f, center = androidx.compose.ui.geometry.Offset(ex, ey))
                    // Feathers
                    drawLine(TapestryRed, androidx.compose.ui.geometry.Offset(ex - length*0.7f, ey + 2f), androidx.compose.ui.geometry.Offset(ex - length, ey + 12f), strokeWidth = 4f, cap = StrokeCap.Round)
                    drawLine(TapestryRed, androidx.compose.ui.geometry.Offset(ex - length*0.7f, ey - 2f), androidx.compose.ui.geometry.Offset(ex - length, ey - 12f), strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
        }
    }'''
code = code.replace(old_torso_end, new_torso_end)

# 3. Draw bows/crossbows for ancillaries
old_anc_arm = '''                    "anc_cupbearer" -> {
                        // Small golden pitcher or goblet'''
new_anc_arm = '''                    "anc_crossbowman" -> {
                        // Crossbow
                        val armAngle = -15f
                        withTransform({ rotate(armAngle, pivot = Offset(cx, cy + 50f)) }) {
                            // stock
                            drawLine(Color(0xFF5C4033), Offset(cx - 10f, cy + 40f), Offset(cx + 40f, cy + 30f), strokeWidth = 5f)
                            // bow limbs
                            val bowPath = Path().apply {
                                moveTo(cx + 35f, cy + 10f)
                                quadraticBezierTo(cx + 45f, cy + 30f, cx + 35f, cy + 50f)
                            }
                            drawPath(bowPath, Color(0xFF2E2E2E), style = Stroke(width = 4f))
                            // string
                            drawLine(Color(0xFFDDDDDD), Offset(cx + 35f, cy + 10f), Offset(cx + 10f, cy + 35f), strokeWidth = 1.5f)
                            drawLine(Color(0xFFDDDDDD), Offset(cx + 35f, cy + 50f), Offset(cx + 10f, cy + 35f), strokeWidth = 1.5f)
                        }
                    }
                    "anc_archer" -> {
                        // Longbow
                        val armAngle = -30f
                        withTransform({ rotate(armAngle, pivot = Offset(cx, cy + 50f)) }) {
                            val bowPath = Path().apply {
                                moveTo(cx + 30f, cy - 10f)
                                quadraticBezierTo(cx + 45f, cy + 40f, cx + 30f, cy + 90f)
                            }
                            drawPath(bowPath, Color(0xFF6E5536), style = Stroke(width = 4.5f))
                            drawLine(Color(0xFFDDDDDD), Offset(cx + 30f, cy - 10f), Offset(cx + 30f, cy + 90f), strokeWidth = 1f)
                        }
                    }
                    "anc_cupbearer" -> {
                        // Small golden pitcher or goblet'''
code = code.replace(old_anc_arm, new_anc_arm)

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("TapestryRenderer patched for projectile embedding and ancillaries!")
