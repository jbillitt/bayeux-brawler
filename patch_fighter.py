import re

with open("app/src/main/java/com/example/game/SimulationModels.kt", "r") as f:
    content = f.read()

target1 = """    // Equipped gear
    val weaponHead: GearItem,
    val weaponHandle: GearItem,
    val shield: GearItem,
    val armor: GearItem,
    val headgear: GearItem,
    val isDualWielding: Boolean = false,"""

replacement1 = """    // Equipped gear
    var weaponHead: GearItem,
    var weaponHandle: GearItem,
    var shield: GearItem,
    var armor: GearItem,
    var headgear: GearItem,
    var isDualWielding: Boolean = false,"""

if target1 in content:
    content = content.replace(target1, replacement1)

target2 = """    fun getRandomShout(type: SoundType, strikeType: String = ""): String {
        return when (type) {
            SoundType.CLANG -> BLOCK_SHOUTS.random()
            SoundType.THWACK -> {
                when (strikeType) {
                    "pierce" -> PIERCE_SHOUTS.random()
                    "slash" -> SLASH_SHOUTS.random()
                    else -> BLUNT_SHOUTS.random()
                }
            }
            SoundType.OUCH -> DAMAGE_SHOUTS.random()
            SoundType.HUZZAH -> VICTORY_SHOUTS.random()"""
            
replacement2 = """    fun getRandomShout(type: SoundType, strikeType: String = ""): String {
        return when (type) {
            SoundType.CLANG -> BLOCK_SHOUTS.random()
            SoundType.THWACK, SoundType.CRUNCH -> {
                when (strikeType) {
                    "pierce" -> PIERCE_SHOUTS.random()
                    "slash" -> SLASH_SHOUTS.random()
                    else -> BLUNT_SHOUTS.random()
                }
            }
            SoundType.OUCH -> DAMAGE_SHOUTS.random()
            SoundType.HUZZAH -> VICTORY_SHOUTS.random()
            else -> "" """

if target2 in content:
    content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/game/SimulationModels.kt", "w") as f:
    f.write(content)
print("Replaced successfully")
