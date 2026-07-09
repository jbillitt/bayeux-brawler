import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """                    // Splash damage for big heavy weapons!
                    if (attacker.totalMass > 5.0f && attacker.damageSlash > 10f) { // heavy weapon like axe or claymore"""

replacement = """                    // Limb loss mechanic! (heavy slash)
                    if (slash > 18f && kotlin.random.Random.nextFloat() < 0.2f && !currTarget.missingArm) {
                        currTarget.missingArm = true
                        // Disarm off-hand/shield logically
                        if (currTarget.isDualWielding || currTarget.shield.id != "shield_none") {
                            currTarget.isDualWielding = false
                            // Find 'shield_none' safely
                            GameData.SHIELDS.find { it.id == "shield_none" }?.let { currTarget = currTarget.copy(shield = it) }
                        }
                        MedievalAudioSynth.playSound(SoundType.THWACK)
                        addPopup("ARM SEVERED!", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.Red)
                    }
                    
                    // Crumple mechanic! (heavy blunt)
                    if (blunt > 18f && kotlin.random.Random.nextFloat() < 0.25f && !currTarget.isCrumpled) {
                        currTarget.isCrumpled = true
                        MedievalAudioSynth.playSound(SoundType.CRUNCH)
                        addPopup("CRUMPLED!", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.DarkGray)
                    }

                    // Splash damage for big heavy weapons!
                    if (attacker.totalMass > 5.0f && attacker.damageSlash > 10f) { // heavy weapon like axe or claymore"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
