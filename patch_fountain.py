import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """            // Draw a blood fountain from the neck stump
            val fountainPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - 5f, cy + 10f)
                quadraticTo(cx + flyDir * 20f * progress, cy - 50f * progress, cx + flyDir * 40f * progress, cy - 10f * progress)
            }
            scope.drawPath(fountainPath, Color(0xFF9E3624), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))"""

replacement = """            // Draw a blood fountain from the neck stump (Solid stream instead of sausages)
            val fountainPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - 5f, cy + 10f)
                quadraticTo(cx + flyDir * 20f * progress, cy - 50f * progress, cx + flyDir * 40f * progress, cy - 10f * progress)
            }
            scope.drawPath(fountainPath, Color(0xFF9E3624), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
    f.write(content)

