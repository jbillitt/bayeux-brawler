import re

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Make projectiles embed
if "embeddedProjectiles.add" not in code:
    old_apply = '''    private fun applyProjectileDamage(proj: Projectile, target: FighterState) {'''
    new_apply = '''    private fun applyProjectileDamage(proj: Projectile, target: FighterState) {
        val dir = if (proj.velocityX > 0) 1f else -1f
        target.embeddedProjectiles.add(
            EmbeddedProjectile(
                type = proj.type,
                isBallista = proj.isBallista,
                hasSpikes = proj.hasSpikes,
                offsetX = (kotlin.random.Random.nextFloat() * 30f - 15f),
                offsetY = (kotlin.random.Random.nextFloat() * 60f - 30f),
                angle = dir * (kotlin.random.Random.nextFloat() * 20f + 10f)
            )
        )'''
    code = code.replace(old_apply, new_apply)

# Add Crossbowman entourage logic
old_archer = '''        // 5b. Archer Entourage Fire
        if (_uiState.value.unlockedAncillaries.contains("anc_archer") && !player.isDead && !player.isDying) {
            if (Random.nextFloat() < dt * 0.4f) { // roughly every 2.5 seconds'''
new_archer = '''        // 5b. Archer and Crossbowman Entourage Fire
        val hasArcher = _uiState.value.unlockedAncillaries.contains("anc_archer")
        val hasCrossbow = _uiState.value.unlockedAncillaries.contains("anc_crossbowman")
        if ((hasArcher || hasCrossbow) && !player.isDead && !player.isDying) {
            // Check archer fire
            if (hasArcher && Random.nextFloat() < dt * 0.4f) { // roughly every 2.5 seconds
                val dir = if (player.facingRight) 1f else -1f
                val proj = Projectile(
                    id = "arch__",
                    isPlayerOwned = true,
                    posX = player.posX - (80f * dir),
                    posY = 230f,
                    velocityX = dir * (300f + Random.nextFloat() * 80f),
                    velocityY = -45f + (Random.nextFloat() * 10f - 5f),
                    damage = 12f,
                    pierce = 8f,
                    blunt = 2f,
                    type = "arrow",
                    sizeMultiplier = 1f,
                    hasSpikes = false,
                    launchedWeaponId = null,
                    isSplash = false,
                    isPoisonous = false,
                    isBallista = false
                )
                remainingProjectiles.add(proj)
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
            }
            // Check crossbow fire (slower but stronger ballista bolt)
            if (hasCrossbow && Random.nextFloat() < dt * 0.25f) { // roughly every 4 seconds
'''
old_archer2 = '''            }
        }'''
new_archer2 = '''            }
        }'''

# Since we want to insert crossbow fire cleanly:
import re
match = re.search(r'(if \(_uiState\.value\.unlockedAncillaries\.contains\("anc_archer"\).*?)(val newlySpawned)', code, re.DOTALL)
if match:
    old_block = match.group(1)
    new_block = '''        // 5b. Archer & Crossbowman Entourage Fire
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
        }
        
        '''
    code = code.replace(old_block, new_block)

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("GameViewModel patched!")
