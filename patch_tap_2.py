import re

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Move Blood Pool BEFORE the character transform so it draws underneath
old_blood_pool_block = '''            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                if (!fighter.isMounted) {
                    drawLegs(this, cx, cy, fighter)
                }

                val mountOffsetY = if (fighter.isMounted) -35f else 0f
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
                    val poolW = 45f * poolProgress * fighter.size
                    val poolH = 15f * poolProgress * fighter.size
                    val poolCenterX = cx + flyDir * 65f * fighter.size
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

new_blood_pool_block = '''            // Draw blood pool BEFORE the ragdoll transform so it stays flat on the floor AND underneath the body!
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
            }

            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                if (!fighter.isMounted) {
                    drawLegs(this, cx, cy, fighter)
                }

                val mountOffsetY = if (fighter.isMounted) -35f else 0f
                withTransform({ translate(0f, mountOffsetY) }) {
                    drawTorso(this, cx, cy, fighter)
                    drawHead(this, cx, cy, fighter)
                    drawBackArmAndShield(this, cx, cy, fighter)
                    drawFrontArmAndWeapon(this, cx, cy, fighter)
                }
            }'''
code = code.replace(old_blood_pool_block, new_blood_pool_block)


# 2. Fix shield arm positioning to be right of body (front)
old_shield_arm_1 = '''        if (fighter.missingArm) {
            scope.withTransform({ rotate(shieldArmAngle, pivot = Offset(cx - 5f, cy + 25f)) }) {
                val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(cx - 10f, cy + 35f), sleeveColor)
                scope.drawCircle(Color(0xFF9E3624), radius = 7f, center = Offset(cx - 10f, cy + 35f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 3.5f, center = Offset(cx - 10f, cy + 35f))
            }
            return
        }'''
new_shield_arm_1 = '''        if (fighter.missingArm) {
            scope.withTransform({ rotate(shieldArmAngle, pivot = Offset(cx + 5f, cy + 25f)) }) {
                val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                drawStitchedStrap(this, Offset(cx + 5f, cy + 25f), Offset(cx + 10f, cy + 35f), sleeveColor)
                scope.drawCircle(Color(0xFF9E3624), radius = 7f, center = Offset(cx + 10f, cy + 35f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 3.5f, center = Offset(cx + 10f, cy + 35f))
            }
            return
        }'''
code = code.replace(old_shield_arm_1, new_shield_arm_1)

old_shield_arm_2 = '''                scope.withTransform({
                    rotate(armAngle, pivot = Offset(cx - 5f, cy + 30f))
                }) {
                    val hx = cx - 30f
                    val hy = cy + 40f
                    val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                    drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)'''
new_shield_arm_2 = '''                scope.withTransform({
                    rotate(armAngle, pivot = Offset(cx + 5f, cy + 30f))
                }) {
                    val hx = cx + 25f
                    val hy = cy + 40f
                    val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                    drawStitchedStrap(this, Offset(cx + 5f, cy + 25f), Offset(hx, hy), sleeveColor)'''
code = code.replace(old_shield_arm_2, new_shield_arm_2)

old_shield_arm_3 = '''                    scope.withTransform({
                        rotate(armAngle, pivot = Offset(cx - 5f, cy + 30f))
                    }) {
                        val hx = cx - 5f
                        val hy = cy + 45f
                        val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                        drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)'''
new_shield_arm_3 = '''                    scope.withTransform({
                        rotate(armAngle, pivot = Offset(cx + 5f, cy + 30f))
                    }) {
                        val hx = cx + 8f
                        val hy = cy + 45f
                        val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
                        drawStitchedStrap(this, Offset(cx + 5f, cy + 25f), Offset(hx, hy), sleeveColor)'''
code = code.replace(old_shield_arm_3, new_shield_arm_3)

old_shield_arm_4 = '''        scope.withTransform({
            rotate(shieldArmAngle, pivot = Offset(cx - 5f, cy + 25f))
        }) {
            val hx = cx - 8f
            val hy = cy + 45f
            val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
            drawStitchedStrap(this, Offset(cx - 5f, cy + 25f), Offset(hx, hy), sleeveColor)
            scope.drawCircle(Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
            scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
        }

        val shx = cx - 8f
        val shy = cy + 45f'''
new_shield_arm_4 = '''        scope.withTransform({
            rotate(shieldArmAngle, pivot = Offset(cx + 5f, cy + 25f))
        }) {
            val hx = cx + 8f
            val hy = cy + 45f
            val sleeveColor = if (fighter.isPlayer) Color(0xFF1E3F4F) else Color(0xFF8A2E1E)
            drawStitchedStrap(this, Offset(cx + 5f, cy + 25f), Offset(hx, hy), sleeveColor)
            scope.drawCircle(Color(0xFFE8C5A4), radius = 5f, center = Offset(hx, hy))
            scope.drawCircle(ThreadColor, radius = 5f, center = Offset(hx, hy), style = Stroke(width = 2f))
        }

        val shx = cx + 8f
        val shy = cy + 45f'''
code = code.replace(old_shield_arm_4, new_shield_arm_4)

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("TapestryRenderer patched for shield arm and blood pool!")
