import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target = """            val handleSpeedPenalty = weaponHandle.speedPenalty + (if (weaponHandle.id == "handle_double_ended") 0.15f else 0f)
            val finalDelay = baseDelay * weightFactor * shieldFactor * (1f + handleSpeedPenalty)
            return max(0.4f, finalDelay)
        }

    // Move speed multiplier based on mass and speed penalties
    val moveSpeed: Float
        get() {
            val baseSpeed = if (isPlayer) 75f else 60f // Pixels per second
            // Bigger characters move slower base speed
            val sizeSpeed = baseSpeed / size
            val penaltyFactor = 1f - (totalMass * 0.025f).coerceIn(0f, 0.6f)
            return (sizeSpeed * penaltyFactor) * (1f + speedBoost)
        }"""

replacement = """            val handleSpeedPenalty = weaponHandle.speedPenalty + (if (weaponHandle.id == "handle_double_ended") 0.15f else 0f)
            val crumpleFactor = if (isCrumpled) 1.5f else 1.0f
            val finalDelay = baseDelay * weightFactor * shieldFactor * (1f + handleSpeedPenalty) * crumpleFactor
            return max(0.4f, finalDelay)
        }

    // Move speed multiplier based on mass and speed penalties
    val moveSpeed: Float
        get() {
            val baseSpeed = if (isPlayer) 75f else 60f // Pixels per second
            // Bigger characters move slower base speed
            val sizeSpeed = baseSpeed / size
            val crumplePenalty = if (isCrumpled) 0.5f else 1.0f
            val penaltyFactor = 1f - (totalMass * 0.025f).coerceIn(0f, 0.6f)
            return (sizeSpeed * penaltyFactor) * (1f + speedBoost) * crumplePenalty
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")

