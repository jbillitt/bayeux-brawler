import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target = """            // Two-handing (no shield) doubles weapon speed!
            val shieldFactor = if (shield.id == "shield_none") 0.5f else 1.0f"""

replacement = """            // Two-handing (no shield) doubles weapon speed! Missing an arm means you can't two-hand.
            val shieldFactor = if (missingArm) 1.0f else if (shield.id == "shield_none" && !isDualWielding) 0.5f else if (isDualWielding) 0.6f else 1.0f"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
