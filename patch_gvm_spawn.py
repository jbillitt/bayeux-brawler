import re

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Make projectiles spawn from the correct ancillary position!
# We need to find where ancillaries are sorted to get their index.
# In TapestryRenderer: val sortedAncs = fighter.unlockedAncillaries.sorted()
# The offset is: val offsetX = if (isPlayer) -80f - (idx * 50f) else 80f + (idx * 50f)
# cy is 200f. 
# Crossbow is at cy + 40f => 240f
# Bow is at cy + 50f => 250f

old_fire = '''        // 5b. Archer & Crossbowman Entourage Fire
        val hasArcher = _uiState.value.unlockedAncillaries.contains("anc_archer")
        val hasCrossbow = _uiState.value.unlockedAncillaries.contains("anc_crossbowman")
        if (!player.isDead && !player.isDying) {
            if (hasArcher && Random.nextFloat() < dt * 0.4f) {
                val dir = if (player.facingRight) 1f else -1f
                remainingProjectiles.add(Projectile(
                    id = "arch__",
                    isPlayerOwned = true, posX = player.posX - (80f * dir), posY = 150f,
                    velocityX = dir * (400f + Random.nextFloat() * 80f), velocityY = -30f + (Random.nextFloat() * 10f),
                    damage = 12f, pierce = 8f, blunt = 2f, type = "arrow",
                    sizeMultiplier = 1f, hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = false
                ))
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
            }
            if (hasCrossbow && Random.nextFloat() < dt * 0.25f) {
                val dir = if (player.facingRight) 1f else -1f
                remainingProjectiles.add(Projectile(
                    id = "xbow__",
                    isPlayerOwned = true, posX = player.posX - (50f * dir), posY = 170f,
                    velocityX = dir * (600f + Random.nextFloat() * 50f), velocityY = -5f,
                    damage = 25f, pierce = 20f, blunt = 10f, type = "arrow",
                    sizeMultiplier = 1f, hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = true
                ))
                MedievalAudioSynth.playSound(SoundType.THWACK)
            }
        }'''

new_fire = '''        // 5b. Archer & Crossbowman Entourage Fire
        val hasArcher = _uiState.value.unlockedAncillaries.contains("anc_archer")
        val hasCrossbow = _uiState.value.unlockedAncillaries.contains("anc_crossbowman")
        if (!player.isDead && !player.isDying) {
            val sortedAncs = _uiState.value.unlockedAncillaries.sorted()
            val archerIdx = sortedAncs.indexOf("anc_archer")
            val crossbowIdx = sortedAncs.indexOf("anc_crossbowman")
            
            if (hasArcher && Random.nextFloat() < dt * 0.4f) {
                val dir = if (player.facingRight) 1f else -1f
                val archerOffsetX = -80f - (archerIdx * 50f)
                val spawnX = player.posX + (archerOffsetX * dir)
                remainingProjectiles.add(Projectile(
                    id = "arch__",
                    isPlayerOwned = true, posX = spawnX, posY = 240f,
                    velocityX = dir * (400f + Random.nextFloat() * 80f), velocityY = -30f + (Random.nextFloat() * 10f),
                    damage = 12f, pierce = 8f, blunt = 2f, type = "arrow",
                    sizeMultiplier = 1f, hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = false
                ))
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
            }
            if (hasCrossbow && Random.nextFloat() < dt * 0.25f) {
                val dir = if (player.facingRight) 1f else -1f
                val crossbowOffsetX = -80f - (crossbowIdx * 50f)
                val spawnX = player.posX + (crossbowOffsetX * dir)
                remainingProjectiles.add(Projectile(
                    id = "xbow__",
                    isPlayerOwned = true, posX = spawnX, posY = 230f,
                    velocityX = dir * (600f + Random.nextFloat() * 50f), velocityY = -5f,
                    damage = 25f, pierce = 20f, blunt = 10f, type = "arrow",
                    sizeMultiplier = 1f, hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = true
                ))
                MedievalAudioSynth.playSound(SoundType.THWACK)
            }
        }'''

code = code.replace(old_fire, new_fire)

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("GameViewModel patched for precise projectile spawn!")
