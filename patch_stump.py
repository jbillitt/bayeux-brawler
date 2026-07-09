import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """        // 1. Update Fighter Timers and Status Effects
        val allFighters = listOf(player) + _enemiesState.value"""

replacement = """        // 1. Update Fighter Timers and Status Effects
        val allFighters = listOf(player) + _enemiesState.value
        allFighters.forEach { f ->
            if (f.missingArm && !f.isDead) {
                if (kotlin.random.Random.nextFloat() < dt * 4f) { // a few times a second
                    addBloodParticles(f.posX - if(f.facingRight) 5f else -5f, 130f, count = 2)
                }
            }
        }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)

