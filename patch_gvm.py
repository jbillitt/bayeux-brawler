import re

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Make generateRandomSaxon incredibly robust
old_generate = '''        val loadout = when (arch) {
            EnemyArchetype.FYRD_LEVY -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_spear", "head_dagger", "head_bare", "head_pitchfork", "head_club") }.random(rng),
                GameData.WEAPON_HANDLES.first { it.id == "handle_short" },
                GameData.SHIELDS.first { it.id == if (rng.nextFloat() < 0.3f) "shield_buckler" else "shield_none" },
                GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }
            ), false)
            EnemyArchetype.HOUSECARL -> Pair(listOf(
                GameData.WEAPON_HEADS.first { it.id == "head_axe" },
                GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                GameData.SHIELDS.first { it.id == "shield_none" },
                GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_conical" }
            ), false)
            EnemyArchetype.ARCHER -> Pair(listOf(
                GameData.WEAPON_HEADS.first { it.id == "head_bow" },
                GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                GameData.SHIELDS.first { it.id == "shield_none" },
                GameData.ARMOR_PIECES.first { it.id == "armor_leather" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }
            ), false)
            EnemyArchetype.SHIELD_WALL -> Pair(listOf(
                GameData.WEAPON_HEADS.first { it.id == "head_spear" },
                GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                GameData.SHIELDS.first { it.id == "shield_tower" },
                GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_conical" }
            ), false)
            EnemyArchetype.BERSERKER -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_axe", "head_sword", "head_flail", "head_mace", "head_war_flail") }.random(rng),
                GameData.WEAPON_HANDLES.first { it.id == "handle_short" },
                GameData.SHIELDS.first { it.id == "shield_none" },
                GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }
            ), false)
            EnemyArchetype.CAVALRY -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_pike", "head_sword", "head_javelin") }.random(rng),
                GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                GameData.SHIELDS.first { it.id == "shield_heater" },
                GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_conical" }
            ), true)
            EnemyArchetype.CHAMPION -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_claymore", "head_maul", "head_war_flail", "head_halberd") }.random(rng),
                GameData.WEAPON_HANDLES.first { it.id == "handle_iron" },
                GameData.SHIELDS.first { it.id == "shield_none" },
                GameData.ARMOR_PIECES.first { it.id == "armor_scale" },
                GameData.HEADGEAR_PIECES.first { it.id == "helm_great" }
            ), if (rng.nextFloat() < 0.5f) true else false)
        }'''

new_generate = '''        fun <T> List<T>.safeRandom(fallback: T): T = if (this.isEmpty()) fallback else this.random(rng)
        fun safeHead(id: String) = GameData.WEAPON_HEADS.firstOrNull { it.id == id } ?: GameData.WEAPON_HEADS.first()
        fun safeHandle(id: String) = GameData.WEAPON_HANDLES.firstOrNull { it.id == id } ?: GameData.WEAPON_HANDLES.first()
        fun safeShield(id: String) = GameData.SHIELDS.firstOrNull { it.id == id } ?: GameData.SHIELDS.first()
        fun safeArmor(id: String) = GameData.ARMOR_PIECES.firstOrNull { it.id == id } ?: GameData.ARMOR_PIECES.first()
        fun safeHelm(id: String) = GameData.HEADGEAR_PIECES.firstOrNull { it.id == id } ?: GameData.HEADGEAR_PIECES.first()

        val loadout = when (arch) {
            EnemyArchetype.FYRD_LEVY -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_spear", "head_dagger", "head_bare", "head_pitchfork", "head_club") }.safeRandom(safeHead("head_bare")),
                safeHandle("handle_short"),
                safeShield(if (rng.nextFloat() < 0.3f) "shield_buckler" else "shield_none"),
                safeArmor("armor_padded"),
                safeHelm("helm_none")
            ), false)
            EnemyArchetype.HOUSECARL -> Pair(listOf(
                safeHead("head_axe"),
                safeHandle("handle_medium"),
                safeShield("shield_none"),
                safeArmor("armor_chainmail"),
                safeHelm("helm_conical")
            ), false)
            EnemyArchetype.ARCHER -> Pair(listOf(
                safeHead("head_bow"),
                safeHandle("handle_fists"),
                safeShield("shield_none"),
                safeArmor("armor_leather"),
                safeHelm("helm_none")
            ), false)
            EnemyArchetype.SHIELD_WALL -> Pair(listOf(
                safeHead("head_spear"),
                safeHandle("handle_medium"),
                safeShield("shield_tower"),
                safeArmor("armor_chainmail"),
                safeHelm("helm_conical")
            ), false)
            EnemyArchetype.BERSERKER -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_axe", "head_sword", "head_flail", "head_mace", "head_war_flail") }.safeRandom(safeHead("head_axe")),
                safeHandle("handle_short"),
                safeShield("shield_none"),
                safeArmor("armor_bare"),
                safeHelm("helm_none")
            ), false)
            EnemyArchetype.CAVALRY -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_pike", "head_sword", "head_javelin") }.safeRandom(safeHead("head_sword")),
                safeHandle("handle_medium"),
                safeShield("shield_heater"),
                safeArmor("armor_chainmail"),
                safeHelm("helm_conical")
            ), true)
            EnemyArchetype.CHAMPION -> Pair(listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_claymore", "head_maul", "head_war_flail", "head_halberd") }.safeRandom(safeHead("head_maul")),
                safeHandle("handle_iron"),
                safeShield("shield_none"),
                safeArmor("armor_scale"),
                safeHelm("helm_great")
            ), if (rng.nextFloat() < 0.5f) true else false)
        }'''

code = code.replace(old_generate, new_generate)

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("GameViewModel patched for safety!")
