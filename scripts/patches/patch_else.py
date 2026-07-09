import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target = """            SoundType.OUCH -> DAMAGE_SHOUTS.random()
            SoundType.HUZZAH -> VICTORY_SHOUTS.random()
            else -> "" 
            SoundType.SWOOSH -> "SWOOSHUS!"
        }"""

replacement = """            SoundType.OUCH -> DAMAGE_SHOUTS.random()
            SoundType.HUZZAH -> VICTORY_SHOUTS.random()
            SoundType.SWOOSH -> "SWOOSHUS!"
            else -> ""
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
