import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
    "    private fun applyFlatDamage(dmg: Float, defender: FighterState, source: FighterState? = null) {",
    "    private fun applyFlatDamage(dmg: Float, defender: FighterState, isPlayerSource: Boolean = false) {"
)

content = content.replace(
    """            if (source != null && source.isPlayer && !defender.isPlayer) {
                _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1)
                source.kills++
            }""",
    """            if (isPlayerSource && !defender.isPlayer) {
                _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1)
            }"""
)

# Replace all calls:
# For block damage
content = content.replace("applyFlatDamage(blockDamage, currTarget)", "applyFlatDamage(blockDamage, currTarget, attacker.isPlayer)")
content = content.replace("applyFlatDamage(totalDamage, currTarget)", "applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)")
content = content.replace("applyFlatDamage(splashDmg, enemy)", "applyFlatDamage(splashDmg, enemy, attacker.isPlayer)") # this one is in melee splash
content = content.replace("applyFlatDamage(totalDamage, defender)", "applyFlatDamage(totalDamage, defender, proj.isPlayerOwned)")
# For projectile splash on enemy
content = content.replace('applyFlatDamage(splashDmg, enemy)', 'applyFlatDamage(splashDmg, enemy, proj.isPlayerOwned)')
# For projectile splash on player
content = content.replace('applyFlatDamage(splashDmg, player)', 'applyFlatDamage(splashDmg, player, proj.isPlayerOwned)')

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)

