import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target = """    private fun generateRandomSaxon(index: Int, level: Int): FighterState {
        val names = listOf(
            "Harold of Essex", "Gyrth Shield-Cleaver", "Leofwine", "Tostig Dunce", 
            "Aldred the Bald", "Godwin the Grumpy", "Sigurd Skull-Basher", "Ethelred the Unready",
            "Cerdic the Giant", "Wulfric", "Odo the Swift"
        )
        val saxonName = if (index < names.size) names[index] else "Saxon Foe ${index + 1}"

        // Scale Saxon health slightly with level
        val baseHp = 60f + (level * 8f)

        // Saxon gear is randomly compiled from weapon options
        val weaponHeads = listOf(
            GameData.WEAPON_HEADS[1], // pike
            GameData.WEAPON_HEADS[2], // axe
            GameData.WEAPON_HEADS[3], // sword
            GameData.WEAPON_HEADS[4], // morningstar
            GameData.WEAPON_HEADS[6], // spear
            GameData.WEAPON_HEADS[7], // dagger
            GameData.WEAPON_HEADS[8], // bow
            GameData.WEAPON_HEADS[9], // slingshot
            GameData.WEAPON_HEADS[10] // halberd
        )
        val selectedHead = weaponHeads.random()
        val selectedHandle = if (selectedHead.isRanged) GameData.WEAPON_HANDLES[0] else listOf(GameData.WEAPON_HANDLES[1], GameData.WEAPON_HANDLES[2], GameData.WEAPON_HANDLES[4]).random()
        
        // Saxon shields: some have shields, some don't
        val saxonShields = listOf(GameData.SHIELDS[0], GameData.SHIELDS[1], GameData.SHIELDS[2], GameData.SHIELDS[3])
        val selectedShield = if (selectedHead.isRanged) GameData.SHIELDS[0] else saxonShields.random()

        // Saxon armor: mostly padded or chainmail depending on level
        val armorOptions = if (level > 2) {
            listOf(GameData.ARMOR_PIECES[1], GameData.ARMOR_PIECES[2], GameData.ARMOR_PIECES[3], GameData.ARMOR_PIECES[4])
        } else {
            listOf(GameData.ARMOR_PIECES[0], GameData.ARMOR_PIECES[1], GameData.ARMOR_PIECES[2])
        }
        val selectedArmor = armorOptions.random()

        // Saxon helmet
        val helmOptions = listOf(GameData.HEADGEAR_PIECES[0], GameData.HEADGEAR_PIECES[1], GameData.HEADGEAR_PIECES[2])
        val selectedHelm = helmOptions.random()"""

replacement = """    private fun generateRandomSaxon(index: Int, level: Int): FighterState {
        val names = listOf(
            "Harold of Essex", "Gyrth Shield-Cleaver", "Leofwine", "Tostig Dunce", 
            "Aldred the Bald", "Godwin the Grumpy", "Sigurd Skull-Basher", "Ethelred the Unready",
            "Cerdic the Giant", "Wulfric", "Odo the Swift", "Aelfric", "Leofric", "Edric"
        )
        val saxonName = if (index < names.size) names[index] else "Saxon Foe ${index + 1}"

        // Scale Saxon health slightly with level
        val baseHp = 50f + (level * 10f)

        // Poor gear for early levels
        val selectedHead: com.example.game.GearItem
        val selectedHandle: com.example.game.GearItem
        val selectedShield: com.example.game.GearItem
        val selectedArmor: com.example.game.GearItem
        val selectedHelm: com.example.game.GearItem

        if (level <= 2) {
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
        } else {
            // High levels: good gear
            val weaponHeads = GameData.WEAPON_HEADS.filter { it.id !in listOf("head_bare", "head_pitchfork", "head_club", "head_slingshot") }
            selectedHead = weaponHeads.random()
            selectedHandle = if (selectedHead.isRanged) GameData.WEAPON_HANDLES[0] else listOf(GameData.WEAPON_HANDLES[1], GameData.WEAPON_HANDLES[2], GameData.WEAPON_HANDLES[4]).random()
            
            val saxonShields = listOf(GameData.SHIELDS[0], GameData.SHIELDS[1], GameData.SHIELDS[2], GameData.SHIELDS[3])
            selectedShield = if (selectedHead.isRanged) GameData.SHIELDS[0] else saxonShields.random()

            val armorOptions = listOf(GameData.ARMOR_PIECES[1], GameData.ARMOR_PIECES[2], GameData.ARMOR_PIECES[3], GameData.ARMOR_PIECES[4])
            selectedArmor = armorOptions.random()

            val helmOptions = listOf(GameData.HEADGEAR_PIECES[0], GameData.HEADGEAR_PIECES[1], GameData.HEADGEAR_PIECES[2])
            selectedHelm = helmOptions.random()
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
