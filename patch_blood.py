import re

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'r', encoding='utf-8') as f:
    code = f.read()

old_head = '''            // Blood pool on the ground
            if (fountainProgress > 0.3f) {
                val poolProgress = ((fountainProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                val poolW = 45f * poolProgress
                val poolH = 15f * poolProgress
                val poolCenterX = cx + flyDir * 65f
                val poolCenterY = cy + 125f
                scope.drawOval(
                    color = Color(0xFF9E3624).copy(alpha = 0.85f),
                    topLeft = Offset(poolCenterX - poolW, poolCenterY - poolH),
                    size = androidx.compose.ui.geometry.Size(poolW * 2f, poolH * 2f)
                )
                // Highlights in the blood pool
                scope.drawOval(
                    color = Color(0xFF6E2215).copy(alpha = 0.5f),
                    topLeft = Offset(poolCenterX - poolW * 0.6f, poolCenterY - poolH * 0.4f),
                    size = androidx.compose.ui.geometry.Size(poolW * 1.2f, poolH * 0.8f)
                )
            }'''

new_head = '''            // Blood pool is now drawn outside the character rotation in drawCharacter()'''

code = code.replace(old_head, new_head)

old_char = '''            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                if (!fighter.isMounted) {
                    drawLegs(this, cx, cy, fighter)
                }

                val mountOffsetY = if (fighter.isMounted) -68f else 0f
                withTransform({ translate(0f, mountOffsetY) }) {
                    drawTorso(this, cx, cy, fighter)
                    drawHead(this, cx, cy, fighter)
                    drawBackArmAndShield(this, cx, cy, fighter)
                    drawFrontArmAndWeapon(this, cx, cy, fighter)
                }
            }
        }
    }'''

new_char = '''            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                if (!fighter.isMounted) {
                    drawLegs(this, cx, cy, fighter)
                }

                val mountOffsetY = if (fighter.isMounted) -68f else 0f
                withTransform({ translate(0f, mountOffsetY) }) {
                    drawTorso(this, cx, cy, fighter)
                    drawHead(this, cx, cy, fighter)
                    drawBackArmAndShield(this, cx, cy, fighter)
                    drawFrontArmAndWeapon(this, cx, cy, fighter)
                }
            }
            
            // Draw blood pool outside the ragdoll transform so it stays flat on the floor!
            if ((fighter.isDead || fighter.isDying) && fighter.deathType == 5) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                val fountainProgress = progress.coerceIn(0f, 1f)
                if (fountainProgress > 0.3f) {
                    val flyDir = if (fighter.facingRight) -1f else 1f
                    val poolProgress = ((fountainProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    val poolW = 45f * poolProgress * fighter.characterSize
                    val poolH = 15f * poolProgress * fighter.characterSize
                    val poolCenterX = cx + flyDir * 65f * fighter.characterSize
                    val poolCenterY = cy + 90f // Fixed ground Y
                    scope.drawOval(
                        color = Color(0xFF9E3624).copy(alpha = 0.85f),
                        topLeft = Offset(poolCenterX - poolW, poolCenterY - poolH),
                        size = androidx.compose.ui.geometry.Size(poolW * 2f, poolH * 2f)
                    )
                    scope.drawOval(
                        color = Color(0xFF6E2215).copy(alpha = 0.5f),
                        topLeft = Offset(poolCenterX - poolW * 0.6f, poolCenterY - poolH * 0.4f),
                        size = androidx.compose.ui.geometry.Size(poolW * 1.2f, poolH * 0.8f)
                    )
                }
            }
        }
    }'''

code = code.replace(old_char, new_char)

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("TapestryRenderer patched for blood pools!")
