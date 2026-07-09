import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """            // Apply Poison Upgrade
            if (proj.isPoisonous) {
                defender.poisonDuration = 5.0f
                addPopup("+POISONED+", defender.posX, 120f, Color(0xFF2E7D32))
            }"""

replacement = """            // Apply Poison Upgrade
            if (proj.isPoisonous) {
                defender.poisonDuration = 5.0f
                addPopup("+POISONED+", defender.posX, 120f, Color(0xFF2E7D32))
            }
            
            // Apply Spikes Bleed
            if (proj.hasSpikes) {
                defender.bleedDuration = 4.0f
                addPopup("+BLEEDING+", defender.posX, 120f, Color(0xFFA62B2B))
            }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)

