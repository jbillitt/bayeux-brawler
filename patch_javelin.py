import re

# 1. Update GameViewModel.kt
with open('app/src/main/java/com/example/game/GameViewModel.kt', 'r', encoding='utf-8') as f:
    gvm = f.read()

# Add gravityMult to Projectile
gvm = gvm.replace("val isBallista: Boolean = false\n)", "val isBallista: Boolean = false,\n    val gravityMult: Float = 1f\n)")

# Update gravity application
gvm = gvm.replace("proj.velocityY += 130f * dt // Gravity pulling it downwards!", "proj.velocityY += (130f * proj.gravityMult) * dt // Gravity pulling it downwards!")

# Update performStrike for Javelin
strike_old = '''            // Stats based on weapon head
            val isSlingshot = attacker.weaponHead.id == "head_slingshot"
            val projType = if (isSlingshot) "stone" else "arrow"
            
            var sizeMult = 1f
            var spikes = false
            var launchedWep: String? = null
            var splash = false
            var poison = false
            var ballista = false
            var finalDmg = attacker.baseDamage
            var finalPierce = attacker.damagePierce
            var finalBlunt = attacker.damageBlunt
            var velY = -55f // slightly arched trajectory
            var velX = dir * 350f'''

strike_new = '''            // Stats based on weapon head
            val isSlingshot = attacker.weaponHead.id == "head_slingshot"
            val isJavelin = attacker.weaponHead.id == "head_javelin"
            val projType = if (isSlingshot) "stone" else if (isJavelin) "javelin" else "arrow"
            
            var sizeMult = 1f
            var spikes = false
            var launchedWep: String? = null
            var splash = false
            var poison = false
            var ballista = false
            var finalDmg = attacker.baseDamage
            var finalPierce = attacker.damagePierce
            var finalBlunt = attacker.damageBlunt
            var velY = -55f // slightly arched trajectory
            var velX = dir * 350f
            var gravMult = 1f'''
gvm = gvm.replace(strike_old, strike_new)

slingshot_old = '''            if (isSlingshot) {
                if (attacker.rangedUpgrades.contains("slingshot_bigger")) {'''
slingshot_new = '''            if (isSlingshot) {
                if (attacker.rangedUpgrades.contains("slingshot_bigger")) {'''
# wait, I need to replace the else block logic

bow_old = '''                if (attacker.rangedUpgrades.contains("slingshot_poison")) {
                    poison = true
                }
            } else {
                // Bow performance variance
                val varianceMult = Random.nextFloat() * 0.9f + 0.6f // 0.6 to 1.5'''

bow_new = '''                if (attacker.rangedUpgrades.contains("slingshot_poison")) {
                    poison = true
                }
            } else if (isJavelin) {
                gravMult = 0.4f // glide!
                velY = -30f
                velX = dir * 380f
            } else {
                // Bow performance variance
                val varianceMult = Random.nextFloat() * 0.9f + 0.6f // 0.6 to 1.5'''
gvm = gvm.replace(bow_old, bow_new)

proj_old = '''                isPoisonous = poison,
                isBallista = ballista
            )
            projectiles.add(proj)'''
proj_new = '''                isPoisonous = poison,
                isBallista = ballista,
                gravityMult = gravMult
            )
            projectiles.add(proj)'''
gvm = gvm.replace(proj_old, proj_new)

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(gvm)


# 2. Update MainActivity.kt rendering
with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    main = f.read()

draw_old = '''                    } else if (proj.type == "arrow") {'''
draw_new = '''                    } else if (proj.type == "javelin") {
                        // Draw huge spear
                        val shaftColor = Color(0xFF6E5536) // Darker wood
                        val strokeW = 6.5f
                        val length = 60f
                        
                        // Shaft
                        drawLine(
                            color = shaftColor,
                            start = Offset(sx, sy),
                            end = Offset(sx - (length * arrowDir), sy + 3f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        // Giant Iron Spear Tip
                        val hPath = Path().apply {
                            moveTo(sx - (4f * arrowDir), sy)
                            lineTo(sx + (18f * arrowDir), sy - 5f)
                            lineTo(sx + (24f * arrowDir), sy)
                            lineTo(sx + (18f * arrowDir), sy + 5f)
                            close()
                        }
                        drawPath(hPath, Color(0xFF8C969E))
                        drawPath(hPath, TapestryDark, style = Stroke(width = 1.5f))
                        
                        // Some leather bindings
                        drawLine(TapestryDark, Offset(sx - (4f * arrowDir), sy - 3f), Offset(sx - (4f * arrowDir), sy + 3f), strokeWidth = 3f)
                        drawLine(TapestryDark, Offset(sx - (8f * arrowDir), sy - 3f), Offset(sx - (8f * arrowDir), sy + 3f), strokeWidth = 3f)
                    } else if (proj.type == "arrow") {'''
main = main.replace(draw_old, draw_new)

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(main)

print("Javelin patched!")
