import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                // 3. Draw Fighters
                // Draw enemies first
                viewModel.enemiesState.value.forEach { enemy ->
                    com.example.game.TapestryRenderer.drawCharacter(this, enemy, scale = 1f)
                    val px = enemy.posX * playerScaleX
                    val py = 200f * scaleFactor
                    if (!enemy.isDead && !enemy.isDying) {
                        drawHealthBar(this, px, py - 120f, enemy.hp, enemy.maxHp, enemy.maxHp)
                    }
                }
                
                // Draw player
                val px = player.posX * playerScaleX
                val py = 200f * scaleFactor
                com.example.game.TapestryRenderer.drawCharacter(this, player, scale = 1f)
                if (!player.isDead && !player.isDying) {
                    drawHealthBar(this, px, py - 120f, player.hp, player.maxHp, player.maxHp)
                }"""
                
replacement = """                // 3. Draw Fighters
                // Draw enemies first
                viewModel.enemiesState.value.forEach { enemy ->
                    com.example.game.TapestryRenderer.drawCharacter(this, enemy, scale = 1f)
                    val px = enemy.posX * playerScaleX
                    val py = 200f * scaleFactor
                    if (!enemy.isDead && !enemy.isDying) {
                        drawHealthBar(this, px, py - 120f, enemy.hp, enemy.maxHp, enemy.maxHp)
                        drawStatusEffects(this, px, py - 135f, enemy)
                    }
                }
                
                // Draw player
                val px = player.posX * playerScaleX
                val py = 200f * scaleFactor
                com.example.game.TapestryRenderer.drawCharacter(this, player, scale = 1f)
                if (!player.isDead && !player.isDying) {
                    drawHealthBar(this, px, py - 120f, player.hp, player.maxHp, player.maxHp)
                    drawStatusEffects(this, px, py - 135f, player)
                }"""

content = content.replace(target, replacement)

target2 = """private fun drawHealthBar(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, hp: Float, ghostHp: Float, maxHp: Float) {"""

replacement2 = """private fun drawStatusEffects(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, fighter: com.example.game.FighterState) {
    var offsetX = x - 10f
    val iconRadius = 4f
    
    if (fighter.poisonDuration > 0f) {
        // Draw poison symbol (green circle with P?) We can just draw a little green bubble
        scope.drawCircle(color = Color(0xFF2E7D32), radius = iconRadius, center = Offset(offsetX, y))
        offsetX += 12f
    }
    if (fighter.bleedDuration > 0f) {
        // Draw bleed symbol (red droplet)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(offsetX, y - iconRadius)
            lineTo(offsetX + iconRadius, y + iconRadius * 0.5f)
            arcTo(androidx.compose.ui.geometry.Rect(offsetX - iconRadius, y - iconRadius * 0.5f, offsetX + iconRadius, y + iconRadius * 1.5f), 0f, 180f, false)
            close()
        }
        scope.drawPath(path, color = Color(0xFFA62B2B))
        offsetX += 12f
    }
}

private fun drawHealthBar(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, hp: Float, ghostHp: Float, maxHp: Float) {"""

content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

