import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """        val handleLen = if (isBowOrSlingshot) {
            0f
        } else {
            when (fighter.weaponHandle.id) {
                "handle_long" -> 110f
                "handle_medium" -> 55f
                else -> 30f // short
            }
        }"""

replacement = """        val handleLen = if (isBowOrSlingshot) {
            0f
        } else {
            when (fighter.weaponHandle.id) {
                "handle_long" -> 110f
                "handle_medium" -> 55f
                "handle_chain" -> 60f
                else -> 30f // short
            }
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
