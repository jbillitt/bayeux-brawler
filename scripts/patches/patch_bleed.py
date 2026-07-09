import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

content = content.replace("    var poisonDuration: Float = 0f,", "    var poisonDuration: Float = 0f,\n    var bleedDuration: Float = 0f,")

with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
    f.write(content)

