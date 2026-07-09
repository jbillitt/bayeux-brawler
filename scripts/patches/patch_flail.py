import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """        if (!isBowOrSlingshot) {
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
            } else {"""
            
replacement = """        if (!isBowOrSlingshot) {
            if (fighter.weaponHandle.id == "handle_chain") {
                // Draw a rigid handle first
                val rigidHandleLen = 30f
                val rigidEnd = androidx.compose.ui.geometry.Offset(hx + rigidHandleLen * 0.8f, hy - rigidHandleLen * 0.4f)
                scope.drawLine(
                    color = Color(0xFF5A442E), // wood handle color
                    start = androidx.compose.ui.geometry.Offset(hx, hy),
                    end = rigidEnd,
                    strokeWidth = 6f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                // Draw a flailing chain with physics-like whip
                val timeSecs = System.currentTimeMillis() / 1000f
                val baseFlailX = kotlin.math.sin(timeSecs * 10f + fighter.posX) * 5f
                val baseFlailY = kotlin.math.cos(timeSecs * 12f + fighter.posX) * 5f
                
                var whipX = 0f
                var whipY = 15f // gravity sag
                
                // Physics-like flail based on swing progress
                if (fighter.isAttacking) {
                    val swing = fighter.swingProgress
                    if (swing < 0.5f) {
                        // Wind up: chain lags behind
                        whipX = -30f * (swing / 0.5f)
                        whipY = 20f * (swing / 0.5f)
                    } else {
                        // Strike: chain whips forward!
                        val strike = (swing - 0.5f) / 0.5f
                        whipX = -30f + 70f * strike
                        whipY = 20f - 40f * strike
                    }
                } else if (!fighter.isDead && fighter.velocityX != 0f) {
                    whipX = -fighter.velocityX * 0.2f
                }
                
                val linkCount = 7
                val dx = (shaftEnd.x - rigidEnd.x) / linkCount
                val dy = (shaftEnd.y - rigidEnd.y) / linkCount
                for (i in 0..linkCount) {
                    val progress = i.toFloat() / linkCount
                    val pX = rigidEnd.x + dx * i + baseFlailX * progress + (whipX * progress * progress)
                    val pY = rigidEnd.y + dy * i + baseFlailY * progress + (whipY * progress)
                    
                    scope.drawCircle(
                        color = fighter.weaponHandle.color,
                        radius = 2.5f,
                        center = androidx.compose.ui.geometry.Offset(pX, pY)
                    )
                    scope.drawCircle(
                        color = ThreadColor,
                        radius = 2.5f,
                        center = androidx.compose.ui.geometry.Offset(pX, pY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                    if (i == linkCount) {
                        headPos = androidx.compose.ui.geometry.Offset(pX, pY)
                    }
                }
            } else {"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
    f.write(content)

