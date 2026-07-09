import re

with open('app/src/main/java/com/example/game/SimulationModels.kt', 'r', encoding='utf-8') as f:
    text = f.read()

ids_to_check = [
    "head_axe", "head_sword", "head_flail", "head_mace", "head_war_flail",
    "head_pike", "head_javelin", "head_claymore", "head_maul", "head_halberd"
]

missing = []
for i in ids_to_check:
    if f'"{i}"' not in text:
        missing.append(i)

print("Missing IDs:", missing)
