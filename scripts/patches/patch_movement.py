import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target_all = """            } else if (dist < reachPixels * 0.7f && fighter.moveSpeed > 0f) {
                // Step back to keep them at the tip of our longer weapon!
                val direction = if (target.posX > fighter.posX) -1f else 1f
                fighter.posX += direction * (fighter.moveSpeed * 0.45f) * dt
                fighter.animFrame = (fighter.animFrame - dt * (6f + (fighter.maxHp % 3f))) % 4f 
                
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                    triggerAttack(fighter)
                }
            } else {
                // Wield weapon/Attack!
                fighter.animFrame = 0f // stand
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                    triggerAttack(fighter)
                }
            }"""

replacement_all = """            } else if (dist < reachPixels * 0.7f && fighter.moveSpeed > 0f) {
                // Step back to keep them at the tip of our longer weapon!
                val direction = if (target.posX > fighter.posX) -1f else 1f
                fighter.posX += direction * (fighter.moveSpeed * 0.45f) * dt
                fighter.animFrame = (fighter.animFrame - dt * (6f + (fighter.maxHp % 3f))) % 4f 
                
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                    triggerAttack(fighter)
                }
            } else {
                // Wield weapon/Attack!
                fighter.animFrame = 0f // stand
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                    triggerAttack(fighter)
                }
            }
            
            // Clamp position to screen bounds
            fighter.posX = fighter.posX.coerceIn(30f, 970f)
"""

if target_all in content:
    content = content.replace(target_all, replacement_all)
    with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
