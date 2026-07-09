import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Hide share button on Win
old_buttons = '''                        Button(
                            onClick = {
                                val uri = generateShareImage(context, player, uiState.scoreMultiplier, uiState.totalKills, isWin)
                                if (uri != null) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Tale"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Share Tale", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TapestryLight)
                        }'''
new_buttons = '''                        if (!isWin) {
                            Button(
                                onClick = {
                                    val uri = generateShareImage(context, player, uiState.scoreMultiplier, uiState.totalKills, isWin)
                                    if (uri != null) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Tale"))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Share Tale", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TapestryLight)
                            }
                        }'''
code = code.replace(old_buttons, new_buttons)

# 2. Fix generateShareImage character scale and weapon name text
old_gen = '''    paint.textSize = 35f
    paint.typeface = android.graphics.Typeface.SERIF
    val statusText = if(isWin) "Vanquished" else "Perished"
    androidCanvas.drawText("Status: " + statusText + " | Score: " + score + "x | Kills: " + kills, width / 2f, 160f, paint)
    
    // Draw character
    val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    val size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
    
    drawScope.draw(
        androidx.compose.ui.unit.Density(context),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        composeCanvas,
        size
    ) {
        withTransform({
            scale(15f, 15f, pivot = androidx.compose.ui.geometry.Offset.Zero)
            translate(10f, -60f) 
        }) {
            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }, posX = 0f, animFrame = 0f, isDead = false, isDying = false)
            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
        }
    }'''

new_gen = '''    paint.textSize = 35f
    paint.typeface = android.graphics.Typeface.SERIF
    val statusText = if(isWin) "Vanquished" else "Perished"
    androidCanvas.drawText("Status: " + statusText + " | Score: " + score + "x | Kills: " + kills, width / 2f, 160f, paint)
    
    val wpnBase = player.weaponHead.name + " on a " + player.weaponHandle.name
    val wpnName = if (player.weaponAttachments.isNotEmpty()) {
        player.weaponAttachments.joinToString(", ") { it.name } + " attached to " + wpnBase
    } else wpnBase
    paint.textSize = 24f
    androidCanvas.drawText("Wielding: " + wpnName, width / 2f, 200f, paint)
    
    // Draw character
    val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    val size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
    
    drawScope.draw(
        androidx.compose.ui.unit.Density(context),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        composeCanvas,
        size
    ) {
        withTransform({
            scale(3.5f, 3.5f, pivot = androidx.compose.ui.geometry.Offset.Zero)
            translate(110f, 10f) 
        }) {
            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }, posX = 0f, animFrame = 0f, isDead = false, isDying = false)
            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
        }
    }'''
code = code.replace(old_gen, new_gen)

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity patched for share button and PNG rendering!")
