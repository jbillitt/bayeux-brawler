import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target = """    // Death tracking
    var deathType: Int = 0,
    var deathTime: Long = 0
) {"""

replacement = """    // Death tracking
    var deathType: Int = 0,
    var deathTime: Long = 0,
    
    // Status effects
    var missingArm: Boolean = false,
    var isCrumpled: Boolean = false
) {"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
