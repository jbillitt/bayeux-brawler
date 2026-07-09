import re

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Replace the blood pool in drawCharacter with stream + pool
old_pool_char = '''            // Draw blood pool BEFORE the ragdoll transform so it stays flat on the floor AND underneath the body!
            if ((fighter.isDead || fighter.isDying) && fighter.deathType == 5) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                val fountainProgress = progress.coerceIn(0f, 1f)
                if (fountainProgress > 0.3f) {
                    val flyDir = if (fighter.facingRight) -1f else 1f
                    val poolProgress = ((fountainProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    val poolW = 45f * poolProgress * fighter.size
                    val poolH = 15f * poolProgress * fighter.size
                    // Attach to where the blood is coming from (the neck area base, around cx + 20f)
                    val poolCenterX = cx + flyDir * 20f * fighter.size
                    val poolCenterY = cy + 90f // Fixed ground Y
                    drawScope.drawOval(
                        color = Color(0xFF9E3624).copy(alpha = 0.85f),
                        topLeft = Offset(poolCenterX - poolW, poolCenterY - poolH),
                        size = androidx.compose.ui.geometry.Size(poolW * 2f, poolH * 2f)
                    )
                    drawScope.drawOval(
                        color = Color(0xFF6E2215).copy(alpha = 0.5f),
                        topLeft = Offset(poolCenterX - poolW * 0.6f, poolCenterY - poolH * 0.4f),
                        size = androidx.compose.ui.geometry.Size(poolW * 1.2f, poolH * 0.8f)
                    )
                }
            }'''

new_pool_char = '''            // Draw blood pool and stream BEFORE the ragdoll transform so it stays flat on the floor AND underneath the body!
            if ((fighter.isDead || fighter.isDying) && fighter.deathType == 5) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                val fountainProgress = progress.coerceIn(0f, 1f)
                if (fountainProgress > 0.05f) {
                    val flyDir = if (fighter.facingRight) -1f else 1f
                    
                    // Track world position of the rotating neck
                    val angleRad = Math.toRadians(rotationAngle.toDouble())
                    val nx = cx + (-70f * Math.sin(angleRad)).toFloat() * fighter.size
                    val ny = (cy + 80f) + (-70f * Math.cos(angleRad)).toFloat() * fighter.size
                    
                    // The pool is where the neck ultimately lands
                    val poolCenterX = cx + flyDir * 70f * fighter.size
                    val poolCenterY = cy + 90f 
                    
                    // 1. Draw Stream FIRST (so pool overlaps it)
                    val streamPath = Path().apply {
                        moveTo(nx, ny)
                        quadraticTo(
                            nx + flyDir * 35f * fountainProgress,
                            ny - 80f * fountainProgress,
                            poolCenterX,
                            poolCenterY
                        )
                    }
                    drawScope.drawPath(streamPath, Color(0xFF9E3624), style = Stroke(width = 10f * fighter.size, cap = StrokeCap.Round))
                    drawScope.drawPath(streamPath, Color(0xFFBF4040), style = Stroke(width = 5f * fighter.size, cap = StrokeCap.Round))
                    
                    // 2. Draw Pool (overlaps stream)
                    val poolProgress = ((fountainProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    if (poolProgress > 0f) {
                        val poolW = 45f * poolProgress * fighter.size
                        val poolH = 15f * poolProgress * fighter.size
                        drawScope.drawOval(
                            color = Color(0xFF9E3624).copy(alpha = 0.85f),
                            topLeft = Offset(poolCenterX - poolW, poolCenterY - poolH),
                            size = androidx.compose.ui.geometry.Size(poolW * 2f, poolH * 2f)
                        )
                        drawScope.drawOval(
                            color = Color(0xFF6E2215).copy(alpha = 0.5f),
                            topLeft = Offset(poolCenterX - poolW * 0.6f, poolCenterY - poolH * 0.4f),
                            size = androidx.compose.ui.geometry.Size(poolW * 1.2f, poolH * 0.8f)
                        )
                    }
                }
            }'''
code = code.replace(old_pool_char, new_pool_char)

# 2. Remove fountain from drawHead
old_fountain = '''            // Draw a blood fountain from the neck stump
            val fountainProgress = progress.coerceIn(0f, 1f)
            
            val landX = cx + flyDir * 65f * fountainProgress
            val landY = cy + 10f + (115f * fountainProgress)
            
            // Blood pool is now drawn outside the character rotation in drawCharacter()
            
            val fountainPath = Path().apply {
                moveTo(cx - 5f, cy + 10f)
                quadraticTo(
                    cx + flyDir * 35f * fountainProgress,
                    cy - 80f * fountainProgress,
                    landX,
                    landY
                )
            }
            scope.drawPath(fountainPath, Color(0xFF9E3624), style = Stroke(width = 10f, cap = StrokeCap.Round))
            scope.drawPath(fountainPath, Color(0xFFBF4040), style = Stroke(width = 5f, cap = StrokeCap.Round))
            
            val fountainPath2 = Path().apply {
                moveTo(cx + 3f, cy + 10f)
                quadraticTo(
                    cx + flyDir * 20f * fountainProgress,
                    cy - 50f * fountainProgress,
                    cx + flyDir * 45f * fountainProgress,
                    cy + 10f + (80f * fountainProgress)
                )
            }
            scope.drawPath(fountainPath2, Color(0xFF9E3624), style = Stroke(width = 6f, cap = StrokeCap.Round))'''
new_fountain = '''            // Blood fountain is now drawn in world-space in drawCharacter() so it connects properly to the pool!'''
code = code.replace(old_fountain, new_fountain)

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("Blood stream fixed!")
