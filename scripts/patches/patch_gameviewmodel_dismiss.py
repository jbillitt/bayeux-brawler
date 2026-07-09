import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """                // Completely random starter gear for the next attempt (each attempt starts fresh and unique!)
                state.copy(
                    isBattleActive = false,
                    battleWon = false,
                    battleLost = false,
                    level = 1,
                    extraAttachments = emptyList(),
                    extraArmors = emptyList(),
                    handleExtensionCount = 0,
                    unlockedAncillaries = emptySet(),
                    weaponHead = GameData.WEAPON_HEADS.random(),
                    weaponHandle = GameData.WEAPON_HANDLES.random(),
                    shield = if (Random.nextBoolean()) GameData.SHIELDS.random() else GameData.SHIELDS.first { it.id == "shield_none" },
                    armor = GameData.ARMOR_PIECES.random(),
                    headgear = GameData.HEADGEAR_PIECES.random()
                )"""

replacement = """                val initialGear = mutableSetOf<String>()
                initialGear.add("head_bare")
                initialGear.add("handle_fists")
                initialGear.add("shield_none")
                initialGear.add("armor_none")
                initialGear.add("head_none")
                initialGear.addAll(GameData.WEAPON_HEADS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.WEAPON_HANDLES.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.SHIELDS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.ARMOR_PIECES.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.HEADGEAR_PIECES.shuffled().take(2).map { it.id })
                
                // Completely random starter gear for the next attempt (each attempt starts fresh and unique!)
                state.copy(
                    isBattleActive = false,
                    battleWon = false,
                    battleLost = false,
                    level = 1,
                    unlockedGearIds = initialGear,
                    extraAttachments = emptyList(),
                    extraArmors = emptyList(),
                    handleExtensionCount = 0,
                    unlockedAncillaries = emptySet(),
                    weaponHead = GameData.WEAPON_HEADS.filter { it.id in initialGear }.random(),
                    weaponHandle = GameData.WEAPON_HANDLES.filter { it.id in initialGear }.random(),
                    shield = GameData.SHIELDS.filter { it.id in initialGear }.random(),
                    armor = GameData.ARMOR_PIECES.filter { it.id in initialGear }.random(),
                    headgear = GameData.HEADGEAR_PIECES.filter { it.id in initialGear }.random()
                )"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
