import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """        if (level <= 2) {
            // Early levels: pitchforks, clubs, slingshots, mostly shirtless or tunic
            val earlyHeads = listOf("head_bare", "head_pitchfork", "head_club", "head_slingshot", "head_dagger")
            val head = GameData.WEAPON_HEADS.filter { it.id in earlyHeads }.randomOrNull() ?: GameData.WEAPON_HEADS[0]
            selectedHead = head
            selectedHandle = if (head.isRanged || head.id == "head_bare") GameData.WEAPON_HANDLES[0] else GameData.WEAPON_HANDLES.filter { it.id in listOf("handle_short", "handle_medium") }.random()
            selectedShield = GameData.SHIELDS[0] // No shield early
            selectedArmor = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_bare", "armor_tunic") }.random()
            selectedHelm = GameData.HEADGEAR_PIECES.filter { it.id in listOf("head_none", "head_hood") }.random()
        } else if (level <= 4) {
            // Mid levels: spears, axes, shields
            val midHeads = listOf("head_spear", "head_axe", "head_sword", "head_bow", "head_club")
            val head = GameData.WEAPON_HEADS.filter { it.id in midHeads }.randomOrNull() ?: GameData.WEAPON_HEADS.random()
            selectedHead = head
            selectedHandle = if (head.isRanged) GameData.WEAPON_HANDLES[0] else GameData.WEAPON_HANDLES.filter { it.id in listOf("handle_medium", "handle_long") }.random()
            selectedShield = if (head.isRanged) GameData.SHIELDS[0] else GameData.SHIELDS.filter { it.id in listOf("shield_none", "shield_buckler", "shield_tower") }.random()
            selectedArmor = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_tunic", "armor_padded") }.random()
            selectedHelm = GameData.HEADGEAR_PIECES.filter { it.id in listOf("head_hood", "head_nasal") }.random()
        } else {"""

replacement = """        if (level <= 2) {
            // Early levels: pitchforks, clubs, slingshots, mostly shirtless or tunic
            val earlyHeads = listOf("head_bare", "head_pitchfork", "head_club", "head_slingshot", "head_dagger")
            val head = GameData.WEAPON_HEADS.filter { it.id in earlyHeads }.randomOrNull() ?: GameData.WEAPON_HEADS[0]
            selectedHead = head
            selectedHandle = if (head.isRanged || head.id == "head_bare") GameData.WEAPON_HANDLES[0] else GameData.WEAPON_HANDLES.filter { it.id in listOf("handle_short", "handle_medium") }.random()
            selectedShield = GameData.SHIELDS[0] // No shield early
            val armorChoices = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_bare", "armor_padded") }
            selectedArmor = if (armorChoices.isNotEmpty()) armorChoices.random() else GameData.ARMOR_PIECES[0]
            val helmChoices = GameData.HEADGEAR_PIECES.filter { it.id in listOf("helm_none", "helm_coif") }
            selectedHelm = if (helmChoices.isNotEmpty()) helmChoices.random() else GameData.HEADGEAR_PIECES[0]
        } else if (level <= 4) {
            // Mid levels: spears, axes, shields
            val midHeads = listOf("head_spear", "head_axe", "head_sword", "head_bow", "head_club")
            val head = GameData.WEAPON_HEADS.filter { it.id in midHeads }.randomOrNull() ?: GameData.WEAPON_HEADS.random()
            selectedHead = head
            selectedHandle = if (head.isRanged) GameData.WEAPON_HANDLES[0] else GameData.WEAPON_HANDLES.filter { it.id in listOf("handle_medium", "handle_long") }.random()
            selectedShield = if (head.isRanged) GameData.SHIELDS[0] else GameData.SHIELDS.filter { it.id in listOf("shield_none", "shield_buckler", "shield_tower") }.random()
            val armorChoices = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_padded", "armor_leather") }
            selectedArmor = if (armorChoices.isNotEmpty()) armorChoices.random() else GameData.ARMOR_PIECES[0]
            val helmChoices = GameData.HEADGEAR_PIECES.filter { it.id in listOf("helm_coif", "helm_conical") }
            selectedHelm = if (helmChoices.isNotEmpty()) helmChoices.random() else GameData.HEADGEAR_PIECES[0]
        } else {"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
