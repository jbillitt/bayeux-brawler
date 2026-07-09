import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """                            // Find 'shield_none' safely
                            GameData.SHIELDS.find { it.id == "shield_none" }?.let { currTarget = currTarget.copy(shield = it) }"""

replacement = """                            // Find 'shield_none' safely
                            GameData.SHIELDS.find { it.id == "shield_none" }?.let { currTarget.shield = it }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
