import re

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Add head_javelin, head_mace, and else fallback
old_when = '''                    "head_crossbow" -> {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x - 10f, headPos.y + 10f)
                            lineTo(headPos.x + 30f, headPos.y - 20f)
                        }
                        scope.drawPath(path, androidx.compose.ui.graphics.Color(0xFF6E5536), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                        val bowPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x + 20f, headPos.y - 35f)
                            quadraticTo(headPos.x + 60f, headPos.y, headPos.x + 20f, headPos.y + 35f)
                        }
                        scope.drawPath(bowPath, fighter.weaponHead.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                        scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x + 20f, headPos.y - 35f), androidx.compose.ui.geometry.Offset(headPos.x + 20f, headPos.y + 35f), strokeWidth = 1.5f)
                    }
                }
            }'''

new_when = '''                    "head_crossbow" -> {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x - 10f, headPos.y + 10f)
                            lineTo(headPos.x + 30f, headPos.y - 20f)
                        }
                        scope.drawPath(path, androidx.compose.ui.graphics.Color(0xFF6E5536), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                        val bowPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x + 20f, headPos.y - 35f)
                            quadraticTo(headPos.x + 60f, headPos.y, headPos.x + 20f, headPos.y + 35f)
                        }
                        scope.drawPath(bowPath, fighter.weaponHead.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                        scope.drawLine(androidx.compose.ui.graphics.Color(0xFFE4D6B6), androidx.compose.ui.geometry.Offset(headPos.x + 20f, headPos.y - 35f), androidx.compose.ui.geometry.Offset(headPos.x + 20f, headPos.y + 35f), strokeWidth = 1.5f)
                    }
                    "head_javelin" -> {
                        // Draw a short javelin head
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x, headPos.y)
                            lineTo(headPos.x + 10f, headPos.y - 5f)
                            lineTo(headPos.x + 25f, headPos.y - 7f) // point
                            lineTo(headPos.x + 12f, headPos.y - 1f)
                            close()
                        }
                        scope.drawPath(path, fighter.weaponHead.color)
                        scope.drawPath(path, ThreadColor, style = StitchedStroke)
                    }
                    "head_mace" -> {
                        // Draw flanged mace head
                        scope.drawCircle(fighter.weaponHead.color, radius = 14f, center = headPos)
                        scope.drawCircle(ThreadColor, radius = 14f, center = headPos, style = StitchedStroke)
                        for (i in 0 until 4) {
                            val angle = i * Math.PI / 2
                            val p1 = androidx.compose.ui.geometry.Offset(headPos.x + kotlin.math.cos(angle).toFloat() * 18f, headPos.y + kotlin.math.sin(angle).toFloat() * 18f)
                            val p2 = androidx.compose.ui.geometry.Offset(headPos.x - kotlin.math.cos(angle).toFloat() * 18f, headPos.y - kotlin.math.sin(angle).toFloat() * 18f)
                            scope.drawLine(ThreadColor, p1, p2, strokeWidth = 4f)
                        }
                    }
                    "head_halberd" -> {
                        // Halberd head
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headPos.x, headPos.y)
                            lineTo(headPos.x + 10f, headPos.y - 20f)
                            lineTo(headPos.x + 20f, headPos.y - 5f)
                            lineTo(headPos.x + 35f, headPos.y - 5f) // top point
                            lineTo(headPos.x + 10f, headPos.y + 10f)
                            close()
                        }
                        scope.drawPath(path, fighter.weaponHead.color)
                        scope.drawPath(path, ThreadColor, style = StitchedStroke)
                    }
                    else -> {
                        // Generic fallback so we never draw empty
                        scope.drawCircle(fighter.weaponHead.color, radius = 10f, center = headPos)
                        scope.drawCircle(ThreadColor, radius = 10f, center = headPos, style = StitchedStroke)
                    }
                }
            }'''

code = code.replace(old_when, new_when)

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("TapestryRenderer patched for robustness!")
