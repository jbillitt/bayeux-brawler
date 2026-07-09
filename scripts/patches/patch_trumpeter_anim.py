import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """                    "anc_trumpeter" -> {
                        // Trumpet raised to mouth!
                        val armAngle = -25f
                        // The trumpeter usually holds a horn/trumpet. 
                        // Draw a golden horn from the hand!
                        scope.drawLine(
                            androidx.compose.ui.graphics.Color(0xFFD4B144), 
                            androidx.compose.ui.geometry.Offset(cx + if (facingRight) 20f else -20f, cy + 18f),
                            androidx.compose.ui.geometry.Offset(cx + if (facingRight) 45f else -45f, cy + 10f),
                            strokeWidth = 6f
                        )
                    }"""

replacement = """                    "anc_trumpeter" -> {
                        val bpm = (55f + (fighter.level - 1) * 6f).coerceAtMost(110f)
                        val beatMs = (60000f / bpm).toInt()
                        val stepMs = beatMs / 2
                        val totalMs = 32 * stepMs
                        val timeMs = System.currentTimeMillis() % totalMs
                        val currentStep = (timeMs / stepMs).toInt()
                        
                        val isBlowing = currentStep in listOf(1, 2, 9, 10, 17, 18, 25, 26)
                        val armAngle = if (isBlowing) -25f else 15f
                        
                        // Golden horn
                        val hornTipY = if (isBlowing) 10f else 35f
                        scope.drawLine(
                            androidx.compose.ui.graphics.Color(0xFFD4B144), 
                            androidx.compose.ui.geometry.Offset(cx + if (facingRight) 20f else -20f, cy + 18f),
                            androidx.compose.ui.geometry.Offset(cx + if (facingRight) 45f else -45f, cy + hornTipY),
                            strokeWidth = 6f
                        )
                        
                        if (isBlowing) {
                            scope.drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), 4f, androidx.compose.ui.geometry.Offset(cx + if (facingRight) 45f else -45f, cy + hornTipY))
                        }
                    }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
    f.write(content)

