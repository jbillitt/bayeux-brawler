import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """        // Handle shaft (wooden)
        val shaftEnd = Offset(hx + handleLen * 0.8f, hy - handleLen * 0.4f)
        if (!isBowOrSlingshot) {
            scope.drawLine(
                color = fighter.weaponHandle.color,
                start = Offset(hx - 25f, hy + 12f), // pommel/grip end extends further back
                end = shaftEnd,
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            // Outline shaft
            scope.drawLine(
                color = ThreadColor,
                start = Offset(hx - 25f, hy + 12f),
                end = shaftEnd,
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
        }

        // Weapon Head
        val headPos = shaftEnd"""

replacement = """        // Handle shaft (wooden)
        val shaftEnd = Offset(hx + handleLen * 0.8f, hy - handleLen * 0.4f)
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
                        center = Offset(pX, pY)
                    )
                    scope.drawCircle(
                        color = ThreadColor,
                        radius = 3f,
                        center = Offset(pX, pY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                    if (i == linkCount) {
                        headPos = Offset(pX, pY)
                    }
                }
            } else {
                scope.drawLine(
                    color = fighter.weaponHandle.color,
                    start = Offset(hx - 25f, hy + 12f), // pommel/grip end extends further back
                    end = shaftEnd,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                // Outline shaft
                scope.drawLine(
                    color = ThreadColor,
                    start = Offset(hx - 25f, hy + 12f),
                    end = shaftEnd,
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Round
                )
            }
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
