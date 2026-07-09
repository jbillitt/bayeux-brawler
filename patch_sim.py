import re

with open('app/src/main/java/com/example/game/SimulationModels.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Add EmbeddedProjectile
if "data class EmbeddedProjectile" not in code:
    code = code.replace("data class FighterState(", 
'''data class EmbeddedProjectile(
    val type: String,
    val isBallista: Boolean,
    val hasSpikes: Boolean,
    val offsetX: Float,
    val offsetY: Float,
    val angle: Float
)

data class FighterState(''')

# Add embeddedProjectiles to FighterState
if "var embeddedProjectiles" not in code:
    old_state = '''    var deathType: Int = 0 // 0=normal, 1=crumple, 2=fly, 3=decapitate, 4=dismember, 5=bloody fountain
) {'''
    new_state = '''    var deathType: Int = 0, // 0=normal, 1=crumple, 2=fly, 3=decapitate, 4=dismember, 5=bloody fountain
    var embeddedProjectiles: MutableList<EmbeddedProjectile> = mutableListOf()
) {'''
    code = code.replace(old_state, new_state)

with open('app/src/main/java/com/example/game/SimulationModels.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("SimulationModels patched!")
