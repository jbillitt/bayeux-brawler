import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """        // Poison tick over time
        if (fighter.poisonDuration > 0f) {
            fighter.poisonDuration -= dt
            val poisonDmg = 8f * dt // deals 8 damage per second
            if (fighter.hp > 0f) {
                fighter.hp = (fighter.hp - poisonDmg).coerceAtLeast(0f)
                if (Random.nextFloat() < dt * 1.5f) { // occasionally show green "+POISON+" popup
                    addPopup("POISON!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF2E7D32))
                    addBloodParticles(fighter.posX, 100f, count = 2) // tiny droplets
                }
                if (fighter.hp <= 0f) {
                    fighter.isDying = true
                    fighter.animFrame = 0f
                    fighter.deathType = Random.nextInt(0, 6)
                    fighter.deathTime = System.currentTimeMillis()
                    MedievalAudioSynth.playSound(SoundType.OUCH)
                    val deathShout = if (fighter.isPlayer) "VÆ MIHI MORTIS!" else "AARRGGHH!"
                    addPopup(deathShout, fighter.posX, 130f, Color.DarkGray)
                }
            }
        }"""
        
replacement = """        // Poison tick over time
        if (fighter.poisonDuration > 0f) {
            fighter.poisonDuration -= dt
            val poisonDmg = 8f * dt // deals 8 damage per second
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) { // occasionally show green "+POISON+" popup
                    addPopup("POISON!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF2E7D32))
                    addBloodParticles(fighter.posX, 100f, count = 2) // tiny droplets
                }
                applyFlatDamage(poisonDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }
        
        // Bleed tick over time
        if (fighter.bleedDuration > 0f) {
            fighter.bleedDuration -= dt
            val bleedDmg = 12f * dt // deals 12 damage per second
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) {
                    addPopup("BLEED!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFFA62B2B))
                    addBloodParticles(fighter.posX, 100f, count = 3)
                }
                applyFlatDamage(bleedDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)

