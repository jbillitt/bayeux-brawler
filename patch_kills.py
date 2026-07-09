import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

content = content.replace("    val isMounted: Boolean = false", "    val isMounted: Boolean = false,\n    var kills: Int = 0")
content = content.replace("val showLevelUpScreen: Boolean = false", "val showLevelUpScreen: Boolean = false,\n    val totalKills: Int = 0")

with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
    f.write(content)

