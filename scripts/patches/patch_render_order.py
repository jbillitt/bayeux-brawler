import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """                // 1. Draw Legs (Walking cycle)
                drawLegs(this, cx, cy, fighter)
                // 2. Draw Back Arm and Shield (behind torso)
                drawBackArmAndShield(this, cx, cy, fighter)
                // 3. Draw Tunic / Torso with Chainmail Ring-texture if equipped
                drawTorso(this, cx, cy, fighter)
                // 4. Draw Head (Skin, hair cut, mustache, helmet)
                drawHead(this, cx, cy, fighter)
                // 5. Draw Front Arm & Weapon (swinging angle)
                drawFrontArmAndWeapon(this, cx, cy, fighter)"""
                
replacement = """                // 1. Draw Legs (Walking cycle)
                drawLegs(this, cx, cy, fighter)
                // 2. Draw Tunic / Torso with Chainmail Ring-texture if equipped
                drawTorso(this, cx, cy, fighter)
                // 3. Draw Head (Skin, hair cut, mustache, helmet)
                drawHead(this, cx, cy, fighter)
                // 4. Draw Front Arm & Weapon (swinging angle) (drawn behind the shield)
                drawFrontArmAndWeapon(this, cx, cy, fighter)
                // 5. Draw Back Arm and Shield (Drawn on top for 3/4 perspective!)
                drawBackArmAndShield(this, cx, cy, fighter)"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
    f.write(content)

