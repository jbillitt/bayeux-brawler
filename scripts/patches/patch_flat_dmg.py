import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """    private fun applyFlatDamage(dmg: Float, defender: FighterState) {
        if (defender.isDead || defender.isDying) return"""
        
replacement = """    private fun applyFlatDamage(dmg: Float, defender: FighterState, source: FighterState? = null) {
        if (defender.isDead || defender.isDying) return"""
        
content = content.replace(target, replacement)

target2 = """            addPopup(deathShout, defender.posX, 130f, Color.DarkGray)
        }
    }"""
    
replacement2 = """            addPopup(deathShout, defender.posX, 130f, Color.DarkGray)
            if (source != null && source.isPlayer && !defender.isPlayer) {
                _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1)
                source.kills++
            }
        }
    }"""
    
content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)

