import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target1 = """            selectedArmor = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_bare", "armor_tunic") }.random()
            selectedHelm = GameData.HEADGEAR_PIECES.filter { it.id in listOf("head_none", "head_hood") }.random()"""
replacement1 = """            selectedArmor = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_bare", "armor_padded") }.random()
            selectedHelm = GameData.HEADGEAR_PIECES.filter { it.id in listOf("helm_none", "helm_coif") }.random()"""

if target1 in content:
    content = content.replace(target1, replacement1)

target2 = """            selectedArmor = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_tunic", "armor_padded") }.random()
            selectedHelm = GameData.HEADGEAR_PIECES.filter { it.id in listOf("head_hood", "head_nasal") }.random()"""
replacement2 = """            selectedArmor = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_padded", "armor_leather") }.random()
            selectedHelm = GameData.HEADGEAR_PIECES.filter { it.id in listOf("helm_coif", "helm_conical") }.random()"""

if target2 in content:
    content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)
print("Replaced")
