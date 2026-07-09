import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """            // Handle dying fall down rotation
            var rotationAngle = 0f
            var offsetX = 0f
            var offsetY = 0f
            var scaleY = 1f
            if (fighter.isDead || fighter.isDying) {"""

replacement = """            // Handle dying fall down rotation
            var rotationAngle = 0f
            var offsetX = 0f
            var offsetY = 0f
            var scaleY = 1f
            if (fighter.isCrumpled && !fighter.isDead && !fighter.isDying) {
                // Smashed down into a squashed pancake, but still alive? Or normally it happens on death?
                // Let's make them squashed if they got a huge blunt hit
                scaleY = 0.6f
                offsetY = 30f
            }
            if (fighter.isDead || fighter.isDying) {"""

if target in content:
    content = content.replace(target, replacement)
    
target2 = """                    3 -> { // Squashed pancake falling over
                        rotationAngle = if (fighter.facingRight) -85f * progress else 85f * progress
                        scaleY = 1f - 0.7f * progress
                        offsetY = 65f * progress
                    }"""
                    
replacement2 = """                    3 -> { // Squashed pancake falling over
                        rotationAngle = if (fighter.facingRight) -85f * progress else 85f * progress
                        scaleY = (if (fighter.isCrumpled) 0.6f else 1f) - 0.7f * progress
                        offsetY = 65f * progress
                    }"""

if target2 in content:
    content = content.replace(target2, replacement2)
    
target3 = """    private fun drawBackArmAndShield(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        val sColor = fighter.shield.color"""

replacement3 = """    private fun drawBackArmAndShield(scope: DrawScope, cx: Float, cy: Float, fighter: FighterState) {
        if (fighter.missingArm) {
            // Draw a bloody stump
            val sleeveColor = if (fighter.isPlayer) androidx.compose.ui.graphics.Color(0xFF1E3F4F) else androidx.compose.ui.graphics.Color(0xFF8A2E1E)
            scope.drawLine(sleeveColor, androidx.compose.ui.geometry.Offset(cx - 5f, cy + 25f), androidx.compose.ui.geometry.Offset(cx - 5f, cy + 30f), strokeWidth = 10f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            scope.drawCircle(androidx.compose.ui.graphics.Color(0xFF9E3624), radius = 5f, center = androidx.compose.ui.geometry.Offset(cx - 5f, cy + 30f))
            return
        }
        val sColor = fighter.shield.color"""

if target3 in content:
    content = content.replace(target3, replacement3)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
    f.write(content)
print("Replaced successfully")
