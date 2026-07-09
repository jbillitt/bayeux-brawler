import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """        when (fighter.weaponHead.id) {
            "head_pike", "head_spear" -> {"""

replacement = """        // Dangle rotation if it's on a chain!
        val isChain = fighter.weaponHandle.id == "handle_chain"
        val timeSecs = System.currentTimeMillis() / 1000f
        val dangleAngle = if (isChain) kotlin.math.sin(timeSecs * 5f + fighter.posX) * 20f + 70f else 0f
        
        scope.withTransform({
            if (isChain) {
                rotate(dangleAngle, pivot = headPos)
            }
        }) {
        when (fighter.weaponHead.id) {
            "head_pike", "head_spear" -> {"""

target_end = """                // Small blood thread to show it's "stitched" on
                scope.drawLine(Color(0xFF9E3624), headPos, Offset(headPos.x + 5f, headPos.y - 5f), strokeWidth = 2f)
            }
        }
    }"""
        
replacement_end = """                // Small blood thread to show it's "stitched" on
                scope.drawLine(Color(0xFF9E3624), headPos, Offset(headPos.x + 5f, headPos.y - 5f), strokeWidth = 2f)
            }
        }
        } // close withTransform
    }"""

if target in content and target_end in content:
    content = content.replace(target, replacement).replace(target_end, replacement_end)
    with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
        f.write(content)
    print("Replaced dangle successfully")
else:
    print("Dangle Target not found.")
