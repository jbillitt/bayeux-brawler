import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    content = f.read()

target = """        val isScythe = fighter.weaponHead.id == "head_scythe"
        
        var thrustOffset = Offset.Zero"""

replacement = """        val isScythe = fighter.weaponHead.id == "head_scythe"
        val isBowOrSlingshot = fighter.weaponHead.id in listOf("head_bow", "head_longbow", "head_slingshot")
        
        var thrustOffset = Offset.Zero"""

content = content.replace(target, replacement)

target2 = """            } else if (isHeavy) {
                // Massive overhead smash
                if (swing < 0.5f) {
                    -75f * (swing / 0.5f) // pull back very high
                } else {
                    -75f + 160f * ((swing - 0.5f) / 0.5f) // slam down
                }
            } else {"""

replacement2 = """            } else if (isHeavy) {
                // Massive overhead smash
                if (swing < 0.5f) {
                    -75f * (swing / 0.5f) // pull back very high
                } else {
                    -75f + 160f * ((swing - 0.5f) / 0.5f) // slam down
                }
            } else if (isBowOrSlingshot) {
                // Raise arm and hold, then snap forward
                if (swing < 0.4f) {
                    -35f * (swing / 0.4f) // raise arm
                } else if (swing < 0.85f) {
                    -35f // hold tension
                } else {
                    -35f + 45f * ((swing - 0.85f) / 0.15f) // release
                }
            } else {"""

content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
    f.write(content)

