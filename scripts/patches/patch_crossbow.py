import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target = """        GearItem("head_crossbow", "Heavy Crossbow", ItemType.WEAPON_HEAD, 3.0f, pierce = 40f, reach = 12.0f, isRanged = true, description = "A mechanical bow. Immense armor piercing, but slow to crank.", color = Color(0xFF4A3B2C))"""
replacement = """        GearItem("head_crossbow", "Heavy Crossbow", ItemType.WEAPON_HEAD, 1.5f, pierce = 65f, reach = 12.0f, isRanged = true, description = "A mechanical bow. High armor piercing and fast to crank.", color = Color(0xFF4A3B2C))"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
    f.write(content)

