import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target_armor = """                LevelUpChoice(
                    id = "armor_${item.id}",
                    type = "armor",
                    title = "🪖 LAYER: ${item.name}",
                    description = item.description
                )"""

replacement_armor = """                LevelUpChoice(
                    id = "armor_${item.id}",
                    type = "armor",
                    title = "Add Layer: ${item.name}",
                    description = item.description
                )"""

content = content.replace(target_armor, replacement_armor)

target_weapon = """                LevelUpChoice(
                    id = "weapon_${item.id}",
                    type = "attachment",
                    title = "⚔️ ATTACH: ${item.name}",
                    description = item.description
                )"""

replacement_weapon = """                LevelUpChoice(
                    id = "weapon_${item.id}",
                    type = "attachment",
                    title = "Attach: ${item.name}",
                    description = item.description
                )"""

content = content.replace(target_weapon, replacement_weapon)

target_follower = """                LevelUpChoice(
                    id = "follower_${item.id}",
                    type = "follower",
                    title = "🧍 RECRUIT: ${item.name} (${item.title})",
                    description = item.description
                )"""

replacement_follower = """                LevelUpChoice(
                    id = "follower_${item.id}",
                    type = "follower",
                    title = "Recruit: ${item.name} (${item.title})",
                    description = item.description
                )"""

content = content.replace(target_follower, replacement_follower)

target_haft = """                LevelUpChoice(
                    id = "haft_extension",
                    type = "extension",
                    title = "🛡️ EXTEND HAFT (+6 Inches)",
                    description = "Adds more reach and sweeping arc, but increases weight and reduces swing speed."
                )"""

replacement_haft = """                LevelUpChoice(
                    id = "haft_extension",
                    type = "extension",
                    title = "Extend haft length",
                    description = "Adds reach, increases weight."
                )"""

content = content.replace(target_haft, replacement_haft)

target_ranged = """                LevelUpChoice(
                    id = "ranged_barbs",
                    type = "ranged",
                    title = "🏹 BARBED AMMO",
                    description = "Ranged weapons deal bleeding damage and penetrate armor better."
                )"""

replacement_ranged = """                LevelUpChoice(
                    id = "ranged_barbs",
                    type = "ranged",
                    title = "Barbed ammo",
                    description = "Ranged weapons penetrate armor."
                )"""

content = content.replace(target_ranged, replacement_ranged)

target_poison = """                LevelUpChoice(
                    id = "ranged_poison",
                    type = "ranged",
                    title = "🏹 POISON AMMO",
                    description = "Ranged weapons cause damage over time to targets."
                )"""

replacement_poison = """                LevelUpChoice(
                    id = "ranged_poison",
                    type = "ranged",
                    title = "Poison ammo",
                    description = "Ranged hits poison targets."
                )"""

content = content.replace(target_poison, replacement_poison)

target_dual = """                LevelUpChoice(
                    id = "dual_wield",
                    type = "dual",
                    title = "⚔️ LEARN: DUAL WIELDING",
                    description = "Grants the ability to carry a secondary weapon instead of a shield. Double the attacks!"
                )"""

replacement_dual = """                LevelUpChoice(
                    id = "dual_wield",
                    type = "dual",
                    title = "Learn dual wielding",
                    description = "Carry a secondary weapon instead of a shield."
                )"""

content = content.replace(target_dual, replacement_dual)


with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)
print("Replaced successfully")

