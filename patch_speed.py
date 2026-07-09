import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target = """            val baseDelay = if (isRanged) {
                if (weaponHead.id == "head_slingshot") 0.9f
                else if (weaponHead.id == "head_longbow") 2.2f
                else 1.6f
            } else 1.1f"""

replacement = """            val baseDelay = if (isRanged) {
                if (weaponHead.id == "head_slingshot") 1.8f // Slower slingshot
                else if (weaponHead.id == "head_longbow") 2.5f
                else 2.0f
            } else 1.1f"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")

